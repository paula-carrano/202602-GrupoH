# Feature Specification: Plataforma de Mercado y Valoración de Jugadores — Entrega 1: Foundation

**Feature Branch**: `001-entrega1-foundation`  
**Created**: 2026-09-16  
**Status**: Draft  
**Input**: User description: "Definir la Specification correspondiente EXCLUSIVAMENTE a la Entrega 1 — Foundation del proyecto Plataforma de Mercado y Valoración de Jugadores de Fútbol (Tokens de Jugadores), respetando la Constitución como contrato técnico y funcional principal."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro, Autenticación y Gestión de Identidad de Usuario (Priority: P1)

Como usuario de la plataforma, quiero registrarme proporcionando mis datos personales y credenciales, e iniciar sesión de forma segura para obtener un token de acceso JWT que me permita interactuar con las operaciones autorizadas del sistema.

**Why this priority**: Es el pilar fundamental del sistema de seguridad. Sin la gestión de identidad y autenticación de usuarios no es posible establecer sesiones seguras ni auditar quién interactúa con el backend.

**Independent Test**: Puede ser testeado de forma totalmente aislada invocando `POST /api/v1/auth/register` con datos válidos, validando la creación de la cuenta (status 201), autenticando luego vía `POST /api/v1/auth/login` para recibir un JWT válido (status 200), y verificando que el envío de credenciales inválidas resulte en un rechazo (status 401) con formato de error estandarizado en español.

**Acceptance Scenarios**:

1. **Registro Exitoso**:
   - **Given** un nuevo usuario con nombre de usuario único, dirección de correo electrónico válida y no registrada, y una contraseña que cumple los criterios de longitud y complejidad,
   - **When** envía una solicitud de registro a `POST /api/v1/auth/register`,
   - **Then** el sistema registra al usuario en la base de datos con la contraseña cifrada mediante algoritmo robusto (BCrypt), devuelve código HTTP `201 Created` y un DTO de respuesta con `id`, `username`, `email` y `createdAt`, sin exponer la contraseña ni el hash.

2. **Registro Fallido por Duplicidad de Correo o Username**:
   - **Given** un usuario que intenta registrarse con un correo electrónico o nombre de usuario que ya existe en el sistema,
   - **When** envía la solicitud a `POST /api/v1/auth/register`,
   - **Then** el sistema rechaza la solicitud devolviendo código HTTP `409 Conflict`, con una estructura `ProblemDetail` que incluye el código de error `USER_ALREADY_EXISTS` y un mensaje en español indicando la colisión.

3. **Registro Fallido por Validación de Datos**:
   - **Given** datos de registro incompletos o mal formateados (e.g. email inválido, contraseña menor a 8 caracteres, nombre de usuario en blanco),
   - **When** envía la solicitud a `POST /api/v1/auth/register`,
   - **Then** el sistema retorna código HTTP `400 Bad Request`, informando detalladamente las violaciones de campos con mensajes en español y el código `VALIDATION_FAILED`.

4. **Inicio de Sesión Exitoso**:
   - **Given** un usuario previamente registrado con credenciales correctas,
   - **When** envía una solicitud de login a `POST /api/v1/auth/login`,
   - **Then** el sistema valida las credenciales y devuelve código HTTP `200 OK` con un token JWT firmado, tipo Bearer y tiempo de expiración.

5. **Inicio de Sesión con Credenciales Inválidas**:
   - **Given** un intento de login con contraseña incorrecta o nombre de usuario inexistente,
   - **When** envía la solicitud a `POST /api/v1/auth/login`,
   - **Then** el sistema retorna código HTTP `401 Unauthorized` con el código de error `INVALID_CREDENTIALS` y el mensaje en español "Credenciales inválidas. Verifique su usuario y contraseña.", sin dar pistas sobre si el usuario existe o no por motivos de seguridad.

---

### User Story 2 - Generación y Validación de API Keys para Acceso M2M/Clientes (Priority: P2)

Como usuario o administrador autenticado en el sistema, quiero solicitar la emisión de una API Key para autorizar la integración de aplicaciones externas o servicios automatizados con la plataforma, garantizando que el secreto no quede expuesto en texto plano en la base de datos.

**Why this priority**: Cumple con el requerimiento de seguridad híbrida (JWT para usuarios / API Key para clientes programáticos o integradores) estipulado en la Constitución.

**Independent Test**: Iniciar sesión para obtener un JWT, emitir una API Key mediante `POST /api/v1/auth/api-keys`, comprobar que la clave en texto plano se entrega una única vez en la respuesta y se guarda hasheada en PostgreSQL, y luego usar dicha clave para acceder a recursos protegidos por API Key.

