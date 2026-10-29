# Quickstart Validation Guide: Entrega 2 Backend Core & Real-Time Engine

**Feature**: `004-entrega2-core`  
**Date**: 2026-10-24  
**Scope**: Step-by-step runnable guide to validate all Backend Core & SSE features end-to-end.

---

## 1. Prerequisites

- **Java Development Kit**: OpenJDK 17+
- **Docker**: Docker Desktop / Engine running (required for Testcontainers & Redis container)
- **Git**: Working on branch `feature/004-entrega2-core-update`
- **Maven**: Accessible via project wrapper `./mvnw` or `mvnw.cmd`

---

## 2. Infrastructure Setup & Build

### Step 2.1: Start Local PostgreSQL & Redis (for manual or development testing)
If not running via docker-compose:
```bash
docker run -d --name cbo-postgres -e POSTGRES_DB=cbo_players_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15-alpine
docker run -d --name cbo-redis -p 6379:6379 redis:7-alpine
```

### Step 2.2: Compile and Validate Build
```bash
cd back
./mvnw clean compile
```

---

## 3. Automated Test Verification (Testing Profiles)

### Scenario A: Run Pure Unit Tests (Isolated Profile)
Executes isolated unit tests with Mockito and JUnit 5 without starting Spring or Docker containers:
```bash
./mvnw test -Punit
```
**Expected Outcome**: 100% tests green. Fast execution (< 15 seconds).

### Scenario B: Run End-to-End Tests with Testcontainers
Executes integration tests spinning up PostgreSQL via Testcontainers:
```bash
./mvnw verify -Pe2e
```
**Expected Outcome**: Testcontainers starts ephemeral PostgreSQL, runs integration tests (AuthController, CatalogIntegration, OrderConcurrency, SseAlerts), and shuts down cleanly.

---

## 4. End-to-End Functional Scenarios (API Flow)

Refer to [contracts/](contracts/) and [data-model.md](data-model.md) for full payload structures.

### Scenario 1: Top-up Credits (Simulation)
1. **Request**:
   ```bash
   curl -X POST http://localhost:8080/api/users/1/credits/topup \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <USER_JWT>" \
     -d '{"amount": 100.0}'
   ```
2. **Verify**: Returns `200 OK` with `balanceAfter: 100.0000`. Database records a transaction in `transactions` with `operation_type = 'RECARGA'`.

### Scenario 2: Superuser Activates Strategy B
1. **Request**:
   ```bash
   curl -X PATCH http://localhost:8080/api/admin/strategies/2/activate \
     -H "Authorization: Bearer <SUPERUSER_JWT>"
   ```
2. **Verify**: Returns `200 OK` with `active: true` for strategy 2. Attempting this endpoint with a regular user JWT returns `403 Forbidden`.

### Scenario 3: Recalculate Quotes
1. **Request**:
   ```bash
   curl -X POST http://localhost:8080/api/quotes/recalculate \
     -H "Authorization: Bearer <SUPERUSER_JWT>"
   ```
2. **Verify**: All player quotes update in `player_quotes`. Redis cache keys for ranking are cleared.

### Scenario 4: Create Price Alert & Connect to SSE Stream
1. **Subscribe to SSE stream**:
   ```bash
   curl -N -H "Authorization: Bearer <USER_JWT>" -H "Accept: text/event-stream" http://localhost:8080/api/alerts/stream
   ```
2. **Create Alert**:
   ```bash
   curl -X POST http://localhost:8080/api/alerts \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <USER_JWT>" \
     -d '{"playerId": 45, "condition": "ABOVE", "targetPrice": 25.0}'
   ```
3. **Trigger Recalculation**: Run `POST /api/quotes/recalculate`.
4. **Verify**: The SSE terminal window instantly prints `event: price-alert` within 2 seconds. The alert status transitions to `DISPARADA` in the database.

### Scenario 5: Buy & Sell Tokens
1. **Buy Order**:
   ```bash
   curl -X POST http://localhost:8080/api/orders/buy \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <USER_JWT>" \
     -d '{"playerId": 45, "quantity": 2}'
   ```
   **Verify**: User balance decreases by `2 * currentQuote`. User gains 2 tokens. Superuser pool has 98 tokens left.
2. **Sell Order**:
   ```bash
   curl -X POST http://localhost:8080/api/orders/sell \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <USER_JWT>" \
     -d '{"playerId": 45, "quantity": 1}'
   ```
   **Verify**: User balance credited with `1 * currentQuote`. Superuser pool returns to 99 tokens.

### Scenario 6: Verify Portfolio & Ranking
1. **Portfolio**: `GET http://localhost:8080/api/users/1/portfolio` returns positions, weighted average cost, and nominal/percentage PnL.
2. **Ranking**: `GET http://localhost:8080/api/players/ranking` returns sorted leaderboard served from Redis.
