# Tasks: Entrega 2 — Adaptación del frontend financiero

**Input**: [plan.md](./plan.md), [spec.md](./spec.md) y [checklist](./checklists/requirements.md).
**Prerequisites**: Plan y spec existentes. Investigación, decisiones y validación están consolidadas en el plan; no existen contratos financieros propios confirmados en esta feature.
**Branch**: `feature/front2`
**Tests**: Vitest y Testing Library existentes, requeridos por la spec. Los tests de componentes acompañan A/B; D verifica integración real y aceptación final.
**Organization**: Cuatro fases A–D solicitadas, con subsecciones por historia y prioridades de la spec. Setup/fundación se incluyen en A y el pulido final en D. No reinicializar el proyecto.
**Estado**: Etapas 1 y 2 autorizadas el 2026-10-20: navegación, aislamiento de sesión y capa HTTP. T001–T004, T006 y T019–T021 completadas. T008 y T022–T024 conservan pendientes/prerrequisitos según el registro de ejecución. No se acredita disponibilidad de APIs financieras ni se agregan dependencias.

## Format: `[ID] [P?] [Story] Description`

- IDs consecutivos, casillas sin marcar y rutas relativas a la raíz del repositorio.
- `[P]` identifica trabajo en archivos distintos que puede ejecutarse en paralelo una vez satisfechos los prerrequisitos indicados; no significa que se puedan ignorar dependencias.
- `[US1]` gráficos P1; `[US2]` operaciones P1; `[US3]` alertas P1; `[US4]` portfolio/historial/ranking P2; `[US5]` compatibilidad HTTP/tests P2; `[US6]` inicio/inversión/privacidad P1.
- **BLOQUEADA** en la descripción impide ejecutar esa integración hasta registrar el contrato o aprobación señalado. No sustituir un bloqueo con una URL o respuesta supuesta.
- Rutas nuevas bajo `front/src/` son destinos de implementación futura, no archivos ya creados. Este comando genera únicamente `tasks.md`.

## Path Conventions y restricciones

Reutilizar `front/src/components/`, `pages/`, `hooks/`, `services/`, `utils/` y `test/`. React/JavaScript/Bootstrap; sin TypeScript ni Tailwind. Mantener Axios, React Router, configuración Vitest y `/api/v1` para los contratos actuales. Backend, Constitución, spec, plan y checklist son fuentes de lectura en esta generación; no modificarlos ni crear metadatos o ramas.

Se resolvió la plantilla `.specify/templates/tasks-template.md` y la feature mediante `Get-FeaturePathsEnv -NoPersist`. No se ejecutó `setup-tasks.ps1`, cuyo uso del directorio explícito persiste metadatos. No existe `.specify/extensions.yml`: no hay hooks previos/posteriores. Esta adaptación conserva formato, trazabilidad, pruebas independientes y dependencias de Spec-Kit sin generar archivos auxiliares.

### Regla de fixtures y mocks

En A se permiten fixtures de **presentación**, etiquetadas `SOLO PRUEBA`, dentro de `front/src/test/fixtures/`. Se inyectan como props o callbacks desde tests; no son DTOs de red, ni se diseñan endpoints simulados financieros. Mocks HTTP en B solo reproducen contratos ya confirmados. Los futuros mocks HTTP financieros de C deberán derivarse del contrato acordado y citarlo en los tests.

Ningún módulo productivo importará `src/test`, sustituirá una falla real por fixtures ni anunciará una operación exitosa por un callback simulado. Mientras falte integración, la aplicación mostrará estado no disponible y acciones que no envíen órdenes; solo los tests exploran estados financieros con datos explícitamente ficticios. No agregar servidor mock, Storybook, librería de estado o configuración de ejecución. Las pruebas de UI no satisfacen aceptación de integración.

## Fase A — Estructura, navegación y componentes sin nuevos endpoints

**Goal**: Entregar una base visual navegable y comprobable con la aplicación existente. Los gráficos completos requieren aprobación de biblioteca; aquí se preparan contenedores, estados y representación textual, no se declara cumplido FR-001.
**Independent Test**: Ejecutar tests de componentes con fixtures y callbacks controlados; autenticación y catálogo conservan sus contratos. Ninguna pantalla solicita una URL financiera no confirmada.

### Setup y fundación compartida

- [x] T001 Revisar la base de trabajo y registrar en la sección «Registro de ejecución» de `specs/005-entrega2-frontend/tasks.md` el estado previo, dependencias abiertas y comandos de validación aplicables a `front/package.json`; preservar cambios existentes y no recrear componentes, dependencias ni tests que ya existen.
- [x] T002 [P] Preparar fixtures de presentación identificadas como `SOLO PRUEBA` en `front/src/test/fixtures/financialPresentation.js` para vacío, dato desconocido, error, punto único y valores diferentes de mercado/posición; no definir JSON HTTP, fórmulas financieras ni importarlas desde producción. Depende de T001.

### US6 — Inicio, catálogo e inversión personal segura (P1)

