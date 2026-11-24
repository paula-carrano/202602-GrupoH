# Feature Specification: Entrega 3 — Observabilidad, Auditoría, Arquitectura y Optimización

**Feature Branch**: `006-entrega3-observability-optimization`  
**Created**: 2026-11-24  
**Status**: Draft  
**Scope**: Entrega 3 (Final) del Trabajo Práctico — Observabilidad operativa, auditoría transversal de servicios web, validación automatizada de arquitectura, caché distribuida para alta disponibilidad, optimización de consultas de ranking de alta frecuencia, endpoint de métricas avanzadas y formalización del cierre de versión (Tag y Release Notes en GitHub).  
**Input**: User description: "La spec tiene que ser sobre la entrega 3, y final, del TP y estos son los requerimientos a implementar: CORE: Crear un test de arquitectura; Redis para cache; Auditoría de Web-Services. Loguear <timestamp,user,operación/metodo, parámetros, tiempoDeEjecicion> de los servicios publicados con Spring utilizando Log4j/logback implementando AOP (Aspect Oriented Programming - Programación Orientada a Aspectos); TAG en GitHub y Confeccionar Release Notes de entrega 3; Configurar Prometheus (Métricas); Configurar Spring Boot Actuators (Endpoints de Monitoreo). FUNCIONALIDAD: Optimizar Ranking de jugadores según estrategia activa para alta frecuencia de consultas; Endpoint de métricas avanzadas."

---

## Resumen de Prioridades

- **Prioridad P1 (Crítica / Bloqueante para la Entrega 3):**
  - **User Story 1:** Optimización del Ranking de jugadores según estrategia activa y capa de caché distribuida (Redis) para alta frecuencia de consultas.
  - **User Story 2:** Auditoría transversal de servicios web mediante intercepción AOP con Correlation ID y protección estricta de datos sensibles.
  - **User Story 3:** Observabilidad operativa mediante endpoints de monitoreo (Actuator) y exposición de métricas compatibles con Prometheus.
  - **User Story 4:** Validación automatizada de reglas arquitectónicas mediante suite de tests de arquitectura.
- **Prioridad P2 (Importante / Funcionalidad Avanzada y Cierre de Entrega):**
  - **User Story 5:** Endpoint de métricas avanzadas de negocio y rendimiento de la aplicación (`GET /metrics/advanced`).
  - **User Story 6:** Empaquetado final de versión: generación de Release Notes de Entrega 3, creación de Tag y publicación de Release en GitHub.

---

## Clarifications

### Session 2026-11-24

- Q: ¿Quiénes deberían poder consultar el endpoint de métricas avanzadas (`GET /metrics/advanced`) y los endpoints de Spring Boot Actuator (`/actuator/metrics`, `/actuator/prometheus`)? → A: `GET /metrics/advanced` restringido exclusivamente al superusuario (`403 Forbidden` para usuarios regulares, `401 Unauthorized` sin autenticar); `/actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` públicos para monitoreo y demo.
- Q: ¿El alcance de la Entrega 3 se mantiene exclusivamente en el backend (más el Tag y Release Notes), o incluye una vista en el frontend para métricas avanzadas? → A: Exclusivamente Backend (+ Tag y Release Notes en GitHub); el frontend React queda fuera de alcance de esta especificación.
- Q: ¿Dónde deben emitirse los registros de auditoría de los Web-Services generados por AOP? → A: Consola estructurada (stdout) y archivo rotativo dedicado (`logs/audit.log`, excluido de Git) exclusivo para eventos de auditoría de Web-Services.
- Q: Para la configuración de Prometheus, ¿alcanza con exponer `/actuator/prometheus` o también se agrega un contenedor de Prometheus en `docker-compose.yml`? → A: Exponer `/actuator/prometheus` en el backend y agregar el servicio Prometheus en `docker-compose.yml` con configuración (`prometheus.yml`) preconfigurada haciendo scraping automático al backend.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Optimización de Ranking de Alta Frecuencia y Caché Distribuida con Redis (Priority: P1)

Como usuario o cliente consumidor de la plataforma, quiero consultar el ranking de jugadores ordenado por la estrategia de valoración activa (`GET /players/ranking`) y los datos frecuentes del catálogo con respuestas casi instantáneas incluso bajo alta concurrencia de consultas, confiando en que los valores reflejan siempre la última cotización y estrategia vigente sin degradar la base de datos ni depender de proveedores externos en el camino de lectura.

