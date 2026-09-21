CONSTITUCIÓN DEL PROYECTO & CONTRATO DE DESARROLLO (Spec Kit)

&nbsp;

**1\. Misión y Visión General**

&nbsp;

***Proyecto***

***Plataforma de Mercado y Valoración de Jugadores de Fútbol (Tokens de Jugadores)***

&nbsp;

Misión

&nbsp;

Desarrollar una plataforma full-stack que permita integrar información de jugadores de fútbol pertenecientes a cinco de las principales ligas(Premier League \- Inglaterra, Bundesliga- Alemania, La Liga- España, Serie A \- Italia, Ligue 1 \- Francia), calcular periódicamente su valoración de mercado mediante estrategias configurables y ofrecer un mercado de tokens asociado a cada jugador.

&nbsp;

La plataforma permitirá a los usuarios consultar jugadores, cotizaciones actuales e históricas, comprar y vender tokens, administrar su portfolio y consultar el historial de operaciones realizadas.

&nbsp;

El sistema deberá mantener separación de responsabilidades, seguridad, trazabilidad de operaciones financieras, tolerancia a fallos de proveedores externos y capacidad de evolución.

&nbsp;

Reglas de negocio principales

&nbsp;

\- Cada jugador tendrá una cotización que puede variar a lo largo del tiempo.

\- La cotización será calculada mediante una estrategia de valoración configurable.

\- El sistema deberá soportar al menos dos estrategias de valoración.

\- Las estrategias deberán permitir configurar pesos para las distintas métricas utilizadas.

\- Cada jugador dispondrá de \*\*100 tokens\*\* máximos emitidos.

\- El valor inicial de cada token será de \*\*1 crédito\*\*.

\- Inicialmente, todos los tokens estarán en posesión del superusuario.

\- Los usuarios podrán comprar y vender tokens de jugadores.

\- Una compra deberá validar disponibilidad de tokens y utilizar la cotización vigente.

\- Una venta deberá validar que el usuario posea la cantidad de tokens indicada.

\- Toda operación financiera deberá quedar registrada y ser auditable.

\- Las cotizaciones deberán conservar su historial.

\- El sistema deberá poder consultar la cotización de un jugador para una fecha determinada.

\- El ranking deberá utilizar la estrategia de valoración activa.

&nbsp;

\---

&nbsp;

**2\. Stack Tecnológico Estándar**

&nbsp;

Backend

&nbsp;

\- Java 17+

\- Spring Boot 3.x

\- Spring Web

\- Spring Data JPA

\- Hibernate

\- PostgreSQL como base de datos principal

\- HSQLDB o H2 para los perfiles de pruebas definidos en la Entrega 2

\- Spring Security

\- JWT

\- API Keys

\- Bean Validation

\- Spring Boot Actuator

\- Prometheus

\- Logback / Log4j

