# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 2: Core Functionality

**Feature Branch**: `004-entrega2-core`  
**Created**: 2026-10-12  
**Status**: Draft  
**Google Docs Oficial del Proyecto**: [Documento de Cátedra y SDD](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk) (ID: `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`)  
**Input**: User description: "Crea una spec para realizar lo pedido para la entrega 2: Cálculo de cotización de jugadores según estrategia, compra/venta de tokens, cotización a una fecha dada, historial de operaciones, ranking según estrategia activa, feature adicional planificado, persistencia testing H2/HSQLDB, scheduler semanal, integración y sync unificado de WhoScored y Football-Data sin /v1, y componentes de frontend con gráficos."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cálculo y Recálculo de Cotizaciones de Jugadores por Estrategia Activa (Priority: P1)

Como administrador o sistema automatizado, quiero calcular y actualizar periódicamente las cotizaciones de los jugadores en base a una estrategia de valoración configurable (con pesos ponderados de métricas deportivas), registrando el historial y la versión de la estrategia utilizada, para que el mercado refleje fielmente el rendimiento deportivo de los futbolistas.

**Why this priority**: Es el núcleo del valor diferencial de la plataforma. Sin una cotización calculada por estrategia no existe precio de mercado para habilitar la compra, venta o ranking de tokens.

**Independent Test**: Puede probarse configurando dos estrategias de valoración distintas en la base de datos, invocando el endpoint de recálculo `POST /api/quotes/recalculate`, y verificando que cada jugador posea una nueva cotización generada, asociada a la versión de la estrategia activa y persistida en su histórico.

**Acceptance Scenarios**:

1. **Recálculo Masivo Exitoso**:
   - **Given** un catálogo de jugadores con métricas deportivas registradas y una estrategia de valoración activa configurada con pesos para goles, asistencias, minutos y rating,
   - **When** se solicita el recálculo mediante `POST /api/quotes/recalculate`,
   - **Then** el sistema calcula la nueva cotización para cada jugador, persiste un nuevo registro en `PlayerQuote` con fecha actual, valor calculado, identificador y versión de la estrategia, y responde con código HTTP `200 OK` informando la cantidad de jugadores actualizados.

2. **Soporte de Múltiples Estrategias Configurables**:
   - **Given** dos estrategias registradas en el sistema (e.g. "Rendimiento Ofensivo" y "Rendimiento Global"),
   - **When** el administrador activa la estrategia alternativa y ejecuta el recálculo,
   - **Then** las nuevas cotizaciones reflejan los nuevos pesos de la estrategia activa, manteniendo las cotizaciones previas intactas con su estrategia original para trazabilidad histórica.

3. **Recálculo ante Métricas Parciales o Incompletas**:
   - **Given** un jugador que no posee minutos jugados o con métricas en cero,
   - **When** se ejecuta el recálculo,
   - **Then** el sistema aplica el valor base mínimo definido (1 crédito) sin producir divisiones por cero ni errores de cálculo, registrando la cotización correctamente.

---

### User Story 2 - Mercado de Tokens: Compra y Venta Transaccional (Priority: P1)

Como usuario inversor autenticado, quiero comprar y vender tokens de jugadores a su cotización vigente, validando mi saldo de créditos y la disponibilidad de tokens en el mercado (hasta un tope estricto de 100 tokens emitidos por jugador), para administrar mi cartera financiera dentro de la plataforma.

**Why this priority**: Es la funcionalidad transaccional principal del sistema. Implementa el intercambio económico, garantiza la integridad del suministro de tokens (100 por jugador) y la consistencia de saldos.

**Independent Test**: Registrar un usuario con créditos disponibles, ejecutar una compra vía `POST /api/orders/buy` verificando descuento de créditos y acreditación de tokens; luego ejecutar una venta vía `POST /api/orders/sell` verificando el reintegro de créditos a cotización vigente y disminución de tokens en cartera.

**Acceptance Scenarios**:

1. **Compra Exitosa de Tokens**:
   - **Given** un usuario autenticado con saldo suficiente (e.g. 50 créditos) y un jugador con cotización vigente de 5 créditos y 10 tokens disponibles en posesión del pool/superusuario,
   - **When** el usuario envía una solicitud a `POST /api/orders/buy` solicitando comprar 4 tokens,
   - **Then** el sistema debita 20 créditos del saldo del usuario, transfiere los 4 tokens del pool al usuario, registra la transacción como `COMPRA`, actualiza su posición en el portfolio con el precio promedio correspondiente, y retorna HTTP `200 OK`.

