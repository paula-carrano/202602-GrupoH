# Implementation Plan: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Branch**: `feature/spec-006-entrega3-observability-optimization` | **Date**: 2026-11-24 *(Updated: 2026-11-25)* | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-entrega3-observability-optimization/spec.md`

> [!IMPORTANT]
> **RESTRICCIÓN CRÍTICA DE FRONTEND (MÉTRICAS AVANZADAS):**  
> El frontend básico para visualizar las métricas avanzadas (`GET /metrics/advanced`) **DEBE** tomar como referencia visual, estilística y arquitectónica el frontend actual (`front/`), implementándose en archivos nuevos dedicados y **SIN tocar ningún archivo actual del frontend a menos que sea sumamente importante / estrictamente indispensable** (únicamente el registro mínimo indispensable de ruta en `front/src/App.jsx` si no existe otro punto de entrada, sin modificar estilos, componentes, hooks, servicios ni tests existentes).

> [!IMPORTANT]
> **CONVENCIÓN DE RUTAS REST (SIN `/api` NI `/v1`):**  
> Ningún endpoint de la especificación ni de los nuevos controladores debe llevar el prefijo `/api/` ni `/v1/` (`GET /metrics/advanced`, `GET /players/ranking`, `GET /players`, `POST /quotes/recalculate`, `PATCH /admin/strategies/{id}/activate`).

---

## Summary

Completar la **Entrega 3 (Final)** del Trabajo Práctico consolidando observabilidad, auditoría transversal, verificación arquitectónica automatizada, optimización de rendimiento, verificación del scheduler de scraping y vista básica aislada de métricas avanzadas:
1. **Tests de Arquitectura (`ArchUnit`)**: Verificación automática en perfil `unit`/CI de la separación por capas (`Controllers → Services → Repositories` y `Adapters`), ausencia de ciclos entre paquetes y prohibición de exponer entidades JPA desde controladores.
2. **Optimización de Ranking + Caché Redis Completa (`FR-002`, `FR-003`, `FR-004`)**: Eliminación del problema N+1 en `RankingService` mediante consultas batch e índices compuestos en PostgreSQL (`player_quotes(player_id, timestamp)` y `players(active)`), clave determinística por estrategia y versión (`active_ranking:<strategyCode>:v<version>`) con protección anti-*cache stampede*, caché Redis en ranking (`players::ranking`), catálogo (`players::catalog`) y respuestas externas (`players::external`), invalidación en recálculo / cambio de estrategia / persistencia de catálogo, conexión Docker `cbo-redis`, degradación elegante `200 OK` ante caída de Redis e instrumentación de métricas `cbo_ranking_*`.
3. **Verificación del Scheduler de Scraping (`FR-013`)**: Garantizar que `CatalogScheduler` + `CatalogSyncService` + `CatalogPersistenceService` actualicen correctamente los jugadores y estadísticas en PostgreSQL, invaliden cachés al completar la persistencia y ejecuten el recálculo de cotizaciones una vez persistidos los datos actualizados (sin condición de carrera asíncrona).
4. **Auditoría AOP + Correlation ID**: Filtro `CorrelationIdFilter` (`@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE)` en Phase 2, MDC + cabecera `X-Correlation-ID` unificada con `GlobalExceptionHandler` y `SecurityConfig`), aspecto `@Aspect` (`WebServiceAuditAspect`) con enmascaramiento estricto `[REDACTED]` y configuración `logback-spring.xml` escribiendo a `stdout` y al archivo rotativo dedicado `logs/audit.log`.
5. **Actuator + Prometheus**: Exposición pública segura de `/actuator/health` (con detalle de DB y Redis), `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` con métricas personalizadas `cbo_*`, más servicio `prometheus` preconfigurado en `docker-compose.yml`.
6. **Endpoint `GET /metrics/advanced` + Vista Básica en Frontend**: Reporte consolidado de negocio y rendimiento (con `generatedAt` en nivel raíz) restringido exclusivamente al `SUPERUSER` (`401`/`403`), acompañado de una vista básica aislada en `front/` creada en archivos nuevos tomando el front actual solo como referencia sin modificar archivos existentes salvo lo estrictamente indispensable.
7. **Cierre de Entrega**: Release Notes de Entrega 3 (`docs/release-notes-v3.0.0.md`), validación previa obligatoria de CI en `SUCCESS` + SonarCloud `< 10 issues`, creación de Tag anotado `v3.0.0` y Release en GitHub.

---

## Technical Context

