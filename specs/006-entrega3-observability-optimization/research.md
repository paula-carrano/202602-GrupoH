# Research & Technical Decisions: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Feature**: `006-entrega3-observability-optimization`  
**Date**: 2026-11-24  
**Spec**: [spec.md](./spec.md)

---

## Diagnóstico del Estado Actual del Backend (Codebase Audit)

Al inspeccionar el código actual de `back/` y `docker-compose.yml` se identificaron las siguientes brechas concretas que guían las decisiones técnicas:

1. **Prefijo real de rutas REST**: Todos los controladores actuales exponen rutas bajo `/api/...` sin `/v1` (ej. `/api/players/ranking`, `/api/admin/strategies/{id}/activate`, `/api/quotes/recalculate`). Por consistencia, el nuevo endpoint de métricas avanzadas se expondrá en `GET /api/metrics/advanced`.
2. **Problema N+1 en `RankingService`**: `RankingService.getRanking()` actualmente invoca `playerRepository.findAll()` (filtrando `isActive` en memoria) y luego ejecuta `playerQuoteRepository.findFirstByPlayerIdOrderByTimestampDesc(p.getId())` dentro de un loop por cada jugador activo. `PlayerQuote` carece de un índice compuesto `(player_id, timestamp)`.
3. **Brechas en Caché Redis**:
   - `docker-compose.yml` declara el servicio `redis` (`cbo-redis`), pero no inyecta `REDIS_HOST: cbo-redis` ni `REDIS_PORT: 6379` al servicio `backend` (en Docker el backend apunta a `localhost:6379`).
   - `ValuationStrategyService.activateStrategy(...)` no invalida la caché `players::ranking` al cambiar de estrategia activa.
   - `RedisCacheConfig` utiliza `GenericJackson2JsonRedisSerializer` sin `JavaTimeModule` y no expone estadísticas de caché hacia Micrometer.
4. **Faltantes de Actuator y Prometheus**:
   - `pom.xml` ya incluye `spring-boot-starter-actuator`, pero falta `io.micrometer:micrometer-registry-prometheus`.
   - `application.yml` no tiene sección `management:` (no expone `/actuator/info`, `/actuator/metrics` ni `/actuator/prometheus`, ni detalles de `/actuator/health`).
   - `SecurityConfig` solo permite públicamente `/actuator/health` (bloquea `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus`).
   - `docker-compose.yml` no incluye aún el servicio `prometheus` ni `monitoring/prometheus.yml`.
5. **Faltantes de Auditoría AOP y Correlation ID**:
   - `pom.xml` no incluye `spring-boot-starter-aop`.
   - No existe `CorrelationIdFilter` (MDC) y `GlobalExceptionHandler` genera un `UUID.randomUUID()` distinto por excepción en lugar de propagar el `correlationId` de la petición.
   - No existe `logback-spring.xml` con logger dedicado a `stdout` + archivo rotativo `logs/audit.log`.
6. **Faltantes de Tests de Arquitectura**:
   - `pom.xml` no incluye `com.tngtech.archunit:archunit-junit5`.
   - No existe suite de tests de arquitectura en `back/src/test/java/com/cbo/players/architecture/`.

---

## Decisiones Técnicas

### 1. Tests de Arquitectura con ArchUnit (JUnit 5)
- **Decision**: Incorporar `com.tngtech.archunit:archunit-junit5` (versión `1.3.0`, compatible con Java 17 y JUnit 5) en alcance `test` y crear `ArchitectureTest` dentro del paquete `com.cbo.players.architecture`, ejecutado automáticamente en el perfil `unit` (y en CI).
- **Rationale**: ArchUnit analiza bytecode compilado en milisegundos sin levantar contexto de Spring ni contenedores Docker, integrándose nativamente con JUnit 5 y Maven Surefire. Permite validar de forma declarativa la arquitectura en capas (`Controllers → Services → Repositories` y `Adapters`), ciclos entre paquetes (`slices().matching(...)`), ubicación/anotaciones de clases y prohibición de retornar entidades JPA (`@Entity`) desde métodos públicos de controladores.
- **Alternatives considered**:
  - *Spring Modulith*: orienta a módulos verticales y no a arquitectura clásica por capas del TP.
  - *Checkstyle / PMD*: no permiten expresar reglas ricas de grafos de dependencias entre capas ni verificación de firmas con `@Entity`.

