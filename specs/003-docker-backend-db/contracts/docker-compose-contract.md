# Contract: Docker Compose Architecture & Service Specification

**Feature**: `003-docker-backend-db`  
**Artifact**: `contracts/docker-compose-contract.md`  

---

## 1. Services Contract

```yaml
version: "3.8"

networks:
  cbo_network:
    driver: bridge

volumes:
  postgres_data:
    driver: local

services:
  db:
    image: postgres:16.15-alpine
    container_name: cbo-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-cbo_players_db}
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - cbo_network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-cbo_players_db}"]
      interval: 5s
      timeout: 5s
      retries: 5
      start_period: 10s

  backend:
    build:
      context: ./back
      dockerfile: Dockerfile
    container_name: cbo-backend
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-cbo_players_db}
      DB_USERNAME: ${POSTGRES_USER:-postgres}
      DB_PASSWORD: ${POSTGRES_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION_MS: ${JWT_EXPIRATION_MS:-86400000}
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    networks:
      - cbo_network
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
```

---

## 2. Ports & Network Interfaces

| Service | Internal Port | Default Host Port | External Access | Inter-Container Access |
|---------|---------------|-------------------|-----------------|------------------------|
| `db` | `5432` | `5432` | `localhost:5432` (optional local DBA tools) | `db:5432` via `cbo_network` |
| `backend` | `8080` | `8080` | `http://localhost:8080` (API & Swagger) | `backend:8080` via `cbo_network` |

---

## 3. Extensibility Guarantees
- Any future service (e.g., `scraping` or `frontend`) joining `cbo_network` can directly reach the backend at `http://backend:8080` and the database at `db:5432` without modifying existing port configurations.
