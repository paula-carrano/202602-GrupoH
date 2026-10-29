# Research & Technical Decisions: Entrega 2 Backend Core & Real-Time Engine

**Feature**: `004-entrega2-core`  
**Date**: 2026-10-24  
**Scope**: Backend Architecture, Persistence, Cache, Real-Time Streaming & Testing Profiles

---

## 1. Valuation Strategy Architecture & Superuser Activation

### Context & Challenge
The platform requires supporting at least two configurable valuation strategies that compute player quotes based on sporting statistics (minutes, goals, assists, shots, passes, interceptions, cards, rating). The active strategy and its metric weights must be centrally managed by a superuser (`PATCH /admin/strategies/{id}/activate`). A recalculation (`POST /quotes/recalculate` or weekly cron) must globally recalculate all active players and record historical quotes.

### Decision
- **Pattern**: Classic Spring-managed **Strategy Pattern** with dynamic weights backed by the `ValuationStrategy` entity.
- **Components**:
  - `ValuationStrategy` JPA entity storing: `id`, `name`, `code` (`OFFENSIVE`, `BALANCED`), `description`, `version`, `active` (boolean), `minQuote` (default 1.0), and weight coefficients stored either as JSON or dedicated columns (`weightGoals`, `weightAssists`, `weightMinutes`, `weightRating`, etc.).
  - `ValuationFormula` interface with implementations (e.g. `OffensiveValuationFormula`, `BalancedValuationFormula`) parameterized by weights.
  - `ValuationStrategyService`: Manages registered strategies and controls activation.
  - `AdminStrategyController`: Exposes `PATCH /api/admin/strategies/{id}/activate` secured with `@PreAuthorize("hasRole('SUPERUSER')")`.
- **Recalculation Flow**:
  1. `QuoteRecalculationService.recalculateAllQuotes()` retrieves the active strategy.
  2. For each active `Player` with `PlayerStatistics`, calculates `quote = max(1.0, formula.calculate(stats, strategy.getWeights()))`.
  3. Inserts a new `PlayerQuote` entity with `timestamp = UTC now`, `price`, `strategyName`, and `strategyVersion`.
  4. Publishes a Spring Application Event `QuotesRecalculatedEvent` carrying updated player quotes.
  5. Evicts the Redis cache for ranking and quotes.

### Alternatives Considered
- *Hardcoded Java formulas without DB persistence*: Rejected because requirements specify configurable weights, version tracking, and administrative activation.
- *Dynamic Groovy / Spring Expression Language (SpEL) scripts*: Rejected due to high security surface and unnecessary complexity.

---

## 2. Token Market Concurrency & Invariant of 100 Tokens

### Context & Challenge
Each football player has a strictly fixed emission of **100 tokens**, with initial price of 1 credit, initially 100% owned by the superuser. Users execute buy orders (`POST /orders/buy`) and sell orders (`POST /orders/sell`). Simultaneous concurrent requests must not violate the invariant:
$$\sum_{\text{users}} \text{tokens}(p) + \text{superuser\_tokens}(p) = 100$$