**Why this priority**: El ranking es la vista de mayor tráfico de lectura de la plataforma y combina el requerimiento CORE de caché distribuida con Redis y el requerimiento FUNCIONAL de optimización para alta frecuencia de consultas.

**Independent Test**: Realizar múltiples consultas consecutivas y concurrentes a `GET /players/ranking` verificando tiempos de respuesta reducidos tras el primer acceso (acierto de caché); luego ejecutar un recálculo de cotizaciones (`POST /quotes/recalculate`) o cambiar la estrategia activa (`PATCH /admin/strategies/{id}/activate`) y confirmar que la siguiente consulta refleja los nuevos valores de forma inmediata; finalmente simular la indisponibilidad temporal del servicio de caché y confirmar que el endpoint sigue respondiendo correctamente con los datos persistidos.

**Acceptance Scenarios**:

1. **Aceleración de Consultas Frecuentes de Ranking desde Caché**:
   - **Given** un catálogo de jugadores con cotizaciones calculadas bajo la estrategia activa y una consulta previa ya procesada de `GET /players/ranking`,
   - **When** múltiples clientes consultan `GET /players/ranking` de forma repetida y concurrente,
   - **Then** el sistema sirve la respuesta desde la caché distribuida (Redis) sin re-ejecutar consultas costosas a la base de datos ni invocar proveedores externos, manteniendo el orden descendente por cotización vigente y los metadatos de la estrategia activa.

2. **Invalidación Consistente ante Cambio de Estrategia o Recálculo**:
   - **Given** un ranking actualmente almacenado en caché bajo la estrategia A,
   - **When** el superusuario activa la estrategia B (`PATCH /admin/strategies/{id}/activate`) o se ejecuta un recálculo de cotizaciones (manual vía `POST /quotes/recalculate` o automático vía scheduler),
   - **Then** el sistema invalida inmediatamente las entradas obsoletas en caché de modo que la siguiente consulta a `GET /players/ranking` retorna los valores actualizados sin servir datos vencidos.

3. **Optimización de Acceso a Datos en Fallo de Caché (Cache Miss)**:
   - **Given** la caché vacía o expirada por TTL para el ranking y el catálogo de jugadores,
   - **When** se invoca `GET /players/ranking`,
   - **Then** el sistema resuelve la consulta mediante acceso optimizado e indexado en base de datos (sin problema de consultas N+1), almacena el resultado en caché con su TTL configurado y devuelve la respuesta completa.

4. **Degradación Elegante ante Caída de Caché**:
   - **Given** una interrupción temporal de conectividad con el servidor de caché distribuida,
   - **When** un cliente solicita `GET /players/ranking` o `GET /players`,
   - **Then** el sistema captura el fallo de forma controlada, registra el incidente en logs sin propagar un error HTTP 500 al cliente y responde correctamente desde los datos locales persistidos.

---

### User Story 2 - Auditoría Transversal de Web-Services con AOP y Correlation ID (Priority: P1)

Como auditor o responsable operativo del sistema, quiero que cada invocación a los servicios web publicados quede registrada automáticamente de forma estructurada con marca temporal, usuario autenticado (o anónimo), operación/método invocado, parámetros sanitizados, tiempo de ejecución y Correlation ID, sin contaminar la lógica de negocio de cada endpoint ni exponer datos confidenciales.

**Why this priority**: Es un requerimiento CORE obligatorio de Entrega 3 y una pieza clave de trazabilidad y seguridad definida en la Constitución del proyecto.

**Independent Test**: Invocar distintos endpoints públicos (ej. login) y autenticados (ej. compra de tokens, consulta de ranking), con y sin cabecera `X-Correlation-ID`, e inspeccionar la salida del registro de auditoría verificando que cada llamada genera una entrada con `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` y Correlation ID, y que credenciales como contraseñas, tokens JWT o API keys aparezcan enmascaradas.

**Acceptance Scenarios**:

1. **Registro Completo de Invocación Exitosa**:
   - **Given** un usuario autenticado que invoca cualquier endpoint publicado del sistema (ej. `GET /players/ranking` o `POST /orders/buy`),
   - **When** la petición finaliza exitosamente,
   - **Then** el mecanismo transversal de auditoría registra una entrada estructurada conteniendo: `timestamp` (UTC ISO-8601), `user` (identificador/email del actor autenticado), `operación/método`, `parámetros` de entrada, `tiempoDeEjecución` en milisegundos y el `correlationId` de la petición.

