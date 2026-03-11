# Backend Engineering Project: Event-Driven Order Processing System
> A 4-week guided learning project (1 hour/day ≈ 28 hours total)

---

## How to Use This Document with Claude Code

This file is your **source of truth** when working with Claude Code. At the start of every session, tell Claude:

```
Read backend-project-guide.md. You are my backend engineering teacher. 
Do NOT write code for me. Instead, explain concepts, ask me guiding questions, 
and review code I write. Point out mistakes and explain why they're wrong. 
I am currently on [WEEK 1, DAY 5].
```

Update the **Current Progress** section below after every session so Claude always has context.

---

## Current Progress

```
Current Week: 1
Current Day:  5
Last completed task: (none yet)
Blockers / Questions: (none yet)
```

> **Update this section every session before prompting Claude.**

---

## Project Overview

You are building a **production-style Order Processing System** — a backend service that accepts orders via REST API, persists them to a database, streams events through a message broker, processes them asynchronously, and caches results for fast retrieval.

### What You Are Learning (In Order)

| Week | Theme | Core Concepts |
|------|-------|--------------|
| 1 | Core Backend API | REST, JPA, PostgreSQL, validation, service layers |
| 2 | Event-Driven Architecture | Kafka, async workers, failure simulation, retry logic |
| 3 | Caching & Performance | Redis, cache-aside pattern, TTL, cache eviction |
| 4 | Production Readiness | Docker Compose, load testing, idempotency, documentation |

### System Architecture (Final State)

```
Client (Postman)
      |
      v
Spring Boot REST API
      |
      +--> PostgreSQL (persist orders)
      |
      +--> Kafka Producer (publish OrderCreatedEvent)
                |
                v
         Kafka Consumer (Worker)
                |
                +--> Update order status (PROCESSING → EXECUTED | FAILED)
                |
                +--> Redis Cache (cache GET /orders/{id} responses)
```

---

## Tech Stack

| Tool | Purpose | Version Guidance |
|------|---------|-----------------|
| Java | Primary language | Java 21 |
| Spring Boot | Backend framework | 3.x |
| PostgreSQL | Relational database | 15+ |
| Apache Kafka | Event streaming / message broker | Latest via Docker |
| Redis | In-memory caching | Latest via Docker |
| Docker & Docker Compose | Containerised infrastructure | Latest |
| Postman | API testing | Latest |
| Maven | Build tool | Bundled with Spring Boot |

---

## Project Structure

Your final codebase should follow this layout:

```
order-system/
├── src/main/java/com/orderSystem/
│   ├── controller/         # HTTP layer — receives requests, returns responses
│   │   └── OrderController.java
│   ├── service/            # Business logic layer
│   │   └── OrderService.java
│   ├── repository/         # Database access layer (JPA)
│   │   └── OrderRepository.java
│   ├── model/              # Database entities
│   │   └── Order.java
│   ├── dto/                # Data Transfer Objects (request/response shapes)
│   │   ├── CreateOrderRequest.java
│   │   └── OrderResponse.java
│   ├── kafka/              # Kafka producer and consumer
│   │   ├── OrderProducer.java
│   │   └── OrderConsumer.java
│   ├── config/             # Configuration classes (Kafka, Redis, etc.)
│   └── exception/          # Custom exceptions and error handling
├── src/main/resources/
│   └── application.yml     # App configuration
├── docker-compose.yml      # All infrastructure services
├── Dockerfile              # For containerising the Spring Boot app
└── README.md               # Architecture write-up (Week 4)
```

---

## Database Schema

```sql
-- Table: orders
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

-- Index for frequent status queries
CREATE INDEX idx_orders_status ON orders(status);
```

**Order status lifecycle:**
```
PENDING → PROCESSING → EXECUTED
                    └→ FAILED (retried up to 3 times)
```

---

## API Contract

### POST /orders — Submit a new order
**Request:**
```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 190.00
}
```
**Response (201 Created):**
```json
{
  "orderId": 1,
  "status": "PENDING"
}
```

---

### GET /orders/{id} — Get order by ID
**Response (200 OK):**
```json
{
  "orderId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 190.00,
  "status": "EXECUTED",
  "createdAt": "2025-03-07T10:00:00Z"
}
```
> This endpoint will be cached in Redis (Week 3)

---

### GET /orders — Get all orders
**Response (200 OK):** Array of order objects.

---

### GET /orders/{id}/status — Get just the status (Week 4)
**Response (200 OK):**
```json
{ "orderId": 1, "status": "EXECUTED" }
```

---

## Kafka Events

### Topic: `order-created`

Published by: `OrderProducer` when a new order is saved.

```json
{
  "orderId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 190.00
}
```

Consumed by: `OrderConsumer` which processes the order and updates its status.