**Language/Version**: Java 17 (Spring Boot `3.2.3`) + React / Vite (solo archivos nuevos dedicados para la vista básica de métricas avanzadas)  
**Primary Dependencies**: Spring Web, Spring Data JPA/Hibernate, Spring Security (JWT + API Keys), Spring Data Redis (`Lettuce`), Spring Boot Actuator, Micrometer Prometheus Registry (`micrometer-registry-prometheus`), Spring AOP (`spring-boot-starter-aop`), Logback (SLF4J), Springdoc OpenAPI v3, ArchUnit JUnit 5 (`archunit-junit5:1.3.0`)  
**Storage**: PostgreSQL 16 (persistencia principal), Redis 7 (caché distribuida), archivo local rotativo `logs/audit.log` (auditoría AOP, excluido de Git)  
**Testing**: JUnit 5 + Mockito + ArchUnit (perfil `unit`); Spring Boot Test + Testcontainers PostgreSQL (perfil `e2e`); Vitest (solo test dedicado a la vista básica de métricas avanzadas sin romper tests existentes)  
**Target Platform**: Contenedores Docker Linux (`docker-compose.yml`) y CI GitHub Actions  
**Project Type**: Web-service REST backend (Spring Boot) + configuración de stack Docker Compose + vista básica aislada en Frontend React (`front/`)  
**Performance Goals**: `GET /players/ranking` `< 50 ms` p95 con caché caliente y hit ratio `> 90%`; endpoints de observabilidad y `GET /metrics/advanced` `< 500 ms`  
**Constraints**: Rutas sin prefijo `/api` ni `/v1` (`GET /metrics/advanced`, `GET /players/ranking`, etc.); errores en español con `ProblemDetail` (RFC 7807) y `correlationId`; 0 secretos en logs ni repositorio; no modificar archivos existentes del frontend salvo lo estrictamente indispensable; SonarCloud `Issues < 10`; build CI en estado `SUCCESS`  
**Scale/Scope**: 6 User Stories (4 P1 + 2 P2), 13 Requerimientos Funcionales (`FR-001` a `FR-013`), 7 Criterios de Éxito (`SC-001` a `SC-007`)

---

## Constitution Check

*GATE: Evaluated before Phase 0 and re-checked after Phase 1 & post-analysis remediation.*

| Principio Constitucional | Estado | Evidencia / Diseño |
| :--- | :--- | :--- |
| **§3.1 Arquitectura en capas** (`Controllers → Services → Repositories → Adapters`) | **PASS** | `AdvancedMetricsController → AdvancedMetricsService → Repositories`. Además se formaliza como gate automático en `ArchitectureTest` (ArchUnit). |
| **§3.3 DTOs y Entidades** | **PASS** | Todos los endpoints nuevos y existentes exponen únicamente DTOs (`AdvancedMetricsResponseDto`, etc.); validado por ArchUnit. |
| **§4 Seguridad y Datos Sensibles** | **PASS** | `GET /metrics/advanced` restringido a `SUPERUSER`; endpoints destructivos de Actuator deshabilitados; `AuditParameterSanitizer` enmascara cualquier secreto (`[REDACTED]`). |
| **§5 Manejo de Errores en Español (`ProblemDetail` + `correlationId`)** | **PASS** | `CorrelationIdFilter` + `MDC` unifica `correlationId` entre respuestas exitosas, `GlobalExceptionHandler` y `SecurityConfig` (`403 ACCESS_DENIED`). |
| **§6.1 Calidad y CI (SonarCloud `< 10 issues`)** | **PASS** | Sin duplicación, sin dependencias cíclicas, sin `System.out`, sin fugas de recursos; cubierto por perfiles `unit`, `e2e` y Vitest. |
| **§8 Entrega 3 (Observabilidad, Auditoría, Optimización, Tag/Release)** | **PASS** | Cubre íntegramente §8.1 a §8.8 más §9 (Caché Redis con degradación elegante) y verificación de scheduler de scraping (§7.6). |
| **§10 Perfiles de Testing (`unit` y `e2e` sobre PostgreSQL)** | **PASS** | Sin H2/HSQLDB; ArchUnit y unit tests en `-Punit`, pruebas de integración en `-Pe2e` con Testcontainers PostgreSQL. |
| **§11 API REST (sin `/api` ni `/v1`)** | **PASS** | `GET /metrics/advanced` y todas las rutas especificadas sin prefijo `/api` ni `/v1`, documentadas en Swagger/OpenAPI v3. |
| **§12 Frontend (consumo exclusivo REST, sin reglas de negocio)** | **PASS** | Vista básica aislada en archivos nuevos tomando `front/` como referencia visual sin alterar archivos existentes salvo el mínimo indispensable. |

**Gate Result**: **PASS** (0 violaciones; no requiere justificación en Complexity Tracking).

