# Contratos de Interfaz (API Contracts): Entrega 1 — Foundation

**Feature**: `001-entrega1-foundation`  
**Date**: 2026-09-16  

Este directorio contiene las especificaciones formales de los contratos de interfaz REST para los clientes de la plataforma.

## Archivos de Contrato

- [`openapi.yaml`](./openapi.yaml): Especificación formal OpenAPI 3.0.3 completa que define todos los endpoints, schemas de solicitud/respuesta, esquemas de seguridad (`Bearer JWT` y `X-API-Key`) y estructuras de error `ProblemDetail` (RFC 7807).

## Matriz de Endpoints y Seguridad

| Método | Endpoint | Autenticación | Request Body | Response Exitosa | Errores Posibles |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Pública | `RegisterRequestDto` | `201 Created` (`UserResponseDto`) | `400` (Validation), `409` (Duplicate), `500` |
| `POST` | `/api/v1/auth/login` | Pública | `LoginRequestDto` | `200 OK` (`LoginResponseDto`) | `400` (Validation), `401` (Credentials), `500` |
| `POST` | `/api/v1/auth/api-keys` | Bearer JWT | `CreateApiKeyRequestDto` | `201 Created` (`ApiKeyCreatedResponseDto`) | `400` (Validation), `401` (Unauthorized), `500` |
| `GET` | `/api/v1/players` | Bearer JWT o API Key | Ninguno | `200 OK` (`List<PlayerResponseDto>`) | `401` (Unauthorized), `500` |
| `GET` | `/api/v1/players/{id}` | Bearer JWT o API Key | Ninguno | `200 OK` (`PlayerResponseDto`) | `400` (Format), `401` (Unauthorized), `404` (Not Found), `500` |
| `GET` | `/swagger-ui/index.html` | Pública | Ninguno | `200 OK` (HTML) | - |
| `GET` | `/v3/api-docs` | Pública | Ninguno | `200 OK` (JSON) | - |

## Formato Estándar de Error (`ProblemDetail`)

Todas las respuestas de error (`4xx` y `5xx`) retornan el content-type `application/problem+json` con la estructura:

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No se encontró ningún jugador con el identificador especificado.",
  "instance": "/api/v1/players/999",
  "errorCode": "PLAYER_NOT_FOUND",
  "timestamp": "2026-09-16T12:00:00Z",
  "correlationId": "4a72d3f9-71c1-4b72-911e-b8d96b9911e3",
  "violations": null
}
```
