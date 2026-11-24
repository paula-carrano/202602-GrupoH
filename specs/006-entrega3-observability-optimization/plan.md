# Implementation Plan: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Branch**: `006-entrega3-observability-optimization` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-entrega3-observability-optimization/spec.md`

---

## Summary

Completar la **Entrega 3 (Final)** del Trabajo Práctico consolidando observabilidad, auditoría transversal, verificación arquitectónica automatizada y optimización de rendimiento en el backend Spring Boot:
1. **Tests de Arquitectura (`ArchUnit`)**: Verificación automática en perfil `unit`/CI de la separación por capas (`Controllers → Services → Repositories` y `Adapters`), ausencia de ciclos entre paquetes y prohibición de exponer entidades JPA desde controladores.
2. **Optimización de Ranking + Caché Redis**: Eliminación del problema N+1 en `RankingService` mediante consultas batch e índices compuestos en PostgreSQL (`player_quotes(player_id, timestamp)` y `players(active)`), consolidación de Redis (serialización JSON con soporte `JavaTime`, conexión Docker `cbo-redis`, invalidación en recálculo y cambio de estrategia, y degradación elegante ante fallos) e instrumentación de métricas de caché.
3. **Auditoría AOP + Correlation ID**: Filtro `CorrelationIdFilter` (MDC + cabecera `X-Correlation-ID` unificada con `GlobalExceptionHandler`), aspecto `@Aspect` (`WebServiceAuditAspect`) con enmascaramiento estricto de credenciales y configuración `logback-spring.xml` escribiendo a `stdout` y al archivo rotativo dedicado `logs/audit.log`.
4. **Actuator + Prometheus**: Exposición pública segura de `/actuator/health` (con detalle de DB y Redis), `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` con métricas personalizadas `cbo_*`, más servicio `prometheus` preconfigurado en `docker-compose.yml`.
5. **Endpoint de Métricas Avanzadas (`GET /api/metrics/advanced`)**: Reporte consolidado de negocio y rendimiento restringido exclusivamente al `SUPERUSER` (`401`/`403`).
6. **Cierre de Entrega**: Release Notes de Entrega 3 (`docs/release-notes-v3.0.0.md`), validación CI + SonarCloud `< 10 issues`, creación de Tag `v3.0.0` y Release en GitHub.

---

## Technical Context

**Language/Version**: Java 17 (Spring Boot `3.2.3`)  
**Primary Dependencies**: Spring Web, Spring Data JPA/Hibernate, Spring Security (JWT + API Keys), Spring Data Redis (`Lettuce`), Spring Boot Actuator, Micrometer Prometheus Registry (`micrometer-registry-prometheus`), Spring AOP (`spring-boot-starter-aop`), Logback (SLF4J), Springdoc OpenAPI v3, ArchUnit JUnit 5 (`archunit-junit5:1.3.0`)  
**Storage**: PostgreSQL 16 (persistencia principal), Redis 7 (caché distribuida), archivo local rotativo `logs/audit.log` (auditoría AOP, excluido de Git)  
**Testing**: JUnit 5 + Mockito + ArchUnit (perfil `unit`); Spring Boot Test + Testcontainers PostgreSQL (perfil `e2e`)  
**Target Platform**: Contenedores Docker Linux (`docker-compose.yml`) y CI GitHub Actions  
**Project Type**: Web-service REST backend (Spring Boot) + configuración de stack Docker Compose (alcance exclusivo Backend; Frontend React fuera de alcance)  
**Performance Goals**: `GET /api/players/ranking` `< 50 ms` p95 con caché caliente y hit ratio `> 90%`; endpoints de observabilidad y `GET /api/metrics/advanced` `< 500 ms`  
**Constraints**: Rutas API bajo `/api/...` sin `/v1`; errores en español con `ProblemDetail` (RFC 7807) y `correlationId`; 0 secretos en logs ni repositorio; SonarCloud `Issues < 10`; build CI en estado `SUCCESS`  
**Scale/Scope**: 6 User Stories (4 P1 + 2 P2), 12 Requerimientos Funcionales (`FR-001` a `FR-012`), 7 Criterios de Éxito (`SC-001` a `SC-007`)

