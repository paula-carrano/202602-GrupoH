# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 2: Backend Core & Real-Time Engine

**Feature Branch**: `004-entrega2-core`  
**Created**: 2026-10-12  
**Status**: Draft  
**Scope**: **Exclusivamente Backend** (API REST, Lógica de Dominio, Persistencia PostgreSQL, Scheduler, Integración Externa y Motor de Eventos en Tiempo Real). El alcance de interfaz de usuario se encuentra separado atómicamente en [`specs/005-entrega2-frontend/spec.md`](../005-entrega2-frontend/spec.md).  
**Google Docs Oficial del Proyecto**: [Documento de Cátedra y SDD](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk) (ID: `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`)  
**Consigna Oficial (Solo Lectura)**: [Documento de Visión](https://docs.google.com/document/d/12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8/edit?usp=sharing) (ID: `12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8`)  
**Input**: User description: "Especificación atómica de BACKEND para la Entrega 2: Cálculo de cotización de jugadores según estrategia configurable, compra/venta transaccional de tokens, cotización a una fecha dada e histórico, portfolio y transacciones para alimentar gráficos, ranking según estrategia activa, scheduler semanal, motor backend del Feature Adicional (Alertas de Precios con streaming de notificaciones en tiempo real vía WebSockets/SSE), separación de profiles unit y e2e, y simplificación de sync sin /v1."

---

## Directivas de Cátedra — Alcance Depurado de Entrega 2 (Backend)

Por indicación expresa del cuerpo docente, se depuran los puntos de infraestructura que ya fueron cubiertos en la Entrega 1:
- **Descartados (ya resueltos o sin efecto):**
  1. ~~Utilizar HSQLDB / H2 para persistir datos~~ *(El proyecto ya opera directamente sobre PostgreSQL).*
  2. ~~Crear datos de prueba cuando levanta la aplicación~~ *(Estaba vinculado al uso de H2).*
  3. ~~Documentación de Endpoints con Swagger~~ *(Cubierto y entregado en Entrega 1).*
  4. ~~Implementar Job de Coverage~~ *(Integrado en SonarCloud durante Entrega 1).*
- **Único punto Core técnico requerido en Backend para Entrega 2:**
  1. **Separar profiles de testing para pruebas unitarias (`unit`) y end-to-end (`e2e`).**

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cálculo y Recálculo de Cotizaciones de Jugadores por Estrategia Activa (Priority: P1)

Como administrador o proceso automatizado del backend, quiero calcular y actualizar las cotizaciones de los jugadores en base a una estrategia de valoración configurable (con pesos ponderados por métrica deportiva), registrando el historial y la versión de la estrategia utilizada en la base de datos.

**Why this priority**: Es el núcleo del modelo de dominio. Sin cotizaciones calculadas por estrategia no existe precio de referencia para operar en el mercado de tokens ni construir el ranking.

**Independent Test**: Configurar dos estrategias de valoración en la base de datos, invocar `POST /quotes/recalculate`, y verificar que cada jugador posea un nuevo registro en `PlayerQuote` con el valor calculado, fecha, estrategia activa y versión.

**Acceptance Scenarios**:

1. **Recálculo Masivo Exitoso**:
   - **Given** un catálogo de jugadores con métricas deportivas registradas y una estrategia de valoración activa configurada con pesos para goles, asistencias, minutos, tiros, pases, intercepciones, tarjetas y rating,
   - **When** se invoca `POST /quotes/recalculate`,
   - **Then** el backend calcula la nueva cotización para cada jugador, persiste el nuevo registro en `PlayerQuote` con timestamp, valor, nombre y versión de la estrategia, y responde con HTTP `200 OK` informando la cantidad de jugadores actualizados.

2. **Soporte de Múltiples Estrategias Configurables**:
   - **Given** al menos dos estrategias registradas en el sistema (e.g. "Rendimiento Ofensivo" y "Rendimiento Global"),
   - **When** se cambia la estrategia activa y se ejecuta el recálculo,
   - **Then** las nuevas cotizaciones se calculan con las ponderaciones de la nueva estrategia, conservando inalteradas las cotizaciones históricas previas con su versión original.

3. **Recálculo ante Métricas en Cero o Jugadores sin Minutos**:
   - **Given** un jugador sin minutos jugados o con estadísticas en cero,
   - **When** se ejecuta el recálculo,
   - **Then** el sistema asigna el valor base mínimo garantizado (1.0 crédito) sin errores aritméticos.

---

### User Story 2 - Mercado de Tokens: Órdenes Transaccionales de Compra y Venta (Priority: P1)

Como cliente autenticado del API, quiero ejecutar órdenes de compra (`POST /orders/buy`) y venta (`POST /orders/sell`) de tokens de jugadores a su cotización vigente, validando disponibilidad de tokens (máximo estricto de 100 tokens emitidos por jugador, inicialmente en poder del superusuario) y saldo de créditos del usuario.

**Why this priority**: Implementa las reglas de negocio financieras críticas y transaccionales del dominio.

**Independent Test**: Invocar `POST /orders/buy` con un usuario con saldo suficiente y verificar en la base de datos el descuento de créditos, la transferencia de tokens desde el superusuario y la creación del registro de transacción; luego invocar `POST /orders/sell` verificando el proceso inverso.

**Acceptance Scenarios**:

1. **Compra Transaccional Exitosa**:
   - **Given** un usuario autenticado con 50 créditos de saldo y un jugador con cotización vigente de 5.0 créditos y 10 tokens disponibles en poder del superusuario,
   - **When** envía `POST /orders/buy` solicitando 4 tokens,
   - **Then** el backend ejecuta en una única transacción atómica: el débito de 20 créditos al usuario, la transferencia de 4 tokens del superusuario al comprador, el recálculo del precio promedio ponderado de compra en su posición, y el registro inmutable de la transacción `COMPRA`, devolviendo HTTP `200 OK`.

2. **Rechazo de Compra por Saldo Insuficiente**:
   - **Given** un usuario con saldo de 10 créditos que solicita comprar tokens por un total de 30 créditos,
   - **When** envía `POST /orders/buy`,
   - **Then** el backend aborta la operación y devuelve HTTP `400 Bad Request` con estructura `ProblemDetail`, código `INSUFFICIENT_FUNDS` y mensaje en español "Saldo insuficiente para realizar la compra."

3. **Rechazo de Compra por Límite de Emisión (100 Tokens)**:
   - **Given** un jugador del cual solo quedan 2 tokens disponibles en poder del superusuario,
   - **When** un usuario solicita comprar 5 tokens,
   - **Then** el backend rechaza la solicitud con HTTP `400 Bad Request`, código `INSUFFICIENT_TOKENS_AVAILABLE` y mensaje en español detallando la disponibilidad actual.

4. **Venta Transaccional Exitosa**:
   - **Given** un usuario que posee 5 tokens de un jugador cuya cotización vigente es de 8.0 créditos,
   - **When** envía `POST /orders/sell` por 3 tokens,
   - **Then** el backend retira 3 tokens de la posición del usuario, los reintegra al superusuario, acredita 24.0 créditos al saldo del usuario y registra la operación `VENTA` con HTTP `200 OK`.

5. **Rechazo de Venta por Tenencia Insuficiente**:
   - **Given** un usuario que posee 2 tokens de un jugador e intenta vender 4 tokens,
   - **When** envía `POST /orders/sell`,
   - **Then** el backend retorna HTTP `400 Bad Request` con código `INSUFFICIENT_TOKEN_BALANCE` y mensaje en español.

---

### User Story 3 - Consulta de Cotización Actual, Histórica y por Fecha Dada (Priority: P1)

Como cliente del API, quiero consultar el historial de cotizaciones de un jugador (`GET /players/{id}/quotes`) y obtener la cotización vigente en una fecha específica (`GET /players/{id}/quotes?date=YYYY-MM-DD`), para proveer trazabilidad histórica y alimentar las series temporales.

**Why this priority**: Requerimiento obligatorio del dominio y fuente de datos para los gráficos de evolución de precios.

**Independent Test**: Persistir cotizaciones en distintas fechas para un jugador y realizar peticiones con y sin el query param `date`, verificando que devuelva la serie completa o el valor vigente en la fecha consultada.

**Acceptance Scenarios**:

1. **Consulta de Cotización a una Fecha Específica**:
   - **Given** un jugador con cotizaciones registradas el `2026-10-01` (2.50 créditos) y el `2026-10-08` (3.20 créditos),
   - **When** se solicita `GET /players/{id}/quotes?date=2026-10-05`,
   - **Then** el backend retorna HTTP `200 OK` con la cotización vigente en esa fecha (`2.50` créditos) y los metadatos de la estrategia aplicada.

2. **Consulta a Fecha Previa al Primer Registro**:
   - **Given** una consulta para una fecha anterior a la primera cotización histórica del jugador,
   - **When** se invoca `GET /players/{id}/quotes?date=2026-01-01`,
   - **Then** el backend retorna el valor nominal inicial de `1.0` crédito con HTTP `200 OK`.

3. **Listado de Serie Histórica Completa**:
   - **Given** un jugador con múltiples cotizaciones registradas,
   - **When** se invoca `GET /players/{id}/quotes` sin parámetro de fecha,
   - **Then** el backend retorna la colección ordenada cronológicamente con fecha, valor, estrategia y versión.

---

### User Story 4 - Feature Adicional (Backend): Motor de Alertas de Precios y Notificaciones Push en Tiempo Real (Priority: P1)

Como usuario inversor, quiero que el backend gestione mis alertas de precio (`POST /alerts`, `GET /alerts`, `DELETE /alerts/{id}`), evalúe automáticamente cada cambio de cotización y me envíe una notificación instantánea en tiempo real mediante Server-Sent Events (SSE) o WebSockets (`/alerts/stream`), para enterarme en el acto cuando un jugador cruza mi precio objetivo.

**Why this priority**: Constituye el componente backend del **Feature Adicional obligatorio** de la Entrega 2, aportando arquitectura dirigida por eventos y comunicación push en tiempo real.

#### Justificación Técnica en Backend
- Desacopla el proceso de recálculo de cotizaciones del mecanismo de aviso al usuario mediante eventos de dominio (`QuoteUpdatedEvent`).
- Expone un endpoint de suscripción reactiva (`GET /alerts/stream` vía `SseEmitter` o endpoint WebSocket STOMP) autenticado para empujar eventos JSON en vivo al navegador sin necesidad de polling.
- Persiste el estado de las alertas (`ACTIVA`, `DISPARADA`) para que ningún evento se pierda si el usuario estaba desconectado en el instante del recálculo.

**Independent Test**: Crear una alerta con `POST /alerts`, abrir una conexión al endpoint de streaming `/alerts/stream`, disparar `POST /quotes/recalculate` superando el umbral configurado, y comprobar que el stream recibe el payload JSON de la alerta disparada en menos de 2 segundos y que el estado en base de datos pasa a `DISPARADA`.

**Acceptance Scenarios**:

1. **Creación y Listado de Alertas de Precio**:
   - **Given** un usuario autenticado y un jugador existente,
   - **When** envía `POST /alerts` indicando `playerId`, `condition` (`ABOVE` / `BELOW`) y `targetPrice`,
   - **Then** el backend valida los datos, persiste la alerta en estado `ACTIVA` y responde HTTP `201 Created` con el DTO de la alerta creada.

2. **Evaluación Reactiva y Disparo en Tiempo Real**:
   - **Given** una alerta `ACTIVA` con condición `ABOVE` `12.0` créditos y una nueva cotización del jugador calculada en `12.8` créditos,
   - **When** finaliza el recálculo de cotizaciones,
   - **Then** el motor de alertas actualiza la alerta a `DISPARADA` con `triggeredAt`, y emite inmediatamente el evento de notificación a través del canal SSE/WebSocket activo del usuario.

---

### User Story 5 - Endpoints de Portfolio e Historial de Transacciones (Priority: P2)

Como cliente autenticado, quiero consultar el portfolio consolidado de un usuario (`GET /users/{id}/portfolio`) y su historial de transacciones (`GET /users/{id}/transactions`), recibiendo las métricas financieras calculadas (tokens por jugador, precio promedio de compra, valor actual, ganancia/pérdida nominal y porcentual, saldo disponible y serie de operaciones) para alimentar tanto las tablas como los gráficos del usuario.

**Why this priority**: Provee los contratos de datos financieros exigidos por la consigna y requeridos por los gráficos de usuario del frontend.

**Independent Test**: Ejecutar compras y ventas para un usuario y consultar `GET /users/{id}/portfolio` y `GET /users/{id}/transactions`, verificando los cálculos de precio promedio ponderado, PnL y listado de operaciones.

**Acceptance Scenarios**:

1. **Consulta de Portfolio Consolidado**:
   - **Given** un usuario con 10 tokens de un jugador comprados a precio promedio de `2.0` y cotización actual de `3.5`,
   - **When** solicita `GET /users/{id}/portfolio`,
   - **Then** el backend devuelve HTTP `200 OK` con el saldo actual de créditos, valor total de la cartera, ganancia/pérdida global y el arreglo de posiciones detallando jugador, cantidad de tokens (`10`), precio promedio (`2.0`), cotización actual (`3.5`), valor actual (`35.0`), PnL nominal (`+15.0`) y PnL porcentual (`+75.0%`).

2. **Consulta de Historial de Operaciones**:
   - **Given** un usuario con transacciones previas,
   - **When** solicita `GET /users/{id}/transactions`,
   - **Then** el backend retorna la lista ordenada cronológicamente con identificador, fecha/hora, tipo (`COMPRA` / `VENTA`), jugador, cantidad de tokens, precio unitario, monto total y saldo resultante post-operación.

---

### User Story 6 - Ranking de Jugadores por Estrategia Activa y Scheduler Semanal (Priority: P2)

Como cliente u operador, quiero consultar el ranking de jugadores (`GET /players/ranking`) ordenado por la cotización vigente bajo la estrategia activa y disponer de un job automático semanal (`Spring Scheduler`) que ejecute el recálculo periódico.

**Why this priority**: Completa las funcionalidades obligatorias de cotización y procesamiento batch del backend.

**Independent Test**: Invocar `GET /players/ranking` verificando el ordenamiento descendente por cotización actual, e invocar el método del scheduler verificando la generación de nuevas cotizaciones.

**Acceptance Scenarios**:

1. **Ranking según Estrategia Activa**:
   - **Given** jugadores cotizados bajo la estrategia activa,
   - **When** se invoca `GET /players/ranking`,
   - **Then** el backend devuelve la lista ordenada de mayor a menor cotización vigente junto con los datos de la estrategia activa.

2. **Ejecución del Scheduler Semanal**:
   - **Given** el componente `@Scheduled` configurado con expresión cron parametrizable,
   - **When** se cumple el intervalo programado,
   - **Then** ejecuta el recálculo masivo de cotizaciones y dispara la evaluación de alertas de precio.

---

### User Story 7 - Separación de Profiles (`unit` y `e2e`), Simplificación de Sync y Rutas sin `/v1` (Priority: P2)

Como equipo de desarrollo backend, quiero contar con perfiles separados `unit` y `e2e`, unificar la persistencia de datos de Football-Data y WhoScored en la entidad de jugador con fallback local ante caídas del scraper, y exponer todos los controladores REST sin el prefijo `/v1`.

**Why this priority**: Cumple con el único punto Core técnico vigente de la Entrega 2 y sanea las observaciones pendientes de la Entrega 1.

**Independent Test**: Ejecutar los tests filtrando por perfil `unit` y por perfil `e2e`, verificar la respuesta de los endpoints en `/players`, `/quotes/recalculate`, `/orders/buy`, etc., y simular un error `SCRAPE_TIMEOUT` del microservicio scraper comprobando el fallback a datos locales en PostgreSQL.

**Acceptance Scenarios**:

1. **Separación de Profiles `unit` y `e2e`**:
   - **Given** los perfiles configurados en el proyecto backend,
   - **When** se ejecutan las pruebas unitarias (`unit`) o las pruebas de integración/extremo a extremo (`e2e`),
   - **Then** cada suite corre con su configuración correspondiente sin colisiones ni dependencia de HSQLDB/H2.

2. **Unificación de Datos y Fallback Local**:
   - **Given** la sincronización de un jugador entre Football-Data.org y el scraper de WhoScored,
   - **When** el scraper responde exitosamente o falla con `SCRAPE_TIMEOUT` / `SCRAPE_BLOCKED`,
   - **Then** el adapter actualiza las métricas en el registro único del jugador en PostgreSQL o recurre silenciosamente a los últimos datos locales persistidos sin propagar errores 500.

---

### Edge Cases

- **Concurrencia en compra simultánea de tokens:** El backend debe emplear bloqueo transaccional sobre el pool/tenencia del jugador para evitar que dos compras simultáneas vendan más de los 100 tokens emitidos.
- **División por cero en estrategias o PnL:** Si una métrica o costo base es cero, las fórmulas de valoración y rentabilidad deben manejar el caso de borde de forma segura.
- **Cliente desconectado al dispararse una alerta:** La alerta queda persistida en estado `DISPARADA` con su timestamp para ser consultada vía `GET /alerts` cuando el usuario vuelva a conectarse.
- **Indisponibilidad de proveedores externos durante el Scheduler:** Si falla la consulta externa para un jugador, se calcula su cotización con las métricas locales vigentes y el job continúa con el resto del catálogo.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El backend DEBE implementar al menos dos estrategias de valoración configurables con pesos por métrica deportiva.
- **FR-002**: El backend DEBE persistir cada cotización en `PlayerQuote` registrando fecha, valor, estrategia y versión.
- **FR-003**: El backend DEBE exponer `POST /quotes/recalculate` para recalcular las cotizaciones de todos los jugadores.
- **FR-004**: El backend DEBE garantizar una emisión fija de 100 tokens por jugador con valor inicial de 1.0 crédito asignados inicialmente al superusuario.
- **FR-005**: El backend DEBE procesar órdenes de compra (`POST /orders/buy`) y venta (`POST /orders/sell`) de manera transaccional, validando autenticación, saldo, disponibilidad y tenencia.
- **FR-006**: El backend DEBE exponer `GET /players/{id}/quotes` soportando un parámetro opcional `date` (`YYYY-MM-DD`) para consultar la cotización a una fecha dada o el historial completo.
- **FR-007**: El backend DEBE exponer `GET /users/{id}/portfolio` y `GET /users/{id}/transactions` con todos los datos necesarios para auditoría y renderizado de gráficos de usuario.
- **FR-008**: El backend DEBE exponer `GET /players/ranking` ordenado según la estrategia activa.
- **FR-009**: El backend DEBE ejecutar un job semanal mediante `Spring Scheduler` para actualizar cotizaciones automáticamente.
- **FR-010**: El backend DEBE implementar el motor del Feature Adicional de Alertas de Precios (`POST /alerts`, `GET /alerts`, `DELETE /alerts/{id}`) y un canal de notificación push en tiempo real (`GET /alerts/stream` vía SSE o WebSocket).
- **FR-011**: El backend DEBE separar los perfiles de testing en `unit` y `e2e` operando sobre PostgreSQL.
- **FR-012**: El backend DEBE unificar el modelo de persistencia de jugadores entre Football-Data y WhoScored con fallback a datos locales.
- **FR-013**: Todos los endpoints REST DEBEN exponerse sin el prefijo `/v1` y retornar errores en **español** con formato `ProblemDetail` (RFC 7807) y códigos de error estables.

### Key Entities

- **Player**: Jugador con datos base de Football-Data y métricas estadísticas de WhoScored unificadas.
- **ValuationStrategy**: Configuración de estrategia de valoración con pesos por métrica, versión y estado activo.
- **PlayerQuote**: Cotización histórica de un jugador vinculada a una fecha y versión de estrategia.
- **PortfolioPosition**: Tenencia de tokens de un usuario sobre un jugador con precio promedio ponderado de compra.
- **Transaction / Order**: Registro auditable e inmutable de compra o venta de tokens.
- **PriceAlert**: Alerta de umbral de precio configurada por un usuario con estado (`ACTIVA` / `DISPARADA`).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las órdenes de compra y venta mantienen la invariante de 100 tokens totales por jugador incluso bajo peticiones concurrentes.
- **SC-002**: El disparo de una alerta tras un recálculo emite el evento en tiempo real por el canal SSE/WebSocket en menos de 2 segundos.
- **SC-003**: Los perfiles de prueba `unit` y `e2e` se ejecutan de forma independiente con 100% de tests en verde y menos de 10 issues en SonarCloud.
- **SC-004**: El 100% de los endpoints responden sin el prefijo `/v1` y mantienen operatividad total con datos locales si los proveedores externos no están disponibles.

---

## Assumptions

- **Separación Atómica Front/Back:** Esta especificación cubre únicamente las responsabilidades del servidor backend. El consumo visual, gráficos y componentes React se especifican en `005-entrega2-frontend`.
- **Persistencia PostgreSQL:** No se utiliza HSQLDB/H2 por directiva docente.