**Acceptance Scenarios**:

1. **Emisión Exitosa de API Key**:
   - **Given** un usuario autenticado mediante su token JWT,
   - **When** solicita una nueva API Key a `POST /api/v1/auth/api-keys` indicando una descripción o nombre identificatorio (e.g., "Integración Móvil"),
   - **Then** el sistema genera una clave criptográficamente segura con prefijo legible, persiste su hash criptográfico (SHA-256) en la base de datos vinculado al usuario, y retorna código HTTP `201 Created` conteniendo la API Key en texto plano por única vez junto con su fecha de creación y prefijo identificador.

2. **Solicitud de API Key sin Autenticación**:
   - **Given** un cliente que intenta solicitar una API Key sin incluir un JWT válido,
   - **When** envía la solicitud a `POST /api/v1/auth/api-keys`,
   - **Then** el sistema intercepta la petición y responde con código HTTP `401 Unauthorized` y código `UNAUTHORIZED_ACCESS`.

3. **Solicitud con Nombre Vacío o Inválido**:
   - **Given** una solicitud para emitir una API Key con el campo de nombre en blanco,
   - **When** envía la petición a `POST /api/v1/auth/api-keys`,
   - **Then** el sistema responde con código HTTP `400 Bad Request` indicando la violación de validación en español.

---

### User Story 3 - Consulta del Catálogo Inicial de Jugadores (Priority: P1)

Como usuario autenticado de la plataforma (vía JWT o API Key válida), quiero consultar el catálogo inicial de jugadores registrados en el sistema, tanto en listado general como por identificador individual, para conocer sus datos de base futbolística y de dominio.

**Why this priority**: Constituye la entidad central de dominio alrededor de la cual girará la cotización, los tokens y el mercado en etapas posteriores. Permite validar la arquitectura por capas, persistencia en PostgreSQL y desacoplamiento mediante DTOs.

**Independent Test**: Registrar datos iniciales de jugadores en base de datos, consultar `GET /api/v1/players` recibiendo una lista serializada de DTOs, y consultar `GET /api/v1/players/{id}` verificando tanto la respuesta 200 con el DTO correspondiente como la respuesta 404 para un identificador inexistente.

**Acceptance Scenarios**:

1. **Listado Completo de Jugadores**:
   - **Given** un catálogo inicial con jugadores persistidos en el sistema y un cliente autenticado,
   - **When** realiza una petición a `GET /api/v1/players`,
   - **Then** el sistema retorna código HTTP `200 OK` con un arreglo de objetos `PlayerResponseDto` conteniendo nombre, apellido, fecha de nacimiento (o `null` si no está disponible en el origen), nacionalidad, posición, equipo actual y liga, sin exponer entidades JPA directas.

2. **Consulta de Jugador por ID Existente**:
   - **Given** un jugador con identificador `1` en la base de datos y un cliente autenticado,
   - **When** realiza una petición a `GET /api/v1/players/1`,
   - **Then** el sistema retorna código HTTP `200 OK` con el `PlayerResponseDto` correspondiente a ese jugador.

3. **Consulta de Jugador por ID Inexistente**:
   - **Given** un identificador que no corresponde a ningún jugador en el sistema (e.g. `99999`),
   - **When** el cliente realiza una petición a `GET /api/v1/players/99999`,
   - **Then** el sistema responde con código HTTP `404 Not Found`, con una estructura `ProblemDetail` conteniendo el código de error `PLAYER_NOT_FOUND` y el mensaje en español "No se encontró ningún jugador con el identificador especificado."

4. **Acceso no Autenticado al Catálogo**:
   - **Given** un cliente que intenta invocar `GET /api/v1/players` o `GET /api/v1/players/{id}` sin proveer credenciales de autenticación válidas,
   - **When** envía la petición HTTP,
   - **Then** el sistema rechaza la petición con código HTTP `401 Unauthorized`.

---

### User Story 4 - Documentación Interactiva y Contrato OpenAPI (Priority: P3)

Como desarrollador o consumidor de la API, quiero acceder a la documentación interactiva Swagger UI y a la especificación OpenAPI v3 para conocer los contratos, schemas de DTOs, métodos de autenticación y códigos de error soportados.

**Why this priority**: Facilita la comunicación con el equipo frontend y garantiza la verificabilidad de los contratos REST de la Entrega 1.

**Independent Test**: Navegar a `/swagger-ui/index.html` y `/v3/api-docs` sin necesidad de autenticación, constatando que todos los endpoints de la Entrega 1 (`auth` y `players`) estén documentados con sus esquemas de request, response y errores.

