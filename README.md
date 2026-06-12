# Event-Driven Order Processing System

A production-style trading order backend built with Java 21, Spring Boot 3.x, Apache Kafka, PostgreSQL, and Redis. Orders are submitted via REST API, persisted immediately, streamed through Kafka for asynchronous processing, retried on failure, and cached for fast retrieval.

---

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Order Lifecycle](#order-lifecycle)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Kafka Events](#kafka-events)
- [Caching](#caching)
- [Retry Logic](#retry-logic)
- [Observability](#observability)
- [Running Locally](#running-locally)
- [Engineering Decisions](#engineering-decisions)

---

## Architecture

```
Client (HTTP / Postman)
         |
         v
 ┌───────────────────┐
 │   OrderController  │   HTTP layer — validates input, delegates to service
 └────────┬──────────┘
          |
          v
 ┌───────────────────┐
 │    OrderService    │   Business logic — creates, reads, updates orders
 └────────┬──────────┘
          |
     ┌────+────┐
     |         |
     v         v
 PostgreSQL  OrderProducer ──→ Kafka topic: order-created
 (PENDING)                              |
                                        v
                               ┌─────────────────┐
                               │  OrderConsumer   │   Async worker
                               └────────┬─────────┘
                                        |
                          ┌─────────────+─────────────┐
                          |                           |
                          v                           v
                      EXECUTED                   RETRY / FAILED
                          |
                          v
                     Redis Cache   ←── GET /orders/{id}
                    (evicted on
                    status change)
```

The HTTP request path is fully decoupled from the processing path. `POST /orders` saves the order, publishes a Kafka event, and returns `PENDING` — it never waits for execution. The consumer runs in a separate thread, processes the event asynchronously, and updates status independently. `GET /orders/{id}` is served from Redis on repeat calls, hitting PostgreSQL only on a cache miss.

---

## Tech Stack

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21 | Primary language |
| Spring Boot | 3.x | Web framework, dependency injection, auto-configuration |
| PostgreSQL | 15+ | Relational persistence for orders |
| Apache Kafka | Latest (Docker) | Async event streaming between producer and consumer |
| Redis | Latest (Docker) | In-memory cache for GET /orders/{id} |
| Docker & Docker Compose | Latest | Containerised infrastructure — single command to start everything |
| Maven | Bundled | Build tool and dependency management |

---

## Order Lifecycle

Every order moves through a defined set of statuses:

```
PENDING ──→ PROCESSING ──→ EXECUTED
                       └──→ RETRY ──→ PROCESSING ──→ EXECUTED
                                  └──→ RETRY ──→ PROCESSING ──→ EXECUTED
                                             └──→ RETRY ──→ FAILED
```

| Status | Meaning |
|--------|---------|
| `PENDING` | Order saved to PostgreSQL; Kafka event published; returned immediately to client |
| `PROCESSING` | Consumer has picked up the event and begun work |
| `RETRY` | Processing failed; order re-queued with incremented retry count |
| `EXECUTED` | Order successfully processed |
| `FAILED` | Permanently failed after 3 consecutive retry attempts |

The `retry_count` column tracks how many times an order has been re-attempted. Once it reaches 3, the order is marked `FAILED` and no further processing is attempted.

---

## Project Structure

```
src/main/java/com/orderSystem/order_system/
├── controller/
│   ├── HealthController.java          — GET /health
│   └── OrderController.java           — REST endpoints for orders
├── service/
│   └── OrderService.java              — All business logic; no repository access in controllers
├── repository/
│   └── OrderRepository.java           — JPA interface; extends JpaRepository<Order, Long>
├── model/
│   └── Order.java                     — @Entity; maps to the `orders` table
├── dto/
│   ├── CreateOrderRequest.java        — Validated inbound request; decoupled from entity
│   └── OrderResponse.java             — Outbound response shape; never exposes entity directly
├── kafka/
│   ├── OrderCreatedEvent.java         — Kafka message payload
│   ├── OrderProducer.java             — Publishes to topic `order-created`
│   └── OrderConsumer.java             — @KafkaListener; processes events, handles retries
├── config/
│   ├── KafkaConfig.java               — Producer/consumer factory beans
│   └── RedisConfig.java               — Cache manager, TTL configuration
└── exception/
    └── GlobalExceptionHandler.java    — @RestControllerAdvice; consistent error responses

src/main/resources/
└── application.yml                    — Datasource, Kafka broker, Redis, JPA settings

docker-compose.yml                     — PostgreSQL, Kafka (KRaft), Redis
Dockerfile                             — Containerises the Spring Boot app
```

---

## Database Schema

```sql
CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(10)    NOT NULL,
    side        VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    price       DECIMAL(10,2)  NOT NULL CHECK (price > 0),
    status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

CREATE INDEX idx_orders_status ON orders(status);
```

---

## API Reference

### `GET /health`

Returns application health status.

**Response `200 OK`**
```json
{ "status": "UP" }
```

---

### `POST /orders`

Submits a new order. Saves to the database with status `PENDING` and publishes an event to Kafka. Returns immediately — does not wait for processing.

**Request body**
```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 190.00,
  "clientOrderId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `symbol` | string | Required, non-blank |
| `side` | string | Required; `BUY` or `SELL` |
| `quantity` | integer | Required, minimum 1 |
| `price` | decimal | Required, minimum 1 |
| `clientOrderId` | UUID | Required; idempotency key — duplicate submissions return the existing order |

**Response `201 Created`**
```json
{
  "orderId": 1,
  "status": "PENDING"
}
```

**Response `400 Bad Request`** (validation failure)
```json
{
  "error": "Validation failed",
  "details": {
    "quantity": "must be greater than or equal to 1",
    "symbol": "must not be blank"
  },
  "status": 400
}
```

**Idempotency:** If a request arrives with a `clientOrderId` that already exists in the database, the existing order is returned rather than creating a duplicate. This is critical for financial systems where network retries can cause double submissions.

---

### `GET /orders/{id}`

Returns a single order by ID. Served from Redis cache on repeat calls; fetches from PostgreSQL on a cache miss and caches the result.

**Response `200 OK`**
```json
{
  "orderId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 190.00,
  "status": "EXECUTED",
  "retryCount": 0,
  "createdAt": "2026-06-12T10:00:00"
}
```

**Response `404 Not Found`**
```json
{
  "error": "Order not found",
  "status": 404
}
```

---

### `GET /orders`

Returns all orders.

**Response `200 OK`** — array of order objects (same shape as above).

---

### `GET /orders/{id}/status`

Returns only the status of a given order.

**Response `200 OK`**
```json
{
  "orderId": 1,
  "status": "EXECUTED"
}
```

---

## Kafka Events

**Topic:** `order-created`

Published by `OrderProducer` immediately after an order is saved. Consumed by `OrderConsumer`.

```json
{
  "orderId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 190.00
}
```

On retry, the consumer re-publishes this same event to the same topic. The re-queued event is indistinguishable from the original — the consumer checks `retryCount` on the database record to determine whether to process or give up.

---

## Caching

`GET /orders/{id}` is cached in Redis using Spring's `@Cacheable` annotation:

- **Cache name:** `orders`
- **Cache key:** order ID
- **TTL:** 10 minutes (configured via `RedisCacheConfiguration` bean)
- **Eviction:** `@CacheEvict` is called whenever an order's status is updated, ensuring the next read fetches fresh data from PostgreSQL and re-populates the cache

This is the **cache-aside pattern**: the application checks the cache first, falls back to the database on a miss, then writes the result back to the cache. The TTL acts as a safety net — even if an eviction is missed, stale data expires automatically.

---

## Retry Logic

When the consumer fails to process an order:

1. Check `retryCount` on the order record
2. If `retryCount < 3`: increment counter, set status to `RETRY`, re-publish the `OrderCreatedEvent` to Kafka, log a warning
3. If `retryCount >= 3`: set status to `FAILED`, log an error, stop processing

Re-publishing to Kafka means retries benefit from all the same durability and delivery guarantees as the original event. The consumer does not block during retries — it re-queues and moves on to the next message immediately.

---

## Observability

Structured SLF4J log statements at every stage of the order lifecycle:

| Event | Level | Location |
|-------|-------|----------|
| Order created and saved | `INFO` | `OrderService` |
| Kafka event published | `INFO` | `OrderProducer` |
| Consumer started processing | `INFO` | `OrderConsumer` |
| Order executed successfully | `INFO` | `OrderConsumer` |
| Order retry queued | `WARN` | `OrderConsumer` |
| Order permanently failed | `ERROR` | `OrderConsumer` |

---

## Running Locally

**Prerequisites:** Java 21, Maven, Docker

```bash
# Start all infrastructure (PostgreSQL, Kafka, Redis)
docker compose up -d

# Build and run the application
mvn clean package -DskipTests
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

To run the fully containerised stack (app + infrastructure):

```bash
docker compose up --build
```

---

## Engineering Decisions

**Why Kafka instead of direct REST calls between services?**
A synchronous HTTP call from the API to a processing worker couples their availability — if the worker is slow or down, the API blocks or fails. Kafka is a durable, ordered log: events survive consumer restarts, can be replayed from any offset, and allow the consumer to scale independently. For an order system where losing a submitted order is unacceptable, the durability and decoupling of a message broker outweighs the added complexity over a direct call.

**Why Redis for caching?**
Order status is read far more frequently than it changes. Serving reads from Redis eliminates repeated database round-trips for the same record. Using a shared cache (rather than in-process memory) also means all instances of the service see a consistent view — important when scaling horizontally. Explicit eviction on status change keeps the cache correct; the TTL is a fallback to prevent stale data persisting indefinitely if an eviction is ever missed.

**Why a separate DTO layer?**
Exposing JPA entities directly from controllers couples the API contract to the database schema. Adding a field to the entity for an internal purpose would silently change the API response. DTOs also provide the right place to enforce validation rules (`@NotNull`, `@Min`) without polluting the entity with HTTP concerns.

**Why idempotency via `clientOrderId`?**
In a networked system, the client cannot know whether a timed-out request was received or not. Without idempotency, retrying a failed submission creates duplicate orders. By requiring a client-generated UUID, the server can detect and deduplicate retries — the second identical request returns the existing order with a `200` rather than creating a second one with a `201`.
