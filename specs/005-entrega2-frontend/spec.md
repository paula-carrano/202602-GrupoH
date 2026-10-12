# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 2: Frontend Visualization, Trading & Real-Time Alerts

**Feature Branch**: `005-entrega2-frontend`  
**Created**: 2026-10-12  
**Status**: Draft  
**Scope**: **Exclusivamente Frontend** (Vistas en React + TypeScript, Gráficos Financieros Interactivos, Formularios de Compra/Venta, Panel de Portfolio/Historial/Ranking, y Cliente de Notificaciones en Tiempo Real con Compra Directa). Consume los contratos expuestos por el backend definidos en [`specs/004-entrega2-core/spec.md`](../004-entrega2-core/spec.md) sin implementar lógica de negocio propia del servidor.  
**Google Docs Oficial del Proyecto**: [Documento de Cátedra y SDD](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk) (ID: `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`)  
**Consigna Oficial (Solo Lectura)**: [Documento de Visión](https://docs.google.com/document/d/12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8/edit?usp=sharing) (ID: `12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8`)  
**Input**: User description: "Especificación atómica de FRONTEND para la Entrega 2: Visualización de datos de usuario con 3 gráficos interactivos (serie temporal de cotización del jugador, distribución de cartera y evolución del patrimonio/saldo), botones y flujo de compra/venta de tokens, vistas de cotización a una fecha dada, portfolio, historial de operaciones y ranking, e interfaz en tiempo real del Feature Adicional (Alertas de Precios con notificaciones push y acción rápida de compra/venta), adaptando las rutas sin /v1."

---

## Directivas de Cátedra — Alcance de Frontend para Entrega 2

Conforme a las directivas docentes para la Entrega 2:
1. **Requerimiento Nuevo Central 1:** Agregar **visualización de datos de usuario con gráficos en el front** (evolución de cotización del jugador, distribución de cartera y evolución patrimonial/saldo).
2. **Requerimiento Nuevo Central 2 (Feature Adicional en Frontend):** Interfaz de gestión de **Alertas de Precios** y recepción de **Notificaciones en Tiempo Real** (vía WebSockets / SSE) con botón de acción rápida (*One-Click Trade*) para comprar o vender tokens en vivo sin cambiar de pantalla.
3. **Funcionalidades Core de Mercado en Frontend:** Botones de compra/venta de tokens, visualización de cotización actual y a fecha dada, portfolio con ganancias/pérdidas (PnL), historial de operaciones y ranking de jugadores, consumiendo las rutas estandarizadas sin `/v1`.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visualización de Datos Financieros y de Usuario con 3 Gráficos Interactivos (Priority: P1)

Como usuario inversor en la aplicación web, quiero visualizar mis datos financieros y la evolución de los jugadores mediante tres gráficos interactivos (serie temporal de cotización del jugador, distribución de mi cartera de tokens y evolución de mi patrimonio/saldo), para tomar decisiones informadas de inversión de manera clara e intuitiva.

**Why this priority**: Es el requerimiento principal de interfaz de usuario exigido por los docentes para la Entrega 2.

**Independent Test**: Renderizar las vistas de Detalle de Jugador y Dashboard de Portfolio con datos de prueba o del backend, verificando que los tres gráficos se dibujen correctamente, muestren leyendas/tooltips en español y respondan de forma responsiva.

**Acceptance Scenarios**:

1. **Gráfico 1 — Serie Temporal de Cotización Histórica del Jugador**:
   - **Given** un usuario visualizando el detalle de un jugador (`GET /players/{id}/quotes`),
   - **When** se carga la vista de detalle,
   - **Then** se renderiza un gráfico de líneas interactivo con las fechas en el eje horizontal y el valor en créditos en el eje vertical, mostrando en el tooltip el valor exacto y la versión de la estrategia utilizada en cada punto, además de un selector de fecha para consultar la cotización en un día puntual (`GET /players/{id}/quotes?date=YYYY-MM-DD`).

2. **Gráfico 2 — Distribución de Cartera (Portfolio Allocation)**:
   - **Given** un usuario autenticado con posiciones activas en su portfolio (`GET /users/{id}/portfolio`),
   - **When** ingresa a la vista de su Portfolio,
   - **Then** se muestra un gráfico de dona/torta que representa el porcentaje y valor en créditos que ocupa cada jugador dentro del valor total de su cartera de inversión.

3. **Gráfico 3 — Evolución del Patrimonio y Saldo del Usuario**:
   - **Given** un usuario con historial de transacciones (`GET /users/{id}/transactions`) y cartera activa,
   - **When** visualiza su resumen financiero en el Portfolio,
   - **Then** se renderiza un gráfico evolutivo que muestra la progresión de su patrimonio total (saldo líquido en créditos + valoración de tokens) a lo largo de sus operaciones.

4. **Estado Vacío (Empty State) en Gráficos de Usuario Nuevo**:
   - **Given** un usuario recién registrado sin tokens ni transacciones previas,
   - **When** accede a la vista de Portfolio,
   - **Then** la interfaz muestra un estado vacío amigable en español invitándolo a explorar el catálogo de jugadores, sin producir errores de renderizado ni gráficos rotos.

---

### User Story 2 - Operación Interactiva de Compra y Venta de Tokens (Priority: P1)

Como usuario autenticado, quiero contar con botones de compra y venta de tokens tanto en la ficha del jugador como en mi portfolio, con un modal o formulario interactivo que calcule el monto total en tiempo real, valide la cantidad ingresada y actualice mis datos financieros inmediatamente tras confirmar la transacción.

**Why this priority**: Permite al usuario ejecutar las operaciones centrales del mercado de tokens desde la interfaz gráfica.

**Independent Test**: Hacer clic en "Comprar Tokens" o "Vender Tokens", ingresar una cantidad válida, enviar la orden (`POST /orders/buy` o `POST /orders/sell`) y comprobar que se muestre confirmación en español y se refresquen el saldo, la tenencia de tokens y los gráficos.

**Acceptance Scenarios**:

1. **Flujo de Compra Exitoso desde la Interfaz**:
   - **Given** un usuario autenticado en la vista de un jugador con cotización vigente de `5.0` créditos,
   - **When** presiona "Comprar", ingresa `3` tokens (visualizando el costo estimado de `15.0` créditos) y confirma la operación,
   - **Then** el frontend invoca `POST /orders/buy`, muestra una notificación de éxito en español y actualiza de inmediato el saldo de créditos, los tokens disponibles del jugador y la posición del usuario.

2. **Flujo de Venta Exitoso desde la Interfaz**:
   - **Given** un usuario que posee `5` tokens de un jugador,
   - **When** presiona "Vender", selecciona `2` tokens y confirma,
   - **Then** el frontend invoca `POST /orders/sell`, informa el éxito de la venta en español y actualiza el saldo y la cartera del usuario.

3. **Manejo Visual de Errores de Negocio por Código Estable**:
   - **Given** una orden rechazada por el backend (e.g. código `INSUFFICIENT_FUNDS`, `INSUFFICIENT_TOKENS_AVAILABLE` o `INSUFFICIENT_TOKEN_BALANCE`),
   - **When** el cliente HTTP recibe la respuesta de error `ProblemDetail`,
   - **Then** el frontend identifica el error por su **código de error** y presenta el mensaje comprensible en español al usuario sin cerrar abruptamente su contexto ni exponer trazas técnicas.

---

### User Story 3 - Feature Adicional (Frontend): Alertas de Precios en Tiempo Real y Compra Directa (One-Click Trade) (Priority: P1)

Como usuario inversor, quiero crear alertas de precio sobre jugadores desde la interfaz web y recibir una notificación emergente en vivo en mi pantalla (vía SSE o WebSocket) en el instante en que el precio de un jugador cruce mi umbral configurado, contando con un botón de acción directa en la propia notificación para comprar o vender tokens al instante.

**Why this priority**: Materializa en la interfaz de usuario el **Feature Adicional de la Entrega 2**, brindando una demostración visual de alto impacto para la defensa del trabajo práctico.

**Independent Test**: Crear una alerta desde el formulario de alertas de un jugador, simular la llegada de un evento en el stream `/alerts/stream`, verificar que aparezca el toast/banner interactivo en tiempo real y que al pulsar "Operar ahora" se abra el modal de compra/venta preconfigurado con ese jugador.

**Acceptance Scenarios**:

1. **Configuración de Alerta desde la Ficha del Jugador**:
   - **Given** la vista de detalle de un jugador,
   - **When** el usuario hace clic en "Crear Alerta", elige la condición ("Suba a" / "Baje a"), ingresa el valor objetivo en créditos y guarda,
   - **Then** el frontend envía `POST /alerts` y agrega la alerta a la lista de alertas activas del usuario.

2. **Recepción de Notificación Push en Tiempo Real**:
   - **Given** el usuario navegando en cualquier pantalla de la aplicación con conexión activa al canal `/alerts/stream`,
   - **When** el backend emite el evento de disparo de alerta tras un cambio de cotización,
   - **Then** el frontend muestra en menos de 2 segundos una notificación emergente destacada en español (e.g. "¡Alerta de Precio! Kylian Mbappé alcanzó los 14.50 créditos") junto con un contador en la campana de notificaciones.

3. **Compra/Venta Rápida desde la Notificación (One-Click Trade)**:
   - **Given** la notificación en tiempo real visible en pantalla,
   - **When** el usuario hace clic en el botón de acción rápida "Comprar / Vender ahora",
   - **Then** se abre directamente el modal de transacción para ese jugador con su nueva cotización cargada, listo para confirmar la operación en un solo paso.

---

### User Story 4 - Vistas de Portfolio, Historial de Operaciones y Ranking de Jugadores (Priority: P2)

Como usuario autenticado, quiero navegar entre mis vistas de Portfolio, Historial de Operaciones y Ranking de Jugadores para consultar el detalle tabular de mis posiciones, mi rentabilidad acumulada, todas mis transacciones históricas y los jugadores más valiosos del mercado.

**Why this priority**: Completa todas las pantallas requeridas por la consigna oficial para la operación integral del usuario.

**Independent Test**: Navegar a `/portfolio`, `/transactions` y `/ranking` en la aplicación React y verificar que cada tabla muestre las columnas requeridas con formato numérico y monetario claro en español.

**Acceptance Scenarios**:

1. **Tabla de Posiciones en Portfolio**:
   - **Given** la vista de Portfolio del usuario,
   - **When** se cargan los datos de `GET /users/{id}/portfolio`,
   - **Then** se listan los jugadores en cartera con: nombre, cantidad de tokens, precio promedio de compra, cotización actual, valor actual total, ganancia/pérdida (en verde si es positiva, en rojo si es negativa) y botones rápidos de compra/venta.

2. **Tabla de Historial de Operaciones**:
   - **Given** la vista de Historial de Operaciones (`GET /users/{id}/transactions`),
   - **When** el usuario consulta sus movimientos,
   - **Then** visualiza el listado cronológico con fecha/hora formateada, tipo de operación (badge de `COMPRA` o `VENTA`), jugador, cantidad de tokens, precio unitario y monto total.

3. **Vista de Ranking de Jugadores**:
   - **Given** la pantalla de Ranking (`GET /players/ranking`),
   - **When** el usuario accede a la sección,
   - **Then** visualiza los jugadores ordenados de mayor a menor cotización según la estrategia activa, con acceso directo a la ficha y compra de cada jugador.

---

### User Story 5 - Estandarización de Servicios HTTP sin `/v1` y Tests en Vitest (Priority: P2)

Como equipo de desarrollo frontend, quiero actualizar todos los servicios de comunicación HTTP para consumir los endpoints sin el prefijo `/v1` y contar con tests automatizados en Vitest para los nuevos componentes, gráficos y flujos de mercado.

**Why this priority**: Asegura la sincronización de contratos con el backend depurado y mantiene la calidad del código frontend.

**Independent Test**: Ejecutar `npm test` (Vitest) verificando que todas las pruebas unitarias y de componentes pasen exitosamente y que las llamadas de red apunten a `/players`, `/orders/buy`, `/orders/sell`, `/quotes/recalculate`, `/users/{id}/portfolio`, `/users/{id}/transactions` y `/alerts`.

**Acceptance Scenarios**:

1. **Alineación de Rutas sin `/v1`**:
   - **Given** la capa de servicios Axios del frontend,
   - **When** realiza peticiones hacia el backend,
   - **Then** utiliza las rutas estandarizadas sin `/v1` conforme a la Consigna Oficial y la Spec 004.

---

### Edge Cases

- **Desconexión temporal del stream de notificaciones:** El cliente SSE/WebSocket en React debe implementar reconexión automática transparente si se cae momentáneamente la red, y sincronizar el estado de alertas al reconectar (`GET /alerts`).
- **Prevención de doble clic en órdenes de compra/venta:** Mientras una petición `POST /orders/buy` o `POST /orders/sell` está en vuelo (*loading*), el botón de confirmación debe deshabilitarse para evitar envíos duplicados accidentales.
- **Validación de entradas en cliente:** El formulario de compra/venta debe impedir ingresar cantidades negativas, cero, decimales o superiores al máximo disponible antes de enviar la petición al servidor.
- **Jugador con una única cotización histórica:** El gráfico de serie temporal debe renderizar correctamente el punto único sin fallar al trazar la línea.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El frontend DEBE implementar los 3 gráficos interactivos de datos de usuario y mercado: (1) Serie temporal de cotización histórica del jugador, (2) Gráfico de dona/torta de distribución de cartera, y (3) Gráfico de evolución del patrimonio y saldo del usuario.
- **FR-002**: El frontend DEBE permitir consultar la cotización de un jugador para una fecha específica mediante un selector de fecha en su vista de detalle (`GET /players/{id}/quotes?date=YYYY-MM-DD`).
- **FR-003**: El frontend DEBE proveer botones e interfaz modal/formulario para comprar (`POST /orders/buy`) y vender (`POST /orders/sell`) tokens, mostrando el cálculo previo del total en créditos y actualizando reactivamente el estado financiero del usuario.
- **FR-004**: El frontend DEBE mostrar la vista de Portfolio (`GET /users/{id}/portfolio`) detallando cantidad de tokens, precio promedio de compra, cotización actual, valor total y ganancia/pérdida (PnL).
- **FR-005**: El frontend DEBE mostrar la vista de Historial de Operaciones (`GET /users/{id}/transactions`) con todos los movimientos de compra y venta del usuario.
- **FR-006**: El frontend DEBE mostrar la vista de Ranking de Jugadores (`GET /players/ranking`) según la estrategia de valoración activa.
- **FR-007**: El frontend DEBE implementar la interfaz del Feature Adicional de Alertas de Precios (`POST /alerts`, `GET /alerts`, `DELETE /alerts/{id}`), conectándose en tiempo real al stream del backend (`/alerts/stream`) para desplegar notificaciones push instantáneas con botón de compra/venta rápida (*One-Click Trade*).
- **FR-008**: El frontend DEBE identificar y manejar los errores del backend mediante su **código de error estable** (y no comparando textos), mostrando siempre mensajes claros en **español**.
- **FR-009**: El frontend DEBE consumir todos los endpoints REST prescindiendo del prefijo `/v1` y mantener cobertura de pruebas de componentes y servicios con Vitest.
- **FR-010**: El frontend NO DEBE implementar ni duplicar reglas de negocio o cálculos de valuación que correspondan al backend.

### Key Entities (Frontend View Models / DTOs)

- **PlayerQuotePoint**: Punto de serie temporal con fecha, valor en créditos, nombre y versión de estrategia para el gráfico de cotizaciones.
- **PortfolioSummaryDto**: Resumen financiero del usuario con saldo disponible, valor total en tokens, PnL global y lista de posiciones por jugador.
- **TransactionItemDto**: Registro visual de operación con fecha, tipo (`COMPRA` / `VENTA`), jugador, tokens, precio unitario y total.
- **PriceAlertNotificationDto**: Evento recibido en tiempo real cuando se dispara una alerta, conteniendo datos del jugador, precio alcanzado, umbral y enlace de acción rápida.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los 3 gráficos financieros se renderizan sin errores en menos de 1 segundo tras recibir los datos del backend y se actualizan automáticamente luego de cada compra o venta.
- **SC-002**: Un usuario puede completar una operación de compra o venta (incluyendo desde una notificación de alerta en tiempo real) en un máximo de 3 clics.
- **SC-003**: Las notificaciones de alertas disparadas aparecen en la interfaz del usuario en menos de 2 segundos desde que el backend emite el evento.
- **SC-004**: El 100% de las suites de tests en Vitest pasan en estado SUCCESS y la interfaz mantiene un diseño responsivo en resoluciones de escritorio y dispositivos móviles.

---

## Assumptions

- **Separación Atómica Front/Back:** Esta especificación abarca únicamente la capa de presentación y cliente React (`front/`). Todos los endpoints y eventos en tiempo real son provistos por el backend especificado en `004-entrega2-core`.
- **Librería de Gráficos:** Se utilizará una librería de gráficos compatible con React y TypeScript (como `Recharts` o `Chart.js`) estilizada en armonía con Tailwind CSS.
