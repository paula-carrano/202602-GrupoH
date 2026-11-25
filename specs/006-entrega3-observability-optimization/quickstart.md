# Quickstart & Validation Guide: Entrega 3

**Feature**: `006-entrega3-observability-optimization`  
**Date**: 2026-11-24 *(Updated: 2026-11-25)*  
**References**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [contracts/observability-and-metrics-api.md](./contracts/observability-and-metrics-api.md)

> [!IMPORTANT]
> **RESTRICCIÓN CRÍTICA DE FRONTEND (MÉTRICAS AVANZADAS):**  
> Al validar la vista básica de métricas avanzadas en `front/`, verificar con `git diff front/` que la implementación vive en archivos nuevos creados usando el frontend actual como referencia y que **no se modificó ningún archivo existente del frontend salvo lo estrictamente indispensable** (registro mínimo de ruta en `App.jsx`).

---

## Prerrequisitos

- Java 17+ y Maven (`./mvnw` en `back/`)
- Node.js 18+ y npm (para validar tests Vitest en `front/`)
- Docker Desktop / Docker Engine con `docker compose`
- Archivo `.env` configurado en la raíz del repositorio (con `POSTGRES_PASSWORD`, `JWT_SECRET`, etc.)

---

## 1. Validación Automatizada (Perfiles `unit`, `e2e` y Vitest)

### Suite Unitaria + Tests de Arquitectura (ArchUnit) + Scheduler + Caché
No requiere contenedores externos ni Redis levantado:
```powershell
cd back
./mvnw test -Punit
```
**Resultado esperado**:
- `ArchitectureTest` pasa con 0 violaciones de capas, ciclos ni exposición de entidades JPA.
- `RankingServiceTest`, `PlayerServiceCacheTest` y `CatalogSchedulerTest` validan eliminación de N+1, clave por estrategia/versión, protección contra *cache stampede*, degradación `200 OK` ante fallo de Redis (`SC-006`) y actualización persistida en base de datos por el scheduler de scraping (`FR-013`).
- `AuditParameterSanitizerTest`, `CorrelationIdFilterTest`, `WebServiceAuditAspectTest` y `AdvancedMetricsServiceTest` pasan al 100%.

### Suite End-to-End (PostgreSQL vía Testcontainers)
Requiere Docker activo:
```powershell
cd back
./mvnw test -Pe2e
```
**Resultado esperado**:
- `ObservabilityAndMetricsE2ETest` valida `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, control de acceso (`200`/`403`/`401`) de `GET /metrics/advanced` (con `generatedAt` en nivel raíz), propagación de `X-Correlation-ID`, hit ratio `> 90%` y latencia `< 50 ms` en lecturas repetidas de `GET /players/ranking` (`SC-001`), y fallback `200 OK` ante fallo simulado de caché (`SC-006`).

### Suite Frontend Aislada (Vitest)
```powershell
cd front
npm test
```
**Resultado esperado**:
- `advanced-metrics.test.jsx` y toda la suite previa de `front/` pasan al 100% sin regresiones.

---

## 2. Validación en Vivo con Docker Compose (Backend + PostgreSQL + Redis + Prometheus)

Levantar el stack completo desde la raíz del repositorio:
```powershell
docker compose up --build -d
```

### Escenario A — Monitoreo Actuator y Scraping de Prometheus
1. Verificar salud de dependencias (`db` y `redis` en estado `UP`):
   ```powershell
   curl.exe -s http://localhost:8080/actuator/health
   ```
2. Verificar listado de métricas y endpoint Prometheus:
   ```powershell
   curl.exe -s http://localhost:8080/actuator/metrics
   curl.exe -s http://localhost:8080/actuator/prometheus | Select-String "cbo_"
   ```
3. Abrir la UI de Prometheus en `http://localhost:9090/targets` y verificar que el job `players-market-backend` aparece en estado **`UP`**.

### Escenario B — Optimización de Ranking con Redis, Clave por Estrategia y Tolerancia a Fallos
1. Autenticarse y consultar `GET /players/ranking` repetidas veces consecutivas:
   - La primera llamada registra `cache miss` (`cbo_ranking_cache_requests_total{result="miss"}`) bajo la clave `active_ranking:<strategyCode>:v<version>`.
   - Las siguientes llamadas responden desde Redis en `< 50 ms` (`result="hit"`), logrando hit ratio `> 90%`.
2. Ejecutar `POST /quotes/recalculate` o `PATCH /admin/strategies/{id}/activate` (como superusuario) y volver a consultar `GET /players/ranking`: confirmar que los datos reflejan inmediatamente el cambio.
3. Simular caída de Redis (`docker compose stop redis`), consultar `GET /players/ranking` y `GET /players`, y confirmar respuestas `200 OK` (degradación elegante a PostgreSQL). Luego restaurar Redis (`docker compose start redis`).

### Escenario C — Scheduler de Scraping Actualizando Base de Datos (`FR-013`)
1. Confirmar que el contenedor `scraper` está saludable y que `CATALOG_SYNC_ENABLED=true` en `docker-compose.yml`.
2. Verificar que tras disparar la sincronización programada del scheduler (`CatalogScheduler`), los registros de `players` y `player_statistics` en PostgreSQL se actualizan, las cachés `players::catalog` y `players::ranking` se invalidan y las cotizaciones se recalculan con las métricas actualizadas.

### Escenario D — Auditoría AOP, Correlation ID y Redacción de Secretos
1. Invocar `POST /auth/login` enviando cabecera `X-Correlation-ID: demo-trace-001`.
2. Verificar que la respuesta incluye la cabecera `X-Correlation-ID: demo-trace-001`.
3. Inspeccionar los logs de consola (`docker compose logs backend`) y el archivo dedicado `back/logs/audit.log`:
   - Debe figurar `timestamp`, `correlationId=demo-trace-001`, `user`, `operation`, `params` con `password=[REDACTED]`, `executionTimeMs` y `outcome`.

### Escenario E — Endpoint `GET /metrics/advanced` y Vista Básica en Frontend
1. Sin token → `401 Unauthorized`.
2. Con token de usuario regular → `403 Forbidden` con `ProblemDetail` (`code: "ACCESS_DENIED"`).
3. Con token de superusuario → `200 OK` con los bloques `market`, `valuation`, `alerts`, `performance` y `generatedAt` en nivel raíz.
4. Abrir la vista básica en el frontend (`/metrics/advanced`) autenticado como superusuario y verificar la visualización de los 4 bloques y `generatedAt`.

---

## 3. Verificación Final de Release (Entrega 3 — Compuerta CI/SonarCloud)

1. Confirmar pipeline de GitHub Actions en estado **`SUCCESS`** (suites `unit`, `e2e`, ArchUnit y Vitest en verde).
2. Confirmar en **SonarCloud** que el proyecto cumple **`Issues < 10`**.
3. Verificar presencia de `docs/release-notes-v3.0.0.md`.
4. Solo tras cumplir los pasos 1–3, crear y pushear la etiqueta anotada `v3.0.0` y publicar la Release en GitHub:
   ```powershell
   git tag -l "v3.0.0"
   ```
