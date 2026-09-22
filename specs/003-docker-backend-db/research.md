# Research & Technical Decisions: Dockerización de Backend y Base de Datos

**Feature**: `003-docker-backend-db`  
**Date**: 2026-09-22  
**Status**: Completed  

---

## 1. Multi-Stage Dockerfile Strategy for Backend

### Decision
Implement a 2-stage build inside `/back/Dockerfile` using exact patch-pinned base images: `eclipse-temurin:17.0.20_8-jdk-alpine` as builder and `eclipse-temurin:17.0.20_8-jre-alpine` as runtime.

- **Stage 1 (Builder)**:
  1. Base image: `eclipse-temurin:17.0.20_8-jdk-alpine`.
  2. Set working directory to `/build`.
  3. Copy Maven configuration and wrapper files (`.mvn/`, `mvnw`, `pom.xml`).
  4. Run `./mvnw dependency:go-offline -B` to leverage Docker layer caching so dependencies are re-downloaded only if `pom.xml` changes.
  5. Copy application source code (`src/`).
  6. Execute `./mvnw clean package -DskipTests -B` to produce the final executable fat JAR.
- **Stage 2 (Runtime)**:
  1. Base image: `eclipse-temurin:17.0.20_8-jre-alpine` (includes minimal JVM runtime, wget for healthchecks).
  2. Create a dedicated non-privileged user and group (`spring:spring`) to avoid running the application container as root.
  3. Copy only the compiled JAR from `builder:/build/target/*.jar` into `/app/app.jar`.
  4. Expose port `8080`.
  5. Set `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.

### Rationale
- **Exact Version Pinning**: Pinning to `17.0.20_8` prevents floating tags (`17-jdk-alpine` / `17-jre-alpine`) from silently introducing breaking JVM or Alpine patch changes during subsequent builds.
- **Image Size**: The full JDK image is > 400 MB, whereas the Alpine JRE image is ~140 MB, reducing network transfer and attack surface.
- **Cache Optimization**: Splitting dependency resolution from code compilation ensures fast iterative builds during local development when only Java source code changes.
- **Security**: Running as non-root aligns with container security best practices and compliance requirements.

### Alternatives Considered
- Floating tags (`eclipse-temurin:17-jdk-alpine` / `17-jre-alpine`): Rejected because they do not guarantee reproducible builds over time.
- `maven:3.9-eclipse-temurin-17`: Evaluated, but using the project's existing `./mvnw` ensures consistent Maven versions across local machines, CI, and Docker builds without divergence.
- Single-stage build: Rejected because it leaves Maven build tools, source files, and intermediate artifacts inside the final runtime image.

---

## 2. PostgreSQL Service & Storage Configuration

### Decision
Use official `postgres:16.15-alpine` in `docker-compose.yml` with a named Docker volume (`postgres_data`) mounted at `/var/lib/postgresql/data`.

- Image: `postgres:16.15-alpine` (pinned to exact patch version, strictly avoiding `:latest` and floating `:16-alpine`).
- Named volume: `postgres_data` declared at top-level `volumes:`.
- Database initialization: Configured via environment variables passed from `.env` (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).

### Rationale
- Pinned patch tag (`16.15-alpine`) guarantees 100% reproducibility across environments, preventing unexpected minor/patch upgrades or behavior changes.
- Alpine variant keeps the DB container lightweight (~80 MB) without compromising PostgreSQL functionality.
- Named volume ensures persistent storage survives container restarts, recreations, and `docker compose down` operations (unless explicit `-v` flag is supplied).

### Alternatives Considered
- Floating major tag `postgres:16-alpine`: Rejected because it resolves to different patch versions over time.
- `postgres:latest`: Rejected per specification rule ("fijar versión, no latest").
- Host-bind mount (`./data:/var/lib/postgresql/data`): Rejected because host-bind mounts cause file permission conflicts between host OS (especially Windows vs Linux Docker daemon) and the container's internal postgres user.

---

## 3. Healthcheck Architecture & Dependencies

### Decision
1. **PostgreSQL Healthcheck**:
   - Native `pg_isready` command:
     ```yaml
     healthcheck:
       test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-cbo_players_db}"]
       interval: 5s
       timeout: 5s
       retries: 5
       start_period: 10s
     ```
2. **Backend Healthcheck**:
   - Add `spring-boot-starter-actuator` to `/back/pom.xml` (version managed by Spring Boot Parent 3.2.3).
   - Configure `/actuator/health` to expose status.
   - Update `/back/src/main/java/com/cbo/players/security/SecurityConfig.java` to permit public access to `/actuator/health` without JWT/API Key requirement.
   - In `docker-compose.yml`, configure backend healthcheck using `wget`:
     ```yaml
     healthcheck:
       test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
       interval: 10s
       timeout: 5s
       retries: 5
       start_period: 30s
     ```
3. **Dependency Orchestration**:
   - Backend service declares:
     ```yaml
     depends_on:
       db:
         condition: service_healthy
     ```

### Rationale
- Prevents the backend from crashing or failing migrations by ensuring PostgreSQL is fully initialized and accepting socket connections before the Spring application context boots.
- Spring Boot Actuator `/actuator/health` provides a real health probe that checks database connectivity, satisfying acceptance criterion 1.

---

## 4. Docker Network Topology & Extensibility

### Decision
Define a custom bridge network `cbo_network` at root level in `docker-compose.yml`. Both `db` and `backend` attach to `cbo_network`.

- Service naming conventions:
  - Database service name: `db` (accessible within network as `db:5432`).
  - Backend service name: `backend` (accessible within network as `backend:8080`).
- Extensibility:
  - When `/scraping` (or future `/front`) is introduced, it will simply attach to `cbo_network` without requiring changes to existing network declarations or service hosts.

---

## 5. Environment Variables & Secret Handling

### Decision
1. Strict required variables in `docker-compose.yml`:
   - `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}` (strictly no fallback)
   - `JWT_SECRET: ${JWT_SECRET}` (strictly no fallback)
   - If either variable is missing from `.env`, `docker compose up` warns/errors immediately rather than starting with insecure, well-known defaults or leaking secrets into the codebase.
2. Optional variables maintain developer-friendly local defaults:
   - `POSTGRES_DB: ${POSTGRES_DB:-cbo_players_db}`
   - `POSTGRES_USER: ${POSTGRES_USER:-postgres}`
   - `POSTGRES_PORT: ${POSTGRES_PORT:-5432}`
   - `BACKEND_PORT: ${BACKEND_PORT:-8080}`
   - `JWT_EXPIRATION_MS: ${JWT_EXPIRATION_MS:-86400000}`
3. Template `.env.example` committed in repository root documenting necessary variables with non-production placeholder values.
4. `.gitignore` explicitly ignores `.env`, `*.env`, and `*.env.*` at root and `/back`.

### Rationale
- Strictly aligns with Constitution Section 4.3 (sensitive data must not exist in repository files).
- Eliminates silent startup failures where an unconfigured system would use hardcoded default credentials.

---

## 6. Scope Isolation (/front & /scraping)

### Decision
- Zero files in `/front` or `/scraping` will be added, modified, or removed.
- `docker-compose.yml` is located solely in the project root and references only `./back` and official PostgreSQL images.