**Acceptance Scenarios**:

1. **Acceso a la Interfaz Gráfica Swagger UI**:
   - **Given** la aplicación en ejecución,
   - **When** se solicita la ruta `/swagger-ui/index.html` en un navegador o cliente HTTP,
   - **Then** el sistema responde con código HTTP `200 OK`, renderizando la interfaz de Swagger UI configurada en springdoc-openapi.

2. **Acceso al JSON OpenAPI v3**:
   - **Given** la aplicación en ejecución,
   - **When** se solicita `/v3/api-docs`,
   - **Then** el sistema responde con código HTTP `200 OK` y un documento JSON válido OpenAPI 3.0 con la definición de esquemas, esquemas de seguridad Bearer JWT y ApiKey, y operaciones de Entrega 1.

---

### User Story 5 - Integración Continua, Compilación y Calidad SonarCloud (Priority: P2)

Como equipo de desarrollo, quiero que cada cambio enviado al repositorio ejecute una suite de validación automatizada en GitHub Actions que compile el backend, corra los tests unitarios y valide el umbral de calidad en SonarCloud con menos de 10 issues, para asegurar la estabilidad continua del código.

**Why this priority**: Requisito mandatorio de la Constitución para asegurar que ningún código defectuoso o con deuda técnica ingrese a las ramas principales.

**Independent Test**: Ejecutar el workflow `.github/workflows/ci.yml` ante un pull request simulado o push, verificando que la compilación y pruebas JUnit 5/Mockito finalicen exitosamente y que SonarCloud reporte un número total de issues menor a 10.

**Acceptance Scenarios**:

1. **Pipeline Exitoso**:
   - **Given** código fuente con tests unitarios pasando y sin violaciones críticas de SonarCloud,
   - **When** se realiza un `push` o `pull_request` a las ramas principales,
   - **Then** el workflow `.github/workflows/ci.yml` finaliza con estado global `SUCCESS`.

2. **Cumplimiento de Regla SonarCloud**:
   - **Given** el análisis de SonarCloud ejecutado sobre el backend,
   - **When** se totalizan los bugs, vulnerabilidades y code smells,
   - **Then** el total reportado es estrictamente menor a 10 (máximo 9 issues).

---

### Edge Cases

- **Formato Inválido de Parámetro en Path**: Cuando se envía un valor no numérico o alfanumérico malformado como identificador (e.g. `GET /api/v1/players/abc`), el sistema intercepta el error de conversión de tipo y devuelve `400 Bad Request` con código `INVALID_PARAMETER_FORMAT` y mensaje en español, en lugar de un error 500.
- **JWT Malformado o Expirado**: Si una petición envía un encabezado `Authorization: Bearer <token>` cuyo token está expirado, truncado o con firma adulterada, el filtro de Spring Security responde `401 Unauthorized` con código `TOKEN_EXPIRED` o `TOKEN_INVALID`.
- **API Key Revocada o Inactiva**: Si un cliente presenta una API Key que existe en base de datos pero posee la marca `active = false`, el sistema rechaza la solicitud con `403 Forbidden` y código `API_KEY_INACTIVE`.
- **Concurrencia en Registro**: Ante dos peticiones simultáneas con el mismo email o username, la restricción de unicidad de base de datos es capturada ordenadamente por `@RestControllerAdvice`, transformando la violación de integridad en una respuesta `409 Conflict` limpia sin mostrar trazas de PostgreSQL.
- **Jugador sin Fecha de Nacimiento en Origen**: Cuando la información de origen de un jugador no incluya fecha de nacimiento, el sistema admite que `birthDate` sea `null` tanto en la entidad `Player` como en los DTOs de respuesta, sin fallos de validación ni persistencia.
- **Jugador sin Cotizaciones Previas**: Al consultar el detalle de un jugador que recién se da de alta en el catálogo y aún no tiene cotizaciones asociadas, el endpoint retorna el DTO del jugador de manera consistente sin arrojar NullPointerException.
- **Payloads Vacíos o JSON Corrupto**: Si el cliente envía un cuerpo de solicitud con sintaxis JSON inválida en un endpoint POST, el sistema retorna `400 Bad Request` con código `MALFORMED_JSON`.

---

## Requirements *(mandatory)*

### Functional Requirements