2. **Rechazo de Compra por Saldo Insuficiente**:
   - **Given** un usuario con saldo de 10 créditos que intenta comprar tokens cuyo costo total asciende a 30 créditos,
   - **When** envía la solicitud a `POST /api/orders/buy`,
   - **Then** el sistema rechaza la operación con código HTTP `400 Bad Request`, código de error `INSUFFICIENT_FUNDS` y mensaje en español "Saldo insuficiente para realizar la compra.", sin alterar saldos ni tokens.

3. **Rechazo de Compra por Exceso de Disponibilidad (Límite 100 Tokens)**:
   - **Given** un jugador del cual solo quedan 2 tokens disponibles en el mercado para compra,
   - **When** un usuario intenta comprar 5 tokens,
   - **Then** el sistema rechaza la operación con código HTTP `400 Bad Request`, código de error `INSUFFICIENT_TOKENS_AVAILABLE` y mensaje en español indicando la disponibilidad máxima actual.

4. **Venta Exitosa de Tokens**:
   - **Given** un usuario que posee 5 tokens de un jugador cuya cotización vigente es de 8 créditos,
   - **When** envía una solicitud a `POST /api/orders/sell` para vender 3 tokens,
   - **Then** el sistema retira 3 tokens de la cartera del usuario, acredita 24 créditos en su saldo, devuelve los tokens al pool de liquidez, registra la transacción como `VENTA`, y responde con código HTTP `200 OK`.

5. **Rechazo de Venta por Tenencia Insuficiente**:
   - **Given** un usuario que posee 2 tokens de un jugador e intenta vender 4 tokens,
   - **When** envía la solicitud a `POST /api/orders/sell`,
   - **Then** el sistema responde con código HTTP `400 Bad Request`, código de error `INSUFFICIENT_TOKEN_BALANCE` y mensaje en español indicando que no dispone de los tokens requeridos.

---

### User Story 3 - Consulta de Cotización Histórica y por Fecha Determinada (Priority: P1)

Como usuario o analista, quiero consultar la cotización de un jugador en una fecha histórica específica o su serie temporal de cotizaciones, para evaluar la tendencia de revalorización del futbolista antes de tomar decisiones de inversión.

**Why this priority**: Cumple con el requisito explícito de trazabilidad temporal del precio y alimenta los gráficos interactivos del frontend.

**Independent Test**: Generar cotizaciones para un jugador con fechas pasadas, realizar peticiones a `GET /api/players/{id}/quotes?date=YYYY-MM-DD` y `GET /api/players/{id}/quotes`, validando que se devuelvan los valores precisos correspondientes al instante solicitado.

**Acceptance Scenarios**:

1. **Consulta de Cotización a una Fecha Determinada**:
   - **Given** un jugador con cotizaciones registradas el 2026-10-01 (valor: 2.50) y el 2026-10-08 (valor: 3.20),
   - **When** el cliente solicita `GET /api/players/{id}/quotes?date=2026-10-05`,
   - **Then** el sistema retorna la cotización vigente a esa fecha (2.50 créditos) con código HTTP `200 OK`.

2. **Consulta a una Fecha Anterior a la Primera Cotización**:
   - **Given** una fecha consultada anterior a la existencia de cotizaciones para ese jugador,
   - **When** el cliente solicita la cotización para dicha fecha previa,
   - **Then** el sistema responde con el valor nominal inicial de 1.0 crédito o un mensaje explicativo con status HTTP `200 OK`.

3. **Obtención del Histórico de Cotizaciones para Gráficos**:
   - **Given** un jugador con múltiples cotizaciones a lo largo de las semanas,
   - **When** el cliente consulta `GET /api/players/{id}/quotes`,
   - **Then** el sistema retorna una lista cronológica ordenada con fecha, valor, estrategia y versión, lista para ser consumida por el componente de gráficos del frontend.

---

### User Story 4 - Consulta de Portfolio de Inversión y Rendimiento Financiero (Priority: P2)

Como usuario inversor autenticado, quiero consultar mi portfolio de inversiones para visualizar mis posiciones actuales de tokens, el precio promedio ponderado de compra, la valuación actual de mercado y mi ganancia o pérdida (PnL) acumulada por jugador y total.