- [x] T003 [US6] Extraer las rutas a `front/src/App.jsx`, mantener `front/src/main.jsx` como entrada y crear `front/src/components/MarketWorkspace.jsx`; trasladar el catálogo de `front/src/pages/Home.jsx` a `front/src/pages/PlayersPage.jsx`, conservar sus servicios/componentes y establecer login a `/home` en `front/src/pages/LoginPage.jsx`. Registrar las seis rutas protegidas con estados no disponibles para las páginas aún no desarrolladas, sin importaciones inexistentes. Depende de T001.
- [x] T004 [US6] Adaptar `front/src/components/DashboardLayout.jsx` y `front/src/components/index.js` con navegación activa a Inicio, Jugadores, Portfolio, Historial, Ranking y Alertas; mantener logo, identidad visual y modo preview, sin ruta redundante de Operaciones. Depende de T003.
- [ ] T005 [US6] Ampliar `front/src/hooks/usePlayerCatalog.js`, `front/src/pages/PlayersPage.jsx` y `front/src/components/PlayerTable.jsx` con filtros locales por liga/equipo/posición, reinicio de paginación y búsqueda existente; identificar la numeración de filas como enumeración, sin presentarla como ranking financiero ni inventar parámetros HTTP. Depende de T003.
- [x] T006 [US6] Aislar estado privado, selección y modal por sesión en `front/src/components/MarketWorkspace.jsx` y preparar `front/src/hooks/useSessionRequest.js` para cancelación y descarte por generación de consulta; comprobar el mecanismo con callbacks de prueba, sin crear servicios financieros. Limpiar al logout, cambio de sesión o jugador y evitar persistencia de datos personales. Depende de T003.
- [x] T007 [US6] Ampliar la presentación de `front/src/components/PlayerProfileDialog.jsx` y crear `front/src/components/PlayerInvestmentSummary.jsx` separando Mercado/Mi inversión; preservar estadísticas, consulta de detalle y distinguir posición vacía confirmada de error/no disponible. Inyectar datos personales únicamente desde el contenedor o tests, no desde una URL supuesta. Depende de T002 y T006.
- [ ] T008 [US6] Adaptar `front/src/test/auth.test.jsx`, `home.test.jsx`, `player-profile.test.jsx` y `player-table.test.jsx`, agregar `front/src/test/navigation.test.jsx` y `session-isolation.test.jsx` para rutas, catálogo extraído, filtros, precios distintos, estados y respuestas tardías de A/B; no afirmar titularidad backend a partir de estas pruebas. Depende de T004–T007.

### US1 — Estructura de los tres gráficos (P1)

- [x] T009 [P] [US1] Crear contenedores de presentación `front/src/components/charts/QuoteHistoryChart.jsx`, `PortfolioAllocationChart.jsx` y `WealthHistoryChart.jsx` con títulos, leyendas, estados y alternativa textual/tabular; aceptar datos de presentación sin adaptadores HTTP, no reconstruir históricos ni instalar una biblioteca. Depende de T002.
- [x] T010 [US1] Componer el dashboard en `front/src/pages/Home.jsx` con resumen, tres contenedores de gráficos y selección explícita de jugador desde el catálogo actual; ofrecer acceso al detalle y conservar no disponible donde falten datos reales. Depende de T004, T007 y T009.
- [x] T011 [US1] Crear `front/src/test/dashboard.test.jsx` y `chart-states.test.jsx` para ausencia de selección, carga, error parcial, vacío, no disponible, punto único y datos desconocidos distintos de cero mediante fixtures de presentación; no medir rendimiento ni dar por verificado el renderizador final. Depende de T010.

### US2 — Modal compartido de operaciones (P1)

- [ ] T012 [P] [US2] Crear `front/src/components/TradeDialog.jsx` reutilizando `FormField`/`FeedbackAlert` y Bootstrap: cantidad entera positiva, estimado recibido como dato de presentación, confirmación explícita, bloqueo de doble envío y estados de resultado/refresco separados. Usar callbacks inyectados; sin servicio HTTP, cálculo financiero no acordado o éxito automático. Depende de T002.
- [ ] T013 [US2] Crear `front/src/test/trade-dialog.test.jsx` con callbacks controlados para validación, límites conocidos/desconocidos, confirmación por teclado, doble clic y orden confirmada seguida de refresco fallido; un reintento de consulta no debe invocar el callback de orden. Depende de T012.

### US3 — Presentación de alertas (P1)

- [ ] T014 [P] [US3] Crear `front/src/pages/AlertsPage.jsx` y `front/src/components/PriceAlertNotifications.jsx` con estados, formularios y acciones mediante callbacks inyectados; usar `front/src/test/fixtures/financialPresentation.js` solo desde tests, sin conectar SSE/WebSocket ni inventar eventos de red. Depende de T002.
- [ ] T015 [US3] Crear `front/src/test/alert-presentation.test.jsx` que compruebe error, vacío, carga, estado desconectado y apertura del flujo compartido sin envío automático; distinguir props de una notificación visual de un payload backend aún desconocido. Depende de T012 y T014.

### US4 — Tablas y resumen de inversiones (P2)

- [x] T016 [US4] Crear `front/src/pages/PortfolioPage.jsx`, `TransactionsPage.jsx` y `RankingPage.jsx` como vistas de presentación con datos/callbacks inyectados y estados; reutilizar gráficos de cartera y mensajes, mostrar campos especificados sin inventar DTOs ni scores, y permitir seleccionar jugador. Depende de T007 y T009.
- [x] T017 [US4] Crear `front/src/test/financial-pages.test.jsx` con fixtures para estados de las tres vistas, inversión vacía, etiquetas financieras sin doble contabilización, orden recibido y selección de jugador; sin acreditar cálculos o APIs reales. Depende de T016.

### Cierre del incremento visual

- [ ] T018 Integrar las páginas y el modal disponibles en `front/src/components/MarketWorkspace.jsx` y `front/src/App.jsx`, ajustar `front/src/style.css` con Bootstrap existente y validar teclado/foco a 360/1280 px: no superponer detalle y operación, devolver foco al cierre y mantener acciones financieras no conectadas sin envío. Registrar revisión del incremento visual en `specs/005-entrega2-frontend/tasks.md`. Depende de T008, T011, T013, T015 y T017.

**Checkpoint A**: Estructura y componentes comprobables de forma aislada; catálogo conserva comportamiento real. No equivale a tres gráficos integrados, operaciones reales ni alertas funcionales.

## Fase B — Servicios y adaptadores sobre contratos existentes confirmados

**Goal**: Fortalecer la base HTTP, identidad y catálogo sin especular sobre contratos financieros.
**Contratos habilitantes**: AuthController/LoginResponseDto, PlayerController/PlayerDetailResponseDto, JwtTokenProvider y GlobalExceptionHandler bajo `back/src/main/java/com/cbo/players/`. La evidencia es inspección de código, no una prueba de disponibilidad de un despliegue. La diferencia entre OpenAPI antiguo y detalle con estadísticas queda registrada en el plan.
**Independent Test**: Vitest con respuestas basadas en esos DTOs, base `/api/v1` y Bearer; sin llamada financiera. B puede avanzar en paralelo con A cuando no comparte archivos.