### Decision
- **Model**: `PortfolioPosition` entity representing `(user_id, player_id)` with `tokensQuantity` and `averageBuyPrice`.
- **Concurrency Control**:
  - Pessimistic write lock (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the superuser's `PortfolioPosition` for the target player during purchases and sales.
  - Check constraint and DB-level validation: `tokens_quantity >= 0`.
  - Transactional boundary (`@Transactional(isolation = Isolation.READ_COMMITTED)`) enclosing balance debit/credit, token transfer, and insertion into `Transaction` history.
- **Buyer PnL / Average Cost Accounting**:
  - On buy: `newAvgPrice = ((prevTokens * prevAvgPrice) + (newTokens * currentQuote)) / (prevTokens + newTokens)`.
  - On sell: average buy price remains unchanged; realized PnL = `(currentQuote - avgPrice) * soldTokens`.

### Alternatives Considered
- *Optimistic Locking (`@Version`)*: Under high concurrency on popular players, optimistic locking causes excessive `OptimisticLockException` and transaction rollbacks for buyers. Pessimistic write locking serializes token allocation safely without high rollback rates.

---

## 3. Simulated Credit Top-up & Immutable Transaction Audit

### Context & Challenge
Users need to add funds to their wallet balance to buy tokens without third-party payment gateways (Stripe, MercadoPago). The operation must be traceable and auditable.

### Decision
- **Endpoint**: `POST /api/users/{id}/credits/topup` receiving `{"amount": <BigDecimal>}`.
- **Constraints**:
  - `amount > 0.0` and `amount <= 10000.0` per operation.
  - Requires authenticated JWT matching the `{id}` or superuser.
  - Directly increments `User.creditsBalance`.
- **Audit Entity (`Transaction`)**:
  - Columns: `id`, `timestamp` (UTC), `actorUserId`, `operationType` (`COMPRA`, `VENTA`, `RECARGA`, `CAMBIO_ESTRATEGIA`), `playerId` (nullable), `quantity` (nullable), `unitPrice` (nullable), `totalAmount`, `balanceBefore`, `balanceAfter`, `tokensBefore` (nullable), `tokensAfter` (nullable), `strategyVersion`.
  - Service enforcement: No `update` or `delete` repository methods exist for `Transaction`. `@PreUpdate` and `@PreRemove` throw `UnsupportedOperationException`.

### Alternatives Considered
- *Mock Stripe webhook*: Rejected by explicit group and teaching directives; creates fake complexity without student or product value.

---

## 4. Real-Time Price Alerts via Server-Sent Events (SSE)

### Context & Challenge
The Additional Feature requires notifying users when a player's quote crosses a target threshold (`ABOVE` or `BELOW`). The group chose **Server-Sent Events (SSE)** instead of WebSockets.

### Decision
- **Endpoint**: `GET /api/alerts/stream` returning `text/event-stream`.
- **Technology**: Spring `SseEmitter` managed by a dedicated thread-safe `SseEmitterService`.
- **Heartbeat & Lifecycle**:
  - 30-minute timeout per emitter (`timeout = 1800000L`).
  - Scheduled ping event every 30 seconds (`event: ping`) to prevent proxy/browser connection timeouts.
  - `onCompletion`, `onTimeout`, and `onError` callbacks clean up the subscriber registry.
- **Evaluation Mechanism**:
  - Listener on `QuotesRecalculatedEvent`:
    - Queries active alerts (`PriceAlert` with `status = 'ACTIVA'`).
    - Evaluates condition: `(condition == ABOVE && newPrice >= targetPrice) || (condition == BELOW && newPrice <= targetPrice)`.
    - If triggered:
      1. Updates alert status to `DISPARADA`, sets `triggeredAt = Instant.now()`.
      2. If the user has an active `SseEmitter`, sends SSE event `name: price-alert` with payload:
         ```json
         {
           "alertId": 12,
           "playerId": 45,
           "playerName": "Erling Haaland",
           "condition": "ABOVE",
           "targetPrice": 25.0,
           "currentPrice": 27.4,
           "triggeredAt": "2026-10-24T20:15:30Z"
         }
         ```

### Alternatives Considered
- *WebSocket STOMP*: Overkill for strictly server-to-client notifications. Requires complex SockJS fallback, handshake auth tokens, and session heartbeat protocols. SSE uses native HTTP headers (`Authorization: Bearer <JWT>`), works out-of-the-box with browser `EventSource`, and reconnects automatically.

---

## 5. Redis Caching & Invalidation Architecture

### Context & Challenge
High traffic on `GET /api/players` and `GET /api/players/ranking`, and frequent calls to external football data APIs, require caching to minimize database reads and insulate the backend against provider downtime.

### Decision
- **Infrastructure**: Spring Boot Starter Data Redis with Lettuce driver.
- **Cache Names & Keys**:
  - `players::catalog`: Stores paginated player catalog. TTL = 30 minutes.
  - `players::ranking`: Stores active ranking list. TTL = 1 hour.
  - `scraper::stats::{whoscoredId}`: Caches raw external stats. TTL = 24 hours.
- **Invalidation Strategy**:
  - Programmatic cache eviction via `CacheManager` / `@CacheEvict(cacheNames = {"players::catalog", "players::ranking"}, allEntries = true)` invoked immediately after:
    1. `POST /api/quotes/recalculate`
    2. Scheduled weekly catalog sync & recalculation
    3. `PATCH /api/admin/strategies/{id}/activate`
- **Fallback Behavior**:
  - If Redis is temporarily down, Spring Cache fallbacks gracefully without failing HTTP requests (configured via custom `CacheErrorHandler`).
  - If external scraper fails, cached stats or local PostgreSQL `player_statistics` are returned without throwing 500.

---

## 6. Testing Profiles Separation (`unit` vs `e2e`) & Testcontainers

### Context & Challenge
The teaching body discarded HSQLDB/H2 in favor of PostgreSQL for all persistent testing. We must provide independent Maven profiles: `unit` (fast, isolated, mocked) and `e2e` (real Spring context with PostgreSQL).

### Decision
- **Maven Configuration**:
  - Remove H2 dependency completely.
  - Add Testcontainers PostgreSQL (`org.testcontainers:postgresql:1.19.7`) and Testcontainers JUnit Jupiter.
  - Profiles in `pom.xml`:
    - `unit`:
      - Runs `maven-surefire-plugin` with `<includes><include>**/*Test.java</include></includes>` and `<excludes><include>**/*E2ETest.java</include><include>**/*IntegrationTest.java</include></excludes>`.
      - Pure unit tests with JUnit 5 & Mockito; no Spring context or container spinning.
    - `e2e`:
      - Runs `maven-failsafe-plugin` with `<includes><include>**/*E2ETest.java</include><include>**/*IntegrationTest.java</include></includes>`.
      - Base abstract class `AbstractE2ETest` configuring:
        ```java
        @Testcontainers
        @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
        public abstract class AbstractE2ETest {
            @Container
            static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
            // dynamic property source setting spring.datasource.*
        }
        ```
- **Execution Commands**:
  - Unit tests: `./mvnw test -Punit`
  - End-to-end tests: `./mvnw verify -Pe2e`

---

## 7. API Route Sanitation (Removal of `/v1`)

### Context & Challenge
The constitution v1.1.0 and user specifications mandate eliminating `/v1` across all endpoints to follow university standards.

### Decision
- Refactor controller annotations:
  - `@RequestMapping("/api/v1/auth")` $\to$ `@RequestMapping("/api/auth")`
  - `@RequestMapping("/api/v1/players")` $\to$ `@RequestMapping("/api/players")`
  - `@RequestMapping("/api/admin")` $\to$ `@RequestMapping("/api/admin")`
  - New controllers:
    - `@RequestMapping("/api/quotes")`
    - `@RequestMapping("/api/orders")`
    - `@RequestMapping("/api/users")`
    - `@RequestMapping("/api/alerts")`
- Keep Springdoc OpenAPI configuration pointing to `/v3/api-docs` and `/swagger-ui.html`.
