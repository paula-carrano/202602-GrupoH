# Tasks: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Input**: Design documents from `/specs/006-entrega3-observability-optimization/`  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/observability-and-metrics-api.md`, `quickstart.md`

**Tests**: Se incluyen tareas de tests unitarios, de arquitectura (ArchUnit), E2E (Testcontainers) y Vitest porque la especificación los exige explícitamente (`FR-001`, `FR-002`, `FR-013`, `SC-001`, `SC-003`, `SC-004`, `SC-005`, `SC-006`, `SC-007`).

**Organization**: Las tareas se agrupan por User Story (P1 → P2) para permitir implementación y validación incremental e independiente.

> [!IMPORTANT]
> **RESTRICCIÓN CRÍTICA DE FRONTEND (MÉTRICAS AVANZADAS):**  
> Todas las tareas de frontend básico para `GET /metrics/advanced` (`T040`–`T044`) **DEBEN** tomar el frontend actual (`front/`) únicamente como referencia visual y arquitectónica, implementándose en archivos nuevos dedicados y **SIN tocar ningún archivo actual del frontend a menos que sea sumamente importante / estrictamente indispensable** (como máximo el registro mínimo de la ruta en `front/src/App.jsx`, sin alterar componentes, estilos, servicios ni tests existentes).

> [!IMPORTANT]
> **CONVENCIÓN DE RUTAS REST (SIN `/api` NI `/v1`):**  
> Ningún endpoint nuevo ni especificado debe llevar el prefijo `/api/` ni `/v1/` (`GET /metrics/advanced`, `GET /players/ranking`, `GET /players`, `POST /quotes/recalculate`, `PATCH /admin/strategies/{id}/activate`).

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

**Purpose**: Infraestructura transversal compartida por múltiples historias (índices de base de datos, Correlation ID activo desde el primer filtro vía MDC y configuración base de Spring Boot Actuator).

**⚠️ CRITICAL**: Completar esta fase antes de iniciar las User Stories.

- [ ] T004 [P] Agregar índice `@Index(name = "idx_players_active", columnList = "active")` en `back/src/main/java/com/cbo/players/model/Player.java`
- [ ] T005 [P] Agregar índice compuesto `@Index(name = "idx_player_quotes_player_timestamp", columnList = "player_id, timestamp")` en `back/src/main/java/com/cbo/players/model/PlayerQuote.java`
- [ ] T006 [P] Agregar índice `@Index(name = "idx_transactions_operation_type", columnList = "operation_type")` en `back/src/main/java/com/cbo/players/model/Transaction.java`
- [ ] T007 Implementar y registrar filtro `CorrelationIdFilter` (`@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE)`, extendiendo `OncePerRequestFilter`, leyendo/generando `X-Correlation-ID`, poblando `MDC`, enviando cabecera de respuesta `X-Correlation-ID` y limpiando `MDC` en `finally`) para que quede activo desde Phase 2 sin depender de US3 en `back/src/main/java/com/cbo/players/audit/CorrelationIdFilter.java`
- [ ] T008 Actualizar `GlobalExceptionHandler` para leer el `correlationId` activo desde `MDC` (con fallback a UUID si no existe) en `back/src/main/java/com/cbo/players/exception/GlobalExceptionHandler.java`
- [ ] T009 Configurar bloque `management` (exposición `health,info,metrics,prometheus`, `show-details: always`, `probes.enabled: true`, tags `application`), TTLs configurables de caché Redis (`ranking`, `catalog`, `external`) e `info.app` (versión `3.0.0`) en `back/src/main/resources/application.yml`

**Checkpoint**: Base lista — las User Stories P1 pueden desarrollarse de forma independiente o en paralelo.

---

## Phase 3: User Story 1 - Optimización de Ranking, Caché Redis Completa y Scheduler de Scraping (Priority: P1) 🎯 MVP

**Goal**: Eliminar el problema N+1 en `GET /players/ranking`, implementar clave de caché por estrategia y protección anti-*cache stampede*, consolidar caché Redis completa (ranking, catálogo y proveedores externos con degradación elegante `200 OK`) y verificar/garantizar que el scheduler de scraping actualice correctamente los datos de jugadores en base de datos.