### US5 — Compatibilidad HTTP y manejo de errores (P2, fundamento técnico)

- [x] T019 [P] [US5] Agregar `front/src/test/api-services.test.jsx` para los servicios existentes de autenticación y catálogo: rutas bajo la base actual, abort signal, timeout, errores de red, ProblemDetail con `errorCode` y respuestas de seguridad sin ese cuerpo; no simular contratos financieros. Depende de T001.
- [x] T020 [US5] Adaptar `front/src/services/api.js` y, si corresponde al flujo existente, `front/src/hooks/useApiRequest.js` para mensajes españoles por códigos realmente definidos en backend, fallback seguro y distinción de error/cancelación; conservar timeout, interceptor y base configurable sin copiar códigos de mercado aún no implementados. Depende de T019.

### US6 — Identidad de la sesión (P1)

- [x] T021 [P] [US6] Extender `front/src/services/session.js` y `front/src/services/auth.js` para leer `userId` del JWT existente con utilidades nativas, rehidratar sesiones guardadas y bloquear consultas personales si falta identidad válida; conservar «Recordarme», sin usar username como id ni tratar decodificación como autorización. Depende de T001.
- [ ] T022 [US6] Agregar `front/src/test/session.test.jsx` con tokens de prueba explícitos para rehidratación, expiración, identidad ausente y logout; integrar la identidad en `front/src/components/MarketWorkspace.jsx` y ampliar `front/src/test/session-isolation.test.jsx` para impedir actualizaciones de otra sesión. Depende de T006, T008 y T021.

### US5 — Catálogo compatible y regresión de servicios (P2)

- [ ] T023 [US5] Reutilizar `front/src/services/players.js` sin otro cliente HTTP y ajustar `front/src/hooks/usePlayerCatalog.js` y `front/src/components/PlayerProfileDialog.jsx` al array y detalle reales, estadísticas nullable, cancelación y manejo diferenciado de 401/403; no añadir consultas redundantes a estadísticas ni parámetros no aceptados. Depende de T005, T007 y T020.
- [ ] T024 [US5] Completar `front/src/test/api-services.test.jsx` y `front/src/test/player-profile.test.jsx` con catálogo/detalle reales documentados en código, errores y cancelación; verificar `front/vite.config.js` y `front/src/services/api.js` sin alterar configuración, `/api/v1`, proxy o contratos backend. Depende de T022 y T023.

**Checkpoint B**: Base actual comprobable con pruebas locales. No hay familia financiera autorizada para adaptadores HTTP en B al generar estas tareas. Un contrato nuevo debe pasar su gate de C, no incorporarse por analogía con una ruta de la spec.

## Fase C — Integraciones financieras condicionadas a contratos reales

**Goal**: Reemplazar las entradas de presentación de A por datos reales mediante adaptadores documentados, manteniendo los componentes. Cada capacidad se desbloquea por separado.
**Regla de entrada**: Antes de ejecutar cada tarea bloqueada, registrar en «Registro de ejecución» la referencia al contrato confirmado, responsable backend, URL real, formato de entrada/salida, errores y evidencia de implementación/disponibilidad. Si el contrato está confirmado pero el endpoint aún no responde, se pueden preparar sus adaptadores/tests contractuales; la conexión y aceptación real permanecen bloqueadas. No marcar la tarea completa si incluye integración no disponible.

### US1 — Cotizaciones y renderizadores (P1)

- [ ] T025 [US1] BLOQUEADA DEP-001: crear `front/src/services/quotes.js` y `front/src/test/quotes-service.test.jsx` desde el contrato confirmado de cotización actual, histórica y fecha, con campos/precisión/fechas/estrategia reales y respuestas sin datos; no deducir URLs de la spec ni introducir fórmulas de variación no acordadas. Depende de T024 y del gate DEP-001.
- [ ] T026 [US1] BLOQUEADA DEP-008, no por backend: evaluar y obtener aprobación de la biblioteca de gráficos compatible con React/JS/Bootstrap, registrar elección en `specs/005-entrega2-frontend/tasks.md` y solo entonces adaptar `front/src/components/charts/QuoteHistoryChart.jsx`, `PortfolioAllocationChart.jsx`, `WealthHistoryChart.jsx` y `front/src/test/chart-states.test.jsx` al renderizador. Cambios en `front/package.json` y `front/package-lock.json` requieren esa aprobación explícita; sin TypeScript, Tailwind o nueva configuración de tests. Depende de T009 y T011; puede avanzar con fixtures de presentación sin endpoints.
- [ ] T027 [US1] BLOQUEADA DEP-001: crear `front/src/hooks/usePlayerQuotes.js` y conectar `front/src/pages/Home.jsx` y `front/src/components/PlayerProfileDialog.jsx` a cotizaciones/fecha/selección, usando el renderizador y períodos respaldados por datos reales; abortar solicitudes anteriores y conservar estados. Depende de T018, T025 y T026, y endpoint disponible.

### US4 — Posiciones personales (P2; dependencia de US1/US2)

- [ ] T028 [US4] BLOQUEADA DEP-003: crear `front/src/services/portfolio.js` y `front/src/test/portfolio-service.test.jsx` a partir del contrato real de posiciones/resumen y titularidad, sin calcular costo, PnL o patrimonio para cubrir campos faltantes. Depende de T024 y del gate DEP-003.
- [ ] T029 [US4] BLOQUEADA DEP-003: crear `front/src/hooks/usePortfolio.js` y conectar `front/src/pages/PortfolioPage.jsx`, `front/src/components/PlayerInvestmentSummary.jsx` y `front/src/components/MarketWorkspace.jsx` con datos de la sesión, error distinto de posición vacía y descarte de respuestas tardías. Depende de T018, T028 y endpoint disponible.

