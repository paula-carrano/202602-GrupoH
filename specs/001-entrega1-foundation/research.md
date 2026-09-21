# Research & Technical Decisions: Entrega 1 — Foundation

**Feature**: `001-entrega1-foundation`  
**Date**: 2026-09-16  
**Status**: Completed  

Este documento consolida las decisiones técnicas, alternativas evaluadas y justificaciones arquitectónicas para la implementación de la **Entrega 1 — Foundation** de la *Plataforma de Mercado y Valoración de Jugadores de Fútbol*, en estricto cumplimiento con la Constitución del Proyecto.

---

## 1. Runtime, Framework y Herramienta de Construcción

### Decisión
- **Java**: 17 LTS (o superior, e.g. 21).
- **Framework**: Spring Boot 3.2.x / 3.3.x con Spring Web, Spring Security y Spring Data JPA.
- **Herramienta de Construcción**: Maven (`pom.xml`) con Maven Wrapper (`mvnw`).

### Rationale
- Spring Boot 3.x exige como línea base Java 17+, proveyendo soporte nativo para `ProblemDetail` (RFC 7807), mejoras de rendimiento en Jakarta EE 10 y compatibilidad de última generación con `springdoc-openapi` v2.
- Maven con wrapper garantiza reproducibilidad idéntica tanto en entornos locales (Windows/Linux/macOS) como en el runner de GitHub Actions y el escaneo de SonarCloud.

### Alternativas Consideradas
- *Gradle*: Ofrece tiempos de compilación incrementales rápidos, pero Maven posee una integración más estandarizada y directa con los plugins oficiales de SonarCloud y Spring Boot en proyectos académicos universitarios.

---

## 2. Arquitectura en Capas y Aislamiento de Entidades

### Decisión
- Implementar una separación estricta por capas:
  `Controllers` → `Services` → `Repositories` → `Adapters`.
- **Controllers**: Manejan HTTP, validaciones (`@Valid`), y transforman requests/responses utilizando DTOs.
- **Services**: Contienen las reglas de negocio, lógica transaccional (`@Transactional`) y orquestación.
- **Repositories**: Interfaces de Spring Data JPA que interactúan con PostgreSQL.
- **Adapters**: Paquete preparado para clientes e integraciones externas (en Entrega 1 queda estructurado como interfaz/puerto base sin scraping ni APIs externas).
- **Prohibición de exposición**: Ninguna entidad JPA (`@Entity`) sale del controlador; todas las respuestas utilizan DTOs dedicados (e.g. `UserResponseDto`, `PlayerResponseDto`).

### Rationale
- Cumple directamente con el principio 3.1 y 3.3 de la Constitución.
- Evita el acoplamiento directo entre el esquema relacional de la base de datos y la API REST externa, previniendo vulnerabilidades de asignación masiva (*mass assignment*) y problemas de serialización recursiva o `LazyInitializationException`.

### Alternativas Consideradas
- *Exponer entidades directamente*: Descartado expresamente por la Constitución y por ser un anti-patrón de seguridad y diseño.

---

## 3. Estrategia de Seguridad Híbrida: JWT vs API Key

### Decisión
- **JWT (JSON Web Token)**:
  - *Propósito*: Autenticación de usuarios interactivos / humanos en sesiones web o móviles.
  - *Algoritmo*: HMAC-SHA256 (`HS256`).
  - *Manejo*: Generado en `POST /api/v1/auth/login`, transmitido en header `Authorization: Bearer <token>`, validado mediante un filtro `OncePerRequestFilter` (`JwtAuthenticationFilter`).
  - *Expiración*: Configurable mediante `JWT_EXPIRATION_MS` (default 24 horas).
  - *Payload*: `sub` (username), `userId`, `role`, `iat`, `exp`.

- **API Key**:
  - *Propósito*: Autorización de acceso programático para aplicaciones cliente, sistemas M2M o integraciones backend.
  - *Formato de la clave*: Prefijo legible + entropía criptográfica (e.g., `cbo_live_` seguido de 32 caracteres hexadecimales/alfanuméricos generados con `SecureRandom`).
  - *Emisión (`POST /api/v1/auth/api-keys`)*: El usuario autenticado vía JWT solicita la clave. El servidor entrega la clave en texto plano **una única vez** en el response DTO.
  - *Almacenamiento*: La base de datos almacena únicamente el hash criptográfico SHA-256 (`keyHash`) y los primeros 12-16 caracteres como prefijo identificatorio (`prefix`), junto con el estado `active = true` y la relación ManyToOne hacia `User`.
  - *Validación*: Filtro de seguridad (`ApiKeyAuthenticationFilter`) que extrae el header `X-API-Key`, calcula el hash SHA-256 y busca una coincidencia activa en el `ApiKeyRepository`.

### Rationale
- Satisface la exigencia constitucional de delimitar claramente ambas responsabilidades y no asumir que cumplen la misma función.
- Sigue el estándar de la industria (similar a Stripe, GitHub o SendGrid) al no almacenar nunca secretos de API Keys en texto plano en la base de datos, mitigando fugas ante dumps o lecturas indebidas.