2. **Enmascaramiento Estricto de Información Sensible**:
   - **Given** una invocación a un endpoint que recibe o transporta datos confidenciales (como contraseñas en `POST /auth/login` o `POST /auth/register`, secretos, tokens JWT o API keys),
   - **When** el interceptor de auditoría registra la llamada,
   - **Then** todos los campos sensibles son enmascarados o redactados (ej. `[REDACTED]`) de manera que ningún secreto quede almacenado en texto plano en los logs.

3. **Registro de Invocaciones Fallidas y Propagación de Correlation ID**:
   - **Given** una petición que produce un error de validación o negocio (ej. saldo insuficiente HTTP 400 o recurso inexistente HTTP 404), con o sin cabecera `X-Correlation-ID` provista por el cliente,
   - **When** la ejecución falla,
   - **Then** el sistema asigna o propaga el mismo `correlationId` tanto en la cabecera de respuesta HTTP, en la respuesta de error al cliente y en el registro de auditoría, dejando constancia de la duración y el resultado fallido sin exponer trazas técnicas al cliente.

4. **Identificación de Invocaciones Anónimas**:
   - **Given** una llamada a un endpoint público sin usuario autenticado (ej. registro o catálogo público si aplica),
   - **When** el interceptor de auditoría procesa la invocación,
   - **Then** registra el campo `user` con un valor explícito estandarizado (`anonymous`) junto con el resto de los campos obligatorios.

---

### User Story 3 - Monitoreo Operativo con Endpoints de Salud y Métricas Prometheus (Priority: P1)

Como operador de infraestructura o docente evaluador, quiero disponer de endpoints estandarizados de monitoreo de salud (`/actuator/health`), métricas generales (`/actuator/metrics`) y recolección compatible con Prometheus (`/actuator/prometheus`), para inspeccionar en tiempo real el estado del sistema, sus dependencias críticas y las métricas de rendimiento y negocio.

**Why this priority**: Cumple directamente con dos requerimientos CORE obligatorios de la Entrega 3 (Actuator y Prometheus) y habilita la observabilidad continua en tiempo de ejecución.

**Independent Test**: Consultar `GET /actuator/health`, `GET /actuator/metrics` y `GET /actuator/prometheus` contra la aplicación levantada y verificar respuestas HTTP `200 OK`, estado de salud detallado de base de datos y caché, y presencia de métricas estándar (HTTP, JVM) junto con métricas de negocio personalizadas en formato texto plano de Prometheus.

**Acceptance Scenarios**:

1. **Verificación de Salud del Sistema y Dependencias**:
   - **Given** la aplicación en ejecución con sus servicios dependientes (base de datos relacional y caché distribuida) operativos,
   - **When** se consulta `GET /actuator/health`,
   - **Then** el sistema responde HTTP `200 OK` reportando estado `UP` junto con el estado de salud de los componentes críticos configurados.

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

**Independent Test**: Ejecutar la suite de tests del proyecto comprobando que todos los tests de arquitectura pasan sobre el código actual; validar además (mediante verificación de diseño o prueba controlada) que una violación simulada —como un controlador invocando directamente un repositorio o una dependencia cíclica entre capas— provoca el fallo inmediato de la suite.

**Acceptance Scenarios**:

1. **Validación de Dependencias Permitidas entre Capas**:
   - **Given** el código fuente del backend organizado en capas (`controller`, `service`, `repository`, `adapter`, `model`, `dto`, `security`, `config`),
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

### User Story 5 - Endpoint de Métricas Avanzadas de Negocio y Sistema (Priority: P2)

Como superusuario, analista o evaluador del sistema, quiero consultar un endpoint dedicado de métricas avanzadas (`GET /metrics/advanced`) que consolide en una única respuesta estructurada indicadores clave de negocio (volumen de mercado, jugadores más negociados, liquidez de tokens, estado de estrategia y alertas) e indicadores técnicos de rendimiento (tasa de acierto de caché, latencia de ranking y salud operativa), para obtener una radiografía completa del comportamiento de la plataforma.