**Why this priority**: Proporciona la visión consolidada patrimonial del usuario, esencial para la experiencia de usuario y la toma de decisiones financieras.

**Independent Test**: Comprar tokens de dos jugadores a precios distintos, consultar `GET /api/users/{id}/portfolio`, y verificar que el DTO devuelva tokens en posesión, precio promedio ponderado, cotización vigente, valor total de cartera y ganancia/pérdida porcentual y nominal calculada correctamente.

**Acceptance Scenarios**:

1. **Visualización de Portfolio con Posiciones Activas**:
   - **Given** un usuario que posee 10 tokens de Jugador A comprados a precio promedio de 2.0 y cotización actual de 3.0,
   - **When** consulta `GET /api/users/{id}/portfolio`,
   - **Then** el sistema devuelve código HTTP `200 OK` con el desglose: tokens poseídos (10), costo invertido (20.0), valor actual (30.0), ganancia (+10.0 / +50%) y el balance de créditos disponible.

2. **Portfolio de Usuario sin Inversiones**:
   - **Given** un usuario recién registrado sin compras realizadas,
   - **When** consulta su portfolio,
   - **Then** el sistema responde con una lista de posiciones vacía, valor total invertido en 0 y su saldo inicial de créditos.

---

### User Story 5 - Consulta del Historial de Operaciones Financieras (Priority: P2)

Como usuario o auditor, quiero consultar el historial completo de todas las operaciones de compra y venta realizadas por mi cuenta, para mantener un registro transparente y auditable de mis transacciones.

**Why this priority**: Garantiza la trazabilidad y rendición de cuentas estipulada en la Constitución del proyecto.

**Independent Test**: Realizar una secuencia de compras y ventas y consultar `GET /api/users/{id}/transactions`, verificando que se listen todas las operaciones con timestamp, jugador, tipo de operación, cantidad de tokens, precio unitario y resultado.

**Acceptance Scenarios**:

1. **Listado Cronológico de Transacciones**:
   - **Given** un usuario con operaciones previas registradas,
   - **When** consulta `GET /api/users/{id}/transactions`,
   - **Then** el sistema retorna un listado paginado u ordenado cronológicamente con `transactionId`, `fechaHora`, `tipo` (COMPRA / VENTA), `jugadorId`, `nombreJugador`, `cantidadTokens`, `precioUnitario`, `montoTotal` y `estado` (EXITOSA).

---

### User Story 6 - Consulta de Ranking de Jugadores por Estrategia Activa (Priority: P2)

Como usuario de la plataforma, quiero consultar una tabla de clasificación (ranking) de jugadores ordenada de mayor a menor según su cotización vigente bajo la estrategia activa, para identificar a los futbolistas más valiosos y con mejor desempeño deportivo del mercado.

**Why this priority**: Fomenta el descubrimiento de jugadores y la dinámica competitiva de la plataforma.

**Independent Test**: Invocar `GET /api/players/ranking` y verificar que el listado retorne los jugadores ordenados descendentemente por su cotización actual, indicando posición en el ranking, equipo, liga y variación respecto a la última medición.

**Acceptance Scenarios**:

1. **Ranking Ordenado Correctamente**:
   - **Given** un catálogo con jugadores cotizados a distintos valores,
   - **When** se consulta `GET /api/players/ranking`,
   - **Then** el sistema devuelve la lista ordenada de mayor a menor valor, con paginación opcional y metadatos de la estrategia activa empleada.

---

### User Story 7 - Actualización Periódica Automatizada de Cotizaciones (Scheduler Semanal) (Priority: P2)

Como operador de la plataforma, quiero que el sistema ejecute de forma autónoma una tarea programada semanal para actualizar las cotizaciones de todos los jugadores en base a los datos deportivos más recientes, garantizando la frescura del mercado sin intervención manual continua.

**Why this priority**: Automatiza el ciclo de vida del negocio conforme a los requerimientos de la Entrega 2.

**Independent Test**: Configurar la anotación `@Scheduled` con periodicidad de prueba o invocar el bean del scheduler, verificando en los logs y en la base de datos que se haya disparado el cálculo y persistido la nueva tanda de cotizaciones.

**Acceptance Scenarios**:

