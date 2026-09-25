# Research & Technical Decisions: Microservicio de Datos Externos (WhoScored & Football-Data.org)

**Feature**: `002-player-scraper`  
**Date**: 2026-09-22  
**Status**: Completed  

Este documento consolida las investigaciones técnicas, decisiones de arquitectura y patrones adoptados para el microservicio de scraping y consumo de APIs de fútbol (`/scraping`).

---

## 1. Motor de Navegación, Scraping e Imagen Base Docker

### Contexto
El microservicio debe extraer métricas estadísticas avanzadas individuales de jugadores y alineaciones tácticas de partidos desde WhoScored. WhoScored utiliza renderizado dinámico en el cliente y protecciones anti-scraping.

### Decisión
- **Motor**: **Puppeteer** con Chromium headless.
- **Imagen Base Docker**: **`node:20-slim`** (Debian bookworm-slim).

### Justificación
- **Descarte explícito de Alpine Linux**: Puppeteer descarga por defecto una versión binaria de Chromium precompilada contra `glibc`. Alpine Linux utiliza `musl libc`, lo que genera incompatibilidades binarias y fallos críticos en el arranque del navegador a menos que se instale Chromium desde los repositorios de paquetes del sistema operativo y se configure manualmente la ruta mediante `PUPPETEER_EXECUTABLE_PATH`. Esta última opción introduce fragilidad entre versiones y complica el mantenimiento.
- **Ventajas de `node:20-slim`**: Provee una base Debian oficial liviana (~200MB) totalmente compatible con `glibc`. Solo requiere instalar las dependencias nativas mínimas de Chromium (`libnss3`, `libatk1.0-0`, `libatk-bridge2.0-0`, `libcups2`, `libdrm2`, `libxcomposite1`, `libxdamage1`, `libxrandr2`, `libgbm1`, `libasound2`), asegurando un entorno de ejecución robusto, estable y reproducible.
- **Estrategia de Docker Healthcheck (Decisión Opción B)**: La imagen `node:20-slim` no incluye binarios como `curl` ni `wget` por diseño para minimizar la superficie de ataque y el tamaño de la imagen. En lugar de forzar la instalación y mantenimiento de un paquete de sistema adicional vía `apt-get`, se adopta la **Opción B**: ejecutar el healthcheck directamente mediante el runtime de Node.js presente en la imagen base (`node -e "require('http').get('http://localhost:3000/health', r => process.exit(r.statusCode === 200 ? 0 : 1)).on('error', () => process.exit(1))"`). Esto elimina dependencias de paquetes externos y garantiza consistencia absoluta.
- **Descarte de Cheerio y Playwright**:
  - *Cheerio*: Rechazado porque WhoScored carga sus tablas y alineaciones de forma asíncrona mediante scripts de cliente que requieren un motor JavaScript real.
  - *Playwright*: Descartado por su mayor consumo de memoria y tamaño de imagen Docker superior, innecesario para un microservicio con un solo navegador especializado.

---

## 2. Pinneo Estricto de Dependencias y Reproducibilidad (`npm ci`)

### Contexto
De acuerdo con los principios de reproducibilidad del proyecto aplicados a las imágenes de Docker (`postgres:16.15-alpine`, `eclipse-temurin:17-jre`), no se deben permitir dependencias flotantes en producción.

### Decisión
- Todas las dependencias en `package.json` deben tener **versiones exactas fijadas a nivel de patch**, prohibiendo el uso de prefijos flotantes como `^` o `~`.
  - Ejemplo: `express: "4.21.0"`, `puppeteer: "23.4.0"`, `bottleneck: "2.19.5"`, `axios: "1.7.7"`.
- El `Dockerfile` del microservicio debe utilizar obligatoriamente **`npm ci --omit=dev`** (y `npm ci` para testing en entornos CI) basándose en un archivo `package-lock.json` commiteado en el repositorio, garantizando builds determinísticos, inmutables y rápidos aprovechando el cache de capas de Docker.

