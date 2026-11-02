# Catálogo persistido y sincronización

El backend importa los planteles actuales de Premier League (`PL`), Bundesliga
(`BL1`), La Liga (`PD`), Serie A (`SA`) y Ligue 1 (`FL1`). Football-Data
provee los jugadores; WhoScored agrega estadísticas únicamente a los vinculados.
Las consultas del catálogo leen PostgreSQL y no consultan proveedores externos.

## Configuración

En el `.env` de la raíz para Docker:

```dotenv
FOOTBALL_DATA_API_KEY=tu_clave_del_proveedor
SCRAPER_API_KEY=tu_clave_interna
CATALOG_SYNC_ENABLED=true
CATALOG_SYNC_CRON=0 0 15 * * MON
CATALOG_SYNC_ZONE=America/Argentina/Buenos_Aires
```

Docker habilita el scheduler por defecto. En ejecución local está deshabilitado
salvo que se configure `CATALOG_SYNC_ENABLED=true`. El backend local recibe
variables de entorno; no carga automáticamente un archivo `.env`.
El scraper local carga `scraping/.env`, donde la clave interna se llama `API_KEY`.

El horario predeterminado es **lunes a las 15:00 de Argentina**. El backend debe
estar funcionando; no recupera ejecuciones perdidas ni importa al arrancar.
La importación es secuencial y respeta el limitador de Football-Data del scraper.
Las cinco ligas pueden tardar varios minutos. No hay reintentos en el backend.

## Primera importación desde Postman

1. Ejecutar `docker compose up --build -d`.
2. Registrar o utilizar un usuario y asignarle el rol de administrador en desarrollo.
   Desde `docker compose exec db psql -U postgres -d cbo_players_db`, ejecutar:

   ```sql
   UPDATE users SET role = 'ROLE_ADMIN' WHERE username = 'tu_usuario';
   ```

   Sustituir usuario/base si se personalizaron. No modificar otros usuarios.
3. Iniciar sesión nuevamente con `POST http://localhost:8080/api/v1/auth/login`:

   ```json
   {"username":"tu_usuario","password":"tu_contraseña"}
   ```

4. En Postman usar **Authorization → Bearer Token**, con `accessToken` de la respuesta.
5. Enviar `POST http://localhost:8080/api/v1/admin/catalog-sync`, sin body.
   Devuelve `202`, `{"id":1}` y el header `Location`.
6. Consultar `GET http://localhost:8080/api/v1/admin/catalog-sync/1`.
   Responde con estado, fechas, contadores y errores resumidos. Si ya hay una
   ejecución activa, otra solicitud de inicio devuelve `409`.
7. Consultar `GET http://localhost:8080/api/v1/players` para ver los jugadores
   persistidos. Este endpoint requiere autenticación, pero no rol de administrador.

Los endpoints administrativos requieren `ROLE_ADMIN`, tanto con JWT como con
una API key de usuario emitida por el backend. La clave interna del scraper no
otorga permisos de administración sobre el backend.

## Vincular WhoScored

Para iniciar la vinculaciÃ³n masiva de jugadores activos sin `whoscored_id`, enviar
`POST /api/admin/whoscored/bulk-link` con un usuario administrador. El proceso
busca los perfiles por plantel y usa la bÃºsqueda por nombre para los jugadores
que no aparecen en la pÃ¡gina del equipo. Solo asocia una coincidencia Ãºnica de
nombre; los casos sin resultado o ambiguos se conservan en `reviewItems`.

La respuesta incluye `id` y contadores iniciales. Consultar el progreso con
`GET /api/admin/whoscored/bulk-link/{id}`. Los estados son `RUNNING`,
`COMPLETED` y `BLOCKED`; ante un bloqueo de WhoScored, el proceso se detiene
para no seguir enviando solicitudes. Una ejecuciÃ³n no guarda su progreso al
reiniciar el backend; se puede volver a iniciar y los jugadores ya vinculados
se omiten.

Al terminar, iniciar `POST /api/admin/catalog-sync` para traer y guardar las
estadÃ­sticas de los jugadores vinculados.

Obtener el ID local del jugador en el catálogo y confirmar su identidad en
WhoScored. El número de su URL `/Players/123/...` es el ID externo.
No usar el ID de Football-Data como si fuera el de WhoScored.