1. **Disparo Programado del Scheduler**:
   - **Given** el backend en ejecución con Spring Scheduler habilitado,
   - **When** se alcanza el cron programado semanal,
   - **Then** el servicio ejecuta el recálculo masivo de cotizaciones para todos los jugadores activos y loguea el inicio, cantidad procesada y tiempo total de ejecución.

---

### User Story 8 - Integración y Mapeo Unificado de WhoScored y Football-Data.org con Tolerancia a Fallos (Priority: P3)

Como subsistema de ingestión de datos, quiero unificar la obtención de información deportiva combinando la API oficial de Football-Data.org con las métricas detalladas obtenidas del scraper de WhoScored, asociando ambos orígenes al mismo registro de jugador y tolerando caídas de servicios externos mediante persistencia local.

**Why this priority**: Resuelve el issue identificado en el TP1 sobre simplificar la estructura de sincronización y garantiza que el sistema continúe operando aún si WhoScored o Football-Data no están disponibles.

**Independent Test**: Simular la desconexión del scraper de WhoScored o devolver error 502/504 ("SCRAPE_TIMEOUT"), y verificar que el servicio de sincronización mantenga los datos locales del jugador sin interrumpir las consultas ni los recálculos.

**Acceptance Scenarios**:

1. **Sincronización Unificada de Datos**:
   - **Given** un jugador identificado con ID de Football-Data y mapping a WhoScored,
   - **When** se ejecuta el proceso de sincronización,
   - **Then** el sistema almacena las estadísticas futbolísticas detalladas directamente en el modelo del jugador en PostgreSQL, manteniendo un único identificador consistente en el dominio.

2. **Fallback ante Indisponibilidad de Fuentes Externas**:
   - **Given** que el microservicio de scraping WhoScored responde con error o agota el timeout,
   - **When** el Adapter del backend detecta la falla,
   - **Then** utiliza los datos locales previamente almacenados, registra una advertencia en logs y no propaga errores 500 al usuario.

---

### User Story 9 - Feature Adicional: Sistema de Alertas de Precios y Notificaciones de Mercado (Priority: P3)

Como usuario inversor, quiero configurar alertas de precio personalizadas para jugadores de mi interés (e.g. "notificarme si Erling Haaland supera los 15 créditos" o "si cae por debajo de 8 créditos"), para estar al tanto de oportunidades de compra o venta en el mercado.

**Why this priority**: Cumple con el requisito obligatorio de la Entrega 2 de planificar, implementar y presentar un **feature adicional innovador** para el trabajo práctico.

**Independent Test**: Crear una alerta de precio vía `POST /api/alerts` con un umbral determinado, ejecutar un recálculo de cotizaciones que cruce dicho umbral, y consultar `GET /api/alerts` o el buzón de notificaciones del usuario verificando que la alerta haya cambiado a estado `DISPARADA`.

**Acceptance Scenarios**:

1. **Creación de Alerta de Precio**:
   - **Given** un usuario autenticado y un jugador con cotización de 10 créditos,
   - **When** el usuario crea una alerta para `jugadorId=1` con condición `PRECIO_MAYOR_QUE` y valor `12.0`,
   - **Then** el sistema persiste la alerta en estado `ACTIVA` y responde con código HTTP `201 Created`.

2. **Disparo Automático de la Alerta tras Recálculo**:
   - **Given** una alerta activa de precio mayor a 12.0 y una nueva cotización del jugador calculada en 13.5 créditos,
   - **When** finaliza el recálculo de cotizaciones,
   - **Then** el evaluador de alertas marca la alerta como `DISPARADA`, genera una notificación para el usuario con timestamp y mensaje en español ("La cotización de Erling Haaland ha alcanzado 13.50 créditos").

---

### User Story 10 - Visualización Frontend: Gráficos de Cotización, Cartera y Operaciones (Priority: P2)

Como usuario del cliente web en React, quiero ver gráficos interactivos de la evolución histórica de cotizaciones, operar con botones de compra y venta en tiempo real y visualizar mi portfolio con indicadores gráficos claros, para interactuar de forma intuitiva con la plataforma.

**Why this priority**: Cumple con los requerimientos de la Entrega 2 para el frontend, enriqueciendo la experiencia de usuario.

