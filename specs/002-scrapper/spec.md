# Feature Specification: Microservicio de Datos Externos de Jugadores y Partidos (WhoScored & Football-Data.org)

**Feature Branch**: `002-player-scraper`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Microservicio en Node.js, ubicado en /scraping, consumido exclusivamente por el backend (/back) a través de un Adapter. Integra dos fuentes de datos externas: (1) WhoScored vía web scraping para estadísticas individuales de rendimiento de jugadores y fallback de alineaciones, y (2) Football-Data.org vía API REST oficial para resultados de partidos y fixtures de las cinco ligas principales (Premier League, Bundesliga, La Liga, Serie A, Ligue 1)."

## User Scenarios & Testing *(mandatory)*

> **Nota sobre Autenticación**: La autenticación de servicio a servicio mediante la cabecera fija `X-API-Key` (FR-003, FR-004) es un requisito fundacional transversal activo desde la primera tarea de infraestructura, no una historia de usuario diferible ni con prioridad propia. Aplica de manera uniforme a todos los endpoints de negocio, quedando excluido únicamente el endpoint público `GET /health` (FR-023).

### User Story 1 - Extracción y consulta de estadísticas crudas de jugador desde WhoScored (Priority: P1)

El backend de la plataforma necesita consultar periódicamente las estadísticas de rendimiento de un jugador de fútbol a partir de su identificador en WhoScored para que el módulo de valoración pueda calcular su cotización de mercado. El microservicio extrae la información vía scraping dinámico en tiempo real o bajo demanda y la entrega de forma estructurada.

**Why this priority**: Es la funcionalidad principal para la cotización de jugadores. Sin la capacidad de extraer y entregar las 10 métricas básicas individuales, el backend no puede alimentar las estrategias de valoración ni el mercado de tokens.

**Independent Test**: Se puede probar de forma independiente invocando `GET /players/{whoscoredId}/stats` con el header de autenticación `X-API-Key` y verificando que el payload devuelto contenga exactamente los 10 campos requeridos con sus tipos de datos numéricos correctos.

**Acceptance Scenarios**:

1. **Given** un identificador válido `whoscoredId` existente en la fuente externa y una solicitud con header `X-API-Key` válido, **When** el backend solicita `GET /players/{whoscoredId}/stats`, **Then** el microservicio responde con código HTTP 200 y un JSON con las métricas crudas: `goals`, `assists`, `shots`, `keyPasses`, `dribbles`, `tackles`, `rating`, `minutosJugados`, `tarjetasAmarillas` y `tarjetasRojas`.
2. **Given** un jugador con valores faltantes o no registrados en alguna métrica individual de la página (por ejemplo sin tarjetas o sin asistencias), **When** se procesa la página, **Then** el microservicio normaliza esos valores a 0 (o su equivalente numérico neutro) manteniendo la consistencia de tipos numéricos.

---

### User Story 2 - Consulta de resultados y fixtures desde Football-Data.org con fallback de alineaciones a WhoScored (Priority: P2)

El backend necesita obtener el calendario de partidos (fixtures) y resultados finalizados de las 5 ligas principales (Premier League, Bundesliga, La Liga, Serie A, Ligue 1) consumiendo la API REST oficial de Football-Data.org. Dado que el plan gratuito de Football-Data.org no incluye alineaciones (lineups), el backend obtiene los datos de alineación a través de un mecanismo de fallback hacia WhoScored mediante un endpoint dedicado `GET /lineups` que realiza matching por nombres de equipo y fecha sin colisionar con rutas dinámicas de partidos.

**Why this priority**: La integración de resultados, fixtures y alineaciones de las 5 ligas es un requerimiento explícito del documento de visión y constitución del proyecto para dar soporte al catálogo deportivo y contextualizar el rendimiento de los jugadores.

**Independent Test**: Se puede probar de forma aislada invocando: (a) los endpoints de partidos y fixtures (usando fixtures JSON grabados de Football-Data.org en tests) con el header `X-API-Key`, y (b) el endpoint de alineaciones `GET /lineups` (usando fixtures HTML grabados de WhoScored en tests).

**Acceptance Scenarios**:

