# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 2: Frontend Visualization, Trading & Real-Time Alerts

**Feature Branch**: `feature/front2`
**Feature Directory**: `specs/005-entrega2-frontend`
**Backend de referencia / rama base declarada**: `004-entrega2-core`
**Created**: 2026-10-12  
**Status**: Draft  
**Scope**: **Exclusivamente Frontend** (React con JavaScript y Bootstrap, dashboard financiero, gráficos, compra/venta, detalle de inversión personal, portfolio, historial, ranking y alertas). Reutiliza la aplicación existente y depende de los contratos previstos en la [spec 004](../004-entrega2-core/spec.md), cuya documentación no demuestra disponibilidad. No implementa lógica de negocio propia del servidor.
**Google Docs Oficial del Proyecto**: [Documento de Cátedra y SDD](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk) (ID: `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`)  
**Consigna Oficial (Solo Lectura)**: [Documento de Visión](https://docs.google.com/document/d/12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8/edit?usp=sharing) (ID: `12zE46_K98iAfxZiJKpyJeko_F7HHjIDUKw6-9FB0zV8`)  
**Input**: Adaptar el frontend existente para Entrega 2: dashboard inicial con tres gráficos, separación entre mercado e inversión personal, operaciones confirmadas, portfolio, historial, ranking y alertas. Conservar JavaScript, Bootstrap y Vitest; coordinar la futura eliminación de `/v1` con backend y proxy.

---

## Directivas de Cátedra — Alcance de Frontend para Entrega 2

Conforme a las directivas docentes para la Entrega 2:
1. **Requerimiento Nuevo Central 1:** Agregar **visualización de datos de usuario con gráficos en el front** (evolución de cotización del jugador, distribución de cartera y evolución patrimonial/saldo).
2. **Requerimiento Nuevo Central 2 (Feature Adicional en Frontend):** Interfaz de gestión de **Alertas de Precios** y recepción de **Notificaciones en Tiempo Real** (vía WebSockets / SSE) con botón de acción rápida (*One-Click Trade*) para comprar o vender tokens en vivo sin cambiar de pantalla.
3. **Funcionalidades Core de Mercado en Frontend:** Botones de compra/venta de tokens, visualización de cotización actual y a fecha dada, portfolio con ganancias/pérdidas (PnL), historial de operaciones y ranking de jugadores. La eliminación de `/v1` sigue siendo el objetivo del proyecto, condicionado al acuerdo y disponibilidad en backend y proxy; no se realizará unilateralmente desde frontend.

### Estado auditado y lectura de los escenarios

La inspección del código encontró autenticación y catálogo/detalle bajo `/api/v1`, JWT con `userId`, servicios Axios, React Router y pruebas Vitest. No encontró implementaciones de cotizaciones, órdenes, portfolio, transacciones, ranking financiero ni alertas. La columna actual «Ranking» del catálogo es una enumeración de filas.

Las rutas financieras citadas a continuación son **referencias documentadas en 004, pendientes de contrato e implementación verificada**, no APIs disponibles. Los escenarios describen comportamiento objetivo, no pruebas ejecutadas. Los datos de prueba deberán estar identificados como tales y no constituirán evidencia de integración real. Las dependencias pendientes no eliminan funcionalidades de la Entrega 2.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visualización de Datos Financieros y de Usuario con 3 Gráficos Interactivos (Priority: P1)

Como usuario inversor en la aplicación web, quiero visualizar mis datos financieros y la evolución de los jugadores mediante tres gráficos interactivos (serie temporal de cotización del jugador, distribución de mi cartera de tokens y evolución de mi patrimonio/saldo), para tomar decisiones informadas de inversión de manera clara e intuitiva.

**Why this priority**: Es el requerimiento principal de interfaz de usuario exigido por los docentes para la Entrega 2.

**Independent Test**: Renderizar Inicio, Detalle de Jugador y Portfolio con fixtures identificadas como prueba, y luego con datos del backend cuando estén disponibles; verificar gráficos, títulos, leyendas/tooltips en español y presentación responsive, sin confundir ambas verificaciones.

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
   - **Given** un usuario para el cual el backend entrega una serie temporal suficiente de patrimonio y saldo mediante un contrato acordado,
   - **When** visualiza su resumen financiero en el Portfolio,
   - **Then** se renderiza un gráfico con series diferenciadas de patrimonio total y saldo disponible en los períodos respaldados por esos datos, sin reconstruir valoraciones históricas desde transacciones y posiciones actuales.

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
   - **Then** el frontend envía la orden según el contrato de compra acordado, espera la confirmación del backend, muestra éxito en español y solicita los datos actualizados de saldo, disponibilidad y posición; el total previo es estimado, no el importe de ejecución garantizado.

2. **Flujo de Venta Exitoso desde la Interfaz**:
   - **Given** un usuario que posee `5` tokens de un jugador,
   - **When** presiona "Vender", selecciona `2` tokens y confirma,
   - **Then** el frontend envía la orden según el contrato de venta acordado y solo después de la confirmación del backend informa éxito y solicita el saldo y la cartera actualizados.

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
   - **Then** el frontend envía la solicitud conforme al contrato acordado de `POST /alerts` y agrega la alerta a la lista del usuario solo tras la confirmación del backend.

2. **Recepción de Notificación Push en Tiempo Real**:
   - **Given** el usuario navegando en cualquier pantalla de la aplicación con conexión activa al canal `/alerts/stream`,
   - **When** el cliente recibe un evento válido de disparo de alerta conforme al contrato acordado,
   - **Then** el frontend muestra en menos de 2 segundos desde esa recepción una notificación destacada en español con el jugador y precio recibidos y actualiza el contador de notificaciones. La latencia desde la emisión backend queda pendiente del contrato de transporte.

3. **Compra/Venta Rápida desde la Notificación (One-Click Trade)**:
   - **Given** la notificación en tiempo real visible en pantalla,
   - **When** el usuario hace clic en el botón de acción rápida "Comprar / Vender ahora",
   - **Then** se abre el modal compartido para ese jugador, se consulta su cotización vigente y el usuario ingresa o revisa la cantidad y confirma explícitamente; el precio del evento no se asume vigente y abrir la notificación nunca envía una orden. Si falla la consulta, se informa el error y no se habilita la confirmación con datos desconocidos.

4. **Consulta y eliminación de alertas**:
   - **Given** una sesión autenticada y contratos de consulta y eliminación acordados,
   - **When** el usuario consulta sus alertas y solicita eliminar una,
   - **Then** la lista refleja los datos del backend y la eliminación se muestra como exitosa solo después de su confirmación; si falla, se conserva el contexto y se informa el error en español.

5. **Desconexión y recuperación del canal**:
   - **Given** un canal de notificaciones autenticado cuyo transporte y recuperación están acordados,
   - **When** se desconecta y luego se recupera,
   - **Then** se informa su estado sin bloquear otras pantallas, se reconecta y sincroniza según ese contrato, sin asumir recuperación de eventos que el backend no garantice; al cerrar sesión se abandona el canal anterior.

---

### User Story 4 - Vistas de Portfolio, Historial de Operaciones y Ranking de Jugadores (Priority: P2)

Como usuario autenticado, quiero navegar entre mis vistas de Portfolio, Historial de Operaciones y Ranking de Jugadores para consultar el detalle tabular de mis posiciones, mi rentabilidad acumulada, todas mis transacciones históricas y los jugadores más valiosos del mercado.

**Why this priority**: Completa todas las pantallas requeridas por la consigna oficial para la operación integral del usuario.

**Independent Test**: Navegar a `/portfolio`, `/transactions` y `/ranking` en la aplicación React y verificar que cada tabla muestre las columnas requeridas con formato numérico y monetario claro en español.

**Acceptance Scenarios**:

1. **Tabla de Posiciones en Portfolio**:
   - **Given** la vista de Portfolio del usuario,
   - **When** se cargan los datos de `GET /users/{id}/portfolio`,
   - **Then** se listan los jugadores en cartera con: nombre, cantidad de tokens, precio promedio de compra, cotización actual, valor actual total, ganancia/pérdida (signo y texto, además de verde si es positiva o rojo si es negativa), acceso al detalle y botones rápidos de compra/venta. El resumen distingue patrimonio total y saldo; los gráficos de distribución y evolución se reutilizan desde Inicio.

2. **Tabla de Historial de Operaciones**:
   - **Given** la vista de Historial de Operaciones (`GET /users/{id}/transactions`),
   - **When** el usuario consulta sus movimientos,
   - **Then** visualiza el listado cronológico con fecha/hora formateada, tipo de operación (badge de `COMPRA` o `VENTA`), jugador, cantidad de tokens, precio unitario y monto total conforme al contrato, con acceso al detalle del jugador y sin inventar campos ausentes.

3. **Vista de Ranking de Jugadores**:
   - **Given** la pantalla de Ranking (`GET /players/ranking`),
   - **When** el usuario accede a la sección,
   - **Then** visualiza los jugadores ordenados de mayor a menor cotización según la estrategia activa, con acceso directo a la ficha y compra de cada jugador.

---

### User Story 5 - Compatibilidad de Servicios HTTP y Tests en Vitest (Priority: P2)

Como equipo de desarrollo frontend, quiero conservar la compatibilidad con la API disponible, coordinar la futura eliminación de `/v1` y mantener tests en Vitest para componentes, gráficos y flujos de mercado.

**Why this priority**: Evita romper autenticación y catálogo por una migración unilateral y mantiene la calidad del frontend.

**Independent Test**: Ejecutar `npm test` con la configuración existente y comprobar que los servicios usan la base y rutas acordadas para cada integración. Las pruebas simuladas no acreditan disponibilidad backend. `/quotes/recalculate` permanece como responsabilidad backend de 004; su presencia documental no agrega una acción de usuario a esta feature.

**Acceptance Scenarios**:

1. **Conservación de rutas actuales**:
   - **Given** que autenticación y catálogo siguen publicados bajo `/api/v1`,
   - **When** el frontend realiza esas consultas,
   - **Then** conserva su compatibilidad sin sustituir unilateralmente la base de la API.

2. **Migración coordinada**:
   - **Given** un acuerdo verificado de rutas sin `/v1` entre backend, cliente y proxy,
   - **When** se habilita esa migración,
   - **Then** se comprueban autenticación, catálogo y los contratos financieros disponibles contra las rutas acordadas, sin deducir la base HTTP a partir de las rutas de navegación.

---

### User Story 6 - Inicio Financiero e Inversión Personal Segura (Priority: P1)

Como usuario autenticado, quiero distinguir el mercado de mi inversión y navegar desde un resumen financiero sin ver datos pertenecientes a otra sesión.

**Why this priority**: Evita confundir precios, duplicar importes o exponer posiciones personales ajenas.

**Independent Test**: Usar fixtures identificadas de dos usuarios y un jugador, controlar respuestas demoradas y fallidas y recorrer las vistas por teclado; la integración real se verifica por separado cuando existan contratos.

**Acceptance Scenarios**:

1. **Inicio financiero y catálogo independiente (FR-011)**:
   - **Given** un inicio de sesión exitoso,
   - **When** se accede a `/home`,
   - **Then** se muestra el resumen financiero y las tres áreas de gráficos, cada una con su estado, y el catálogo se encuentra en `/players`. La serie de cotizaciones identifica el jugador seleccionado, permite cambiarlo y acceder a su detalle; solo ofrece períodos respaldados por datos disponibles.

2. **Cotización distinta del precio promedio (FR-012)**:
   - **Given** datos de prueba con cotización de mercado de 3,5 créditos y precio promedio personal de 2 créditos,
   - **When** se consulta el detalle del jugador,
   - **Then** «Mercado» muestra la cotización, información disponible, histórico y acciones, mientras «Mi inversión» muestra por separado promedio, cantidad, valor actual y PnL recibidos para el usuario autenticado, sin intercambiar esos importes.

3. **Usuario sin tokens (FR-012, FR-015)**:
   - **Given** una consulta personal exitosa que confirma ausencia de posición,
   - **When** se muestra el detalle,
   - **Then** se explica que el usuario no posee tokens y se ofrece comprar; no se inventa un precio promedio ni una rentabilidad y no se habilita vender una tenencia inexistente.

4. **Error al consultar la posición (FR-012, FR-015)**:
   - **Given** un fallo en la consulta de la posición personal,
   - **When** se muestra el detalle,
   - **Then** la sección personal informa el error y ofrece reintento sin afirmar que el usuario no tiene tokens ni reemplazar valores desconocidos por cero; el mercado disponible puede seguir visible.

5. **Cambio o cierre de sesión con consultas pendientes (FR-013)**:
   - **Given** datos y consultas personales pendientes del usuario A,
   - **When** se cierra su sesión o inicia la del usuario B y llega una respuesta anterior,
   - **Then** se descartan los datos de A y esa respuesta no se presenta ni actualiza la nueva sesión; las nuevas consultas se vinculan exclusivamente a B y no se aceptan identificadores de otro usuario como autorización.

6. **Definiciones sin doble contabilización (FR-014)**:
   - **Given** una respuesta de prueba contractual con saldo de 50, cartera actual de 35, patrimonio de 85 y PnL de 15 créditos,
   - **When** se muestra el resumen,
   - **Then** se identifican esos conceptos y se presenta patrimonio de 85, no 100; costo de adquisición y PnL mantienen la definición backend. Si un concepto no está definido o informado, se señala su indisponibilidad en lugar de calcularlo con una fórmula inventada.

7. **Histórico insuficiente (FR-001, FR-015)**:
   - **Given** datos históricos ausentes, un único punto o falta de un período comparable,
   - **When** se muestran gráficos y variación porcentual,
   - **Then** se distingue ausencia de datos de error, se representa el punto único cuando exista y no se inventan líneas, períodos ni variaciones. La falta de serie patrimonial se informa aunque existan transacciones; toda variación mostrada identifica el período y usa datos comparables según contrato.

8. **Estados por pantalla (FR-015)**:
   - **Given** cada una de las vistas dashboard, catálogo, portfolio, historial y ranking,
   - **When** sus consultas están pendientes, fallan, devuelven una colección vacía o no suministran datos requeridos,
   - **Then** cada vista muestra, respectivamente, carga, error con reintento, vacío explicativo o dato no disponible; no sustituye ninguno por cero ni presenta información anterior como actual. Un error parcial del dashboard no oculta las secciones que sí pudieron cargarse.

9. **Navegación e identidad (FR-016)**:
   - **Given** la aplicación en anchos de 360 y 1280 píxeles,
   - **When** se recorre con teclado el menú, controles y modal de operaciones,
   - **Then** todas las secciones objetivo son accesibles, el foco es visible, el modal permite cierre y devolución del foco al disparador, y las tablas pueden desplazarse sin ocultar acciones; se conservan logo, azul oscuro, blanco y estilos Bootstrap existentes.

10. **Orden confirmada y fallo de refresco (FR-017)**:
    - **Given** una orden confirmada por el backend,
    - **When** falla la posterior consulta de saldo, posición, historial o gráficos afectados,
    - **Then** se conserva el resultado exitoso de la orden, se avisa que los datos no pudieron actualizarse y el reintento consulta solamente esos datos, sin volver a enviar la operación.

11. **Prevención de órdenes duplicadas (FR-003, FR-017)**:
    - **Given** una orden en envío,
    - **When** el usuario repite la confirmación mediante clic o teclado,
    - **Then** no se envía una segunda orden y no se anuncia éxito antes de recibir confirmación backend; una respuesta incierta no dispara un reenvío automático.

---

### Edge Cases

- **Desconexión temporal del canal:** Reconectar y sincronizar según el contrato que se acuerde, informando estado y sin bloquear otras vistas. No elegir SSE/WebSocket ni garantizar reproducción de eventos sin respaldo contractual.
- **Prevención de doble clic en órdenes de compra/venta:** Mientras una petición `POST /orders/buy` o `POST /orders/sell` está en vuelo (*loading*), el botón de confirmación debe deshabilitarse para evitar envíos duplicados accidentales.
- **Validación de entradas en cliente:** Impedir cantidades negativas, cero y decimales; comprobar límites cuando el backend los informe, sin tratar un límite desconocido como cero. La disponibilidad, saldo y tenencia se validan definitivamente en backend.
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
- **FR-009**: El frontend DEBE mantener compatibilidad con las rutas actuales `/api/v1` hasta acordar y verificar su migración con backend y proxy. La eliminación de `/v1` continúa como objetivo, sin cambios unilaterales. DEBE conservar y ampliar las pruebas de componentes y servicios con Vitest y su configuración existente.
- **FR-010**: El frontend NO DEBE implementar ni duplicar reglas de negocio o cálculos de valuación que correspondan al backend.
- **FR-011**: Después del login, `/home` DEBE presentar el resumen financiero y los tres gráficos obligatorios con sus estados correspondientes. El catálogo DEBE permanecer en `/players`; el gráfico de cotizaciones DEBE identificar el jugador consultado y permitir acceder a su detalle.
- **FR-012**: El detalle DEBE distinguir «Mercado» de «Mi inversión»: cotización actual e histórico del jugador frente a cantidad de tokens, precio promedio de compra, valor actual y PnL de la posición autenticada. Una posición confirmada como inexistente DEBE mostrar un vacío explicativo y acción de compra; un fallo de consulta NO DEBE interpretarse como ausencia de posición.
- **FR-013**: Las consultas y datos personales DEBEN vincularse a la sesión autenticada. Al cerrar sesión o cambiar de usuario, se DEBEN descartar datos anteriores e impedir que respuestas pendientes o notificaciones de la sesión anterior actualicen o aparezcan en la nueva. La autorización y verificación de titularidad permanecen en backend.
- **FR-014**: La presentación DEBE diferenciar saldo disponible, valor actual de cartera, costo de adquisición, patrimonio total y PnL según las definiciones de esta spec y el contrato backend. NO DEBE sumar el PnL nuevamente al patrimonio ni inventar definiciones o cálculos faltantes.
- **FR-015**: Dashboard, catálogo, detalle, portfolio, historial, ranking y alertas DEBEN distinguir carga, error, colección vacía y dato no disponible. Un dato desconocido NO DEBE representarse como cero. Los gráficos DEBEN incluir títulos, leyendas y estados alternativos; no se crearán series ni variaciones sin datos suficientes.
- **FR-016**: La adaptación DEBE conservar React con JavaScript, Bootstrap, logo, identidad visual y componentes/servicios reutilizables. DEBE ofrecer la navegación objetivo, presentación responsive y uso por teclado con foco visible, sin incorporar TypeScript, Tailwind ni sustituir el framework visual. Las dependencias nuevas requieren aprobación previa.
- **FR-017**: Una operación DEBE mostrar éxito solo tras confirmación backend y solicitar la actualización de saldo, disponibilidad, posiciones, historial y gráficos afectados. Durante el envío se DEBEN impedir confirmaciones duplicadas. Si falla el refresco, se DEBE conservar la confirmación y permitir reintentar únicamente las consultas, sin reenviar la orden.

### Definiciones financieras para la presentación

| Concepto | Definición y límite |
|---|---|
| Saldo disponible | Créditos líquidos disponibles para operar, informados por backend. |
| Valor actual de cartera | Valoración vigente de las posiciones abiertas, conforme al backend. |
| Patrimonio total | Saldo disponible más valor actual de cartera, sin sumar nuevamente el PnL. Es una definición de presentación, no una implementación de valuación en React. |
| Costo de adquisición | Importe de adquisición según la definición contractual backend; no se deduce un método de cálculo o tratamiento de ventas. |
| PnL | Ganancia o pérdida según contrato backend; no se asume si incluye resultados realizados, no realizados o ambos. |

Precio promedio personal y cotización actual de mercado son conceptos distintos. «Valor invertido» no debe usarse como etiqueta ambigua para costo y valor actual. Si falta una definición contractual o un dato, se registra la dependencia y se muestra indisponibilidad, sin inventar fórmulas. La estimación previa de una orden no reemplaza el precio ni total ejecutados por backend.

### Navegación objetivo

| Ruta frontend | Contenido |
|---|---|
| `/home` | Dashboard inicial, resumen financiero y tres gráficos; selección de jugador para cotizaciones. |
| `/players` | Catálogo existente con búsqueda, paginación, filtros por liga/equipo/posición y acceso al detalle; cotizaciones condicionadas a datos reales. |
| `/portfolio` | Posiciones personales, resumen y gráficos de cartera reutilizados desde Inicio. |
| `/transactions` | Historial personal y acceso al detalle del jugador. |
| `/ranking` | Ranking ordenado según datos y estrategia activa informados por backend. |
| `/alerts` | Gestión de alertas condicionada a su contrato. |

Operaciones se ofrece mediante un modal compartido desde las vistas pertinentes y las notificaciones, sin duplicar formularios ni crear una pantalla redundante. Las rutas frontend no prueban existencia de endpoints backend. Los filtros y consultas deben respetar las capacidades del catálogo disponible; no presuponen nuevos parámetros HTTP.

### Key Entities (Frontend View Models / DTOs)

Los nombres siguientes describen necesidades de presentación; **no son DTOs implementados ni contratos de red aprobados**. Sus campos y disponibilidad deben contrastarse con backend antes de integrar.

- **PlayerQuotePoint**: Punto de serie temporal con fecha, valor en créditos, nombre y versión de estrategia para el gráfico de cotizaciones.
- **PortfolioSummaryDto**: Resumen financiero del usuario con saldo disponible, valor total en tokens, PnL global y lista de posiciones por jugador.
- **TransactionItemDto**: Registro visual de operación con fecha, tipo (`COMPRA` / `VENTA`), jugador, tokens, precio unitario y total.
- **PriceAlertNotificationDto**: Evento recibido en tiempo real cuando se dispara una alerta, conteniendo datos del jugador, precio alcanzado, umbral y enlace de acción rápida.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con datos suficientes, cada uno de los tres gráficos se presenta sin errores en menos de 1 segundo desde la recepción de los datos completos. Sin datos suficientes se cumple US6.7, sin series ficticias. La medición documentará navegador, equipo, volumen de datos y resultados; fixtures identificadas permiten evaluar presentación, pero no acreditan integración real.
- **SC-002**: Para compra y venta desde detalle, portfolio y alerta, el usuario puede abrir el flujo compartido, ingresar o revisar cantidad y confirmar explícitamente sin cambiar de pantalla. Ninguna apertura o notificación envía una orden; la confirmación produce como máximo una solicitud mientras está pendiente. Sustituye la métrica ambigua de tres clics, que no contemplaba entrada de cantidad ni errores.
- **SC-003**: Un evento válido recibido por el cliente se presenta como notificación en menos de 2 segundos desde su recepción. La medición desde emisión backend requiere contrato de transporte y trazabilidad aún pendientes; no se declara satisfecha mediante simulación local.
- **SC-004**: El 100% de las suites de Vitest pasa con la configuración existente al validar la implementación. La navegación y las acciones se verifican por teclado y en anchos de 360 y 1280 píxeles conforme a US6.9, sin ocultar controles; se admite desplazamiento interno de tablas.
- **SC-005**: En los escenarios de cierre y cambio de sesión, ninguna respuesta ni notificación anterior muestra información personal en la sesión siguiente; las pruebas controlan respuestas demoradas y comprueban el descarte.
- **SC-006**: En cada escenario de orden confirmada y refresco fallido, reintentar no incrementa el número de órdenes enviadas y conserva el resultado confirmado, mostrando el estado de actualización pendiente.
- **SC-007**: Dashboard, catálogo, portfolio, historial y ranking distinguen los cuatro estados de US6.8; el detalle distingue mercado, posición vacía y error personal, y el resumen no contabiliza dos veces PnL.

Estos son objetivos de aceptación pendientes de ejecución para la nueva funcionalidad, no resultados verificados. Los datos de prueba se usan únicamente para pruebas o demostraciones identificadas. Los criterios conservan numeración consecutiva; la versión revisada tenía SC-001 a SC-004 completos, sin truncamiento comprobado.

---

## Assumptions

- **Separación Front/Back:** Esta feature abarca únicamente presentación y cliente en `front/`. La provisión de APIs y eventos corresponde a 004; su existencia se verificará antes de integrar, sin modificar backend desde esta tarea.
- **Stack y discrepancia abierta:** `front/package.json` y el código usan React, JavaScript, Bootstrap, Axios, React Router y Vitest. La Constitución y el contexto de cátedra mencionan TypeScript/Tailwind. La instrucción explícita para esta adaptación conserva el stack real; la contradicción documental sigue abierta, debe revisarse por la gobernanza del proyecto y no se declara conformidad constitucional total. No se modifica la Constitución, el contexto ni la consigna.
- **Librería de gráficos:** No se encontró una instalada. Recharts es solo candidata pendiente de evaluación y aprobación; esta especificación no aprueba instalación ni fija una dependencia.
- **Reutilización:** Conservar autenticación, servicios HTTP, catálogo, componentes, estilos e identidad visual. El render aprobado guía la presentación, pero no sustituye requisitos ni contratos; no se verificó fidelidad visual contra una imagen no aportada a la auditoría.
- **Aprobación:** La implementación frontend requiere aprobación posterior de su alcance, archivos, dependencias y pruebas. Esta actualización documental no genera plan ni tareas ni autoriza implementar.

### Dependencias pendientes y límites de integración

| ID | Dependencia | Estado y resolución requerida |
|---|---|---|
| DEP-001 | Cotizaciones actuales, históricas y por fecha | Rutas previstas en 004; no se encontró implementación. Acordar y verificar campos, fechas, estrategia/versiones y períodos comparables antes de integrar gráficos o variaciones. |
| DEP-002 | Órdenes de compra/venta | Contratos de entrada, resultado confirmado, precio ejecutado, límites y códigos de rechazo pendientes. Las reglas transaccionales y autorización pertenecen a backend. |
| DEP-003 | Portfolio e historial personales | Endpoints documentados, sin implementación encontrada. Confirmar campos, titularidad, definiciones de costo/PnL y resumen; el `userId` del JWT existente no reemplaza autorización del servidor. |
| DEP-004 | Serie histórica patrimonial | No está definido un contrato suficiente. Transacciones y posiciones actuales no equivalen a histórico de valoración; solicitar provisión backend y períodos respaldados, sin inventar un endpoint. |
| DEP-005 | Ranking | Verificar contrato, estrategia y orden recibido; no usar la numeración local del catálogo como ranking financiero. |
| DEP-006 | Alertas y canal | CRUD, eventos, autenticación, transporte, reconexión y recuperación pendientes. No elegir SSE/WebSocket ni asumir `/alerts/stream` operativo. |
| DEP-007 | Eliminación de `/v1` | Coordinar backend, cliente y proxy, incluida la base `/api`; conservar compatibilidad actual hasta verificar el cambio. |
| DEP-008 | Librería y datos de prueba | Biblioteca de gráficos pendiente de aprobación. Fixtures identificadas, basadas en contratos acordados para pruebas de integración; no equivalen a datos reales ni prueban disponibilidad. |

### Trazabilidad documental de aceptación

`USn.m` identifica el escenario m de User Story n. La tabla acredita ubicación documental, no ejecución.

| Requisitos | Escenarios |
|---|---|
| FR-001, FR-002 | US1.1–4; US6.1 y US6.7 |
| FR-003, FR-008 | US2.1–3; US6.11 |
| FR-004, FR-005, FR-006 | US4.1–3; US6.8 |
| FR-007 | US3.1–5 |
| FR-009 | US5.1–2; verificación Vitest definida en US5 |
| FR-010 | US1.3; US6.6–7; reglas y autorización backend en US2 y dependencias |
| FR-011 | US6.1 |
| FR-012 | US6.2–4 |
| FR-013 | US6.5; US3.5 |
| FR-014 | US6.6 |
| FR-015 | US1.4; US6.3–4 y US6.7–8; US3.4–5 |
| FR-016 | US6.9 y navegación objetivo; stack contrastado con package.json |
| FR-017 | US2.1–2; US6.10–11 |
