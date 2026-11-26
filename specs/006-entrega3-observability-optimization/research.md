# Research & Technical Decisions: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Feature**: `006-entrega3-observability-optimization`  
**Date**: 2026-11-24 *(Updated: 2026-11-25)*  
**Spec**: [spec.md](./spec.md)

> [!IMPORTANT]
> **RESTRICCIÓN CRÍTICA DE FRONTEND (MÉTRICAS AVANZADAS):**  
> El frontend básico para visualizar las métricas avanzadas (`GET /metrics/advanced`) **DEBE** tomar como referencia visual, estilística y arquitectónica el frontend actual (`front/`), implementándose en archivos nuevos dedicados y **SIN tocar ningún archivo actual del frontend a menos que sea sumamente importante / estrictamente indispensable**.

---

## Diagnóstico del Estado Actual del Backend y Frontend (Codebase Audit)

Al inspeccionar el código actual de `back/`, `front/` y `docker-compose.yml` se identificaron las siguientes brechas concretas que guían las decisiones técnicas:

1. **Convención de Rutas sin `/api` ni `/v1` vs. Estado Actual en `main`**:
   - Por directiva explícita del equipo y la Constitución (§11), **ningún endpoint debe llevar prefijo `/api/` ni `/v1/`** (`GET /metrics/advanced`, `GET /players/ranking`, `GET /players`, `POST /quotes/recalculate`, `PATCH /admin/strategies/{id}/activate`).
   - *Hallazgo técnico importante en el código actual de `main`*: los 9 controladores existentes en `back/src/main/java/com/cbo/players/controller/*.java` actualmente tienen `@RequestMapping("/api/...")`, y `front/src/services/api.js` usa `baseURL: '/api'` junto con el proxy `'/api': 'http://localhost:8080'` en `front/vite.config.js`.
   - *Implicancia para la implementación*: `AdvancedMetricsController` se expondrá directamente en `/metrics/advanced` (sin `/api`). Al no deber modificar archivos existentes del frontend (`front/src/services/api.js`, `front/vite.config.js`, etc.) a menos que sea estrictamente indispensable, el nuevo servicio dedicado `front/src/services/advancedMetrics.js` debe respetar la ruta `/metrics/advanced` y coordinarse cuidadosamente durante `/speckit-implement` (evaluando en implementación si el proxy o los controladores heredados requieren algún ajuste mínimo explícitamente autorizado).
2. **Problema N+1 en `RankingService` y Ausencia de Clave por Estrategia / Protección Stampede**:
   - `RankingService.getRanking()` invoca `playerRepository.findAll()` (filtrando `isActive` en memoria) y luego ejecuta `playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(p.getId())` dentro de un loop por cada jugador activo.
   - `PlayerQuote` carece de índice compuesto `(player_id, timestamp)` y `Player` carece de índice sobre `active`.
   - La clave de caché actual (`'active_ranking'`) no incorpora el código ni versión de la estrategia activa y carece de sincronización contra *cache stampede*.
3. **Brechas en Caché Redis (Ranking, Catálogo y Proveedores Externos)**:
   - `docker-compose.yml` declara `redis` (`cbo-redis`), pero no inyecta `REDIS_HOST: cbo-redis` ni `REDIS_PORT: 6379` al servicio `backend`.
   - `ValuationStrategyService.activateStrategy(...)` no invalida `players::ranking`.
   - `PlayerService` (`getAllPlayers()`, `getPlayerById()`) no utiliza `@Cacheable("players::catalog")` y `CatalogPersistenceService` (`importSquad()`, `saveStatistics()`) no invalida `players::catalog` ni `players::ranking`.
   - `ScrapingRestAdapter` no cuenta con caché Redis de respuestas/respaldo para datos de proveedores externos (`players::external`) ni fallback al caché ante indisponibilidad temporal del scraper.
   - `RedisCacheConfig` utiliza `GenericJackson2JsonRedisSerializer` sin `JavaTimeModule` y no expone estadísticas de caché hacia Micrometer.
4. **Condición de Carrera en `CatalogScheduler` (Scheduler de Scraping)**:
   - `CatalogScheduler.synchronize()` invoca `sync.start(CatalogSyncRun.Origin.SCHEDULED)` —que corre de forma **asíncrona** en `catalogSyncExecutor`— e inmediatamente después, sin esperar a que finalice la persistencia de equipos y estadísticas de jugadores en PostgreSQL, llama a `quoteCalculationService.recalculateAllQuotes()`.
   - Además `app.catalog-sync.enabled` viene en `false` por defecto en `application.yml` (`true` en `docker-compose.yml`) y no existe aún un test integral que verifique que el ciclo del scheduler actualice efectivamente los jugadores y estadísticas en la base de datos e invalide cachés antes del recálculo.
