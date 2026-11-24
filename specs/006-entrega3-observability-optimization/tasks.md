# Tasks: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Input**: Design documents from `/specs/006-entrega3-observability-optimization/`  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/observability-and-metrics-api.md`, `quickstart.md`

**Tests**: Se incluyen tareas de tests unitarios, de arquitectura (ArchUnit) y E2E porque la especificación los exige explícitamente (`FR-001`, `SC-003`, `SC-004`, `SC-005`, `SC-007`).

**Organization**: Las tareas se agrupan por User Story (P1 → P2) para permitir implementación y validación incremental e independiente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencia de tareas incompletas)
- **[Story]**: User Story correspondiente (`US1` a `US6`)
- Todas las tareas incluyen rutas exactas de archivos en el repositorio.

---

## Phase 1: Setup (Dependencias e Infraestructura Base)

**Purpose**: Agregar las dependencias Maven requeridas para AOP, Prometheus y ArchUnit, y preparar archivos base de configuración y exclusión de logs.

- [ ] T001 Agregar dependencias `spring-boot-starter-aop`, `io.micrometer:micrometer-registry-prometheus` y `com.tngtech.archunit:archunit-junit5:1.3.0` (scope `test`) en `back/pom.xml`
- [ ] T002 [P] Excluir archivos y directorios de logs de auditoría (`logs/`, `back/logs/`, `*.log`) en `.gitignore`
- [ ] T003 [P] Crear configuración de scraping automático al backend (`cbo-backend:8080/actuator/prometheus`) en `monitoring/prometheus.yml`

---

## Phase 2: Foundational (Prerrequisitos Bloqueantes)

**Purpose**: Infraestructura transversal compartida por múltiples historias (índices de base de datos, Correlation ID en MDC y configuración base de Spring Boot Actuator).

**⚠️ CRITICAL**: Completar esta fase antes de iniciar las User Stories.

- [ ] T004 [P] Agregar índice `@Index(name = "idx_players_active", columnList = "active")` en `back/src/main/java/com/cbo/players/model/Player.java`
- [ ] T005 [P] Agregar índice compuesto `@Index(name = "idx_player_quotes_player_timestamp", columnList = "player_id, timestamp")` en `back/src/main/java/com/cbo/players/model/PlayerQuote.java`
- [ ] T006 [P] Agregar índice `@Index(name = "idx_transactions_operation_type", columnList = "operation_type")` en `back/src/main/java/com/cbo/players/model/Transaction.java`
- [ ] T007 Implementar filtro `CorrelationIdFilter` (`OncePerRequestFilter`, leyendo/generando `X-Correlation-ID`, poblando `MDC` y respondiendo cabecera `X-Correlation-ID`) en `back/src/main/java/com/cbo/players/audit/CorrelationIdFilter.java`
- [ ] T008 Actualizar `GlobalExceptionHandler` para leer el `correlationId` activo desde `MDC` (con fallback a UUID si no existe) en `back/src/main/java/com/cbo/players/exception/GlobalExceptionHandler.java`
- [ ] T009 Configurar bloque `management` (exposición `health,info,metrics,prometheus`, `show-details: always`, `probes.enabled: true`, tags `application`) e `info.app` (versión `3.0.0`) en `back/src/main/resources/application.yml`

**Checkpoint**: Base lista — las User Stories P1 pueden desarrollarse de forma independiente o en paralelo.

---

## Phase 3: User Story 1 - Optimización de Ranking de Alta Frecuencia y Caché Distribuida con Redis (Priority: P1) 🎯 MVP

**Goal**: Eliminar el problema N+1 en `GET /api/players/ranking`, consolidar Redis con invalidación consistente (recálculo + activación de estrategia), degradación elegante ante fallos y métricas de caché.

**Independent Test**: Invocar `GET /api/players/ranking` repetidas veces comprobando aciertos de caché en Redis (`< 50 ms`) y 0 consultas N+1; activar otra estrategia o recalcular cotizaciones verificando invalidación inmediata; detener Redis y verificar respuesta `200 OK` desde PostgreSQL.