**Independent Test**: Navegar a la ficha de un jugador en la aplicación React, observar el gráfico de líneas con la serie histórica de precios, presionar el botón de compra, confirmar la transacción y observar la actualización inmediata del gráfico y del saldo en el portfolio.

**Acceptance Scenarios**:

1. **Renderizado de Gráfico de Cotización Histórica**:
   - **Given** la vista de detalle de un jugador con historial de cotizaciones,
   - **When** se carga la página,
   - **Then** se renderiza un gráfico de líneas temporal interactivo (con eje temporal y eje de créditos) sin errores de consola.

2. **Flujo de Compra y Venta con Feedback Visual**:
   - **Given** la interfaz de compra/venta en la vista de detalle,
   - **When** el usuario ingresa la cantidad de tokens y confirma,
   - **Then** la interfaz valida el formulario, envía la petición al backend, actualiza el saldo disponible y muestra un mensaje de confirmación o error en español.

---

### Edge Cases

- **Concurrencia en compra del último token:** Si dos usuarios intentan comprar simultáneamente el último token disponible de un jugador, la transacción debe resolverse con bloqueo optimista/pesimista para que uno adquiera el token y el otro reciba una respuesta controlada de tokens agotados (`INSUFFICIENT_TOKENS_AVAILABLE`).
- **Límite absoluto de 100 tokens:** El sistema debe impedir bajo cualquier circunstancia (incluso recargas o ventas) que la suma de tokens en manos de usuarios más los tokens en el pool del superusuario sea distinta de 100.
- **Cotización a fecha sin historial previo:** Si se consulta una fecha anterior al inicio del historial de cotizaciones, debe retornar el valor base inicial (1.0 crédito).
- **Caída de servicios externos en ejecución de Scheduler:** Si el proveedor externo falla durante el recálculo periódico, el scheduler debe continuar el procesamiento de los demás jugadores y usar valores locales para los fallidos sin abortar la ejecución completa.
- **Cero minutos jugados o sin estadísticas:** Jugadores sin actividad deportiva deben recibir la cotización base mínima garantizada (1 crédito) sin errores matemáticos.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE proveer al menos dos estrategias de valoración configurables (con pesos definibles para minutos, goles, asistencias, tiros, pases, intercepciones, tarjetas, posición y rating).
- **FR-002**: El sistema DEBE versionar cada cotización generada, registrando fecha, valor, identificador de estrategia y versión aplicada para trazabilidad histórica.
- **FR-003**: El sistema DEBE permitir recalcular las cotizaciones bajo demanda a través del endpoint `POST /api/quotes/recalculate`.
- **FR-004**: Cada jugador registrado DEBE contar con exactamente 100 tokens emitidos en el sistema, con valor inicial de 1 crédito por token, inicialmente en poder del superusuario.
- **FR-005**: El sistema DEBE procesar compras de tokens mediante `POST /api/orders/buy`, debitando créditos, transfiriendo tokens del pool al comprador y actualizando el precio promedio de compra de forma transaccional.
- **FR-006**: El sistema DEBE procesar ventas de tokens mediante `POST /api/orders/sell`, validando tenencia previa, acreditando créditos a cotización vigente y reintegrando los tokens al pool de forma transaccional.
- **FR-007**: El sistema DEBE registrar un historial inmutable y auditable de cada transacción financiera (`GET /api/users/{id}/transactions`).
- **FR-008**: El sistema DEBE calcular y exponer el portfolio de inversiones del usuario (`GET /api/users/{id}/portfolio`), reportando cantidad de tokens, precio promedio de compra, cotización actual, valor total y ganancia/pérdida (PnL).
- **FR-009**: El sistema DEBE permitir consultar la cotización de un jugador en una fecha histórica determinada (`GET /api/players/{id}/quotes?date=YYYY-MM-DD`).
- **FR-010**: El sistema DEBE exponer el ranking de jugadores ordenado descendentemente por cotización bajo la estrategia activa (`GET /api/players/ranking`).
- **FR-011**: El sistema DEBE contar con un proceso programado (Spring Scheduler semanal) para la actualización periódica de cotizaciones.
- **FR-012**: El sistema DEBE unificar los datos de WhoScored y Football-Data.org bajo una estructura simplificada en la base de datos, implementando fallback a datos locales ante fallas del proveedor externo.
- **FR-013**: Todos los endpoints REST de la aplicación DEBEN prescindir del prefijo `/v1`, estandarizándose bajo `/api/...` (e.g. `/api/players`, `/api/quotes`, `/api/orders`, `/api/users`, `/api/alerts`).
- **FR-014**: El sistema DEBE incorporar el feature adicional de "Alertas de Cotización de Jugadores", permitiendo crear (`POST /api/alerts`), listar (`GET /api/alerts`) y disparar notificaciones cuando un jugador alcance un precio umbral.
- **FR-015**: El sistema DEBE soportar perfiles independientes de ejecución para testing con persistencia en memoria (HSQLDB o H2) para perfiles `unit` y `e2e`, incluyendo carga automática de datos iniciales en startup.
- **FR-016**: La interfaz web en React DEBE incluir componentes con gráficos de cotización temporal, botones de compra y venta con validación, y visualización de métricas de cartera y transacciones.
- **FR-017**: Todos los mensajes de error y validación DEBEN estructurarse de forma consistente y redactarse en **español**, utilizando `ProblemDetail` (RFC 7807) sin exponer detalles técnicos internos.