5. **Faltantes de Actuator y Prometheus**:
   - Falta `io.micrometer:micrometer-registry-prometheus` en `pom.xml`.
   - `application.yml` carece de bloque `management:` (no expone `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus` ni detalles de `/actuator/health`).
   - `SecurityConfig` solo permite públicamente `/actuator/health`.
   - `docker-compose.yml` no incluye aún el servicio `prometheus` ni `monitoring/prometheus.yml`.
6. **Faltantes de Auditoría AOP y Correlation ID**:
   - Falta `spring-boot-starter-aop` en `pom.xml`.
   - No existe `CorrelationIdFilter` (MDC) y `GlobalExceptionHandler` genera un `UUID.randomUUID()` distinto por excepción.
   - No existe `logback-spring.xml` con logger dedicado a `stdout` + archivo rotativo `logs/audit.log`.
7. **Faltantes de Tests de Arquitectura**:
   - Falta `com.tngtech.archunit:archunit-junit5` y `ArchitectureTest`.
8. **Estructura de Referencia en `front/`**:
   - El frontend usa React + Vite + React Router (`App.jsx`, `DashboardLayout.jsx`, `style.css`, `FeedbackAlert.jsx`, `session.js`, `api.js`, Vitest).
   - Para cumplir la regla de no modificar archivos existentes salvo lo estrictamente indispensable, la vista básica de métricas avanzadas debe crearse en archivos nuevos aislados (`front/src/services/advancedMetrics.js`, `front/src/components/AdvancedMetricsPanel.jsx`, `front/src/pages/AdvancedMetricsPage.jsx`, `front/src/test/advanced-metrics.test.jsx`), limitando cualquier edición en archivos existentes únicamente al registro mínimo indispensable de ruta en `App.jsx` (y navegación condicional solo si se considera estrictamente necesario).

---

## Decisiones Técnicas

### 1. Tests de Arquitectura con ArchUnit (JUnit 5)
- **Decision**: Incorporar `com.tngtech.archunit:archunit-junit5:1.3.0` (scope `test`) y crear `ArchitectureTest` en `com.cbo.players.architecture`, ejecutado automáticamente en el perfil `unit` y en CI.
- **Rationale**: Analiza bytecode compilado en milisegundos sin levantar Spring ni Docker; valida arquitectura por capas (`controller → service → repository` y `adapter`), ciclos entre paquetes, convenciones de nomenclatura y prohibición de retornar `@Entity` desde métodos públicos de controladores.
- **Alternatives considered**: Spring Modulith (enfoque modular vertical distinto a capas clásicas), Checkstyle/PMD (sin validación de grafos de dependencias entre capas).

### 2. Optimización de Ranking, Caché Redis Completa (`FR-002`) y Scheduler de Scraping (`FR-013`)
- **Decision**:
  - **Nivel Base de Datos**: Índices `@Index(name = "idx_player_quotes_player_timestamp", columnList = "player_id, timestamp")` en `PlayerQuote`, `@Index(name = "idx_players_active", columnList = "active")` en `Player` y `@Index(name = "idx_transactions_operation_type", columnList = "operation_type")` en `Transaction`. Eliminar el loop N+1 de `RankingService` mediante `playerRepository.findAllByActiveTrue()` (ya existente o `findByActiveTrue()`) + consulta batch única en `PlayerQuoteRepository` para la última cotización por jugador (desempate por `id DESC`).
  - **Clave por Estrategia y Protección contra Cache Stampede (`C1`)**: Construir la clave de caché del ranking como `active_ranking:<strategyCode>:v<version>` (resolviendo primero estrategia activa) y proteger la carga en caché miss mediante bloqueo sincronizado por clave (`ConcurrentHashMap` / `synchronized` o `sync = true`) para evitar *cache stampede* bajo ráfagas concurrentes.
  - **Caché Completa de Catálogo y Proveedores Externos (`FR-002` / `G1`)**:
    - Configurar en `RedisCacheConfig` tres espacios con TTLs configurables: `players::ranking` (1 h), `players::catalog` (15 min) y `players::external` (30 min), usando `ObjectMapper` dedicado con `JavaTimeModule`, `enableStatistics()` y `CacheErrorHandler` tolerante a fallos.
    - Habilitar caché en `PlayerService` (`players::catalog`) e invalidación explícita en `CatalogPersistenceService` (`importSquad()`, `saveStatistics()`), `QuoteCalculationService.recalculateAllQuotes()` y `ValuationStrategyService.activateStrategy(...)`.
    - Integrar caché/respaldo en `ScrapingRestAdapter` (o fachada de caché externa en `service`/`config`) para almacenar respuestas de proveedores externos y degradar a datos locales persistidos si fallan el proveedor o Redis.
  - **Verificación y Corrección del Scheduler de Scraping (`FR-013`)**:
    - Asegurar en `CatalogScheduler` + `CatalogSyncService` que la ejecución programada espere/coordine la finalización de la actualización de planteles y estadísticas en PostgreSQL antes de invalidar cachés y disparar `quoteCalculationService.recalculateAllQuotes()` (o dispare el recálculo e invalidación como callback/hook al completarse el `CatalogSyncRun`), verificándolo con tests unitarios e integración.
  - **Telemetría de Caché y Rendimiento**: Instrumentar en `RankingService` los contadores `cbo_ranking_cache_requests_total` (`result=hit|miss`) y `cbo_ranking_query_duration_seconds` (`source=cache|db`).
