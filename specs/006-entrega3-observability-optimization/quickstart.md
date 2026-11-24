# Quickstart & Validation Guide: Entrega 3

**Feature**: `006-entrega3-observability-optimization`  
**Date**: 2026-11-24  
**References**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [contracts/observability-and-metrics-api.md](./contracts/observability-and-metrics-api.md)

---

## Prerrequisitos

- Java 17+ y Maven (`./mvnw` en `back/`)
- Docker Desktop / Docker Engine con `docker compose`
- Archivo `.env` configurado en la raíz del repositorio (con `POSTGRES_PASSWORD`, `JWT_SECRET`, etc.)

---

## 1. Validación Automatizada (Perfiles `unit` y `e2e`)

### Suite Unitaria + Tests de Arquitectura (ArchUnit)
No requiere contenedores externos ni Redis levantado:
```powershell
cd back
./mvnw test -Punit
```
**Resultado esperado**:
- `ArchitectureTest` pasa con 0 violaciones de capas, ciclos ni exposición de entidades JPA.
- Tests unitarios de auditoría AOP, sanitizador de secretos, filtro `CorrelationId`, `RankingService` y `AdvancedMetricsService` pasan al 100%.

### Suite End-to-End (PostgreSQL vía Testcontainers)
Requiere Docker activo:
```powershell
cd back
./mvnw test -Pe2e
```
**Resultado esperado**:
- `ObservabilityAndMetricsE2ETest` valida `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, control de acceso (`200`/`403`/`401`) de `GET /api/metrics/advanced`, propagación de `X-Correlation-ID` y consistencia de `GET /api/players/ranking`.

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

### Escenario B — Optimización de Ranking con Redis y Tolerancia a Fallos
1. Autenticarse y consultar `GET /api/players/ranking` dos veces consecutivas:
   - La primera llamada registra `cache miss` (`cbo_ranking_cache_requests_total{result="miss"}`).
   - La segunda y sucesivas llamadas responden desde Redis en `< 50 ms` (`result="hit"`).
2. Ejecutar `POST /api/quotes/recalculate` (o `PATCH /api/admin/strategies/{id}/activate` como superusuario) y volver a consultar `GET /api/players/ranking`: confirmar que los datos reflejan inmediatamente el cambio.
3. Simular caída de Redis (`docker compose stop redis`), consultar `GET /api/players/ranking` y confirmar respuesta `200 OK` (degradación elegante a PostgreSQL). Luego restaurar Redis (`docker compose start redis`).

### Escenario C — Auditoría AOP, Correlation ID y Redacción de Secretos
1. Invocar `POST /api/auth/login` enviando cabecera `X-Correlation-ID: demo-trace-001`.
2. Verificar que la respuesta incluye la cabecera `X-Correlation-ID: demo-trace-001`.
3. Inspeccionar los logs de consola (`docker compose logs backend`) y el archivo dedicado `back/logs/audit.log` (o volumen montado):
   - Debe figurar `timestamp`, `correlationId=demo-trace-001`, `user`, `operation`, `params` con `password=[REDACTED]`, `executionTimeMs` y `outcome`.

### Escenario D — Endpoint de Métricas Avanzadas (`GET /api/metrics/advanced`)
1. Sin token → `401 Unauthorized`.
2. Con token de usuario regular → `403 Forbidden` con `ProblemDetail` (`code: "ACCESS_DENIED"`).
3. Con token de superusuario → `200 OK` con los bloques `market`, `valuation`, `alerts`, `performance` y `generatedAt`.

---

## 3. Verificación Final de Release (Entrega 3)

1. Confirmar pipeline de GitHub Actions en estado `SUCCESS` y SonarCloud con `Issues < 10`.
2. Verificar presencia de `docs/release-notes-v3.0.0.md`.
3. Confirmar etiqueta y Release en GitHub:
   ```powershell
   git tag -l "v3.0.0"
   ```
