# saga-orchestrated-distributed-order-system

Order flow across multiple services — when you place an order it goes through inventory (reserve stock), payment (authorize), and shipping (create shipment). I used Kafka for messaging between services and the Saga pattern so if something fails (e.g. payment fails) the previous steps get compensated (stock released, payment voided). Each service has its own Postgres DB. Outbox pattern is used so events are published to Kafka reliably after the DB transaction.

**Tech:** Java 17, Spring Boot 3, Kafka, PostgreSQL, Docker.

**Run everything:**
```bash
docker-compose up -d
```

**Create an order** (after services are up):
```bash
curl -X POST http://localhost:8081/api/orders -H "Content-Type: application/json" -d "{\"customerId\":\"cust-1\",\"items\":[{\"productId\":\"p1\",\"quantity\":2}]}"
```

**Services:** order (8081), payment (8082), inventory (8083), shipping (8084). Order service is the one that receives the HTTP request and drives the saga via Kafka; the others consume commands and publish events back.