#### 1. Configuración y Arquitectura
- **FR-001**: El backend DEBE desarrollarse utilizando Java 17+ y Spring Boot 3.x.
- **FR-002**: El sistema DEBE implementar una arquitectura en capas estrictas: `Controllers` → `Services` → `Repositories` → `Adapters`.
- **FR-003**: Los Controllers DEBEN comunicarse exclusivamente mediante Data Transfer Objects (DTOs), prohibiendo la exposición directa de entidades JPA a través de la API REST.
- **FR-004**: La persistencia principal del sistema DEBE realizarse sobre PostgreSQL utilizando Spring Data JPA e Hibernate.

#### 2. Seguridad e Identidad
- **FR-005**: El sistema DEBE proveer registro de usuarios mediante `POST /api/v1/auth/register`, encriptando las contraseñas con BCrypt antes de almacenarlas.
- **FR-006**: El sistema DEBE proveer login de usuarios mediante `POST /api/v1/auth/login`, validando credenciales y retornando un token JWT firmado.
- **FR-007**: El token JWT DEBE utilizarse para la autenticación e identificación del usuario en sesiones interactivas, con un tiempo de expiración configurable.
- **FR-008**: El sistema DEBE proveer un mecanismo de emisión de API Keys mediante `POST /api/v1/auth/api-keys`, accesible únicamente para usuarios autenticados vía JWT.
- **FR-009**: Las API Keys DEBEN ser generadas con entropía criptográfica suficiente (e.g. prefijo `cbo_` seguido de token aleatorio seguro). La clave en texto plano DEBE ser entregada una sola vez en el response DTO; en la base de datos DEBE persistirse exclusivamente su hash criptográfico (SHA-256) junto con un prefijo para identificación rápida.
- **FR-010**: El sistema DEBE distinguir la responsabilidad de JWT (autenticación de usuarios humanos / sesión) frente a API Keys (autorización / acceso e identificación de aplicaciones cliente o servicios integradores).
- **FR-011**: NINGÚN secreto, contraseña, clave JWT, API Key real ni credencial de base de datos DEBE quedar hardcodeada ni versionada en el repositorio. Toda información sensible DEBE inyectarse mediante variables de entorno o perfiles externos.
- **FR-012**: Los endpoints de documentación `/swagger-ui/**`, `/v3/api-docs/**`, y los endpoints de autenticación pública `POST /api/v1/auth/register` y `POST /api/v1/auth/login` DEBEN ser de acceso público; los demás endpoints de la Entrega 1 DEBEN requerir autenticación vía JWT o validación de API Key activa según la política configurada.

#### 3. Catálogo de Jugadores
- **FR-013**: El sistema DEBE exponer el listado de jugadores mediante `GET /api/v1/players`, retornando un conjunto de `PlayerResponseDto`.
- **FR-014**: El sistema DEBE exponer la consulta de un jugador por su identificador primario mediante `GET /api/v1/players/{id}`.
- **FR-015**: Si el jugador solicitado en `GET /api/v1/players/{id}` no existe, el sistema DEBE retornar código HTTP `404 Not Found` con mensaje en español y código de error específico.
- **FR-016**: La Entrega 1 NO DEBE implementar scraping de WhoScored ni consumo de la API externa de Football-Data.org. El catálogo de la Entrega 1 actúa como base inicial del dominio persistida localmente.

#### 4. Modelo Quote / PlayerQuote
- **FR-017**: El modelo inicial DEBE incluir la entidad de persistencia `PlayerQuote` (o `Quote`) vinculada a `Player`, almacenando como mínimo: identificador, referencia al jugador, valor o precio (BigDecimal), fecha/hora (timestamp) y moneda/unidad.
- **FR-018**: La Entrega 1 NO DEBE implementar lógica de recálculo, schedulers, rankings, ni estrategias de valoración para las cotizaciones. La entidad solo debe quedar modelada y persistible para dar soporte a las entregas subsiguientes.

#### 5. Manejo Centralizado de Errores y Validaciones
- **FR-019**: El sistema DEBE implementar un manejador global de excepciones anotado con `@RestControllerAdvice`.
- **FR-020**: Las respuestas de error DEBEN seguir el estándar `ProblemDetail` (RFC 7807), incluyendo:
  - `status`: código HTTP numérico.
  - `title`: descripción breve del tipo de error.
  - `detail`: mensaje comprensible para el usuario, obligatoriamente en **español**.
  - `instance`: URI o path de la solicitud que originó el error.
  - Extensiones adicionales:
    - `errorCode`: identificador alfanumérico estable (e.g. `USER_NOT_FOUND`, `INVALID_CREDENTIALS`).
    - `timestamp`: marca temporal en formato ISO-8601.
    - `correlationId`: identificador único de trazabilidad de la petición.
    - `violations`: arreglo de errores de validación de campo cuando aplique (campo y mensaje en español).
