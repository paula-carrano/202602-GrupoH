# Interface Contracts: Observabilidad, Auditoría y Métricas Avanzadas (Entrega 3)

**Feature**: `006-entrega3-observability-optimization`  
**Date**: 2026-11-24  
**Base URL (API de negocio)**: `/api` *(sin prefijo `/v1`, conforme a la Constitución v1.1.0 y a los controladores existentes)*

---

## 1. Endpoint de Métricas Avanzadas

### `GET /api/metrics/advanced`
Consolida métricas de negocio (mercado, valoración, alertas) y métricas técnicas de rendimiento (caché y latencia de ranking).

- **Autenticación y Autorización**: Requiere JWT válido con rol `SUPERUSER` (`Authorization: Bearer <token>`).
- **Cabecera opcional de trazabilidad**: `X-Correlation-ID: <uuid>` (si no se envía, el backend genera uno y lo retorna en la cabecera de respuesta `X-Correlation-ID`).

#### Respuesta Exitosa (`200 OK`, `application/json`)
```json
{
  "market": {
    "totalTransactions": 42,
    "transactionsByType": {
      "COMPRA": 20,
      "VENTA": 10,
      "RECARGA": 11,
      "CAMBIO_ESTRATEGIA": 1
    },
    "totalTradedCredits": 1245.5000,
    "activeUsers": 8,
    "totalIssuedTokens": 5000,
    "tokensInCirculation": 310,
    "circulationRatioPercent": 6.2,
    "topTradedPlayers": [
      {
        "playerId": 12,
        "playerName": "Kylian Mbappé",
        "team": "Real Madrid",
        "totalTokensTraded": 85,
        "totalVolumeCredits": 412.2500
      }
    ]
  },
  "valuation": {
    "activeStrategyCode": "OFFENSIVE",
    "activeStrategyName": "Rendimiento Ofensivo",
    "activeStrategyVersion": 1,
    "lastRecalculationAt": "2026-11-24T18:00:00",
    "coveredPlayers": 50,
    "averageQuote": 4.2500,
    "minQuote": 1.0000,
    "maxQuote": 14.8000
  },
  "alerts": {
    "activeAlerts": 5,
    "triggeredAlerts": 3
  },
  "performance": {
    "rankingCacheHits": 120,
    "rankingCacheMisses": 4,
    "rankingCacheHitRatioPercent": 96.77,
    "avgRankingLatencyMs": 4.12
  },
  "generatedAt": "2026-11-24T21:35:00.123Z"
}
```

#### Respuestas de Error
- **`401 Unauthorized`**: Petición sin credenciales JWT/API Key válidas.
- **`403 Forbidden`** (`application/problem+json`): Usuario autenticado sin rol `SUPERUSER`:
```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Acceso denegado: se requieren permisos exclusivos de superusuario para consultar métricas avanzadas.",
  "instance": "/api/metrics/advanced",
  "code": "ACCESS_DENIED",
  "errorCode": "ACCESS_DENIED",
  "timestamp": "2026-11-24T21:35:00.123Z",
  "correlationId": "b6d9f9f6-3b7e-4c11-94aa-2e8e2ef31a40"
}
```

---

## 2. Ranking Optimizado (Contrato Preservado)

### `GET /api/players/ranking`
- **Autenticación**: JWT o API Key (sin cambios de contrato respecto a Entrega 2).
- **Comportamiento optimizado**:
  - En `cache hit`: servido desde Redis (`players::ranking::active_ranking`) en `< 50 ms`, incrementando `cbo_ranking_cache_requests_total{result="hit"}`.
  - En `cache miss`: resuelto mediante 2 consultas indexadas (sin N+1), guardado en Redis y registrando `cbo_ranking_cache_requests_total{result="miss"}`.
  - En caída de Redis: fallback transparente a PostgreSQL sin error HTTP 500.
  - Invalidado automáticamente ante `POST /api/quotes/recalculate`, scheduler periódico y `PATCH /api/admin/strategies/{id}/activate`.

---

## 3. Endpoints de Monitoreo (Spring Boot Actuator y Prometheus)

Todos los siguientes endpoints son públicos (`permitAll`) para healthchecks, scraping de Prometheus y verificación operativa:

| Método | Endpoint | Content-Type | Propósito |
| :--- | :--- | :--- | :--- |
| `GET` | `/actuator/health` | `application/vnd.spring-boot.actuator.v3+json` | Estado de salud global (`UP`/`DOWN`) con detalle de `db`, `redis` y `diskSpace`. |
| `GET` | `/actuator/info` | `application/vnd.spring-boot.actuator.v3+json` | Metadatos de aplicación (nombre, versión `3.0.0`, descripción). |
| `GET` | `/actuator/metrics` | `application/vnd.spring-boot.actuator.v3+json` | Listado de nombres de métricas disponibles. |
| `GET` | `/actuator/metrics/{metricName}` | `application/vnd.spring-boot.actuator.v3+json` | Detalle y mediciones de una métrica específica (ej. `http.server.requests`, `cbo.ranking.cache.requests`). |
| `GET` | `/actuator/prometheus` | `text/plain; version=0.0.4` | Métricas estándar (HTTP, JVM, HikariCP) y personalizadas (`cbo_*`) en formato Prometheus. |

*Restricción de seguridad*: Endpoints administrativos sensibles (`shutdown`, `env`, `heapdump`, `beans`, `configprops`) permanecen deshabilitados/no expuestos.

---

## 4. Contrato de Trazabilidad (`X-Correlation-ID`) y Auditoría AOP

### Cabecera HTTP `X-Correlation-ID`
- Si el cliente envía `X-Correlation-ID`, el servidor conserva ese valor; si no lo envía (o está vacío), genera un UUID v4.
- El servidor devuelve siempre `X-Correlation-ID` en las cabeceras de respuesta HTTP y lo incluye en el campo `correlationId` de cualquier respuesta de error `ProblemDetail`.

### Formato de Línea de Auditoría (`AUDIT_LOGGER` → `stdout` y `logs/audit.log`)
```text
AUDIT | timestamp=2026-11-24T21:35:00.123Z | correlationId=b6d9f9f6-3b7e-4c11-94aa-2e8e2ef31a40 | user=admin@cbo.com | operation=POST /api/orders/buy (OrderController.buyTokens) | params={request={playerId=12, quantity=5}} | executionTimeMs=18 | outcome=SUCCESS
```
Ejemplo con datos sensibles redactados (`POST /api/auth/login`):
```text
AUDIT | timestamp=2026-11-24T21:35:05.004Z | correlationId=7c1a... | user=anonymous | operation=POST /api/auth/login (AuthController.login) | params={request={email=user@cbo.com, password=[REDACTED]}} | executionTimeMs=42 | outcome=SUCCESS
```