1. **Given** un código de competición válido (ej. `PL`, `BL1`, `PD`, `SA`, `FL1`) y un rango de fechas opcional, **When** el backend solicita la lista de partidos, **Then** el microservicio consulta Football-Data.org con su token `X-Auth-Token` y responde con HTTP 200 y una colección estructurada de objetos `MatchFixture` normalizados.
2. **Given** un identificador de partido válido `matchId`, **When** el backend solicita el detalle de dicho partido a Football-Data.org, **Then** el microservicio devuelve con HTTP 200 el detalle del encuentro (estado y resultado, sin alineaciones ya que Football-Data.org no las incluye en su plan gratuito).
3. **Given** un identificador de partido `matchId` que no existe en Football-Data.org, **When** el backend solicita su detalle, **Then** el microservicio responde con HTTP 404 y código de error estructurado `MATCH_NOT_FOUND`.
4. **Given** los parámetros `homeTeam`, `awayTeam` y `date` (formato ISO 8601), **When** el backend solicita `GET /lineups`, **Then** el microservicio busca el partido correspondiente en WhoScored mediante matching de equipos y fecha, y devuelve con HTTP 200 un objeto `MatchLineup` con `source: "WHOSCORED"`, formaciones, titulares (`startingXI`) y suplentes (`bench`).
5. **Given** una solicitud a `GET /lineups` donde el partido no puede ser localizado en WhoScored tras la normalización básica de nombres, **When** se evalúa la búsqueda, **Then** el microservicio responde con HTTP 404 y código de error estructurado `MATCH_NOT_FOUND`.
6. **Given** que la API de Football-Data.org responde con HTTP 429 (límite de 10 peticiones/minuto excedido), **When** el microservicio detecta esta respuesta, **Then** responde al backend con código HTTP 429 y un cuerpo de error estructurado con código `RATE_LIMIT_EXCEEDED` en lugar de reenviar el error crudo o fallar con 500 genérico.
7. **Given** una falla de autenticación contra Football-Data.org (HTTP 401 o 403 por token inválido, revocado o ausente), **When** se procesa la solicitud, **Then** el microservicio responde al backend con HTTP 502 y código estructurado `EXTERNAL_API_AUTH_ERROR`, distinguiéndose claramente del error `UNAUTHORIZED` (que aplica a la clave entre backend y microservicio).

---

### User Story 3 - Resiliencia y tipado de errores de WhoScored (Priority: P2)

Cuando la fuente externa de scraping (WhoScored) presenta fallas de red, demoras excesivas, mecanismos de bloqueo (anti-scraping) o el recurso solicitado no existe (sea un jugador o un partido para alineaciones), el microservicio debe identificar el escenario exacto y responder con un código de error específico y tipado. Esto permite que el Adapter del backend distinga con precisión cuándo usar datos locales/caché o cuándo informar la no existencia del recurso, evitando en todo momento responder con un HTTP 500 genérico.

**Why this priority**: Es un requisito fundamental de la constitución y de las decisiones de diseño para garantizar la tolerancia a fallos ante proveedores externos y evitar caídas en cascada hacia el backend.

**Independent Test**: Se puede probar de forma aislada simulando escenarios de error (perfil inexistente, partido no encontrado, timeout tras reintentos, bloqueo HTTP 403/429/antibot) y validando que el microservicio devuelva el código HTTP y el código de error estructurado correspondiente sin emitir un 500 genérico.

**Acceptance Scenarios**:

1. **Given** un `whoscoredId` que no existe en WhoScored, **When** el microservicio navega y detecta que la página no existe (404), **Then** responde con HTTP 404 y un cuerpo JSON de error con código `PLAYER_NOT_FOUND`.
2. **Given** una consulta a `GET /lineups` donde el partido no existe en WhoScored, **When** concluye la búsqueda, **Then** responde con HTTP 404 y código `MATCH_NOT_FOUND`.
3. **Given** una falla de conectividad o demora prolongada en WhoScored (en scraping de jugador o de alineación), **When** el microservicio ejecuta los reintentos configurados con backoff exponencial y se supera el tiempo límite máximo de espera, **Then** responde con HTTP 504 y código estructurado `SCRAPE_TIMEOUT`.
4. **Given** una respuesta de WhoScored que bloquea o detecta automatización (CAPTCHA, HTTP 403, HTTP 429), **When** se agotan los reintentos, **Then** responde con HTTP 502 y código estructurado `SCRAPE_BLOCKED`.

---

### User Story 4 - Control defensivo de concurrencia y rate limiting hacia fuentes externas (Priority: P3)

