# Data Model & Observability Schemas: Entrega 3

**Feature**: `006-entrega3-observability-optimization`  
**Date**: 2026-11-24 *(Updated: 2026-11-25)*  
**Spec**: [spec.md](./spec.md)

---

## 1. Optimizaciones de Esquema e Índices (PostgreSQL / JPA)

No se crean nuevas tablas en base de datos; se optimizan índices existentes para acelerar las consultas de ranking, catálogo y métricas avanzadas:

| Tabla / Entidad | Índice | Columnas | Propósito |
| :--- | :--- | :--- | :--- |
| `player_quotes` (`PlayerQuote`) | `idx_player_quotes_player_timestamp` *(nuevo)* | `player_id, timestamp` | Eliminar N+1 y full-table scans al obtener la última cotización por jugador y al calcular estadísticas de cotización. |
| `players` (`Player`) | `idx_players_active` *(nuevo)* | `active` | Filtrado directo de jugadores activos en `findAllByActiveTrue()` para ranking, catálogo y métricas. |
| `transactions` (`Transaction`) | `idx_transactions_operation_type` *(nuevo)* | `operation_type` | Acelerar agregaciones por tipo de operación (`COMPRA`, `VENTA`, `RECARGA`, `CAMBIO_ESTRATEGIA`) en `GET /metrics/advanced`. |

---

## 2. DTOs de Respuesta — Métricas Avanzadas (`GET /metrics/advanced`)

### `AdvancedMetricsResponseDto`
Objeto raíz retornado por `GET /metrics/advanced` *(con `generatedAt` en el nivel raíz, alineado con `spec.md` y `contracts/`)*:

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `market` | `MarketMetricsDto` | Indicadores agregados de operaciones financieras, liquidez y jugadores más operados. |
| `valuation` | `ValuationMetricsDto` | Estado de la estrategia activa y distribución de cotizaciones vigentes. |
| `alerts` | `AlertMetricsDto` | Estado agregado del motor de alertas de precio. |
| `performance` | `PerformanceMetricsDto` | Telemetría en tiempo real de caché de ranking y latencia. |
| `generatedAt` | `Instant` (ISO-8601 UTC) | Marca temporal de generación del reporte (nivel raíz). |

### `MarketMetricsDto`
| Campo | Tipo | Regla / Validación |
| :--- | :--- | :--- |
| `totalTransactions` | `long` | `>= 0` (suma de todas las operaciones registradas). |
| `transactionsByType` | `Map<String, Long>` | Conteo por tipo (`COMPRA`, `VENTA`, `RECARGA`, `CAMBIO_ESTRATEGIA`); valores en `0` si no existen registros. |
| `totalTradedCredits` | `BigDecimal` | Suma de `totalAmount` en operaciones `COMPRA` y `VENTA`; `0.0000` si no hay operaciones. |
| `activeUsers` | `long` | Cantidad de usuarios registrados activos en el sistema. |
| `totalIssuedTokens` | `long` | `activePlayers * 100`. |
| `tokensInCirculation` | `long` | Suma de tokens actualmente en manos de usuarios no superusuario. |
| `circulationRatioPercent` | `double` | `(tokensInCirculation / totalIssuedTokens) * 100`, redondeado a 2 decimales (`0.0` si `totalIssuedTokens == 0`). |
| `topTradedPlayers` | `List<TopTradedPlayerDto>` | Top 5 jugadores por volumen total de tokens operados (`COMPRA` + `VENTA`), ordenados descendentemente. |

### `TopTradedPlayerDto`
| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `playerId` | `Long` | Identificador del jugador. |
| `playerName` | `String` | Nombre completo (`firstName + " " + lastName`). |
| `team` | `String` | Equipo actual del jugador. |
| `totalTokensTraded` | `long` | Cantidad acumulada de tokens negociados. |
| `totalVolumeCredits` | `BigDecimal` | Monto total negociado en créditos. |

### `ValuationMetricsDto`
| Campo | Tipo | Regla / Validación |
| :--- | :--- | :--- |
| `activeStrategyCode` | `String` | Código de la estrategia activa (ej. `OFFENSIVE`, `BALANCED`). |
| `activeStrategyName` | `String` | Nombre legible de la estrategia activa. |
| `activeStrategyVersion` | `int` | Versión vigente de la estrategia activa. |
| `lastRecalculationAt` | `LocalDateTime` (nullable) | Timestamp UTC de la cotización más reciente registrada (`null` si aún no hubo recálculos). |
| `coveredPlayers` | `long` | Cantidad de jugadores activos en catálogo. |
| `averageQuote` | `BigDecimal` | Promedio de cotizaciones vigentes (`0.0000` si no hay jugadores). |
| `minQuote` | `BigDecimal` | Cotización vigente mínima (`0.0000` si no hay jugadores). |
| `maxQuote` | `BigDecimal` | Cotización vigente máxima (`0.0000` si no hay jugadores). |

