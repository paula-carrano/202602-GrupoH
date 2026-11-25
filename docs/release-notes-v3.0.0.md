# Release Notes — v3.0.0 (Entrega 3 — Final)

**Versión:** `v3.0.0`  
**Fecha:** 2026-11-25  
**Proyecto:** Plataforma de Mercado y Valoración de Jugadores de Fútbol (Grupo H — UNQ Desarrollo de Aplicaciones)  
**Especificación:** [`specs/006-entrega3-observability-optimization/spec.md`](../specs/006-entrega3-observability-optimization/spec.md)

---

## Resumen Ejecutivo

La versión **v3.0.0 (Entrega 3 — Final)** consolida la observabilidad operativa, la auditoría transversal de servicios web, la gobernanza arquitectónica automatizada y la optimización de rendimiento para alta frecuencia de consultas sobre la plataforma de mercado de tokens de jugadores.

---

## Novedades CORE

### 1. Tests de Arquitectura Automatizados (`ArchUnit`)
- Suite `ArchitectureTest` integrada en el perfil `unit` y en CI (`./mvnw test -Punit`).
- Valida automáticamente:
  - Respeto estricto a la arquitectura por capas (`Controllers → Services → Repositories` y `Adapters`).
  - Prohibición de acceso directo desde `Controllers` hacia `Repositories` o `Adapters`.
  - Prohibición de dependencias inversas (`Repositories → Services/Controllers`, `Adapters → Controllers`).
  - Ausencia de dependencias cíclicas entre paquetes principales (`com.cbo.players.(*)..`).
  - Prohibición de exponer entidades JPA (`@Entity`) desde firmas públicas de controladores (uso obligatorio de DTOs).
  - Convenciones de ubicación y nomenclatura de `@RestController`, `@Service` y `@Repository`.

### 2. Caché Distribuida con Redis y Degradación Elegante
- Consolidación de `RedisCacheConfig` con serialización JSON con soporte `JavaTimeModule` y preservación de tipos Java `record`, estadísticas habilitadas y TTLs configurables para `players::ranking`, `players::catalog` y `players::external`.
- Cableado productivo de Redis en `docker-compose.yml` (`REDIS_HOST=cbo-redis`, `REDIS_PORT=6379`, dependencia saludable `redis`).
- Tolerancia a fallos (`CacheErrorHandler` y manejo defensivo): si Redis no está disponible, el sistema degrada transparentemente a PostgreSQL retornando `200 OK` sin errores 500.
- Caché y fallback resiliente para respuestas de proveedores externos (`ScrapingRestAdapter` ante errores 5xx/timeout/conexión).

### 3. Auditoría Transversal de Web-Services con Spring AOP, Logback y Correlation ID
- **`CorrelationIdFilter`** (`@Order(Ordered.HIGHEST_PRECEDENCE)`): genera o propaga `X-Correlation-ID` vía `MDC` en cada solicitud HTTP, unificado con `GlobalExceptionHandler` y `SecurityConfig` (`ProblemDetail` 401/403).
- **`WebServiceAuditAspect`** (`@Aspect`): intercepta todos los `@RestController` de negocio y emite registros estructurados con `<timestamp, correlationId, user, operation, params, executionTimeMs, outcome>`.
- **`AuditParameterSanitizer`**: redacta recursivamente cualquier campo sensible (`password`, `secret`, `token`, `apiKey`, `authorization`, `jwt`, `credential`, `key`) reemplazándolo por `[REDACTED]` y trunca payloads extensos previniendo inyección CRLF.
- **`logback-spring.xml`**: logger dedicado `AUDIT_LOGGER` (`additivity="false"`) escribiendo simultáneamente en consola estructurada (`stdout`) y en el archivo rotativo `logs/audit.log` (excluido de Git).