### Implementation for User Story 1

- [ ] T010 [P] [US1] Agregar consulta `findByActiveTrue()` en `back/src/main/java/com/cbo/players/repository/PlayerRepository.java`
- [ ] T011 [P] [US1] Optimizar consulta batch de última cotización por jugador (resolviendo desempates de timestamp por `id DESC` sin N+1) en `back/src/main/java/com/cbo/players/repository/PlayerQuoteRepository.java`
- [ ] T012 [US1] Consolidar `RedisCacheConfig` con `ObjectMapper` dedicado con `JavaTimeModule`, `enableStatistics()`, TTLs configurables y `CacheErrorHandler` tolerante a fallos en `back/src/main/java/com/cbo/players/config/RedisCacheConfig.java`
- [ ] T013 [US1] Refactorizar `RankingService` para eliminar el loop N+1 usando las consultas batch de `T010`/`T011`, gestionar caché Redis con degradación elegante e instrumentar métricas Micrometer (`cbo_ranking_cache_requests_total` con tag `result=hit|miss` y `cbo_ranking_query_duration_seconds`) en `back/src/main/java/com/cbo/players/service/RankingService.java`
- [ ] T014 [US1] Agregar invalidación de caché `players::ranking` al activar una nueva estrategia en `back/src/main/java/com/cbo/players/service/ValuationStrategyService.java`
- [ ] T015 [US1] Inyectar variables `REDIS_HOST: cbo-redis`, `REDIS_PORT: 6379` y dependencia saludable `redis` al servicio `backend` en `docker-compose.yml`
- [ ] T016 [US1] Actualizar y ampliar tests unitarios de `RankingService` verificando eliminación de N+1 (1 llamada batch en lugar de N), registro de métricas hit/miss y fallback ante error de caché en `back/src/test/java/com/cbo/players/service/RankingServiceTest.java`

**Checkpoint**: User Story 1 (MVP) funcional, optimizada y testeada de forma independiente.

---

## Phase 4: User Story 2 - Auditoría Transversal de Web-Services con AOP y Correlation ID (Priority: P1)

**Goal**: Interceptar todas las invocaciones a controladores de negocio mediante Spring AOP y registrar `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` + `correlationId` + `outcome` en consola estructurada y en archivo rotativo `logs/audit.log`, enmascarando estrictamente datos sensibles.

**Independent Test**: Invocar endpoints públicos y autenticados (incluyendo `POST /api/auth/login` con contraseña y endpoints con error), verificando en consola y en `logs/audit.log` los 5 campos obligatorios, el `correlationId` propagado y la redacción `[REDACTED]` de contraseñas/tokens/API keys.

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar `AuditParameterSanitizer` para serializar argumentos de forma segura, enmascarar campos sensibles (`password`, `secret`, `token`, `apikey`, `authorization`, `credential`, `jwt`) con `[REDACTED]` y truncar payloads largos en `back/src/main/java/com/cbo/players/audit/AuditParameterSanitizer.java`
- [ ] T018 [P] [US2] Crear configuración Logback con appender de consola estructurada y `RollingFileAppender` dedicado (`logs/audit.log`, logger `AUDIT_LOGGER` con `additivity="false"`, rotación diaria y límite de tamaño) en `back/src/main/resources/logback-spring.xml`
- [ ] T019 [US2] Implementar aspecto AOP `WebServiceAuditAspect` (`@Around` sobre métodos públicos de `@RestController` en `com.cbo.players.controller..*`) registrando `<timestamp, correlationId, user, operation, params, executionTimeMs, outcome>` para casos exitosos y fallidos en `back/src/main/java/com/cbo/players/audit/WebServiceAuditAspect.java`
- [ ] T020 [P] [US2] Crear tests unitarios de redacción de secretos, objetos anidados, nulos y truncado en `back/src/test/java/com/cbo/players/audit/AuditParameterSanitizerTest.java`
- [ ] T021 [P] [US2] Crear tests unitarios de generación, propagación y limpieza de `MDC` para `X-Correlation-ID` en `back/src/test/java/com/cbo/players/audit/CorrelationIdFilterTest.java`
- [ ] T022 [US2] Crear tests unitarios de `WebServiceAuditAspect` verificando logging estructurado para usuario autenticado, usuario anónimo, excepciones y enmascaramiento de parámetros en `back/src/test/java/com/cbo/players/audit/WebServiceAuditAspectTest.java`

