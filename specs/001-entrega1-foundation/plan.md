# Implementation Plan: Plataforma de Mercado y Valoración de Jugadores — Entrega 1: Foundation

**Branch**: `001-entrega1-foundation` | **Date**: 2026-09-16 | **Spec**: [`specs/001-entrega1-foundation/spec.md`](./spec.md)

**Input**: Feature specification from `/specs/001-entrega1-foundation/spec.md`

---

## Summary

Establecer la base arquitectural, técnica y de seguridad de la plataforma mediante un servicio backend REST con Spring Boot 3.x y Java 17+, persistencia en PostgreSQL con Spring Data JPA/Hibernate, autenticación híbrida (JWT para usuarios y API Keys criptográficas para clientes M2M), catálogo inicial de jugadores desacoplado mediante DTOs, gestión global de errores en español bajo el estándar RFC 7807 (`ProblemDetail`), documentación interactiva Swagger/OpenAPI v3 y pipeline de integración continua en GitHub Actions con control de calidad en SonarCloud (<10 issues).

---

## Technical Context

**Language/Version**: Java 17 LTS (compatible con Java 21)  
**Primary Dependencies**:
- Spring Boot 3.2+ (`spring-boot-starter-web`)
- Spring Security (`spring-boot-starter-security`)
- Spring Data JPA (`spring-boot-starter-data-jpa`)
- PostgreSQL JDBC Driver (`org.postgresql:postgresql`)
- Bean Validation (`spring-boot-starter-validation`)
- JWT Library (`io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` versión 0.12.x)
- Swagger / OpenAPI (`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0`)
- Testing (`spring-boot-starter-test`, JUnit 5, Mockito, `spring-security-test`)

**Storage**: PostgreSQL 14+  
**Testing**: JUnit 5, Mockito, Spring MockMvc  
**Target Platform**: JVM (Linux/Windows/macOS), ejecutable en contenedor Docker o servidor estándar  
**Project Type**: REST Web Service (Spring Boot)  
**Performance Goals**: Tiempo de respuesta sub-100ms para endpoints locales de autenticación y consulta de catálogo  
**Constraints**:
- Respuestas de error estandarizadas en español utilizando `ProblemDetail` (RFC 7807)
- Prohibición estricta de credenciales/secretos en el repositorio (inyección por variables de entorno)
- Entidades JPA nunca expuestas en la capa de controladores (uso exclusivo de DTOs)
- API Keys hasheadas con SHA-256 en base de datos
- SonarCloud con estricto Quality Gate < 10 issues totales (máx 9 issues entre bugs, vulnerabilidades y code smells)

**Scale/Scope**: Delimitado exclusivamente a la Entrega 1 — Foundation (5 endpoints funcionales + 2 de documentación)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio Constitucional | Requisito de la Constitución | Estado del Diseño | Veredicto |
| :--- | :--- | :--- | :--- |
| **3.1 Arquitectura en capas** | Controllers → Services → Repositories → Adapters | Paquetes y dependencias siguen el flujo unidireccional estricto. Controllers solo consumen Services; Services orquestan Repositories. | **PASS** |
| **3.2 Independencia de BD** | PostgreSQL como base principal; lógica independiente | Se utiliza Spring Data JPA con dialecto PostgreSQL sin sentencias SQL propietarias acopladas. | **PASS** |
| **3.3 DTOs y entidades** | Entidades JPA nunca expuestas directamente en Controllers | Todos los endpoints usan DTOs dedicados de request y response (`UserResponseDto`, `PlayerResponseDto`, etc.). | **PASS** |
| **4.1 JWT** | Autenticación con algoritmo seguro (HS256) | Implementado con clave de 256 bits inyectada por variable de entorno para sesiones interactivas. | **PASS** |
| **4.2 API Keys** | Identificación de aplicaciones cliente; claves hasheadas | API Key tiene responsabilidad M2M, clave persistida exclusivamente en hash SHA-256 (`keyHash`). | **PASS** |
| **4.3 Datos sensibles** | Cero contraseñas, secretos o keys en el repositorio | Variables de entorno `DB_*` y `JWT_*` con placeholders para testing. | **PASS** |
| **5. Manejo de errores** | Mensajes en español, `ProblemDetail`, códigos estables, sin stack traces | `@RestControllerAdvice`, ProblemDetail RFC 7807, extensiones `errorCode`, `timestamp`, `correlationId`, `violations`. | **PASS** |
| **6.1 DevOps y Calidad** | GitHub Actions `.github/workflows/ci.yml`, SonarCloud < 10 issues | Workflow automatizado con compilación Maven, ejecución de JUnit 5 y análisis SonarCloud. | **PASS** |
| **6.2 Endpoints y Swagger** | 5 endpoints iniciales + Swagger UI (`/swagger-ui/index.html`) y `/v3/api-docs` | Todos los endpoints formalizados y documentados en OpenAPI 3.0.3 (`contracts/openapi.yaml`). | **PASS** |
| **6.3 Modelo inicial** | User, ApiKey, Player, Quote/PlayerQuote | Entidades modeladas en `data-model.md`. Sin lógica de mercado de entregas posteriores. | **PASS** |
| **Límites Explícitos** | Sin scraping, sin valoración, sin mercado, sin órdenes, sin H2 de Entrega 2 | Todos los componentes de Entrega 2 y 3 quedan explícitamente excluidos. | **PASS** |