### US1 — Gráficos personales (P1)

- [ ] T030 [US1] BLOQUEADA DEP-003: conectar distribución de cartera en `front/src/pages/Home.jsx`, `front/src/pages/PortfolioPage.jsx` y `front/src/components/charts/PortfolioAllocationChart.jsx` con valores actuales completos del contrato, no con conteo de tokens; tratar cartera vacía y total no informado según contrato. Depende de T026 y T029.
- [ ] T031 [US1] BLOQUEADA DEP-004: crear `front/src/services/wealthHistory.js`, `front/src/hooks/useWealthHistory.js` y `front/src/test/wealth-history.test.jsx` solo después de confirmar una fuente histórica suficiente; documentar el mapeo real en el test y no reconstruir historia desde transacciones ni inventar endpoint. Depende de T024 y del gate DEP-004.
- [ ] T032 [US1] BLOQUEADA DEP-004: conectar `front/src/components/charts/WealthHistoryChart.jsx`, `front/src/pages/Home.jsx` y `front/src/pages/PortfolioPage.jsx` a patrimonio/saldo y períodos reales; probar ausencia de serie y punto único sin interpolación ficticia. Depende de T026, T029, T031 y fuente histórica disponible.

### US2 — Operaciones confirmadas y refresco (P1)

- [ ] T033 [US2] BLOQUEADA DEP-002: crear `front/src/services/orders.js` y `front/src/test/orders-service.test.jsx` con el contrato confirmado de compra/venta, rechazo y confirmación; respetar precisión acordada y prohibir reenvío automático ante timeout/respuesta incierta, sin inventar claves de idempotencia. Depende de T024 y del gate DEP-002.
- [ ] T034 [US2] BLOQUEADA DEP-001/002/003: conectar `front/src/components/TradeDialog.jsx`, `front/src/hooks/useTrade.js` y `front/src/components/MarketWorkspace.jsx` a precio vigente, límites conocidos y confirmación real; invalidar recursos afectados en la sesión y refrescar consumidores disponibles, manteniendo éxito de orden separado de error de consulta. Depende de T013, T025, T029, T033 y APIs disponibles.
- [ ] T035 [US2] BLOQUEADA DEP-002: ampliar `front/src/test/trade-dialog.test.jsx` y crear `front/src/test/trade-refresh.test.jsx` con mocks derivados de contratos confirmados para códigos de saldo/disponibilidad/tenencia, doble envío, resultado incierto y refresco fallido que no repite POST. Depende de T034; estas pruebas no reemplazan T043.

### US3 — Gestión y canal de alertas (P1)

- [ ] T036 [US3] BLOQUEADA DEP-006: crear `front/src/services/alerts.js` y `front/src/test/alerts-service.test.jsx` sobre CRUD confirmado, identidad y códigos reales; no tratar las props visuales de A como DTOs. Depende de T024 y del gate DEP-006 para CRUD.
- [ ] T037 [US3] BLOQUEADA DEP-006: crear `front/src/hooks/usePriceAlerts.js` y `front/src/test/alert-stream.test.jsx` con transporte/autenticación/payload/recuperación acordados; conexión única por sesión, cancelación y descarte, sin JWT en URL ni garantía de replay/deduplicación no respaldada. Depende de T022, T036 y del gate específico de transporte; ninguna biblioteca nueva sin aprobación.
- [ ] T038 [US3] BLOQUEADA DEP-001/002/003/006: conectar `front/src/pages/AlertsPage.jsx`, `front/src/components/PriceAlertNotifications.jsx` y `front/src/components/MarketWorkspace.jsx` al CRUD/canal y al mismo flujo de operaciones, consultando precio vigente y exigiendo confirmación; no bloquear otras rutas al desconectarse. Depende de T015, T034, T037 y APIs/canal disponibles.

### US4 — Historial y ranking (P2)

- [ ] T039 [US4] BLOQUEADA DEP-003: crear `front/src/services/transactions.js`, `front/src/hooks/useTransactions.js` y `front/src/test/transactions.test.jsx`, conectar `front/src/pages/TransactionsPage.jsx` al contrato real de historial, campos, fechas y paginación y registrar su refresco en `front/src/components/MarketWorkspace.jsx` para cuando T034 confirme órdenes; no inventar filtros HTTP ni información faltante. Depende de T017, T018, T024 y contrato/endpoint de historial disponibles; la lectura no espera a órdenes.
- [ ] T040 [US4] BLOQUEADA DEP-005: crear `front/src/services/ranking.js` y `front/src/test/ranking.test.jsx`, conectar `front/src/pages/RankingPage.jsx` al orden/estrategia reales y al detalle/modal compartidos, sin recalcular scores; las acciones de operación siguen no disponibles hasta T034. Depende de T017, T018, T024 y contrato/endpoint de ranking disponibles; la lectura no espera a órdenes.

### US5 — Compatibilidad futura de rutas (P2)

- [ ] T041 [US5] BLOQUEADA DEP-007: únicamente tras acuerdo backend/proxy/cliente, adaptar las URLs reales en `front/src/services/api.js` y servicios afectados y ampliar `front/src/test/api-services.test.jsx`; mantener `/api/v1` hasta entonces. Cualquier cambio de `front/vite.config.js` queda fuera de este lote sin aprobación adicional. Depende de T024 y acuerdo de migración; no es prerrequisito para integrar APIs confirmadas bajo la base vigente.

**Checkpoint C**: Solo las capacidades con contrato y disponibilidad verificados pueden pasar a validación real. C no es una barrera indivisible: cotizaciones, portfolio, patrimonio, operaciones, historial, ranking y alertas se desbloquean por sus gates; T026 es una aprobación frontend y T041 una migración coordinada independiente.

## Fase D — Integración real, validación funcional y pulido final

