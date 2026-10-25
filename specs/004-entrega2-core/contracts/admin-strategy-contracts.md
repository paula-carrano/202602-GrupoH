# API Contract: Administrative Valuation Strategy Management

**Base Path**: `/api/admin/strategies`  
**Security**: Bearer JWT with `ROLE_SUPERUSER`

---

## 1. Activate Valuation Strategy

Activates a registered valuation strategy globally. All subsequent manual or scheduled quote recalculations will apply this strategy to every player in the catalog.

- **Method**: `PATCH`
- **Path**: `/api/admin/strategies/{id}/activate`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>` (Superuser role required)

### Path Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | `Long` | Yes | Identifier of the registered valuation strategy to activate |

### Request Body
None.

### Success Response (`200 OK`)
```json
{
  "id": 2,
  "code": "BALANCED",
  "name": "Rendimiento Global Equilibrado",
  "description": "Ponderación integral de estadísticas ofensivas, minutos y disciplina",
  "version": 1,
  "active": true,
  "minQuote": 1.0000,
  "weights": {
    "goals": 1.0,
    "assists": 0.8,
    "minutes": 0.01,
    "shots": 0.1,
    "keyPasses": 0.2,
    "rating": 1.5,
    "cards": -0.5
  },
  "updatedAt": "2026-10-24T21:00:00Z"
}
```

### Error Responses

#### `403 Forbidden` (User is not a Superuser)
```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Acceso denegado: se requieren permisos exclusivos de superusuario para activar estrategias de mercado.",
  "instance": "/api/admin/strategies/2/activate",
  "code": "ACCESS_DENIED"
}
```

#### `404 Not Found` (Strategy does not exist)
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No se encontró la estrategia de valoración con ID 99.",
  "instance": "/api/admin/strategies/99/activate",
  "code": "STRATEGY_NOT_FOUND"
}
```
