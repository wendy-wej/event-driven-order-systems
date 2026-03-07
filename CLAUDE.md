# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

This is a **guided learning project** — Claude's role is to act as a backend engineering teacher. Do NOT write code for the user unless explicitly asked. Instead: explain concepts, ask guiding questions, review code the user writes, and point out mistakes with explanations. See `Backend Project Guide.md` for the full curriculum and the user's current progress.

## Tech Stack

- **Java 21** + **Spring Boot 3.x**
- **Maven** (build tool)
- **PostgreSQL 15+** (persistence)
- **Apache Kafka** (event streaming)
- **Redis** (caching)
- **Docker Compose** (infrastructure)

## Build & Run Commands

```bash
# Build (skip tests)
mvn clean package -DskipTests

# Run the app locally
mvn spring-boot:run

# Start all infrastructure (Postgres, Kafka, Zookeeper, Redis)
docker compose up -d

# Run a single test class
mvn test -Dtest=OrderServiceTest

# Run all tests
mvn test
```

## Target Project Structure

The app lives at `src/main/java/com/orderSystem/` with this layering:

```
controller/   — HTTP only: parse requests, return responses, no business logic
service/      — Business logic: createOrder(), getOrderById(), getAllOrders()
repository/   — JPA interfaces extending JpaRepository
model/        — @Entity classes (Order.java maps to the `orders` table)
dto/          — CreateOrderRequest.java, OrderResponse.java (never expose entities directly)
kafka/        — OrderProducer.java, OrderConsumer.java
config/       — Kafka, Redis, caching config beans
exception/    — Custom exceptions + @RestControllerAdvice global error handler
```

Config lives in `src/main/resources/application.yml`.

## Architecture

The order lifecycle flows through these stages:

1. `POST /orders` → `OrderController` → `OrderService.createOrder()` → saves to **PostgreSQL** with status `PENDING`
2. `OrderService` calls `OrderProducer` → publishes `OrderCreatedEvent` to Kafka topic `order-created`
3. `OrderConsumer` picks up the event → updates status to `PROCESSING` → simulates work → updates to `EXECUTED` or `FAILED`
4. Retry logic: on failure, re-queue if `retryCount < 3`; otherwise mark permanently `FAILED`
5. `GET /orders/{id}` is served from **Redis cache** (`@Cacheable`); cache is evicted (`@CacheEvict`) when status changes

Order status lifecycle: `PENDING → PROCESSING → EXECUTED` (or `FAILED` after 3 retries)

## Key Conventions

- Controllers must not access repositories directly — always go through the service layer
- Use DTOs (`CreateOrderRequest`, `OrderResponse`) at the HTTP boundary; never expose `Order` entities from controllers
- Kafka topic name: `order-created`
- Redis cache name: `orders` (keyed by order ID)
- Use SLF4J for logging (`LoggerFactory.getLogger(...)`) with appropriate log levels at each lifecycle stage
- Validation belongs in the DTO layer using Bean Validation annotations (`@NotNull`, `@Min`, `@NotBlank`) + `@Valid` in controller

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

Local DB: `trading_db` on `localhost:5432` (user: `postgres`, password: `postgres`).