**Goal**: Verificar que el comportamiento con servicios reales coincide con la spec, sin confundir mocks aprobados con aceptación final.
**Independent Test**: Backend de prueba con contratos implementados, cuentas de prueba y datos financieros provistos por el responsable backend; usar la aplicación sin fixtures productivas. Registrar evidencia reproducible y límites.

- [ ] T042 [US1] BLOQUEADA por las APIs de gráficos: validar dashboard/detalle/portfolio contra fuentes reales, fecha, períodos, tres gráficos y ausencia de datos, y contrastar `front/src/test/dashboard.test.jsx`, `chart-states.test.jsx` y `wealth-history.test.jsx` con esas respuestas; registrar evidencia por escenario US1 y US6.7 en `specs/005-entrega2-frontend/tasks.md`. Depende de T027, T030 y T032.
- [ ] T043 [US2] BLOQUEADA por mercado real: validar compra/venta, rechazos, confirmación y refresco sin orden duplicada con backend de prueba, contrastar `front/src/test/trade-refresh.test.jsx` y registrar resultados de US2 y US6.10–11 en `specs/005-entrega2-frontend/tasks.md`; no operar con cuentas reales. Depende de T035 y T039.
- [ ] T044 [US3] BLOQUEADA por canal real: validar CRUD, notificación, desconexión/recuperación y acción rápida con precio vigente; contrastar `front/src/test/alert-stream.test.jsx` y registrar US3 en `specs/005-entrega2-frontend/tasks.md`. Depende de T038.
- [ ] T045 [US4] BLOQUEADA por consultas financieras reales: verificar portfolio, historial, ranking, posiciones vacías, permisos y acceso al detalle con datos backend; contrastar `front/src/test/financial-pages.test.jsx`, `transactions.test.jsx` y `ranking.test.jsx`, registrar US4 en `specs/005-entrega2-frontend/tasks.md`. Depende de T029, T039 y T040.
- [ ] T046 [US5] Validar login/registro/catálogo y base de rutas acordada contra backend disponible, contrastar `front/src/test/api-services.test.jsx` y registrar US5 en `specs/005-entrega2-frontend/tasks.md`; si T041 sigue bloqueada, documentar conservación de `/api/v1` sin dar por realizada la migración. Depende de T024; puede ejecutarse antes del resto de D.
- [ ] T047 [US6] BLOQUEADA por consultas personales reales: validar dos usuarios, cierre/cambio de sesión con respuestas pendientes y rechazo backend de acceso ajeno; contrastar `front/src/test/session-isolation.test.jsx`, comprobar inversión y resumen sin doble PnL y registrar US6 en `specs/005-entrega2-frontend/tasks.md`. Depende de T029, T034 y T038 para incluir eventos.
- [ ] T048 Revisar la interfaz integrada a 360/1280 px, teclado y foco en `front/src/components/DashboardLayout.jsx`, `PlayerProfileDialog.jsx`, `TradeDialog.jsx` y `front/src/style.css`; medir gráficos desde datos completos y avisos desde recepción, registrar navegador/equipo/volumen y resultados SC-001/003/004 en `specs/005-entrega2-frontend/tasks.md`, sin usar tiempos de jsdom como evidencia visual. Depende de T042–T045 y T047.
- [ ] T049 Ejecutar los scripts existentes de `front/package.json`: `npm test`, `npm run test:coverage` y `npm run build`; revisar imports de fixtures desde `front/src/` y ausencia de datos de prueba en producción, registrar resultados y fallos en `specs/005-entrega2-frontend/tasks.md`, sin modificar configuración ni inventar umbrales de cobertura. Depende de T048; las suites relevantes se ejecutan además durante A–C.
- [ ] T050 Revisar trazabilidad final de `specs/005-entrega2-frontend/tasks.md` contra `spec.md` y `checklists/requirements.md`, contrastar SC-001 a SC-007 y registrar qué se verificó, qué sigue bloqueado y qué aprobaciones faltan; no editar spec/Constitución ni marcar integraciones pendientes como cumplidas. Depende de T046 y T049; T041 puede seguir pendiente si la base vigente se conserva por acuerdo, y debe quedar explícito.

## Dependencias externas — responsable backend y aprobaciones

No son tareas de implementación backend asignadas a esta feature. El responsable backend debe proporcionar la evidencia para levantar cada gate; el frontend no modifica esos contratos para desbloquearse.

| Gate | Evidencia requerida | Tareas afectadas |
|---|---|---|
| DEP-001 | URL real y contrato confirmado de cotización actual/histórico/fecha, campos, precisión, estrategia, períodos y falta de datos; endpoint accesible para aceptación | T025, T027, T034, T038, T042 |
| DEP-002 | Órdenes: entrada/salida, confirmación, códigos de rechazo, límites, precisión y tratamiento del resultado incierto; backend transaccional operativo | T033–T035, T038, acciones de operación/refresco de T039–T040 y T043; no bloquea lecturas de historial/ranking |
| DEP-003 | Portfolio/historial por sesión: titularidad, saldo, costo/PnL, nulos, fechas/paginación; disponibilidad se confirma por endpoint, no por familia completa | T028–T030, T032, T034, T039, T045, T047 |
| DEP-004 | Serie histórica patrimonial con saldo y patrimonio, períodos y fuente real, sin deducirla de transacciones | T031–T032, T042 |
| DEP-005 | Ranking: contrato, orden y estrategia activa implementados | T040, T045 |
| DEP-006 | CRUD y canal se confirman separadamente: transporte, autenticación, eventos, recuperación, duplicados y disponibilidad | T036–T038, T044, T047 |
| DEP-007 | Acuerdo de migración exacta de rutas con backend y proxy | T041; no bloquea A/B ni APIs disponibles bajo rutas actuales |
| DEP-008 | Responsable frontend/equipo: evaluación y aprobación explícita de biblioteca compatible; fixtures contractuales una vez confirmados contratos | T026 y tareas que requieren gráficos completos |