```http
PUT /api/v1/admin/players/ID_LOCAL/whoscored
Content-Type: application/json
Authorization: Bearer TOKEN_ADMIN

{"whoscoredId":123}
```

Devuelve `playerId` y `whoscoredId`. Un ID ya vinculado a otro jugador devuelve
`409`; IDs no positivos, `400`. Para desvincular, enviar
`{"whoscoredId":null}`. Cambiar o quitar la asociación elimina las estadísticas
anteriores; repetir la misma asociación las conserva.

La vinculación no consulta WhoScored automáticamente. Ejecutar la sincronización
manual o esperar al lunes y consultar:

```http
GET /api/v1/players/ID_LOCAL/stats
Authorization: Bearer TOKEN
```

La respuesta contiene `playerId`, `whoscoredId`, `lastSuccessfulSyncAt` y
`metrics` con las diez métricas. Si aún no hay estadísticas, devuelve `404`.
Solo se guarda la última lectura. El scraper no identifica su temporada o
competencia: la fecha de sincronización no representa el período estadístico.

## Persistencia y fallos

- `players`: datos del catálogo, IDs externos y última importación correcta.
- `player_statistics`: una lectura por jugador, ID de WhoScored utilizado y fecha.
- `catalog_sync_runs`: ejecuciones manuales/programadas y contadores.
- `catalog_sync_errors`: hasta 100 resúmenes por ejecución; `errorCount` cuenta
  todos los errores, aunque se alcance el límite de resúmenes.

Estados: `RUNNING`, `SUCCESS`, `PARTIAL`, `FAILED`, `INTERRUPTED`.
Los fallos aislados permiten continuar con otros equipos/jugadores. Los errores
de autenticación o de cuota detienen la fase del proveedor afectado. Un bloqueo
de WhoScored también detiene su fase para evitar repetir solicitudes bloqueadas.
Las lecturas anteriores se conservan ante errores. Los planteles vacíos no
eliminan jugadores, y no se desactivan automáticamente jugadores ausentes.

Cada equipo se guarda en una transacción corta, después de consultar la fuente.
Las restricciones únicas sobre IDs externos impiden duplicados. Las
actualizaciones conservan el ID local y las vinculaciones manuales.
Una respuesta de estadísticas en vuelo se descarta si cambió la vinculación.

Se admite una sola instancia del backend: un trabajador y un bloqueo en memoria
evitan superposiciones. Al reiniciar, las ejecuciones que quedaron `RUNNING`
pasan a `INTERRUPTED`. No se reanudan automáticamente.

## Base existente y datos demo

Hibernate agrega tablas y columnas. Un inicializador de compatibilidad para
PostgreSQL amplía nombres/apellidos a 200 caracteres y permite nacionalidad y
posición nulas en tablas preexistentes. No borra registros.

La carga SQL automática está deshabilitada. Para una base de demostración,
activar el perfil `demo`, que carga `demo-data.sql`.
Los jugadores cargados anteriormente permanecen; no se fusionan por nombre
con los importados. Pueden reconciliarse manualmente después de verificar su
identidad y sus relaciones. La ausencia de datos se muestra como `N/D`.

## Verificación

```sql
SELECT league, count(*) FROM players
WHERE football_data_id IS NOT NULL GROUP BY league ORDER BY league;

SELECT id, first_name, last_name, football_data_id, whoscored_id,
       current_team, last_catalog_sync_at
FROM players WHERE football_data_id IS NOT NULL ORDER BY id LIMIT 20;

SELECT player_id, whoscored_id, rating, last_successful_sync_at
FROM player_statistics;

SELECT id, status, origin, teams_processed, players_created, players_updated,
       statistics_updated, error_count
FROM catalog_sync_runs ORDER BY id DESC;
```

Repetir una importación actualiza por `football_data_id` sin duplicar jugadores.
Reiniciar los servicios conserva los datos en el volumen PostgreSQL.

Pruebas: `.\mvnw.cmd test` desde `back/`;
`npm test` desde `scraping/` y `front/`.
Las pruebas automatizadas simulan proveedores y no necesitan claves reales.