Cuando el backend solicita operaciones simultáneas (por ejemplo durante procesos de actualización periódica), el microservicio debe: (1) regular el paralelismo de instancias de scraping hacia WhoScored (tanto para estadísticas de jugadores como para alineaciones) para evitar bloqueos por IP, y (2) regular la cadencia de peticiones hacia Football-Data.org para respetar el límite de 10 requests por minuto de su plan gratuito.

**Why this priority**: Protege la disponibilidad del servicio y evita sanciones o bloqueos temporales por parte de los proveedores externos ante ráfagas de tráfico originadas en el backend.

**Independent Test**: Se prueba enviando ráfagas de peticiones concurrentes y verificando que el microservicio encole/regule las consultas salientes hacia ambas fuentes respetando sus respectivos límites.

**Acceptance Scenarios**:

1. **Given** un límite de concurrencia de scraping configurado (ej. 2 navegaciones paralelas hacia WhoScored), **When** se reciben 5 solicitudes simultáneas de stats o alineaciones, **Then** el microservicio procesa como máximo 2 navegaciones en paralelo hacia WhoScored.
2. **Given** una ráfaga de consultas hacia Football-Data.org que supere 10 peticiones en menos de un minuto, **When** ingresan las solicitudes, **Then** el microservicio aplica throttling o encolado interno para no exceder las 10 llamadas/minuto contra la API oficial externa.

---

### Edge Cases

- **Partidos no encontrados y matching de equipos (`MATCH_NOT_FOUND`)**:
  - En `GET /lineups` (WhoScored): El emparejamiento por `homeTeam`, `awayTeam` y `date` es *best-effort* y puede fallar debido a diferencias de nomenclatura entre fuentes (ej. `"Manchester United"` en Football-Data.org vs `"Man Utd"` en WhoScored). Si no se encuentra un partido coincidente tras aplicar una normalización básica (conversión a minúsculas, eliminación de acentos, espacios sobrantes y supresión de sufijos comunes como `"FC"` o `"CF"`), el microservicio DEBE responder con HTTP 404 y código estructurado `MATCH_NOT_FOUND`. No se deben implementar heurísticas de aproximación de nombres complejas más allá de esta normalización básica.
  - En `GET /matches/{matchId}` (Football-Data.org): Si el identificador no existe en la API externa, el microservicio DEBE responder con HTTP 404 y el mismo código estructurado `MATCH_NOT_FOUND`, unificando la semántica de partido inexistente.
