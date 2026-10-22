# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 2: Backend Core & Real-Time Engine

**Feature Branch**: `feature/004-entrega2-core-update`  
**Created**: 2026-10-12  
**Updated**: 2026-10-21  
**Status**: Ready for Planning  
**Scope**: **Exclusivamente Backend** (API REST, Lógica de Dominio, Persistencia PostgreSQL, Caché Redis, Scheduler, Integración Externa y Motor de Notificaciones SSE en Tiempo Real). El alcance de interfaz de usuario se encuentra separado atómicamente en [`specs/005-entrega2-frontend/spec.md`](../005-entrega2-frontend/spec.md).  
**Google Docs Oficial del Grupo**: [Documento de Cátedra y SDD](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk) (ID: `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`) — Sub-pestaña `SDD - Back` (`t.w8nwnbazlabd`)  
**Consigna Oficial (Solo Lectura)**: [Documento de Visión](https://docs.google.com/document/d/12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8/edit?usp=sharing) (ID: `12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8`)  
**Input**: User description: "Especificación atómica de BACKEND para la Entrega 2: Cálculo y recálculo de cotización de jugadores según estrategia configurable gestionada por superusuario, compra/venta transaccional de tokens, recarga simulada de créditos, cotización histórica paginada y por fecha dada, portfolio y transacciones auditables inmutables, ranking según estrategia activa, scheduler semanal, motor backend del Feature Adicional (Alertas de Precios con streaming push de notificaciones en tiempo real vía Server-Sent Events - SSE), caché de alta disponibilidad con Redis, separación de profiles unit y e2e como P1 obligatorio, y unificación de sync sin prefijo /v1."

---

## Resumen de Prioridades

- **Prioridad P1 (Crítica / Bloqueante para la Entrega 2):**
  - **User Story 1:** Cálculo y recálculo de cotizaciones de jugadores y activación centralizada de estrategia por superusuario (`PATCH /admin/strategies/{id}/activate`, `POST /quotes/recalculate`).
  - **User Story 2:** Mercado de tokens: órdenes transaccionales de compra/venta y recarga simulada de créditos (`POST /orders/buy`, `POST /orders/sell`, `POST /users/{id}/credits/topup`).
  - **User Story 3:** Consulta de cotización actual, serie histórica paginada y cotización a fecha dada (`GET /players/{id}/quotes`).
  - **User Story 4:** Feature Adicional: Motor de alertas de precios y notificaciones push en tiempo real vía Server-Sent Events (`POST /alerts`, `GET /alerts`, `DELETE /alerts/{id}`, `GET /alerts/stream`).
  - **User Story 7:** Infraestructura Core de cátedra: Separación de profiles de testing `unit` y `e2e` sobre PostgreSQL, unificación de sync WhoScored + Football-Data y rutas sin `/v1`.
- **Prioridad P2 (Importante / Soporte Funcional y Optimización):**
  - **User Story 5:** Endpoints consolidados de portfolio de usuario e historial auditable de transacciones (`GET /users/{id}/portfolio`, `GET /users/{id}/transactions`).
  - **User Story 6:** Ranking de jugadores por estrategia activa con caché Redis y Scheduler semanal `@Scheduled` (`GET /players/ranking`).

---

## Directivas de Cátedra — Alcance Depurado de Entrega 2 (Backend)

Por indicación expresa del cuerpo docente en canal oficial (Discord), se depuran los puntos de infraestructura iniciales que ya fueron cubiertos en la Entrega 1:
- **Descartados (ya resueltos o sin efecto):**
  1. ~~Utilizar HSQLDB / H2 para persistir datos~~ *(El proyecto ya opera directamente sobre PostgreSQL con Testcontainers).*
  2. ~~Crear datos de prueba cuando levanta la aplicación~~ *(Estaba vinculado a la volatilidad de H2).*
  3. ~~Documentación de Endpoints con Swagger~~ *(Cubierto y entregado en Entrega 1).*
  4. ~~Implementar Job de Coverage~~ *(Integrado en SonarCloud durante Entrega 1).*
- **Único punto Core técnico obligatorio en Backend para Entrega 2:**
  1. **Separar profiles de testing para pruebas unitarias (`unit`) y end-to-end (`e2e`) sobre PostgreSQL.** *(Priorizado como P1).*

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cálculo, Recálculo de Cotizaciones y Control Administrativo de Estrategias (Priority: P1)

Como superusuario del sistema, quiero activar y ponderar la estrategia de valoración vigente (`PATCH /admin/strategies/{id}/activate`) de forma centralizada, y como proceso automatizado o administrador, recalcular las cotizaciones de todos los jugadores (`POST /quotes/recalculate`) aplicando la estrategia activa para impactar de forma global las cotizaciones vigentes del mercado, registrando el historial y la versión en la base de datos.

**Why this priority**: Es el núcleo del modelo de dominio. Sin cotizaciones calculadas por estrategia no existe precio de referencia para operar en el mercado de tokens, alimentar las alertas ni construir el ranking.

**Impacto de Negocio**: Al cambiar la estrategia activa o sus pesos, el próximo recálculo de cotizaciones (manual o programado por scheduler) utiliza la nueva estrategia para **TODOS** los jugadores del catálogo, afectando de forma global y centralizada las cotizaciones vigentes del mercado. Es una palanca de política de mercado administrada exclusivamente por el superusuario, no una configuración individual por usuario.

**Independent Test**: Invocar el endpoint administrativo con un usuario regular comprobando `403 Forbidden`; autenticarse como superusuario, activar una nueva estrategia B mediante `PATCH /admin/strategies/{id}/activate`, disparar `POST /quotes/recalculate`, y verificar que cada jugador posea un nuevo registro en `PlayerQuote` con el valor calculado según la fórmula de B, fecha, estrategia activa y su versión correspondiente.

**Acceptance Scenarios**:

1. **Recálculo Masivo Exitoso**:
   - **Given** un catálogo de jugadores con métricas deportivas registradas y una estrategia de valoración activa configurada con pesos para goles, asistencias, minutos, tiros, pases, intercepciones, tarjetas y rating,
   - **When** se invoca `POST /quotes/recalculate`,
   - **Then** el backend calcula la nueva cotización para cada jugador, persiste el nuevo registro en `PlayerQuote` con timestamp, valor, nombre y versión de la estrategia, e invalida el caché en Redis, respondiendo HTTP `200 OK` con la cantidad de cotizaciones actualizadas.

2. **Soporte de Múltiples Estrategias Configurables**:
   - **Given** al menos dos estrategias registradas en el sistema (ej. "Rendimiento Ofensivo" y "Rendimiento Global"),
   - **When** se cambia la estrategia activa y se ejecuta el recálculo,
   - **Then** las nuevas cotizaciones se calculan con las ponderaciones de la nueva estrategia, conservando inalteradas las cotizaciones históricas previas con su versión original.

3. **Recálculo ante Métricas en Cero o Jugadores sin Minutos**:
   - **Given** un jugador sin minutos jugados o con estadísticas en cero,
   - **When** se ejecuta el recálculo,
   - **Then** el sistema asigna el valor base mínimo garantizado (1.0 crédito) sin producir errores aritméticos ni divisiones por cero.

4. **Activación Centralizada de Estrategia por Superusuario**:
   - **Given** el superusuario autenticado con JWT y dos estrategias registradas en el sistema (A y B, con A activa),
   - **When** invoca `PATCH /admin/strategies/{id}/activate` con el identificador de la estrategia B,
   - **Then** la estrategia activa pasa a ser B, se registra la versión y timestamp del cambio, y el siguiente `POST /quotes/recalculate` (manual o vía scheduler) calcula todas las cotizaciones del mercado utilizando la estrategia B.

5. **Control de Acceso y Rechazo a Usuario Regular (403 Forbidden)**:
   - **Given** un usuario regular autenticado (no superusuario),
   - **When** intenta invocar el endpoint administrativo `PATCH /admin/strategies/{id}/activate`,
   - **Then** el backend responde HTTP `403 Forbidden` con estructura `ProblemDetail` y código `ACCESS_DENIED`, sin aplicar ninguna modificación en la estrategia activa ni en las cotizaciones.

---

### User Story 2 - Mercado de Tokens y Recarga Simulada de Créditos (Priority: P1)

Como usuario autenticado, quiero ejecutar órdenes de compra (`POST /orders/buy`) y venta (`POST /orders/sell`) de tokens de jugadores a su cotización vigente validando disponibilidad (máximo estricto de 100 tokens emitidos por jugador, en poder inicial del superusuario) y saldo, y disponer de un endpoint de recarga de créditos simulada (`POST /users/{id}/credits/topup`) para incrementar mi saldo sin pasarelas de pago reales.

**Why this priority**: Implementa las reglas de negocio transaccionales del dominio financiero y habilita la liquidez de los usuarios para interactuar con la plataforma.

**Independent Test**: Invocar la recarga simulada para acreditar fondos a un usuario con saldo cero, verificar el registro auditable de recarga; luego ejecutar `POST /orders/buy` verificando el descuento de créditos, transferencia de tokens del superusuario y creación de la transacción, seguido por `POST /orders/sell` verificando el reintegro de tokens y acreditación de saldo.

**Acceptance Scenarios**:

1. **Recarga Simulada de Créditos Exitosa**:
   - **Given** un usuario autenticado con saldo 0 créditos,
   - **When** invoca `POST /users/{id}/credits/topup` enviando `{"amount": 100.0}`,
   - **Then** el backend valida que el monto sea numérico y estrictamente positivo, suma 100.0 créditos al saldo del usuario, genera un registro auditable e inmutable en `Transaction` de tipo `RECARGA` con `balanceBefore=0.0`, `balanceAfter=100.0` y campos de jugador/tokens en nulo, respondiendo HTTP `200 OK` con el saldo actualizado.

2. **Rechazo de Recarga con Monto Inválido**:
   - **Given** una solicitud a `POST /users/{id}/credits/topup` con monto menor o igual a cero (`amount <= 0`) o valor no numérico,
   - **When** se envía la petición,
   - **Then** el backend aborta la operación con HTTP `400 Bad Request`, código `INVALID_AMOUNT` y mensaje en español informando que el monto a recargar debe ser un valor positivo válido.

3. **Compra Transaccional Exitosa**:
   - **Given** un usuario autenticado con 50.0 créditos de saldo y un jugador con cotización vigente de 5.0 créditos y 10 tokens disponibles en poder del superusuario,
   - **When** envía `POST /orders/buy` solicitando 4 tokens,
   - **Then** el backend ejecuta en una única transacción atómica: el débito de 20.0 créditos al usuario, la transferencia de 4 tokens del superusuario al comprador, la actualización del precio promedio ponderado de compra en su posición, y el registro inmutable de la transacción `COMPRA`, devolviendo HTTP `200 OK`.

4. **Rechazo de Compra por Saldo Insuficiente**:
   - **Given** un usuario con saldo de 10.0 créditos que solicita comprar tokens por un costo total de 30.0 créditos,
   - **When** envía `POST /orders/buy`,
   - **Then** el backend cancela la operación y devuelve HTTP `400 Bad Request` con estructura `ProblemDetail`, código `INSUFFICIENT_FUNDS` y mensaje en español "Saldo insuficiente para realizar la compra."

5. **Rechazo de Compra por Límite de Emisión (100 Tokens)**:
   - **Given** un jugador del cual solo quedan 2 tokens disponibles en poder del superusuario,
   - **When** un usuario solicita comprar 5 tokens,
   - **Then** el backend rechaza la solicitud con HTTP `400 Bad Request`, código `INSUFFICIENT_TOKENS_AVAILABLE` y mensaje en español detallando la disponibilidad actual.

6. **Venta Transaccional Exitosa**:
   - **Given** un usuario que posee 5 tokens de un jugador cuya cotización vigente es de 8.0 créditos,
   - **When** envía `POST /orders/sell` por 3 tokens,
   - **Then** el backend descuenta 3 tokens de la posición del usuario, los reintegra al balance del superusuario, acredita 24.0 créditos al saldo del usuario y genera el registro inmutable de operación `VENTA` con HTTP `200 OK`.

7. **Rechazo de Venta por Tenencia Insuficiente**:
   - **Given** un usuario que posee 2 tokens de un jugador e intenta vender 4 tokens,
   - **When** envía `POST /orders/sell`,
   - **Then** el backend retorna HTTP `400 Bad Request` con código `INSUFFICIENT_TOKEN_BALANCE` y mensaje descriptivo en español.

---

### User Story 3 - Consulta de Cotización Actual, Histórica Paginada y por Fecha Dada (Priority: P1)

Como cliente del API, quiero consultar el historial de cotizaciones de un jugador con soporte de paginación (`GET /players/{id}/quotes?page=0&size=20`) y obtener la cotización vigente en una fecha específica (`GET /players/{id}/quotes?date=YYYY-MM-DD`), para proveer trazabilidad cronológica eficiente y alimentar las series temporales de los gráficos.

**Why this priority**: Requerimiento obligatorio del dominio y fuente de datos para los gráficos de evolución de precios del frontend.

**Independent Test**: Persistir más de 25 cotizaciones en distintas fechas para un jugador; invocar `GET /players/{id}/quotes` verificando la paginación con `page=0&size=20`, la presencia de metadata (`totalElements`, `totalPages`) y el ordenamiento descendente; luego realizar consultas con `?date=YYYY-MM-DD` comprobando que devuelva únicamente la cotización vigente a esa fecha sin paginación.

**Acceptance Scenarios**:

1. **Consulta de Cotización a una Fecha Específica (Sin Paginación)**:
   - **Given** un jugador con cotizaciones registradas el `2026-10-01` (2.50 créditos) y el `2026-10-08` (3.20 créditos),
   - **When** se solicita `GET /players/{id}/quotes?date=2026-10-05`,
   - **Then** el backend retorna HTTP `200 OK` con la cotización vigente en esa fecha (`2.50` créditos) y los metadatos de la estrategia aplicada, sin aplicar estructura de paginación.

2. **Consulta a Fecha Previa al Primer Registro**:
   - **Given** una consulta para una fecha anterior a la primera cotización histórica registrada del jugador,
   - **When** se invoca `GET /players/{id}/quotes?date=2026-01-01`,
   - **Then** el backend retorna el valor nominal base de `1.0` crédito con HTTP `200 OK`.

3. **Listado de Serie Histórica Paginada**:
   - **Given** un jugador con 35 cotizaciones registradas a lo largo del tiempo,
   - **When** se invoca `GET /players/{id}/quotes` sin parámetro de fecha (por defecto `page=0`, `size=20`),
   - **Then** el backend retorna una respuesta paginada con 20 elementos ordenados por fecha de forma descendente, acompañada de la metadata de paginación: `totalElements: 35`, `totalPages: 2`, `currentPage: 0` y `pageSize: 20`.

---

### User Story 4 - Feature Adicional (Backend): Motor de Alertas de Precios y Notificaciones Push vía Server-Sent Events (SSE) (Priority: P1)

Como usuario inversor, quiero gestionar mis alertas de precio (`POST /alerts`, `GET /alerts`, `DELETE /alerts/{id}`), para que el backend evalúe automáticamente cada cambio de cotización y me envíe una notificación instantánea en tiempo real mediante Server-Sent Events (`GET /alerts/stream` vía `SseEmitter`), enterándome al instante cuando un jugador alcanza o supera mi precio objetivo.

**Why this priority**: Constituye el componente backend del **Feature Adicional obligatorio** de la Entrega 2, aportando arquitectura dirigida por eventos y entrega push en vivo hacia el navegador.

#### Justificación Técnica de la Elección de SSE (frente a WebSockets):
1. **Unidireccionalidad precisa servidor→cliente**: El requerimiento de notificaciones en tiempo real es estrictamente push del servidor hacia el cliente una vez disparada la alerta. No se requiere streaming bidireccional continuo desde el cliente.
2. **Integración nativa con Spring Security y HTTP**: Al tratarse de una conexión HTTP estándar bajo el protocolo Server-Sent Events (`text/event-stream`), `SseEmitter` se integra de forma transparente con la autenticación JWT mediante encabezados `Authorization: Bearer <token>`, evitando la complejidad de handshakes STOMP, sub-protocolos y sockets TCP dedicados.
3. **Simplicidad operativa y resiliencia**: SSE incluye soporte nativo de reconexión automática en el estándar HTML5 `EventSource` del navegador y consume significativamente menor sobrecarga de infraestructura, ideal para el alcance del TP.

**Independent Test**: Crear una alerta activa con `POST /alerts`, suscribir un cliente HTTP/SSE al endpoint `GET /alerts/stream`, ejecutar `POST /quotes/recalculate` haciendo que el jugador supere el precio objetivo, y verificar la recepción inmediata del payload JSON del evento por el stream SSE (latencia < 2 segundos) y el cambio de estado a `DISPARADA` en la base de datos.

**Acceptance Scenarios**:

1. **Creación y Listado de Alertas de Precio**:
   - **Given** un usuario autenticado y un jugador existente,
   - **When** envía `POST /alerts` indicando `playerId`, `condition` (`ABOVE` / `BELOW`) y `targetPrice`,
   - **Then** el backend valida que el precio objetivo sea positivo, persiste la alerta en estado `ACTIVA` y responde HTTP `201 Created` con el DTO de la alerta.

2. **Evaluación Reactiva y Disparo Push vía SSE**:
   - **Given** una alerta `ACTIVA` con condición `ABOVE` `12.0` créditos y una nueva cotización del jugador calculada en `12.8` créditos tras un recálculo,
   - **When** finaliza el cálculo de cotizaciones,
   - **Then** el motor de alertas actualiza la alerta a `DISPARADA` con marca de tiempo `triggeredAt`, y emite inmediatamente el evento JSON de notificación a través del canal SSE activo (`GET /alerts/stream`) del usuario destinatario.

3. **Cancelación de Alerta por el Usuario**:
   - **Given** una alerta `ACTIVA` propiedad del usuario autenticado,
   - **When** envía `DELETE /alerts/{id}`,
   - **Then** el backend elimina o desactiva la alerta y responde HTTP `204 No Content`.

---

### User Story 7 - Separación de Profiles (`unit` y `e2e`), Simplificación de Sync y Rutas sin `/v1` (Priority: P1)

Como equipo de desarrollo backend, quiero contar con perfiles de configuración separados `unit` y `e2e` sobre PostgreSQL (utilizando Testcontainers), unificar la persistencia de datos de Football-Data y WhoScored en la entidad de jugador con fallback local ante caídas del scraper, y exponer todos los controladores REST sin el prefijo `/v1`.

**Why this priority**: Es el **único punto CORE de infraestructura pendiente exigido formalmente por la cátedra para la Entrega 2**. Al poseer un esfuerzo acotado y bien delimitado respecto al resto de la entrega, debe garantizarse al 100% como prioridad P1 independientemente del grado de avance o completitud que se alcance en el feature adicional (motor de alertas reactivo), el cual al ser más complejo podría admitir una presentación parcial si los tiempos apremian.

**Independent Test**: Ejecutar `./mvnw test -Punit` y `./mvnw test -Pe2e`, validando que cada perfil levante su contexto específico sin conflictos y sin referencia a HSQLDB/H2; verificar que todos los endpoints respondan sin `/v1` y simular la desconexión del scraper validando la persistencia y lectura de datos locales.

**Acceptance Scenarios**:

1. **Separación de Profiles `unit` y `e2e` sobre PostgreSQL**:
   - **Given** los perfiles definidos en el `pom.xml` y properties de Spring Boot,
   - **When** se ejecutan las pruebas unitarias (`unit`) o las pruebas de integración (`e2e`),
   - **Then** cada suite corre con su respectiva configuración aislada, utilizando PostgreSQL mediante Testcontainers en integración y mocks en pruebas unitarias, sin dependencias de HSQLDB/H2.

2. **Unificación de Datos y Fallback Local**:
   - **Given** el proceso de sincronización de estadísticas de un jugador,
   - **When** el scraper de WhoScored responde exitosamente o presenta fallos de timeout/bloqueo (`SCRAPE_TIMEOUT`),
   - **Then** el adapter consolida los datos en la entidad `Player` en PostgreSQL o recurre silenciosamente al último estado local persistido sin propagar errores 500 al cliente.

3. **Exposición de Rutas sin Prefijo `/v1`**:
   - **Given** cualquier endpoint de la API (`/players`, `/quotes/recalculate`, `/orders/buy`, `/alerts`, etc.),
   - **When** se invoca sin el prefijo `/v1`,
   - **Then** responde correctamente bajo la ruta normalizada.

---

### User Story 5 - Endpoints de Portfolio e Historial Auditable de Transacciones (Priority: P2)

Como cliente autenticado, quiero consultar el portfolio consolidado de un usuario (`GET /users/{id}/portfolio`) y su historial de transacciones auditables (`GET /users/{id}/transactions`), recibiendo las métricas financieras calculadas (tokens por jugador, precio promedio de compra, valor actual, ganancia/pérdida nominal y porcentual, saldo disponible y serie de operaciones) para alimentar tanto las tablas como los gráficos del usuario.

**Why this priority**: Provee los contratos de datos financieros indispensables para alimentar las vistas y gráficos de distribución y patrimonio en el frontend.

**Independent Test**: Ejecutar recargas, compras y ventas para un usuario; consultar `GET /users/{id}/portfolio` y `GET /users/{id}/transactions`, comprobando el balance, PnL y la inmutabilidad de cada registro histórico de transacción.

**Acceptance Scenarios**:

1. **Consulta de Portfolio Consolidado**:
   - **Given** un usuario con 10 tokens de un jugador adquiridos a precio promedio de `2.0` créditos y cotización actual de `3.5` créditos,
   - **When** solicita `GET /users/{id}/portfolio`,
   - **Then** el backend devuelve HTTP `200 OK` con el saldo actual de créditos, valor total de cartera, PnL global y el desglose de posiciones: tokens (`10`), precio promedio (`2.0`), cotización vigente (`3.5`), valor actual (`35.0`), PnL nominal (`+15.0`) y PnL porcentual (`+75.0%`).

2. **Consulta de Historial Auditable de Operaciones**:
   - **Given** un usuario con operaciones previas de recarga, compra y venta,
   - **When** solicita `GET /users/{id}/transactions`,
   - **Then** el backend retorna la colección cronológica inmutable con todos los campos de auditoría (`id`, `timestamp`, `actorUserId`, `operationType`, `playerId`, `quantity`, `unitPrice`, `totalAmount`, `balanceBefore`, `balanceAfter`, `tokensBefore`, `tokensAfter`, `strategyVersion`).

---

### User Story 6 - Ranking de Jugadores por Estrategia Activa, Caché Redis y Scheduler Semanal (Priority: P2)

Como cliente del sistema, quiero consultar el ranking de jugadores (`GET /players/ranking`) optimizado con caché Redis y ordenado por la cotización vigente bajo la estrategia activa, y disponer de un proceso programado semanal (`@Scheduled`) que ejecute el recálculo automático y refresque las cotizaciones y el ranking.

**Why this priority**: Automatiza el mantenimiento continuo de la valoración del mercado y asegura alto desempeño en consultas masivas mediante almacenamiento en memoria con Redis.

**Independent Test**: Invocar `GET /players/ranking` verificando que la respuesta se almacene en Redis con clave dedicada; invocar el recálculo y comprobar la invalidación de la clave de caché y el recálculo automático al cumplirse el cron programado.

**Acceptance Scenarios**:

1. **Ranking según Estrategia Activa y Aceleración con Redis**:
   - **Given** jugadores valorados bajo la estrategia activa actual,
   - **When** se solicita `GET /players/ranking`,
   - **Then** el backend retorna la lista ordenada de mayor a menor cotización vigente junto con los metadatos de la estrategia activa, sirviendo la respuesta desde Redis tras la primera invocación.

2. **Ejecución del Scheduler Semanal e Invalidación de Caché**:
   - **Given** el componente `@Scheduled` configurado con expresión cron semanal parametrizable,
   - **When** se cumple el intervalo programado,
   - **Then** ejecuta el recálculo masivo de cotizaciones para todos los jugadores, invalida el caché de ranking en Redis y dispara la evaluación de alertas de precio.

---

### Edge Cases

- **Concurrencia en compra simultánea de tokens:** El backend debe emplear bloqueo transaccional pesimista u optimista sobre la posición del jugador para asegurar que dos transacciones simultáneas no excedan el tope estricto de 100 tokens emitidos.
- **División por cero en estrategias o PnL:** Si una estadística deportiva o el costo base de compra es cero, las fórmulas de valoración y cálculo de rentabilidad deben manejar el caso con valores de respaldo predeterminados (cotización mínima 1.0 crédito, PnL porcentual 0.0%).
- **Cliente desconectado al dispararse una alerta SSE:** La alerta queda registrada en PostgreSQL con estado `DISPARADA` y fecha `triggeredAt` para ser consultada vía `GET /alerts` cuando el usuario inicie sesión.
- **Indisponibilidad de proveedores externos:** Si WhoScored o Football-Data no responden durante una sincronización o recálculo, el backend utiliza los datos almacenados en caché de Redis o los últimos datos persistidos en PostgreSQL sin abortar el procesamiento global.
- **Intentos de recarga negativa o fraudulenta:** La recarga de créditos rechaza montos menores o iguales a cero y valores no numéricos con HTTP 400 antes de tocar la base de datos.
- **Invocación no autorizada de endpoints administrativos:** Cualquier invocación a `PATCH /admin/strategies/{id}/activate` sin token de superusuario retorna inmediatamente `403 Forbidden`.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El backend DEBE implementar al menos dos estrategias de valoración configurables con pesos ponderados por métrica deportiva (minutos, goles, asistencias, tiros, pases, intercepciones, tarjetas, rating).
- **FR-002**: El backend DEBE persistir cada cotización calculada en la entidad `PlayerQuote`, registrando timestamp, valor en créditos, identificador y versión de la estrategia utilizada.
- **FR-003**: El backend DEBE exponer `POST /quotes/recalculate` para recalcular las cotizaciones de todos los jugadores del catálogo en base a la estrategia activa.
- **FR-004**: El backend DEBE garantizar una emisión fija e inmutable de 100 tokens por jugador con valor nominal inicial de 1.0 crédito, asignados inicialmente en su totalidad a la cartera del superusuario.
- **FR-005**: El backend DEBE procesar órdenes de compra (`POST /orders/buy`) y venta (`POST /orders/sell`) de manera estrictamente transaccional, validando autenticación, disponibilidad de tokens, tenencia del vendedor y suficiencia de saldo de créditos del comprador.
- **FR-006**: El backend DEBE exponer `GET /players/{id}/quotes` soportando parámetros de paginación (`page` con default 0, `size` con default 20) ordenados por fecha descendente e incluyendo metadata de paginación (`totalElements`, `totalPages`), y soportar el parámetro opcional `date` (`YYYY-MM-DD`) para obtener únicamente la cotización vigente a dicha fecha (caso en el cual la paginación no aplica).
- **FR-007**: El backend DEBE exponer `GET /users/{id}/portfolio` y `GET /users/{id}/transactions` calculando métricas de cartera (tokens, precio promedio, valor actual, PnL nominal y porcentual) y proveyendo el historial auditable completo para alimentar tablas y gráficos.
- **FR-008**: El backend DEBE exponer `GET /players/ranking` ordenado descendentemente por cotización vigente bajo la estrategia activa.
- **FR-009**: El backend DEBE ejecutar un job semanal parametrizable mediante `Spring Scheduler` (`@Scheduled`) que ejecute el recálculo masivo y dispare la evaluación reactiva de alertas.
- **FR-010**: El backend DEBE implementar el motor de alertas de precios (`POST /alerts`, `GET /alerts`, `DELETE /alerts/{id}`) y un canal push en tiempo real exclusivo mediante Server-Sent Events (`GET /alerts/stream` utilizando `SseEmitter` de Spring), autenticado mediante JWT y despachando eventos instantáneos al dispararse un umbral de cotización.
- **FR-011**: El backend DEBE separar los perfiles de testing en `unit` y `e2e` configurados sobre PostgreSQL (con Testcontainers para pruebas de integración), descartando definitivamente el uso de HSQLDB/H2.
- **FR-012**: El backend DEBE unificar la información de Football-Data y WhoScored en la entidad `Player` con tolerancia a fallos y fallback transparente a datos locales o caché ante caídas del proveedor.
- **FR-013**: Todos los endpoints REST DEBEN exponerse sin el prefijo `/v1` y retornar mensajes de error en **español** bajo el estándar `ProblemDetail` (RFC 7807) con códigos de error funcionales estables.
- **FR-014**: El backend DEBE utilizar **Redis** como capa de caché dedicada para acelerar las respuestas de `GET /players` y `GET /players/ranking`, así como para almacenar temporalmente los datos obtenidos de los adaptadores externos (WhoScored / Football-Data.org). El caché de cotizaciones y ranking DEBE invalidarse y refrescarse automáticamente tras cada recálculo (manual, programado o derivado de una activación de estrategia). Si los proveedores externos no responden, el sistema DEBE servir los datos a partir del caché de Redis o de los datos locales persistidos en PostgreSQL.
- **FR-015**: El backend DEBE exponer un endpoint de recarga de créditos simulada (`POST /users/{id}/credits/topup`) que reciba un monto numérico positivo (`amount`) e incremente directamente el saldo del usuario autenticado sin interacción con pasarelas de pago reales. Esta operación DEBE generar un registro auditable e inmutable en `Transaction` de tipo `RECARGA`.
- **FR-016**: El backend DEBE exponer el endpoint administrativo `PATCH /admin/strategies/{id}/activate` restringido de forma exclusiva al **superusuario** mediante validación de rol en su token JWT (respondiendo HTTP `403 Forbidden` a cualquier usuario regular). La activación de una nueva estrategia o modificación de sus pesos impacta de forma global a todos los jugadores del sistema a partir del siguiente recálculo de cotizaciones.

---

### Key Entities

- **Player**: Representa al jugador de fútbol, integrando información biográfica de Football-Data.org y métricas deportivas consolidadas de WhoScored.
- **ValuationStrategy**: Configuración de la fórmula de cálculo con ponderación de métricas deportivas, versión, identificador y flag de estado activo (`ACTIVA` / `INACTIVA`).
- **PlayerQuote**: Registro histórico de cotización de un jugador, asociado a una fecha/timestamp, un valor monetario en créditos y la versión de la estrategia aplicada.
- **PortfolioPosition**: Registro de la tenencia de tokens de un usuario sobre un jugador específico, manteniendo el saldo de tokens y el precio promedio ponderado de adquisición.
- **Transaction / Order**: Registro auditable e inmutable de operaciones sobre la plataforma. Campos mínimos obligatorios:
  - `id`: Identificador único de la transacción (UUID o numérico).
  - `timestamp`: Marca temporal UTC de la ejecución de la operación.
  - `actorUserId`: Identificador del usuario que ejecutó la operación (usuario regular o superusuario).
  - `operationType`: Tipo de operación (`COMPRA`, `VENTA`, `RECARGA`, `CAMBIO_ESTRATEGIA`).
  - `playerId`: Identificador del jugador involucrado (nulo en `RECARGA` y `CAMBIO_ESTRATEGIA`).
  - `quantity`: Cantidad de tokens negociados (nulo salvo en `COMPRA` y `VENTA`).
  - `unitPrice`: Cotización vigente aplicada al momento de la operación (nulo salvo en `COMPRA` y `VENTA`).
  - `totalAmount`: Monto total en créditos involucrado (nulo en `CAMBIO_ESTRATEGIA`).
  - `balanceBefore` / `balanceAfter`: Saldo de créditos del usuario antes y después de la operación (nulos en `CAMBIO_ESTRATEGIA`).
  - `tokensBefore` / `tokensAfter`: Tenencia de tokens del jugador antes y después de la operación (nulos salvo en `COMPRA` y `VENTA`).
  - `strategyVersion`: Versión de la estrategia de valoración vigente en el momento de registrar la operación.
  - *Regla de inmutabilidad*: No se permite ninguna operación de modificación (`UPDATE`) ni eliminación (`DELETE`) a nivel de entidad, servicio o repositorio una vez persistida la transacción.
- **PriceAlert**: Alerta de umbral de cotización creada por un usuario sobre un jugador, con condición (`ABOVE` / `BELOW`), precio objetivo (`targetPrice`), estado (`ACTIVA` / `DISPARADA`) y timestamp de disparo (`triggeredAt`).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las operaciones de compra y venta respetan la invariante estricta de 100 tokens totales por jugador en circulación entre el superusuario y los usuarios.
- **SC-002**: Las notificaciones push de alertas disparadas tras un recálculo se entregan al cliente conectado mediante el stream SSE (`GET /alerts/stream`) con una latencia inferior a 2 segundos desde la persistencia de las nuevas cotizaciones.
- **SC-003**: Las suites de pruebas separadas `unit` y `e2e` sobre PostgreSQL se ejecutan de manera aislada con 100% de tests aprobados y calidad de código acorde a SonarCloud (< 10 issues totales).
- **SC-004**: El 100% de los endpoints del backend responden sin el prefijo `/v1` y mantienen operatividad con datos locales persistidos o en caché Redis ante caídas de proveedores externos.
- **SC-005**: Las consultas a `GET /players/ranking` con caché Redis activo responden con una latencia inferior a 50 milisegundos para cargas de catálogo estándar, e invalidan su contenido de forma inmediata tras el recálculo de cotizaciones.
- **SC-006**: Los intentos de acceso a `PATCH /admin/strategies/{id}/activate` por parte de usuarios sin credenciales de superusuario son rechazados en el 100% de los casos con HTTP `403 Forbidden`.

---

## Assumptions

- **Separación Atómica Front/Back:** Esta especificación cubre exclusivamente el comportamiento, contratos y persistencia del servidor backend. Las pantallas, gráficos de React y consumo del stream SSE se especifican en `005-entrega2-frontend`.
- **Saldo Inicial y Recarga Simulada:** El saldo inicial de un usuario nuevo es de 0 créditos; el usuario incrementa su saldo exclusivamente a través del endpoint de recarga simulada (`POST /users/{id}/credits/topup`), sin pasarelas de pago reales ni cobro de dinero fiat.
- **Control Centralizado de Mercado:** El superusuario administra centralizadamente la activación y ponderación de la estrategia de valoración. Los usuarios regulares no configuran estrategias personales.
- **Mecanismo de Streaming Push:** Se utiliza exclusivamente Server-Sent Events (SSE) a través de `SseEmitter` de Spring Boot por adecuarse a la naturaleza unidireccional de las alertas y permitir autenticación JWT directa vía HTTP.
- **Persistencia PostgreSQL y Testcontainers:** No se utiliza HSQLDB/H2 en ningún entorno; las pruebas de integración utilizan contenedores PostgreSQL descartables mediante Testcontainers.
