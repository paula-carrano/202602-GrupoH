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

## Frontend

Requisito: Node.js 20.19+ o 22.12+ y npm.

```powershell
cd front
npm install
npm run dev
```

La URL local aparece en la salida de Vite. Para comprobar la compilación, ejecutar `npm run build`. El frontend es una base inicial y todavía no consume la API.

## Scraping

`scraping/` está vacía por ahora. El archivo `.gitkeep` permite conservar la carpeta en Git.