- **Métricas no publicadas o formatos no numéricos en WhoScored**: Si la página presenta guiones (`"-"`), espacios en blanco o valores nulos para un jugador en un partido o temporada, el microservicio debe convertirlos de manera segura a `0` (o `0.0` para el rating) evitando errores de parsing.
- **Identificador de jugador o parámetros de consulta inválidos**: Si el `whoscoredId`, el código de competición no pertenece al enum permitido (`PL`, `BL1`, `PD`, `SA`, `FL1`), o los parámetros obligatorios de fecha (`dateFrom`, `dateTo`, `date`) no siguen el formato `YYYY-MM-DD`, o `homeTeam`/`awayTeam` están vacíos en `/lineups`, el microservicio debe responder con HTTP 400 y código `INVALID_REQUEST_PARAMS`.
- **Estructura HTML modificada en WhoScored**: Si la página responde con HTTP 200 pero los selectores clave no se encuentran en el documento, se debe interpretar como fallo de extracción o bloqueo y responder con `SCRAPE_BLOCKED` indicando el incidente en los logs estructurados, sin responder con 500 no controlado.
- **Partidos aplazados o cancelados en Football-Data.org**: El modelo `MatchFixture` debe soportar estados como `POSTPONED`, `CANCELLED` o `SUSPENDED` sin que los campos de resultado nulo generen excepciones.
- **Límites de cuota diaria o mensual de Football-Data.org**: Si la API externa responde con rechazo de cuota no recuperable en la ventana de 1 minuto, el microservicio debe responder con `RATE_LIMIT_EXCEEDED` informando en los logs estructurados el detalle.
- **Cancelación de petición por parte del cliente**: Si el backend corta la conexión antes de completar la consulta externa, el microservicio debe escuchar el evento `close` del request (`req.on('close')`) y abortar la navegación pendiente en Puppeteer liberando la página y los recursos asociados.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El microservicio DEBE ubicarse en el directorio `/scraping` en la raíz del proyecto y no debe alterar ningún archivo dentro de `/back` ni `/front`.
- **FR-002**: El microservicio DEBE exponer un endpoint REST `GET /players/{whoscoredId}/stats` para obtener estadísticas de jugadores mediante scraping de WhoScored.
- **FR-003**: El microservicio DEBE exigir y validar el header `X-API-Key` en todas las solicitudes entrantes de negocio desde la fase inicial de infraestructura, comparándolo contra la variable de entorno `API_KEY`.
- **FR-004**: Si el header `X-API-Key` está ausente o no coincide con la variable de entorno `API_KEY`, el microservicio DEBE rechazar la petición en menos de 20 ms con código HTTP 401 y código de error estructurado `UNAUTHORIZED` sin invocar navegadores ni APIs externas.
- **FR-005**: El endpoint `GET /players/{whoscoredId}/stats` DEBE retornar un JSON con las siguientes 10 métricas de rendimiento crudas del jugador:
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
- **FR-006**: El microservicio NO DEBE implementar ninguna lógica de valuación, cálculo de cotización, pricing ni ponderación de métricas; únicamente debe extraer y retornar datos crudos.
- **FR-007**: Si el jugador no existe en WhoScored, el microservicio DEBE responder con código HTTP 404 y código estructurado `PLAYER_NOT_FOUND`.
- **FR-008**: Si WhoScored excede el tiempo límite configurado tras agotar la política de reintentos con backoff exponencial (en estadísticas o alineaciones), el microservicio DEBE responder con código HTTP 504 y código estructurado `SCRAPE_TIMEOUT`.
- **FR-009**: Si WhoScored detecta la automatización o bloquea el scraping (anti-bot, CAPTCHA, 403, 429), el microservicio DEBE responder con código HTTP 502 y código estructurado `SCRAPE_BLOCKED`.
- **FR-010**: El microservicio NUNCA DEBE devolver un código HTTP 500 genérico no estructurado ante fallas de cualquiera de los proveedores externos o del proceso de extracción.
- **FR-011**: El microservicio DEBE implementar una estrategia de reintentos configurables (ej. 3 intentos) con backoff exponencial ante errores transitorios de red o timeouts en la navegación de WhoScored.
- **FR-012**: El microservicio DEBE limitar la cantidad de navegaciones paralelas simultáneas hacia WhoScored mediante un mecanismo de control de concurrencia configurable para mitigar riesgos de rate limiting por IP.
- **FR-013**: El microservicio DEBE exponer un endpoint REST `GET /competitions/{competitionCode}/matches` que consulte y normalice los partidos (fixtures y resultados) de Football-Data.org para una competición y rango de fechas opcional (`dateFrom`, `dateTo`), validando que `competitionCode` pertenezca al enum permitido (`PL`, `BL1`, `PD`, `SA`, `FL1`).
- **FR-014**: El microservicio DEBE exponer un endpoint REST `GET /matches/{matchId}` para obtener el detalle de un partido puntual (estado y resultado) desde Football-Data.org (sin alineaciones). Si el partido no existe en Football-Data.org, DEBE responder con HTTP 404 y código estructurado `MATCH_NOT_FOUND`.
- **FR-015**: El microservicio DEBE exponer un endpoint REST `GET /lineups` con query params obligatorios `homeTeam`, `awayTeam` y `date` (en formato ISO-8601 `YYYY-MM-DD`), que busque el partido correspondiente en WhoScored por coincidencia de equipos y fecha, y devuelva la alineación (`startingXI`, `bench`, formación táctica y `source: "WHOSCORED"`) si se encuentra, o responda con código HTTP 404 y código estructurado `MATCH_NOT_FOUND` si no se localiza. Los errores `SCRAPE_TIMEOUT` y `SCRAPE_BLOCKED` aplican igualmente a este endpoint.
- **FR-016**: El microservicio DEBE autenticarse contra Football-Data.org utilizando el header `X-Auth-Token` alimentado por la variable de entorno `FOOTBALL_DATA_API_KEY`, de manera totalmente independiente del header `X-API-Key` de autenticación interna.
- **FR-017**: El microservicio DEBE respetar el límite de tasa de Football-Data.org (10 requests por minuto en plan gratuito) mediante mecanismos de throttling o cola interna, evitando bloqueos por rate limiting.
- **FR-018**: Si Football-Data.org responde con HTTP 429 (límite de tasa excedido), el microservicio DEBE responder al backend con código HTTP 429 y código estructurado `RATE_LIMIT_EXCEEDED` en lugar de propagar un error no controlado o 500 genérico.
- **FR-019**: Si Football-Data.org responde con HTTP 401 o 403 por token inválido o revocado, el microservicio DEBE responder con código HTTP 502 y código estructurado `EXTERNAL_API_AUTH_ERROR`, diferenciándolo de `UNAUTHORIZED`.
- **FR-020**: La suite de tests automatizados DEBE ser completamente hermética en CI:
  - Para WhoScored (estadísticas de jugador y alineaciones): DEBE utilizar exclusivamente fixtures HTML grabados localmente en el repositorio.
  - Para Football-Data.org (competiciones, fixtures y partidos): DEBE utilizar exclusivamente fixtures JSON grabados localmente en el repositorio.
  - En ningún caso DEBE realizar llamadas de red a sitios o APIs en vivo durante la ejecución de CI.