**Checkpoint**: User Story 2 funcional y verificable en consola y `logs/audit.log`.

---

## Phase 5: User Story 3 - Monitoreo Operativo con Endpoints de Salud y Métricas Prometheus (Priority: P1)

**Goal**: Exponer públicamente `/actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus`, instrumentar contadores de dominio en Prometheus y levantar el contenedor Prometheus en Docker Compose apuntando al backend.

**Independent Test**: Consultar `GET /actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` recibiendo `200 OK` sin autenticación; verificar presencia de métricas `cbo_*` y target `UP` en `http://localhost:9090/targets` con Docker Compose.

### Implementation for User Story 3

- [ ] T023 [US3] Actualizar `SecurityConfig` para permitir acceso público a `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/actuator/metrics`, `/actuator/metrics/**` y `/actuator/prometheus`, incluir `correlationId` (desde `MDC`) en `accessDeniedHandler` y registrar `CorrelationIdFilter` en la cadena de filtros en `back/src/main/java/com/cbo/players/security/SecurityConfig.java`
- [ ] T024 [P] [US3] Instrumentar contador Micrometer `cbo_orders_executed_total` (tag `type=buy|sell`) en `back/src/main/java/com/cbo/players/service/OrderService.java`
- [ ] T025 [P] [US3] Instrumentar contador Micrometer `cbo_quotes_recalculations_total` (tag `strategy=<code>`) en `back/src/main/java/com/cbo/players/service/QuoteCalculationService.java`
- [ ] T026 [US3] Agregar servicio `prometheus` (`prom/prometheus:v2.54.1`, puerto `9090`, volumen `./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro`, red `cbo_network`) en `docker-compose.yml`

**Checkpoint**: User Story 3 operativa con Actuator, Prometheus y métricas de dominio.

---

## Phase 6: User Story 4 - Suite Automatizada de Tests de Arquitectura (Priority: P1)

**Goal**: Garantizar mediante ArchUnit en el perfil `unit` (y CI) el cumplimiento estricto de la arquitectura por capas, ausencia de ciclos entre paquetes y prohibición de exponer entidades JPA desde controladores.

**Independent Test**: Ejecutar `./mvnw test -Punit` en `back/` comprobando que todas las reglas de `ArchitectureTest` pasan con 0 violaciones sin levantar Docker ni contexto de Spring.

### Implementation for User Story 4

- [ ] T027 [US4] Crear suite `ArchitectureTest` con ArchUnit (`@AnalyzeClasses`) validando: arquitectura en capas (`controller → service → repository` y `adapter`), prohibición de acceso directo `controller → repository/adapter`, prohibición de `repository → service/controller`, ausencia de dependencias cíclicas entre paquetes, no retorno de clases `@Entity` desde métodos públicos de controladores y convenciones de sufijo/paquete en `back/src/test/java/com/cbo/players/architecture/ArchitectureTest.java`
- [ ] T028 [US4] Ejecutar `ArchitectureTest` vía `./mvnw test -Punit -Dtest=ArchitectureTest` y corregir cualquier violación arquitectónica preexistente si existiera en `back/src/main/java/com/cbo/players/`

**Checkpoint**: Todos los requerimientos CORE técnicos P1 (US1, US2, US3, US4) implementados y verificados.

---

## Phase 7: User Story 5 - Endpoint de Métricas Avanzadas de Negocio y Sistema (Priority: P2)

**Goal**: Exponer `GET /api/metrics/advanced` restringido exclusivamente al `SUPERUSER`, consolidando métricas de mercado, valoración, alertas y rendimiento/caché con documentación OpenAPI/Swagger.