**Why this priority**: Cumple con el requerimiento FUNCIONAL de "Endpoint de métricas avanzadas" de Entrega 3 y agrega alto valor demostrativo en la evaluación final al integrar datos del dominio con telemetría en tiempo real.

**Independent Test**: Ejecutar operaciones de mercado (compras, ventas, recargas), consultas al ranking y recálculos; luego invocar `GET /metrics/advanced` autenticado y verificar que el payload JSON devuelve todas las secciones definidas con valores consistentes con el estado real de la base de datos y los contadores de ejecución, y que los accesos no autenticados sean rechazados.

**Acceptance Scenarios**:

1. **Consulta Exitosa de Métricas Avanzadas Consolidadas**:
   - **Given** un sistema con jugadores cotizados, transacciones registradas, alertas creadas y consultas previas al ranking,
   - **When** un usuario autorizado consulta `GET /metrics/advanced`,
   - **Then** el sistema responde HTTP `200 OK` con un objeto JSON estructurado que incluye:
     - **Mercado y Trading**: cantidad total de operaciones desglosadas por tipo (`COMPRA`, `VENTA`, `RECARGA`), volumen total negociado en créditos, cantidad total de usuarios activos, porcentaje de tokens en circulación respecto al total emitido, y Top 5 de jugadores más operados por volumen de tokens.
     - **Valoración y Estrategia**: nombre y versión de la estrategia activa, timestamp del último recálculo, cotización promedio, máxima y mínima vigente del catálogo, y cantidad de jugadores cubiertos.
     - **Alertas**: cantidad de alertas activas y cantidad de alertas disparadas.
     - **Rendimiento y Caché**: cantidad de aciertos (hits), fallos (misses) y tasa de acierto porcentual (hit ratio) de la caché del ranking, latencia promedio (ms) de consulta del ranking, y timestamp de generación del reporte.

2. **Comportamiento ante Sistema Sin Operaciones Previas**:
   - **Given** un entorno recién inicializado sin transacciones ni consultas previas al ranking,
   - **When** se consulta `GET /metrics/advanced`,
   - **Then** el sistema responde HTTP `200 OK` sin errores aritméticos ni divisiones por cero, informando contadores en `0`, tasas porcentuales en `0.0` y listas vacías donde corresponda.

3. **Control de Acceso al Endpoint de Métricas Avanzadas**:
   - **Given** una solicitud sin credenciales válidas (sin JWT ni API Key),
   - **When** intenta consultar `GET /metrics/advanced`,
   - **Then** el sistema rechaza la petición con HTTP `401 Unauthorized` utilizando la estructura de error estándar en español.

4. **Rechazo a Usuario Regular en Métricas Avanzadas (`403 Forbidden`)**:
   - **Given** un usuario regular autenticado (sin rol de superusuario),
   - **When** intenta consultar `GET /metrics/advanced`,
   - **Then** el sistema rechaza la petición con HTTP `403 Forbidden` con estructura `ProblemDetail` y código `ACCESS_DENIED`.

---

### User Story 6 - Tag en GitHub y Confección de Release Notes de Entrega 3 (Priority: P2)

Como equipo de proyecto y cátedra evaluadora, quiero contar con un documento de Release Notes claro y completo para la Entrega 3, acompañado de su correspondiente etiqueta (Tag) y Release publicado en GitHub sobre la versión estable y verificada, para dejar constancia formal del cierre del trabajo práctico y facilitar su revisión.

**Why this priority**: Es un requerimiento CORE obligatorio de cierre de Entrega 3 que se ejecuta una vez completadas y validadas las demás historias de usuario.

**Independent Test**: Verificar la existencia del archivo de Release Notes actualizado con los cambios de Entrega 3, la presencia del tag semántico en el repositorio (`v3.0.0`) apuntando al commit verificado con CI y SonarCloud en verde, y la publicación de la Release correspondiente en GitHub.

**Acceptance Scenarios**:

1. **Confección de Release Notes Completas**:
   - **Given** todas las funcionalidades y requerimientos core de Entrega 3 implementados y validados,
   - **When** se confeccionan las Release Notes de la versión final,
   - **Then** el documento incluye: versión y fecha de entrega, resumen ejecutivo, lista detallada de novedades CORE y FUNCIONALES, nuevos endpoints agregados, mejoras de rendimiento y observabilidad, estado de calidad (tests, arquitectura, SonarCloud) e instrucciones breves de verificación.

