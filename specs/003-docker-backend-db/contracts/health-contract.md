# Contract: Service Healthcheck Specification

**Feature**: `003-docker-backend-db`  
**Artifact**: `contracts/health-contract.md`  

---

## 1. Database Healthcheck Contract

- **Target Service**: `db` (PostgreSQL 16)
- **Protocol**: Local socket / IPC command execution inside container
- **Command**:
  ```sh
  pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}
  ```
- **Exit Codes**:
  - `0`: Success (server is accepting connections). Container status transition: `starting` -> `healthy`.
  - `1`: Server is rejecting connections (e.g. still initializing). Container remains `starting`.
  - `2`: Server did not respond (connection attempt failed).
  - `3`: No attempt made (invalid parameters or environment).
- **Execution Parameters**:
  - `interval`: 5 seconds
  - `timeout`: 5 seconds
  - `retries`: 5
  - `start_period`: 10 seconds

---

## 2. Backend Healthcheck Contract

- **Target Service**: `backend` (Spring Boot 3.2.3)
- **Endpoint**: `GET http://localhost:8080/actuator/health`
- **Security Rule**: Public access (`permitAll()`) in Spring Security configuration.
- **Probe Mechanism**: HTTP GET using `wget -qO-` inside the Alpine container.

### Response Payload (HTTP 200 OK)

```json
{
  "status": "UP"
}
```

### Detailed Component Status (when Actuator details are authorized)

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### Unhealthy Response Conditions (HTTP 503 Service Unavailable)
Triggered if PostgreSQL connection is down or initialization fails:
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN"
    }
  }
}
```

- **Execution Parameters**:
  - `interval`: 10 seconds
  - `timeout`: 5 seconds
  - `retries`: 5
  - `start_period`: 30 seconds
