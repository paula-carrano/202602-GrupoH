# Frontend financiero — Entrega 2

React 19, Bootstrap, Axios y Recharts. El backend se consume sin modificaciones.

## Ejecutar

```sh
npm ci
npm run dev
```

Vite reenvía `/api` a `http://localhost:8080`. Para otro despliegue definir
`VITE_API_BASE_URL` al construir la aplicación. En producción servir `/api` con
un reverse proxy al backend, incluyendo streaming SSE sin buffering. El build
estático por sí solo no configura un proxy.

```sh
npm test
npm run build
```

## Páginas y contratos

| Página | Datos y acciones |
|---|---|
| Inicio | `GET /users/{id}/portfolio`, catálogo, cotizaciones e historial para los gráficos |
| Jugadores / perfil | `GET /players`, `GET /players/{id}`, `GET /players/{id}/quotes` con paginación o `date` |
| Portfolio | Posiciones, resumen, `POST /users/{id}/credits/topup`, `POST /orders/buy` y `/orders/sell` |
| Historial | `GET /users/{id}/transactions?page=…&size=20` |
| Ranking | `GET /players/ranking`, detalle y compra; conserva el orden del servidor |
| Alertas | `GET/POST /alerts`, `DELETE /alerts/{id}`, `GET /alerts/stream` |

Todas las rutas llevan el prefijo `/api`. Identidad personal desde el claim
`userId` del JWT; autorización efectiva a cargo del backend. Las peticiones usan
Bearer y se invalidan al cambiar o cerrar sesión. El stream usa `fetch` para
enviar el encabezado Authorization, con reconexión y descarte de avisos duplicados.
No utiliza tokens en la URL ni realiza operaciones automáticamente.

Los formularios requieren confirmación y bloquean envíos duplicados. Ante un
timeout o error de servidor en una mutación, el resultado se considera incierto:
no se reintenta automáticamente. Consultar portfolio e historial antes de repetir.
Una cancelación de la petición en el navegador no revierte una operación que el
servidor ya haya ejecutado.

## Gráficos

- Cotización: Recharts `LineChart`, con todos los puntos disponibles del período.
- Distribución: `PieChart` en dona por valor de mercado de las posiciones, sin saldo.
- Patrimonio: dos líneas, saldo y saldo más valor de cartera. Tablas accesibles
  acompañan a los tres gráficos; estados vacíos no inventan series.

### Reconstrucción patrimonial autorizada

No hay endpoint patrimonial histórico. Se leen todas las páginas de operaciones,
el portfolio actual y todas las cotizaciones de cada jugador involucrado,
incluidos los vendidos completamente. Las descargas por jugador tienen un máximo
de cuatro solicitudes concurrentes.

Partiendo del estado actual se revierten operaciones para obtener tenencias y
saldo al cierre de cada día UTC. Cada tenencia se valúa con la última cotización
anterior o igual al cierre; antes de la primera cotización se usa 1 CRD, igual que
el contrato del backend. El día en curso es una observación actual, no un cierre
futuro. Se ofrece 30/90 días o todo el historial. `decimal.js` evita errores de
aritmética binaria en sumas, multiplicaciones y validaciones; los números enviados
al gráfico se redondean a cuatro decimales.

Se valida la continuidad de saldos y tokens, los importes de cada operación y la
valuación actual. Páginas duplicadas, faltantes o que cambian de tamaño durante la
consulta se rechazan. Si no concilia, el gráfico muestra el error y permite
reintentar; no se rellena con ceros. No se infiere historia anterior a la primera
operación disponible ni se garantiza detectar registros ausentes que sean
indistinguibles de una historia completa. La API no ofrece una lectura atómica
entre recursos: operar mientras se descarga puede exigir actualizar la vista.

La reconstrucción es de presentación. No decide precios de ejecución, PnL,
estrategias ni rankings. Costos y ganancias/pérdidas visibles provienen del backend.

## Demo con backend real

Requisitos: backend y base operativos, catálogo con jugadores activos y pool de
tokens configurado. Para el recálculo, disponer de una cuenta autorizada en Swagger.

1. Registrar una cuenta e iniciar sesión. Verificar catálogo y perfil.
2. En Portfolio, recargar créditos y mostrar el identificador de la transacción.
3. Comprar tokens desde el perfil o ranking. Mostrar precio ejecutado, saldo y
   tenencia actualizados. Vender una parte y verificar Historial.
4. En Inicio seleccionar un jugador: mostrar cotizaciones, dona y patrimonio.
   Consultar una fecha y cambiar períodos. Un usuario nuevo puede tener un solo
   punto: usar una cuenta con historia real para demostrar evolución temporal.
5. Crear una alerta con un umbral alcanzable y mantener la sesión abierta.
6. Ejecutar el recálculo existente desde Swagger con credenciales autorizadas.
   Mostrar el evento en React y el cambio de estado de la alerta.
7. Desde la notificación abrir Comprar o Vender y confirmar manualmente. Mostrar
   el registro de la operación y la actualización del portfolio.
8. Cerrar sesión y verificar que desaparezcan datos personales y notificaciones.

**Producto:** la alerta reduce la necesidad de revisar precios continuamente y
permite abrir una operación desde el aviso, manteniendo el control del usuario.
**Técnica:** SSE cubre el envío unidireccional; las órdenes continúan usando HTTP.
Hay una suscripción por sesión, lectura incremental, heartbeat, reconexión y
consulta del listado al reconectar. El stream no ofrece replay; el listado permite
ver alertas disparadas mientras el usuario estuvo desconectado.

## Prueba visual reproducible

Con Vite activo, Python Playwright y Chrome instalados:

```sh
python src/test/browser_market.py
```

Esta prueba usa respuestas **simuladas y explícitamente identificadas**, verifica
Recharts y navegación a 360/1280 píxeles y guarda capturas en `artifacts/market-ui`.
No sustituye la demo contra backend real. La administración y las API keys quedan
fuera del frontend.

### Validación realizada — 2026-10-29

- Vitest: 16 archivos, 141 pruebas aprobadas.
- Build de producción: aprobado. Vite advierte que el bundle principal supera
  500 kB; no impide generar ni ejecutar la aplicación.
- Chrome con API simulada: gráficos, navegación y diálogo de operación verificados
  a 360 y 1280 píxeles, sin errores JavaScript ni desbordamiento de página.
- Demo contra backend real: pendiente. No había servicio en `localhost:8080` y
  el motor de Docker Desktop no estaba activo durante esta validación.
