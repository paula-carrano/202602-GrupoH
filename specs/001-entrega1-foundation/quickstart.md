# Quickstart & Validation Guide: Entrega 1 — Foundation

**Feature**: `001-entrega1-foundation`  
**Date**: 2026-09-16  
**Status**: Completed  

Esta guía describe los pasos necesarios para levantar el entorno de desarrollo local, ejecutar la suite de pruebas automatizadas y validar de punta a punta cada uno de los flujos y endpoints correspondientes a la **Entrega 1 — Foundation**.

---

## 1. Prerrequisitos

- **Java Development Kit (JDK)**: Versión 17 o superior.
- **Maven**: Versión 3.9+ (o utilizar el wrapper incluido `./mvnw`).
- **PostgreSQL**: Servidor 14+ activo y accesible (local o mediante contenedor Docker).
- **Cliente HTTP**: `curl`, HTTPie o Postman.

---

## 2. Configuración de Variables de Entorno

Antes de iniciar la aplicación, exportar las siguientes variables sensibles en su terminal:

```bash
# Conexión a Base de Datos PostgreSQL
export DB_URL="jdbc:postgresql://localhost:5432/cbo_players_db"
export DB_USERNAME="postgres"
export DB_PASSWORD="secret_password"

# Configuración de Seguridad JWT (clave HMAC-SHA256 de mínimo 256 bits)
export JWT_SECRET="un_secreto_super_seguro_para_firmar_jwt_con_mas_de_256_bits_de_longitud"
export JWT_EXPIRATION_MS="86400000"  # 24 horas en milisegundos
```

*(En Windows PowerShell utilizar `$env:DB_URL="..."`, etc.)*

---

## 3. Compilación y Ejecución de Pruebas

Para compilar el proyecto y verificar que todos los tests unitarios y de validación pasen limpiamente:

```bash
mvn clean test
```

Para empaquetar y verificar métricas de calidad:

```bash
mvn clean verify
```

---

## 4. Inicialización de la Aplicación

```bash
mvn spring-boot:run
```

La aplicación quedará disponible en `http://localhost:8080`.

---

## 5. Escenarios de Validación de Punta a Punta

### Escenario 1: Registro de un Nuevo Usuario

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "diego_maradona",
    "email": "diego@ejemplo.com",
    "password": "Password123!"
  }'
```
**Resultado Esperado**: Código HTTP `201 Created` con JSON conteniendo `id`, `username`, `email`, `createdAt`. Sin hash de contraseña.

---

### Escenario 2: Intento de Registro con Email Duplicado (Error)

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "otro_usuario",
    "email": "diego@ejemplo.com",
    "password": "Password123!"
  }'
```
**Resultado Esperado**: Código HTTP `409 Conflict` con `ProblemDetail` conteniendo `errorCode: "USER_ALREADY_EXISTS"` y mensaje explicativo en español.

---

### Escenario 3: Inicio de Sesión y Obtención de JWT

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "diego_maradona",
    "password": "Password123!"
  }'
```
**Resultado Esperado**: Código HTTP `200 OK` retornando `accessToken`, `tokenType: "Bearer"`, `expiresInSeconds: 86400`.

*Guardar el token retornado:*
```bash
TOKEN="<copiar_el_accessToken_aqui>"
```

---

### Escenario 4: Emisión de una API Key

```bash
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Cliente de Consola"
  }'
```
**Resultado Esperado**: Código HTTP `201 Created` devolviendo `id`, `name`, `rawKey: "cbo_live_..."`, `prefix: "cbo_live_..."` y `createdAt`.

*Guardar la API Key retornada:*
```bash
API_KEY="<copiar_el_rawKey_aqui>"
```

---

### Escenario 5: Consulta del Catálogo de Jugadores con JWT

```bash
curl -X GET http://localhost:8080/api/v1/players \
  -H "Authorization: Bearer $TOKEN"
```
**Resultado Esperado**: Código HTTP `200 OK` con un arreglo JSON de jugadores (`PlayerResponseDto`), contemplando `birthDate` que puede ser fecha válida o `null`.

---

### Escenario 6: Consulta del Catálogo de Jugadores con API Key

```bash
curl -X GET http://localhost:8080/api/v1/players \
  -H "X-API-Key: $API_KEY"
```
**Resultado Esperado**: Código HTTP `200 OK` con el listado de jugadores, verificando que la API Key es reconocida y autorizada de forma independiente al JWT.

---

### Escenario 7: Consulta de Jugador por ID Existente

```bash
curl -X GET http://localhost:8080/api/v1/players/1 \
  -H "Authorization: Bearer $TOKEN"
```
**Resultado Esperado**: Código HTTP `200 OK` con el objeto `PlayerResponseDto` del jugador 1.

---

### Escenario 8: Consulta de Jugador Inexistente (404 Not Found)

```bash
curl -X GET http://localhost:8080/api/v1/players/99999 \
  -H "Authorization: Bearer $TOKEN"
```
**Resultado Esperado**: Código HTTP `404 Not Found` con estructura `ProblemDetail`, `errorCode: "PLAYER_NOT_FOUND"` y mensaje en español: *"No se encontró ningún jugador con el identificador especificado."*

---

### Escenario 9: Acceso no Autenticado (401 Unauthorized)

```bash
curl -X GET http://localhost:8080/api/v1/players
```
**Resultado Esperado**: Código HTTP `401 Unauthorized`.

---

### Escenario 10: Documentación Swagger UI y OpenAPI v3

- Abrir en el navegador: `http://localhost:8080/swagger-ui/index.html`
- Obtener especificación JSON: `curl http://localhost:8080/v3/api-docs`

**Resultado Esperado**: Acceso libre y sin autenticación, interfaz interactiva con todos los endpoints de Entrega 1 documentados.

---

## 6. Documentos de Referencia

- Especificación de Requisitos: [`spec.md`](./spec.md)
- Modelo de Datos Detallado: [`data-model.md`](./data-model.md)
- Contratos OpenAPI: [`contracts/openapi.yaml`](./contracts/openapi.yaml)
- Decisiones Técnicas: [`research.md`](./research.md)