### `AlertMetricsDto`
| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `activeAlerts` | `long` | Cantidad de alertas en estado `ACTIVA`. |
| `triggeredAlerts` | `long` | Cantidad de alertas en estado `DISPARADA`. |

### `PerformanceMetricsDto`
| Campo | Tipo | Regla / Validación |
| :--- | :--- | :--- |
| `rankingCacheHits` | `long` | Total acumulado de aciertos de caché en consultas de ranking. |
| `rankingCacheMisses` | `long` | Total acumulado de fallos de caché en consultas de ranking. |
| `rankingCacheHitRatioPercent` | `double` | `(hits / (hits + misses)) * 100`, redondeado a 2 decimales (`0.0` si `hits + misses == 0`). |
| `avgRankingLatencyMs` | `double` | Latencia promedio en milisegundos de `GET /players/ranking` (`0.0` si aún no hubo consultas). |

---

## 3. Modelo de Registro de Auditoría AOP (`WebServiceAuditRecord`)

Cada invocación a un método público de un `@RestController` de negocio emite una línea estructurada a través del logger `AUDIT_LOGGER` (a consola `stdout` y a `logs/audit.log`):

| Campo | Tipo / Formato | Descripción |
| :--- | :--- | :--- |
| `timestamp` | ISO-8601 UTC (`2026-11-24T21:30:00.123Z`) | Instante de inicio/registro de la invocación. |
| `correlationId` | UUID v4 / Header `X-Correlation-ID` | Identificador único de trazabilidad de la petición (propagado vía MDC). |
| `user` | `String` | Email/username del `Authentication` activo en `SecurityContextHolder`, o `anonymous` si no hay usuario autenticado. |
| `operation` | `String` | Método HTTP + URI + `ControllerSimpleName.methodName` (ej. `POST /orders/buy \| OrderController.buyTokens`). |
| `parameters` | `String` (sanitizado) | Mapa legible `paramName=value` con campos sensibles reemplazados por `[REDACTED]` y valores largos truncados a 500 caracteres. |
| `executionTimeMs` | `long` | Tiempo total de ejecución del método en milisegundos (`System.nanoTime()` convertido a ms). |
| `outcome` | `SUCCESS` \| `ERROR` | Resultado de la ejecución (si lanza excepción, incluye nombre simple de la excepción o código de error sin stack trace). |

### Reglas de Sanitización de Parámetros
- Claves / propiedades de DTO enmascaradas (case-insensitive, coincidencia parcial): `password`, `passwd`, `secret`, `token`, `apikey`, `api_key`, `authorization`, `credential`, `jwt`, `key`.
- Tipos omitidos o resumidos por seguridad/tamaño: `HttpServletRequest`, `HttpServletResponse`, `Principal`, `Authentication`, `BindingResult`, `InputStream`, `MultipartFile`.

---

## 4. Claves de Caché Redis, Protección contra Cache Stampede y Política de Invalidación

| Cache Name | Key Pattern | TTL por defecto | Protección Stampede | Disparadores de Invalidación |
| :--- | :--- | :--- | :--- | :--- |
| `players::ranking` | `active_ranking:<strategyCode>:v<version>` | 1 hora (configurable) | Bloqueo sincronizado por clave (`ConcurrentHashMap` / `sync`) en recarga de *cache miss* | `QuoteCalculationService.recalculateAllQuotes()`, `ValuationStrategyService.activateStrategy(...)`, finalización de sincronización en `CatalogPersistenceService`/`CatalogSyncService`. |
| `players::catalog` | `'all_players'` / `'player:' + playerId` | 15 minutos (configurable) | Carga segura con fallback a PostgreSQL | `QuoteCalculationService.recalculateAllQuotes()`, `CatalogPersistenceService.importSquad()`, `CatalogPersistenceService.saveStatistics()`. |
| `players::external` | `'teams:' + league` / `'squad:' + teamId` / `'stats:' + whoscoredId` | 30 minutos (configurable) | Fallback a caché/datos locales ante fallo del proveedor | Expiración por TTL y actualización en sincronizaciones exitosas del scheduler. |

---

## 5. Catálogo de Métricas Personalizadas (Micrometer / Prometheus)

| Nombre de Métrica | Tipo | Tags | Descripción |
| :--- | :--- | :--- | :--- |
| `cbo_ranking_cache_requests_total` | Counter | `result=hit\|miss` | Cantidad de consultas de ranking resueltas desde Redis (`hit`) vs. recalculadas desde PostgreSQL (`miss`). |
| `cbo_ranking_query_duration_seconds` | Timer | `source=cache\|db` | Distribución de latencia de las consultas al ranking de jugadores. |
| `cbo_orders_executed_total` | Counter | `type=buy\|sell` | Cantidad de órdenes de compra y venta ejecutadas exitosamente. |
| `cbo_quotes_recalculations_total` | Counter | `strategy=<code>` | Cantidad de ejecuciones de recálculo masivo de cotizaciones. |