---

## 3. Integración con Football-Data.org (API REST v4)

### Contexto
El microservicio debe obtener partidos, fixtures y resultados de las 5 ligas europeas principales (Premier League `PL`, Bundesliga `BL1`, La Liga `PD`, Serie A `SA`, Ligue 1 `FL1`).

### Decisión
Consumo directo vía cliente HTTP con versión fijada (`axios: "1.7.7"`) apuntando a `https://api.football-data.org/v4/`, enviando la cabecera `X-Auth-Token` configurada desde `FOOTBALL_DATA_API_KEY`.

### Justificación
- API REST estándar que devuelve JSON estructurado.
- Se confirma que el **plan gratuito** no incluye formaciones ni alineaciones (`lineup`), las cuales retornan vacías o no soportadas en este tier.
- La confirmación de este gap valida la necesidad del fallback hacia WhoScored mediante el endpoint dedicado `GET /lineups`.

---

## 4. Gestión de Concurrencia y Throttling (Rate Limiting)

### Contexto
1. Football-Data.org limita el plan gratuito a **10 peticiones por minuto**.
2. WhoScored es susceptible de bloquear IPs si detecta ráfagas de scraping simultáneas.

### Decisión
Uso de la librería **Bottleneck** (`bottleneck: "2.19.5"`) para la gestión de colas y rate limiting:

1. **Limitador Football-Data.org**:
   - `reservoir: 10` peticiones.
   - `reservoirRefreshAmount: 10`.
   - `reservoirRefreshInterval: 60000` (1 minuto).
   - `minTime: 6000` ms (espaciado preventivo de 6 segundos entre llamadas).
2. **Limitador WhoScored (Scraping Pool)**:
   - `maxConcurrent: MAX_CONCURRENT_SCRAPES` (configurable por entorno, default: 2).
   - `minTime: 1000` ms entre aperturas de página.

### Justificación
`Bottleneck` es una solución madura en Node.js que opera enteramente en memoria sin dependencias externas (no requiere Redis ni bases de datos), ideal para un microservicio stateless, garantizando que el sistema no supere las 10 req/min de Football-Data.org ni sature WhoScored.

---

## 5. Política de Reintentos y Backoff Exponencial

### Contexto
Las conexiones hacia WhoScored pueden experimentar demoras de red, bloqueos transitorios o lentitud en el renderizado.

### Decisión
Módulo de reintentos con backoff exponencial y jitter:
- Reintentos: `MAX_RETRIES` (default: 3).
- Factor base: 1000 ms (esperas progresivas de ~1s, 2s, 4s).
- Timeout global por petición: `SCRAPE_TIMEOUT_MS` (default: 15000 ms).
- Clasificación de errores:
  - Si el error es por timeout tras reintentos → `504 SCRAPE_TIMEOUT`.
  - Si se detecta status 403, 429, CAPTCHA o bloqueo de antibot → `502 SCRAPE_BLOCKED`.
  - Si la página devuelve 404 o no existe perfil → `404 PLAYER_NOT_FOUND` / `404 MATCH_NOT_FOUND`.

---

## 6. Algoritmo de Normalización y Matching de Equipos para Alineaciones

### Contexto
Dado que Football-Data.org y WhoScored no comparten IDs de partidos, el endpoint `GET /lineups?homeTeam=...&awayTeam=...&date=...` debe buscar el encuentro por nombres de equipo y fecha de forma *best-effort*.

### Decisión
Normalizador de cadenas de texto de equipos con las siguientes fases:
1. Conversión a minúsculas y eliminación de diacritics/acentos (`NFD` regex).
2. Eliminación de puntuación (`.`, `-`, `'`, `/`).
3. Supresión de prefijos y sufijos de clubes habituales: `\b(fc|cf|afc|sc|ac|rc|cd|club|de|la|el|the)\b`.
4. Búsqueda y comparación en la cartelera de WhoScored para la fecha dada (`YYYY-MM-DD`).
5. Si no hay coincidencia exacta de los dos equipos normalizados, retornar inmediatamente `404 MATCH_NOT_FOUND`. No aplicar heurísticas difusas complejas (fuzzy matching propenso a falsos positivos).