2. **Creación de Tag Semántico y Publicación de Release**:
   - **Given** la rama `main` con build en estado `SUCCESS`, todas las suites de tests (unit, e2e y arquitectura) pasando y SonarCloud cumpliendo `< 10 issues`,
   - **When** se crea la etiqueta de versión de Entrega 3 (`v3.0.0`) y se publica en GitHub,
   - **Then** el tag y la Release quedan visibles en el repositorio remoto asociados a las Release Notes confeccionadas.

---

### Edge Cases

- **Caída o latencia extrema de Redis**: Si Redis no está disponible o excede el timeout configurado, las lecturas de ranking y catálogo deben degradar transparentemente a PostgreSQL sin devolver errores HTTP 500, y las operaciones de escritura/recálculo no deben abortar su transacción principal por fallos de invalidación de caché.
- **Parámetros nulos, colecciones extensas o binarios en auditoría AOP**: Si un endpoint recibe payloads grandes, archivos o parámetros nulos, el aspecto de auditoría debe serializarlos de forma segura (truncando si superan un límite razonable y manejando valores nulos) sin lanzar excepciones que interfieran con el resultado del endpoint.
- **Datos sensibles anidados en DTOs**: Contraseñas, tokens o API keys dentro de objetos compuestos (request bodies) o headers deben redactarse en auditoría antes de escribir en el log.
- **Exclusión de endpoints de monitoreo del bucle de auditoría excesivo**: El polling frecuente de `/actuator/health` o `/actuator/prometheus` no debe saturar los logs de auditoría funcional de negocio (la auditoría AOP aplica a los controladores de negocio de la aplicación).
- **Concurrencia en invalidación y repoblación de caché (Cache Stampede)**: Bajo ráfagas simultáneas de consultas al ranking tras un recálculo, la estrategia de caché debe minimizar consultas redundantes simultáneas a la base de datos y asegurar consistencia de la clave respecto a la estrategia activa.
- **División por cero en métricas avanzadas**: Si no hay consultas de caché (`hits + misses == 0`), ni tokens emitidos, ni cotizaciones registradas, los porcentajes y promedios en `GET /metrics/advanced` deben retornar `0.0` de forma determinística.
- **Incompatibilidad de tests de arquitectura con perfiles**: Los tests de arquitectura deben ejecutarse de forma rápida y determinística dentro del perfil `unit` sin requerir contenedores externos ni base de datos levantada.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (Tests de Arquitectura)**: El sistema DEBE incluir una suite automatizada de tests de arquitectura que verifique el respeto de la arquitectura en capas (`Controllers → Services → Repositories` y `Adapters`), la ausencia de dependencias cíclicas entre paquetes principales, la prohibición de acceso directo de `Controllers` a `Repositories`/`Adapters` y la no exposición directa de entidades de persistencia desde los `Controllers`.
- **FR-002 (Caché Distribuida con Redis)**: El sistema DEBE utilizar Redis como capa de caché distribuida para las consultas de alta frecuencia (especialmente `GET /players/ranking`, catálogo de jugadores y datos provenientes de proveedores externos), con tiempos de expiración (TTL) configurables por tipo de caché y degradación transparente a datos locales persistidos ante fallos del servicio de caché.
- **FR-003 (Optimización de Ranking para Alta Frecuencia)**: El sistema DEBE optimizar `GET /players/ranking` para soportar alta frecuencia de consultas combinando caché distribuida indexada por estrategia activa, consultas optimizadas con índices adecuados en base de datos (sin consultas N+1) y cero invocaciones a proveedores externos durante la lectura.
- **FR-004 (Consistencia e Invalidación de Caché)**: El sistema DEBE invalidar automáticamente la caché del ranking y de cotizaciones cada vez que se ejecute un recálculo manual (`POST /quotes/recalculate`), un recálculo programado por scheduler o un cambio/activación de estrategia de valoración (`PATCH /admin/strategies/{id}/activate`).
- **FR-005 (Auditoría de Web-Services mediante AOP)**: El sistema DEBE implementar un mecanismo de auditoría transversal mediante Programación Orientada a Aspectos (AOP) que intercepte todos los servicios web publicados de negocio y registre en formato estructurado (utilizando Logback/Log4j, emitiendo tanto a consola estructurada `stdout` como a un archivo rotativo dedicado `logs/audit.log` excluido de Git) al menos: `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` junto con el `correlationId` y el resultado de la operación.
- **FR-006 (Protección de Datos Sensibles en Auditoría)**: El mecanismo de auditoría DEBE sanitizar y redactar obligatoriamente cualquier dato sensible (contraseñas, secretos, tokens JWT, API Keys) antes de escribir en los logs, garantizando cero fugas de credenciales en texto plano.
- **FR-007 (Trazabilidad con Correlation ID)**: El sistema DEBE generar un identificador único `Correlation ID` por cada petición entrante (o reutilizar el provisto en la cabecera `X-Correlation-ID`), propagarlo en el contexto de logging durante todo el ciclo de vida de la solicitud y devolverlo en la cabecera de respuesta HTTP y en las respuestas de error.
- **FR-008 (Endpoints de Monitoreo con Actuator)**: El sistema DEBE exponer y configurar endpoints de monitoreo mediante Spring Boot Actuator incluyendo como mínimo `/actuator/health` (con detalle de estado de dependencias críticas como base de datos y Redis), `/actuator/info` y `/actuator/metrics`, accesibles públicamente para chequeos de infraestructura, recolección y demostración, restringiendo la exposición de endpoints administrativos riesgosos.
- **FR-009 (Métricas Compatibles con Prometheus)**: El sistema DEBE exponer métricas en formato compatible con Prometheus en `/actuator/prometheus` (accesible públicamente para scraping), incorporando tanto métricas estándar de plataforma (solicitudes HTTP, latencias, errores, memoria/CPU, conexiones) como métricas personalizadas del dominio (aciertos/fallos de caché del ranking, operaciones de compra/venta y ejecuciones de recálculo), e incluir el servicio Prometheus en `docker-compose.yml` con su archivo de configuración (`prometheus.yml`) preconfigurado para recolectar automáticamente del backend.
- **FR-010 (Endpoint de Métricas Avanzadas)**: El sistema DEBE exponer el endpoint `GET /metrics/advanced` (sin prefijo `/v1`, documentado en OpenAPI/Swagger y restringido exclusivamente al superusuario: `401 Unauthorized` sin autenticar, `403 Forbidden` para usuarios regulares) que retorne un reporte JSON consolidado con métricas de negocio (volumen de operaciones por tipo, volumen en créditos, liquidez de tokens, top jugadores más operados, estado de estrategia activa, rango de cotizaciones, estado de alertas) y métricas de rendimiento (hits/misses y hit ratio de caché, latencia promedio del ranking y marca temporal del reporte).
- **FR-011 (Estándar de Errores y Documentación)**: Todos los nuevos endpoints y posibles errores derivados DEBEN respetar el contrato sin prefijo `/v1`, documentarse en Swagger/OpenAPI v3 y retornar errores en español bajo el formato estándar `ProblemDetail` (RFC 7807) con `correlationId`.
- **FR-012 (Release Notes y GitHub Tag)**: El proyecto DEBE incluir un documento formal de Release Notes para la Entrega 3 y culminar con la creación de la etiqueta semántica (`v3.0.0`) y publicación de la Release correspondiente en GitHub sobre la rama principal con CI y calidad verificados.