- **FR-021**: El microservicio DEBE ser incorporado en el archivo `docker-compose.yml` existente como servicio adicional conectado a la red `cbo_network`, sin modificar la configuración ya definida para los servicios `db` y `backend`.
- **FR-022**: El microservicio DEBE ser configurable mediante variables de entorno para: `PORT`, `API_KEY`, `FOOTBALL_DATA_API_KEY`, `SCRAPE_TIMEOUT_MS`, `MAX_RETRIES` y `MAX_CONCURRENT_SCRAPES`.
- **FR-023**: El microservicio DEBE exponer un endpoint público `GET /health` que responda HTTP 200 con `{ "status": "UP", "timestamp": "..." }` excluido del middleware de autenticación `X-API-Key`, destinado exclusivamente a los healthchecks del contenedor Docker y orquestadores sin comprometer la seguridad.

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
- **MatchFixture**: Modelo de datos de solo lectura que representa un partido extraído de Football-Data.org.
  - `id`: Identificador numérico único del partido en la fuente externa.
  - `competition`: Código y nombre de la competición (ej. Premier League - `PL`).
  - `utcDate`: Fecha y hora del partido en formato UTC ISO-8601.
  - `status`: Estado del partido (`SCHEDULED`, `TIMED`, `IN_PLAY`, `PAUSED`, `FINISHED`, `POSTPONED`, `CANCELLED`).
  - `matchday`: Jornada o fecha del torneo.
  - `homeTeam`: Nombre e identificador del equipo local.
  - `awayTeam`: Nombre e identificador del equipo visitante.
  - `score`: Detalle de goles (tiempo regular, tiempo extra, penales) si el partido está en juego o finalizado.
- **MatchLineup**: Estructura de datos que representa la alineación de un partido obtenida vía fallback desde WhoScored.
  - `source`: Cadena fija indicando la fuente de los datos (siempre `"WHOSCORED"`).
  - `homeTeam`: Objeto con formación táctica (ej. `"4-3-3"`), titulares (`startingXI`) y suplentes (`bench`).
  - `awayTeam`: Objeto con formación táctica (ej. `"4-4-2"`), titulares (`startingXI`) y suplentes (`bench`).
  - `date`: Fecha del encuentro correspondiente en formato ISO-8601 (`YYYY-MM-DD`).
- **ErrorResponse**: Modelo de datos estándar para las respuestas de error del microservicio.
  - `error`: Identificador textual único y estable del error (`UNAUTHORIZED`, `INVALID_REQUEST_PARAMS`, `PLAYER_NOT_FOUND`, `MATCH_NOT_FOUND`, `SCRAPE_TIMEOUT`, `SCRAPE_BLOCKED`, `RATE_LIMIT_EXCEEDED`, `EXTERNAL_API_AUTH_ERROR`).
  - `message`: Descripción en lenguaje natural en español comprensible para el consumidor.
  - `status`: Código numérico de estado HTTP correspondiente.
  - `timestamp`: Marca de tiempo en formato ISO-8601 del momento en que ocurrió el error.
- **HealthResponse**: Modelo para el estado del servicio.
  - `status`: Cadena (`"UP"`).
  - `timestamp`: Marca de tiempo en formato ISO-8601.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las respuestas exitosas de estadísticas de jugador devuelven el esquema JSON completo con las 10 métricas tipadas numéricamente sin valores nulos indefinidos.