---

## 7. Estrategia de Testing Hermético en CI y Catálogo de Fixtures

### Contexto
La constitución y la especificación prohíben terminantemente llamadas a Internet o sitios en vivo durante la integración continua.

### Decisión
1. **WhoScored (Scraping)**:
   - Los tests unitarios e integrados inyectan snapshots HTML reales pre-grabados en `tests/fixtures/whoscored/`:
     - `player-stats.html`: Perfil completo con tablas de estadísticas cuantitativas (Happy Path de `GET /players/:id/stats`).
     - `player-empty.html`: Perfil con métricas vacías o guiones para verificar la normalización obligatoria a 0.
     - `not-found.html`: Respuesta HTML de perfil inexistente para verificar el código `PLAYER_NOT_FOUND` (HTTP 404).
     - `match-lineup.html`: Detalle de partido con formaciones tácticas, titulares y suplentes (Happy Path de `GET /lineups`).
     - `match-fixtures-date.html`: Cartelera de partidos de una fecha en WhoScored para validar el matching de nombres de equipo y el escenario `MATCH_NOT_FOUND` (cuando los equipos no coinciden).
   - **Simulación de `SCRAPE_BLOCKED` y `SCRAPE_TIMEOUT`**: Para estos dos escenarios de error no se utiliza un archivo HTML estático, sino intercepción a nivel de red y ciclo de vida de página en el test runner (simulando status HTTP 403/429 o inyectando un retraso artificial superior a `SCRAPE_TIMEOUT_MS`). Esto valida el comportamiento de resiliencia sin requerir capturas de CAPTCHAs reales en el repositorio.
2. **Football-Data.org (REST API)**:
   - Los tests mockean el cliente HTTP interceptando las llamadas salientes y respondiendo con los fixtures JSON guardados en `tests/fixtures/football-data/`:
     - `matches-pl.json`: Colección de partidos de una competición para `GET /competitions/:code/matches`.
     - `match-detail.json`: Detalle puntual de un partido para `GET /matches/:id`.
     - `rate-limit-429.json`: Respuesta de cuota excedida para validar `RATE_LIMIT_EXCEEDED` (HTTP 429).
     - `auth-error-401.json`: Respuesta de token rechazado para validar `EXTERNAL_API_AUTH_ERROR` (HTTP 502).

---

## 8. Estructura del Microservicio en `/scraping`

### Decisión
Estructura modular en Node.js respetando arquitectura en capas y separación de responsabilidades:

```text
scraping/
├── Dockerfile                   # Basado en node:20-slim con paquetes Chromium
├── package.json                 # Dependencias fijadas a patch exacto
├── package-lock.json            # Lockfile commiteado para uso con npm ci
├── .env.example                 # Plantilla documentada de variables de entorno
├── .gitignore                   # Exclusión de node_modules y .env
├── src/
│   ├── index.js                 # Entry point, configuración de Express y servidor
│   ├── config/                  # Lectura y validación de variables de entorno
│   ├── middleware/              # Auth X-API-Key, logger estructurado, error handler
│   ├── controllers/             # PlayerController, MatchController, LineupController
│   ├── services/
│   │   ├── whoscored/           # browserPool, playerScraper, lineupScraper, teamMatcher
│   │   └── footballData/        # apiClient, rateLimiter
│   ├── utils/                   # normalizer, retry, errors
│   └── models/                  # Schemas de validación y DTOs
└── tests/
    ├── unit/
    ├── integration/
    └── fixtures/
        ├── whoscored/           # Fixtures .html (player-stats, player-empty, not-found, match-lineup, match-fixtures-date)
        └── football-data/       # Fixtures .json (matches-pl, match-detail, rate-limit-429, auth-error-401)
```