---

## Project Structure

### Documentation (this feature)

```text
specs/006-entrega3-observability-optimization/
├── spec.md                                   # Especificación funcional (Ready for Implementation)
├── plan.md                                   # Este plan técnico (/speckit-plan)
├── research.md                               # Hallazgos del codebase y decisiones técnicas
├── data-model.md                             # Índices, DTOs, esquema de auditoría, claves Redis y métricas
├── quickstart.md                             # Guía de validación end-to-end
├── analysis-report.md                        # Reporte de análisis cruzado actualizado (0 issues pendientes)
├── checklists/
│   └── requirements.md                       # Checklist de calidad de especificación (16/16)
├── contracts/
│   └── observability-and-metrics-api.md      # Contratos REST (sin /api ni /v1), Actuator/Prometheus y auditoría
└── tasks.md                                  # Lista de tareas ordenada y accionable
```

### Source Code (repository root)

```text
back/
├── pom.xml                                   # + spring-boot-starter-aop, micrometer-registry-prometheus, archunit-junit5
├── src/main/resources/
│   ├── application.yml                       # + bloque management (actuator/prometheus/health/info) y propiedades TTL Redis
│   └── logback-spring.xml                    # [NUEVO] Appenders STDOUT estructurado + RollingFile logs/audit.log
├── src/main/java/com/cbo/players/
│   ├── adapter/
│   │   └── ScrapingRestAdapter.java          # [ACTUALIZAR] Caché Redis players::external y fallback ante caída de proveedor
│   ├── audit/                                # [NUEVO] Capa transversal de auditoría AOP y trazabilidad
│   │   ├── AuditParameterSanitizer.java      # Sanitización recursiva de secretos y truncado seguro
│   │   ├── CorrelationIdFilter.java          # @Component @Order(HIGHEST_PRECEDENCE): X-Correlation-ID <-> MDC
│   │   └── WebServiceAuditAspect.java        # @Aspect interceptando @RestController en com.cbo.players.controller
│   ├── config/
│   │   └── RedisCacheConfig.java             # [ACTUALIZAR] ObjectMapper con JavaTimeModule, enableStatistics, TTLs (ranking/catalog/external), CacheErrorHandler
│   ├── controller/
│   │   └── AdvancedMetricsController.java    # [NUEVO] GET /metrics/advanced (sin prefijo /api ni /v1, SUPERUSER)
│   ├── dto/response/
│   │   ├── AdvancedMetricsResponseDto.java   # [NUEVO] Reporte consolidado (generatedAt en nivel raíz)
│   │   ├── AlertMetricsDto.java              # [NUEVO] Bloque alertas
│   │   ├── MarketMetricsDto.java             # [NUEVO] Bloque mercado y liquidez
│   │   ├── PerformanceMetricsDto.java        # [NUEVO] Bloque caché y latencia
│   │   ├── TopTradedPlayerDto.java           # [NUEVO] Top jugadores negociados
│   │   └── ValuationMetricsDto.java          # [NUEVO] Bloque valoración y estrategia activa
│   ├── exception/
│   │   └── GlobalExceptionHandler.java       # [ACTUALIZAR] Reutilizar correlationId desde MDC
│   ├── model/
│   │   ├── Player.java                       # [ACTUALIZAR] + índice idx_players_active
│   │   ├── PlayerQuote.java                  # [ACTUALIZAR] + índice compuesto idx_player_quotes_player_timestamp
│   │   └── Transaction.java                  # [ACTUALIZAR] + índice idx_transactions_operation_type
│   ├── repository/
│   │   ├── PlayerRepository.java             # [ACTUALIZAR] Verificar/usar findAllByActiveTrue() indexado
│   │   ├── PlayerQuoteRepository.java        # [ACTUALIZAR] + consulta batch última cotización sin N+1 + estadísticas
│   │   ├── PortfolioPositionRepository.java  # [ACTUALIZAR] + suma de tokens en circulación (excluyendo superusuario)
│   │   ├── PriceAlertRepository.java         # [ACTUALIZAR] + countByStatus(...)
│   │   └── TransactionRepository.java        # [ACTUALIZAR] + agregaciones por tipo, volumen y top jugadores negociados
│   ├── security/
│   │   └── SecurityConfig.java               # [ACTUALIZAR] Permitir /actuator/{health,info,metrics,prometheus}; proteger /metrics/** (SUPERUSER); correlationId en 403
│   └── service/
│       ├── AdvancedMetricsService.java       # [NUEVO] Agregación de negocio + telemetría Micrometer
│       ├── CatalogPersistenceService.java    # [ACTUALIZAR] Invalidar cachés players::catalog y players::ranking al persistir datos actualizados
│       ├── CatalogScheduler.java             # [ACTUALIZAR] Garantizar actualización completa en DB antes de invalidar cachés y recalcular cotizaciones
│       ├── CatalogSyncService.java           # [ACTUALIZAR] Callback/coordinación post-persistencia para scheduler e invalidación de caché
│       ├── OrderService.java                 # [ACTUALIZAR] + contador Micrometer cbo_orders_executed_total
│       ├── PlayerService.java                # [ACTUALIZAR] Consolidar caché Redis players::catalog con fallback a PostgreSQL
│       ├── QuoteCalculationService.java      # [ACTUALIZAR] + contador Micrometer cbo_quotes_recalculations_total
│       ├── RankingService.java               # [ACTUALIZAR] Eliminar N+1 + clave active_ranking:<code:vN> + anti-stampede + métricas Micrometer
│       └── ValuationStrategyService.java     # [ACTUALIZAR] + invalidación de caché players::ranking al activar estrategia
└── src/test/java/com/cbo/players/
    ├── architecture/
    │   └── ArchitectureTest.java             # [NUEVO] Reglas ArchUnit de capas, ciclos y DTOs
    ├── audit/
    │   ├── AuditParameterSanitizerTest.java  # [NUEVO] Unit test de enmascaramiento de secretos
    │   ├── CorrelationIdFilterTest.java      # [NUEVO] Unit test de generación/propagación y limpieza MDC
    │   └── WebServiceAuditAspectTest.java    # [NUEVO] Unit test de formato y medición de tiempo AOP
    ├── service/
    │   ├── AdvancedMetricsServiceTest.java   # [NUEVO] Unit test de cálculos y casos borde (división por cero)
    │   ├── CatalogSchedulerTest.java         # [NUEVO] Verificación de scheduler actualizando DB, invalidando cachés y recalculando
    │   ├── PlayerServiceCacheTest.java       # [NUEVO] Verificación caché players::catalog y degradación ante caída Redis (SC-006)
    │   └── RankingServiceTest.java           # [ACTUALIZAR] Verificar batch sin N+1, clave por estrategia, anti-stampede, hit ratio > 90% y fallback Redis
    └── integration/
        └── ObservabilityAndMetricsE2ETest.java # [NUEVO] E2E: Actuator, Prometheus, /metrics/advanced (200/403/401), Correlation ID, SC-001 (<50ms, >90% hit ratio), SC-006 (fallback Redis), consistencia ranking

front/                                        # [POLÍTICA STRICT ZERO/MINIMAL-TOUCH: SOLO ARCHIVOS NUEVOS USANDO front/ COMO REFERENCIA]
└── src/
    ├── services/
    │   └── advancedMetrics.js                # [NUEVO] Cliente HTTP aislado para GET /metrics/advanced
    ├── components/
    │   └── AdvancedMetricsPanel.jsx          # [NUEVO] Vista básica de bloques market/valuation/alerts/performance + generatedAt
    ├── pages/
    │   └── AdvancedMetricsPage.jsx           # [NUEVO] Página contenedora reutilizando referencia visual del dashboard sin alterar componentes actuales
    ├── test/
    │   └── advanced-metrics.test.jsx         # [NUEVO] Suite Vitest aislada para la vista básica de métricas avanzadas
    └── App.jsx                               # [SOLO SI ES ESTRICTAMENTE INDISPENSABLE: únicamente agregar <Route path="/metrics/advanced" ... /> sin tocar rutas existentes]

monitoring/
└── prometheus.yml                            # [NUEVO] Configuración de scraping al backend (cbo-backend:8080/actuator/prometheus)

docs/
└── release-notes-v3.0.0.md                   # [NUEVO] Release Notes formales de Entrega 3

docker-compose.yml                            # [ACTUALIZAR] Inyectar REDIS_HOST/REDIS_PORT al backend + agregar servicio prometheus
.gitignore                                    # [ACTUALIZAR] Excluir logs/ y *.log de auditoría local
```

**Structure Decision**: Se preserva la estructura monorepo existente (`back/`, `front/`, `scraping/`, `docs/`), introduciendo `com.cbo.players.audit` en `back/`, `monitoring/prometheus.yml`, `docs/release-notes-v3.0.0.md` y 4 archivos nuevos aislados en `front/src/` para la vista básica de métricas avanzadas, respetando estrictamente la prohibición de modificar archivos actuales de `front/` salvo el registro mínimo indispensable de ruta en `App.jsx`.

---

## Complexity Tracking

> No existen violaciones a la Constitución del Proyecto que requieran justificación excepcional.
