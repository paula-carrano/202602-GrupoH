# API Contract: Orders, Portfolio & Transactions

**Base Paths**:
- `/api/orders`
- `/api/users`

---

## 1. Execute Buy Order

Executes an atomic token purchase using current player quote and available tokens from the superuser pool.

- **Method**: `POST`
- **Path**: `/api/orders/buy`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Request Body
```json
{
  "playerId": 45,
  "quantity": 4
}
```

### Success Response (`200 OK`)
```json
{
  "transactionId": 501,
  "orderType": "COMPRA",
  "playerId": 45,
  "playerName": "Erling Haaland",
  "quantity": 4,
  "unitPrice": 27.4000,
  "totalAmount": 109.6000,
  "balanceBefore": 200.0000,
  "balanceAfter": 90.4000,
  "tokensBefore": 0,
  "tokensAfter": 4,
  "averageBuyPrice": 27.4000,
  "timestamp": "2026-10-24T21:10:00Z"
}
```

### Error Responses
#### `400 Bad Request` (Insufficient Funds)
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Saldo insuficiente para realizar la compra. Saldo disponible: 10.0000 CRD, costo total: 109.6000 CRD.",
  "instance": "/api/orders/buy",
  "code": "INSUFFICIENT_FUNDS"
}
```

#### `400 Bad Request` (Insufficient Tokens in Pool)
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Disponibilidad insuficiente de tokens. Solo quedan 2 tokens disponibles en el mercado para este jugador.",
  "instance": "/api/orders/buy",
  "code": "INSUFFICIENT_TOKENS_AVAILABLE"
}
```

---

## 2. Execute Sell Order

Executes an atomic token sale, returning tokens to the superuser pool and crediting proceeds to the seller.

- **Method**: `POST`
- **Path**: `/api/orders/sell`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Request Body
```json
{
  "playerId": 45,
  "quantity": 2
}
```

### Success Response (`200 OK`)
```json
{
  "transactionId": 502,
  "orderType": "VENTA",
  "playerId": 45,
  "playerName": "Erling Haaland",
  "quantity": 2,
  "unitPrice": 27.4000,
  "totalAmount": 54.8000,
  "balanceBefore": 90.4000,
  "balanceAfter": 145.2000,
  "tokensBefore": 4,
  "tokensAfter": 2,
  "averageBuyPrice": 27.4000,
  "timestamp": "2026-10-24T21:12:00Z"
}
```

### Error Responses
#### `400 Bad Request` (Insufficient Tokens in User Position)
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Tenencia insuficiente. El usuario posee 1 tokens del jugador e intentó vender 2.",
  "instance": "/api/orders/sell",
  "code": "INSUFFICIENT_TOKEN_BALANCE"
}
```

---

## 3. Simulated Credit Top-up

Simulates topping up credits directly into the user's account without payment processors.

- **Method**: `POST`
- **Path**: `/api/users/{id}/credits/topup`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Request Body
```json
{
  "amount": 100.0
}
```

### Success Response (`200 OK`)
```json
{
  "transactionId": 503,
  "userId": 12,
  "operationType": "RECARGA",
  "amountCredited": 100.0000,
  "balanceBefore": 0.0000,
  "balanceAfter": 100.0000,
  "timestamp": "2026-10-24T21:15:00Z"
}
```

### Error Responses
#### `400 Bad Request` (Invalid Amount)
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "El monto a recargar debe ser un valor positivo válido (mayor a 0 y hasta 10000 CRD).",
  "instance": "/api/users/12/credits/topup",
  "code": "INVALID_AMOUNT"
}
```

---

## 4. Get User Portfolio

Returns the consolidated portfolio of the user with real-time valuation, cost basis, and PnL metrics.

- **Method**: `GET`
- **Path**: `/api/users/{id}/portfolio`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Success Response (`200 OK`)
```json
{
  "userId": 12,
  "creditsBalance": 145.2000,
  "totalInvestedCost": 54.8000,
  "totalCurrentValue": 60.0000,
  "totalPnLNominal": 5.2000,
  "totalPnLPercentage": 9.4891,
  "positions": [
    {
      "playerId": 45,
      "playerName": "Erling Haaland",
      "position": "FWD",
      "team": "Manchester City",
      "tokensQuantity": 2,
      "averageBuyPrice": 27.4000,
      "currentQuote": 30.0000,
      "totalCurrentValue": 60.0000,
      "totalCostBasis": 54.8000,
      "pnlNominal": 5.2000,
      "pnlPercentage": 9.4891
    }
  ]
}
```

---

## 5. Get User Transaction History

Returns the immutable paginated ledger of all user operations (`COMPRA`, `VENTA`, `RECARGA`).

- **Method**: `GET`
- **Path**: `/api/users/{id}/transactions`
- **Headers**:
  - `Authorization`: `Bearer <jwt_token>`

### Query Parameters
| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `page` | `Integer` | No | `0` | Page number |
| `size` | `Integer` | No | `20` | Page size |

### Success Response (`200 OK`)
```json
{
  "content": [
    {
      "id": 503,
      "timestamp": "2026-10-24T21:15:00Z",
      "actorUserId": 12,
      "operationType": "RECARGA",
      "playerId": null,
      "playerName": null,
      "quantity": null,
      "unitPrice": null,
      "totalAmount": 100.0000,
      "balanceBefore": 0.0000,
      "balanceAfter": 100.0000,
      "tokensBefore": null,
      "tokensAfter": null,
      "strategyVersion": 1
    },
    {
      "id": 501,
      "timestamp": "2026-10-24T21:10:00Z",
      "actorUserId": 12,
      "operationType": "COMPRA",
      "playerId": 45,
      "playerName": "Erling Haaland",
      "quantity": 4,
      "unitPrice": 27.4000,
      "totalAmount": 109.6000,
      "balanceBefore": 200.0000,
      "balanceAfter": 90.4000,
      "tokensBefore": 0,
      "tokensAfter": 4,
      "strategyVersion": 1
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 2,
  "totalPages": 1,
  "last": true
}
```
