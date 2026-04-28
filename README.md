# Order Processing Platform

A backend REST API for managing orders — built to explore how Spring Boot integrates with PostgreSQL, Redis, Kafka, and Prometheus in a single cohesive system.

The core flow: a user registers, browses products, places an order. The API validates stock, persists the order, evicts the Redis cache for updated products, and fires a Kafka event that downstream consumers can act on. Metrics are scraped by Prometheus and visualized in Grafana.

## Architecture

> See `docs/architecture.png` for the full diagram.

The main moving parts:
- REST API handles auth (JWT), products, and orders
- PostgreSQL stores all persistent data
- Redis caches product lookups — evicted when stock changes after an order
- Kafka publishes an `order-events` topic on every order create/status change
- Prometheus scrapes `/actuator/prometheus` every 15s; Grafana sits on top

## Tech Stack

- Java 17, Spring Boot 3.3.5
- PostgreSQL 15, Spring Data JPA
- Redis 7 — Spring Cache with Jackson serialization
- Apache Kafka — event publishing and consumption
- JWT auth — JJWT 0.12, stateless, BCrypt passwords
- OpenAPI 3 / Swagger UI — `http://localhost:8080/swagger-ui.html`
- Actuator + Micrometer + Prometheus + Grafana
- JUnit 5, Mockito, Testcontainers
- Docker Compose, GitHub Actions CI

## API Endpoints

**Auth** (public)
```
POST /api/auth/register
POST /api/auth/login
```

**Products** (GET is public, POST requires ADMIN role)
```
GET    /api/products
GET    /api/products/{id}    ← Redis cached
POST   /api/products
```

**Orders** (JWT required)
```
POST   /api/orders
GET    /api/orders/{id}
GET    /api/orders/my-orders
PATCH  /api/orders/{id}/status
```

## How to Run

**1. Start infrastructure**
```bash
docker-compose up -d
```

**2. Run the app**
```bash
mvn spring-boot:run
```

**3. Try it in Swagger UI**
```
http://localhost:8080/swagger-ui.html
```

**4. Quick curl flow**
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"secret123"}'

# Login — grab the token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'

# Create a product
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","price":999.99,"stockQuantity":50}'

# Place an order
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":1,"quantity":2}]}'
```

**5. Grafana**

Go to `http://localhost:3000` (admin / admin), add Prometheus as a data source (`http://prometheus:9090`), then import dashboard ID `12900` from grafana.com for a ready-made Spring Boot dashboard.

## Running Tests

```bash
mvn test
```

- `OrderServiceTest` — unit tests: order creation, stock validation, not-found cases
- `OrderControllerTest` — MockMvc: endpoint responses, 400/404 error shapes
- `OrderProcessingApplicationTests` — context load

## Project Structure

```
src/main/java/com/kasha/orderprocessing/
├── config/        SecurityConfig, RedisConfig, KafkaConfig, OpenApiConfig
├── controller/    AuthController, ProductController, OrderController
├── dto/           request/ and response/ DTOs
├── entity/        User, Product, Order, OrderItem
├── enums/         OrderStatus, Role
├── exception/     GlobalExceptionHandler + typed exceptions
├── kafka/         OrderEvent, producer, consumer
├── repository/    Spring Data JPA interfaces
├── security/      JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
└── service/       AuthService, ProductService, OrderService
```

## A Few Tradeoffs Worth Mentioning

**Why Redis for products and not orders?**
Products are read frequently and change rarely. Orders change state often and are user-specific, so caching them adds complexity without much gain at this scale.

**Why Kafka instead of just calling a second service directly?**
The order API shouldn't care whether a downstream notification service is up or slow. Publishing an event and moving on keeps the order flow fast and resilient.

**No Flyway — is that a problem?**
For local development, `ddl-auto: update` is fine. In production, schema changes need to be versioned and auditable. Flyway would be the first thing I'd add before deploying this anywhere real.

## What's Next

- Flyway migrations
- Pagination on list endpoints
- Stock rollback on order cancellation
- Testcontainers integration tests against a real PostgreSQL + Kafka
- Deploy to AWS ECS or a Kubernetes cluster
