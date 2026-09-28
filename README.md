# 202602-GrupoH

Repositorio organizado por componentes:

| Carpeta     | Contenido                                                                                |
| ----------- | ---------------------------------------------------------------------------------------- |
| `back/`     | API Spring Boot con Java y Maven                                                         |
| `front/`    | Aplicación React con Vite                                                                |
| `scraping/` | Microservicio de extracción de datos externos (WhoScored & Football-Data.org) en Node.js |
| `specs/`    | Especificaciones y documentación del proyecto                                            |

## Backend

Requisitos: Java 17 o superior y PostgreSQL para ejecutar la API. Desde `back/`, configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` según [la guía de inicio](specs/001-entrega1-foundation/quickstart.md).

```powershell
cd back
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

En macOS o Linux, usar `./mvnw` en lugar de `.\mvnw.cmd`.

### Ejecución con Docker Compose (Backend + PostgreSQL + Scraper)

Permite ejecutar el backend, la base de datos PostgreSQL y el microservicio de scraping en contenedores aislados:

1. **Configurar variables de entorno**:

    ```bash
    cp .env.example .env
    ```

    _(Asegurarse de definir `POSTGRES_PASSWORD`, `JWT_SECRET`, `SCRAPER_API_KEY` y opcionalmente `FOOTBALL_DATA_API_KEY` en `.env`)_

2. **Construir y levantar servicios**:

    ```bash
    docker compose up --build -d
    ```

3. **Verificar estado y healthcheck**:

    ```bash
    docker compose ps
    curl http://localhost:8080/actuator/health
    curl http://localhost:3000/health
    ```

4. **Detener servicios**:
    - Conservando la persistencia de datos: `docker compose down`
    - Eliminando volúmenes: `docker compose down -v`

## Frontend

Requisito: Node.js 20.19+ o 22.12+ y npm.

```powershell
cd front
npm install
npm run dev
```

La URL local aparece en la salida de Vite. Para comprobar la compilación, ejecutar `npm run build`. En desarrollo, Vite redirige las solicitudes `/api` al backend en `http://localhost:8080`, por lo que ambos servicios deben estar activos para iniciar sesión y ver jugadores.

Rutas disponibles: `/login`, `/register`, `/home` y `/error`. El registro usa usuario, email y contraseña; el inicio de sesión requiere **usuario** y contraseña. `/home` se puede abrir sin sesión para revisar el diseño con datos de muestra. Al iniciar sesión, obtiene el catálogo real desde la API. Para conectar un backend en otro servidor, definir `VITE_API_BASE_URL` con la URL completa que termine en `/api/v1` antes de compilar el frontend.

## Microservicio de Scraping y Datos Externos (`/scraping`)

El backend puede importar planteles y persistir estadísticas con una actualización
semanal los lunes a las 15:00 de Argentina. Ver la [guía de sincronización del catálogo](docs/catalog-sync.md)
para configurar claves, ejecutar la primera importación desde Postman y vincular WhoScored.

Microservicio en Node.js 20 responsable de obtener estadísticas cuantitativas de jugadores y alineaciones desde WhoScored (vía Puppeteer con Chromium headless), e integrar fixtures y resultados de Football-Data.org (API REST v4).

### Ejecución Local

```bash
cd scraping
npm install
npm test
npm start
```

Documentación completa y guía de integración en [specs/002-scrapper/quickstart.md](specs/002-scrapper/quickstart.md).

### Adapter interno del backend

`ScrapingPort` permite consultar estadísticas de WhoScored, partidos por competencia y un partido por su ID externo. Se inyecta por constructor en servicios Java; no agrega endpoints públicos ni guarda datos. No consume `/lineups`.

| Variable del backend         | Valor predeterminado     | Uso                                                                           |
| ---------------------------- | ------------------------ | ----------------------------------------------------------------------------- |
| `SCRAPER_BASE_URL`           | `http://localhost:3000`  | URL del microservicio; Docker usa `http://scraper:3000`                       |
| `SCRAPER_API_KEY`            | Vacía en ejecución local | Clave interna enviada como `X-API-Key`                                        |
| `SCRAPER_CONNECT_TIMEOUT_MS` | `3000`                   | Tiempo máximo de conexión, positivo                                           |
| `SCRAPER_READ_TIMEOUT_MS`    | `90000`                  | Tiempo máximo de lectura, positivo; contempla reintentos internos del scraper |

En local, configurar el mismo valor para `SCRAPER_API_KEY` del backend y `API_KEY` del scraper. Docker Compose comparte `SCRAPER_API_KEY` entre ambos servicios (conserva el valor de desarrollo predeterminado del scraper). La clave `FOOTBALL_DATA_API_KEY` se configura únicamente en el scraper para consultar partidos.

El backend puede arrancar sin clave o sin el scraper disponible. Si falta la clave, cada intento de consulta falla con `ScrapingException.Kind.CONFIGURATION` antes de enviar HTTP. El adapter no realiza consultas al arrancar, reintentos ni fallback.

`whoscoredId` es el identificador externo de WhoScored, no el ID de la tabla local de jugadores. Los otros métodos son `getMatch(long matchId)` y `getCompetitionMatches(String competitionCode, LocalDate dateFrom, LocalDate dateTo)`. Las competencias admitidas son `PL`, `BL1`, `PD`, `SA` y `FL1`; se normalizan a mayúsculas. Las fechas son opcionales y no pueden formar un rango invertido. Los IDs deben ser positivos. Las entradas inválidas producen `IllegalArgumentException`.

Los errores de integración se distinguen mediante `ScrapingException.getKind()`: `CONFIGURATION`, `CONNECTION`, `TIMEOUT`, `INVALID_RESPONSE` y `REMOTE_HTTP`. Este último conserva el estado HTTP real en `getRemoteStatus()` y el código remoto en `getRemoteCode()` (o `REMOTE_HTTP_ERROR` si no es interpretable). Un `504` del scraper es un error remoto; un timeout del cliente es `TIMEOUT`. No se incluyen cuerpos remotos ni claves en las excepciones. Los consumidores deciden cómo presentar estos errores; no hay traducción nueva a respuestas HTTP públicas.