### Alternativas Consideradas
- *Almacenar API Keys en texto plano*: Prohibido por la Constitución.
- *Usar BCrypt para API Keys*: Descartado porque BCrypt es un hash con sal aleatoria (salted) que no permite lookup directo por índice (`findByKeyHash`), requiriendo iterar o comparar todas las claves, lo cual degradaría la latencia de cada request a O(N). SHA-256 sobre una clave de alta entropía (>= 128 bits) es determinístico y seguro contra ataques de preimagen.

---

## 4. Gestión de Secretos y Configuración Externa

### Decisión
- Todas las variables sensibles se gestionan fuera del código y del repositorio mediante variables de entorno:
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - `JWT_SECRET` (mínimo 256 bits / 32 bytes)
  - `JWT_EXPIRATION_MS`
- En `application.yml` o `application.properties` se utilizan placeholders con valores por defecto no productivos exclusivamente para tests locales o perfiles de desarrollo (`${DB_URL:jdbc:postgresql://localhost:5432/cbo_db}`).

### Rationale
- Cumplimiento estricto del requisito 4.3 de la Constitución: "Nunca deberán almacenarse en el repositorio contraseñas, secretos JWT, API Keys reales, credenciales de BD ni variables de producción".

---

## 5. Manejo Global de Excepciones y Formato de Errores

### Decisión
- Se utiliza `@RestControllerAdvice` (`GlobalExceptionHandler`) extendiendo `ResponseEntityExceptionHandler`.
- Estructura de respuesta basada en `ProblemDetail` (RFC 7807), complementada con extensiones tipadas:
  - `status`: Código HTTP.
  - `title`: Título general del error.
  - `detail`: Mensaje claro, comprensible y obligatoriamente en **español**.
  - `instance`: URI solicitada.
  - `errorCode`: Código alfanumérico estable en MAYÚSCULAS para que el frontend no dependa de textos (e.g. `USER_NOT_FOUND`, `INVALID_CREDENTIALS`, `EMAIL_ALREADY_EXISTS`, `PLAYER_NOT_FOUND`, `UNAUTHORIZED_ACCESS`, `FORBIDDEN_OPERATION`, `VALIDATION_FAILED`, `INTERNAL_SERVER_ERROR`).
  - `timestamp`: Fecha/hora ISO-8601.
  - `correlationId`: Identificador generado por petición para trazabilidad (`UUID`).
  - `violations`: Arreglo de violaciones de validación (campo + mensaje en español) ante `MethodArgumentNotValidException`.

### Rationale
- Cumple la Constitución al evitar la exposición de stack traces, nombres de tablas/columnas o excepciones JDBC/JPA, y garantiza soporte estándar en clientes REST.

---

## 6. Persistencia y Modelo Inicial

### Decisión
- **PostgreSQL** como motor de persistencia principal.
- Entidades mínimas requeridas:
  - `User`: Gestión de cuentas y contraseñas cifradas.
  - `ApiKey`: Claves programáticas vinculadas a usuarios.
  - `Player`: Entidad central con datos deportivos y biográficos (`birthDate` configurado como `nullable = true` para tolerar datos incompletos de orígenes de scraping).
  - `PlayerQuote`: Representación base de cotización histórica (ManyToOne con `Player`), con `price`, `currency` y `timestamp`.
- **Límites**: No se definen entidades de `Order`, `Portfolio`, `Transaction` ni schedulers de recálculo en este alcance.

### Rationale
- Aísla la base de datos para la Entrega 1 dejando el modelo extensible para las entregas 2 y 3 sin sobre-ingeniería ni acoplamiento anticipado.

---

## 7. Documentación OpenAPI / Swagger

### Decisión
- Dependencia: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0` (o compatible con Spring Boot 3.x).
- Endpoints expuestos sin autenticación:
  - `/swagger-ui/index.html`
  - `/v3/api-docs`
- Configuración de componentes de seguridad OpenAPI:
  - `bearerAuth`: HTTP Bearer JWT.
  - `apiKeyAuth`: ApiKey en encabezado HTTP `X-API-Key`.

---

## 8. Estrategia de Testing y DevOps

### Decisión
- **Testing**: JUnit 5 + Mockito + Spring Security Test.
  - Pruebas unitarias para Services y lógica de hashing/validación.
  - Pruebas de integración web con `MockMvc` para Controllers y validaciones de DTOs.
- **CI Workflow**: `.github/workflows/ci.yml` configurado con:
  - Trigger en `push` y `pull_request` a ramas `main` / `develop`.
  - Setup de JDK 17 (Eclipse Temurin).
  - Cache de dependencias Maven.
  - Ejecución de `mvn clean verify`.
  - Integración con SonarCloud Scanner con threshold de Quality Gate < 10 issues (máx 9 issues totales).