\- Swagger / OpenAPI v3 mediante \`springdoc-openapi\`

&nbsp;

Frontend

&nbsp;

\- React

\- TypeScript

\- React Router

\- Axios

\- Tailwind CSS

&nbsp;

&nbsp;DevOps y calidad

&nbsp;

\- Git

\- GitHub

\- GitHub Actions

\- SonarCloud

\- JUnit 5

\- Mockito

\- Tests de integración y E2E según corresponda

&nbsp;

Metodología

&nbsp;

El desarrollo seguirá un enfoque \*\*Spec-Driven Development (SDD)\*\* utilizando Spec Kit.

&nbsp;

Las especificaciones deberán definir contratos REST, modelos de dominio, reglas de negocio, validaciones y escenarios de aceptación antes de implementar la funcionalidad correspondiente.

&nbsp;

\---

&nbsp;

**3\. Principios Arquitectónicos**

&nbsp;

**3.1 Arquitectura en capas**

&nbsp;

El backend seguirá una arquitectura por capas:

&nbsp;

\*\*Controllers → Services → Repositories → Adapters\*\*

&nbsp;

Controllers

&nbsp;

Responsables de:

&nbsp;

\- Exponer los endpoints REST.

\- Validar los datos de entrada.

\- Recibir y devolver DTOs.

\- No contener lógica de negocio.

&nbsp;

Services

&nbsp;

Responsables de:

&nbsp;

\- Implementar las reglas de negocio.

\- Coordinar repositorios y adapters.

\- Gestionar transacciones.

\- Resolver cálculos de valoración.

\- Gestionar compras, ventas y portfolios.

&nbsp;

Repositories

&nbsp;

Responsables de:

&nbsp;

\- Persistencia de datos.

\- Acceso a PostgreSQL/HSQLDB/H2 mediante JPA/Hibernate.

\- No deberán contener reglas de negocio.

&nbsp;

Adapters

&nbsp;

Responsables de:

&nbsp;

\- Integración con servicios externos.

\- Scraping de fuentes externas.

\- Football-Data.org u otros proveedores definidos por la especificación.

\- Adaptar datos externos al modelo interno.

&nbsp;

\---

&nbsp;

**3.2 Independencia de la base de datos**

&nbsp;

La lógica de negocio deberá ser independiente del motor de base de datos utilizado.

&nbsp;

PostgreSQL será la base de datos principal del sistema.

&nbsp;

HSQLDB o H2 podrán utilizarse mediante perfiles específicos para pruebas, sin modificar la lógica de negocio ni los contratos REST.

&nbsp;

\---

&nbsp;

**3.3 DTOs y entidades**

&nbsp;

Las entidades JPA no deberán exponerse directamente desde los Controllers.

&nbsp;

La comunicación REST deberá utilizar DTOs.

&nbsp;

Los DTOs deberán representar únicamente la información necesaria para cada operación.

&nbsp;

\---

&nbsp;

&nbsp;**4\. Seguridad**

&nbsp;

**4.1 JWT**

&nbsp;

El sistema deberá implementar autenticación mediante JWT.

&nbsp;

Los endpoints protegidos deberán validar el token mediante Spring Security.

&nbsp;

El JWT deberá estar firmado utilizando un algoritmo seguro, como HS256 o RS256, según la implementación definida en la especificación.

&nbsp;

\---

&nbsp;

**4.2 API Key**

&nbsp;

El sistema deberá implementar API Keys para controlar el acceso a los endpoints REST.

&nbsp;

La responsabilidad y el mecanismo exacto de validación de JWT y API Key deberán quedar definidos explícitamente en la especificación de seguridad de la Entrega 1\.

&nbsp;

No se deberá asumir que ambos mecanismos cumplen exactamente la misma función.

&nbsp;

Las API Keys no deberán almacenarse ni mostrarse en texto plano cuando la implementación permita su protección mediante hash.

&nbsp;

\---

&nbsp;

**4.3 Datos sensibles**

&nbsp;

Nunca deberán almacenarse en el repositorio:

&nbsp;

\- Contraseñas.

\- Secretos JWT.

\- API Keys reales.

\- Credenciales de bases de datos.

\- Tokens.

\- Variables sensibles de producción.

&nbsp;

Las credenciales deberán gestionarse mediante variables de entorno o mecanismos equivalentes.

&nbsp;

\---

&nbsp;

**5\. Manejo de Errores**

&nbsp;

Todos los errores funcionales y de validación visibles para el usuario deberán presentarse en \*\*español\*\*.

&nbsp;

El backend deberá utilizar una estructura de error consistente y fácilmente procesable por el frontend.

&nbsp;

Cada error deberá incluir, cuando corresponda:

&nbsp;

\- HTTP status.

\- Código de error estable.

\- Mensaje en español.

\- Timestamp.

\- Path.

\- Correlation ID.

&nbsp;

El frontend deberá identificar los errores utilizando el \*\*código de error\*\* y no mediante comparación directa del texto del mensaje.

&nbsp;

Los mensajes deberán ser comprensibles para el usuario final.

&nbsp;

No deberán exponerse:

&nbsp;

\- Stack traces.

\- Excepciones internas.

\- Nombres de tablas o columnas.

\- Restricciones de base de datos.

\- Credenciales.

\- Secretos.

\- Información técnica innecesaria.

&nbsp;

Se utilizará \`@RestControllerAdvice\` para centralizar el tratamiento de excepciones.

&nbsp;

Se deberán configurar también mensajes de validación de Bean Validation en español.

&nbsp;

Cuando corresponda, se utilizará \`ProblemDetail\` como estructura estándar compatible con RFC 7807\.

&nbsp;

\---

&nbsp;

&nbsp;**6\. Entrega 1 — Base del Proyecto**

&nbsp;

**6.1 DevOps y calidad**

&nbsp;

El proyecto deberá contar con:

&nbsp;

\- Workflow \`.github/workflows/ci.yml\`.

\- Ejecución automática ante \`push\` y \`pull request\` sobre las ramas principales definidas.

\- Compilación automática del backend.

\- Ejecución automática de tests.

\- Build final en estado \*\*SUCCESS\*\*.

\- Proyecto registrado en SonarCloud.

\- Análisis automático mediante GitHub Actions.

&nbsp;

SonarCloud

&nbsp;

El proyecto deberá mantener:

&nbsp;

\*\*Issues \< 10\*\*

&nbsp;

Es decir, como máximo 9 issues entre:

&nbsp;

\- Bugs.

\- Vulnerabilities.

\- Code Smells.

&nbsp;

No deberá realizarse merge de código que incumpla las condiciones de calidad definidas por el proyecto.

&nbsp;

\---

&nbsp;

**6.2 Seguridad y documentación**

&nbsp;

Implementar:

&nbsp;

\- Registro de usuarios.

\- Login.

\- Autenticación mediante JWT.

\- API Keys.

\- Configuración de Spring Security.

\- Swagger/OpenAPI v3.

&nbsp;

Endpoints iniciales:

&nbsp;

\`\`\`text

POST /api/v1/auth/register

POST /api/v1/auth/login

POST /api/v1/auth/api-keys

&nbsp;

GET /api/v1/players

GET /api/v1/players/{id}

Swagger deberá permitir visualizar y probar los endpoints documentados.

Endpoints de documentación:

/swagger-ui/index.html

/v3/api-docs

## **6.3 Modelo inicial**

El modelo mínimo deberá contemplar:

* User  
* ApiKey  
* Player  
* Quote / PlayerQuote

El modelo deberá estar preparado para incorporar posteriormente:

* Estrategias de valoración.  
* Tokens.  
* Órdenes.  
* Portfolio.  
* Transacciones.  
* Auditoría.

La Entrega 1 no requiere implementar todavía toda la lógica de valoración ni el mercado completo.

---

## **6.4 Testing**

Se deberán implementar tests unitarios utilizando:

* JUnit 5\.  
* Mockito.

Los tests deberán cubrir como mínimo:

* Casos exitosos.  
* Validaciones.  
* Casos de error.  
* Reglas de negocio implementadas durante la entrega.

La cobertura deberá respetar los criterios definidos por la materia.

---

# **7\. Entrega 2 — Funcionalidad Core**

## **7.1 Persistencia para testing**

La aplicación deberá soportar un perfil específico de pruebas utilizando:

* HSQLDB o H2.

Se deberán definir perfiles independientes para:

unit

e2e

La base de datos principal de producción continuará siendo PostgreSQL.

---

## **7.2 Datos iniciales**

La aplicación deberá poder crear datos de prueba automáticamente al iniciar en los perfiles correspondientes.

Estos datos deberán permitir ejecutar y demostrar las funcionalidades de la Entrega 2\.

---

## **7.3 Integración de jugadores**

El sistema deberá integrar información de jugadores pertenecientes a cinco ligas principales.

Se deberá contemplar:

* Obtención de información desde fuentes externas.  
* Persistencia local.  
* Adaptación de datos externos.  
* Manejo de errores del proveedor.  
* Utilización de datos locales cuando la fuente externa no esté disponible.

Entre las fuentes previstas se encuentran:

* WhoScored mediante scraping.  
* Football-Data.org mediante API oficial.

La implementación concreta de cada proveedor deberá realizarse mediante adapters.

---

## **7.4 Estrategias de valoración**

El sistema deberá soportar como mínimo **dos estrategias de valoración configurables**.

Las estrategias podrán utilizar métricas tales como:

* Minutos jugados.  
* Goles.  
* Asistencias.  
* Tiros.  
* Pases.  
* Intercepciones.  
* Tarjetas.  
* Posición.  
* Rating.

Cada estrategia deberá permitir configurar pesos para las métricas utilizadas.

La estrategia activa deberá poder determinar la valoración de los jugadores.

Cada cotización generada deberá conservar la versión de la estrategia utilizada para poder realizar trazabilidad histórica.

---

## **7.5 Cotizaciones**

El sistema deberá:

* Calcular la valoración de los jugadores según la estrategia activa.  
* Generar cotizaciones periódicamente.  
* Permitir recalcular cotizaciones.  
* Mantener el historial de cotizaciones.  
* Consultar la cotización actual.  
* Consultar la cotización correspondiente a una fecha determinada.

Endpoint:

POST /api/v1/quotes/recalculate

---

## **7.6 Scheduler**

El sistema deberá contar con un mecanismo de ejecución periódica para la actualización de cotizaciones.

La periodicidad prevista será semanal, pudiendo ser configurable.

El scheduler deberá utilizar el mismo mecanismo de valoración definido por las estrategias activas.

---

## **7.7 Mercado de tokens**

Se deberá implementar:

POST /api/v1/orders/buy

POST /api/v1/orders/sell

### **Compra**

La compra deberá:

1. Validar autenticación.  
2. Validar disponibilidad de tokens.  
3. Obtener la cotización vigente.  
4. Validar saldo del usuario.  
5. Actualizar saldo.  
6. Actualizar posición del usuario.  
7. Transferir los tokens.  
8. Registrar la operación.

### **Venta**

La venta deberá:

1. Validar autenticación.  
2. Validar que el usuario posea los tokens.  
3. Obtener la cotización correspondiente.  
4. Actualizar la posición.  
5. Actualizar el saldo.  
6. Registrar la operación.

Las operaciones financieras deberán ejecutarse de manera transaccional.

---

## **7.8 Historial de operaciones**

El sistema deberá mantener un historial de operaciones.

Se deberá poder consultar:

GET /api/v1/users/{id}/transactions

El historial deberá permitir identificar como mínimo:

* Usuario.  
* Tipo de operación.  
* Jugador.  
* Cantidad de tokens.  
* Precio.  
* Fecha.  
* Resultado de la operación.

---

## **7.9 Portfolio**

Se deberá implementar:

GET /api/v1/users/{id}/portfolio

El portfolio deberá mostrar como mínimo:

* Jugador.  
* Cantidad de tokens.  
* Precio promedio de compra.  
* Valor actual.  
* Ganancia o pérdida.  
* Historial de operaciones relacionadas.

---

## **7.10 Ranking**

Se deberá implementar:

GET /api/v1/players/ranking

El ranking deberá calcularse utilizando la estrategia de valoración activa.

---

## **7.11 Coverage Job**

GitHub Actions deberá contar con un job específico para ejecutar y/o verificar la cobertura de tests de acuerdo con los criterios establecidos por la materia.

---

# **8\. Entrega 3 — Observabilidad, Auditoría y Optimización**

## **8.1 Architecture Test**

Se deberán implementar tests de arquitectura que verifiquen el cumplimiento de las reglas arquitectónicas definidas.

Como mínimo deberán validar la separación entre:

Controllers

Services

Repositories

Adapters

y evitar dependencias no permitidas entre capas.

---

## **8.2 Auditoría de Web Services**

Se deberá implementar logging de las llamadas a los Web Services.

El registro deberá contener como mínimo:

\<timestamp, user, operación/método, parámetros, tiempoDeEjecución\>

Se utilizará:

* Spring.  
* Logback o Log4j.

Los logs no deberán exponer información sensible.

---

## **8.3 Correlation ID**

Las peticiones deberán poder identificarse mediante un Correlation ID para facilitar el seguimiento de una operación a través de los diferentes componentes del sistema.

---

## **8.4 GitHub Tag y Release Notes**

Para la Entrega 3 se deberá generar:

* GitHub Tag correspondiente a la versión entregada.  
* Release en GitHub.  
* Release Notes con los cambios principales realizados.

---

## **8.5 Prometheus**

Se deberá configurar la exposición de métricas compatibles con Prometheus.

Las métricas deberán permitir observar el comportamiento general de la aplicación.

---

## **8.6 Spring Boot Actuator**

Se deberán configurar endpoints de monitorización mediante Spring Boot Actuator.

Como mínimo se deberá contemplar:

/actuator/health

/actuator/metrics

/prometheus

según la configuración final de la aplicación.

---

## **8.7 Ranking optimizado**

El endpoint de ranking deberá optimizarse para soportar una frecuencia elevada de consultas.

Se deberán considerar mecanismos como:

* Índices de base de datos.  
* Cache.  
* Consultas optimizadas.  
* Reducción de llamadas innecesarias a servicios externos.

La solución deberá mantener la consistencia de los datos.

---

## **8.8 Advanced Metrics Endpoint**

Se deberá implementar un endpoint de métricas avanzadas que permita obtener información relevante sobre el comportamiento de la aplicación.

La especificación deberá definir exactamente las métricas expuestas y su formato.

---

# **9\. Cache y Tolerancia a Fallos**

El sistema deberá implementar un mecanismo de cache obligatorio.

Podrá utilizarse:

* Redis.  
* Cache en memoria.

La cache deberá utilizarse especialmente para:

* Consultas frecuentes.  
* Ranking.  
* Datos de jugadores.  
* Datos provenientes de APIs externas.

Cuando un proveedor externo no esté disponible, el sistema deberá continuar funcionando utilizando información local/cacheada cuando sea posible.

Los errores de proveedores externos deberán transformarse en respuestas controladas y mensajes en español.

---

# **10\. Testing y Ambientes**

Se deberán diferenciar los siguientes tipos de pruebas:

### **Unitarias**

Para servicios, lógica de negocio y componentes aislados.

### **Integración**

Para verificar la interacción entre componentes y persistencia.

### **E2E**

Para validar los flujos principales de la aplicación.

Los perfiles de testing deberán estar separados de la configuración de producción.

Se deberá evitar que los tests dependan de servicios externos reales cuando esto pueda comprometer su reproducibilidad.

---

# **11\. API REST**

Todos los endpoints deberán utilizar el prefijo:

/api/v1

Los contratos REST deberán definir:

* Método HTTP.  
* URL.  
* Parámetros.  
* Request DTO.  
* Response DTO.  
* HTTP status.  
* Errores posibles.  
* Reglas de validación.  
* Requerimientos de autenticación.

Endpoints principales previstos:

POST   /api/v1/auth/register

POST   /api/v1/auth/login

POST   /api/v1/auth/api-keys

&nbsp;

GET    /api/v1/players

GET    /api/v1/players/{id}

GET    /api/v1/players/{id}/quotes

GET    /api/v1/players/ranking

&nbsp;

POST   /api/v1/quotes/recalculate

&nbsp;

POST   /api/v1/orders/buy

POST   /api/v1/orders/sell

&nbsp;

GET    /api/v1/users/{id}/portfolio

GET    /api/v1/users/{id}/transactions

Los endpoints definitivos podrán ajustarse durante la fase de Specification siempre que se mantengan los requerimientos funcionales del proyecto.

---

# **12\. Frontend**

El frontend será desarrollado utilizando React.

Deberá consumir exclusivamente los contratos REST definidos por el backend.

Deberá contemplar como mínimo:

* Registro y autenticación.  
* Catálogo de jugadores.  
* Filtros por liga, equipo y posición.  
* Detalle de jugador.  
* Evolución histórica de cotización.  
* Ranking.  
* Compra de tokens.  
* Venta de tokens.  
* Portfolio.  
* Historial de operaciones.  
* Visualización clara de valores financieros.  
* Manejo de errores en español.  
* Diseño responsive.

El frontend no deberá implementar reglas de negocio que correspondan al backend.

---

# **13\. Spec-Driven Development**

Todo desarrollo deberá seguir el flujo:

Requerimiento

&nbsp;&nbsp;&nbsp;&nbsp;↓

Specification

&nbsp;&nbsp;&nbsp;&nbsp;↓

Contrato API / Modelo de dominio

&nbsp;&nbsp;&nbsp;&nbsp;↓

Escenarios de aceptación

&nbsp;&nbsp;&nbsp;&nbsp;↓

Plan

&nbsp;&nbsp;&nbsp;&nbsp;↓

Tasks

&nbsp;&nbsp;&nbsp;&nbsp;↓

Implementación

&nbsp;&nbsp;&nbsp;&nbsp;↓

Tests

&nbsp;&nbsp;&nbsp;&nbsp;↓

Validación

Las especificaciones deberán mantenerse dentro de `/specs`.

Spec Kit será utilizado para organizar:

* Specifications.  
* Plans.  
* Tasks.  
* Contratos.  
* Reglas de negocio.  
* Criterios de aceptación.

Los escenarios de aceptación podrán expresarse mediante Gherkin cuando resulte conveniente para documentar y validar comportamientos funcionales.

No será obligatorio utilizar Gherkin para todos los endpoints o entidades si no aporta valor al caso particular.

---

# **14\. Gobernanza Git**

Se utilizará un flujo Git simplificado:

main

develop

feature/\<nombre\>

### **main**

Contendrá versiones estables.

### **develop**

Será la rama de integración.

### **feature/\<nombre\>**

Se utilizará para desarrollar funcionalidades específicas.

No deberá realizarse merge cuando:

* El build falle.  
* Los tests fallen.  
* Se incumplan los criterios de cobertura.  
* SonarCloud incumpla el límite establecido.  
* Existan errores críticos de seguridad.  
* La implementación no respete la arquitectura definida.

---

# **15\. Definition of Done**

Una funcionalidad se considerará terminada cuando:

* La Specification esté actualizada.  
* El contrato API esté definido cuando corresponda.  
* Las reglas de negocio estén documentadas.  
* La implementación respete la arquitectura.  
* Existan tests correspondientes.  
* Los tests sean exitosos.  
* Los errores estén correctamente gestionados.  
* Los mensajes visibles al usuario estén en español.  
* La documentación Swagger esté actualizada.  
* No se expongan datos sensibles.  
* SonarCloud cumpla los criterios establecidos.  
* El pipeline de GitHub Actions sea exitoso.  
* La funcionalidad haya sido validada mediante sus criterios de aceptación.

---

# **16\. Roadmap de Entregas**

## **Entrega 1 — Foundation**

* Configuración del proyecto.  
* GitHub Actions.  
* Build SUCCESS.  
* SonarCloud.  
* JWT.  
* API Keys.  
* Swagger/OpenAPI v3.  
* Modelo inicial.  
* PostgreSQL.  
* Tests unitarios.  
* Registro de usuarios.  
* Login.  
* Catálogo inicial de jugadores.  
* Endpoints REST iniciales.  
* Manejo estandarizado de errores en español.

## **Entrega 2 — Core Functionality**

* HSQLDB/H2 para testing.  
* Perfiles Unit y E2E.  
* Datos iniciales de prueba.  
* Integración de datos de jugadores.  
* Estrategias de valoración.  
* Configuración de pesos.  
* Cálculo de cotizaciones.  
* Cotizaciones históricas.  
* Consulta de cotización por fecha.  
* Scheduler semanal.  
* Compra de tokens.  
* Venta de tokens.  
* Historial de operaciones.  
* Portfolio.  
* Ranking.  
* Coverage Job.  
* Cache.  
* Tolerancia a fallos de proveedores externos.

## **Entrega 3 — Observability & Optimization**

* Architecture Tests.  
* Auditoría de Web Services.  
* Logs estructurados.  
* Correlation ID.  
* GitHub Tag.  
* GitHub Release.  
* Release Notes.  
* Prometheus.  
* Spring Boot Actuator.  
* Optimización del ranking.  
* Cache para consultas frecuentes.  
* Advanced Metrics Endpoint.  
* Mejoras de observabilidad y rendimiento.

---

# **17\. Compromiso del Equipo**

Todo integrante del equipo deberá respetar esta Constitución durante el desarrollo.

Cualquier modificación de las reglas establecidas deberá ser acordada por el equipo y documentada.

La Constitución funciona como contrato técnico y funcional del proyecto y será la referencia principal para evaluar si una implementación respeta la arquitectura, los requisitos y los criterios de calidad establecidos.

**Versión: 1.0.0**

&nbsp;