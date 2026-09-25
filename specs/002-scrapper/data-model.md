# Data Model: Microservicio de Datos Externos (WhoScored & Football-Data.org)

**Feature**: `002-player-scraper`  
**Date**: 2026-09-22  
**Status**: Draft  

Este documento especifica los modelos de datos, estructuras de transferencia (DTOs) y validaciones para el microservicio `/scraping`. El microservicio es estrictamente de solo lectura y stateless; no mantiene persistencia de base de datos propia.

---

## 1. Entidades y Modelos de Dominio

### 1.1 `PlayerStats` (Estadísticas Crudas de Jugador)
Representa las 10 métricas cuantitativas extraídas desde WhoScored requeridas por el backend para el cálculo de cotizaciones.

| Campo | Tipo | Requerido | Restricciones / Validación | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `goals` | `Integer` | Sí | `>= 0` | Cantidad total de goles convertidos |
| `assists` | `Integer` | Sí | `>= 0` | Cantidad total de asistencias otorgadas |
| `shots` | `Integer` | Sí | `>= 0` | Cantidad total de disparos al arco |
| `keyPasses` | `Integer` | Sí | `>= 0` | Pases clave que crearon ocasiones de gol |
| `dribbles` | `Integer` | Sí | `>= 0` | Regates y gambetas completadas |
| `tackles` | `Integer` | Sí | `>= 0` | Entradas defensivas e intercepciones exitosas |
| `rating` | `Float` | Sí | `>= 0.0` | Calificación promedio del jugador en WhoScored |
| `minutosJugados` | `Integer` | Sí | `>= 0` | Total de minutos jugados en el período |
| `tarjetasAmarillas` | `Integer` | Sí | `>= 0` | Total de tarjetas amarillas recibidas |
| `tarjetasRojas` | `Integer` | Sí | `>= 0` | Total de tarjetas rojas recibidas |

**Regla de normalización**: Si un dato no figura o está listado como `"-"` o vacío en WhoScored, el servicio lo normaliza a `0` (o `0.0` para el rating), garantizando que nunca se retornen campos nulos ni tipos no numéricos.

---

### 1.2 `MatchFixture` (Partidos y Resultados)
Representa la información normalizada de un partido obtenida desde Football-Data.org.

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `id` | `Integer` | Sí | Identificador único del partido en Football-Data.org |
| `competition` | `String` | Sí | Código de la competición (ej. `PL`, `BL1`, `PD`, `SA`, `FL1`) |
| `utcDate` | `String` | Sí | Fecha y hora del partido en formato ISO-8601 UTC |
| `status` | `String` | Sí | Estado (`SCHEDULED`, `TIMED`, `IN_PLAY`, `PAUSED`, `FINISHED`, `POSTPONED`, `CANCELLED`) |
| `matchday` | `Integer` | No | Número de jornada o fecha del torneo |
| `homeTeam` | `TeamRef` | Sí | Objeto con datos del equipo local |
| `awayTeam` | `TeamRef` | Sí | Objeto con datos del equipo visitante |
| `score` | `ScoreInfo` | No | Marcador del partido (nulo si el partido no inició) |

#### Estructuras secundarias:
- **`TeamRef`**:
  - `id` (`Integer`): ID del equipo en Football-Data.org.
  - `name` (`String`): Nombre completo oficial del club.
  - `shortName` (`String`, opcional): Nombre corto o abreviado.
  - `tla` (`String`, opcional): Sigla de tres letras (ej. `"ARS"`).
- **`ScoreInfo`**:
  - `winner` (`String`): Ganador (`"HOME_TEAM"`, `"AWAY_TEAM"`, `"DRAW"`, `null`).
  - `duration` (`String`): Duración (`"REGULAR"`, `"EXTRA_TIME"`, `"PENALTY_SHOOTOUT"`).
  - `fullTime` (`Goals`): Goles finales del tiempo reglamentario (`home`: `Integer`, `away`: `Integer`).
  - `halfTime` (`Goals`, opcional): Goles al entretiempo (`home`: `Integer`, `away`: `Integer`).

---

### 1.3 `MatchLineup` (Alineación Táctica y Plantilla)
Representa las alineaciones de ambos equipos para un partido obtenidas exclusivamente mediante fallback de scraping hacia WhoScored.

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `source` | `String` | Sí | Origen de la información. Valor fijo: `"WHOSCORED"` |
| `date` | `String` | Sí | Fecha del partido en formato `YYYY-MM-DD` |
| `homeTeam` | `TeamLineup` | Sí | Alineación del equipo local |
| `awayTeam` | `TeamLineup` | Sí | Alineación del equipo visitante |

#### Estructura `TeamLineup`:
- `name` (`String`): Nombre del equipo según WhoScored.
- `formation` (`String`): Esquema táctico (ej. `"4-3-3"`, `"4-2-3-1"`).
- `startingXI` (`Array<PlayerLineupEntry>`): Lista de los 11 jugadores titulares.
- `bench` (`Array<PlayerLineupEntry>`): Lista de los jugadores suplentes.

#### Estructura `PlayerLineupEntry`:
- `id` (`String` / `Integer`): Identificador del jugador en WhoScored si está disponible.
- `name` (`String`): Nombre del jugador.
- `shirtNumber` (`Integer`, opcional): Número de camiseta.
- `position` (`String`): Posición táctica (ej. `"GK"`, `"DC"`, `"MC"`, `"FW"`).

---

### 1.4 `ErrorResponse` (Estructura Unificada de Error)
Estructura estándar compatible con las directrices de la constitución del proyecto para todas las respuestas de error HTTP del microservicio.

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `error` | `String` | Sí | Código de error estable y tipado |
| `message` | `String` | Sí | Mensaje explicativo en español claro |
| `status` | `Integer` | Sí | Código de estado HTTP numérico |
| `timestamp` | `String` | Sí | Marca de tiempo ISO-8601 de ocurrencia |

#### Taxonomía de Códigos de Error:
- **`UNAUTHORIZED`** (`401`): La cabecera `X-API-Key` no fue enviada o no coincide con la configurada internamente.
- **`INVALID_REQUEST_PARAMS`** (`400`): Parámetros de ruta o query inválidos o ausentes.
- **`PLAYER_NOT_FOUND`** (`404`): El identificador de jugador no existe en WhoScored.
- **`MATCH_NOT_FOUND`** (`404`): No se encontró el partido especificado en la fuente externa correspondiente (sea por identificador en Football-Data.org o por coincidencia de equipos y fecha en WhoScored).
- **`SCRAPE_TIMEOUT`** (`504`): La navegación y extracción en WhoScored superó el tiempo máximo tras reintentos.
- **`SCRAPE_BLOCKED`** (`502`): WhoScored bloqueó el acceso (anti-bot, CAPTCHA, 403, 429).
- **`RATE_LIMIT_EXCEEDED`** (`429`): Se superó el límite de peticiones de Football-Data.org.
- **`EXTERNAL_API_AUTH_ERROR`** (`502`): Error de autenticación (401/403) contra la API de Football-Data.org.

---

### 1.5 `HealthResponse` (Estado del Servicio)
Respuesta del endpoint público `/health` para verificación de estado del contenedor en Docker.

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `status` | `String` | Sí | Estado operativo (`"UP"`) |
| `timestamp` | `String` | Sí | Marca de tiempo ISO-8601 |