### Key Entities *(include if feature involves data)*

- **WebServiceAuditRecord (Registro de Auditoría de Servicio Web)**: Evento estructurado emitido en logs por cada invocación a un endpoint de negocio. Atributos: `timestamp` (UTC), `correlationId`, `user`, `operation` (controlador/método y ruta HTTP), `parameters` (sanitizados), `executionTimeMs`, `outcome` (`SUCCESS` / `ERROR`).
- **AdvancedMetricsSnapshot (Instantánea de Métricas Avanzadas)**: Estructura de respuesta consolidada de `GET /metrics/advanced` compuesta por cuatro bloques lógicos:
  - `market`: métricas de volumen de transacciones, créditos operados, usuarios activos, liquidez de tokens y ranking de jugadores más negociados.
  - `valuation`: estrategia activa (nombre y versión), último recálculo, cotización promedio/mínima/máxima y total de jugadores valorados.
  - `alerts`: conteo de alertas activas y disparadas.
  - `performance`: métricas de caché de ranking (`hits`, `misses`, `hitRatioPercent`), latencia promedio del ranking (`avgRankingLatencyMs`) y `generatedAt`.
- **CacheEntry (Entrada de Caché de Ranking/Catálogo)**: Representación serializada en caché distribuida asociada a una clave determinística vinculada a la estrategia activa y parámetros de consulta, con tiempo de vida (`TTL`) configurable.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (Rendimiento de Ranking en Alta Frecuencia)**: Al menos el 95% de las consultas repetidas al ranking de jugadores (`GET /players/ranking`) con caché caliente responden en menos de 50 milisegundos, logrando una tasa de aciertos de caché (hit ratio) superior al 90% bajo escenarios de lectura repetitiva sin recálculos intermedios.
- **SC-002 (Consistencia Inmediata de Datos)**: El 100% de las consultas al ranking realizadas inmediatamente después de un recálculo de cotizaciones o un cambio de estrategia activa reflejan los valores actualizados sin servir datos obsoletos.
- **SC-003 (Cobertura Total y Seguridad de Auditoría)**: El 100% de las invocaciones a endpoints de negocio generan un registro de auditoría completo con los 5 campos requeridos (`timestamp`, `user`, `operación/método`, `parámetros`, `tiempoDeEjecución`) más `correlationId`, y el 0% de los registros contienen contraseñas, tokens JWT o API keys en texto plano.
- **SC-004 (Cumplimiento Arquitectónico Automatizado)**: El 100% de las reglas arquitectónicas definidas se verifican automáticamente en la suite de pruebas con 0 violaciones, y el build falla de forma determinística ante cualquier infracción de capas.
- **SC-005 (Observabilidad Operativa Verificable)**: Los endpoints de salud (`/actuator/health`), métricas operativas (`/actuator/metrics`), exportación Prometheus (`/actuator/prometheus`) y métricas avanzadas (`GET /metrics/advanced`) responden con estado exitoso (`200 OK`) y datos consistentes con el estado real del sistema en menos de 500 milisegundos.
- **SC-006 (Resiliencia ante Fallo de Caché)**: El 100% de las solicitudes al ranking y catálogo continúan respondiendo exitosamente (`200 OK`) cuando el servicio de caché distribuida se encuentra temporalmente inaccesible.
- **SC-007 (Calidad de Entrega y Release Final)**: La versión etiquetada en GitHub (`v3.0.0`) cuenta con Release Notes completas, pipeline de CI en estado `SUCCESS`, 100% de pruebas unitarias, E2E y de arquitectura aprobadas, y SonarCloud manteniendo menos de 10 issues totales (`Issues < 10`).

