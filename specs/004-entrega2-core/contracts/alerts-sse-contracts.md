# API Contract: Real-Time Price Alerts (SSE Stream)

**Base Path**: `/api/alerts`

---

## 1. Create Price Alert

Registers a price target threshold on a player for the authenticated user.

- **Method**: `POST`
- **Path**: `/api/alerts`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Request Body
```json
{
  "playerId": 45,
  "condition": "ABOVE",
  "targetPrice": 25.0000
}
```

### Success Response (`201 Created`)
```json
{
  "id": 14,
  "userId": 12,
  "playerId": 45,
  "playerName": "Erling Haaland",
  "condition": "ABOVE",
  "targetPrice": 25.0000,
  "status": "ACTIVA",
  "createdAt": "2026-10-24T21:20:00Z",
  "triggeredAt": null
}
```

---

## 2. List User Price Alerts

Returns all alerts created by the authenticated user.

- **Method**: `GET`
- **Path**: `/api/alerts`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Success Response (`200 OK`)
```json
[
  {
    "id": 14,
    "userId": 12,
    "playerId": 45,
    "playerName": "Erling Haaland",
    "condition": "ABOVE",
    "targetPrice": 25.0000,
    "status": "ACTIVA",
    "createdAt": "2026-10-24T21:20:00Z",
    "triggeredAt": null
  },
  {
    "id": 11,
    "userId": 12,
    "playerId": 10,
    "playerName": "Kylian Mbappé",
    "condition": "BELOW",
    "targetPrice": 24.0000,
    "status": "DISPARADA",
    "createdAt": "2026-10-20T10:00:00Z",
    "triggeredAt": "2026-10-22T15:02:11Z"
  }
]
```

---

## 3. Delete Price Alert

Cancels an active alert.

- **Method**: `DELETE`
- **Path**: `/api/alerts/{id}`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Success Response (`204 No Content`)
No content.

---

## 4. Subscribe to Real-Time Alerts Stream (SSE)

Establishes a persistent Server-Sent Events stream for instant push notifications when price alerts trigger.

- **Method**: `GET`
- **Path**: `/api/alerts/stream`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`
  - `Accept`: `text/event-stream`

### Stream Response (`200 OK`)
**Content-Type**: `text/event-stream;charset=UTF-8`  
**Cache-Control**: `no-cache`  
**Connection**: `keep-alive`

#### Heartbeat Event (Every 30 seconds)
```text
event: ping
data: {"timestamp":"2026-10-24T21:20:30Z"}

```

#### Triggered Price Alert Event
```text
event: price-alert
data: {"alertId":14,"playerId":45,"playerName":"Erling Haaland","condition":"ABOVE","targetPrice":25.0000,"currentPrice":27.4000,"triggeredAt":"2026-10-24T21:21:05Z"}

```