**Independent Test**: Invocar `GET /players/ranking` repetidas veces comprobando hit ratio `> 90%`, latencia `< 50 ms` y 0 consultas N+1; activar otra estrategia o recalcular verificando invalidación inmediata; simular caída de Redis verificando `200 OK` en `GET /players/ranking` y `GET /players`; y ejecutar/verificar el scheduler de scraping comprobando la actualización persistida de jugadores/estadísticas en PostgreSQL e invalidación de cachés antes del recálculo.

### Implementation for User Story 1

- [ ] T010 [P] [US1] Verificar/garantizar consulta indexada de jugadores activos (`findAllByActiveTrue()`) en `back/src/main/java/com/cbo/players/repository/PlayerRepository.java`
- [ ] T011 [P] [US1] Optimizar consulta batch de última cotización por jugador (resolviendo desempates de timestamp por `id DESC` sin N+1) en `back/src/main/java/com/cbo/players/repository/PlayerQuoteRepository.java`
- [ ] T012 [US1] Consolidar `RedisCacheConfig` con `ObjectMapper` dedicado con `JavaTimeModule`, `enableStatistics()`, cachés configurables (`players::ranking`, `players::catalog`, `players::external`) y `CacheErrorHandler` tolerante a fallos ante caída de Redis en `back/src/main/java/com/cbo/players/config/RedisCacheConfig.java`
- [ ] T013 [US1] Refactorizar `RankingService` para eliminar el loop N+1 usando las consultas batch de `T010`/`T011`, usar clave de caché vinculada a estrategia activa y versión (`active_ranking:<strategyCode>:v<version>`), aplicar protección sincronizada contra *cache stampede*, mantener degradación elegante a PostgreSQL ante fallo de Redis e instrumentar métricas Micrometer (`cbo_ranking_cache_requests_total` con tag `result=hit|miss` y `cbo_ranking_query_duration_seconds`) en `back/src/main/java/com/cbo/players/service/RankingService.java`
- [ ] T014 [P] [US1] Consolidar caché Redis `players::catalog` (`@Cacheable` con fallback transparente ante error de caché) en `back/src/main/java/com/cbo/players/service/PlayerService.java` e invalidación de `players::catalog` y `players::ranking` al persistir planteles/estadísticas en `back/src/main/java/com/cbo/players/service/CatalogPersistenceService.java`
- [ ] T015 [P] [US1] Incorporar caché Redis `players::external` y fallback resiliente ante indisponibilidad de proveedores externos (WhoScored / Football-Data.org) en `back/src/main/java/com/cbo/players/adapter/ScrapingRestAdapter.java`
- [ ] T016 [US1] Agregar invalidación de caché `players::ranking` al activar una nueva estrategia en `back/src/main/java/com/cbo/players/service/ValuationStrategyService.java`
- [ ] T017 [US1] Verificar y corregir el flujo del scheduler de scraping (`CatalogScheduler` + `CatalogSyncService`) para garantizar que actualice correctamente los datos y estadísticas de los jugadores en la base de datos PostgreSQL, invalide las cachés afectadas al completar la persistencia y ejecute el recálculo de cotizaciones una vez persistidos los datos actualizados (eliminando condición de carrera asíncrona) en `back/src/main/java/com/cbo/players/service/CatalogScheduler.java` y `back/src/main/java/com/cbo/players/service/CatalogSyncService.java`
- [ ] T018 [US1] Inyectar variables `REDIS_HOST: cbo-redis`, `REDIS_PORT: 6379` y dependencia saludable `redis` al servicio `backend` en `docker-compose.yml`
- [ ] T019 [P] [US1] Actualizar y ampliar tests unitarios de `RankingService` verificando eliminación de N+1 (1 llamada batch), clave por estrategia/versión, protección contra *cache stampede*, registro de métricas `hit`/`miss` (hit ratio `> 90%` en lecturas repetidas — `SC-001`) y fallback `200 OK` ante error de Redis (`SC-006`) en `back/src/test/java/com/cbo/players/service/RankingServiceTest.java`
- [ ] T020 [P] [US1] Crear tests unitarios verificando caché `players::catalog`, caché/fallback de proveedor externo (`FR-002`) y degradación elegante ante fallo de Redis (`SC-006`) en `back/src/test/java/com/cbo/players/service/PlayerServiceCacheTest.java`
- [ ] T021 [P] [US1] Crear tests unitarios/integración verificando que `CatalogScheduler` + `CatalogSyncService` actualizan correctamente los datos y estadísticas de jugadores en base de datos, invalidan cachés y recalculan cotizaciones tras la persistencia (`FR-013`) en `back/src/test/java/com/cbo/players/service/CatalogSchedulerTest.java`