---

## Constitution Check

*GATE: Evaluated before Phase 0 and re-checked after Phase 1 design.*

| Principio Constitucional | Estado | Evidencia / Diseño |
| :--- | :--- | :--- |
| **§3.1 Arquitectura en capas** (`Controllers → Services → Repositories → Adapters`) | **PASS** | `AdvancedMetricsController → AdvancedMetricsService → Repositories`. Además se formaliza como gate automático en `ArchitectureTest` (ArchUnit). |
| **§3.3 DTOs y Entidades** | **PASS** | Todos los endpoints nuevos y existentes exponen únicamente DTOs (`AdvancedMetricsResponseDto`, etc.); validado por ArchUnit. |
| **§4 Seguridad y Datos Sensibles** | **PASS** | `GET /api/metrics/advanced` restringido a `SUPERUSER`; endpoints destructivos de Actuator deshabilitados; `AuditParameterSanitizer` enmascara cualquier secreto (`[REDACTED]`). |
| **§5 Manejo de Errores en Español (`ProblemDetail` + `correlationId`)** | **PASS** | `CorrelationIdFilter` + `MDC` unifica `correlationId` entre respuestas exitosas, `GlobalExceptionHandler` y `SecurityConfig` (`403 ACCESS_DENIED`). |
| **§6.1 Calidad y CI (SonarCloud `< 10 issues`)** | **PASS** | Sin duplicación, sin dependencias cíclicas, sin `System.out`, sin fugas de recursos; cubierto por perfiles `unit` y `e2e`. |
| **§8 Entrega 3 (Observabilidad, Auditoría, Optimización, Tag/Release)** | **PASS** | Cubre íntegramente §8.1 a §8.8 más §9 (Caché Redis con degradación elegante). |
| **§10 Perfiles de Testing (`unit` y `e2e` sobre PostgreSQL)** | **PASS** | Sin H2/HSQLDB; ArchUnit y unit tests en `-Punit`, pruebas de integración en `-Pe2e` con Testcontainers PostgreSQL. |
| **§11 API REST (sin `/v1`)** | **PASS** | `GET /api/metrics/advanced` consistente con todos los controladores actuales (`/api/...`) y documentado en Swagger/OpenAPI v3. |

**Gate Result**: **PASS** (0 violaciones; no requiere justificación en Complexity Tracking).

---

## Project Structure

### Documentation (this feature)

```text
specs/006-entrega3-observability-optimization/
├── spec.md                                   # Especificación funcional clarificada
├── plan.md                                   # Este plan técnico (/speckit-plan)
├── research.md                               # Hallazgos del codebase y decisiones técnicas
├── data-model.md                             # Índices, DTOs, esquema de auditoría, claves Redis y métricas
├── quickstart.md                             # Guía de validación end-to-end
├── checklists/
│   └── requirements.md                       # Checklist de calidad de especificación (16/16)
├── contracts/
│   └── observability-and-metrics-api.md      # Contratos REST, Actuator/Prometheus y formato de auditoría
└── tasks.md                                  # A generar por /speckit-tasks
```

### Source Code (repository root)