**Independent Test**: Invocar `GET /api/metrics/advanced` sin token (`401`), con usuario regular (`403 ProblemDetail ACCESS_DENIED`) y con superusuario (`200 OK` con bloques `market`, `valuation`, `alerts`, `performance` y `generatedAt`, incluyendo escenario sin operaciones sin división por cero).

### Implementation for User Story 5

- [ ] T029 [P] [US5] Crear DTOs de respuesta `TopTradedPlayerDto`, `MarketMetricsDto`, `ValuationMetricsDto`, `AlertMetricsDto`, `PerformanceMetricsDto` y `AdvancedMetricsResponseDto` en `back/src/main/java/com/cbo/players/dto/response/`
- [ ] T030 [P] [US5] Agregar consultas agregadas JPQL (conteo por tipo de operación, suma de créditos negociados y Top 5 jugadores por volumen de tokens) en `back/src/main/java/com/cbo/players/repository/TransactionRepository.java`
- [ ] T031 [P] [US5] Agregar consulta agregada de tokens en circulación (excluyendo al superusuario) en `back/src/main/java/com/cbo/players/repository/PortfolioPositionRepository.java`
- [ ] T032 [P] [US5] Agregar consulta de conteo por estado `countByStatus(AlertStatus status)` en `back/src/main/java/com/cbo/players/repository/PriceAlertRepository.java`
- [ ] T033 [US5] Implementar `AdvancedMetricsService` consolidando métricas de mercado, valoración (últimas cotizaciones + estrategia activa), alertas y telemetría de `MeterRegistry` (cache hits/misses, hit ratio y latencia promedio del ranking, con protección contra división por cero) en `back/src/main/java/com/cbo/players/service/AdvancedMetricsService.java`
- [ ] T034 [US5] Implementar `AdvancedMetricsController` exponiendo `GET /api/metrics/advanced` con anotaciones Swagger/OpenAPI en `back/src/main/java/com/cbo/players/controller/AdvancedMetricsController.java`
- [ ] T035 [US5] Restringir `/api/metrics/**` exclusivamente a `hasRole("SUPERUSER")` y personalizar mensaje `403 ProblemDetail` en español en `back/src/main/java/com/cbo/players/security/SecurityConfig.java`
- [ ] T036 [US5] Crear tests unitarios de `AdvancedMetricsService` cubriendo escenario con datos completos y escenario vacío (división por cero segura) en `back/src/test/java/com/cbo/players/service/AdvancedMetricsServiceTest.java`

**Checkpoint**: User Story 5 funcional, segura y documentada en Swagger.

---

## Phase 8: Polish, Integración E2E y Validación Final

**Purpose**: Verificación integral E2E sobre PostgreSQL (Testcontainers), ejecución completa de suites `unit` y `e2e`, verificación de arquitectura tras los últimos cambios y control de calidad.