**Checkpoint**: User Story 1 (MVP) funcional, optimizada, con cobertura completa de `FR-002`, `FR-003`, `FR-004`, `FR-013`, `SC-001` y `SC-006`.

---

## Phase 4: User Story 2 - Auditoría Transversal de Web-Services con AOP y Correlation ID (Priority: P1)

**Goal**: Interceptar todas las invocaciones a controladores de negocio mediante Spring AOP y registrar `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` + `correlationId` + `outcome` en consola estructurada y en archivo rotativo `logs/audit.log`, enmascarando estrictamente datos sensibles.

**Independent Test**: Invocar endpoints públicos y autenticados (incluyendo `POST /auth/login` con contraseña y endpoints con error), verificando en consola y en `logs/audit.log` los 5 campos obligatorios, el `correlationId` propagado desde `CorrelationIdFilter` (ya registrado en Phase 2) y la redacción `[REDACTED]` de contraseñas/tokens/API keys.

### Implementation for User Story 2

- [ ] T022 [P] [US2] Implementar `AuditParameterSanitizer` para serializar argumentos de forma segura, enmascarar campos sensibles (`password`, `secret`, `token`, `apikey`, `authorization`, `credential`, `jwt`) con `[REDACTED]` y truncar payloads largos (> 500 caracteres) en `back/src/main/java/com/cbo/players/audit/AuditParameterSanitizer.java`
- [ ] T023 [P] [US2] Crear configuración Logback con appender de consola estructurada y `RollingFileAppender` dedicado (`logs/audit.log`, logger `AUDIT_LOGGER` con `additivity="false"`, rotación diaria y límite de tamaño) en `back/src/main/resources/logback-spring.xml`
- [ ] T024 [US2] Implementar aspecto AOP `WebServiceAuditAspect` (`@Around` sobre métodos públicos de `@RestController` en `com.cbo.players.controller..*`) registrando `<timestamp, correlationId, user, operation, params, executionTimeMs, outcome>` para casos exitosos y fallidos en `back/src/main/java/com/cbo/players/audit/WebServiceAuditAspect.java`
- [ ] T025 [P] [US2] Crear tests unitarios de redacción de secretos, objetos anidados, nulos y truncado en `back/src/test/java/com/cbo/players/audit/AuditParameterSanitizerTest.java`
- [ ] T026 [P] [US2] Crear tests unitarios de generación, propagación y limpieza de `MDC` para `X-Correlation-ID` en `back/src/test/java/com/cbo/players/audit/CorrelationIdFilterTest.java`
- [ ] T027 [US2] Crear tests unitarios de `WebServiceAuditAspect` verificando logging estructurado para usuario autenticado, usuario anónimo, excepciones y enmascaramiento de parámetros en `back/src/test/java/com/cbo/players/audit/WebServiceAuditAspectTest.java`

**Checkpoint**: User Story 2 funcional, 100% independiente de US3 y verificable en consola y `logs/audit.log`.

---

## Phase 5: User Story 3 - Monitoreo Operativo con Endpoints de Salud y Métricas Prometheus (Priority: P1)

**Goal**: Exponer públicamente `/actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus`, instrumentar contadores de dominio en Prometheus y levantar el contenedor Prometheus en Docker Compose apuntando al backend.

**Independent Test**: Consultar `GET /actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` recibiendo `200 OK` sin autenticación; verificar presencia de métricas `cbo_*` y target `UP` en `http://localhost:9090/targets` con Docker Compose.

### Implementation for User Story 3