### Key Entities

- **Player**: Futbolista perteneciente a una de las 5 ligas principales, con metadatos base y estadísticas deportivas consolidadas de Football-Data y WhoScored.
- **ValuationStrategy**: Estrategia de ponderación deportiva con pesos asignados a cada métrica futbolística y estado activo/inactivo.
- **PlayerQuote**: Registro histórico de cotización que vincula a un jugador con una fecha, un valor en créditos y la versión de la estrategia utilizada.
- **TokenPool / PlayerToken**: Control de emisión de los 100 tokens por jugador, distinguiendo tokens en posesión de usuarios vs. tokens en el pool del superusuario.
- **Order / Transaction**: Registro transaccional inmutable de una operación de compra o venta con usuario, jugador, cantidad de tokens, precio unitario, monto total, fecha y estado.
- **UserPortfolio / Position**: Posición del usuario sobre un jugador, conteniendo cantidad de tokens acumulados y precio promedio ponderado de compra.
- **PriceAlert**: Alerta configurada por un usuario para un jugador con tipo de condición (mayor/menor que), precio objetivo y estado (ACTIVA/DISPARADA).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las operaciones de compra y venta se ejecutan de manera atómica/transaccional, garantizando que el total de tokens por jugador permanezca exactamente en 100 sin sobreemisión ni inconsistencia de saldo.
- **SC-002**: El recálculo masivo de cotizaciones para todos los jugadores del catálogo se completa en menos de 5 segundos en ambiente local.
- **SC-003**: La consulta de cotización histórica por fecha devuelve el valor correspondiente con precisión del 100% frente a la serie temporal registrada.
- **SC-004**: En caso de indisponibilidad total o timeout del scraper de WhoScored, el 100% de los endpoints de consulta y mercado continúan respondiendo con éxito utilizando la información local persistida.
- **SC-005**: La suite de tests unitarios y de integración cubre todas las reglas de negocio de la Entrega 2, manteniendo el pipeline de CI en estado SUCCESS con SonarCloud < 10 issues.
- **SC-006**: La interfaz web permite a un usuario autenticado consultar el detalle de un jugador, ver su gráfico histórico y ejecutar una orden de compra o venta en menos de 3 pasos interactivos.

---

## Assumptions

- **Unidad monetaria:** La plataforma opera con créditos virtuales internos; no se requiere integración con pasarelas de pago reales (Stripe, MercadoPago, etc.).
- **Distribución inicial:** Al inicializarse el sistema, el 100% de los tokens de cada jugador (100 tokens) pertenecen a la cuenta del superusuario/mercado a un valor nominal de 1 crédito cada uno.
- **Frecuencia del Scheduler:** El scheduler semanal se configura mediante una expresión cron en `application.yml`, permitiendo además la ejecución bajo demanda mediante el endpoint de recálculo para fines de demostración y pruebas.
- **Feature Adicional seleccionado:** Se adopta formalmente el "Sistema de Alertas de Precios de Jugadores (Price Alerts)" como el feature adicional requerido para la Entrega 2, al integrarse de forma natural con el recálculo de cotizaciones y aportar alto valor funcional al usuario inversor.
- **Normalización de rutas:** De acuerdo con los issues del TP1, se elimina el prefijo `/v1` en todos los endpoints, manteniendo `/api/...` como raíz común.