### 4. Spring Boot Actuator y Métricas Prometheus
- Exposición pública segura de `/actuator/health` (con detalle de `db`, `redis` y `diskSpace`), `/actuator/info` (versión `3.0.0`), `/actuator/metrics` y `/actuator/prometheus`, manteniendo ocultos endpoints destructivos o sensibles.
- Métricas de dominio personalizadas en Prometheus (`Micrometer`):
  - `cbo_ranking_cache_requests_total{result="hit|miss"}`
  - `cbo_ranking_query_duration_seconds{source="cache|db"}`
  - `cbo_orders_executed_total{type="buy|sell"}`
  - `cbo_quotes_recalculations_total{strategy="<code>"}`
- Servicio `prometheus` (`prom/prometheus:v2.54.1`) agregado en `docker-compose.yml` junto con `monitoring/prometheus.yml` preconfigurado para scraping automático contra `cbo-backend:8080/actuator/prometheus`.

---

## Novedades FUNCIONALES

### 1. Optimización de Ranking para Alta Frecuencia de Consultas
- Eliminación completa del problema N+1 en `RankingService` mediante consulta batch indexada `findLatestQuotesForActivePlayers()` y nuevos índices JPA (`idx_players_active`, `idx_player_quotes_player_timestamp`, `idx_transactions_operation_type`).
- Clave de caché determinística por estrategia y versión (`active_ranking:<strategyCode>:v<version>`) con protección sincronizada contra *cache stampede*.
- Invalidación inmediata de `players::ranking` ante recálculo manual (`POST /quotes/recalculate`), cambio de estrategia activa (`PATCH /admin/strategies/{id}/activate`) y finalización del scheduler de scraping.

### 2. Verificación y Coordinación del Scheduler de Scraping (`CatalogScheduler`)
- Coordinación sincrónica segura (`awaitCompletion`) en `CatalogScheduler` + `CatalogSyncService` para garantizar que la actualización de planteles y estadísticas en PostgreSQL y la invalidación de cachés (`players::catalog`, `players::ranking`) finalicen antes de recalcular cotizaciones.

### 3. Endpoint de Métricas Avanzadas (`GET /metrics/advanced`) y Vista Básica en Frontend
- Nuevo endpoint `GET /metrics/advanced` (sin prefijo `/api` ni `/v1`, documentado en Swagger/OpenAPI v3 y restringido exclusivamente al `SUPERUSER`: `401` sin autenticar, `403 ProblemDetail ACCESS_DENIED` para usuarios regulares).
- Retorna `generatedAt` en nivel raíz junto con los bloques consolidados `market`, `valuation`, `alerts` y `performance`.
- Vista básica aislada en frontend (`/metrics/advanced`) construida en archivos nuevos (`front/src/services/advancedMetrics.js`, `front/src/components/AdvancedMetricsPanel.jsx`, `front/src/pages/AdvancedMetricsPage.jsx`, `front/src/test/advanced-metrics.test.jsx`) usando el frontend actual como referencia sin alterar archivos existentes salvo el registro mínimo de ruta en `front/src/App.jsx`.

---

## Verificación y Calidad

| Verificación | Comando | Estado |
| :--- | :--- | :--- |
| Suite Unitaria + ArchUnit | `cd back && ./mvnw.cmd test -Punit` | **PASS** (246/246 tests, 10/10 reglas ArchUnit) |
| Suite Frontend (Vitest) | `cd front && npm test -- --run` | **PASS** (19/19 archivos, 152/152 tests) |
| Validación Docker Compose | `docker compose config --quiet` | **PASS** (PostgreSQL + Backend + Scraper + Redis + Prometheus) |
| Suite E2E (Testcontainers PostgreSQL + Redis 7) | `cd back && ./mvnw.cmd test -Pe2e` | **PASS** (46/46 tests, incluyendo `ObservabilityAndMetricsE2ETest` y `RankingRedisCacheE2ETest`) |
| Calidad SonarCloud y Tag `v3.0.0` | Pipeline GitHub Actions (`Issues < 10`) | Ejecutar publicación de Tag y Release en GitHub tras validar CI en verde y SonarCloud `< 10 issues` en `main` |
