# Quickstart & Validation Guide: Docker Infrastructure

**Feature**: `003-docker-backend-db`  
**Artifact**: `quickstart.md`  

This guide provides end-to-end instructions to build, execute, verify, and clean up the Dockerized backend and PostgreSQL database environment.

---

## 1. Prerequisites

- [Docker Engine](https://docs.docker.com/engine/) (version 20.10+ recommended)
- [Docker Compose](https://docs.docker.com/compose/) (v2 syntax: `docker compose`)
- `curl` or a modern web browser for HTTP verification

No local installation of Java, Maven, or PostgreSQL is required on the host system.

---

## 2. Setup & Execution

### Step 1: Prepare Environment Configuration
Create your local environment file from the provided template:

```bash
# In repository root
cp .env.example .env
```

*(Optional)* Edit `.env` to change `POSTGRES_PASSWORD` or other parameters if desired.

### Step 2: Build and Start Services
Build the multi-stage Docker image and start the containers in detached mode:

```bash
docker compose up --build -d
```

Expected behavior:
1. Docker builds the Maven backend image using multi-stage caching.
2. The `cbo-db` container starts first.
3. Once `cbo-db` passes its healthcheck (`pg_isready`), Docker Compose launches `cbo-backend`.

---

## 3. Verification Scenarios

### Scenario 1: Verify Container Status & Health
Check the operational state of both containers:

```bash
docker compose ps
```

**Expected Outcome**:
Both containers are in `Up (healthy)` state:
```text
NAME          IMAGE                      COMMAND                  SERVICE   CREATED         STATUS                   PORTS
cbo-backend   202602-grupoh-backend      "java -jar /app/app.…"   backend   1 minute ago    Up 40 seconds (healthy)  0.0.0.0:8080->8080/tcp
cbo-db        postgres:16.15-alpine      "docker-entrypoint.s…"   db        1 minute ago    Up 1 minute (healthy)    0.0.0.0:5432->5432/tcp
```

### Scenario 2: Test Actuator Health Endpoint
Verify that the Spring Boot backend can access the database:

```bash
curl -i http://localhost:8080/actuator/health
```

**Expected Outcome**:
HTTP Status `200 OK` with JSON response:
```json
{"status":"UP"}
```

### Scenario 3: Verify OpenAPI / Swagger Documentation
Open in browser or curl:
```bash
curl -i http://localhost:8080/swagger-ui.html
```

**Expected Outcome**:
HTTP Status `302 Found` (redirecting to `/swagger-ui/index.html`) or `200 OK`.

### Scenario 4: Test Database Volume Persistence
Verify that database tables and data persist across container restarts:

1. Create or register a test user or verify created schema:
   ```bash
   docker compose exec db psql -U postgres -d cbo_players_db -c "\dt"
   ```
2. Stop and remove containers (without volume destruction):
   ```bash
   docker compose down
   ```
3. Restart containers:
   ```bash
   docker compose up -d
   ```
4. Query the database again:
   ```bash
   docker compose exec db psql -U postgres -d cbo_players_db -c "\dt"
   ```

**Expected Outcome**:
Tables (`users`, `players`, `api_keys`, etc.) remain intact and queries execute without data loss.

---

## 4. Teardown & Reset

- **Stop containers and retain volume**:
  ```bash
  docker compose down
  ```
- **Stop containers and completely wipe data volume**:
  ```bash
  docker compose down -v
  ```
