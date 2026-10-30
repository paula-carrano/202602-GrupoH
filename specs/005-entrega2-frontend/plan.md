# Implementation Plan: Entrega 2 — Adaptación del frontend financiero

> Actualizacion de implementacion (2026-10-29): por instruccion del usuario, el backend aprobado se conserva sin cambios y los contratos `/api` ya fueron contrastados con el codigo. El frontend usa Recharts y reconstruccion patrimonial diaria con operaciones y cotizaciones reales, autorizada expresamente. Esta decision reemplaza las restricciones anteriores sobre `/api/v1`, contratos pendientes y DEP-004. No se implementan pantallas administrativas ni API keys. Ver [contratos, limites y demo del frontend](../../front/README.md). Las secciones originales siguientes conservan el contexto de planificacion anterior.


**Branch**: `feature/front2` | **Date**: 2026-10-13 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/005-entrega2-frontend/spec.md` y [checklist](./checklists/requirements.md).

**Estado**: Plan técnico documental; integraciones financieras bloqueadas por contratos pendientes. No autoriza implementación ni declara cumplidos criterios de aceptación.

## Summary

Adaptar la aplicación existente, sin reescribirla: convertir `/home` en dashboard, conservar el catálogo en `/players`, ampliar el detalle con mercado e inversión personal y reutilizar componentes para portfolio, historial, ranking, operaciones y alertas. Mantener React con JavaScript, Bootstrap, Axios, React Router y Vitest. Las rutas actuales de autenticación y catálogo conservan `/api/v1`.

El código inspeccionado implementa autenticación, catálogo y estadísticas, pero no expone las APIs financieras requeridas. Se puede diseñar la navegación, estados y límites de presentación; no se pueden cerrar integraciones, cuerpos de órdenes ni transporte de alertas hasta resolver DEP-001 a DEP-008. Un estado «datos no disponibles» es honesto durante desarrollo, pero no satisface por sí solo la entrega funcional de los gráficos o las operaciones.

Este documento conserva las secciones de `.specify/templates/plan-template.md`. Por la restricción de generar únicamente `plan.md`, investigación, modelo de presentación y guía de validación se integran aquí; no se crean `research.md`, `data-model.md`, `contracts/`, `quickstart.md` ni `tasks.md`. Se inspeccionó `setup-plan.ps1`: su resolución con directorio explícito persiste `.specify/feature.json`; se omite ese script y se usa la plantilla resuelta en lectura. No existe `.specify/extensions.yml`, por lo que no hay hooks previos/posteriores que ejecutar. El workflow estándar no se declara totalmente desbloqueado: las decisiones externas permanecen como gates pendientes.

## Technical Context

**Language/Version**: JavaScript ES modules y JSX; React/React DOM `^19.1.1` declarados. Sin TypeScript.

**Primary Dependencies**: Bootstrap `^5.3.8`, Axios `^1.20.0`, React Router DOM `^7.18.4`, React Icons `^5.7.0`; Vite `^6.3.5` y plugin React existente. Versiones declaradas en `front/package.json`, no una propuesta de actualización. Sin librería de gráficos instalada.

**Storage**: Sesión actual en localStorage o sessionStorage según «Recordarme». Los datos financieros y de notificaciones se mantendrán en memoria vinculada a la sesión; sin base de datos frontend ni nueva persistencia de posiciones.

**Testing**: Vitest `5.0.1`, jsdom, Testing Library y cobertura V8 configurados en `front/vite.config.js`. Conservar `src/test/setup.js`, scripts y configuración. CI ya ejecuta cobertura y build y consume `front/coverage/lcov.info` desde Sonar; no inventar umbrales ni modificar pipeline.

**Target Platform**: Aplicación web responsive, con validación a 360 y 1280 píxeles y navegación por teclado según SC-004. No se fija una matriz adicional de navegadores no acordada.

**Project Type**: SPA existente en `front/` con backend Spring en `back/`, este último de solo lectura para esta feature.

**Performance Goals**: SC-001: gráficos en menos de 1 segundo desde datos completos. SC-003: notificación en menos de 2 segundos desde recepción cliente. Registrar navegador, equipo y volumen al medir; no afirmar latencia desde emisión backend. No usar tiempos de jsdom como evidencia de rendimiento visual.

**Constraints**: Solo consumir contratos verificados; conservar base configurable `VITE_API_BASE_URL` con fallback `/api/v1` y proxy `/api` actual. No asumir datos ausentes como cero, no calcular reglas financieras ni reintentar órdenes automáticamente. Mantener logo e identidad azul oscuro/blanco. Dependencias nuevas e implementación requieren aprobación posterior.

**Scale/Scope**: Seis rutas de negocio, detalle reutilizado y un flujo compartido de operaciones. Catálogo actual completo con páginas locales de 10 filas. No hay evidencia de volúmenes financieros, paginación remota ni SLA backend; no introducir virtualización, paginación HTTP o caché persistente por anticipado.

## Constitution Check

Fuentes leídas: `.specify/memory/constitucion.md` (nombre real), `docs/consigna-tp.md`, `docs/contexto-catedra.md`, specs 004/005 y contratos de foundation/scraper. La consigna define capacidades; los controladores y DTOs permiten distinguir qué existe en código. La inspección estática no equivale a una prueba HTTP contra un backend en ejecución.

| Gate | Evaluación antes y después del diseño |
|---|---|
| React y separación de responsabilidades (§12) | Compatible por diseño: frontend presenta datos; valuación, PnL, transacciones y autorización son del backend. Sin cambios backend. |
| Stack declarado (§2) | Discrepancia abierta: Constitución/contexto indican TypeScript y Tailwind, mientras spec e instrucción explícita preservan JavaScript y Bootstrap. Registrar excepción de alcance; no declarar conformidad total ni editar la Constitución. |
| Seguridad (§4) | Diseño mantiene Bearer JWT existente, evita credenciales hardcodeadas y descarta datos de sesiones anteriores. Verificación de titularidad financiera pendiente del backend. |
| Errores (§5) | Planificar interpretación por `errorCode`, mensajes españoles y fallbacks sin trazas ni detalles internos. Implementación y pruebas pendientes. |
| Contratos y SDD (§12–13) | Bloqueo de integración: faltan APIs financieras, definiciones y transporte. No generar contratos ficticios para superar el gate. |
| Calidad y Definition of Done (§15) | No superado para implementación: los nuevos escenarios no están ejecutados. La checklist permanece intacta y sus pendientes siguen abiertos. |

La autorización actual permite producir este plan condicionado, no cerrar los gates ni crear tareas ejecutables que supongan dependencias resueltas. La discrepancia del stack y los bloqueos siguen presentes tras el diseño.

## Project Structure

### Documentation (this feature)

```text
specs/005-entrega2-frontend/
├── spec.md                     # Existente; solo lectura en esta tarea
├── checklists/requirements.md  # Existente; solo lectura en esta tarea
└── plan.md                     # Único archivo generado
```

### Source Code (repository root)

```text
front/
├── package.json, package-lock.json, vite.config.js   # Conservar
└── src/
    ├── main.jsx, style.css
    ├── pages/                  # Home, LoginPage, RegisterPage, ErrorPage
    ├── components/             # Layout, catálogo, detalle, formularios, mensajes
    ├── hooks/                  # useApiRequest, usePlayerCatalog
    ├── services/               # api, auth, session, players
    ├── utils/date.js
    └── test/                   # auth, home, player-table, player-profile, setup
