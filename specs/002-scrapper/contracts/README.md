# Contratos de Interfaz: Microservicio de Datos Externos (WhoScored & Football-Data.org)

**Feature**: `002-player-scraper`  
**Date**: 2026-09-22  

Este directorio documenta formalmente los contratos REST expuestos por el microservicio `/scraping` hacia el backend `/back`.

## Archivos de Contrato

- [`openapi.yaml`](./openapi.yaml): Especificación OpenAPI 3.0.3 completa que define los endpoints, tipos de datos, seguridad por `X-API-Key` y estructura de respuestas de error.

## Matriz de Endpoints y Seguridad

| Método | Endpoint | Fuente Externa | Parámetros Principales | Códigos HTTP | Códigos de Error Específicos |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/players/{whoscoredId}/stats` | WhoScored (Scraping) | `whoscoredId` (path) | `200`, `401`, `404`, `502`, `504` | `UNAUTHORIZED`, `PLAYER_NOT_FOUND`, `SCRAPE_BLOCKED`, `SCRAPE_TIMEOUT` |
| `GET` | `/competitions/{code}/matches` | Football-Data.org (API) | `code` (path), `dateFrom`, `dateTo` (query) | `200`, `400`, `401`, `429`, `502` | `UNAUTHORIZED`, `INVALID_REQUEST_PARAMS`, `RATE_LIMIT_EXCEEDED`, `EXTERNAL_API_AUTH_ERROR` |
| `GET` | `/matches/{matchId}` | Football-Data.org (API) | `matchId` (path) | `200`, `401`, `404`, `429`, `502` | `UNAUTHORIZED`, `RATE_LIMIT_EXCEEDED`, `EXTERNAL_API_AUTH_ERROR` |
| `GET` | `/lineups` | WhoScored (Fallback) | `homeTeam`, `awayTeam`, `date` (query) | `200`, `400`, `401`, `404`, `502`, `504` | `UNAUTHORIZED`, `INVALID_REQUEST_PARAMS`, `MATCH_NOT_FOUND`, `SCRAPE_BLOCKED`, `SCRAPE_TIMEOUT` |

## Reglas de Comunicación y Autenticación

1. **Autenticación Interna**: Toda petición hacia este microservicio exige la cabecera `X-API-Key`.
2. **Autenticación Externa**: El microservicio inyecta internamente `X-Auth-Token` contra Football-Data.org usando `FOOTBALL_DATA_API_KEY`.
3. **Resiliencia**: Nunca se retorna un error `500` genérico ante caídas, bloqueos o timeouts de los proveedores externos.
