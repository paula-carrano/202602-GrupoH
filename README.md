# 202602-GrupoH

Repositorio organizado por componentes:

| Carpeta | Contenido |
| --- | --- |
| `back/` | API Spring Boot con Java y Maven |
| `front/` | Aplicación React con Vite |
| `scraping/` | Reservada para el futuro módulo de scraping |
| `specs/` | Especificaciones y documentación del proyecto |

## Backend

Requisitos: Java 17 o superior y PostgreSQL para ejecutar la API. Desde `back/`, configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` según [la guía de inicio](specs/001-entrega1-foundation/quickstart.md).

```powershell
cd back
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

En macOS o Linux, usar `./mvnw` en lugar de `.\mvnw.cmd`.

### Ejecución con Docker Compose (Backend + PostgreSQL)

Permite ejecutar el backend y la base de datos PostgreSQL en contenedores aislados sin requerir Java ni PostgreSQL en el host:

1. **Configurar variables de entorno**:
   ```bash
   cp .env.example .env
   ```
   *(Asegurarse de definir `POSTGRES_PASSWORD` y `JWT_SECRET` en `.env`)*

2. **Construir y levantar servicios**:
   ```bash
   docker compose up --build -d
   ```

3. **Verificar estado y healthcheck**:
   ```bash
   docker compose ps
   curl http://localhost:8080/actuator/health
   ```

4. **Detener servicios**:
   - Conservando la persistencia de datos: `docker compose down`
   - Eliminando volúmenes: `docker compose down -v`

## Frontend

Requisito: Node.js 20.19+ o 22.12+ y npm.

```powershell
cd front
npm install
npm run dev
```

La URL local aparece en la salida de Vite. Para comprobar la compilación, ejecutar `npm run build`. En desarrollo, Vite redirige las solicitudes `/api` al backend en `http://localhost:8080`, por lo que ambos servicios deben estar activos para iniciar sesión y ver jugadores.

Rutas disponibles: `/login`, `/register`, `/home` y `/error`. El registro usa usuario, email y contraseña; el inicio de sesión requiere **usuario** y contraseña. `/home` se puede abrir sin sesión para revisar el diseño con datos de muestra. Al iniciar sesión, obtiene el catálogo real desde la API. Para conectar un backend en otro servidor, definir `VITE_API_BASE_URL` con la URL completa que termine en `/api/v1` antes de compilar el frontend.

## Scraping

`scraping/` está vacía por ahora. El archivo `.gitkeep` permite conservar la carpeta en Git.
