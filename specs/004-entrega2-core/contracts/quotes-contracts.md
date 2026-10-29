# API Contract: Quotes & Ranking

**Base Paths**:
- `/api/quotes`
- `/api/players`

---

## 1. Recalculate Quotes Globally

Recalculates current quotes for all active players applying the active valuation strategy. Persists historical quotes, triggers price alert evaluations, and clears the Redis cache.

- **Method**: `POST`
- **Path**: `/api/quotes/recalculate`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>` (Admin/Superuser role required)

### Success Response (`200 OK`)
```json
{
  "recalculatedPlayersCount": 450,
  "strategyCode": "BALANCED",
  "strategyName": "Rendimiento Global Equilibrado",
  "strategyVersion": 1,
  "executionDurationMs": 320,
  "timestamp": "2026-10-24T21:05:00Z"
}
```

---

## 2. Get Historical Player Quotes (Paginated or by Specific Date)

Returns either the paginated chronological history of quotes for a player, or the single quote effective on a given date.

- **Method**: `GET`
- **Path**: `/api/players/{id}/quotes`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>` (Any authenticated user)

### Query Parameters
| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `date` | `String` (`YYYY-MM-DD`) | No | `null` | If provided, returns single quote effective at this date. Pagination parameters are ignored. |
| `page` | `Integer` | No | `0` | Page number (0-based) for historical series. |
| `size` | `Integer` | No | `20` | Page size for historical series. |

### Success Response A: Paginated Historical Series (without `date` param) (`200 OK`)
```json
{
  "content": [
    {
      "quoteId": 1450,
      "playerId": 45,
      "price": 27.4000,
      "currency": "CRD",
      "strategyName": "Rendimiento Global Equilibrado",
      "strategyVersion": 1,
      "timestamp": "2026-10-24T21:05:00Z"
    },
    {
      "quoteId": 1210,
      "playerId": 45,
      "price": 25.1000,
      "currency": "CRD",
      "strategyName": "Rendimiento Ofensivo",
      "strategyVersion": 1,
      "timestamp": "2026-10-17T15:00:00Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 2,
  "totalPages": 1,
  "last": true
}
```

### Success Response B: Specific Date Quote (`?date=2026-10-20`) (`200 OK`)
```json
{
  "quoteId": 1210,
  "playerId": 45,
  "effectiveDate": "2026-10-20",
  "price": 25.1000,
  "currency": "CRD",
  "strategyName": "Rendimiento Ofensivo",
  "strategyVersion": 1,
  "quoteTimestamp": "2026-10-17T15:00:00Z"
}
```

---

## 3. Get Players Ranking

Returns active players ordered from highest to lowest current quote under the active valuation strategy. Accelerated via Redis cache.

- **Method**: `GET`
- **Path**: `/api/players/ranking`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Success Response (`200 OK`)
```json
{
  "strategyCode": "BALANCED",
  "strategyVersion": 1,
  "cached": true,
  "ranking": [
    {
      "rank": 1,
      "playerId": 45,
      "firstName": "Erling",
      "lastName": "Haaland",
      "position": "FWD",
      "team": "Manchester City",
      "league": "Premier League",
      "currentQuote": 27.4000,
      "currency": "CRD"
    },
    {
      "rank": 2,
      "playerId": 10,
      "firstName": "Kylian",
      "lastName": "Mbappé",
      "position": "FWD",
      "team": "Real Madrid",
      "league": "La Liga",
      "currentQuote": 26.8000,
      "currency": "CRD"
    }
  ]
}
```
