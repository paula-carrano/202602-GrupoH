# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 2: Core Functionality

**Feature Branch**: `004-entrega2-core`  
**Created**: 2026-10-12  
**Status**: Draft  
**Google Docs Oficial del Proyecto**: [Documento de Cátedra y SDD](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk) (ID: `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`)  
**Consigna Oficial (Solo Lectura)**: [Documento de Visión](https://docs.google.com/document/d/12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8/edit?usp=sharing) (ID: `12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8`)  
**Input**: User description: "Actualización de Spec para Entrega 2 incorporando directivas docentes: Se descartan HSQLDB/H2, creación de datos de prueba ligada a H2, documentación Swagger y Job de Coverage (ya cubiertos en Entrega 1). Queda como único punto Core separar profiles de testing para unitarios y e2e. Se priorizan los dos requerimientos clave: (1) Visualización de datos de usuario con gráficos en el front (serie temporal de cotización, distribución de cartera y evolución patrimonial) y (2) Feature Adicional: Sistema de Alertas de Precios con Notificaciones en Tiempo Real (WebSockets/SSE) permitiendo compra/venta inmediata, además de cotizaciones por estrategia, mercado de tokens, historial, ranking, scheduler semanal y unificación de fuentes sin /v1."

---

## Directivas de Cátedra — Alcance Depurado de Entrega 2

Por indicación expresa del cuerpo docente, se depuran los puntos de infraestructura que ya fueron cubiertos en la Entrega 1:
- **Descartados (ya resueltos o sin efecto):**
  1. ~~Utilizar HSQLDB / H2 para persistir datos~~ *(El proyecto ya opera directamente sobre PostgreSQL).*
  2. ~~Crear datos de prueba cuando levanta la aplicación~~ *(Estaba vinculado al uso de H2).*
  3. ~~Documentación de Endpoints con Swagger~~ *(Cubierto y entregado en Entrega 1).*
  4. ~~Implementar Job de Coverage~~ *(Integrado en SonarCloud durante Entrega 1).*
- **Único punto Core técnico requerido en Entrega 2:**
  1. **Separar profiles de testing para pruebas unitarias (`unit`) y end-to-end (`e2e`).**

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cálculo y Recálculo de Cotizaciones de Jugadores por Estrategia Activa (Priority: P1)

Como administrador o sistema automatizado, quiero calcular y actualizar periódicamente las cotizaciones de los jugadores en base a una estrategia de valoración configurable (con pesos ponderados de métricas deportivas), registrando el historial y la versión de la estrategia utilizada, para que el mercado refleje fielmente el rendimiento deportivo de los futbolistas.

**Why this priority**: Es el núcleo del valor diferencial de la plataforma. Sin una cotización calculada por estrategia no existe precio de mercado para habilitar la compra, venta o ranking de tokens.

**Independent Test**: Puede probarse configurando dos estrategias de valoración distintas en la base de datos, invocando el endpoint de recálculo `POST /quotes/recalculate`, y verificando que cada jugador posea una nueva cotización generada, asociada a la versión de la estrategia activa y persistida en su histórico.

**Acceptance Scenarios**:

1. **Recálculo Masivo Exitoso**:
   - **Given** un catálogo de jugadores con métricas deportivas registradas y una estrategia de valoración activa configurada con pesos para goles, asistencias, minutos y rating,
   - **When** se solicita el recálculo mediante `POST /quotes/recalculate`,
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

**Independent Test**: Registrar un usuario con créditos disponibles, ejecutar una compra vía `POST /orders/buy` verificando descuento de créditos y acreditación de tokens; luego ejecutar una venta vía `POST /orders/sell` verificando el reintegro de créditos a cotización vigente y disminución de tokens en cartera.

**Acceptance Scenarios**:

1. **Compra Exitosa de Tokens**:
   - **Given** un usuario autenticado con saldo suficiente (e.g. 50 créditos) y un jugador con cotización vigente de 5 créditos y 10 tokens disponibles en posesión del superusuario,
   - **When** el usuario envía una solicitud a `POST /orders/buy` solicitando comprar 4 tokens,
   - **Then** el sistema debita 20 créditos del saldo del usuario, transfiere los 4 tokens del superusuario al usuario, registra la transacción como `COMPRA`, actualiza su posición en el portfolio con el precio promedio ponderado correspondiente, y retorna HTTP `200 OK`.

2. **Rechazo de Compra por Saldo Insuficiente**:
   - **Given** un usuario con saldo de 10 créditos que intenta comprar tokens cuyo costo total asciende a 30 créditos,
   - **When** envía la solicitud a `POST /orders/buy`,
   - **Then** el sistema rechaza la operación con código HTTP `400 Bad Request`, código de error `INSUFFICIENT_FUNDS` y mensaje en español "Saldo insuficiente para realizar la compra.", sin alterar saldos ni tokens.

3. **Rechazo de Compra por Exceso de Disponibilidad (Límite 100 Tokens)**:
   - **Given** un jugador del cual solo quedan 2 tokens disponibles en el mercado para compra,
   - **When** un usuario intenta comprar 5 tokens,
   - **Then** el sistema rechaza la operación con código HTTP `400 Bad Request`, código de error `INSUFFICIENT_TOKENS_AVAILABLE` y mensaje en español indicando la disponibilidad máxima actual.

4. **Venta Exitosa de Tokens**:
   - **Given** un usuario que posee 5 tokens de un jugador cuya cotización vigente es de 8 créditos,
   - **When** envía una solicitud a `POST /orders/sell` para vender 3 tokens,
   - **Then** el sistema retira 3 tokens de la cartera del usuario, acredita 24 créditos en su saldo, devuelve los tokens al superusuario, registra la transacción como `VENTA`, y responde con código HTTP `200 OK`.

5. **Rechazo de Venta por Tenencia Insuficiente**:
   - **Given** un usuario que posee 2 tokens de un jugador e intenta vender 4 tokens,
   - **When** envía la solicitud a `POST /orders/sell`,
   - **Then** el sistema responde con código HTTP `400 Bad Request`, código de error `INSUFFICIENT_TOKEN_BALANCE` y mensaje en español indicando que no dispone de los tokens requeridos.

---

### User Story 3 - Consulta de Cotización Histórica y por Fecha Determinada (Priority: P1)

Como usuario o analista, quiero consultar la cotización de un jugador en una fecha histórica específica o su serie temporal completa de cotizaciones, para evaluar la tendencia de revalorización del futbolista antes de tomar decisiones de inversión.

**Why this priority**: Cumple con el requisito explícito de trazabilidad temporal del precio y alimenta los gráficos interactivos del frontend.

**Independent Test**: Generar cotizaciones para un jugador con fechas pasadas, realizar peticiones a `GET /players/{id}/quotes?date=YYYY-MM-DD` y `GET /players/{id}/quotes`, validando que se devuelvan los valores precisos correspondientes al instante solicitado.

**Acceptance Scenarios**:

1. **Consulta de Cotización a una Fecha Determinada**:
   - **Given** un jugador con cotizaciones registradas el 2026-10-01 (valor: 2.50) y el 2026-10-08 (valor: 3.20),
   - **When** el cliente solicita `GET /players/{id}/quotes?date=2026-10-05`,
   - **Then** el sistema retorna la cotización vigente a esa fecha (2.50 créditos) con código HTTP `200 OK`.

2. **Consulta a una Fecha Anterior a la Primera Cotización**:
   - **Given** una fecha consultada anterior a la existencia de cotizaciones para ese jugador,
   - **When** el cliente solicita la cotización para dicha fecha previa,
   - **Then** el sistema responde con el valor nominal inicial de 1.0 crédito con status HTTP `200 OK`.

3. **Obtención del Histórico de Cotizaciones para Gráficos**:
   - **Given** un jugador con múltiples cotizaciones a lo largo de las semanas,
   - **When** el cliente consulta `GET /players/{id}/quotes`,
   - **Then** el sistema retorna una lista cronológica ordenada con fecha, valor, estrategia y versión, lista para ser consumida por el componente de gráficos del frontend.

---

### User Story 4 - Visualización de Datos de Usuario con Gráficos en el Frontend (Priority: P1)

Como usuario inversor en la aplicación web, quiero visualizar mis datos financieros y de mercado a través de gráficos interactivos (evolución histórica de cotización por jugador, distribución de mi cartera de tokens y evolución de mi patrimonio/saldo), junto con controles directos de compra y venta, para analizar visualmente el rendimiento de mis inversiones.

**Why this priority**: Es el **primer requerimiento nuevo central** estipulado por la cátedra para la Entrega 2 en reemplazo de los antiguos puntos core.

**Independent Test**: Iniciar sesión en el frontend con un usuario que posea transacciones e inversiones activas, navegar al dashboard de portfolio y al detalle de un jugador, y comprobar el renderizado de los 3 gráficos interactivos con datos reales del backend.

**Acceptance Scenarios**:

1. **Gráfico de Serie Temporal de Cotización del Jugador**:
   - **Given** la vista de detalle de un jugador con historial de cotizaciones,
   - **When** el usuario accede a la pantalla del jugador,
   - **Then** se visualiza un gráfico de línea temporal mostrando la evolución del precio en créditos por fecha y versión de estrategia.

2. **Gráfico de Distribución de Cartera (Portfolio Allocation)**:
   - **Given** un usuario con tokens de distintos jugadores en su portfolio,
   - **When** accede a su panel de Portfolio,
   - **Then** se renderiza un gráfico de dona/torta que representa la proporción del valor actual invertido en cada jugador respecto al total de su cartera.

3. **Gráfico de Evolución del Patrimonio y Saldo del Usuario**:
   - **Given** un usuario con historial de operaciones de compra/venta y variaciones de cotización,
   - **When** visualiza su resumen financiero en el frontend,
   - **Then** se muestra un gráfico evolutivo que compara su liquidez en créditos y la valoración total de sus posiciones a lo largo del tiempo.

4. **Operación Interactiva de Compra/Venta y Historial**:
   - **Given** la vista de un jugador o del portfolio,
   - **When** el usuario presiona el botón de comprar o vender tokens y confirma la cantidad,
   - **Then** la transacción impacta en el backend y actualiza dinámicamente los gráficos, el saldo y la tabla de historial de operaciones sin recargar la página completa.

---

### User Story 5 - Feature Adicional: Alertas de Precios con Notificaciones en Tiempo Real y Compra Directa (Priority: P1)

Como usuario inversor, quiero configurar alertas de precio sobre jugadores específicos (por encima o por debajo de un umbral en créditos) y recibir una notificación instantánea en tiempo real en mi navegador (vía WebSockets / Server-Sent Events) cuando una cotización cruce dicho umbral, con un acceso rápido para ejecutar una compra o venta inmediata en ese instante.

**Why this priority**: Es el **segundo requerimiento nuevo central** de la Entrega 2. Ejercita el desarrollo de producto e innovación técnica pedidos por la cátedra, conectando el motor de cotizaciones del backend con una experiencia reactiva en tiempo real en el frontend.

#### Justificación de Producto (Valor de Negocio)
En un mercado donde las cotizaciones varían según el desempeño deportivo y los tokens son escasos (máximo 100 por jugador), enterarse tarde de una variación de precio significa perder la oportunidad de compra o no poder tomar ganancias a tiempo. El sistema de **Alertas en Tiempo Real con Acción Rápida (One-Click Trade)** convierte una plataforma pasiva de consulta en una plataforma activa de trading deportivo.

#### Justificación Técnica (Arquitectura)
Incorpora un flujo **Event-Driven & Push Notification**:
1. Al finalizar un recálculo de cotizaciones (manual o por Scheduler), el servicio publica un evento interno de actualización de precios.
2. El motor de alertas evalúa las condiciones activas de los usuarios contra los nuevos precios.
3. Cuando se cumple una condición, el backend envía un evento push en tiempo real mediante **WebSockets (STOMP) o Server-Sent Events (SSE)** hacia el cliente React conectado.
4. El frontend despliega un banner/toast interactivo en vivo que permite abrir el modal de compra/venta pre-cargado para operar en tiempo real.

**Independent Test**: Suscribir el cliente frontend al canal de notificaciones, crear una alerta (`POST /alerts`) para un jugador, disparar `POST /quotes/recalculate` de modo que el nuevo precio supere el umbral, y verificar que aparezca la notificación en tiempo real en el navegador con el botón de compra inmediata funcional.

**Acceptance Scenarios**:

1. **Suscripción y Creación de Alerta de Precio**:
   - **Given** un usuario autenticado viendo un jugador con cotización actual de 10 créditos,
   - **When** configura una alerta (`POST /alerts`) con condición `PRECIO_MAYOR_O_IGUAL` a `12.0` créditos,
   - **Then** el sistema guarda la alerta en estado `ACTIVA` y retorna HTTP `201 Created`.

2. **Notificación Push en Tiempo Real al Cumplirse la Condición**:
   - **Given** una alerta activa y el usuario con sesión abierta en el frontend,
   - **When** se recalcula la cotización del jugador y su nuevo valor pasa a `12.8` créditos,
   - **Then** el backend cambia el estado de la alerta a `DISPARADA`, persiste la notificación y emite un mensaje por WebSocket/SSE al frontend, mostrando instantáneamente una alerta visual en pantalla.

3. **Acción de Compra en Tiempo Real desde la Notificación**:
   - **Given** la notificación emergente en tiempo real en el frontend,
   - **When** el usuario hace clic en el botón "Comprar ahora" dentro de la notificación,
   - **Then** se abre directamente el formulario de orden preconfigurado con ese jugador y su nueva cotización para confirmar la operación al instante.

---

### User Story 6 - Consulta de Portfolio de Inversión e Historial de Operaciones (Priority: P2)

Como usuario inversor autenticado, quiero consultar el detalle de mi portfolio (`GET /users/{id}/portfolio`) y el registro auditable de todas mis transacciones (`GET /users/{id}/transactions`), para conocer mi cantidad de tokens por jugador, precio promedio de compra, valor actual, ganancia/pérdida (PnL) y el historial cronológico de operaciones.

**Why this priority**: Provee los datos fundamentales que respaldan la auditoría financiera y alimentan las vistas y gráficos del usuario.

**Independent Test**: Ejecutar compras y ventas con un usuario y consultar ambos endpoints verificando la exactitud matemática del precio promedio ponderado, la ganancia/pérdida y el registro inmutable de cada transacción.

**Acceptance Scenarios**:

1. **Cálculo Exacto de Posición y Rentabilidad en Portfolio**:
   - **Given** un usuario que compró 10 tokens a 2.0 créditos y luego 10 tokens a 4.0 créditos (precio promedio: 3.0), y cuya cotización actual es 5.0,
   - **When** consulta `GET /users/{id}/portfolio`,
   - **Then** el sistema informa: 20 tokens, precio promedio 3.0, valor actual 100.0, ganancia neta +40.0 créditos (+66.67%).

2. **Historial Auditable de Transacciones**:
   - **Given** un usuario que realizó operaciones de compra y venta,
   - **When** consulta `GET /users/{id}/transactions`,
   - **Then** recibe la lista cronológica con fecha/hora, tipo de operación, jugador, cantidad de tokens, precio unitario, monto total y resultado.

---

### User Story 7 - Ranking de Jugadores según Estrategia Activa y Scheduler Semanal (Priority: P2)

Como usuario u operador, quiero consultar el ranking de jugadores (`GET /players/ranking`) ordenado según la estrategia de valoración activa y contar con un proceso programado semanal (`Spring Scheduler`) que actualice automáticamente las cotizaciones.

**Why this priority**: Completa el circuito funcional del sistema de cotización requerido para la Entrega 2.

**Independent Test**: Invocar `GET /players/ranking` verificando el orden descendente por cotización vigente, y verificar la ejecución del job `@Scheduled` en el backend.

**Acceptance Scenarios**:

1. **Consulta de Ranking**:
   - **Given** jugadores con distintas cotizaciones bajo la estrategia activa,
   - **When** se solicita `GET /players/ranking`,
   - **Then** el sistema retorna la lista ordenada de mayor a menor cotización incluyendo posición, datos del jugador, cotización actual y estrategia aplicada.

2. **Ejecución Automática del Scheduler**:
   - **Given** el job programado configurado en Spring,
   - **When** se cumple el ciclo programado,
   - **Then** recalcula las cotizaciones de todos los jugadores utilizando la estrategia activa y dispara las alertas de precio correspondientes.

---

### User Story 8 - Separación de Profiles de Testing (`unit` y `e2e`) y Simplificación de Sync / Rutas (Priority: P2)

Como equipo de desarrollo, quiero separar la configuración de perfiles de ejecución para tests unitarios (`unit`) y tests end-to-end (`e2e`), unificar la estructura de sincronización entre Football-Data y WhoScored en la base de datos PostgreSQL, y exponer todos los endpoints sin el prefijo `/v1`.

**Why this priority**: Satisface el **único requerimiento Core** vigente para la Entrega 2 y resuelve los puntos pendientes de revisión de la Entrega 1.

**Independent Test**: Ejecutar la suite de tests bajo el perfil `unit` (rápido, con mocks y sin levantar dependencias externas) y bajo el perfil `e2e` (validando el flujo extremo a extremo contra la base de datos), comprobando que ambos perfiles corran de manera independiente y limpia.

**Acceptance Scenarios**:

1. **Ejecución Aislada por Perfil (`unit` vs `e2e`)**:
   - **Given** las configuraciones `application-unit.yml` y `application-e2e.yml`,
   - **When** se ejecutan los comandos de test filtrando por cada perfil,
   - **Then** los tests unitarios se ejecutan de forma aislada sin requerir servicios externos y los tests E2E validan la integración completa de punta a punta.

2. **Endpoints Estandarizados sin `/v1`**:
   - **Given** la suite de controladores REST,
   - **When** el frontend o un cliente invoca `/players`, `/quotes/recalculate`, `/orders/buy`, `/orders/sell`, `/users/{id}/portfolio`, `/users/{id}/transactions`,
   - **Then** responden directamente sin el segmento `/v1`, alineados con la consigna oficial.

---

### Edge Cases

- **Concurrencia en compra del último token:** Si dos usuarios intentan comprar simultáneamente los últimos tokens disponibles de un jugador, la transacción debe asegurar mediante control de concurrencia que nunca se supere el límite de 100 tokens emitidos.
- **Reconexión de cliente en Notificaciones en Tiempo Real:** Si el usuario pierde conexión temporalmente y vuelve a abrir la aplicación, debe poder visualizar las alertas que se dispararon durante su desconexión.
- **Cotización a fecha sin historial previo:** Si se consulta una fecha anterior al primer registro de cotización del jugador, el sistema retorna el valor inicial nominal (1 crédito).
- **Tolerancia a fallos de proveedores externos:** Si WhoScored o Football-Data fallan durante la sincronización o el scheduler, el sistema utiliza los datos persistidos en PostgreSQL sin interrumpir el mercado ni arrojar errores 500.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE soportar al menos dos estrategias de valoración configurables con pesos asignables por métrica deportiva (goles, asistencias, minutos, tiros, pases, intercepciones, tarjetas, posición, rating).
- **FR-002**: Cada cotización generada DEBE persistirse en el historial conservando fecha, valor, estrategia utilizada y su versión.
- **FR-003**: El sistema DEBE exponer `POST /quotes/recalculate` para recalcular cotizaciones con la estrategia activa.
- **FR-004**: Cada jugador DEBE tener exactamente 100 tokens emitidos con valor inicial de 1 crédito en posesión inicial del superusuario.
- **FR-005**: El sistema DEBE procesar compras (`POST /orders/buy`) y ventas (`POST /orders/sell`) de forma transaccional, validando saldo de créditos, disponibilidad de tokens y tenencia del usuario.
- **FR-006**: El sistema DEBE permitir consultar el historial de cotizaciones y la cotización de un jugador para una fecha dada (`GET /players/{id}/quotes?date=YYYY-MM-DD`).
- **FR-007**: El sistema DEBE exponer el portfolio del usuario (`GET /users/{id}/portfolio`) con cantidad de tokens, precio promedio ponderado de compra, valor actual y ganancia/pérdida (PnL).
- **FR-008**: El sistema DEBE exponer el historial inmutable de transacciones financieras del usuario (`GET /users/{id}/transactions`).
- **FR-009**: El sistema DEBE exponer el ranking de jugadores (`GET /players/ranking`) según la estrategia de valoración activa.
- **FR-010**: El sistema DEBE ejecutar un job programado semanal (`Spring Scheduler`) para recalcular cotizaciones automáticamente.
- **FR-011**: El Frontend DEBE implementar visualización de datos de usuario mediante 3 gráficos interactivos: (1) Serie temporal de cotización histórica del jugador, (2) Gráfico de distribución de cartera de tokens del usuario, y (3) Gráfico de evolución del patrimonio y saldo del usuario.
- **FR-012**: El sistema DEBE implementar como **Feature Adicional** un sistema de **Alertas de Precios con Notificaciones en Tiempo Real** (vía WebSockets o SSE), permitiendo configurar umbrales (`POST /alerts`, `GET /alerts`), recibir avisos push instantáneos en el frontend cuando el precio cruza el umbral y ejecutar la compra/venta en tiempo real desde la notificación.
- **FR-013**: El proyecto DEBE separar los perfiles de testing en `unit` y `e2e` como único requerimiento Core de infraestructura de la Entrega 2, operando sobre PostgreSQL sin requerir HSQLDB/H2.
- **FR-014**: El sistema DEBE simplificar la sincronización entre WhoScored y Football-Data.org unificando los datos en el modelo de jugador y garantizando fallback local si los proveedores fallan.
- **FR-015**: Todos los endpoints DEBEN prescindir del segmento `/v1` y devolver errores de validación y negocio en **español** bajo el estándar `ProblemDetail` (RFC 7807).

### Key Entities

- **Player**: Jugador de una de las 5 ligas con datos unificados de Football-Data y estadísticas de WhoScored.
- **ValuationStrategy**: Estrategia configurable con pesos por métrica, versión y bandera de estrategia activa.
- **PlayerQuote**: Cotización histórica de un jugador en una fecha con el valor calculado y versión de estrategia.
- **Order / Transaction**: Operación auditable de compra o venta de tokens con usuario, jugador, cantidad, precio unitario, total y fecha.
- **PortfolioPosition**: Posición activa de un usuario sobre un jugador (cantidad de tokens y precio promedio de compra).
- **PriceAlert**: Alerta configurada por un usuario con jugador, condición (`MAYOR_IGUAL` / `MENOR_IGUAL`), precio objetivo, estado (`ACTIVA` / `DISPARADA`) y fecha de disparo.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las órdenes de compra y venta se ejecutan de manera transaccional, manteniendo invariante el suministro de 100 tokens por jugador y la exactitud de saldos.
- **SC-002**: Cuando una cotización se actualiza y cruza el umbral de una alerta activa, la notificación en tiempo real llega al cliente web en menos de 2 segundos sin necesidad de refrescar el navegador.
- **SC-003**: Los 3 gráficos financieros del frontend (evolución de cotización, distribución de cartera y evolución patrimonial) se renderizan y actualizan dinámicamente tras cada operación de compra o venta.
- **SC-004**: Los suites de pruebas pueden ejecutarse de forma independiente mediante los perfiles `unit` y `e2e`, manteniendo el build en verde y SonarCloud con menos de 10 issues.
- **SC-005**: Ante la caída simulada del scraper de WhoScored, el 100% de las funcionalidades de consulta, cotización y mercado siguen operando con datos locales.

---

## Assumptions

- **Persistencia unificada en PostgreSQL:** Conforme a la aclaración de la cátedra, se descarta HSQLDB/H2 y la carga de datos asociada a bases en memoria.
- **Rutas alineadas a la Consigna Oficial:** Se remueve `/v1` de las rutas REST para coincidir con la tabla de endpoints del Documento de Visión (`/players`, `/quotes/recalculate`, `/orders/buy`, `/orders/sell`, `/users/{id}/portfolio`, `/users/{id}/transactions`).
- **Canal de Tiempo Real:** Se utilizará Server-Sent Events (SSE) o WebSockets (STOMP) nativos de Spring Boot para empujar las notificaciones de alertas de precios en vivo hacia el cliente React.