**Resultado Global del Constitution Check: APROBADO (PASS)**.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-entrega1-foundation/
├── spec.md              # Especificación de requisitos funcionales y de aceptación
├── plan.md              # Plan de implementación arquitectónica (este archivo)
├── research.md          # Fase 0: Decisiones técnicas y justificaciones
├── data-model.md        # Fase 1: Esquema de datos, entidades, relaciones e índices
├── quickstart.md        # Fase 1: Guía de ejecución, configuración y validación e2e
├── checklists/
│   └── requirements.md  # Checklist de calidad y completitud de requisitos
└── contracts/
    ├── openapi.yaml     # Contrato formal OpenAPI 3.0.3 completo
    └── README.md        # Resumen y matriz de contratos REST
```

### Source Code (repository root)

```text
back/
├── pom.xml                            # Configuración Maven del backend
├── mvnw / mvnw.cmd                    # Maven Wrappers
└── src/
    ├── main/
    │   ├── java/com/cbo/players/
    │   │   ├── PlayersMarketApplication.java
    │   │   ├── adapter/               # Paquete base para futuros adapters externos
    │   │   ├── config/                # Configuraciones Spring (OpenAPI, Web, etc.)
    │   │   │   └── OpenApiConfig.java
    │   │   ├── controller/            # Controladores REST (Controllers)
    │   │   │   ├── AuthController.java
    │   │   │   └── PlayerController.java
    │   │   ├── dto/                   # Data Transfer Objects (DTOs)
    │   │   │   ├── request/
    │   │   │   │   ├── RegisterRequestDto.java
    │   │   │   │   ├── LoginRequestDto.java
    │   │   │   │   └── CreateApiKeyRequestDto.java
    │   │   │   └── response/
    │   │   │       ├── UserResponseDto.java
    │   │   │       ├── LoginResponseDto.java
    │   │   │       ├── ApiKeyCreatedResponseDto.java
    │   │   │       └── PlayerResponseDto.java
    │   │   ├── exception/             # Manejo global de excepciones (@RestControllerAdvice)
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── ApiException.java
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   ├── UserAlreadyExistsException.java
    │   │   │   ├── InvalidCredentialsException.java
    │   │   │   └── ErrorCode.java
    │   │   ├── model/                 # Entidades JPA del dominio
    │   │   │   ├── User.java
    │   │   │   ├── UserRole.java
    │   │   │   ├── ApiKey.java
    │   │   │   ├── Player.java
    │   │   │   ├── PlayerPosition.java
    │   │   │   └── PlayerQuote.java
    │   │   ├── repository/            # Interfaces de persistencia (Repositories)
    │   │   │   ├── UserRepository.java
    │   │   │   ├── ApiKeyRepository.java
    │   │   │   ├── PlayerRepository.java
    │   │   │   └── PlayerQuoteRepository.java
    │   │   ├── security/              # Componentes de Spring Security
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── JwtTokenProvider.java
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── ApiKeyAuthenticationFilter.java
    │   │   │   ├── CustomUserDetailsService.java
    │   │   │   └── UserPrincipal.java
    │   │   └── service/               # Lógica de negocio (Services)
    │   │       ├── AuthService.java
    │   │       ├── ApiKeyService.java
    │   │       └── PlayerService.java
    │   └── resources/
    │       ├── application.yml        # Configuración por perfiles y variables de entorno
    │       └── data.sql               # Semilla inicial para catálogo de jugadores
    └── test/
        ├── java/com/cbo/players/
        │   ├── controller/
        │   │   ├── AuthControllerTest.java
        │   │   └── PlayerControllerTest.java
        │   ├── service/
        │   │   ├── AuthServiceTest.java
        │   │   ├── ApiKeyServiceTest.java
        │   │   └── PlayerServiceTest.java
        │   ├── security/
        │   │   ├── JwtTokenProviderTest.java
        │   │   └── ApiKeyServiceSecurityTest.java
        │   └── exception/
        │       └── GlobalExceptionHandlerTest.java
        └── resources/
            └── application-test.yml
```

**Structure Decision**: Se adopta la estructura por capas modularizada dentro de un proyecto Spring Boot estándar con Maven, respetando la directriz de la Constitución: `Controllers` → `Services` → `Repositories` → `Adapters`.

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No aplica: No existen violaciones ni desvíos respecto a los principios constitucionales del proyecto.*