### 2. Optimización de Ranking y Caché Distribuida con Redis
- **Decision**:
  - **Nivel Base de Datos**: Agregar índice compuesto `@Index(name = "idx_player_quotes_player_timestamp", columnList = "player_id, timestamp")` en `PlayerQuote` e índice `@Index(name = "idx_players_active", columnList = "active")` en `Player`. Reemplazar el loop N+1 de `RankingService` por consultas optimizadas: `playerRepository.findByActiveTrue()` + una única consulta batch en `PlayerQuoteRepository` que recupere la última cotización por jugador (resolviendo desempates por `id DESC`).
  - **Nivel Caché Redis**: Consolidar `RedisCacheConfig` con `ObjectMapper` configurado con `JavaTimeModule` (sin afectar el `ObjectMapper` global de MVC), `enableStatistics()` en `RedisCacheManager`, y configurar `REDIS_HOST: cbo-redis` + `depends_on: redis` en `docker-compose.yml`.
  - **Nivel Consistencia**: Invalidar `players::ranking` tanto en `QuoteCalculationService.recalculateAllQuotes()` como en `ValuationStrategyService.activateStrategy(...)` (y al sincronizar catálogo si cambian jugadores activos). Mantener `CacheErrorHandler` tolerante a fallos para degradación elegante a PostgreSQL ante caída de Redis.
  - **Telemetría de Caché**: Instrumentar explícitamente en `RankingService` / wrapper o vía Micrometer (`ranking.cache.hits`, `ranking.cache.misses` y `ranking.query.duration`) para que tanto Prometheus como `GET /api/metrics/advanced` reflejen hits, misses, hit ratio y latencia promedio con precisión incluso si Redis no está disponible.
- **Rationale**: Combinar optimización SQL (de O(N+1) queries a 2 queries indexadas) con Redis + invalidación determinística ataca tanto el `cache hit` (< 50 ms) como el `cache miss` y el escenario de degradación sin Redis.
- **Alternatives considered**:
  - *Vista materializada en PostgreSQL*: añade complejidad de DDL/refresh manual con Hibernate `ddl-auto: update` y duplica el propósito de Redis exigido por la consigna.
  - *Solo caché en memoria (Caffeine)*: incumple el requerimiento CORE explícito de Redis para caché.

### 3. Auditoría Transversal de Web-Services con Spring AOP, Logback y Correlation ID
- **Decision**:
  - Agregar `org.springframework.boot:spring-boot-starter-aop` en `pom.xml`.
  - Implementar `CorrelationIdFilter` (`OncePerRequestFilter`, alta prioridad antes de filtros de seguridad) que lea `X-Correlation-ID` o genere un UUID v4, lo cargue en `MDC` (`correlationId`), lo agregue como cabecera de respuesta `X-Correlation-ID` y limpie el `MDC` en `finally`. Actualizar `GlobalExceptionHandler` y `SecurityConfig` (`accessDeniedHandler`) para reutilizar `MDC.get("correlationId")` en lugar de generar UUIDs aislados.
  - Implementar `WebServiceAuditAspect` (`@Aspect`, `@Component`) interceptando métodos públicos de clases anotadas con `@RestController` dentro de `com.cbo.players.controller..*` (excluyendo automáticamente `/actuator/**` y Swagger porque no están en ese paquete).
  - Registrar el formato estructurado requerido: `timestamp=<ISO-8601 UTC> | correlationId=<id> | user=<email|anonymous> | operation=<HTTP_METHOD path | Controller.method> | params=<sanitized> | executionTimeMs=<ms> | outcome=<SUCCESS|ERROR>`.
  - Implementar `AuditParameterSanitizer` que enmascare recursivamente cualquier parámetro o campo de DTO/Map cuyo nombre contenga términos sensibles (`password`, `secret`, `token`, `apikey`, `api_key`, `authorization`, `credential`, `jwt`) con `[REDACTED]`, y trunque payloads extensos (> 500 caracteres) manejando valores `null` o tipos no serializables (`HttpServletRequest`, `HttpServletResponse`, `SseEmitter`, etc.) sin lanzar excepciones.
  - Configurar `back/src/main/resources/logback-spring.xml` con un logger dedicado `AUDIT_LOGGER` (`additivity="false"`) escribiendo tanto a `STDOUT` estructurado como a un `RollingFileAppender` en `logs/audit.log` (rotación diaria, retención 7 días, máx. 10 MB por archivo), agregando `logs/` a `.gitignore`.
- **Rationale**: Cumple al 100% el formato de la consigna (`<timestamp, user, operación/método, parámetros, tiempoDeEjecución>`), garantiza cero fugas de credenciales, mantiene el código de los controladores limpio y produce evidencia auditable en consola y archivo dedicado.
- **Alternatives considered**:
  - *HandlerInterceptor de Spring MVC*: no tiene acceso directo a los argumentos deserializados del método Java ni al nombre exacto del método del servicio/controlador de forma tan limpia como `@Around` en Spring AOP, y la consigna pide explícitamente AOP.
  - *Log4j2 en lugar de Logback*: Spring Boot 3 incluye Logback por defecto; cambiar a Log4j2 exige excluir `spring-boot-starter-logging` en todos los starters sin aportar beneficios para este alcance.

