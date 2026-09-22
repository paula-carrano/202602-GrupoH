# Feature Specification: Microservicio de Scraping de Jugadores (WhoScored)

**Feature Branch**: `002-player-scraper`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Microservicio de scraping de jugadores en Node.js, ubicado en /scraping, consumido exclusivamente por el backend (/back) a través de un Adapter. Adjunto el documento de decisiones ya cerradas para este componente (plantilla-scraper-completada); usalo como fuente de verdad para todos los requisitos, no lo reinterpretes ni cambies ninguna decisión ya tomada ahí."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Extracción y consulta de estadísticas crudas de jugador (Priority: P1)

El backend de la plataforma necesita consultar periódicamente las estadísticas de rendimiento de un jugador de fútbol a partir de su identificador en WhoScored para que el módulo de valoración pueda calcular su cotización de mercado. El microservicio extrae la información en tiempo real o bajo demanda y la entrega de forma estructurada.

**Why this priority**: Es la funcionalidad principal del microservicio. Sin la capacidad de extraer y entregar las 10 métricas básicas, el backend no puede alimentar las estrategias de valoración ni el mercado de tokens.

**Independent Test**: Se puede probar de forma independiente invocando `GET /players/{whoscoredId}/stats` con el header de autenticación `X-API-Key` y verificando que el payload devuelto contenga exactamente los 10 campos requeridos con sus tipos de datos correctos.

**Acceptance Scenarios**:

1. **Given** un identificador válido `whoscoredId` existente en la fuente externa y una solicitud con header `X-API-Key` válido, **When** el backend solicita `GET /players/{whoscoredId}/stats`, **Then** el microservicio responde con código HTTP 200 y un JSON con las métricas crudas: `goals`, `assists`, `shots`, `keyPasses`, `dribbles`, `tackles`, `rating`, `minutosJugados`, `tarjetasAmarillas` y `tarjetasRojas`.
2. **Given** un jugador con valores faltantes o no registrados en alguna métrica individual de la página (por ejemplo sin tarjetas o sin asistencias), **When** se procesa la página, **Then** el microservicio normaliza esos valores a 0 (o su equivalente numérico neutro) manteniendo la consistencia de tipos numéricos.

---

### User Story 2 - Resiliencia y tipado de errores del proveedor externo (Priority: P2)

Cuando la fuente externa presenta fallas de red, demoras excesivas, mecanismos de bloqueo (anti-scraping) o el jugador solicitado no existe, el microservicio debe identificar el escenario exacto y responder con un código de error específico y tipado. Esto permite que el Adapter del backend distinga con precisión cuándo usar datos locales/caché o cuándo informar la no existencia del recurso, evitando en todo momento responder con un HTTP 500 genérico.

**Why this priority**: Es un requisito fundamental de la constitución y de las decisiones de diseño para garantizar la tolerancia a fallos ante proveedores externos y evitar caídas en cascada hacia el backend.

**Independent Test**: Se puede probar de forma aislada simulando escenarios de error (perfil inexistente, timeout tras reintentos, bloqueo HTTP 403/429/antibot) y validando que el microservicio devuelva el código HTTP y el código de error estructurado correspondiente sin emitir un 500 genérico.

**Acceptance Scenarios**:

1. **Given** un `whoscoredId` que no existe en la fuente externa, **When** el microservicio navega y detecta que la página no existe (404), **Then** responde con HTTP 404 y un cuerpo JSON de error con código `PLAYER_NOT_FOUND`.
2. **Given** una falla de conectividad o demora prolongada en la fuente externa, **When** el microservicio ejecuta los reintentos configurados con backoff exponencial y se supera el tiempo límite máximo de espera, **Then** responde con HTTP 504 y código estructurado `SCRAPE_TIMEOUT`.
3. **Given** una respuesta de la fuente externa que bloquea o detecta automatización (CAPTCHA, HTTP 403, HTTP 429), **When** se agotan los reintentos, **Then** responde con HTTP 502 y código estructurado `SCRAPE_BLOCKED`.

---

### User Story 3 - Autenticación de servicio a servicio mediante API Key (Priority: P3)

El microservicio de scraping es un componente privado de uso interno, consumido exclusivamente por el backend mediante un Adapter. Debe restringir el acceso a clientes que presenten una clave de servicio compartida predefinida, sin utilizar ni gestionar sesiones JWT de usuario final.

**Why this priority**: Garantiza la seguridad y el aislamiento del microservicio dentro de la arquitectura, asegurando que solo el backend pueda emitir órdenes de scraping.

**Independent Test**: Se prueba enviando solicitudes sin el header `X-API-Key` o con una clave incorrecta, verificando el rechazo inmediato con HTTP 401.

**Acceptance Scenarios**:

1. **Given** una petición al endpoint `GET /players/{whoscoredId}/stats` que no incluye el header `X-API-Key`, **When** se evalúa la solicitud, **Then** responde inmediatamente con HTTP 401 y código `UNAUTHORIZED`, sin iniciar el navegador ni realizar peticiones externas.
2. **Given** una petición con un header `X-API-Key` cuyo valor no coincide con la clave configurada en el entorno, **When** se evalúa la solicitud, **Then** responde con HTTP 401 y código `UNAUTHORIZED`.
3. **Given** una petición con el header `X-API-Key` correcto, **When** se procesa la solicitud, **Then** la autenticación es exitosa y se procede con el flujo de scraping.

---

### User Story 4 - Control defensivo de concurrencia hacia la fuente externa (Priority: P3)

Cuando el backend solicita estadísticas de múltiples jugadores simultáneamente (por ejemplo durante procesos de actualización periódica), el microservicio debe limitar la cantidad de instancias o navegaciones concurrentes hacia WhoScored para minimizar el riesgo de ser bloqueado por rate limiting de la fuente externa.

**Why this priority**: Protege la disponibilidad del servicio y la reputación de la IP frente al proveedor externo durante cargas en ráfaga.

**Independent Test**: Se prueba enviando una ráfaga de solicitudes paralelas que supere el límite de concurrencia configurado y comprobando que las peticiones se procesen de forma regulada sin saturar la red ni provocar bloqueos masivos.

**Acceptance Scenarios**:

1. **Given** un límite de concurrencia configurado (por ejemplo 2 peticiones paralelas hacia WhoScored), **When** se reciben 5 solicitudes concurrentes válidas, **Then** el microservicio gestiona las peticiones asegurando que no se ejecuten más de 2 navegaciones simultáneas hacia la fuente externa, resolviendo todas ordenadamente.

---

### Edge Cases

- **Métricas no publicadas o formatos no numéricos**: Si WhoScored presenta guiones (`"-"`), espacios en blanco o valores nulos para un jugador en un partido o temporada, el microservicio debe convertirlos de manera segura a `0` (o `0.0` para el rating) evitando que se generen errores de parsing.
- **Identificador de jugador inválido**: Si el `whoscoredId` provisto contiene caracteres inválidos o está vacío, el microservicio debe responder con HTTP 400 y código `INVALID_PLAYER_ID`.
- **Estructura HTML modificada o inesperada en la fuente**: Si la página responde con HTTP 200 pero los selectores clave de estadísticas no se encuentran en el documento, se debe interpretar como fallo de extracción o bloqueo y responder con `SCRAPE_BLOCKED` indicando el incidente en los logs estructurados, sin responder con 500 no controlado.
- **Cancelación o interrupción de conexión por parte del cliente**: Si el backend corta la conexión antes de completar el scraping, el microservicio debe abortar la navegación pendiente y liberar recursos del pool/navegador de forma limpia.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El microservicio DEBE ubicarse en el directorio `/scraping` en la raíz del proyecto y no debe alterar ningún archivo dentro de `/back` ni `/front`.
- **FR-002**: El microservicio DEBE exponer un endpoint REST `GET /players/{whoscoredId}/stats`.
- **FR-003**: El microservicio DEBE exigir y validar el header `X-API-Key` en todas las solicitudes, comparándolo contra la variable de entorno `API_KEY`.
- **FR-004**: Si el header `X-API-Key` está ausente o no coincide con la variable de entorno `API_KEY`, el microservicio DEBE rechazar la petición con código HTTP 401 y código de error `UNAUTHORIZED`.
- **FR-005**: El microservicio DEBE retornar un JSON con las siguientes 10 métricas de rendimiento crudas del jugador:
  - `goals` (número entero >= 0)
  - `assists` (número entero >= 0)
  - `shots` (número entero >= 0)
  - `keyPasses` (número entero >= 0)
  - `dribbles` (número entero >= 0)
  - `tackles` (número entero >= 0)
  - `rating` (número decimal >= 0)
  - `minutosJugados` (número entero >= 0)
  - `tarjetasAmarillas` (número entero >= 0)
  - `tarjetasRojas` (número entero >= 0)