La discrepancia TypeScript/Tailwind declarados frente a JavaScript/Bootstrap real permanece abierta para gobernanza. No se migra stack ni se bloquea la estructura A/B por APIs ausentes. La imagen aprobada debe estar disponible para afirmar fidelidad al render; sin ella solo se valida conservación de la identidad actual.

## Dependencies & Execution Order

### Phase dependencies

```text
T001 → A: fixtures + estructura + componentes + pruebas aisladas → T018
  └──→ B: HTTP confirmado + identidad + catálogo → T024
A + B + contrato de cada capacidad → C correspondiente
A + aprobación biblioteca → T026 (sin esperar backend)
C disponible por capacidad → D de esa capacidad → revisión final
Acuerdo de rutas → T041 (independiente del avance con /api/v1)
```

Las dependencias exactas están en cada tarea; el orden de IDs no obliga a esperar tareas anteriores que no sean prerrequisitos. No se ejecuta C en bloque ni se marca un gate por leer la ruta en la spec. No hay tareas completadas previas en este archivo: autenticación, catálogo y tests existentes se reutilizan, no se vuelven a crear como si faltaran.

### User story dependencies e independencia de pruebas

| Historia | Incremento aislado comprobable | Condición de integración completa |
|---|---|---|
| US1 | T009–T011 con presentación/estados; T026 tras aprobación | DEP-001/003/004/008 y T042 |
| US2 | T012–T013 con callbacks y bloqueo de envío | DEP-001/002/003, conexión e historial para refresco; T043 |
| US3 | T014–T015 con notificaciones visuales | DEP-006 y US2 para acción rápida; T044 |
| US4 | T016–T017 con tablas y estados | DEP-003/005 y US2 para acciones; T045 |
| US5 | T019–T020, T023–T024 con contratos actuales | T046; migración T041 solo si se acuerda |
| US6 | T003–T008, T021–T022 con navegación y aislamiento controlado | Consultas personales y eventos reales para T047 |

### Parallel examples

- Tras T001, T002, T003, T019 y T021 pueden iniciarse sin depender de endpoints nuevos; T003 no lleva `[P]` porque establece la estructura compartida que luego se edita secuencialmente.
- Tras T002, US1 T009, US2 T012 y US3 T014 trabajan en archivos distintos. Los tests T011/T013/T015 esperan sus componentes; evitar modificaciones simultáneas de la fixture compartida.
- US4 T016 puede avanzar mientras se prueban US2/US3, una vez listos T007/T009; no editar `MarketWorkspace.jsx` desde esos grupos, reservar la composición a T018.
- US5 T019/T020 y US6 T021 tienen archivos distintos; T022/T023 esperan las tareas de A que tocan los mismos archivos.
- En C se pueden preparar T025, T028, T031, T033 y T036 cuando sus gates individuales estén resueltos; coordinar cambios en `Home.jsx`, `MarketWorkspace.jsx` y `useTrade.js` secuencialmente. No usar paralelismo para omitir gates.

## Implementation Strategy

**Primer incremento utilizable**: estructura A, navegación y catálogo real conservado, más endurecimiento B sobre contratos existentes. Entregable frontend revisable con estados y pruebas, no un MVP financiero completo.

**Avance paralelo con backend**: crear componentes mediante props/callbacks y fixtures de presentación; mantener producción sin datos ficticios. Al confirmarse cada contrato, implementar su servicio y tests, conectar la UI existente y ejecutar su validación real sin esperar a las capacidades no relacionadas.

**Tests durante el desarrollo**: añadir casos significativos antes o junto con el cambio de cada tarea y ejecutar la suite afectada; no esperar a D para comprobar componentes. D añade evidencia real, responsive y mediciones, no repite la implementación de A–C.

**Límite de finalización**: ninguna casilla de aceptación financiera se cierra con mocks. Los estados no disponibles no reemplazan capacidades obligatorias. Generar esta lista no ejecuta tareas ni altera las restricciones del plan; el pedido actual habilita planificar A/B y registrar C/D como pendientes condicionados.

## Registro de ejecución

### T001 — Inspección inicial (2026-10-13)

- Responsable: Codex, ejecución autorizada por el usuario para T001–T003; sin commit.
- Rama: `feature/front2`. Estado previo: spec y checklist modificados; `docs/catalog-sync.md`, `plan.md` y este `tasks.md` sin seguimiento. No había cambios en código frontend. Conservar todo ese estado; se registraron hashes de spec, plan, checklist, Constitución y `docs/catalog-sync.md` para comprobar preservación.
- Leídos spec, plan, tareas, Constitución y entrada, catálogo, layout, login, sesión, tests y configuración existentes. No existen todavía `App.jsx`, `MarketWorkspace.jsx`, `PlayersPage.jsx` ni fixtures financieras. React/JavaScript/Bootstrap y servicios `/api/v1` son reutilizables; no hacen falta paquetes nuevos.
- Discrepancia conocida e informada antes de editar: stack constitucional TypeScript/Tailwind frente al stack real; se mantiene la decisión explícita del usuario de JavaScript/Bootstrap sin modificar la Constitución. T003 prepara estados provisionales, no completa el dashboard ni sus criterios financieros.
- Dependencias de alcance informadas: el menú lateral sigue siendo T004; el Inicio provisional ofrecerá acceso al catálogo. El test existente de catálogo necesita ajustar su importación al componente extraído para verificar T003, sin ampliar los escenarios de T008.
- DEP-001 a DEP-008 siguen abiertas para integración/aprobaciones futuras. No bloquean fixtures de presentación ni extracción de rutas. No se definen contratos financieros.
- Comandos disponibles desde `front/`: `npm test`, `npm run test:coverage`, `npm run build`. Para este cambio se verificarán la suite existente, las rutas extraídas y el build; cobertura no tiene un umbral nuevo impuesto. El primer intento de `npm test` en sandbox falló al iniciar esbuild con `spawn EPERM`, antes de ejecutar pruebas; requiere reintento con permisos de proceso, sin cambiar configuración.
- T001 terminada por inspección y registro; T002/T003 se marcarán solo después de verificar sus cambios. Las verificaciones locales no acreditan integración con APIs financieras.

