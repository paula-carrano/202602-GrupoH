# Implementation Plan: Microservicio de Datos Externos (WhoScored & Football-Data.org)

**Branch**: `002-player-scraper` | **Date**: 2026-09-22 | **Spec**: [specs/002-scrapper/spec.md](file:///c:/Users/vhba0/Documents/Estudios/UNQ/TPI/CBO/Desarrollo/202602-GrupoH/specs/002-scrapper/spec.md)

**Input**: Feature specification from `/specs/002-scrapper/spec.md`

## Summary

Desarrollar un microservicio privado en Node.js ubicado en `/scraping`, consumido exclusivamente por el backend (`/back`) a través de un Adapter REST interno. El servicio integra dos fuentes de datos externas:
1. **WhoScored**: Extracción vía web scraping (Puppeteer en modo headless sobre imagen base `node:20-slim`) de 10 métricas cuantitativas crudas de rendimiento de jugadores (`GET /players/{whoscoredId}/stats`) y fallback de alineaciones tácticas mediante matching de equipos y fecha (`GET /lineups`), con manejo de cancelación de petición (`req.on('close')`).
2. **Football-Data.org**: Consumo de su API REST oficial v4 para fixtures y resultados de partidos de las 5 grandes ligas (`GET /competitions/{competitionCode}/matches` y `GET /matches/{matchId}`), aplicando control estricto de tasa (10 req/min) mediante Bottleneck y desacoplando la autenticación externa (`FOOTBALL_DATA_API_KEY`) de la interna (`X-API-Key`).
3. **Salud y Autenticación**: Endpoint público `GET /health` para Docker healthcheck y middleware transversal de autenticación `X-API-Key` activo desde el inicio para todos los endpoints de negocio.

## Technical Context

**Language/Version**: Node.js 20 LTS  
**Primary Dependencies**: Express 4.21.0, Puppeteer 23.4.0 (Chromium headless), Bottleneck 2.19.5 (throttling y control de concurrencia), Axios 1.7.7, Nock 13.5.5 (mocking HTTP)  
**Build & Reproducibility**: Dependencias de `package.json` fijadas a versión exacta de patch (sin `^` ni `~`); instalación determinística mediante `npm ci` a partir de `package-lock.json` commiteado  
**Storage**: N/A (Microservicio de solo lectura y stateless; sin base de datos propia)  
**Testing**: Jest, Supertest, Nock, fixtures locales grabados (HTML para WhoScored, JSON para Football-Data.org)  
**Target Platform**: Contenedor Docker basado en `node:20-slim` (Debian bookworm-slim) en la red compartida `cbo_network`  
**Project Type**: Microservicio Web REST interno (servicio-a-servicio)  
**Performance Goals**: Rechazo de peticiones no autorizadas en < 20 ms; respuesta de `GET /health` en < 10 ms; respuesta de scraping en < 5 s bajo condiciones nominales; cumplimiento estricto de <= 10 req/min hacia Football-Data.org con bypass (`minTime: 0`) en tests  
**Constraints**: Alcance estricto sin modificar `/back` ni `/front`; extensión limpia de `docker-compose.yml` para el servicio `scraper` con healthcheck; taxonomía estricta de errores (prohibido HTTP 500 genérico); ejecución 100% hermética en CI sin dependencias de red  
**Scale/Scope**: Cobertura de las 5 ligas europeas (PL, BL1, PD, SA, FL1), perfiles de jugadores y alineaciones tácticas  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Arquitectura en capas y separación de responsabilidades**: **PASS**. El microservicio se aloja en `/scraping` de forma autónoma. No contiene lógica de negocio de scoring ni valuación de cotizaciones (responsabilidad exclusiva del backend).
- **Seguridad e independencia de autenticación**: **PASS**. La comunicación backend-microservicio usa una API Key fija de servicio en la cabecera `X-API-Key` activa desde el primer momento en todas las rutas de negocio. `GET /health` queda excluido para el healthcheck de Docker. La autenticación externa contra Football-Data.org utiliza `FOOTBALL_DATA_API_KEY` vía `X-Auth-Token`.
- **Datos sensibles fuera del repositorio**: **PASS**. Las claves (`API_KEY`, `FOOTBALL_DATA_API_KEY`) se cargan únicamente vía variables de entorno y archivo `.env` excluido en `.gitignore`.
- **Manejo de errores estructurado**: **PASS**. Se prohíbe emitir HTTP 500 genéricos ante fallas de proveedores. Se establecen códigos tipados (`PLAYER_NOT_FOUND`, `MATCH_NOT_FOUND`, `SCRAPE_TIMEOUT`, `SCRAPE_BLOCKED`, `RATE_LIMIT_EXCEEDED`, `EXTERNAL_API_AUTH_ERROR`, `INVALID_REQUEST_PARAMS`, `UNAUTHORIZED`) con mensajes en español y timestamp.
- **Tolerancia a fallas de proveedores externos**: **PASS**. Incorpora reintentos con backoff exponencial para WhoScored, throttling preventivo para Football-Data.org y fallback hacia WhoScored para alineaciones ausentes en el tier gratuito de Football-Data.org.
- **Estrategia de testing hermético**: **PASS**. Ninguna prueba en CI realizará llamadas salientes a WhoScored ni Football-Data.org; se utilizan fixtures HTML y JSON versionados en el repositorio junto con `nock` para mockear peticiones HTTP.
- **Despliegue local con Docker Compose**: **PASS**. Se añade el servicio `scraper` con healthcheck al `docker-compose.yml` preexistente en la red `cbo_network`, sin modificar los servicios existentes `db` y `backend`.

**Resultado global del Gate**: **PASS (0 violaciones)**.

## Project Structure

### Documentation (this feature)

```text
specs/002-scrapper/
├── spec.md              # Especificación funcional y de requerimientos
├── plan.md              # Este plan de implementación
├── research.md          # Investigación técnica y decisiones de arquitectura (Fase 0)
├── data-model.md        # Definición de entidades, DTOs y validaciones (Fase 1)
├── quickstart.md        # Guía de ejecución, testing offline y verificación (Fase 1)
├── checklists/
│   └── requirements.md  # Checklist de calidad de la especificación
└── contracts/
    ├── openapi.yaml     # Especificación OpenAPI 3.0.3 completa
    └── README.md        # Resumen y matriz de endpoints
```

### Source Code (repository root)

```text
scraping/
├── Dockerfile                   # Basado en node:20-slim con paquetes Chromium y npm ci
├── package.json                 # Dependencias fijadas a versión de patch exacta (sin ^ ni ~)
├── package-lock.json            # Lockfile commiteado para builds reproducibles
├── .env.example                 # Plantilla documentada de variables de entorno
├── .gitignore                   # Exclusión de node_modules, .env y temporales
├── src/
│   ├── index.js                 # Servidor Express, montaje de rutas y GET /health
│   ├── config/
│   │   └── env.js               # Validación y lectura de variables de entorno
│   ├── middleware/
│   │   ├── auth.js              # Validación de cabecera X-API-Key (excluye /health)
│   │   ├── validator.js         # Validación de query/params (enum liga, fechas YYYY-MM-DD)
│   │   ├── logger.js            # Registro estructurado de peticiones
│   │   └── errorHandler.js      # Formateador centralizado de errores estructurados
│   ├── controllers/
│   │   ├── playerController.js  # GET /players/:whoscoredId/stats (req.on close)
│   │   ├── matchController.js   # GET /competitions/:code/matches y GET /matches/:id
│   │   └── lineupController.js  # GET /lineups (req.on close)
│   ├── services/
│   │   ├── whoscored/
│   │   │   ├── browserPool.js   # Gestión de Chromium headless y Bottleneck concurrency
│   │   │   ├── playerScraper.js # Navegación y extracción de stats de jugador
│   │   │   ├── lineupScraper.js # Navegación y extracción de alineaciones
│   │   │   └── teamMatcher.js   # Algoritmo de normalización y matching por fecha
│   │   └── footballData/
│   │       ├── apiClient.js     # Cliente HTTP hacia API v4 de Football-Data.org
│   │       └── rateLimiter.js   # Throttling preventivo con Bottleneck (10 req/min, minTime: 0 en test)
│   └── utils/
│       ├── retry.js             # Reintentos con backoff exponencial y jitter
│       ├── normalizer.js        # Normalizador numérico y de strings
│       └── errors.js            # Clases de error estructuradas
└── tests/
    ├── helpers/
    │   └── httpMock.js          # Mocking HTTP compartido con nock para Football-Data.org
    ├── unit/
    │   ├── authMiddleware.test.js
    │   ├── validator.test.js
    │   ├── teamMatcher.test.js
    │   └── rateLimiter.test.js
    ├── integration/
    │   ├── healthAndAuth.test.js
    │   ├── playerStats.test.js
    │   ├── matches.test.js
    │   ├── lineups.test.js
    │   ├── whoscoredErrors.test.js
    │   └── concurrency.test.js
    └── fixtures/
        ├── whoscored/
        │   ├── player-stats.html         # Perfil completo con estadísticas
        │   ├── player-empty.html         # Perfil con métricas vacías/guiones
        │   ├── not-found.html            # HTML simulado de jugador inexistente (404)
        │   ├── match-lineup.html         # Detalle con alineación completa
        │   └── match-fixtures-date.html  # Cartelera de partidos para matching de equipos
        └── football-data/
            ├── matches-pl.json           # Partidos de Premier League
            ├── match-detail.json         # Detalle de partido individual
            ├── rate-limit-429.json       # Simulación de respuesta 429
            └── auth-error-401.json       # Simulación de respuesta 401 por token inválido
```

## Complexity Tracking

> Ninguna violación a la constitución que justificar. Todas las decisiones técnicas se apegan a los principios de diseño y requisitos del proyecto.