back/src/main/java/com/cbo/players/  # Inspección de contratos; sin cambios
```

**Structure Decision**: Extender las carpetas existentes con páginas, componentes, hooks y servicios JavaScript. No crear otro frontend, sistema de estilos, framework de estado o paquete compartido backend/frontend.

## Phase 0 — Investigación y contratos observados

### Evidencia de API existente

| Fuente de código | Ruta real / contrato observado | Uso previsto |
|---|---|---|
| `controller/AuthController.java`, `dto/response/LoginResponseDto.java` | `POST /api/v1/auth/login`: entrada username/password; respuesta accessToken, tokenType, expiresInSeconds | Conservar `services/auth.js`; el login no devuelve portfolio ni saldo. |
| `AuthController`, DTOs de registro | `POST /api/v1/auth/register`; `POST /api/v1/auth/api-keys` para gestión de claves | Conservar registro y autenticación; no agregar gestión de claves al dashboard. |
| `controller/PlayerController.java`, `PlayerResponseDto` | `GET /api/v1/players`: array de jugadores, sin parámetros de filtro/paginación en el controlador | Conservar servicio y filtrar/paginar localmente. Campos disponibles: id, firstName, lastName, birthDate, nationality, position, currentTeam, league, active. |
| `PlayerController`, `PlayerDetailResponseDto` | `GET /api/v1/players/{id}`: datos del jugador y statistics nullable | Reutilizar consulta actual de `PlayerProfileDialog`; no confundir estadísticas deportivas con cotizaciones. |
| `controller/PlayerStatisticsController.java` | `GET /api/v1/players/{id}/stats` | Existe en código; no agregar una segunda consulta redundante al detalle actual. |
| `security/JwtTokenProvider.java` | Claims userId y role, subject username | El frontend puede identificar al usuario desde el token emitido; esto no valida firma ni permisos en cliente. |
| `exception/GlobalExceptionHandler.java` | ProblemDetail con errorCode, timestamp, correlationId y violations cuando aplica | Extender normalización de errores usando códigos, no textos. No asumir que todas las respuestas de seguridad tienen este formato. |

Las rutas de esta tabla provienen del código bajo `back/src/main/java/com/cbo/players/`. Seguridad admite JWT o API Key para catálogo; el navegador existente usa Bearer y no necesita una API Key nueva.

`specs/001-entrega1-foundation/contracts/openapi.yaml` documenta autenticación y catálogo, pero el detalle declara PlayerResponseDto mientras el controlador devuelve PlayerDetailResponseDto con estadísticas. Tampoco documenta `/stats` ni las rutas administrativas actuales. `specs/002-scrapper/contracts/openapi.yaml` es del scraper interno: no se convierte en API del navegador. Mantener estas discrepancias documentadas; no editar contratos desde 005.

Existe un modelo/repositorio PlayerQuote en backend, pero ello no proporciona una API HTTP de cotizaciones. Las rutas financieras sin prefijo de la spec 004 describen objetivos, no rutas disponibles. No se antepone `/api/v1` automáticamente para fabricar URLs de integración. Tampoco se llama a `/quotes/recalculate` desde la UI: esa capacidad pertenece a backend y no hay acción de usuario aprobada aquí.

### Decisiones técnicas y alternativas

| Decisión | Fundamento | Alternativa descartada o pendiente |
|---|---|---|
| Mantener Axios, React Router y Bootstrap | Ya soportan los servicios, navegación y estilos existentes | Reescritura, Tailwind, otra biblioteca visual o nuevo cliente HTTP: fuera de alcance. |
| Estado React local y contexto acotado a sesión | Compartir selección de jugador, modal e invalidación sin duplicar llamadas por pantalla | No incorporar Redux ni biblioteca de caché de consultas sin necesidad demostrada. |
| Gráficos como componentes de presentación reutilizables | Misma visualización en Inicio, detalle y portfolio, con estados independientes de la biblioteca | Recharts es candidata por composición React y soporte de líneas/dona. No se fija versión, instalación ni compatibilidad comprobada. Antes de aprobar: verificar peer dependencies para React 19, uso desde JSX, licencia, tamaño, accesibilidad y pruebas con jsdom. Comparar con Chart.js y su posible wrapper; no implementarlos en paralelo. |
| Mantener filtros locales del catálogo | El backend devuelve array completo y no acepta filtros en su método actual | No inventar query params ni paginación remota. |
| Contratos ausentes como bloqueos | No hay respuesta financiera validada sobre la que integrar | No usar fixtures en producción, servicios ficticios o cálculos financieros de reemplazo. |

## Phase 1 — Diseño de adaptación

### Navegación y reutilización

- Mantener `main.jsx` como entrada y extraer el componente de rutas a `App.jsx` para probar navegación sin montar una raíz DOM. Usar un contenedor autenticado `MarketWorkspace` que reutilice `DashboardLayout`, comparta selección/modal y se desmonte ante cambio de sesión.
- `/home` conservará el nombre `Home` pero mostrará el dashboard. Extraer su catálogo actual a `PlayersPage`, preservando `PlayerTable`, `Pagination`, búsqueda, carga, errores y detalle. El login exitoso tendrá `/home` como destino financiero según FR-011; probar el cambio del retorno guardado que hoy usa `LoginPage`.
- Agregar rutas protegidas `/players`, `/portfolio`, `/transactions`, `/ranking`, `/alerts`, conservando login, registro, error y not-found. Usar enlaces activos de React Router para que Inicio y Jugadores dejen de apuntar a la misma ruta.
- Reutilizar `AuthLayout`, `BrandPanel`, `FormField`, `FeedbackAlert`, `ErrorDialog`, `utils/date.js` y `components/index.js`. Mantener el logo, iconos y CSS existente; ampliar clases Bootstrap y estilos propios solo donde haga falta.
- Mantener el detalle como diálogo reutilizado, sin crear una ruta de detalle adicional no requerida. Centralizar la selección por id para abrir el mismo detalle desde catálogo, dashboard, portfolio, historial y ranking.
- Ampliar `usePlayerCatalog` con filtros locales de liga, equipo y posición y reinicio de página al cambiar criterios. Las cotizaciones se integrarán solamente cuando DEP-001 esté resuelta; la enumeración de filas no se presentará como ranking financiero.

### Datos, identidad y estados

- Conservar la instancia `api`, su timeout y el interceptor Bearer. Reutilizar `getPlayers` y `getPlayer`; cada nueva familia financiera tendrá un servicio pequeño cuando exista contrato, sin rutas ni payloads anticipados.
- Extender `auth.js`/`session.js` para obtener userId del claim existente con utilidades nativas, sin otra dependencia. Validar presencia/formato antes de consultas personales; rehidratar también las sesiones almacenadas existentes. Si no se puede identificar al usuario, impedir consultas personales y solicitar nueva autenticación, sin usar username o un id fijo como sustituto.
- Montar el estado privado dentro de `MarketWorkspace` y asociar cada solicitud a la identidad de sesión y a un identificador de generación de consulta. Abortar al desmontar, cambiar usuario/jugador o cerrar sesión; además ignorar respuestas tardías, incluso si el transporte no logró cancelarlas. No registrar tokens ni persistir posiciones.
- Estados de UI separados: carga, datos disponibles, vacío confirmado, error y no disponible por ausencia contractual/datos. El estado de refresco fallido se marca como desactualizado, nunca como dato nuevo. Una colección vacía exitosa puede acreditar ausencia de posición; un fallo o contrato ausente no.
- Usar hooks por capacidad para lectura y refresco, con invalidación compartida después de operar. Conservar `useApiRequest` para formularios compatibles; no usar su retorno indistinto de error como confirmación de orden.
- Mapear códigos conocidos a mensajes españoles. Para códigos desconocidos, red o timeout, usar fallback seguro sin mostrar trazas. No interpretar textos de `detail` como códigos. Distinguir 401 de sesión inválida y 403 de acceso denegado; no extender a las pantallas nuevas el logout indiscriminado que hoy hace el catálogo para ambos estados.
- Los nombres PlayerQuotePoint, PortfolioSummaryDto, TransactionItemDto y PriceAlertNotificationDto siguen siendo necesidades de presentación. No crear esquemas de red a partir de esos nombres. El formato monetario, escala, redondeo, fechas y paginación financiera deben confirmarse con los contratos; conservar un cero real y distinguir null/ausencia.

### Dashboard, detalle y portfolio

- Crear `QuoteHistoryChart`, `PortfolioAllocationChart` y `WealthHistoryChart` bajo `components/charts/`, con props de datos de presentación y estados; integrar la biblioteca únicamente tras aprobación. Títulos, leyendas, etiquetas en español y una alternativa textual/tabular accesible acompañarán al gráfico.
- `Home` mostrará resumen, selección de jugador y los tres gráficos. No seleccionar un jugador inventado: usar catálogo real y una selección explícita; antes de elegir mostrar la invitación correspondiente. Reutilizar cotizaciones en detalle y los dos gráficos personales en portfolio.
- `PlayerProfileDialog` mantendrá identidad y estadísticas disponibles y añadirá bloques «Mercado» y «Mi inversión». Mostrar cotización actual, histórico y consulta por fecha cuando el contrato lo permita; mostrar promedio, cantidad, valor actual y PnL únicamente desde la posición de la sesión.
- `PortfolioPage` listará posiciones con acceso al detalle y operaciones. Saldo, valor de cartera y patrimonio tendrán etiquetas distintas; no sumar otra vez PnL ni reconstruir costo de adquisición. Formatear valores recibidos no equivale a recalcularlos.
- Variación porcentual: solo con datos comparables y período identificado conforme a DEP-001. Dona: distribución por valor actual, no por simple cantidad de tokens; los porcentajes de presentación usan valores contractuales completos y no alteran valuaciones. Patrimonio histórico: no derivarlo de operaciones y cartera actual; queda bloqueado por DEP-004.
- Un único punto se representa como tal. Ausencia de serie, cartera vacía, fallo y dato no provisto deben poder distinguirse. No interpolar una historia financiera ni inventar intervalos para ofrecer selectores de períodos.

### Operaciones, historial, ranking y alertas

- Un `TradeDialog` compartido recibe jugador y tipo de operación desde el contenedor, consulta precio vigente y permite cantidad entera positiva y confirmación explícita. Validar límites solo si están informados. Precio estimado y monto ejecutado son conceptos separados; precisión y cálculo estimado quedan sujetos al contrato.
- Mantener un bloqueo de envío además de deshabilitar el botón para evitar duplicados por clic/teclado. No usar éxito optimista. Una orden confirmada inicia refresco de los recursos afectados; si falla un GET, ofrecer reintentar ese GET sin repetir el POST. Una respuesta de orden incierta requiere informar la incertidumbre; no inventar idempotency keys ni reconciliación inexistente.
- `TransactionsPage` muestra solo campos contractuales y acceso al detalle. `RankingPage` usa el orden y la estrategia recibidos; no calcula scores. Paginación, filtros y orden temporal de historial se adaptarán al contrato confirmado, no se inventan parámetros.
- `AlertsPage` y `PriceAlertNotifications` comparten el mismo ciclo de sesión y `TradeDialog`. Al operar desde alerta, volver a consultar precio vigente; el precio del evento no basta. Para evitar diálogos superpuestos, cerrar temporalmente el detalle al abrir operaciones y restaurar contexto/foco al terminar.
- Un hook de notificaciones se instanciará una sola vez en el contenedor autenticado. Su transporte, autenticación, deduplicación y recuperación se diseñarán al resolver DEP-006. No poner JWT en URLs ni elegir una biblioteca SSE/WebSocket. Reconexión no debe bloquear navegación y el cierre de sesión cancela la conexión y descarta eventos tardíos.

### Archivos previstos para una implementación posterior

Esta lista no autoriza editarlos en esta ejecución. Los nombres nuevos identifican responsabilidades propuestas, no archivos existentes.

| Grupo | Archivos existentes a adaptar | Archivos nuevos previstos |
|---|---|---|
| Entrada y layout | `front/src/main.jsx`, `pages/LoginPage.jsx`, `pages/Home.jsx`, `components/DashboardLayout.jsx`, `components/index.js`, `style.css` | `front/src/App.jsx`, `components/MarketWorkspace.jsx`, `pages/PlayersPage.jsx` |
| Catálogo/detalle | `hooks/usePlayerCatalog.js`, `components/PlayerTable.jsx`, `components/PlayerProfileDialog.jsx` | `components/PlayerInvestmentSummary.jsx` |
| Sesión/HTTP | `services/api.js`, `services/auth.js`, `services/session.js`; `services/players.js` solo al confirmar cotizaciones | Hooks de lectura por capacidad y servicios de cotizaciones, portfolio, órdenes, historial, ranking y alertas, únicamente tras resolver sus contratos |
| Finanzas y operaciones | Componentes Bootstrap y mensajes reutilizados | `pages/PortfolioPage.jsx`, `pages/TransactionsPage.jsx`, `pages/RankingPage.jsx`, `components/TradeDialog.jsx`, `components/charts/QuoteHistoryChart.jsx`, `PortfolioAllocationChart.jsx`, `WealthHistoryChart.jsx` |
| Alertas | Contenedor autenticado y modal compartido | `pages/AlertsPage.jsx`, `components/PriceAlertNotifications.jsx`, hook de conexión condicionado al transporte |
| Pruebas | `front/src/test/auth.test.jsx`, `home.test.jsx`, `player-profile.test.jsx`, `player-table.test.jsx` | Pruebas de rutas, sesión, servicios financieros, estados de gráficos, órdenes y alertas en `front/src/test/` |

No prever cambios de configuración, backend o pipeline para hacer funcionar contratos ausentes. `package.json` y lockfile solo podrían cambiar en una etapa explícitamente aprobada de incorporación de biblioteca; este plan no autoriza esa instalación.

## Dependencias y secuencia de desbloqueo

| Dependencia | Resolución necesaria antes de tareas de integración | Impacto |
|---|---|---|
| DEP-001 | Verificar API real de cotización actual, histórico y fecha: URL exacta, campos, unidades, estrategia, períodos y respuestas sin datos | Gráfico de jugador, consulta de fecha, precio vigente, variaciones |
| DEP-002 | Confirmar entrada/salida de compra/venta, confirmación, códigos, límites, precisión y resultado incierto | Modal, estimación y refresco; no asumir ejecución desde un esquema documental |
| DEP-003 | Confirmar identidad/titularidad, portfolio e historial, costo/PnL, nulos, fechas y paginación | Posición personal, saldo, resumen, tabla y distribución |
| DEP-004 | Backend define y proporciona serie histórica patrimonial suficiente | Tercer gráfico; las transacciones solas no desbloquean esta dependencia |
| DEP-005 | Contrato real de ranking y estrategia activa | Tabla de ranking; no sustituir por orden local |
| DEP-006 | CRUD, payloads de eventos, transporte, autenticación, recuperación y duplicados | Gestión y notificaciones en tiempo real; no seleccionar SSE/WebSocket por anticipado |
| DEP-007 | Acuerdo backend/cliente/proxy para quitar `/v1` | Mantener rutas actuales mientras tanto; no bloquea adaptar el catálogo actual |
| DEP-008 | Evaluar y aprobar biblioteca compatible y fixtures derivadas de contratos | Renderizadores de gráficos; ninguna dependencia aprobada por este documento |

Antes de crear tareas debe revisarse esta matriz con el responsable backend y distinguir pendientes de diseño contractual de disponibilidad de implementación. Acordar también el tratamiento de la discrepancia constitucional sin migrar el stack. La referencia visual aprobada no fue aportada: la fidelidad a ese render requiere poder revisarlo; preservar identidad actual no depende de ello.

Secuencia técnica prevista, no lista de tareas:

1. Resolver alcance de los contratos y biblioteca; mantener visibles los bloqueos de cada capacidad.
2. Tras aprobación de implementación, separar catálogo/dashboard, navegación protegida, estados y aislamiento de sesión reutilizando la base actual.
3. Integrar cotizaciones y cartera cuando sus contratos estén listos; incorporar el histórico patrimonial solo con su fuente válida.
4. Integrar órdenes y actualización posterior, historial y ranking sobre los contratos confirmados.
5. Integrar alertas y canal acordado usando el mismo flujo de operaciones; completar validación funcional y visual.

No generar tareas ejecutables para APIs desconocidas. Si se decide avanzar solo con estructura frontend, requerirá delimitar ese lote y reconocer que no completa la Entrega 2; no cambiar la checklist para aparentar preparación total.

## Validation Strategy

### Pruebas necesarias para la futura implementación

| Cobertura | Casos y evidencia requerida |
|---|---|
| FR-009, FR-011, FR-016 | Regresión de login/registro, redirección a dashboard, rutas protegidas, menú activo, catálogo separado, búsqueda/paginación y filtros locales. Base `/api/v1` intacta. |
| FR-001, FR-002, FR-015 | Tres gráficos con datos completos, cartera vacía, punto único, fecha sin cotización, campos ausentes, errores parciales e histórico insuficiente. No usar ceros ni series inventadas. |
| FR-004, FR-012, FR-014 | Mercado y promedio personal distintos, usuario sin tokens frente a error de posición, saldo 50/cartera 35/patrimonio 85/PnL 15 como fixture identificada, sin sumar PnL otra vez. |
| FR-013 | Respuestas demoradas y eventos posteriores a logout/cambio de usuario; ningún dato anterior visible. Probar cancelación y descarte aunque la cancelación no surta efecto. |
| FR-003, FR-008, FR-017 | Cantidad inválida, límite conocido, errores por código, confirmación explícita, precio actualizado y bloqueo de doble envío. Orden confirmada más fallo de GET: reintentar no envía otro POST. Respuesta incierta no anuncia éxito. |
| FR-005, FR-006, FR-010 | Historial con campos reales y detalle; ranking conserva orden backend; ninguna regla de PnL, score o historial patrimonial reconstruida en cliente. |
| FR-007 | Crear/consultar/eliminar alertas con confirmación; evento abre modal y consulta precio, desconexión no bloquea la UI, recuperación según contrato y cierre de sesión descarta el canal. |
| SC-001 a SC-007 | Pruebas anteriores, revisión de teclado/foco y 360/1280 px, medición en navegador de gráficos desde datos completos y avisos desde recepción. No afirmar tiempos backend ni accesibilidad visual solo con jsdom. |

Mantener pruebas de auth, catálogo y detalle existentes; trasladar el propósito de `home.test.jsx` al catálogo extraído y agregar pruebas del nuevo Home. Usar spies/mocks de Axios y promesas controladas como ya hace el repositorio; no agregar otra librería de mocks por defecto. Fixtures etiquetadas, sin credenciales reales ni datos financieros de demostración inyectados en producción. No afirmar integración real porque un mock pase.

### Guía de validación posterior

Con dependencias existentes instaladas, desde `front/`: `npm test`, `npm run test:coverage` y `npm run build`, conservando configuración. Esta planificación no ejecuta ni modifica tests o configuración. Cuando el backend financiero exista, verificar flujos contra sus rutas reales con usuarios de prueba y permisos; la prueba de autorización backend debe confirmar rechazo del acceso a posiciones ajenas, además del aislamiento visual cliente.

Registrar por separado resultados unitarios, pruebas con APIs reales y revisión manual responsive/teclado. Las 11 pruebas aprobadas durante la auditoría anterior solo acreditaban el frontend previo. No cerrar criterios nuevos ni actualizar marcas de readiness sin evidencia.

### Revisión documental del plan

La arquitectura cubre FR-001 a FR-017 y remite a los 28 escenarios de la spec; los siete criterios de éxito se conservan como objetivos, no logros. Se mantienen abiertos los mismos DEP-001 a DEP-008 y el conflicto de stack de la checklist. Revisar formato de plantilla, enlaces locales, ausencia de payloads/URLs financieras inventadas y alcance del diff. El único artefacto autorizado ahora es este plan.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| JavaScript/Bootstrap frente a TypeScript/Tailwind declarados en §2 | Stack real e instrucción explícita del usuario y spec FR-016; evita rehacer la aplicación | Migrar no resuelve contratos financieros y contradice el alcance aprobado. La discrepancia de gobernanza permanece abierta. |
| Diseño financiero condicionado frente al gate de contratos completos | La solicitud exige documentar el plan y sus dependencias sin inventarlas | Simular que las APIs existen o crear contratos/backend desde esta feature excede el alcance. No se declara listo para tareas de integración. |
