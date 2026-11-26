# Feature Specification: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Feature Branch**: `feature/spec-006-entrega3-observability-optimization`  
**Created**: 2026-11-24  
**Updated**: 2026-11-25  
**Status**: Ready for Implementation  
**Scope**: Entrega 3 (Final) del Trabajo Práctico — Observabilidad operativa, auditoría transversal de servicios web, validación automatizada de arquitectura, caché distribuida para alta disponibilidad, optimización de consultas de ranking de alta frecuencia, verificación del scheduler de scraping sobre base de datos, endpoint de métricas avanzadas con vista básica en frontend (sin modificar archivos existentes del frontend salvo lo estrictamente indispensable) y formalización del cierre de versión (Tag y Release Notes en GitHub).  
**Input**: User description: "La spec tiene que ser sobre la entrega 3, y final, del TP y estos son los requerimientos a implementar: CORE: Crear un test de arquitectura; Redis para cache; Auditoría de Web-Services. Loguear <timestamp,user,operación/metodo, parámetros, tiempoDeEjecicion> de los servicios publicados con Spring utilizando Log4j/logback implementando AOP (Aspect Oriented Programming - Programación Orientada a Aspectos); TAG en GitHub y Confeccionar Release Notes de entrega 3; Configurar Prometheus (Métricas); Configurar Spring Boot Actuators (Endpoints de Monitoreo). FUNCIONALIDAD: Optimizar Ranking de jugadores según estrategia activa para alta frecuencia de consultas; Endpoint de métricas avanzadas."

> [!IMPORTANT]
> **RESTRICCIÓN CRÍTICA DE FRONTEND (MÉTRICAS AVANZADAS):**  
> El frontend básico para visualizar las métricas avanzadas (`GET /metrics/advanced`) **DEBE** tomar como referencia visual, estilística y arquitectónica el frontend actual (`front/`), implementándose en archivos nuevos dedicados y **SIN tocar ningún archivo actual del frontend a menos que sea sumamente importante / estrictamente indispensable** (por ejemplo, el mínimo indispensable para registrar la ruta de acceso, sin alterar estilos, componentes, servicios ni pruebas existentes).

> [!IMPORTANT]
> **CONVENCIÓN DE RUTAS REST (SIN `/api` NI `/v1`):**  
> Todos los endpoints especificados en esta entrega se definen **sin prefijo `/api` ni `/v1`** (ej. `GET /metrics/advanced`, `GET /players/ranking`, `GET /players`, `POST /quotes/recalculate`, `PATCH /admin/strategies/{id}/activate`, `POST /orders/buy`, `POST /auth/login`), manteniendo plena consistencia con la Constitución del Proyecto (§11) y la Consigna Oficial del TP.

---

## Resumen de Prioridades

- **Prioridad P1 (Crítica / Bloqueante para la Entrega 3):**
  - **User Story 1:** Optimización del Ranking de jugadores según estrategia activa, capa de caché distribuida (Redis) para ranking/catálogo/proveedores externos y verificación del scheduler de scraping actualizando los datos de jugadores en base de datos.
  - **User Story 2:** Auditoría transversal de servicios web mediante intercepción AOP con Correlation ID y protección estricta de datos sensibles.
  - **User Story 3:** Observabilidad operativa mediante endpoints de monitoreo (Actuator) y exposición de métricas compatibles con Prometheus (incluyendo servicio Prometheus en Docker Compose).
  - **User Story 4:** Validación automatizada de reglas arquitectónicas mediante suite de tests de arquitectura.
- **Prioridad P2 (Importante / Funcionalidad Avanzada y Cierre de Entrega):**
  - **User Story 5:** Endpoint de métricas avanzadas de negocio y rendimiento de la aplicación (`GET /metrics/advanced`) y vista básica de consulta en el frontend (usando el front actual solo como referencia sin modificar archivos existentes salvo lo estrictamente indispensable).
  - **User Story 6:** Empaquetado final de versión: generación de Release Notes de Entrega 3, creación de Tag y publicación de Release en GitHub tras validar CI y SonarCloud.

---

## Clarifications

### Session 2026-11-24

- Q: ¿Quiénes deberían poder consultar el endpoint de métricas avanzadas (`GET /metrics/advanced`) y los endpoints de Spring Boot Actuator (`/actuator/metrics`, `/actuator/prometheus`)? → A: `GET /metrics/advanced` restringido exclusivamente al superusuario (`403 Forbidden` para usuarios regulares, `401 Unauthorized` sin autenticar); `/actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` públicos para monitoreo y demo.
- Q: ¿Dónde deben emitirse los registros de auditoría de los Web-Services generados por AOP? → A: Consola estructurada (`stdout`) y archivo rotativo dedicado (`logs/audit.log`, excluido de Git) exclusivo para eventos de auditoría de Web-Services.
- Q: Para la configuración de Prometheus, ¿alcanza con exponer `/actuator/prometheus` o también se agrega un contenedor de Prometheus en `docker-compose.yml`? → A: Exponer `/actuator/prometheus` en el backend y agregar el servicio Prometheus en `docker-compose.yml` con configuración (`prometheus.yml`) preconfigurada haciendo scraping automático al backend.