### Primera etapa de correcciones — 2026-10-20

- Alcance autorizado por el usuario: completar y validar separación inicial, navegación, protección de rutas, logout y estados del catálogo. Estado inicial de Git limpio; sin commits ni instalación de dependencias. Las notas anteriores describen el estado histórico de generación/inspección.
- T002 verificada por inspección: `front/src/test/fixtures/financialPresentation.js` contiene vacío, desconocido, error, punto único y precios distintos, etiquetados SOLO PRUEBA; no hay imports productivos. No acredita integración ni pruebas financieras.
- T003 verificada: `App` registra seis rutas protegidas, `main` conserva la entrada, `MarketWorkspace` compone layout/Outlet, `Home` presenta indisponibilidad y acceso al catálogo, `PlayersPage` conserva catálogo/detalle y `LoginPage` redirige a `/home`. No fue necesario modificar esos componentes. Suite integrada y build aprobados.
- T004 completada: `DashboardLayout` usa NavLink para Inicio, Jugadores, Portfolio, Historial, Ranking y Alertas, con destino y estado activo según ruta. Se preservaron identidad, Swagger y preview; `components/index.js` ya exportaba el layout y no requirió cambios. Los enlaces financieros acceden únicamente a los placeholders existentes.
- T008 permanece sin marcar: `navigation.test.jsx` monta App real y prueba login, Inicio/Jugadores, seis rutas protegidas, sesión guardada vencida, logout en ambos almacenamientos y protección al volver en el historial, navegación provisional sin consultas y preview. `home.test.jsx` se renombró a `players.test.jsx`, eliminando el alias PlayersPage as Home y ampliando carga, vacío, búsqueda sin resultados y recuperación de error. El test aislado de login aclara que prueba el destino de redirección. Faltan filtros, inversión personal y aislamiento entre sesiones/respuestas tardías; estos tests no cierran T006/T022.
- Validación desde `front/`: `npm test` falló inicialmente al arrancar esbuild en sandbox con `spawn EPERM`, sin ejecutar pruebas. Reintentado fuera del sandbox: código 0, 5 archivos y 29 tests aprobados, 0 fallidos (22,21 s). `npm run build` fuera del sandbox: código 0, 125 módulos transformados, build en 8,97 s. Sin cambios de configuración ni de `/api/v1`.
- No se realizó revisión manual en navegador a 360/1280 px, medición de gráficos, cobertura ni integración contra backend real. T018, T049 y criterios financieros siguen pendientes; el build y los mocks no acreditan esos alcances.
- Discrepancia documental de stack TypeScript/Tailwind frente a JavaScript/Bootstrap permanece abierta para gobernanza; esta etapa conserva el stack exigido. Los contratos financieros y la aprobación de biblioteca siguen pendientes.

### Etapa 2 — Aislamiento de sesión y capa HTTP — 2026-10-20

- Estado inicial de Git limpio; se conservaron los cambios de la etapa 1 ya presentes. Sin commits, nuevas dependencias, migración de rutas, endpoints ni cálculos financieros. Se inspeccionaron spec/plan/tareas, AuthController/LoginResponseDto, JwtTokenProvider, SecurityConfig, ErrorCode y GlobalExceptionHandler.
- T006 completada: `useSessionRequest` cancela y descarta por generación y clave de recurso/sesión, oculta el resultado previo desde el cambio de clave y no trata cancelación como error ordinario. Catálogo y perfil lo reutilizan. App remonta MarketWorkspace por generación/token; PlayersPage reinicia su selección, búsqueda y paginación por sesión. Logout, cambio de sesión (también por storage) y expiración invalidan consultas. No hay persistencia de datos personales. Los tests fuerzan resoluciones y errores tardíos aunque el transporte ignore el aborto.
- T019 completada: `api-services.test.jsx` ejercita Axios con adaptador de prueba, base `/api/v1`, timeout existente, Bearer, rutas/cuerpos de autenticación y catálogo, signal y cancelación, propagación de red/timeout sin reintentos, códigos ProblemDetail y 401/403 sin cuerpo. Se usan únicamente contratos presentes en código backend.
- T020 completada: `getApiError` traduce códigos del enum ErrorCode; desconoce textos arbitrarios de detail y ofrece fallback seguro. El hook existente de formularios recibe mensaje vacío para cancelaciones, verificado mediante test. El hook de consultas procesa 401 solo si la consulta sigue vigente; 403 conserva sesión y contexto tanto en catálogo como perfil. Se mantuvieron interceptor, timeout y base configurable.
- T021 completada: auth/session decodifican base64url con utilidades nativas, derivan userId numérico seguro desde el JWT (nunca username ni userId guardado), rehidratan sesiones previas y limitan expiresAt con exp cuando existe. Decodificar no valida firma ni autoriza acceso. Una sesión sin identidad legible conserva el catálogo existente, pero `hasSessionIdentity` y `useSessionRequest` con `personal: true` impiden consultas personales; los futuros consumidores personales deberán usar ese modo. No se crean servicios personales.
- T022 tiene implementados y probados sus cambios de identidad/contexto y aislamiento; permanece sin marcar porque su prerrequisito T008 aún incluye filtros e inversión personal no realizados. T023 mantiene completada la parte de catálogo/detalle, cancelación y 401/403; conserva pendientes sus prerrequisitos T005/T007. T024 tiene evidencia local de servicios/detalle, pero permanece sin marcar por T022/T023. No se cierran aceptación financiera, titularidad backend ni T047 con estos mocks.
- Tests nuevos: `session.test.jsx`, `api-services.test.jsx`, `session-isolation.test.jsx` y fixture JWT identificada `test/fixtures/session.js`. Regresiones existentes de auth, catálogo y perfil ajustadas a errorCode real, manteniendo sus escenarios. Incluyen cambio A/B con respuesta pendiente exitosa o 401, cambio de jugador, selección abierta, logout, storage, expiración, misma credencial en una sesión nueva, cancelación y reintento de consulta, identidad ausente y permisos.
- Validación desde `front/`: primer `npm test` en sandbox falló antes de ejecutar por `spawn EPERM` de esbuild. Fuera del sandbox, ejecución inicial: 66 tests aprobados; tras añadir casos de expiración/reinicio y propagación HTTP, ejecución final `npm test`: código 0, **71 tests en 8 archivos**, 0 fallidos, 20,05 s. `npm run build` final: código 0, 126 módulos, 6,66 s. `git diff --check` sin errores de whitespace; Git advierte únicamente normalización LF/CRLF según su configuración.
- Límites: sin pruebas contra backend en ejecución ni revisión manual de navegador. El servidor sigue siendo responsable de validar firma, vigencia y titularidad. DEP-001–008 y discrepancia constitucional de stack permanecen abiertas. No se ensayaron canales/eventos financieros inexistentes; esta etapa no completa FR-013 de punta a punta ni habilita integración sin contratos.

