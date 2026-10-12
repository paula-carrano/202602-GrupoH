# Contexto y Apuntes de Cátedra - UNQ Desarrollo de Aplicaciones

> [!IMPORTANT]
> **Documento Oficial en Google Docs:** [Google Docs - UNQ Desarrollo de Aplicaciones](https://docs.google.com/document/d/1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us/edit?usp=drivesdk)  
> **Document ID:** `1oHy24FY3dOft9mu7OTGj6ndklDz9e2yVK6YZ0w3P0us`  
> **Web de la materia:** https://sites.google.com/view/unq-desarrollo-de-aplicaciones/  
> **Instrucción para Agentes de IA:** Este archivo local es la copia y réplica de contexto del Google Doc oficial del proyecto. Cualquier nuevo agente o sesión de trabajo DEBE consultar y utilizar este enlace y Document ID para sincronizar cambios remotos sin necesidad de volver a solicitar el link al usuario.

---

## Apuntes de Clases

### Miércoles 19/08
* SDD (Spec-Driven Development)
* Probabilidad matemática LLM: simulan entendimiento humano a partir de inmensos corpus de texto. Predicción constante, predice el siguiente fragmento de palabra (token) basado en pura estadística. Generan lenguaje impecable y coherente pero carecen de conocimiento estricto o comprobación empírica del mundo real.
* LM Studio => locales
* No son determinísticos

### Miércoles 09/09
* Requerimientos No Funcionales: no son reglas de negocios, vinculadas al testeo en general ("la sesión debe abrirse en menos de dos segundos y estar cifrada").
* Arquitectura emergente: MVP y construir a partir de ahí.
* Arquitectura Enterprise:
  * Tier => interfaz de usuario
  * Tier backend:
    * Layer => web-service
    * Layer => srv de aplicación
    * Layer => modelo de dominio
    * Layer => acceso a datos
* Patrón de fachada: oculta el modelo tras una interfaz para acceso.

---

## Calendario de Entregas y Stack

* **Entrega 01:** 29/09
* **Entrega 02:** 03/11
* **Entrega 03:** 09/12

### Stack Tecnológico
1. Java + Spring Boot
2. PostgreSQL
3. JPA + Hibernate
4. Spring Security + JWT
5. API Key
6. Swagger / OpenAPI 3
7. JUnit + Mockito
8. Football-Data.org
9. WhoScored
10. Redis
11. Spring Scheduler
12. GitHub Actions
13. SonarCloud
14. React + Vite

---

## Resumen de Requerimientos por Entrega

### ENTREGA 1: 29/09
#### CORE
* Creación de repositorios GitHub
* Configuración en GitHub Actions
* Build corriendo y SUCCESS
* SonarCloud (Registrar el proyecto y con issues menor a 10)
* JWT implementado
* Configuración de Swagger en el back-API (v3)
* Chequear issues en Sonar

#### MODELO
* Modelo mínimo y estructura de datos
* Testing automático unitario según las pautas de la materia

#### FUNCIONALIDAD
* Creación de usuario y API Key para acceder al resto de los endpoints
* Endpoint de catálogo de jugadores

#### FRONTEND
* Mostrar catálogo de jugadores
  * Test con Vitest
  * Ajustar nombres
  * Ajustar services / routes cuando esté todo ok

---

### ENTREGA 2: 03/11

#### ISSUES DEL TP1:
* [x] Revisar issues pendientes de Sonar (Resuelto / Hecho)
* [x] Borrar la carpeta `tp-desarrollo` (Resuelto / Hecho)
* [ ] Sacar lo de `/v1`
* [ ] Reveer la estructura de sync de WhoScored y la DB. Fijarse si se puede simplificar un poco la obtención del ID y que se guarde la data junto con lo de Football-Data

#### CORE (De momento no son necesarios, quedaron viejos o ya se hicieron):
* Estado de build en "Verde"
* Utilizar HSQLDB para persistir datos (opción H2)
* Crear datos de prueba cuando levanta la aplicación
* Documentación de Endpoints (APIs) con Swagger (v3)
* Separar profiles de testing para unitarios y e2e
* Implementar JOB de Coverage

#### FUNCIONALIDAD:
**BACK:**
* Cálculo de cotización de jugadores según estrategia
* Compra/venta de tokens
* Cotización de jugador a una fecha dada
* Historial de operaciones
* Ranking de jugadores según estrategia activa
* Feature ADICIONAL para el TP (planificar, implementar y presentar)

**FRONT:**
* Visualización de datos de usuario con gráficos en el front
* Botón de compra/venta de tokens
* Mostrar la cotización de un jugador
* Opción de ver historial de operaciones

#### Pestaña SDD — Especificación Funcional de Entrega 2 (Spec Kit)
* **Archivo de especificación formal:** [`specs/004-entrega2-core/spec.md`](../specs/004-entrega2-core/spec.md)
* **Checklist de calidad de requisitos:** [`specs/004-entrega2-core/checklists/requirements.md`](../specs/004-entrega2-core/checklists/requirements.md)

##### Resumen de Requisitos y Contrato SDD:
1. **Cálculo y Recálculo de Cotizaciones (P1):**
   - Mínimo 2 estrategias configurables (pesos por goles, asistencias, minutos, rating, etc.).
   - Versionado histórico en `PlayerQuote` (fecha, valor, estrategia, versión).
   - Recálculo bajo demanda: `POST /api/quotes/recalculate`.
2. **Mercado de Tokens (P1):**
   - Máximo estricto de 100 tokens emitidos por jugador.
   - Valor base inicial de 1 crédito en posesión del superusuario/pool.
   - Compra transaccional: `POST /api/orders/buy` (valida fondos y tokens disponibles, actualiza precio promedio).
   - Venta transaccional: `POST /api/orders/sell` (valida tenencia del usuario, liquida a cotización vigente).
3. **Cotización Histórica y por Fecha (P1):**
   - Consulta a fecha determinada: `GET /api/players/{id}/quotes?date=YYYY-MM-DD`.
   - Serie temporal para gráficos: `GET /api/players/{id}/quotes`.
4. **Portfolio y Rendimiento Financiero (P2):**
   - `GET /api/users/{id}/portfolio`: tokens poseídos, costo invertido, valor actual y PnL (+/-).
5. **Historial de Operaciones (P2):**
   - `GET /api/users/{id}/transactions`: registro inmutable y auditable de compras/ventas.
6. **Ranking de Jugadores (P2):**
   - `GET /api/players/ranking` ordenado por la cotización bajo la estrategia activa.
7. **Scheduler Semanal (P2):**
   - Proceso periódico `@Scheduled` para actualización autónoma de cotizaciones.
8. **Integración Unificada y Fallback (P3):**
   - Consolidación de Football-Data.org y WhoScored en un solo modelo.
   - Resiliencia ante fallos (`SCRAPE_TIMEOUT`, `SCRAPE_BLOCKED`) usando datos locales y caché.
9. **Feature Adicional Propuesto (P3):**
   - *Sistema de Alertas de Precios (Price Alerts)*: configuración de umbrales con disparo automático de notificaciones (`POST /api/alerts`, `GET /api/alerts`).
10. **Frontend en React (P2):**
    - Gráfico interactivo de evolución de cotización, botones de compra/venta y vista de portfolio.
11. **Estandarización de Endpoints (TP1 Issue):**
    - Rutas sin `/v1`: `/api/players`, `/api/quotes`, `/api/orders`, `/api/users`, `/api/alerts`.

---

### ENTREGA 3: 09/12
#### CORE
* Crear un test de arquitectura
* Auditoría de Web-Services: Loguear `<timestamp, user, operación/metodo, parámetros, tiempoDeEjecucion>` de los servicios publicados con Spring utilizando Log4j/logback
* TAG en GitHub y confeccionar Release Notes de entrega 3
* Configurar métricas con Prometheus
* Configurar endpoints de monitoreo con Spring Boot Actuator

#### FUNCIONALIDAD
* Optimizar Ranking de jugadores según estrategia activa para alta frecuencia de consultas
* Endpoint de métricas avanzadas

---

## CONSTITUCIÓN DEL PROYECTO & CONTRATO DE DESARROLLO (Spec Kit)

### 1. Misión y Visión General
**Proyecto:** Plataforma de Mercado y Valoración de Jugadores de Fútbol (Tokens de Jugadores)

**Misión:**  
Desarrollar una plataforma full-stack que permita integrar información de jugadores de fútbol pertenecientes a cinco de las principales ligas (Premier League - Inglaterra, Bundesliga - Alemania, La Liga - España, Serie A - Italia, Ligue 1 - Francia), calcular periódicamente su valoración de mercado mediante estrategias configurables y ofrecer un mercado de tokens asociado a cada jugador.

La plataforma permitirá a los usuarios consultar jugadores, cotizaciones actuales e históricas, comprar y vender tokens, administrar su portfolio y consultar el historial de operaciones realizadas.

El sistema deberá mantener separación de responsabilidades, seguridad, trazabilidad de operaciones financieras, tolerancia a fallos de proveedores externos y capacidad de evolución.

**Reglas de negocio principales:**
- Cada jugador tendrá una cotización que puede variar a lo largo del tiempo.
- La cotización será calculada mediante una estrategia de valoración configurable.
- El sistema deberá soportar al menos dos estrategias de valoración.
- Las estrategias deberán permitir configurar pesos para las distintas métricas utilizadas.
- Cada jugador dispondrá de **100 tokens** máximos emitidos.
- El valor inicial de cada token será de **1 crédito**.
- Inicialmente, todos los tokens estarán en posesión del superusuario.
- Los usuarios podrán comprar y vender tokens de jugadores.
- Una compra deberá validar disponibilidad de tokens y utilizar la cotización vigente.
- Una venta deberá validar que el usuario posea la cantidad de tokens indicada.
- Toda operación financiera deberá quedar registrada y ser auditable.
- Las cotizaciones deberán conservar su historial.
- El sistema deberá poder consultar la cotización de un jugador para una fecha determinada.
- El ranking deberá utilizar la estrategia de valoración activa.

---

### 2. Stack Tecnológico Estándar

**Backend:**
- Java 17+
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL como base de datos principal
- HSQLDB o H2 para los perfiles de pruebas definidos en la Entrega 2
- Spring Security
- JWT
- API Keys
- Bean Validation
- Spring Boot Actuator
- Prometheus
- Logback / Log4j
- Swagger / OpenAPI v3 mediante `springdoc-openapi`

**Frontend:**
- React
- TypeScript
- React Router
- Axios
- Tailwind CSS

**DevOps y calidad:**
- Git
- GitHub
- GitHub Actions
- SonarCloud
- JUnit 5
- Mockito
- Tests de integración y E2E según corresponda

**Metodología:**  
El desarrollo seguirá un enfoque **Spec-Driven Development (SDD)** utilizando Spec Kit.  
Las especificaciones deberán definir contratos REST, modelos de dominio, reglas de negocio, validaciones y escenarios de aceptación antes de implementar la funcionalidad correspondiente.

---

### 3. Principios Arquitectónicos

#### 3.1 Arquitectura en capas
El backend seguirá una arquitectura por capas:  
**Controllers → Services → Repositories → Adapters**

* **Controllers:**
  - Exponer los endpoints REST.
  - Validar los datos de entrada.
  - Recibir y devolver DTOs.
  - No contener lógica de negocio.
* **Services:**
  - Implementar las reglas de negocio.
  - Coordinar repositorios y adapters.
  - Gestionar transacciones.
  - Resolver cálculos de valoración.
  - Gestionar compras, ventas y portfolios.
* **Repositories:**
  - Persistencia de datos.
  - Acceso a PostgreSQL/HSQLDB/H2 mediante JPA/Hibernate.
  - No deberán contener reglas de negocio.
* **Adapters:**
  - Integración con servicios externos.
  - Scraping de fuentes externas.
  - Football-Data.org u otros proveedores definidos por la especificación.
  - Adaptar datos externos al modelo interno.

#### 3.2 Independencia de la base de datos
La lógica de negocio deberá ser independiente del motor de base de datos utilizado.  
PostgreSQL será la base de datos principal del sistema.  
HSQLDB o H2 podrán utilizarse mediante perfiles específicos para pruebas, sin modificar la lógica de negocio ni los contratos REST.

#### 3.3 DTOs y entidades
Las entidades JPA no deberán exponerse directamente desde los Controllers.  
La comunicación REST deberá utilizar DTOs con únicamente la información necesaria para cada operación.

---

### 4. Seguridad

#### 4.1 JWT
El sistema deberá implementar autenticación mediante JWT.  
Los endpoints protegidos deberán validar el token mediante Spring Security.  
El JWT deberá estar firmado utilizando un algoritmo seguro (HS256 o RS256).

#### 4.2 API Key
El sistema deberá implementar API Keys para controlar el acceso a los endpoints REST.  
La responsabilidad y el mecanismo exacto de validación de JWT y API Key deberán quedar definidos explícitamente en la especificación de seguridad de la Entrega 1.  
No se deberá asumir que ambos mecanismos cumplen exactamente la misma función.  
Las API Keys no deberán almacenarse ni mostrarse en texto plano cuando la implementación permita su protección mediante hash.

#### 4.3 Datos sensibles
Nunca deberán almacenarse en el repositorio:
- Contraseñas.
- Secretos JWT.
- API Keys reales.
- Credenciales de bases de datos.
- Tokens.
- Variables sensibles de producción.  
Las credenciales deberán gestionarse mediante variables de entorno o mecanismos equivalentes.

---

### 5. Manejo de Errores
- Todos los errores funcionales y de validación visibles para el usuario deberán presentarse en **español**.
- Estructura consistente procesable por el frontend: HTTP status, código de error estable, mensaje en español, timestamp, path, correlation ID.
- El frontend deberá identificar errores utilizando el código de error y no el texto.
- No exponer stack traces, excepciones internas, nombres de tablas/columnas, credenciales ni información técnica sensible.
- Uso de `@RestControllerAdvice` y compatibilidad con `ProblemDetail` (RFC 7807).

---

### 6. Entrega 1 — Base del Proyecto
* **DevOps y calidad:** Workflow `.github/workflows/ci.yml`, build final SUCCESS, SonarCloud con **Issues < 10** (bugs, vulnerabilidades y code smells sumados <= 9).
* **Seguridad y documentación:** Registro, login, JWT, API Keys, Spring Security, Swagger UI (`/swagger-ui/index.html`, `/v3/api-docs`).
* **Endpoints iniciales:**
  ```text
  POST /api/v1/auth/register
  POST /api/v1/auth/login
  POST /api/v1/auth/api-keys
  GET  /api/v1/players
  GET  /api/v1/players/{id}
  ```
* **Modelo inicial:** `User`, `ApiKey`, `Player`, `Quote` / `PlayerQuote`.
* **Testing:** JUnit 5, Mockito (casos de éxito, validaciones, errores y reglas de negocio).

---

### 7. Entrega 2 — Funcionalidad Core
* **Persistencia para testing:** HSQLDB o H2 con perfiles independientes `unit` y `e2e`. PostgreSQL en producción.
* **Datos iniciales:** Población automática al iniciar en los perfiles correspondientes.
* **Integración de jugadores:** 5 ligas principales. Fuentes previstas: WhoScored (scraping) y Football-Data.org (API oficial). Uso de adapters, tolerancia a fallos y respaldo con datos locales.
* **Estrategias de valoración:** Al menos 2 estrategias configurables (minutos, goles, asistencias, tiros, pases, intercepciones, tarjetas, posición, rating) con pesos configurables y trazabilidad histórica de versión de estrategia.
* **Cotizaciones:** Recálculo periódico (`POST /api/v1/quotes/recalculate`), cotización actual y a fecha dada.
* **Scheduler:** Actualización periódica semanal (configurable).
* **Mercado de tokens:** `POST /api/v1/orders/buy` y `POST /api/v1/orders/sell` transaccionales. Validación de saldo, disponibilidad de tokens (máx. 100 emitidos por jugador, valor base 1 crédito).
* **Historial de operaciones:** `GET /api/v1/users/{id}/transactions`.
* **Portfolio:** `GET /api/v1/users/{id}/portfolio` (tokens, precio promedio de compra, valor actual, ganancia/pérdida).
* **Ranking:** `GET /api/v1/players/ranking` con estrategia activa.
* **Coverage Job:** Verificación de cobertura en GitHub Actions.

---

### 8. Entrega 3 — Observabilidad, Auditoría y Optimización
* **Architecture Test:** Validación de límites entre capas (ArchUnit / tests de arquitectura).
* **Auditoría de Web Services:** Loguear `<timestamp, user, operación/método, parámetros, tiempoDeEjecución>` con Logback/Log4j sin datos sensibles.
* **Correlation ID:** Identificador único de peticiones.
* **GitHub Tag y Release Notes:** Tag y Release en GitHub con notas descriptivas.
* **Prometheus y Spring Boot Actuator:** `/actuator/health`, `/actuator/metrics`, `/prometheus`.
* **Ranking optimizado:** Soportar alta frecuencia de consultas (índices, caché, consultas optimizadas).
* **Advanced Metrics Endpoint:** Endpoint con métricas de comportamiento avanzadas.

---

### 9. Cache y Tolerancia a Fallos
* Mecanismo de caché obligatorio (Redis o memoria) para ranking, datos de jugadores y APIs externas.
* Fallback a caché/datos locales ante caídas de proveedores externos.
* Transformación de errores externos a respuestas amigables en español.

---

### 10. Testing y Ambientes
* Diferenciación de tests: Unitarios, Integración, E2E.
* Perfiles separados de producción. Sin dependencia de servicios externos reales en CI.

---

### 11. API REST
* Prefijo común: `/api/v1` (o según se ajuste en la fase de especificación sin `/v1` conforme a los issues planteados).
* Contratos formalizados con Request/Response DTOs, validaciones y códigos de error estándar.

---

### 12. Frontend
* Desarrollado en React, consumiendo contratos REST.
* Vistas: Autenticación, catálogo, filtros (liga, equipo, posición), detalle, evolución histórica, ranking, compra/venta de tokens, portfolio, historial de transacciones.
* Manejo de errores en español y diseño responsive.

---

### 13. Spec-Driven Development (SDD)
Flujo:  
`Requerimiento → Specification → Contrato API / Modelo de dominio → Escenarios de aceptación → Plan → Tasks → Implementación → Tests → Validación`  
Organizado mediante Spec Kit en `/specs`.

---

### 14. Gobernanza Git
* Ramas: `main`, `develop`, `feature/<nombre>`.
* Bloqueo de merges si falla build, tests, cobertura, SonarCloud o arquitectura.

---

### 15. Definition of Done (DoD)
* Spec actualizada y contrato API definido.
* Implementación en capas sin violaciones de arquitectura.
* Tests unitarios e integración pasando.
* Mensajes al usuario en español sin exponer datos sensibles.
* Swagger y contratos sincronizados.
* SonarCloud cumplido (< 10 issues) y CI en verde.

---

### 16. Roadmap de Entregas
* **Entrega 1:** Foundation (Auth, JWT, API Keys, Swagger, Jugadores base, CI, Sonar).
* **Entrega 2:** Core Functionality (Estrategias, Tokens, Cotizaciones, Órdenes, Portfolio, Ranking, Scheduler, Cache, WhoScored/Football-Data).
* **Entrega 3:** Observability & Optimization (ArchUnit, Auditoría, Correlation ID, Tag/Release, Prometheus, Actuator, Ranking optimizado).

---

### 17. Compromiso del Equipo
Versión de constitución: **1.0.0**  
Contrato técnico y funcional vinculante para todo el equipo.

---

## Decisiones de Diseño + Proceso SDD — Scraper de Jugadores (WhoScored)

* **Componente:** Microservicio de scraping de jugadores (WhoScored), consumido por el backend a través de un Adapter.
* **Fecha:** 21/09/2026

### Parte 1 — Decisiones tomadas

1. **Contrato / Interfaz:**
   - Endpoint REST: `GET /players/{whoscoredId}/stats`.
   - Devuelve JSON con las métricas necesarias: `goals`, `assists`, `shots`, `keyPasses`, `dribbles`, `tackles`, `rating`, `minutosJugados`, `tarjetasAmarillas`, `tarjetasRojas`.
   - Errores estructurados: `404` (jugador no encontrado), `502`/`504` con códigos propios `"SCRAPE_TIMEOUT"` o `"SCRAPE_BLOCKED"`.
2. **Autenticación / Autorización:**
   - Solo el backend consume el scraper (servicio a servicio).
   - Autenticación por API Key fija (`X-API-Key`). Sin JWT.
3. **Stack técnico:**
   - Node.js + Playwright/Puppeteer (más adecuado para HTML dinámico que Java).
4. **Resiliencia y fallback:**
   - Reintentos con backoff exponencial (ej. 3 intentos).
   - Timeout máximo configurable.
   - Códigos de error específicos (`"SCRAPE_TIMEOUT"`, `"SCRAPE_BLOCKED"`) para que el backend active el fallback local/caché.
5. **Concurrencia:**
   - Solo lectura hacia WhoScored.
   - Límite de paralelismo / cola defensiva para evitar rate limiting y bloqueos.
6. **Modelo de dominio / fórmulas:**
   - El scraper solo extrae y devuelve métricas crudas.
   - El cálculo de cotización y valoración es responsabilidad exclusiva del backend.
7. **Testing:**
   - Fixtures y snapshots de HTML locales en el repositorio. Nunca pegar al sitio en vivo durante CI.
8. **Deploy / infraestructura local:**
   - Integrado en `docker-compose.yml`. Variables: `API_KEY`, timeouts y reintentos.

### Parte 2 — Pasos SDD a seguir
- [ ] 1. **Spec:** Redactar especificación funcional incorporando estas decisiones como restricciones explícitas.
- [ ] 2. **Contrato:** Formalizar OpenAPI/JSON Schema.
- [ ] 3. **Criterios de aceptación:** Casos de éxito y casos de falla (timeout/bloqueo).
- [ ] 4. **Plan:** Plan de implementación completo antes de codificar.
- [ ] 5. **Tasks:** Desglose en tareas pequeñas y ordenadas.
- [ ] 6. **Implementación:** Tarea por tarea validando contra DoD.
- [ ] 7. **Tests:** Cubrir camino feliz y fallas.
- [ ] 8. **Validación final:** Checklist DoD completo (Sonar, coverage, OpenAPI, logs).