- [ ] T028 [US3] Actualizar `SecurityConfig` para permitir acceso público a `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/actuator/metrics`, `/actuator/metrics/**` y `/actuator/prometheus` e incluir `correlationId` (desde `MDC`) en `accessDeniedHandler` en `back/src/main/java/com/cbo/players/security/SecurityConfig.java`
- [ ] T029 [P] [US3] Instrumentar contador Micrometer `cbo_orders_executed_total` (tag `type=buy|sell`) en `back/src/main/java/com/cbo/players/service/OrderService.java`
- [ ] T030 [P] [US3] Instrumentar contador Micrometer `cbo_quotes_recalculations_total` (tag `strategy=<code>`) en `back/src/main/java/com/cbo/players/service/QuoteCalculationService.java`
- [ ] T031 [US3] Agregar servicio `prometheus` (`prom/prometheus:v2.54.1`, puerto `9090`, volumen `./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro`, red `cbo_network`) en `docker-compose.yml`

**Checkpoint**: User Story 3 operativa con Actuator, Prometheus y métricas de dominio.

---

## Phase 6: User Story 4 - Suite Automatizada de Tests de Arquitectura (Priority: P1)

**Goal**: Garantizar mediante ArchUnit en el perfil `unit` (y CI) el cumplimiento estricto de la arquitectura por capas, ausencia de ciclos entre paquetes y prohibición de exponer entidades JPA desde controladores.

**Independent Test**: Ejecutar `./mvnw test -Punit` en `back/` comprobando que todas las reglas de `ArchitectureTest` pasan con 0 violaciones sin levantar Docker ni contexto de Spring.

### Implementation for User Story 4

- [ ] T032 [US4] Crear suite `ArchitectureTest` con ArchUnit (`@AnalyzeClasses`) validando: arquitectura en capas (`controller → service → repository` y `adapter`), prohibición de acceso directo `controller → repository/adapter`, prohibición de `repository → service/controller`, ausencia de dependencias cíclicas entre paquetes, no retorno de clases `@Entity` desde métodos públicos de controladores y convenciones de sufijo/paquete en `back/src/test/java/com/cbo/players/architecture/ArchitectureTest.java`
- [ ] T033 [US4] Ejecutar `ArchitectureTest` vía `./mvnw test -Punit -Dtest=ArchitectureTest` y corregir cualquier violación arquitectónica preexistente si existiera en `back/src/main/java/com/cbo/players/`

**Checkpoint**: Todos los requerimientos CORE técnicos P1 (US1, US2, US3, US4) implementados y verificados.

---

## Phase 7: User Story 5 - Endpoint `GET /metrics/advanced` + Vista Básica Aislada en Frontend (Priority: P2)

**Goal**: Exponer `GET /metrics/advanced` (sin `/api` ni `/v1`) restringido exclusivamente al `SUPERUSER` con `generatedAt` en nivel raíz y documentación OpenAPI/Swagger, y construir una vista básica en frontend tomando `front/` solo como referencia sin modificar archivos existentes salvo lo estrictamente indispensable.

**Independent Test**: Invocar `GET /metrics/advanced` sin token (`401`), con usuario regular (`403 ProblemDetail ACCESS_DENIED`) y con superusuario (`200 OK` con `market`, `valuation`, `alerts`, `performance` y `generatedAt` en raíz); validar la vista básica en Vitest (`advanced-metrics.test.jsx`) y confirmar con `git diff front/` que no se tocaron archivos existentes salvo el mínimo indispensable.

### Implementation for User Story 5 — Backend

- [ ] T034 [P] [US5] Crear DTOs de respuesta `TopTradedPlayerDto`, `MarketMetricsDto`, `ValuationMetricsDto`, `AlertMetricsDto`, `PerformanceMetricsDto` y `AdvancedMetricsResponseDto` (con `generatedAt` en el nivel raíz) en `back/src/main/java/com/cbo/players/dto/response/`
- [ ] T035 [P] [US5] Agregar consultas agregadas JPQL (conteo por tipo de operación, suma de créditos negociados y Top 5 jugadores por volumen de tokens) en `back/src/main/java/com/cbo/players/repository/TransactionRepository.java`
- [ ] T036 [P] [US5] Agregar consulta agregada de tokens en circulación (excluyendo al superusuario) en `back/src/main/java/com/cbo/players/repository/PortfolioPositionRepository.java`
- [ ] T037 [P] [US5] Agregar consulta de conteo por estado `countByStatus(AlertStatus status)` en `back/src/main/java/com/cbo/players/repository/PriceAlertRepository.java`
- [ ] T038 [US5] Implementar `AdvancedMetricsService` consolidando métricas de mercado, valoración (últimas cotizaciones + estrategia activa), alertas, telemetría de `MeterRegistry` (cache hits/misses, hit ratio y latencia promedio del ranking, con protección contra división por cero) y `generatedAt` en nivel raíz en `back/src/main/java/com/cbo/players/service/AdvancedMetricsService.java`
- [ ] T039 [US5] Implementar `AdvancedMetricsController` exponiendo `GET /metrics/advanced` (sin prefijo `/api` ni `/v1`) con anotaciones Swagger/OpenAPI en `back/src/main/java/com/cbo/players/controller/AdvancedMetricsController.java`, restringir `/metrics/**` exclusivamente a `hasRole("SUPERUSER")` con mensaje `403 ProblemDetail` en español en `back/src/main/java/com/cbo/players/security/SecurityConfig.java`, y crear `back/src/test/java/com/cbo/players/service/AdvancedMetricsServiceTest.java`