### Etapa 3 — Presentación del dashboard y componentes financieros — 2026-10-20

- Estado inicial: `tasks.md` ya estaba modificado con el registro y marcas de la etapa 2; se preservaron esos cambios. Spec, plan y checklist inspeccionados; los controladores siguen sin cotizaciones, posiciones, órdenes, histórico patrimonial ni ranking financieros. Sin commits, dependencias nuevas ni cambios de `/api/v1`.
- T007 completada en su alcance de presentación: perfil mantiene consulta deportiva, estadísticas y errores, añade Mercado y Mi inversión, cotización separada de promedio, cantidades/costo/valor/PnL, estados y acciones por callbacks. La posición vacía se distingue del error/indisponibilidad; sin callback no se habilita operar. No se agregó consulta personal: inversión llega únicamente por props de presentación (tests) y en producción permanece no disponible. Cualquier futuro adaptador personal deberá usar `useSessionRequest` con `personal: true`; los tests existentes de etapa 2 mantienen ese bloqueo sin identidad.
- T009 completada: tres contenedores reutilizables, estados y alternativas tabulares accesibles. Cotización presenta fecha/valor/versión de estrategia; distribución conserva valor/porcentaje recibidos; patrimonio mantiene saldo y patrimonio separados. Punto único explícito, cero distinto de desconocido, sin fórmulas ni interpolación. No se considera implementado el renderizador interactivo, los tooltips ni FR-001 completo.
- T010 completada como composición: Home presenta resumen y tres áreas independientes; permite cargar explícitamente el catálogo vigente, seleccionar jugador y abrir su detalle. Se identifica al jugador elegido incluso sin cotizaciones. No se ofrecen períodos financieros inventados; el contenedor acepta períodos y consulta de fecha mediante callbacks solo para una futura integración respaldada por datos.
- T011 completada: dashboard/chart-states prueban composición, ausencia de selección, carga, error parcial/reintento, vacío, indisponibilidad, falta de autorización, punto único, cero/desconocidos y preservación de conceptos. Son pruebas de presentación, no del gráfico interactivo ni de rendimiento.
- T016/T017 completadas en presentación: PortfolioPage, TransactionsPage y RankingPage reciben datos/callbacks, permiten selección y conservan campos y orden recibidos. Portfolio reutiliza resumen y contenedores. En producción las tres rutas siguen sin datos financieros. Tests verifican estados, vacío, selección, no doble PnL, ranking sin clasificación local e historial sin calcular totales ausentes. Fechas son etiquetas de presentación; un estado transaccional no definido en contrato no se inventó.
- T005 solo avanza en el rótulo «N.º de fila» del catálogo; filtros siguen pendientes. T008 y T018 no se cierran. Se añadió gestión de foco inicial, ciclo Tab/Shift+Tab, Escape y restauración en el perfil, verificada en jsdom, más estilos de foco y tablas desplazables. Faltan revisión manual en navegador a 360/1280 px, lector de pantalla, contraste y comportamiento modal real; T048 tampoco se cierra. Las referencias de pendientes en registros anteriores son históricas.

#### DEP-008 — Opciones y decisión pendiente

| Opción registrada en el plan | Evaluación necesaria antes de aprobar |
|---|---|
| Recharts | Versión concreta, peer dependencies para React 19, integración JSX, licencia, tamaño, accesibilidad, tooltips y pruebas en jsdom. |
| Chart.js y posible wrapper React | Versión y compatibilidad del wrapper, ciclo de vida de canvas, alternativa accesible, licencia, tamaño y pruebas. |

No se verificaron versiones ni se seleccionó o instaló una biblioteca en esta etapa. La decisión actual es conservar los contenedores y tablas; la elección y aprobación explícita del responsable frontend quedan pendientes en T026. No se sustituye ese gate con un renderizador propio ni se afirma compatibilidad no comprobada.

#### Evidencia final y límites

- `npm test` inicial: bloqueo de arranque `spawn EPERM` en sandbox, antes de ejecutar tests. Fuera del sandbox, suite inicial 116 aprobados; suite final tras verificar identificación de jugador/foco/ausencia de consultas personales: **117 tests aprobados en 11 archivos**, 0 fallidos, 26,89 s.
- `npm run build` final: código 0, 136 módulos, 7,55 s. `git diff --check` sin errores, solo advertencias de normalización LF/CRLF. No hay imports productivos de fixtures.
- Integración reutilizada: autenticación, catálogo y perfil deportivo mediante servicios existentes; esta etapa no ejecutó pruebas contra un servidor real. Ninguna integración financiera implementada ni aceptación financiera cerrada. Los valores financieros de tests son SOLO PRUEBA.
- Continúan DEP-001–008, T026 y fases de integración/aceptación. Se requiere contrato confirmado por capacidad, biblioteca aprobada y validación de titularidad backend antes de integrar información personal. Próximo paso: revisión visual manual y decisión sobre biblioteca mientras se resuelven los contratos; filtros y demás tareas pendientes pueden avanzar independientemente.