- [ ] T037 Crear suite de integración `ObservabilityAndMetricsE2ETest` verificando `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, `GET /api/metrics/advanced` (`200`/`403`/`401`), cabecera `X-Correlation-ID` y optimización/consistencia de `GET /api/players/ranking` en `back/src/test/java/com/cbo/players/integration/ObservabilityAndMetricsE2ETest.java`
- [ ] T038 Ejecutar suite completa unitaria y de arquitectura (`./mvnw test -Punit`) y suite E2E (`./mvnw test -Pe2e`) en `back/`, verificando 100% de tests aprobados y 0 issues introducidos de calidad/seguridad
- [ ] T039 Validar escenarios de `specs/006-entrega3-observability-optimization/quickstart.md` (incluyendo verificación de `docker compose config`, formato de `logs/audit.log` y Swagger `/v3/api-docs`)

---

## Phase 9: User Story 6 - Tag en GitHub y Confección de Release Notes de Entrega 3 (Priority: P2)

**Goal**: Documentar formalmente las Release Notes de la Entrega 3 y cerrar la versión con el Tag semántico `v3.0.0` y Release en GitHub una vez validado todo el trabajo.

**Independent Test**: Confirmar existencia de `docs/release-notes-v3.0.0.md` completo, build/tests en verde, tag anotado `v3.0.0` creado en Git y Release publicado en GitHub.

- [ ] T040 [US6] Confeccionar las Release Notes formales de la Entrega 3 (resumen ejecutivo, novedades CORE y funcionales, nuevos endpoints, observabilidad, rendimiento, estado de tests/arquitectura/SonarCloud y guía de verificación) en `docs/release-notes-v3.0.0.md`
- [ ] T041 [US6] Crear tag anotado `v3.0.0` en Git apuntando al commit verificado de Entrega 3 y documentar/ejecutar la publicación de la Release en GitHub con el contenido de `docs/release-notes-v3.0.0.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Sin dependencias — iniciar primero.
- **Phase 2 (Foundational)**: Depende de Phase 1 — BLOQUEA las User Stories.
- **Phase 3 (US1 - Ranking & Redis)**: Depende de Phase 2.
- **Phase 4 (US2 - Auditoría AOP)**: Depende de Phase 2 (`T001`, `T007`, `T008`). Independiente de US1.
- **Phase 5 (US3 - Actuator & Prometheus)**: Depende de Phase 2 (`T001`, `T003`, `T009`).
- **Phase 6 (US4 - Architecture Tests)**: Depende de Phase 1 (`T001`) y conviene ejecutar tras US1–US3 y re-validar al final tras US5.
- **Phase 7 (US5 - Advanced Metrics)**: Depende de Phase 2 y aprovecha las métricas `cbo_ranking_*` introducidas en US1 (`T013`).
- **Phase 8 (Polish & E2E)**: Depende de US1–US5 completas.
- **Phase 9 (US6 - Release Notes & Tag)**: Depende de Phase 8 (solo etiquetar sobre versión 100% verificada).

### Parallel Opportunities
- **Setup**: `T002` y `T003` en paralelo con `T001`.
- **Foundational**: `T004`, `T005`, `T006` (índices JPA) en paralelo entre sí; `T007` + `T009` en paralelo.
- **US1**: `T010` y `T011` en paralelo antes de `T013`.
- **US2**: `T017` y `T018` en paralelo antes de `T019`; `T020` y `T021` en paralelo.
- **US3**: `T024` y `T025` en paralelo con `T023` y `T026`.
- **US5**: `T029`, `T030`, `T031`, `T032` en paralelo antes de `T033`.

### Parallel Example: User Story 5
```text
# Crear DTOs y consultas agregadas en paralelo:
T029 [P] [US5] DTOs en back/src/main/java/com/cbo/players/dto/response/
T030 [P] [US5] Agregaciones en TransactionRepository.java
T031 [P] [US5] Agregación en PortfolioPositionRepository.java
T032 [P] [US5] Conteo por estado en PriceAlertRepository.java

# Luego implementar servicio, controlador, seguridad y tests:
T033 [US5] AdvancedMetricsService.java
T034 [US5] AdvancedMetricsController.java
T035 [US5] SecurityConfig.java
T036 [US5] AdvancedMetricsServiceTest.java
```

---

## Implementation Strategy

1. **MVP Incremental (Phases 1 → 3)**: Dependencias + índices + `CorrelationIdFilter` + Optimización de Ranking con Redis (US1).
2. **Núcleo de Observabilidad y Gobernanza (Phases 4 → 6)**: Auditoría AOP + Logback (US2) → Actuator + Prometheus + Docker (US3) → ArchUnit (US4).
3. **Funcionalidad Avanzada y Validación (Phases 7 → 8)**: `GET /api/metrics/advanced` (US5) → Suite E2E completa y validación de `quickstart.md`.
4. **Cierre Formal de TP (Phase 9)**: Release Notes (`docs/release-notes-v3.0.0.md`) + Tag `v3.0.0` y Release en GitHub (US6).