---

## Week-by-Week Build Plan

---

### WEEK 1 — Core Backend API
**Goal:** A working REST API that persists orders to PostgreSQL.

---

#### Day 1 — Project Setup
**What to do:**
- Go to [start.spring.io](https://start.spring.io) and generate a Spring Boot project
- Add dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok, Validation
- Verify the app starts with `mvn spring-boot:run`
- Add a health check endpoint: `GET /health` returns `{ "status": "UP" }`

**Key question to ask Claude:**
```
I've set up my Spring Boot project. Can you explain the purpose of each dependency 
I added and why the project structure separates controller, service, and repository?
```

**Done when:** App starts, `/health` returns 200.

---

#### Day 2 — Order Entity
**What to do:**
- Create `Order.java` in the `model` package
- Annotate it as a JPA `@Entity`
- Add all fields from the database schema above
- Use Lombok `@Data`, `@NoArgsConstructor` to reduce boilerplate

**Key question to ask Claude:**
```
I've written my Order entity. Can you review it and explain what @Entity, 
@Id, and @GeneratedValue actually do under the hood?
```

**Done when:** Entity class compiles without errors.

---

#### Day 3 — PostgreSQL Integration
**What to do:**
- Run PostgreSQL locally (or via Docker: `docker run -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres`)
- Create database `trading_db`
- Configure `application.yml` with datasource and JPA settings
- Set `spring.jpa.hibernate.ddl-auto=create-drop` for now (you'll manage schema manually later)

**application.yml starter:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/trading_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

**Key question to ask Claude:**
```
What is the difference between ddl-auto values: create-drop, update, validate, none? 
Which should I use in production and why?
```

**Done when:** App starts and connects to PostgreSQL without errors.

---

#### Day 4 — Order Repository & Basic API
**What to do:**
- Create `OrderRepository` extending `JpaRepository<Order, Long>`
- Create `OrderController` with `POST /orders` and `GET /orders/{id}`
- For now, save directly from controller (you'll add the service layer tomorrow)
- Test with Postman

**Done when:** You can POST an order and GET it back by ID.

---

#### Day 5 — Service Layer
**What to do:**
- Create `OrderService` with methods: `createOrder()`, `getOrderById()`, `getAllOrders()`
- Move all business logic out of the controller into the service
- Controller should only handle HTTP concerns (status codes, request parsing)

**Key question to ask Claude:**
```
I've moved logic to a service layer. Why is it bad practice to put business 
logic directly in a controller? What would break if I did?
```

**Done when:** Controller only calls service methods — no direct repository access in controller.

---

#### Day 6 — Input Validation
**What to do:**
- Create a `CreateOrderRequest` DTO (separate from the entity)
- Add validation annotations: `@NotNull`, `@Min`, `@NotBlank`
- Add `@Valid` in the controller method parameter
- Add a `@ControllerAdvice` to return a clean error response on validation failure

**Key question to ask Claude:**
```
Why should I use a DTO instead of accepting my Order entity directly 
in the controller? What risks does that prevent?
```

**Done when:** Sending `quantity: -5` returns a 400 with a helpful error message.

---

#### Day 7 — Week 1 Review
**What to do:**
- Test all endpoints in Postman
- Verify orders persist after app restart
- Review your code for any logic in the wrong layer

**Key question to ask Claude:**
```
Here is my OrderController, OrderService, and OrderRepository code: [paste code].
What would a senior engineer criticise about this? What would they improve?
```

**Done when:** Full CRUD works. You can explain every line.

---

### WEEK 2 — Event-Driven Architecture with Kafka
**Goal:** Orders are processed asynchronously via Kafka.

---

#### Day 8 — Kafka Setup with Docker
**What to do:**
- Create `docker-compose.yml` with `zookeeper` and `kafka` services
- Start with `docker compose up -d`
- Verify Kafka is running

**docker-compose.yml starter:**
```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

**Key question to ask Claude:**
```
What problem does Kafka solve that a regular REST call between services doesn't? 
Why is async processing better for order submission specifically?
```

**Done when:** `docker compose up` starts without errors.

---

#### Day 9 — Kafka Producer
**What to do:**
- Add `spring-kafka` dependency
- Create `OrderCreatedEvent` record/class (the message payload)
- Create `OrderProducer` that publishes to topic `order-created`
- Call the producer from `OrderService.createOrder()`

**Key question to ask Claude:**
```
I've written my Kafka producer. What happens if Kafka is down when I try to publish? 
How should I handle that?
```

**Done when:** Creating an order publishes an event (verify with Kafka logs or a CLI consumer).

---

#### Day 10 — Kafka Consumer (Worker)
**What to do:**
- Create `OrderConsumer` with `@KafkaListener(topics = "order-created")`
- When a message is consumed: update order status to `PROCESSING`, log it, then update to `EXECUTED`
- Add a small `Thread.sleep(1000)` to simulate processing time

**Key question to ask Claude:**
```
My consumer is updating the database. Should I call the repository directly from the 
consumer or go through the service? What are the trade-offs?
```

**Done when:** After POST /orders, you can see status change from PENDING → PROCESSING → EXECUTED.

---

#### Day 11 — Refine the Async Flow
**What to do:**
- Confirm the API returns `PENDING` immediately (not waiting for processing)
- Confirm the consumer processes in the background
- Use Postman to: POST order → immediately GET it (should be PENDING) → GET again after 2s (should be EXECUTED)

**Key question to ask Claude:**
```
What is the difference between synchronous and asynchronous processing? 
Draw out in words how my system now handles an order from POST to EXECUTED.
```

---

#### Day 12 — Failure Simulation
**What to do:**
- In `OrderConsumer`, add random failure: if `Math.random() < 0.2` → throw an exception
- Update order status to `FAILED` when this happens
- Log the failure with order ID

**Done when:** Roughly 1 in 5 orders ends up with status `FAILED`.

---

#### Day 13 — Retry Logic
**What to do:**
- Add `retry_count` field to Order entity
- In the consumer: on failure, if `retryCount < 3`, increment counter and re-queue the event
- If `retryCount >= 3`, mark as permanently `FAILED`

**Key question to ask Claude:**
```
What is exponential backoff and why is it better than retrying immediately? 
How would I implement it here?
```

**Done when:** Failed orders are retried up to 3 times before being marked permanently failed.

---

#### Day 14 — Logging & Observability
**What to do:**
- Add structured log statements at key points: order received, event published, consumer started, order executed/failed
- Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`)
- Review logs for a full order lifecycle

**Key question to ask Claude:**
```
What is the difference between log levels DEBUG, INFO, WARN, ERROR? 
Which should I use at each point in my order flow?
```

---

### WEEK 3 — Redis Caching
**Goal:** Frequently-read order data is served from cache, not the database.

---

#### Day 15 — Redis Setup
**What to do:**
- Add `redis` to `docker-compose.yml`
- Add `spring-boot-starter-data-redis` dependency
- Configure Redis in `application.yml`

```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

**Done when:** App connects to Redis on startup.

---

#### Day 16 — Cache GET /orders/{id}
**What to do:**
- Enable caching with `@EnableCaching` on your main class or a config class
- Add `@Cacheable("orders")` to `OrderService.getOrderById()`
- Test: call GET /orders/1 twice and observe only one DB query in the logs

**Key question to ask Claude:**
```
What is the cache-aside pattern? Is that what @Cacheable implements? 
What are the alternatives?
```

**Done when:** Second call to GET /orders/{id} does not hit the database.

---

#### Day 17 — Cache Eviction
**What to do:**
- When an order's status is updated (by the consumer), the cached value is now stale
- Add `@CacheEvict(value = "orders", key = "#orderId")` to the method that updates order status

**Key question to ask Claude:**
```
What is cache staleness? What bad things happen if I don't evict the cache 
when an order status changes?
```

**Done when:** After status update, next GET fetches fresh data from DB and re-caches it.

---

#### Day 18 — Cache TTL
**What to do:**
- Configure cache TTL (time-to-live) so entries expire after 10 minutes even without explicit eviction
- Do this via a `RedisCacheConfiguration` bean in a config class

**Key question to ask Claude:**
```
Why set a TTL even when you have explicit cache eviction? 
What scenario does TTL protect against that eviction alone doesn't?
```

---

#### Day 19 — Measure the Difference
**What to do:**
- Add timing logs to `getOrderById()` to measure how long DB reads take vs cache reads
- Call the endpoint 10 times with and without cache and compare

**Key question to ask Claude:**
```
I measured ~25ms from DB and ~2ms from cache. When would you NOT want to cache 
a result? What data is unsafe to cache?
```

---

#### Day 20 — Redis for Rate Limiting (Bonus)
**What to do:**
- Implement a simple rate limiter: max 10 requests per minute per IP
- Store counters in Redis with a 60-second TTL
- Return HTTP 429 when limit is exceeded

**Key question to ask Claude:**
```
How would a distributed rate limiter (Redis) behave differently from 
an in-memory rate limiter if I had 3 instances of this service running?
```

---

#### Day 21 — Week 3 Review
**What to do:**
- Verify the full flow: POST → Kafka → Consumer → DB → Cache
- Check cache is invalidated correctly when order status changes
- Ask Claude to quiz you

**Key question to ask Claude:**
```
Quiz me on caching. Ask me 5 questions about what I built this week 
and tell me if my answers are correct.
```

---

### WEEK 4 — Production Hardening
**Goal:** Containerise everything, handle edge cases, and document your decisions.

---

#### Day 22 — Dockerise the Spring Boot App
**What to do:**
- Create a `Dockerfile` at the project root
- Build a JAR first: `mvn clean package -DskipTests`
- Write the Dockerfile

```dockerfile
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/order-system-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Done when:** `docker build -t order-system .` succeeds.

---

#### Day 23 — Full Docker Compose
**What to do:**
- Add the Spring Boot app as a service in `docker-compose.yml`
- Add `depends_on` for postgres, kafka, redis
- Make database URL, Kafka broker, Redis host configurable via environment variables

**Done when:** `docker compose up` starts everything and the app connects to all services.

---

#### Day 24 — Load Testing
**What to do:**
- Write a simple shell script that sends 100 orders to POST /orders
- Watch the Kafka consumer logs — are they keeping up?
- Check GET /orders/{id} — are cache hits increasing?

**Key question to ask Claude:**
```
I sent 100 orders and the consumer processed them in X seconds. 
What does "throughput" mean? How would I scale this system to handle 10x more load?
```

---

#### Day 25 — Idempotency
**What to do:**
- Add a `clientOrderId` (UUID) field to `CreateOrderRequest`
- Before saving, check if an order with that `clientOrderId` already exists
- If it does, return the existing order instead of creating a duplicate

**Key question to ask Claude:**
```
What is idempotency and why does it matter in financial systems specifically? 
What HTTP status code should I return for a duplicate request — 200 or 201?
```

**Done when:** Sending the same request twice with the same `clientOrderId` only creates one order.

---

#### Day 26 — Global Error Handling
**What to do:**
- Create a `@RestControllerAdvice` class that handles all exceptions centrally
- Return consistent error responses: `{ "error": "Order not found", "status": 404 }`
- Handle: `OrderNotFoundException`, validation errors, unexpected exceptions

**Key question to ask Claude:**
```
Review my error handling class. Are there any exception types I'm missing? 
What information should I never expose in an error response?
```

---

#### Day 27 — README / Architecture Write-up
**What to do:**
Write `README.md` covering:
1. What the system does (2 paragraphs)
2. Architecture diagram (ASCII is fine)
3. Tech stack and why each was chosen
4. How to run locally (`docker compose up`)
5. API endpoints with example requests/responses
6. Engineering decisions: *Why Kafka instead of REST between services? Why Redis for caching? How does retry logic work?*

**Key question to ask Claude:**
```
Read my README and tell me: does it clearly explain the engineering decisions? 
Would a senior engineer at Bloomberg understand why I made each architectural choice?
```

---

#### Day 28 — Final Demo & Reflection
**What to do:**
- Start everything with `docker compose up`
- Walk through the full lifecycle in Postman: create order → check status → watch it execute
- Review every class — can you explain what each one does?

**Key question to ask Claude:**
```
I've finished the project. Ask me to explain the architecture as if I'm in 
a Bloomberg interview. Give me feedback on my explanation — what did I miss 
or explain poorly?
```

---

## Key Engineering Questions to Be Able to Answer

These are questions a Bloomberg interviewer might ask. You should be able to answer all of them after this project.

**On REST & APIs:**
- Why do we separate controllers, services, and repositories?
- What does `@Valid` do and where should validation live?
- When should an API return 400 vs 500?

**On Kafka & Async:**
- Why use Kafka instead of calling the worker service directly via HTTP?
- What is a consumer group and why does it matter for scaling?
- What happens if the consumer crashes mid-processing?

**On Caching:**
- What is the cache-aside pattern?
- What is cache staleness and how do you prevent it?
- Why set a TTL even if you have explicit eviction?

**On Resilience:**
- Why retry failed jobs? What is exponential backoff?
- What is idempotency and why is it critical for order processing?
- What happens to in-flight Kafka messages if the app crashes?

**On Docker:**
- What problem does Docker Compose solve vs running services manually?
- How would you scale the consumer horizontally?

---

## Glossary

| Term | Definition |
|------|-----------|
| DTO | Data Transfer Object — a class used to carry data between layers, separate from the database entity |
| JPA | Java Persistence API — the standard way to map Java objects to database tables |
| Kafka Topic | A named channel where events are published and consumed |
| Consumer Group | A set of consumers that share the work of reading from a Kafka topic |
| Cache-Aside | Pattern where app checks cache first, falls back to DB on miss, then populates cache |
| TTL | Time-to-live — how long a cached value stays valid before expiring |
| Idempotency | Property where performing the same operation multiple times has the same effect as once |
| Retry Logic | Mechanism to re-attempt failed operations a limited number of times |
| Backoff | Waiting progressively longer between retries to avoid overwhelming a failing system |