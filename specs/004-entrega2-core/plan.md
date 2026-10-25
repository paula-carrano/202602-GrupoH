# Implementation Plan: Entrega 2 Backend Core & Real-Time Engine

**Branch**: `feature/004-entrega2-core-update` | **Date**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-entrega2-core/spec.md`

---

## Summary

This plan details the implementation of the complete backend core for **Entrega 2** of the Football Player Token Market platform. The scope encompasses:
1. Configurable valuation strategy engine with centralized superuser activation (`PATCH /api/admin/strategies/{id}/activate`) and global recalculation (`POST /api/quotes/recalculate`).
2. High-integrity token market (`POST /api/orders/buy`, `POST /api/orders/sell`) guaranteeing the invariant of 100 issued tokens per player via pessimistic write locking, accompanied by simulated credit top-up (`POST /api/users/{id}/credits/topup`) and an immutable transaction ledger.
3. Paginated historical quotes (`GET /api/players/{id}/quotes`) and date-specific queries (`?date=YYYY-MM-DD`).
4. Real-time Price Alerts Engine (Additional Feature) pushing instant notifications to subscribed web clients via Server-Sent Events (`GET /api/alerts/stream` using Spring `SseEmitter`).
5. Redis high-performance caching for players catalog and ranking (`GET /api/players/ranking`), with automatic invalidation upon recalculation.
6. Core infrastructure sanitation: Separation of Maven testing profiles into `unit` (isolated, fast mocks) and `e2e` (PostgreSQL via Testcontainers), elimination of H2, and removal of the `/v1` path prefix across all controllers.

---

## Technical Context

**Language/Version**: Java 17 (OpenJDK 17 LTS)  
**Primary Dependencies**:
- Spring Boot 3.2.3 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
- Spring Data Redis (`spring-boot-starter-data-redis` with Lettuce driver)
- JJWT 0.12.5 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- Springdoc OpenAPI 2.3.0 (`springdoc-openapi-starter-webmvc-ui`)
- Testcontainers 1.19.7 (`testcontainers`, `testcontainers-postgresql`)  
**Storage**: PostgreSQL 15+ (Production & Testcontainers), Redis 7+ (Caching)  
**Testing**: JUnit 5, Mockito, Spring Security Test, Testcontainers PostgreSQL (Maven profiles: `unit` & `e2e`)  
**Target Platform**: Docker / Linux container runtime on port 8080  
**Project Type**: Multi-tier Web REST API Service with asynchronous Server-Sent Events (SSE) push streaming  
**Performance Goals**:
- Latency `< 50ms` for ranking queries served via Redis cache
- Latency `< 2s` from quote recalculation to SSE event arrival in active browser sessions  
**Constraints**:
- Strict invariant of 100 tokens per player circulating between superuser and user wallets
- Strictly immutable transaction audit records (no updates or deletes permitted)
- All functional error messages formatted in Spanish using `ProblemDetail` (RFC 7807) with stable alphanumeric error codes
- Zero dependency on HSQLDB/H2 in persistent environments  
**Scale/Scope**: 5 European football leagues, ~500 players, transactional order matching, real-time push streaming

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Evaluation & Compliance Evidence |
|---|---|---|
| **1. Layered Architecture** | **PASS** | Strict separation: `controller` $\to$ `service` $\to$ `repository` $\to$ `adapter`. Domain rules only in service/domain classes. |
| **2. DTO Isolation** | **PASS** | Controllers expose and consume dedicated DTOs (`*RequestDto`, `*ResponseDto`). JPA entities never leak across web boundaries. |
| **3. REST Routes without `/v1`** | **PASS** | All routes mapped under `/api/auth`, `/api/players`, `/api/quotes`, `/api/orders`, `/api/users`, `/api/alerts`, `/api/admin`. |
| **4. Spanish Error Messages (RFC 7807)** | **PASS** | `GlobalExceptionHandler` produces standard `ProblemDetail` responses in Spanish with stable error codes (e.g. `INSUFFICIENT_FUNDS`, `ACCESS_DENIED`). |
| **5. Security & Roles** | **PASS** | Spring Security with JWT Bearer validation. Admin/Superuser operations (`PATCH /api/admin/strategies/{id}/activate`) guarded by role checks returning 403 Forbidden on violation. |
| **6. Persistence & Testing Standards** | **PASS** | H2 eliminated. Profiles `unit` (Surefire, mocks) and `e2e` (Failsafe, Testcontainers PostgreSQL) cleanly decoupled in `pom.xml`. |
| **7. Financial & Token Invariant** | **PASS** | Pessimistic locking on token positions, atomic balance/position updates, immutable `Transaction` records with full audit fields. |
| **8. Real-Time Streaming** | **PASS** | SSE adopted via Spring `SseEmitter` (`GET /api/alerts/stream`), strictly server-to-client push with JWT authentication. |
| **9. Redis Caching & Invalidation** | **PASS** | Caches configured for catalog and ranking, explicitly evicted upon recalculation or strategy activation. |

---

## Project Structure

### Documentation (this feature)

```text
specs/004-entrega2-core/
├── spec.md              # Feature specification
├── plan.md              # Implementation plan (this file)
├── research.md          # Phase 0 technical research & architecture decisions
├── data-model.md        # Phase 1 data entities, relationships & lifecycle
├── quickstart.md        # Phase 1 end-to-end validation guide
├── contracts/           # Phase 1 API specifications
│   ├── admin-strategy-contracts.md
│   ├── quotes-contracts.md
│   ├── orders-portfolio-contracts.md
│   └── alerts-sse-contracts.md
├── checklists/
│   └── requirements.md  # Quality checklist
└── tasks.md             # Phase 2 task breakdown (created by /speckit-tasks)
```

### Source Code Layout (`back/`)

```text
back/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/cbo/players/
│   │   │   ├── PlayersMarketApplication.java
│   │   │   ├── adapter/
│   │   │   │   ├── ScrapingPort.java
│   │   │   │   ├── ScrapingRestAdapter.java
│   │   │   │   └── dto/
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── RedisCacheConfig.java             # [NEW] Redis template & cache manager
│   │   │   │   └── AsyncConfig.java                  # [NEW] Thread pool for SSE & recalculation
│   │   │   ├── controller/
│   │   │   │   ├── AdminStrategyController.java      # [NEW] PATCH /api/admin/strategies/{id}/activate
│   │   │   │   ├── AlertController.java              # [NEW] /api/alerts & /api/alerts/stream (SSE)
│   │   │   │   ├── AuthController.java               # [REFACTOR] Remove /v1 prefix
│   │   │   │   ├── OrderController.java              # [NEW] POST /api/orders/buy & /sell
│   │   │   │   ├── PlayerController.java             # [REFACTOR] Remove /v1 prefix, add pagination to quotes
│   │   │   │   ├── QuoteController.java              # [NEW] POST /api/quotes/recalculate, ranking
│   │   │   │   └── UserController.java               # [NEW] GET portfolio, transactions, topup
│   │   │   ├── dto/
│   │   │   │   ├── request/                          # OrderRequest, AlertRequest, TopupRequest
│   │   │   │   └── response/                         # OrderResponse, PortfolioResponse, AlertResponse, QuoteResponse
│   │   │   ├── event/                                # [NEW] QuotesRecalculatedEvent
│   │   │   ├── exception/
│   │   │   │   ├── ErrorCode.java                    # [EXTENDED] INSUFFICIENT_FUNDS, etc.
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── model/
│   │   │   │   ├── Player.java
│   │   │   │   ├── PlayerQuote.java                  # [EXTENDED] strategyName, strategyVersion
│   │   │   │   ├── PlayerStatistics.java
│   │   │   │   ├── PortfolioPosition.java            # [NEW] Token holdings per user
│   │   │   │   ├── PriceAlert.java                   # [NEW] Alert thresholds
│   │   │   │   ├── Transaction.java                  # [NEW] Immutable audit log
│   │   │   │   ├── User.java                         # [EXTENDED] creditsBalance, ROLE_SUPERUSER
│   │   │   │   ├── UserRole.java                     # [EXTENDED] ROLE_SUPERUSER
│   │   │   │   └── ValuationStrategy.java            # [NEW] Configurable formulas & weights
│   │   │   ├── repository/
│   │   │   │   ├── PlayerQuoteRepository.java        # [EXTENDED] Paged quotes, date query
│   │   │   │   ├── PlayerRepository.java
│   │   │   │   ├── PortfolioPositionRepository.java  # [NEW] With pessimistic write lock
│   │   │   │   ├── PriceAlertRepository.java         # [NEW] Active alert queries
│   │   │   │   ├── TransactionRepository.java        # [NEW] Read/insert only
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── ValuationStrategyRepository.java  # [NEW] Active strategy finder
│   │   │   ├── security/
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   ├── SecurityConfig.java               # [UPDATED] Secure routes without /v1
│   │   │   │   └── UserPrincipal.java
│   │   │   └── service/
│   │   │       ├── AlertService.java                 # [NEW] Alert evaluation & lifecycle
│   │   │       ├── AlertStreamService.java           # [NEW] SseEmitter subscriber management
│   │   │       ├── OrderService.java                 # [NEW] Transactional buy/sell execution
│   │   │       ├── PortfolioService.java             # [NEW] Portfolio & PnL calculation
│   │   │       ├── QuoteCalculationService.java      # [NEW] Formula execution & batch recalculate
│   │   │       ├── ValuationStrategyService.java     # [NEW] Strategy activation & weights
│   │   │       └── WalletService.java                # [NEW] Credit top-up & balance validation
│   │   └── resources/
│   │       └── application.yml                       # [UPDATED] Redis & profiles config
│   └── test/
│       ├── java/com/cbo/players/
│       │   ├── unit/                                 # Unit tests (Services, Formulas, Controllers)
│       │   └── e2e/                                  # E2E tests (AbstractE2ETest with Testcontainers)
│       └── resources/
│           ├── application-unit.yml
│           └── application-e2e.yml
```

**Structure Decision**: Standard Spring Boot single-backend architecture partitioned cleanly into layered packages. Test suites are organized to mirror the `unit` and `e2e` Maven profile definitions.

---

## Complexity Tracking

> **Constitution check violations requiring justification**: None. All design choices strictly adhere to the project constitution.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *None* | N/A | N/A |