### Session 2026-11-25 (Correcciones post-análisis)

- **Prefijo de endpoints (`I1`):** Ningún endpoint debe llevar el prefijo `/api/` delante; todas las rutas se definen directamente desde raíz sin `/api` ni `/v1` (`GET /metrics/advanced`, `GET /players/ranking`, `GET /players`, `POST /quotes/recalculate`, `PATCH /admin/strategies/{id}/activate`).
- **Frontend básico de métricas avanzadas:** Se incluye una vista básica en frontend para `GET /metrics/advanced` que **DEBE usar como referencia el frontend actual sin tocar ningún archivo actual a menos que sea sumamente importante**.
- **Scheduler de scraping:** Se incorpora la verificación obligatoria de que el scheduler de scraping actualice correctamente los datos y estadísticas de los jugadores en la base de datos e invalide la caché correspondiente.
- **Estructura de `AdvancedMetricsSnapshot` (`I2`):** `generatedAt` se ubica en el nivel raíz del objeto de respuesta (junto a `market`, `valuation`, `alerts` y `performance`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Optimización de Ranking de Alta Frecuencia, Caché Distribuida con Redis y Verificación de Scheduler de Scraping (Priority: P1)

Como usuario o cliente consumidor de la plataforma, quiero consultar el ranking de jugadores ordenado por la estrategia de valoración activa (`GET /players/ranking`) y los datos frecuentes del catálogo (`GET /players`) con respuestas casi instantáneas incluso bajo alta concurrencia de consultas, confiando en que el scheduler de scraping mantiene actualizados los datos de los jugadores en la base de datos y en que la caché refleja siempre la última cotización y estrategia vigente sin degradar la base de datos ni depender de proveedores externos en el camino de lectura.

**Why this priority**: El ranking es la vista de mayor tráfico de lectura de la plataforma, combina el requerimiento CORE de caché distribuida con Redis y el requerimiento FUNCIONAL de optimización para alta frecuencia de consultas, y depende de que el scheduler de scraping actualice correctamente los jugadores en la base de datos.

**Independent Test**: Realizar múltiples consultas consecutivas y concurrentes a `GET /players/ranking` verificando tiempos de respuesta `< 50 ms` y hit ratio `> 90%`; ejecutar un recálculo (`POST /quotes/recalculate`) o cambiar la estrategia activa (`PATCH /admin/strategies/{id}/activate`) confirmando invalidación inmediata; simular la indisponibilidad de Redis confirmando `200 OK` tanto en `GET /players/ranking` como en `GET /players`; y ejecutar/verificar el ciclo del scheduler de scraping confirmando la actualización persistida de jugadores/estadísticas en PostgreSQL e invalidación de cachés.

**Acceptance Scenarios**:

1. **Aceleración de Consultas Frecuentes de Ranking desde Caché con Clave por Estrategia**:
   - **Given** un catálogo de jugadores con cotizaciones calculadas bajo la estrategia activa y una consulta previa ya procesada de `GET /players/ranking`,
   - **When** múltiples clientes consultan `GET /players/ranking` de forma repetida y concurrente,
   - **Then** el sistema sirve la respuesta desde la caché distribuida (Redis) bajo una clave determinística asociada a la estrategia activa y su versión, con protección contra *cache stampede*, sin re-ejecutar consultas costosas a la base de datos ni invocar proveedores externos.

2. **Invalidación Consistente ante Cambio de Estrategia o Recálculo**:
   - **Given** un ranking actualmente almacenado en caché bajo la estrategia A,
   - **When** el superusuario activa la estrategia B (`PATCH /admin/strategies/{id}/activate`) o se ejecuta un recálculo de cotizaciones (manual vía `POST /quotes/recalculate` o automático vía scheduler),
   - **Then** el sistema invalida inmediatamente las entradas obsoletas en caché de modo que la siguiente consulta a `GET /players/ranking` retorna los valores actualizados sin servir datos vencidos.

3. **Optimización de Acceso a Datos en Fallo de Caché (Cache Miss)**:
   - **Given** la caché vacía o expirada por TTL para el ranking y el catálogo de jugadores,
   - **When** se invoca `GET /players/ranking`,
   - **Then** el sistema resuelve la consulta mediante acceso optimizado e indexado en base de datos (sin problema de consultas N+1), almacena el resultado en caché con su TTL configurado y devuelve la respuesta completa.

4. **Degradación Elegante ante Caída de Caché (`SC-006`)**:
   - **Given** una interrupción temporal de conectividad con el servidor de caché distribuida (Redis),
   - **When** un cliente solicita `GET /players/ranking` o `GET /players`,
   - **Then** el sistema captura el fallo de forma controlada, registra el incidente en logs sin propagar un error HTTP 500 al cliente y responde `200 OK` correctamente desde los datos locales persistidos en PostgreSQL.

5. **Funcionamiento Correcto del Scheduler de Scraping sobre la Base de Datos**:
   - **Given** el scheduler de sincronización de catálogo y scraping configurado con el microservicio de scraping (o stub/fixture controlado en pruebas),
   - **When** se dispara la ejecución programada del scheduler,
   - **Then** el proceso sincroniza los planteles y estadísticas de jugadores desde el scraper, persiste las actualizaciones en PostgreSQL, invalida las cachés afectadas (`players::catalog` y `players::ranking`) y ejecuta el recálculo de cotizaciones sobre los datos ya persistidos y actualizados.

---

### User Story 2 - Auditoría Transversal de Web-Services con AOP y Correlation ID (Priority: P1)

Como auditor o responsable operativo del sistema, quiero que cada invocación a los servicios web publicados quede registrada automáticamente de forma estructurada con marca temporal, usuario autenticado (o anónimo), operación/método invocado, parámetros sanitizados, tiempo de ejecución y Correlation ID, sin contaminar la lógica de negocio de cada endpoint ni exponer datos confidenciales.

**Why this priority**: Es un requerimiento CORE obligatorio de Entrega 3 y una pieza clave de trazabilidad y seguridad definida en la Constitución del proyecto.

**Independent Test**: Invocar distintos endpoints públicos (ej. `POST /auth/login`) y autenticados (ej. `POST /orders/buy`, `GET /players/ranking`), con y sin cabecera `X-Correlation-ID`, e inspeccionar tanto consola (`stdout`) como `logs/audit.log` verificando los 5 campos `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>`, el Correlation ID propagado y el enmascaramiento `[REDACTED]` de contraseñas, tokens JWT y API keys.

**Acceptance Scenarios**:

1. **Registro Completo de Invocación Exitosa**:
   - **Given** un usuario autenticado que invoca cualquier endpoint publicado del sistema (ej. `GET /players/ranking` o `POST /orders/buy`),
   - **When** la petición finaliza exitosamente,
   - **Then** el mecanismo transversal de auditoría registra una entrada estructurada (en consola y `logs/audit.log`) conteniendo: `timestamp` (UTC ISO-8601), `user` (identificador/email del actor autenticado), `operación/método`, `parámetros` de entrada, `tiempoDeEjecución` en milisegundos y el `correlationId` de la petición.

2. **Enmascaramiento Estricto de Información Sensible**:
   - **Given** una invocación a un endpoint que recibe o transporta datos confidenciales (como contraseñas en `POST /auth/login` o `POST /auth/register`, secretos, tokens JWT o API keys),
   - **When** el interceptor de auditoría registra la llamada,
   - **Then** todos los campos sensibles son enmascarados o redactados (`[REDACTED]`) de manera que ningún secreto quede almacenado en texto plano en los logs.

3. **Registro de Invocaciones Fallidas y Propagación de Correlation ID**:
   - **Given** una petición que produce un error de validación o negocio (ej. saldo insuficiente HTTP 400 o recurso inexistente HTTP 404), con o sin cabecera `X-Correlation-ID` provista por el cliente,
   - **When** la ejecución falla,
   - **Then** el sistema asigna o propaga el mismo `correlationId` en la cabecera de respuesta HTTP, en la respuesta de error al cliente y en el registro de auditoría, dejando constancia de la duración y el resultado fallido sin exponer trazas técnicas al cliente.

4. **Identificación de Invocaciones Anónimas**:
   - **Given** una llamada a un endpoint público sin usuario autenticado,
   - **When** el interceptor de auditoría procesa la invocación,
   - **Then** registra el campo `user` con un valor explícito estandarizado (`anonymous`) junto con el resto de los campos obligatorios.

---

### User Story 3 - Monitoreo Operativo con Endpoints de Salud y Métricas Prometheus (Priority: P1)

Como operador de infraestructura o docente evaluador, quiero disponer de endpoints estandarizados de monitoreo de salud (`/actuator/health`), información (`/actuator/info`), métricas generales (`/actuator/metrics`) y recolección compatible con Prometheus (`/actuator/prometheus`), junto con el servicio Prometheus en `docker-compose.yml`, para inspeccionar en tiempo real el estado del sistema, sus dependencias críticas y las métricas de rendimiento y negocio.

**Why this priority**: Cumple directamente con dos requerimientos CORE obligatorios de la Entrega 3 (Actuator y Prometheus) y habilita la observabilidad continua en tiempo de ejecución.

**Independent Test**: Consultar `GET /actuator/health`, `GET /actuator/info`, `GET /actuator/metrics` y `GET /actuator/prometheus` contra la aplicación levantada y verificar respuestas HTTP `200 OK`, estado de salud detallado de base de datos y caché, presencia de métricas estándar (HTTP, JVM) junto con métricas personalizadas `cbo_*`, y target `UP` en Prometheus Docker.

**Acceptance Scenarios**:

1. **Verificación de Salud del Sistema y Dependencias**:
   - **Given** la aplicación en ejecución con sus servicios dependientes (base de datos relacional y caché distribuida) operativos,
   - **When** se consulta `GET /actuator/health`,
   - **Then** el sistema responde HTTP `200 OK` reportando estado `UP` junto con el estado de salud detallado de los componentes críticos configurados (`db`, `redis`, `diskSpace`).

2. **Exposición de Métricas Compatibles con Prometheus**:
   - **Given** tráfico HTTP y operaciones de dominio (consultas de ranking, órdenes de compra/venta, recálculos) ejecutadas en la aplicación,
   - **When** un recolector o usuario consulta `GET /actuator/prometheus`,
   - **Then** el sistema retorna HTTP `200 OK` en formato de exposición de Prometheus incluyendo métricas de solicitudes HTTP (cantidad, latencia, códigos de estado), recursos de ejecución y contadores/temporizadores de negocio (aciertos/fallos de caché, órdenes ejecutadas y recálculos realizados).

3. **Consulta de Métricas Operativas vía Actuator**:
   - **Given** la aplicación operativa,
   - **When** se consulta `GET /actuator/metrics` y una métrica individual (ej. `GET /actuator/metrics/http.server.requests`),
   - **Then** el sistema retorna el listado de métricas disponibles y el detalle medido de la métrica solicitada, manteniendo ocultos endpoints administrativos sensibles o destructivos (como apagado remoto o volcado de variables de entorno).

4. **Recolección Automática desde Contenedor Prometheus en Docker Compose**:
   - **Given** el stack levantado mediante `docker-compose.yml` incluyendo el servicio de Prometheus con `prometheus.yml` apuntando al backend,
   - **When** Prometheus ejecuta su ciclo periódico de scraping sobre `/actuator/prometheus`,
   - **Then** el target del backend figura en estado `UP` en Prometheus y las métricas estándar y de dominio quedan disponibles para consulta inmediata.

---

### User Story 4 - Suite Automatizada de Tests de Arquitectura (Priority: P1)

Como integrante del equipo de desarrollo o revisor técnico, quiero contar con una suite automatizada de tests de arquitectura que valide en cada compilación el respeto estricto a la arquitectura por capas y las convenciones del proyecto, para detectar automáticamente cualquier desviación antes de integrar cambios.

**Why this priority**: Es un requerimiento CORE obligatorio de Entrega 3 y hace ejecutable y verificable la regla central de la Constitución (Sección 3: Arquitectura en capas).

**Independent Test**: Ejecutar la suite de tests del proyecto (`-Punit`) comprobando que todos los tests de arquitectura pasan sobre el código actual sin requerir Docker ni contexto de Spring; validar además que una violación simulada —como un controlador invocando directamente un repositorio o una dependencia cíclica— provoca el fallo inmediato de la suite.

**Acceptance Scenarios**:

1. **Validación de Dependencias Permitidas entre Capas**:
   - **Given** el código fuente del backend organizado en capas (`controller`, `service`, `repository`, `adapter`, `model`, `dto`, `security`, `config`, `audit`),
   - **When** se ejecuta la suite de tests automatizados (`unit` y pipeline de CI),
   - **Then** los tests de arquitectura verifican que:
     - Los `Controllers` solo dependan de `Services` y `DTOs` (nunca directamente de `Repositories` ni `Adapters`).
     - Los `Repositories` no dependan de `Controllers` ni `Services`.
     - Los `Adapters` no dependan de `Controllers`.
     - No existan dependencias cíclicas entre los paquetes principales de la aplicación.

2. **Validación de Aislamiento de Entidades y Convenciones de Capas**:
   - **Given** los endpoints públicos definidos en los `Controllers`,
   - **When** se ejecutan los tests de arquitectura,
   - **Then** se verifica que ningún controlador exponga entidades de persistencia directamente en sus firmas públicas (uso obligatorio de DTOs) y que las clases de cada capa respeten sus convenciones de ubicación y nomenclatura.

3. **Integración en el Flujo de Calidad Continuo**:
   - **Given** el flujo de integración continua del repositorio,
   - **When** se abre un Pull Request o se realiza un push a las ramas principales,
   - **Then** los tests de arquitectura se ejecutan automáticamente junto con los tests unitarios y bloquean el estado de éxito si alguna regla arquitectónica se viola.

---

### User Story 5 - Endpoint de Métricas Avanzadas de Negocio y Sistema + Vista Básica en Frontend (Priority: P2)

Como superusuario, analista o evaluador del sistema, quiero consultar un endpoint dedicado de métricas avanzadas (`GET /metrics/advanced`) y visualizar sus indicadores en una vista básica dentro del frontend (construida tomando el frontend actual como referencia y sin tocar archivos existentes salvo lo estrictamente indispensable), para obtener una radiografía completa de negocio y rendimiento de la plataforma.

**Why this priority**: Cumple con el requerimiento FUNCIONAL de "Endpoint de métricas avanzadas" de Entrega 3 y facilita su demostración visual ante la cátedra sin introducir riesgo de regresión en las pantallas actuales del frontend.

**Independent Test**: Invocar `GET /metrics/advanced` sin credenciales (`401`), con usuario regular (`403 ProblemDetail ACCESS_DENIED`) y con superusuario (`200 OK` con `market`, `valuation`, `alerts`, `performance` y `generatedAt` en el nivel raíz); luego validar la vista básica en frontend con tests aislados de Vitest confirmando que muestra los 4 bloques y no altera ningún test ni archivo previo del frontend (salvo el registro mínimo indispensable de ruta).

**Acceptance Scenarios**:

1. **Consulta Exitosa de Métricas Avanzadas Consolidadas**:
   - **Given** un sistema con jugadores cotizados, transacciones registradas, alertas creadas y consultas previas al ranking,
   - **When** el superusuario consulta `GET /metrics/advanced`,
   - **Then** el sistema responde HTTP `200 OK` con un objeto JSON estructurado que incluye en su nivel raíz `generatedAt` (timestamp UTC de generación del reporte) y cuatro bloques lógicos:
     - **Mercado y Trading (`market`)**: cantidad total de operaciones desglosadas por tipo (`COMPRA`, `VENTA`, `RECARGA`, `CAMBIO_ESTRATEGIA`), volumen total negociado en créditos, cantidad total de usuarios activos, porcentaje de tokens en circulación respecto al total emitido, y Top 5 de jugadores más operados por volumen de tokens.
     - **Valoración y Estrategia (`valuation`)**: código, nombre y versión de la estrategia activa, timestamp del último recálculo, cotización promedio, máxima y mínima vigente del catálogo, y cantidad de jugadores cubiertos.
     - **Alertas (`alerts`)**: cantidad de alertas activas y cantidad de alertas disparadas.
     - **Rendimiento y Caché (`performance`)**: cantidad de aciertos (`rankingCacheHits`), fallos (`rankingCacheMisses`), tasa de acierto porcentual (`rankingCacheHitRatioPercent`) de la caché del ranking y latencia promedio (`avgRankingLatencyMs`) de consulta del ranking.

2. **Comportamiento ante Sistema Sin Operaciones Previas**:
   - **Given** un entorno recién inicializado sin transacciones ni consultas previas al ranking,
   - **When** el superusuario consulta `GET /metrics/advanced`,
   - **Then** el sistema responde HTTP `200 OK` sin errores aritméticos ni divisiones por cero, informando contadores en `0`, tasas porcentuales en `0.0` y listas vacías donde corresponda.

3. **Control de Acceso al Endpoint de Métricas Avanzadas (`401 Unauthorized`)**:
   - **Given** una solicitud sin credenciales válidas (sin JWT ni API Key),
   - **When** intenta consultar `GET /metrics/advanced`,
   - **Then** el sistema rechaza la petición con HTTP `401 Unauthorized` utilizando la estructura de error estándar en español.

4. **Rechazo a Usuario Regular en Métricas Avanzadas (`403 Forbidden`)**:
   - **Given** un usuario regular autenticado (sin rol de superusuario),
   - **When** intenta consultar `GET /metrics/advanced`,
   - **Then** el sistema rechaza la petición con HTTP `403 Forbidden` con estructura `ProblemDetail` y código `ACCESS_DENIED`.

5. **Visualización Básica en Frontend sin Alterar Archivos Existentes Salvo lo Indispensable**:
   - **Given** un superusuario autenticado en el frontend,
   - **When** accede a la vista básica de métricas avanzadas (`/metrics/advanced`),
   - **Then** la interfaz (creada en archivos nuevos dedicados reutilizando la estética y componentes de referencia del frontend actual sin modificar archivos existentes salvo lo estrictamente indispensable) consume `GET /metrics/advanced` y presenta de forma clara los indicadores de mercado, valoración, alertas, rendimiento/caché y `generatedAt`, mostrando un mensaje controlado en español en caso de `403` o indisponibilidad.

---

### User Story 6 - Tag en GitHub y Confección de Release Notes de Entrega 3 (Priority: P2)

Como equipo de proyecto y cátedra evaluadora, quiero contar con un documento de Release Notes claro y completo para la Entrega 3, acompañado de su correspondiente etiqueta (Tag) y Release publicado en GitHub sobre la versión estable y verificada, para dejar constancia formal del cierre del trabajo práctico y facilitar su revisión.

**Why this priority**: Es un requerimiento CORE obligatorio de cierre de Entrega 3 que se ejecuta una vez completadas y validadas las demás historias de usuario.

**Independent Test**: Verificar la existencia del archivo de Release Notes actualizado con los cambios de Entrega 3, la verificación previa de CI en estado `SUCCESS` y SonarCloud `< 10 issues`, la presencia del tag semántico `v3.0.0` y la publicación de la Release correspondiente en GitHub.

**Acceptance Scenarios**:

1. **Confección de Release Notes Completas**:
   - **Given** todas las funcionalidades y requerimientos core de Entrega 3 implementados y validados,
   - **When** se confeccionan las Release Notes de la versión final,
   - **Then** el documento incluye: versión y fecha de entrega, resumen ejecutivo, lista detallada de novedades CORE y FUNCIONALES, nuevos endpoints agregados, mejoras de rendimiento y observabilidad, estado de calidad (tests, arquitectura, SonarCloud) e instrucciones breves de verificación.

2. **Creación de Tag Semántico y Publicación de Release tras Compuerta CI/Sonar**:
   - **Given** la rama `main` con build de CI en estado `SUCCESS`, todas las suites de tests (unit, e2e, arquitectura y Vitest) pasando y SonarCloud cumpliendo `< 10 issues`,
   - **When** se crea la etiqueta anotada `v3.0.0`, se pushea al remoto y se publica la Release en GitHub,
   - **Then** el tag y la Release quedan visibles en el repositorio remoto asociados a las Release Notes confeccionadas.

---

### Edge Cases

- **Caída o latencia extrema de Redis**: Si Redis no está disponible o excede el timeout configurado, las lecturas de `GET /players/ranking` y `GET /players` deben degradar transparentemente a PostgreSQL retornando `200 OK` sin errores HTTP 500, y las operaciones de escritura/recálculo no deben abortar su transacción principal por fallos de invalidación de caché.
- **Concurrencia en invalidación y repoblación de caché (Cache Stampede)**: Bajo ráfagas simultáneas de consultas a `GET /players/ranking` tras un recálculo o cambio de estrategia, el mecanismo de carga sincronizada por clave evita consultas duplicadas simultáneas a la base de datos y la clave determinística incluye el código y versión de la estrategia activa.
- **Ejecución asíncrona o falla parcial del scheduler de scraping**: Si el scheduler de scraping corre de forma programada, el recálculo de cotizaciones y la invalidación de cachés deben ejecutarse una vez finalizada la persistencia de los datos actualizados (no antes por carrera asíncrona); si un equipo o jugador falla en el proveedor externo, el scheduler conserva los datos locales previos en PostgreSQL sin abortar el resto del catálogo.
- **Parámetros nulos, colecciones extensas o binarios en auditoría AOP**: Si un endpoint recibe payloads grandes, archivos o parámetros nulos, el aspecto de auditoría debe serializarlos de forma segura (truncando si superan 500 caracteres y manejando valores nulos) sin lanzar excepciones que interfieran con el resultado del endpoint.
- **Datos sensibles anidados en DTOs**: Contraseñas, tokens o API keys dentro de objetos compuestos (request bodies) o headers deben redactarse (`[REDACTED]`) en auditoría antes de escribir en el log.
- **Exclusión de endpoints de monitoreo del bucle de auditoría excesivo**: El polling frecuente de `/actuator/health` o `/actuator/prometheus` no debe saturar los logs de auditoría funcional de negocio (la auditoría AOP aplica únicamente a los controladores de negocio de la aplicación).
- **División por cero en métricas avanzadas**: Si no hay consultas de caché (`hits + misses == 0`), ni tokens emitidos, ni cotizaciones registradas, los porcentajes y promedios en `GET /metrics/advanced` deben retornar `0.0` de forma determinística.
- **Incompatibilidad de tests de arquitectura con perfiles**: Los tests de arquitectura deben ejecutarse de forma rápida y determinística dentro del perfil `unit` sin requerir contenedores externos ni base de datos levantada.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (Tests de Arquitectura)**: El sistema DEBE incluir una suite automatizada de tests de arquitectura que verifique el respeto de la arquitectura en capas (`Controllers → Services → Repositories` y `Adapters`), la ausencia de dependencias cíclicas entre paquetes principales, la prohibición de acceso directo de `Controllers` a `Repositories`/`Adapters` y la no exposición directa de entidades de persistencia desde los `Controllers`.
- **FR-002 (Caché Distribuida con Redis)**: El sistema DEBE utilizar Redis como capa de caché distribuida para las consultas de alta frecuencia: ranking (`GET /players/ranking`), catálogo de jugadores (`GET /players` y detalle) y respuestas/respaldo de proveedores externos (WhoScored / Football-Data.org), con tiempos de expiración (TTL) configurables por tipo de caché, invalidación al persistir sincronizaciones y degradación transparente a datos locales persistidos en PostgreSQL ante fallos de Redis.
- **FR-003 (Optimización de Ranking para Alta Frecuencia)**: El sistema DEBE optimizar `GET /players/ranking` para soportar alta frecuencia de consultas combinando caché distribuida con clave determinística vinculada a la estrategia activa y su versión, protección contra *cache stampede*, consultas optimizadas con índices compuestos en base de datos (sin consultas N+1) y cero invocaciones a proveedores externos durante la lectura.
- **FR-004 (Consistencia e Invalidación de Caché)**: El sistema DEBE invalidar automáticamente la caché del ranking y de cotizaciones cada vez que se ejecute un recálculo manual (`POST /quotes/recalculate`), un recálculo programado por scheduler, una actualización del catálogo persistido o un cambio/activación de estrategia de valoración (`PATCH /admin/strategies/{id}/activate`).
- **FR-005 (Auditoría de Web-Services mediante AOP)**: El sistema DEBE implementar un mecanismo de auditoría transversal mediante Programación Orientada a Aspectos (AOP) que intercepte todos los servicios web publicados de negocio y registre en formato estructurado (utilizando Logback/Log4j, emitiendo tanto a consola estructurada `stdout` como a un archivo rotativo dedicado `logs/audit.log` excluido de Git) al menos: `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` junto con el `correlationId` y el resultado de la operación.
- **FR-006 (Protección de Datos Sensibles en Auditoría)**: El mecanismo de auditoría DEBE sanitizar y redactar obligatoriamente cualquier dato sensible (contraseñas, secretos, tokens JWT, API Keys) antes de escribir en los logs, garantizando cero fugas de credenciales en texto plano.
- **FR-007 (Trazabilidad con Correlation ID)**: El sistema DEBE generar un identificador único `Correlation ID` por cada petición entrante (o reutilizar el provisto en la cabecera `X-Correlation-ID`), propagarlo en el contexto de logging durante todo el ciclo de vida de la solicitud desde la capa inicial de filtros y devolverlo en la cabecera de respuesta HTTP y en las respuestas de error.
- **FR-008 (Endpoints de Monitoreo con Actuator)**: El sistema DEBE exponer y configurar endpoints de monitoreo mediante Spring Boot Actuator incluyendo como mínimo `/actuator/health` (con detalle de estado de dependencias críticas como base de datos y Redis), `/actuator/info` y `/actuator/metrics`, accesibles públicamente para chequeos de infraestructura, recolección y demostración, restringiendo la exposición de endpoints administrativos riesgosos.
- **FR-009 (Métricas Compatibles con Prometheus)**: El sistema DEBE exponer métricas en formato compatible con Prometheus en `/actuator/prometheus` (accesible públicamente para scraping), incorporando tanto métricas estándar de plataforma (solicitudes HTTP, latencias, errores, memoria/CPU, conexiones) como métricas personalizadas del dominio (aciertos/fallos de caché del ranking, operaciones de compra/venta y ejecuciones de recálculo), e incluir el servicio Prometheus en `docker-compose.yml` con su archivo de configuración (`prometheus.yml`) preconfigurado para recolectar automáticamente del backend.
- **FR-010 (Endpoint de Métricas Avanzadas y Vista Básica de Frontend)**: El sistema DEBE exponer el endpoint `GET /metrics/advanced` (sin prefijo `/api` ni `/v1`, documentado en OpenAPI/Swagger y restringido exclusivamente al superusuario: `401 Unauthorized` sin autenticar, `403 Forbidden` para usuarios regulares) que retorne un reporte JSON consolidado con `generatedAt` en el nivel raíz y los bloques `market`, `valuation`, `alerts` y `performance`. Adicionalmente, DEBE proveerse una vista básica en frontend para consumir y visualizar dicho endpoint usando el frontend actual exclusivamente como referencia visual/arquitectónica y **sin modificar ningún archivo actual del frontend a menos que sea sumamente importante**.
- **FR-011 (Estándar de Rutas, Errores y Documentación)**: Ningún endpoint definido en la plataforma DEBE anteponer el prefijo `/api` ni `/v1` (`/metrics/advanced`, `/players/ranking`, `/players`, `/quotes/recalculate`, `/admin/strategies/{id}/activate`, `/orders/buy`, `/auth/login`); todos los nuevos endpoints DEBEN documentarse en Swagger/OpenAPI v3 y retornar errores en español bajo el formato estándar `ProblemDetail` (RFC 7807) con `correlationId`.
- **FR-012 (Release Notes y GitHub Tag)**: El proyecto DEBE incluir un documento formal de Release Notes para la Entrega 3 y culminar —únicamente tras validar CI en estado `SUCCESS` y SonarCloud `< 10 issues`— con la creación de la etiqueta semántica anotada (`v3.0.0`) y publicación de la Release correspondiente en GitHub sobre la rama principal.
- **FR-013 (Verificación del Scheduler de Scraping sobre Base de Datos)**: El sistema DEBE verificar y garantizar que el scheduler de sincronización y scraping (`CatalogScheduler` / `CatalogSyncService` / `CatalogPersistenceService`) actualice correctamente los datos y estadísticas de los jugadores en la base de datos PostgreSQL, invalide las cachés afectadas al finalizar la persistencia y ejecute el recálculo de cotizaciones una vez persistidos los datos actualizados (sin condiciones de carrera asíncronas).

### Key Entities *(include if feature involves data)*

- **WebServiceAuditRecord (Registro de Auditoría de Servicio Web)**: Evento estructurado emitido en logs por cada invocación a un endpoint de negocio. Atributos: `timestamp` (UTC), `correlationId`, `user`, `operation` (controlador/método y ruta HTTP), `parameters` (sanitizados), `executionTimeMs`, `outcome` (`SUCCESS` / `ERROR`).
- **AdvancedMetricsSnapshot (Instantánea de Métricas Avanzadas)**: Estructura de respuesta consolidada de `GET /metrics/advanced` compuesta por `generatedAt` (timestamp ISO-8601 UTC en el nivel raíz) y cuatro bloques lógicos:
  - `market`: métricas de volumen de transacciones por tipo, créditos operados, usuarios activos, liquidez de tokens y Top 5 de jugadores más negociados.
  - `valuation`: estrategia activa (código, nombre y versión), último recálculo, cotización promedio/mínima/máxima y total de jugadores valorados.
  - `alerts`: conteo de alertas activas y disparadas.
  - `performance`: métricas de caché de ranking (`rankingCacheHits`, `rankingCacheMisses`, `rankingCacheHitRatioPercent`) y latencia promedio del ranking (`avgRankingLatencyMs`).
- **CacheEntry (Entrada de Caché de Ranking/Catálogo/Proveedores)**: Representación serializada en caché distribuida asociada a una clave determinística vinculada a la estrategia activa y su versión (para ranking: `active_ranking:<strategyCode>:v<version>`) o identificador de recurso, con tiempo de vida (`TTL`) configurable y carga protegida contra *cache stampede*.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (Rendimiento de Ranking en Alta Frecuencia)**: Al menos el 95% de las consultas repetidas al ranking de jugadores (`GET /players/ranking`) con caché caliente responden en menos de 50 milisegundos, logrando una tasa de aciertos de caché (hit ratio) superior al 90% bajo escenarios de lectura repetitiva sin recálculos intermedios, verificada mediante prueba automatizada.
- **SC-002 (Consistencia Inmediata de Datos)**: El 100% de las consultas al ranking realizadas inmediatamente después de un recálculo de cotizaciones, una sincronización del scheduler o un cambio de estrategia activa reflejan los valores actualizados sin servir datos obsoletos.
- **SC-003 (Cobertura Total y Seguridad de Auditoría)**: El 100% de las invocaciones a endpoints de negocio generan un registro de auditoría completo con los 5 campos requeridos (`timestamp`, `user`, `operación/método`, `parámetros`, `tiempoDeEjecución`) más `correlationId`, y el 0% de los registros contienen contraseñas, tokens JWT o API keys en texto plano.
- **SC-004 (Cumplimiento Arquitectónico Automatizado)**: El 100% de las reglas arquitectónicas definidas se verifican automáticamente en la suite de pruebas (`-Punit`) con 0 violaciones, y el build falla de forma determinística ante cualquier infracción de capas.
- **SC-005 (Observabilidad Operativa Verificable)**: Los endpoints de salud (`/actuator/health`), información (`/actuator/info`), métricas operativas (`/actuator/metrics`), exportación Prometheus (`/actuator/prometheus`) y métricas avanzadas (`GET /metrics/advanced`) responden con estado exitoso (`200 OK`) y datos consistentes con el estado real del sistema en menos de 500 milisegundos.
- **SC-006 (Resiliencia ante Fallo de Caché)**: El 100% de las solicitudes a `GET /players/ranking` y `GET /players` continúan respondiendo exitosamente (`200 OK`) cuando el servicio de caché distribuida (Redis) se encuentra temporalmente inaccesible, verificado mediante prueba automatizada.
- **SC-007 (Calidad de Entrega y Release Final)**: La versión etiquetada en GitHub (`v3.0.0`) cuenta con Release Notes completas, pipeline de CI en estado `SUCCESS`, 100% de pruebas unitarias, E2E, arquitectura y vista básica de frontend aprobadas, scheduler de scraping verificado actualizando jugadores en DB, y SonarCloud manteniendo menos de 10 issues totales (`Issues < 10`).

---

## Assumptions

- **Alcance Backend + Vista Básica Aislada de Frontend para Métricas Avanzadas**: El núcleo de la Entrega 3 (tests de arquitectura, caché Redis, auditoría AOP, Actuator, Prometheus, optimización de ranking, verificación del scheduler de scraping, endpoint `GET /metrics/advanced` y tag/release) se implementa en el backend y Docker Compose. Adicionalmente se construye una vista básica en frontend para `GET /metrics/advanced` bajo la regla estricta de **usar el frontend actual únicamente como referencia sin tocar ningún archivo actual a menos que sea sumamente importante**.
- **Convención de Rutas sin `/api` ni `/v1`**: Todos los endpoints se definen sin prefijo `/api` ni `/v1` (`GET /metrics/advanced`, `GET /players/ranking`, etc.). Durante la implementación se documentará y coordinará el manejo respecto a cualquier prefijo heredado en controladores existentes para mantener total coherencia sin romper el frontend existente.
- **Política de Acceso a Monitoreo y Métricas**:
  - `/actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` son públicos para chequeos de contenedores (Docker healthcheck), recolección desde Prometheus y verificación operativa en vivo.
  - `GET /metrics/advanced` está restringido exclusivamente al **superusuario** (`401 Unauthorized` si no está autenticado y `403 Forbidden` con código `ACCESS_DENIED` para usuarios regulares).
- **Infraestructura Redis Existente**: El archivo `docker-compose.yml` ya declara un servicio Redis (`redis:7-alpine`) y el backend ya cuenta con dependencias base de Redis y Actuator; esta entrega consolida su configuración productiva, tolerancia a fallos, métricas, invalidación y pruebas de integración.
- **Nomenclatura de Versión de Entrega 3**: Se asume el esquema de versionado semántico `v3.0.0` para el Tag y Release en GitHub correspondiente a la Entrega 3, ejecutándose únicamente luego de confirmar CI en `SUCCESS` y SonarCloud `< 10 issues`.
- **Perfiles de Testing**: Los tests de arquitectura se ejecutan dentro del perfil `unit` (rápido, sin infraestructura externa) y los tests de integración de caché Redis, Actuator/Prometheus, scheduler y métricas avanzadas se cubren en los perfiles `unit` y `e2e` sobre PostgreSQL (Testcontainers).