### 4. Spring Boot Actuator y Prometheus
- **Decision**:
  - Agregar `io.micrometer:micrometer-registry-prometheus` en `pom.xml`.
  - Configurar en `application.yml`:
    - `management.endpoints.web.exposure.include: health,info,metrics,prometheus`
    - `management.endpoint.health.show-details: always` (mostrando `db`, `redis`, `diskSpace`)
    - `management.endpoint.health.probes.enabled: true`
    - `management.metrics.tags.application: ${spring.application.name}`
    - `management.info.env.enabled: true` + bloque `info.app` (nombre, versión `3.0.0`, descripción).
  - Actualizar `SecurityConfig` para permitir públicamente `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/actuator/metrics`, `/actuator/metrics/**` y `/actuator/prometheus`.
  - Registrar métricas personalizadas con `MeterRegistry`:
    - `cbo_ranking_cache_requests_total` (tag `result=hit|miss`)
    - `cbo_ranking_query_duration_seconds` (`Timer`)
    - `cbo_orders_executed_total` (tag `type=buy|sell`)
    - `cbo_quotes_recalculations_total` (`Counter`)
  - Agregar el servicio `prometheus` (`prom/prometheus:v2.54.1`) en `docker-compose.yml` montando `./monitoring/prometheus.yml` con scrape interval `15s` apuntando a `cbo-backend:8080/actuator/prometheus`, puerto `9090:9090`.
- **Rationale**: Sigue directamente las guías oficiales de referencia (`baeldung.com/spring-boot-actuators` y `baeldung.com/spring-boot-prometheus`), asegura seguridad al exponer únicamente endpoints de lectura/observabilidad y permite demostración real de scraping en Docker.
- **Alternatives considered**:
  - *Exponer `*` en Actuator*: descartado por riesgo de seguridad (expondría `env`, `beans`, `heapdump`, `configprops`).

### 5. Endpoint de Métricas Avanzadas (`GET /api/metrics/advanced`)
- **Decision**:
  - Crear `AdvancedMetricsController` (`@RequestMapping("/api/metrics")`, `@GetMapping("/advanced")`) y `AdvancedMetricsService`, protegido en `SecurityConfig` con `.requestMatchers("/api/metrics/**").hasRole("SUPERUSER")` (retornando `401` sin autenticar y `403 ProblemDetail ACCESS_DENIED` para usuarios regulares).
  - Consolidar datos en tiempo real desde los repositorios existentes (`TransactionRepository`, `PlayerRepository`, `PlayerQuoteRepository`, `PortfolioPositionRepository`, `ValuationStrategyRepository`, `PriceAlertRepository`, `UserRepository`) y desde `MeterRegistry` (hits/misses/hitRatio de caché y latencia promedio del ranking), utilizando consultas agregadas JPQL ligeras (sin cargar tablas enteras en memoria).
- **Rationale**: Respeta estrictamente la arquitectura en capas (`Controller → Service → Repository`), evita problemas de rendimiento en las agregaciones y unifica negocio + telemetría técnica en un contrato JSON limpio documentado en Swagger.
- **Alternatives considered**:
  - *Construir las métricas avanzadas como un endpoint custom de Actuator (`/actuator/advanced-metrics`)*: descartado porque la consigna exige un endpoint funcional REST de negocio documentado en OpenAPI/Swagger y con control de acceso basado en rol `SUPERUSER` con `ProblemDetail`.

### 6. Estrategia de Testing y Calidad (SonarCloud `< 10 issues`)
- **Decision**:
  - **Perfil `unit`**: `ArchitectureTest` (ArchUnit), `WebServiceAuditAspectTest`, `AuditParameterSanitizerTest`, `CorrelationIdFilterTest`, `RankingServiceTest` (actualizado sin N+1 + métricas), `AdvancedMetricsServiceTest`.
  - **Perfil `e2e`**: `ObservabilityAndMetricsE2ETest` verificando `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, `GET /api/metrics/advanced` (200 superusuario, 403 usuario regular, 401 anónimo), `X-Correlation-ID` y optimización/consistencia de `GET /api/players/ranking` con PostgreSQL (Testcontainers).
  - **Release Entrega 3**: Crear `docs/release-notes-v3.0.0.md` (y/o `RELEASE_NOTES.md`), verificar CI en verde y SonarCloud `< 10 issues`, crear tag anotado `v3.0.0` y publicar Release en GitHub.
- **Rationale**: Mantiene la separación `unit`/`e2e` exigida por la Constitución v1.1.0 y evita dependencias externas frágiles en CI.