### Implementation for User Story 5 — Frontend Básico (Regla Estricta Zero/Minimal-Touch)

> **Recordatorio obligatorio:** Usar el frontend actual (`front/`) **únicamente como referencia visual y arquitectónica**. NO modificar ningún archivo existente en `front/` a menos que sea sumamente importante / estrictamente indispensable.

- [ ] T040 [P] [US5] Crear servicio HTTP nuevo y aislado para consumir `GET /metrics/advanced` (tomando `front/src/services/api.js` y `session.js` como referencia sin modificarlos) en `front/src/services/advancedMetrics.js`
- [ ] T041 [P] [US5] Crear componente de presentación nuevo `AdvancedMetricsPanel` renderizando los bloques `market`, `valuation`, `alerts`, `performance` y `generatedAt` con las clases/estilos de referencia de `front/src/style.css` (sin modificar `style.css` ni componentes existentes) en `front/src/components/AdvancedMetricsPanel.jsx`
- [ ] T042 [US5] Crear página nueva `AdvancedMetricsPage` (estados de carga, error `403`/red en español y botón de refresco manual) en `front/src/pages/AdvancedMetricsPage.jsx`, registrando únicamente la ruta `/metrics/advanced` en `front/src/App.jsx` si es estrictamente indispensable para navegación sin alterar ninguna ruta ni archivo previo
- [ ] T043 [US5] Crear suite de pruebas aislada en Vitest verificando renderizado de métricas, estado vacío y manejo de `403 Forbidden` en `front/src/test/advanced-metrics.test.jsx` y verificar `npm test` sin regresiones en `front/`

**Checkpoint**: User Story 5 funcional en backend y frontend básico sin alterar funcionalidad previa del frontend.

---

## Phase 8: Polish, Integración E2E y Validación Final

**Purpose**: Verificación integral E2E sobre PostgreSQL (Testcontainers), pruebas completas de rendimiento/resiliencia (`SC-001`, `SC-006`), ejecución completa de suites `unit`, `e2e` y Vitest, y control de calidad.

- [ ] T044 Crear suite de integración `ObservabilityAndMetricsE2ETest` verificando: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, `GET /metrics/advanced` (`200` superusuario con `generatedAt` en raíz / `403` usuario regular / `401` anónimo), cabecera `X-Correlation-ID`, consistencia de `GET /players/ranking`, verificación cuantitativa de `cbo_ranking_cache_requests_total` (`miss` → `hit`, hit ratio `> 90%` y latencia `< 50 ms` en lecturas repetidas — `SC-001`) y fallback `200 OK` en `GET /players/ranking` y `GET /players` ante fallo simulado de Redis (`SC-006`) en `back/src/test/java/com/cbo/players/integration/ObservabilityAndMetricsE2ETest.java`
- [ ] T045 Ejecutar suites completas `./mvnw test -Punit`, `./mvnw test -Pe2e` (en `back/`), `npm test` (en `front/`) y validar `specs/006-entrega3-observability-optimization/quickstart.md` (incluyendo `docker compose config`, formato de `logs/audit.log`, verificación de `git diff front/` minimal-touch y Swagger `/v3/api-docs`)

---

## Phase 9: User Story 6 - Tag en GitHub y Confección de Release Notes de Entrega 3 (Priority: P2)