- **SC-002**: El 100% de las respuestas de fixtures y resultados de partidos devuelven objetos normalizados según el esquema `MatchFixture` respetando los tipos de datos de fecha y equipos.
- **SC-003**: El 100% de las respuestas exitosas de alineaciones en `GET /lineups` devuelven el esquema `MatchLineup` con `source: "WHOSCORED"`, formaciones, titulares y suplentes.
- **SC-004**: El 0% de las solicitudes fallidas por caídas, bloqueos, timeouts, rate limits, errores de autenticación o recursos inexistentes retornan códigos HTTP 500 genéricos; el 100% retorna errores estructurados tipados (`SCRAPE_TIMEOUT`, `SCRAPE_BLOCKED`, `PLAYER_NOT_FOUND`, `MATCH_NOT_FOUND`, `RATE_LIMIT_EXCEEDED`, `EXTERNAL_API_AUTH_ERROR`).
- **SC-005**: El 100% de las solicitudes a endpoints de negocio sin clave válida o con clave errónea son rechazadas con HTTP 401 en menos de 20 ms sin invocar navegadores ni APIs externas.
- **SC-006**: El microservicio no supera bajo ninguna circunstancia el límite de 10 requests por minuto hacia Football-Data.org gracias a su mecanismo de regulación interno.
- **SC-007**: El 100% de los tests automatizados del microservicio se ejecutan de forma hermética y exitosa en CI: WhoScored (stats y alineaciones) mediante fixtures HTML y Football-Data.org mediante fixtures JSON, sin conexión a Internet.
- **SC-008**: El microservicio se integra exitosamente en el entorno de `docker-compose.yml` preexistente sobre la red `cbo_network`, logrando conectividad con el backend sin alterar los servicios `db` o `backend`.
- **SC-009**: El endpoint público `GET /health` responde en menos de 10 ms con HTTP 200 sin autenticación, permitiendo al healthcheck de Docker validar la salud del servicio sin interferir con las rutas protegidas.

---

## Assumptions

- **Consumidor único**: El único cliente autorizado para interactuar con este microservicio es el backend principal de la plataforma (`/back`) a través de un Adapter interno.
- **Autenticación transversal activa**: La cabecera `X-API-Key` es obligatoria desde la primera tarea en todos los endpoints funcionales; únicamente `GET /health` queda excluido por requerimientos operativos de Docker.
- **Identificadores externos y matching**: Se asume que el backend conoce el identificador correspondiente a cada jugador en WhoScored (`whoscoredId`) y los códigos oficiales de competición de Football-Data.org (`PL`, `BL1`, `PD`, `SA`, `FL1`). Para las alineaciones, el matching por `homeTeam` + `awayTeam` + `date` entre ambas fuentes es *best-effort* y puede no encontrar coincidencia en casos donde la nomenclatura de los equipos sea muy divergente entre las plataformas, respondiendo en tal caso con `MATCH_NOT_FOUND` (404).
- **Ausencia de alineaciones en Football-Data.org**: Queda confirmado que el plan gratuito de Football-Data.org no incluye alineaciones (lineups); por lo tanto, la obtención de alineaciones se delega exclusivamente en el endpoint de fallback hacia WhoScored (`GET /lineups`).
- **Estrategia dual de testing con fixtures**: Los tests unitarios y de integración de WhoScored (stats y alineaciones) emplean exclusivamente snapshots HTML grabados en el repo; los tests de Football-Data.org emplean exclusivamente snapshots JSON de respuestas de la API oficial grabados en el repo. Ninguno de los dos hace peticiones de red en CI.
- **Rate limit de Football-Data.org**: Se asume el límite estricto de 10 requests/minuto para el plan gratuito; el microservicio implementa throttling/cola local para respetar este límite.
- **Ausencia de estado persistente local**: El microservicio es de solo lectura y stateless; no requiere base de datos propia, caching persistente ni almacenamiento de transacciones.
- **Gestión de variables de entorno**: Las claves `API_KEY`, `FOOTBALL_DATA_API_KEY` y los parámetros operativos de timeout y reintentos se definen a nivel de entorno / compose y nunca quedan commiteados en texto plano en el repositorio.
- **Stack técnico**: Se mantiene Node.js 20 LTS sobre la imagen base oficial `node:20-slim`.
