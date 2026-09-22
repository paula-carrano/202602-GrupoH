# Implementation Plan: Dockerización de Backend + Base de Datos

**Branch**: `003-docker-backend-db` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-docker-backend-db/spec.md`

---

## Summary

Implement the containerization infrastructure for the Spring Boot backend (`/back`) and PostgreSQL database. This includes creating a 2-stage cached `Dockerfile` inside `/back` using exact patch-pinned base images (`eclipse-temurin:17.0.20_8-jdk-alpine` and `eclipse-temurin:17.0.20_8-jre-alpine`), defining root-level `docker-compose.yml` with pinned `postgres:16.15-alpine`, healthchecks, and a named persistent volume (`postgres_data`). Sensitive variables (`POSTGRES_PASSWORD`, `JWT_SECRET`) are enforced strictly without fallback values to prevent running with known or trivial secrets, templated in `.env.example`, and connected through an extensible Docker network (`cbo_network`). In accordance with the prompt and specification constraints, directories `/front` and `/scraping` remain completely untouched.

---

## Technical Context

**Language/Version**: Java 17 (Builder: `eclipse-temurin:17.0.20_8-jdk-alpine`, Runtime: `eclipse-temurin:17.0.20_8-jre-alpine`)  
**Primary Dependencies**: Spring Boot 3.2.3, Spring Web, Spring Data JPA, Spring Security, Spring Boot Actuator (`spring-boot-starter-actuator`), PostgreSQL JDBC Driver  
**Storage**: PostgreSQL 16.15 Alpine (`postgres:16.15-alpine`) with Docker named volume `postgres_data`  
**Testing**: Maven Surefire, Spring Boot Test, Container Healthchecks (`pg_isready`, Actuator `/actuator/health`)  
**Target Platform**: Docker Engine / Docker Compose (Linux container runtime) on Windows/Linux/macOS host  
**Project Type**: Containerized REST Web Service + Relational Database  
**Performance Goals**: Fast build times through Docker layer caching of Maven dependencies; minimal runtime image size (~140 MB JRE); database socket readiness in < 15s  
**Constraints**: Zero changes to `/front` and `/scraping`; no credentials hardcoded; strictly no fallbacks for sensitive required variables (`POSTGRES_PASSWORD`, `JWT_SECRET`) in compose; exact patch-level pinned Docker images (`postgres:16.15-alpine`, `eclipse-temurin:17.0.20_8-jdk-alpine`, `eclipse-temurin:17.0.20_8-jre-alpine`); `.env` strictly ignored by git  
**Scale/Scope**: Local development foundation for current backend and future scraping service integration  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution Principle | Requirement | Compliance Status | Notes |
|------------------------|-------------|-------------------|-------|
| **2. Stack Tecnológico** | PostgreSQL como base principal | **PASSED** | Uses exact patch `postgres:16.15-alpine`. |
| **2. Stack Tecnológico** | Spring Boot Actuator | **PASSED** | Added `spring-boot-starter-actuator` to `/back/pom.xml` for health monitoring. |
| **3.2 Independencia de BD** | No acoplar lógica a motor específico | **PASSED** | Configuration is externalized via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. |
| **4.3 Datos Sensibles** | No almacenar credenciales o secretos en repo | **PASSED** | `POSTGRES_PASSWORD` and `JWT_SECRET` have **no fallback** in compose; require local `.env`. `.gitignore` verifies `.env` is ignored. |
| **Non-Latest Pinned Tags** | Imágenes base fijadas con tag específico | **PASSED** | `postgres:16.15-alpine`, `eclipse-temurin:17.0.20_8-jdk-alpine`, `eclipse-temurin:17.0.20_8-jre-alpine` are exact patch tags. |
| **5. Scope Isolation** | Sin tocar `/front` ni `/scraping` | **PASSED** | Changes restricted exclusively to `/back` (Dockerfile, pom, SecurityConfig) and root (`docker-compose.yml`, `.env.example`, `README.md`). |

---

## Project Structure

### Documentation (this feature)

```text
specs/003-docker-backend-db/
├── spec.md              # Feature specification
├── plan.md              # Implementation plan (this document)
├── research.md          # Technical research and decisions
├── data-model.md        # Environment, container, and storage schemas
├── quickstart.md        # End-to-end verification and run guide
├── contracts/           # Service and interface contracts
│   ├── docker-compose-contract.md
│   ├── environment-contract.md
│   └── health-contract.md
└── tasks.md             # Tasks document (Phase 2 output via /speckit-tasks)
```

### Source Code Impacted

```text
├── .env.example                                  # [NEW] Template environment variables
├── docker-compose.yml                            # [NEW] Compose definition (db + backend)
├── README.md                                     # [MODIFY] Instructions for Docker Compose
└── back/
    ├── Dockerfile                                # [NEW] Multi-stage build (17.0.20_8-jdk-alpine -> 17.0.20_8-jre-alpine)
    ├── pom.xml                                   # [MODIFY] Add spring-boot-starter-actuator
    └── src/main/java/com/cbo/players/
        └── security/SecurityConfig.java          # [MODIFY] Permit /actuator/health public access
```

---

## Phases & Deliverables

### Phase 0: Outline & Research
- [x] Analyze multi-stage Docker build options with exact patch tags (`eclipse-temurin:17.0.20_8-jdk-alpine` -> `17.0.20_8-jre-alpine`).
- [x] Determine PostgreSQL 16.15 Alpine configuration, healthcheck probe, and volume mapping.
- [x] Plan Spring Boot Actuator addition and SecurityConfig permit for `/actuator/health`.
- [x] Enforce strictly required variables without default fallback in compose (`POSTGRES_PASSWORD`, `JWT_SECRET`).
- [x] Define extensible network topology (`cbo_network`).
- [x] Document decisions in `research.md`.

### Phase 1: Design & Contracts
- [x] Define environment schema, service definitions, and volume specifications in `data-model.md`.
- [x] Create compose service contract in `contracts/docker-compose-contract.md`.
- [x] Create healthcheck specification in `contracts/health-contract.md`.
- [x] Create environment variables matrix with required status in `contracts/environment-contract.md`.
- [x] Create run and verification scenarios in `quickstart.md`.

---

## Complexity Tracking

*No violations of project constitution or design standards.*