**Goal**: Documentar formalmente las Release Notes de la Entrega 3 y cerrar la versión con el Tag semántico `v3.0.0` y Release en GitHub únicamente después de superar la compuerta de CI y SonarCloud.

**Independent Test**: Confirmar existencia de `docs/release-notes-v3.0.0.md` completo, verificar pipeline CI remoto en `SUCCESS` y SonarCloud `< 10 issues`, y comprobar tag anotado `v3.0.0` y Release publicados en GitHub.

- [ ] T046 [US6] Confeccionar las Release Notes formales de la Entrega 3 (resumen ejecutivo, novedades CORE y funcionales, nuevos endpoints sin `/api` ni `/v1`, vista básica de métricas avanzadas, verificación de scheduler de scraping, observabilidad, rendimiento, estado de tests/arquitectura/SonarCloud y guía de verificación) en `docs/release-notes-v3.0.0.md`
- [ ] T047 [US6] Tras verificar compuerta obligatoria de pipeline CI remoto en estado `SUCCESS` y SonarCloud `< 10 issues` sobre la rama principal (`main`), crear el tag anotado `v3.0.0`, pushear el tag a `origin` y publicar la Release en GitHub con el contenido de `docs/release-notes-v3.0.0.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Sin dependencias — iniciar primero.
- **Phase 2 (Foundational)**: Depende de Phase 1 — BLOQUEA las User Stories (incluye registro automático de `CorrelationIdFilter` en `T007`).
- **Phase 3 (US1 - Ranking, Redis Completo & Scheduler)**: Depende de Phase 2.
- **Phase 4 (US2 - Auditoría AOP)**: Depende de Phase 2 (`T001`, `T007`, `T008`). Totalmente independiente de US1 y US3.
- **Phase 5 (US3 - Actuator & Prometheus)**: Depende de Phase 2 (`T001`, `T003`, `T009`).
- **Phase 6 (US4 - Architecture Tests)**: Depende de Phase 1 (`T001`) y se re-valida al final tras US5.
- **Phase 7 (US5 - `GET /metrics/advanced` + Vista Básica Frontend)**: Depende de Phase 2 y aprovecha las métricas `cbo_ranking_*` introducidas en US1 (`T013`).
- **Phase 8 (Polish & E2E)**: Depende de US1–US5 completas.
- **Phase 9 (US6 - Release Notes & Tag)**: Depende de Phase 8 y de la compuerta obligatoria de CI `SUCCESS` + SonarCloud `< 10 issues`.

### Parallel Opportunities
- **Setup**: `T002` y `T003` en paralelo con `T001`.
- **Foundational**: `T004`, `T005`, `T006` (índices JPA) en paralelo entre sí; `T007` + `T009` en paralelo.
- **US1**: `T010` y `T011` en paralelo antes de `T013`; `T014` y `T015` en paralelo; `T019`, `T020` y `T021` en paralelo.
- **US2**: `T022` y `T023` en paralelo antes de `T024`; `T025` y `T026` en paralelo.
- **US3**: `T029` y `T030` en paralelo con `T028` y `T031`.
- **US5**: `T034`–`T037` en paralelo antes de `T038`; `T040` y `T041` en paralelo antes de `T042`.

---

## Implementation Strategy

1. **MVP Incremental (Phases 1 → 3)**: Dependencias + índices + `CorrelationIdFilter` + Optimización de Ranking con Redis (clave por estrategia + anti-stampede) + Caché catálogo/externos + Verificación de `CatalogScheduler` (US1).
2. **Núcleo de Observabilidad y Gobernanza (Phases 4 → 6)**: Auditoría AOP + Logback (US2) → Actuator + Prometheus + Docker (US3) → ArchUnit (US4).
3. **Funcionalidad Avanzada + Vista Básica Aislada (Phases 7 → 8)**: `GET /metrics/advanced` + vista básica en `front/` sin alterar archivos actuales salvo lo indispensable (US5) → Suite E2E completa (`SC-001`, `SC-006`) y validación `quickstart.md`.
4. **Cierre Formal de TP (Phase 9)**: Release Notes (`docs/release-notes-v3.0.0.md`) → Compuerta CI `SUCCESS` + SonarCloud `< 10 issues` → Tag anotado `v3.0.0` y Release en GitHub (US6).