- **FR-021**: El backend NUNCA DEBE exponer stack traces, excepciones internas de Java, nombres de tablas, nombres de columnas ni restricciones de PostgreSQL en las respuestas al cliente.
- **FR-022**: Todas las validaciones de entrada en los DTOs DEBEN gestionarse mediante anotaciones de Bean Validation (`@NotBlank`, `@Email`, `@Size`, etc.), con mensajes de validación definidos explícitamente en **español**.

#### 6. Documentación OpenAPI / Swagger
- **FR-023**: El sistema DEBE integrar `springdoc-openapi` exponiendo `/swagger-ui/index.html` y `/v3/api-docs`.
- **FR-024**: Todos los endpoints de la Entrega 1 DEBEN estar documentados indicando: resumen, descripción, parámetros de entrada, esquemas DTO de request y response, esquemas de seguridad requeridos (Bearer JWT y API Key header `X-API-Key`) y los distintos códigos de respuesta HTTP posibles con sus esquemas de error.

#### 7. DevOps, Testing y SonarCloud
- **FR-025**: El repositorio DEBE contar con el workflow de CI configurado en `.github/workflows/ci.yml`.
- **FR-026**: El workflow de CI DEBE ejecutarse ante eventos `push` y `pull_request` dirigidos a las ramas principales.
- **FR-027**: El workflow de CI DEBE compilar el backend, ejecutar la suite de tests unitarios y reportar estado `SUCCESS`.
- **FR-028**: El proyecto DEBE estar integrado con SonarCloud en el workflow de CI y DEBE mantener estrictamente menos de 10 issues totales (máximo 9 issues sumando bugs, vulnerabilities y code smells).
- **FR-029**: Se DEBEN implementar tests unitarios exhaustivos utilizando JUnit 5 y Mockito para Services, Controllers y componentes de seguridad, cubriendo casos de éxito, validaciones y caminos de error.

---

### Endpoints Detallados de la Entrega 1

#### 1. Registro de Usuario: `POST /api/v1/auth/register`
- **Propósito**: Dar de alta un nuevo usuario en la plataforma con credenciales iniciales.
- **Método HTTP**: `POST`
- **URL**: `/api/v1/auth/register`
- **Autenticación/Autorización**: Pública (sin autenticación previa).
- **Request DTO (`RegisterRequestDto`)**:
  ```json
  {
    "username": "usuario123",
    "email": "usuario@ejemplo.com",
    "password": "Password123!"
  }
  ```
  - `username`: String, obligatorio, entre 3 y 30 caracteres, alfanumérico.
  - `email`: String, obligatorio, formato de email válido, máximo 100 caracteres.
  - `password`: String, obligatorio, mínimo 8 caracteres, al menos una mayúscula, un número y un carácter especial.
- **Response DTO (`UserResponseDto`)**:
  ```json
  {
    "id": 1,
    "username": "usuario123",
    "email": "usuario@ejemplo.com",
    "createdAt": "2026-09-16T12:00:00Z"
  }
  ```
- **Códigos HTTP y Respuestas**:
  - `201 Created`: Usuario registrado exitosamente. Retorna `UserResponseDto`.
  - `400 Bad Request`: Error de validación de campos. Retorna `ProblemDetail` con `errorCode: VALIDATION_FAILED` y lista de violaciones en español.
  - `409 Conflict`: Correo electrónico o username ya registrado. Retorna `ProblemDetail` con `errorCode: USER_ALREADY_EXISTS`.
  - `500 Internal Server Error`: Falla no controlada del servidor. Retorna `ProblemDetail` con `errorCode: INTERNAL_SERVER_ERROR`.

#### 2. Inicio de Sesión: `POST /api/v1/auth/login`
- **Propósito**: Autenticar credenciales de usuario y emitir token JWT de sesión.
- **Método HTTP**: `POST`
- **URL**: `/api/v1/auth/login`
- **Autenticación/Autorización**: Pública.
- **Request DTO (`LoginRequestDto`)**:
  ```json
  {
    "username": "usuario123",
    "password": "Password123!"
  }
  ```
  - `username`: String, obligatorio.
  - `password`: String, obligatorio.
