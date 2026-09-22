# Specification: Dockerización de Backend + Base de Datos

## 1. Contexto

El repositorio tendrá 3 carpetas principales: `/front`, `/back` y `/scraper`. Esta spec cubre **únicamente** la infraestructura Docker de `/back` y su base de datos (PostgreSQL). El objetivo es tener una base de Docker Compose funcionando antes de empezar el desarrollo del microservicio de scraping, para que cuando exista `/scraper` solo haya que sumarle un servicio al compose ya armado.

## 2. Alcance

### Incluido
- Dockerfile del backend (`/back`).
- Servicio de PostgreSQL en `docker-compose.yml`.
- Red y variables de entorno para que el backend se conecte a la base por nombre de servicio, no por `localhost`.
- Persistencia de datos de PostgreSQL mediante volumen.
- Manejo de variables sensibles (credenciales de DB, JWT secret) fuera del repositorio.

### Explícitamente fuera de alcance (no implementar en esta tarea)
- Cualquier servicio de `/front`.
- Cualquier servicio de `/scraper` (todavía no existe el código).
- Cambios a GitHub Actions / CI (esto es para entorno local, no reemplaza el pipeline).
- Orquestación productiva (Kubernetes, etc.).

## 3. Requisitos funcionales

1. Debe existir un `Dockerfile` multi-stage dentro de `/back`:
   - Stage de build: compila el proyecto (Maven o Gradle, según lo que ya use el backend).
   - Stage de runtime: imagen liviana (ej. `eclipse-temurin:17-jre`) que solo contiene el jar/artefacto final, no las herramientas de build.
2. Debe existir un `docker-compose.yml` en la raíz del repositorio con dos servicios:
   - `db`: imagen oficial `postgres` (fijar versión, no `latest`), con volumen nombrado para persistir los datos entre reinicios.
   - `backend`: construido a partir del `Dockerfile` de `/back`, con `depends_on` apuntando a `db` y healthcheck para no arrancar antes de que la base esté lista.
3. El backend debe conectarse a la base usando el nombre del servicio Docker (`db`) como host, mediante un profile de Spring (ej. `application-docker.yml` o variables de entorno que sobreescriban `SPRING_DATASOURCE_URL`).
4. Debe existir un archivo `.env.example` (commiteado) documentando las variables necesarias (usuario/password de DB, nombre de la base, JWT secret, etc.) sin valores reales. El `.env` real no debe commitearse (agregar a `.gitignore` si no está ya).
5. El `docker-compose.yml` debe dejar preparada la red (`networks:`) de forma que agregar el servicio `scraper` en el futuro no requiera reestructurar lo ya definido — no hace falta declarar el servicio todavía, solo que la red y las convenciones de nombres no obliguen a romper lo existente.

## 4. Requisitos no funcionales

- Los tiempos de build de la imagen deben aprovechar cache de capas de Docker (copiar primero los archivos de dependencias — `pom.xml`/`build.gradle` — y recién después el código fuente).
- Ninguna credencial real debe quedar hardcodeada en el `Dockerfile`, `docker-compose.yml` ni en el repositorio, consistente con la sección de datos sensibles de la constitución del proyecto.
- El healthcheck de `db` debe usar `pg_isready` o equivalente.

## 5. Criterios de aceptación

- **Dado** un checkout limpio del repositorio con Docker instalado, **cuando** se ejecuta `docker compose up --build`, **entonces** el backend levanta y responde correctamente en su endpoint de health, sin necesidad de tener Java o PostgreSQL instalados localmente.
- **Dado** el sistema corriendo, **cuando** se reinician los contenedores (`docker compose down` seguido de `docker compose up`, sin `-v`), **entonces** los datos previamente persistidos en PostgreSQL siguen disponibles.
- **Dado** el repositorio clonado por otra persona del equipo, **cuando** copia `.env.example` a `.env` y completa sus propios valores, **entonces** el sistema funciona sin tocar código ni el compose.
- El `docker-compose.yml` no debe incluir ningún servicio de frontend ni de scraper en esta etapa.

## 6. Entregables esperados

- `/back/Dockerfile`
- `docker-compose.yml` (raíz del repo)
- `.env.example`
- Sección breve en el `README` con el comando para levantar el entorno (`docker compose up --build`)

## 7. Fuera de esta tarea (para más adelante, no pedir ahora)

- Servicio `scraper` en el compose (se agregará cuando exista `/scraper`, siguiendo la spec propia de ese componente).
- Servicio de frontend en el compose.
- Cualquier configuración de despliegue en un entorno real (esto es solo para desarrollo local).