---

## Assumptions

- **Alcance Exclusivamente Backend y Cierre de Repositorio (Fuera de Alcance: Frontend)**: Los requerimientos de la Entrega 3 (tests de arquitectura, caché Redis, auditoría AOP, Actuator, Prometheus, optimización de ranking, endpoint de métricas avanzadas y tag/release) corresponden exclusivamente al backend y a la gestión de versiones del repositorio. No se realizan modificaciones en el frontend React dentro de esta especificación.
- **Política de Acceso a Monitoreo y Métricas**:
  - `/actuator/health`, `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` son públicos para chequeos de contenedores (Docker healthcheck), recolección desde Prometheus y verificación operativa en vivo.
  - `GET /metrics/advanced` está restringido exclusivamente al **superusuario** (`401 Unauthorized` si no está autenticado y `403 Forbidden` con código `ACCESS_DENIED` para usuarios regulares).
- **Infraestructura Redis Existente**: El archivo `docker-compose.yml` ya declara un servicio Redis (`redis:7-alpine`) y el backend ya cuenta con dependencias base de Redis y Actuator; esta entrega consolida su configuración productiva, tolerancia a fallos, métricas, invalidación y pruebas de integración.
- **Nomenclatura de Versión de Entrega 3**: Se asume el esquema de versionado semántico `v3.0.0` para el Tag y Release en GitHub correspondiente a la Entrega 3, pudiendo ajustarse el nombre exacto del tag si la cátedra indica otra convención antes del cierre.
- **Perfiles de Testing**: Los tests de arquitectura se ejecutan dentro del perfil `unit` (rápido, sin infraestructura externa) y los tests de integración de caché Redis, Actuator/Prometheus y métricas avanzadas se cubren en el perfil `e2e` sobre contenedores gestionados (PostgreSQL y Redis) o pruebas de contexto aisladas según corresponda.
