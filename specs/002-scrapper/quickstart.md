# Quickstart: Microservicio de Datos Externos (WhoScored & Football-Data.org)

**Feature**: `002-player-scraper`  
**Date**: 2026-09-22  

Esta guía describe los pasos para inicializar, ejecutar y verificar el microservicio `/scraping` tanto en desarrollo local como dentro de la red Docker compartida con el backend y la base de datos.

---

## 1. Prerrequisitos

- **Node.js**: v20 LTS o superior
- **Docker & Docker Compose** (para integración en red con el backend)
- Dependencias de Chromium para Puppeteer (en Linux: paquetes `libnss3`, `libatk1.0-0`, etc. provistos por la imagen oficial de Docker)

---

## 2. Configuración de Variables de Entorno

Crear el archivo `.env` en `/scraping` a partir de `.env.example`:

```bash
cd scraping
cp .env.example .env
```

Variables mínimas requeridas:
```ini
PORT=3000
API_KEY=cbo_secret_scraping_key_12345
FOOTBALL_DATA_API_KEY=tu_token_de_football_data_org
SCRAPE_TIMEOUT_MS=15000
MAX_RETRIES=3
MAX_CONCURRENT_SCRAPES=2
```

---

## 3. Instalación y Ejecución Local

```bash
cd scraping
npm install
npm run dev
```

El servidor estará escuchando en `http://localhost:3000`.

---

## 4. Ejecución Hermética de Tests (CI)

La suite de pruebas se ejecuta de forma completamente aislada de la red, utilizando fixtures HTML (para WhoScored) y fixtures JSON (para Football-Data.org):

```bash
cd scraping
npm test
```

Verificación esperada:
- 100% de los tests unitarios e integrados pasan sin conexión a Internet.
- No se realizan llamadas salientes a dominios externos en CI.

---

## 5. Ejecución con Docker Compose

Para levantar el microservicio junto con la infraestructura preexistente (`db` y `backend`):

```bash
docker compose up --build scraper
```

El microservicio se conecta a `cbo_network` como servicio `scraper` en el puerto interno `3000`.

---

## 6. Verificación de Endpoints (cURL)

### 6.1 Consulta Exitosa de Estadísticas de Jugador (WhoScored)
```bash
curl -X GET "http://localhost:3000/players/29400/stats" \
  -H "X-API-Key: cbo_secret_scraping_key_12345"
```
**Respuesta esperada (HTTP 200)**:
```json
{
  "goals": 10,
  "assists": 5,
  "shots": 40,
  "keyPasses": 28,
  "dribbles": 22,
  "tackles": 12,
  "rating": 7.42,
  "minutosJugados": 1620,
  "tarjetasAmarillas": 2,
  "tarjetasRojas": 0
}
```

### 6.2 Consulta de Fixture y Resultados de Competición (Football-Data.org)
```bash
curl -X GET "http://localhost:3000/competitions/PL/matches?dateFrom=2026-09-01&dateTo=2026-09-30" \
  -H "X-API-Key: cbo_secret_scraping_key_12345"
```
**Respuesta esperada (HTTP 200)**:
Array de objetos `MatchFixture` con fecha UTC, estado, equipos y resultado.

### 6.3 Consulta de Alineación de Partido (Fallback WhoScored)
```bash
curl -X GET "http://localhost:3000/lineups?homeTeam=Arsenal&awayTeam=Chelsea&date=2026-09-20" \
  -H "X-API-Key: cbo_secret_scraping_key_12345"
```
**Respuesta esperada (HTTP 200)**:
```json
{
  "source": "WHOSCORED",
  "date": "2026-09-20",
  "homeTeam": {
    "name": "Arsenal",
    "formation": "4-3-3",
    "startingXI": [ ... ],
    "bench": [ ... ]
  },
  "awayTeam": {
    "name": "Chelsea",
    "formation": "4-2-3-1",
    "startingXI": [ ... ],
    "bench": [ ... ]
  }
}
```

---

## 7. Verificación de Manejo de Errores

### 7.1 Petición No Autorizada (HTTP 401)
```bash
curl -i -X GET "http://localhost:3000/players/29400/stats"
```
**Respuesta esperada**:
```json
{
  "error": "UNAUTHORIZED",
  "message": "Cabecera X-API-Key no proporcionada o inválida.",
  "status": 401,
  "timestamp": "2026-09-22T20:10:00.000Z"
}
```

### 7.2 Jugador Inexistente (HTTP 404)
```bash
curl -i -X GET "http://localhost:3000/players/999999999/stats" \
  -H "X-API-Key: cbo_secret_scraping_key_12345"
```
**Respuesta esperada**:
```json
{
  "error": "PLAYER_NOT_FOUND",
  "message": "No se encontró el jugador especificado en la fuente externa.",
  "status": 404,
  "timestamp": "2026-09-22T20:10:00.000Z"
}
```

### 7.3 Partido No Encontrado para Alineación (HTTP 404)
```bash
curl -i -X GET "http://localhost:3000/lineups?homeTeam=EquipoInexistenteA&awayTeam=EquipoInexistenteB&date=2026-09-20" \
  -H "X-API-Key: cbo_secret_scraping_key_12345"
```
**Respuesta esperada**:
```json
{
  "error": "MATCH_NOT_FOUND",
  "message": "No se encontró el partido especificado en WhoScored para la fecha indicada.",
  "status": 404,
  "timestamp": "2026-09-22T20:10:00.000Z"
}
```
