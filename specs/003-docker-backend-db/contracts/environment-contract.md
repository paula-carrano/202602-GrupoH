# Contract: Environment Variables Specification

**Feature**: `003-docker-backend-db`  
**Artifact**: `contracts/environment-contract.md`  

---

## 1. Overview
This contract defines all configuration keys exposed to Docker Compose via `.env` and propagated to the containers.

Variables marked as **Required: Yes** (`POSTGRES_PASSWORD` and `JWT_SECRET`) do **not** define fallback values in `docker-compose.yml`. If they are not defined in the local `.env` file, Docker Compose will fail explicitly or reject startup rather than proceeding silently with an insecure default or committed secret.

Variables marked as **Required: No** provide safe local fallbacks in `docker-compose.yml`.

---

## 2. Variables Matrix

| Variable Name | Required | Default in Compose | Injected Service | Purpose & Validation |
|---------------|----------|-------------------|------------------|----------------------|
| `POSTGRES_DB` | No | `cbo_players_db` | `db`, `backend` | Name of the PostgreSQL database created at first boot and targeted by JDBC URL. |
| `POSTGRES_USER` | No | `postgres` | `db`, `backend` | Database user for ownership and Spring Boot connection. |
| `POSTGRES_PASSWORD` | **Yes** | **None** (strict, no fallback) | `db`, `backend` | Password for `POSTGRES_USER`. Must be defined in `.env`. No fallback is provided in compose to prevent starting with empty or default credentials. |
| `POSTGRES_PORT` | No | `5432` | Host port binding | Port exposed on host for external database clients. |
| `BACKEND_PORT` | No | `8080` | Host port binding | Port exposed on host for API and documentation. |
| `JWT_SECRET` | **Yes** | **None** (strict, no fallback) | `backend` | HMAC SHA-256 signing secret key (minimum 32 characters/256 bits). Must be defined in `.env`. No fallback is provided in compose to eliminate hardcoded secrets in the repository. |
| `JWT_EXPIRATION_MS` | No | `86400000` | `backend` | Expiration time for authentication tokens (86400000 ms = 24h). |

---

## 3. Template File (`.env.example`) Contract

The `.env.example` in repo root is a template documentation file (not used by Docker Compose unless copied to `.env`). It provides guidance and dummy placeholder values:

```env
# ==============================================================================
# Environment Configuration - Docker Compose
# ==============================================================================

# Database Configuration
POSTGRES_DB=cbo_players_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change_this_secure_password
POSTGRES_PORT=5432

# Backend Application Configuration
BACKEND_PORT=8080
JWT_SECRET=dGhpc19pc19hX3Zlcnlfc2VjdXJlX2tleV9mb3Jfand0X3NpZ25pbmdfMjU2X2JpdHNfc2VjcmV0
JWT_EXPIRATION_MS=86400000
```