- **Rationale**: Cierra completamente `FR-002`, `FR-003`, `FR-004`, `FR-013`, `SC-001` y `SC-006` sin dejar brechas parciales.

### 3. Auditoría Transversal de Web-Services con Spring AOP, Logback y Correlation ID
- **Decision**:
  - Agregar `spring-boot-starter-aop` en `pom.xml`.
  - Registrar `CorrelationIdFilter` directamente en Phase 2 (`T007`) como `@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE)` (independizando US2 de US3, resolviendo `I3`). Unificar `GlobalExceptionHandler` y `SecurityConfig.accessDeniedHandler` leyendo `MDC.get("correlationId")`.
  - Implementar `WebServiceAuditAspect` (`@Around` sobre métodos públicos de `@RestController` en `com.cbo.players.controller..*`) + `AuditParameterSanitizer` (redacción recursiva `[REDACTED]` de campos sensibles y truncado a 500 chars) + `logback-spring.xml` (`AUDIT_LOGGER` con `additivity="false"` hacia `STDOUT` y `RollingFileAppender` en `logs/audit.log`).
- **Rationale**: Cumple el formato `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` con 0 fugas de credenciales y trazabilidad completa.

### 4. Spring Boot Actuator y Prometheus
- **Decision**:
  - Agregar `io.micrometer:micrometer-registry-prometheus` en `pom.xml`.
  - Exponer `health,info,metrics,prometheus` en `application.yml` con `show-details: always`, `probes.enabled: true` y bloque `info.app` (`3.0.0`).
  - Permitir públicamente `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/actuator/metrics`, `/actuator/metrics/**` y `/actuator/prometheus` en `SecurityConfig`.
  - Instrumentar contadores `cbo_orders_executed_total` y `cbo_quotes_recalculations_total`.
  - Agregar servicio `prometheus` (`prom/prometheus:v2.54.1`) en `docker-compose.yml` con `./monitoring/prometheus.yml` apuntando a `cbo-backend:8080/actuator/prometheus`.

### 5. Endpoint `GET /metrics/advanced` y Vista Básica de Frontend
- **Decision**:
  - **Backend**: `AdvancedMetricsController` (`@RequestMapping("/metrics")`, `@GetMapping("/advanced")`) → `AdvancedMetricsService` → consultas agregadas JPQL en repositorios + `MeterRegistry`. Restringido a `SUPERUSER` en `SecurityConfig` (`401`/`403 ProblemDetail ACCESS_DENIED`). Campo `generatedAt` ubicado en la raíz de `AdvancedMetricsResponseDto` (resolviendo `I2`).
  - **Frontend básico (Política Zero/Minimal-Touch)**:
    - Usar los estilos (`front/src/style.css`) y patrones del frontend actual (`DashboardLayout`, `FeedbackAlert`, `session.js`) únicamente como referencia.
    - Crear archivos nuevos autocontenidos: `front/src/services/advancedMetrics.js`, `front/src/components/AdvancedMetricsPanel.jsx`, `front/src/pages/AdvancedMetricsPage.jsx` y `front/src/test/advanced-metrics.test.jsx`.
    - No tocar ningún archivo existente en `front/` salvo el mínimo estrictamente indispensable (únicamente el registro de la ruta `/metrics/advanced` en `front/src/App.jsx` para poder navegarla, sin tocar estilos, servicios, páginas ni tests existentes).
- **Rationale**: Maximiza la demostrabilidad en vivo de Entrega 3 manteniendo riesgo cero de regresión sobre el frontend aprobado en Entrega 2.

### 6. Cierre de Entrega (`v3.0.0`) y Compuerta CI/SonarCloud (`U1`)
- **Decision**: Confeccionar `docs/release-notes-v3.0.0.md` y condicionar estrictamente el push del tag anotado `v3.0.0` y la publicación de la Release en GitHub a la verificación previa de pipeline CI en `SUCCESS` y SonarCloud `< 10 issues`.