- **Response DTO (`LoginResponseDto`)**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
    "tokenType": "Bearer",
    "expiresInSeconds": 86400
  }
  ```
- **Códigos HTTP y Respuestas**:
  - `200 OK`: Credenciales válidas. Retorna `LoginResponseDto`.
  - `400 Bad Request`: Campos faltantes o vacíos. Retorna `ProblemDetail` con `errorCode: VALIDATION_FAILED`.
  - `401 Unauthorized`: Credenciales inválidas (usuario inexistente o contraseña errónea). Retorna `ProblemDetail` con `errorCode: INVALID_CREDENTIALS` y mensaje en español.

#### 3. Emisión de API Key: `POST /api/v1/auth/api-keys`
- **Propósito**: Generar una nueva API Key para integraciones o acceso de clientes programáticos vinculada al usuario autenticado.
- **Método HTTP**: `POST`
- **URL**: `/api/v1/auth/api-keys`
- **Autenticación/Autorización**: Requiere token JWT válido en header `Authorization: Bearer <token>`.
- **Request DTO (`CreateApiKeyRequestDto`)**:
  ```json
  {
    "name": "Cliente Movil"
  }
  ```
  - `name`: String, obligatorio, entre 3 y 50 caracteres descriptivos.
- **Response DTO (`ApiKeyCreatedResponseDto`)**:
  ```json
  {
    "id": 1,
    "name": "Cliente Movil",
    "rawKey": "cbo_live_8f3d1b4a7e9c20f1a5b6c7d8e9f0a1b2",
    "prefix": "cbo_live_8f3d",
    "createdAt": "2026-09-16T12:00:00Z"
  }
  ```
- **Comportamiento Crítico de Seguridad**: El campo `rawKey` se expone de forma única y exclusiva en esta respuesta. En base de datos se almacena el hash criptográfico SHA-256 del valor para verificaciones posteriores.
- **Códigos HTTP y Respuestas**:
  - `201 Created`: Clave generada exitosamente.
  - `400 Bad Request`: Datos de solicitud inválidos. Retorna `ProblemDetail` con `errorCode: VALIDATION_FAILED`.
  - `401 Unauthorized`: Token JWT ausente, inválido o expirado. Retorna `ProblemDetail` con `errorCode: UNAUTHORIZED_ACCESS`.

#### 4. Listado de Jugadores: `GET /api/v1/players`
- **Propósito**: Obtener el catálogo inicial de jugadores registrados en el sistema.
- **Método HTTP**: `GET`
- **URL**: `/api/v1/players`
- **Autenticación/Autorización**: Requiere autenticación mediante Bearer JWT o API Key en encabezado `X-API-Key`.
- **Request**: Sin cuerpo (Body vacío).
- **Response DTO (`List<PlayerResponseDto>`)**:
  ```json
  [
    {
      "id": 1,
      "firstName": "Lionel",
      "lastName": "Messi",
      "birthDate": "1987-06-24",
      "nationality": "Argentina",
      "position": "FORWARD",
      "currentTeam": "Inter Miami",
      "league": "Major League Soccer",
      "active": true
    }
  ]
  ```
- **Campos del DTO (`PlayerResponseDto`)**:
  - `id`: Long, identificador único del jugador.
  - `firstName`: String, nombre.
  - `lastName`: String, apellido.
  - `birthDate`: LocalDate / String ISO-8601 (`yyyy-MM-dd`), NULLABLE (puede ser `null` si no está disponible en los datos de origen).
  - `nationality`: String, nacionalidad.
  - `position`: String, posición del jugador.
  - `currentTeam`: String, equipo actual.
  - `league`: String, liga a la que pertenece el club.
  - `active`: Boolean, estado de actividad.
- **Códigos HTTP y Respuestas**:
  - `200 OK`: Consulta exitosa. Retorna listado de jugadores (puede ser lista vacía `[]` si no hay registros).
  - `401 Unauthorized`: Cliente no autenticado o token/API Key inválida. Retorna `ProblemDetail`.

#### 5. Consulta de Jugador por ID: `GET /api/v1/players/{id}`
- **Propósito**: Obtener el detalle de un jugador específico según su identificador.
- **Método HTTP**: `GET`
- **URL**: `/api/v1/players/{id}`
- **Autenticación/Autorización**: Requiere autenticación mediante Bearer JWT o API Key en encabezado `X-API-Key`.
- **Parámetros de Path**:
  - `id`: Long, número positivo correspondiente al identificador único del jugador.
- **Response DTO (`PlayerResponseDto`)**: Mismo esquema que en el listado unitario.
- **Códigos HTTP y Respuestas**:
  - `200 OK`: Jugador encontrado. Retorna `PlayerResponseDto`.
  - `400 Bad Request`: Identificador con formato inválido (e.g. no numérico). Retorna `ProblemDetail` con `errorCode: INVALID_PARAMETER_FORMAT`.
  - `401 Unauthorized`: Falta de autenticación o credencial inválida.
  - `404 Not Found`: Jugador inexistente. Retorna `ProblemDetail` con `errorCode: PLAYER_NOT_FOUND` y mensaje en español "No se encontró ningún jugador con el identificador especificado."

#### 6. Endpoints de Documentación OpenAPI
- `GET /swagger-ui/index.html`: Interfaz Swagger UI navegable.
- `GET /v3/api-docs`: Especificación OpenAPI v3 en formato JSON.
- **Autenticación**: Acceso público sin credenciales.

---

### Key Entities *(Modelo Inicial)*

#### 1. Entity `User`
- **Propósito**: Representa un usuario registrado en la plataforma.
- **Atributos**:
  - `id`: Long, Primary Key auto-generada (`IDENTITY`).
  - `username`: String, NOT NULL, UNIQUE, longitud máxima 50.
  - `email`: String, NOT NULL, UNIQUE, longitud máxima 100.
  - `passwordHash`: String, NOT NULL, longitud 60 (BCrypt).
  - `role`: String / Enum (`ROLE_USER`, `ROLE_ADMIN`), NOT NULL.
  - `enabled`: Boolean, NOT NULL, default `true`.
  - `createdAt`: LocalDateTime, NOT NULL.
  - `updatedAt`: LocalDateTime, NOT NULL.
- **Relaciones**:
  - `@OneToMany` bidireccional o unidireccional con `ApiKey` (un usuario puede poseer múltiples API Keys).
- **Restricciones y Validaciones**: `email` y `username` únicos a nivel de esquema de base de datos; jamás exponer `passwordHash` en DTOs.

#### 2. Entity `ApiKey`
- **Propósito**: Representa una credencial de acceso para aplicaciones o clientes M2M vinculada a un usuario.
- **Atributos**:
  - `id`: Long, Primary Key auto-generada (`IDENTITY`).
  - `name`: String, NOT NULL, longitud máxima 50 (nombre descriptivo asignado por el usuario).
  - `keyHash`: String, NOT NULL, longitud 64 (digest hexadecimal SHA-256 de la API Key real).
  - `prefix`: String, NOT NULL, longitud 16 (primeros caracteres visibles de la clave, e.g. `cbo_live_8f3d`, para que el usuario la reconozca).
  - `active`: Boolean, NOT NULL, default `true`.
  - `createdAt`: LocalDateTime, NOT NULL.
  - `expiresAt`: LocalDateTime, NULLABLE (soporte para expiración futura si aplica).
- **Relaciones**:
  - `@ManyToOne(fetch = FetchType.LAZY)` con `User`, NOT NULL.
- **Restricciones**: El campo `keyHash` debe tener índice único para búsqueda rápida durante la validación del header `X-API-Key`.

#### 3. Entity `Player`
- **Propósito**: Representa la entidad central de un jugador de fútbol dentro del catálogo del sistema.
- **Atributos**:
  - `id`: Long, Primary Key auto-generada (`IDENTITY`).
  - `firstName`: String, NOT NULL, longitud máxima 50.
  - `lastName`: String, NOT NULL, longitud máxima 50.
  - `birthDate`: LocalDate, NULLABLE (opcional; puede ser nulo si los datos de origen no disponen de esta información).
  - `nationality`: String, NOT NULL, longitud máxima 50.
  - `position`: String / Enum (`GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`), NOT NULL.
  - `currentTeam`: String, NOT NULL, longitud máxima 80.
  - `league`: String, NOT NULL, longitud máxima 80 (e.g. "Premier League", "La Liga", etc.).
  - `active`: Boolean, NOT NULL, default `true`.
  - `createdAt`: LocalDateTime, NOT NULL.
  - `updatedAt`: LocalDateTime, NOT NULL.
- **Relaciones**:
  - `@OneToMany(mappedBy = "player")` con `PlayerQuote`.
- **Restricciones**: Restricción lógica para evitar duplicados idénticos en la misma liga/equipo.

#### 4. Entity `PlayerQuote` (o `Quote`)
- **Propósito**: Estructura de persistencia básica para almacenar la cotización monetaria de un jugador en el tiempo.
- **Atributos**:
  - `id`: Long, Primary Key auto-generada (`IDENTITY`).
  - `price`: BigDecimal, NOT NULL, precisión `(12, 4)` o `(10, 2)`, valor positivo mayor a cero.
  - `currency`: String, NOT NULL, longitud 3 (default `CRD` o `USD`).
  - `timestamp`: LocalDateTime, NOT NULL (marca temporal de la cotización).
- **Relaciones**:
  - `@ManyToOne(fetch = FetchType.LAZY)` con `Player`, NOT NULL.
- **Alcance Estricto Entrega 1**: Solo se define la estructura de la entidad y su relación con `Player`. NO se implementan algoritmos de cálculo, ni estrategias, ni recálculo periódico, ni triggers automáticos.

---

### Límites Explícitos (Fuera de Alcance para Entrega 1)

Quedan formalmente **EXCLUIDAS** de esta especificación y no generarán tareas de implementación en la Entrega 1:
- Scraping de WhoScored.
- Integración HTTP con la API de Football-Data.org.
- Implementación de estrategias de valoración (estrategias simples, complejas o configurables por pesos).
- Cálculo o recálculo periódico de cotizaciones y Scheduler semanal.
- Emisión y mercado de Tokens (100 tokens por jugador, valor inicial de 1 crédito).
- Operaciones de compra (`POST /api/v1/orders/buy`) y venta (`POST /api/v1/orders/sell`).
- Gestión de Portfolio de usuarios (`GET /api/v1/users/{id}/portfolio`).
- Historial de transacciones financieras (`GET /api/v1/users/{id}/transactions`).
- Ranking de jugadores (`GET /api/v1/players/ranking`).
- Mecanismos de Caché o integración con Redis.
- Perfiles de pruebas con HSQLDB/H2 de la Entrega 2.
- Perfiles de ejecución `unit` y `e2e` de la Entrega 2.
- Job específico de cobertura de tests (Coverage Job de Entrega 2).
- Tests de arquitectura (Architecture Tests de Entrega 3).
- Auditoría de Web Services, métricas con Prometheus o Spring Boot Actuator de Entrega 3.
- Creación de GitHub Release de Entrega 3.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los endpoints estipulados para la Entrega 1 (`/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/api-keys`, `/api/v1/players`, `/api/v1/players/{id}`) responden estrictamente según sus contratos REST y DTOs definidos sin exponer entidades JPA.
- **SC-002**: Las API Keys nunca se almacenan en texto plano en la base de datos; el 100% de las claves registradas en `ApiKey` son resguardadas mediante su hash criptográfico.
- **SC-003**: El 100% de las respuestas de error funcionales y de validación siguen la estructura estándar `ProblemDetail` y exponen mensajes claros redactados en idioma **español**, sin revelar trazas internas ni nombres de tablas o columnas.
- **SC-004**: Los contratos de todos los endpoints de la Entrega 1 se encuentran disponibles y navegables al 100% mediante Swagger UI en `/swagger-ui/index.html` y en formato OpenAPI v3 en `/v3/api-docs`.
- **SC-005**: La suite de tests unitarios construida con JUnit 5 y Mockito cubre el 100% de las reglas de negocio, flujos exitosos, validaciones y caminos de error críticos de los Services y Controllers de la Entrega 1.
- **SC-006**: El workflow automatizado en GitHub Actions `.github/workflows/ci.yml` finaliza con estado exitoso (`SUCCESS`) en cada `push` o `pull_request` a las ramas principales.
- **SC-007**: El análisis estático de SonarCloud reporta menos de 10 issues en total (un máximo de 9 issues entre bugs, vulnerabilidades y code smells), cumpliendo con el estándar de calidad fijado en la Constitución.

---

## Assumptions

- **A-001**: El motor de base de datos PostgreSQL estará aprovisionado y disponible para la aplicación mediante configuración por variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
- **A-002**: Para el firmado y validación de tokens JWT se utilizará el algoritmo `HMAC-SHA256` (HS256) empleando una clave secreta (`JWT_SECRET`) inyectada externamente con longitud mínima de 256 bits.
- **A-003**: En la Entrega 1 los datos del catálogo inicial de jugadores podrán ser poblados mediante un script SQL inicial (`data.sql` o mecanismo equivalente en perfiles de desarrollo) o mediante servicios internos de soporte, a fin de posibilitar la prueba de los endpoints de lectura `GET /api/v1/players`.
- **A-004**: La autenticación para consultar el catálogo de jugadores permite tanto el uso de tokens Bearer JWT como de API Keys válidas a través del header HTTP `X-API-Key`.
- **A-005**: Las API Keys emitidas tienen una longitud uniforme y utilizan el prefijo `cbo_live_` para facilitar su identificación y diferenciación en entornos de inspección y logs sin filtrar su secreto.
- **A-006**: Se cuenta con el repositorio de SonarCloud y la organización debidamente vinculados con los secretos `SONAR_TOKEN` configurados en GitHub Secrets para el análisis del pipeline.