- **FR-006**: El microservicio NO DEBE implementar ninguna lógica de valuación, cálculo de cotización, pricing ni ponderación de métricas; únicamente debe extraer y retornar métricas crudas.
- **FR-007**: Si el jugador no existe en la fuente externa, el microservicio DEBE responder con código HTTP 404 y código estructurado `PLAYER_NOT_FOUND`.
- **FR-008**: Si la fuente externa excede el tiempo límite configurado tras agotar la política de reintentos con backoff exponencial, el microservicio DEBE responder con código HTTP 504 y código estructurado `SCRAPE_TIMEOUT`.
- **FR-009**: Si la fuente externa detecta la automatización o bloquea el scraping (anti-bot, CAPTCHA, 403, 429), el microservicio DEBE responder con código HTTP 502 y código estructurado `SCRAPE_BLOCKED`.
- **FR-010**: El microservicio NUNCA DEBE devolver un código HTTP 500 genérico no estructurado ante fallas del proveedor o del proceso de scraping.
- **FR-011**: El microservicio DEBE implementar una estrategia de reintentos configurables (por ejemplo 3 intentos) con backoff exponencial ante errores transitorios de red o timeouts.
- **FR-012**: El microservicio DEBE limitar la cantidad de navegaciones paralelas simultáneas hacia WhoScored mediante un mecanismo de control de concurrencia configurable para mitigar riesgos de rate limiting.
- **FR-013**: Los tests unitarios e integrados del microservicio DEBEN utilizar fixtures de archivos HTML guardados localmente en el repositorio (snapshots de WhoScored) y NUNCA DEBEN realizar solicitudes a la web real durante la ejecución de CI.
- **FR-014**: El microservicio DEBE ser incorporado en el archivo `docker-compose.yml` existente como servicio adicional conectado a la red `cbo_network`, sin modificar la configuración ya definida para los servicios `db` y `backend`.
- **FR-015**: El microservicio DEBE ser configurable mediante variables de entorno para: `PORT`, `API_KEY`, `SCRAPE_TIMEOUT_MS`, `MAX_RETRIES` y `MAX_CONCURRENT_SCRAPES`.

---

### Key Entities *(include if feature involves data)*

- **PlayerStats**: Modelo de datos de solo lectura que agrupa las estadísticas cuantitativas del jugador extraídas de WhoScored.
  - `goals`: Cantidad total de goles convertidos.
  - `assists`: Cantidad total de asistencias otorgadas.
  - `shots`: Cantidad de tiros al arco.
  - `keyPasses`: Pases clave que derivaron en ocasiones de gol.
  - `dribbles`: Regates / gambetas exitosas.
  - `tackles`: Entradas defensivas e intercepciones.
  - `rating`: Calificación promedio de rendimiento según WhoScored.
  - `minutosJugados`: Total de minutos disputados en el período analizado.
  - `tarjetasAmarillas`: Total de tarjetas amarillas recibidas.
  - `tarjetasRojas`: Total de tarjetas rojas recibidas.
- **ErrorResponse**: Modelo de datos estándar para las respuestas de error del microservicio.
  - `error`: Identificador textual único y estable del error (`UNAUTHORIZED`, `PLAYER_NOT_FOUND`, `SCRAPE_TIMEOUT`, `SCRAPE_BLOCKED`, `INVALID_PLAYER_ID`).
  - `message`: Descripción en lenguaje natural en español comprensible para el consumidor.
  - `status`: Código numérico de estado HTTP correspondiente.
  - `timestamp`: Marca de tiempo en formato ISO-8601 del momento en que ocurrió el error.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las respuestas exitosas de `GET /players/{whoscoredId}/stats` devuelven el esquema JSON completo con las 10 métricas tipadas numéricamente sin valores nulos indefinidos.
- **SC-002**: El 0% de las solicitudes fallidas por caída, bloqueo o timeout del proveedor externo retornan códigos HTTP 500 genéricos; el 100% retorna errores estructurados con código `SCRAPE_TIMEOUT`, `SCRAPE_BLOCKED` o `PLAYER_NOT_FOUND`.
- **SC-003**: El 100% de las solicitudes sin clave válida o con clave errónea son rechazadas con HTTP 401 en un tiempo menor a 20 ms sin instanciar componentes de navegación externa.
- **SC-004**: El 100% de los tests automatizados del microservicio se ejecutan de manera aislada y exitosa en entornos CI sin requerir acceso a Internet ni realizar llamadas externas a WhoScored.
- **SC-005**: El microservicio se integra exitosamente en el entorno de `docker-compose.yml` preexistente sobre la red `cbo_network`, logrando conectividad con el backend sin generar roturas en los servicios `db` o `backend`.

---

## Assumptions

- **Consumidor único**: El único cliente autorizado para interactuar con este microservicio es el backend principal de la plataforma (`/back`) a través de un Adapter interno.
- **Identificador de jugador**: Se asume que el backend conoce el identificador correspondiente a cada jugador en WhoScored (`whoscoredId`) para realizar las consultas.
- **Ausencia de estado persistente local**: El microservicio es de solo lectura y stateless; no requiere base de datos propia, caching persistente ni almacenamiento de transacciones.
- **Gestión de variables de entorno**: La clave `API_KEY` y los parámetros operativos de timeout y reintentos se definen a nivel de entorno / compose y no quedan expuestos en el código del repositorio.
- **Stack de navegación**: La selección definitiva entre Playwright o Puppeteer se formalizará en el plan técnico de implementación (`plan.md`), manteniendo la interfaz del microservicio inalterada.