```text
back/
├── pom.xml                                   # + spring-boot-starter-aop, micrometer-registry-prometheus, archunit-junit5
├── src/main/resources/
│   ├── application.yml                       # + bloque management (actuator/prometheus/health/info) y propiedades TTL
│   └── logback-spring.xml                    # [NUEVO] Appenders STDOUT estructurado + RollingFile logs/audit.log
├── src/main/java/com/cbo/players/
│   ├── audit/                                # [NUEVO] Capa transversal de auditoría AOP y trazabilidad
│   │   ├── AuditParameterSanitizer.java      # Sanitización recursiva de secretos y truncado seguro
│   │   ├── CorrelationIdFilter.java          # OncePerRequestFilter: X-Correlation-ID <-> MDC
│   │   └── WebServiceAuditAspect.java        # @Aspect interceptando @RestController en com.cbo.players.controller
│   ├── config/
│   │   └── RedisCacheConfig.java             # [ACTUALIZAR] ObjectMapper con JavaTimeModule, enableStatistics, TTLs configurables
│   ├── controller/
│   │   └── AdvancedMetricsController.java    # [NUEVO] GET /api/metrics/advanced (SUPERUSER)
│   ├── dto/response/
│   │   ├── AdvancedMetricsResponseDto.java   # [NUEVO] Reporte consolidado
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
│   │   ├── PlayerRepository.java             # [ACTUALIZAR] + findByActiveTrue()
│   │   ├── PlayerQuoteRepository.java        # [ACTUALIZAR] + consulta batch última cotización sin N+1 + estadísticas
│   │   ├── PortfolioPositionRepository.java  # [ACTUALIZAR] + suma de tokens en circulación (excluyendo superusuario)
│   │   ├── PriceAlertRepository.java         # [ACTUALIZAR] + countByStatus(...)
│   │   └── TransactionRepository.java        # [ACTUALIZAR] + agregaciones por tipo, volumen y top jugadores negociados
│   ├── security/
│   │   └── SecurityConfig.java               # [ACTUALIZAR] Permitir /actuator/{health,info,metrics,prometheus}; proteger /api/metrics/** (SUPERUSER); correlationId en 403
│   └── service/
│       ├── AdvancedMetricsService.java       # [NUEVO] Agregación de negocio + telemetría Micrometer
│       ├── OrderService.java                 # [ACTUALIZAR] + contador Micrometer cbo_orders_executed_total
│       ├── QuoteCalculationService.java      # [ACTUALIZAR] + contador Micrometer cbo_quotes_recalculations_total
│       ├── RankingService.java               # [ACTUALIZAR] Eliminar N+1 + métricas explícitas cbo_ranking_cache_requests_total / timer
│       └── ValuationStrategyService.java     # [ACTUALIZAR] + invalidación de caché players::ranking al activar estrategia
└── src/test/java/com/cbo/players/
    ├── architecture/
    │   └── ArchitectureTest.java             # [NUEVO] Reglas ArchUnit de capas, ciclos y DTOs
    ├── audit/
    │   ├── AuditParameterSanitizerTest.java  # [NUEVO] Unit test de enmascaramiento de secretos
    │   ├── CorrelationIdFilterTest.java      # [NUEVO] Unit test de generación/propagación de X-Correlation-ID
    │   └── WebServiceAuditAspectTest.java    # [NUEVO] Unit test de formato y medición de tiempo AOP
    ├── service/
    │   ├── AdvancedMetricsServiceTest.java   # [NUEVO] Unit test de cálculos y casos borde (división por cero)
    │   └── RankingServiceTest.java           # [ACTUALIZAR] Verificar consulta batch sin N+1 y registro de métricas
    └── integration/
        └── ObservabilityAndMetricsE2ETest.java # [NUEVO] E2E: Actuator, Prometheus, /api/metrics/advanced (200/403/401), Correlation ID, Ranking

monitoring/
└── prometheus.yml                            # [NUEVO] Configuración de scraping al backend (cbo-backend:8080/actuator/prometheus)

docs/
└── release-notes-v3.0.0.md                   # [NUEVO] Release Notes formales de Entrega 3

docker-compose.yml                            # [ACTUALIZAR] Inyectar REDIS_HOST/REDIS_PORT al backend + agregar servicio prometheus
.gitignore                                    # [ACTUALIZAR] Excluir logs/ y *.log de auditoría local
```

**Structure Decision**: Se preserva la estructura monorepo existente (`back/`, `front/`, `scraping/`, `docs/`) introduciendo únicamente el paquete transversal `com.cbo.players.audit` dentro de `back/` (para mantener separados el aspecto AOP, el sanitizador y el filtro de `CorrelationId`), el directorio `monitoring/` para `prometheus.yml`, y `docs/release-notes-v3.0.0.md` para el cierre de entrega. El directorio `front/` no se modifica.

---

## Complexity Tracking

> No existen violaciones a la Constitución del Proyecto que requieran justificación excepcional.
