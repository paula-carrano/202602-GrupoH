# Análisis unificado de SonarCloud

El destino único es el proyecto público `paula-carrano_202602-GrupoH`, en la
organización `202602-grupoh`. `.github/workflows/ci.yml` compila y ejecuta los
tests de los tres módulos antes de lanzar SonarScanner desde la raíz. El job
espera el Quality Gate y falla si el gate falla o no responde dentro de 300 segundos.

## Activación administrativa

1. Abrir https://sonarcloud.io/dashboard?id=paula-carrano_202602-GrupoH y comprobar
   en la información del proyecto su clave, organización y vinculación con
   `paula-carrano/202602-GrupoH` en GitHub. Mantener su visibilidad pública y su gate.
2. En el proyecto, ir a **Administration > Analysis Method** y desactivar
   **Automatic Analysis**, si está habilitado, para usar exclusivamente CI.
3. Verificar que la cuenta propietaria del token tenga **Execute Analysis** en
   ese proyecto. En GitHub, **Settings > Secrets and variables > Actions**, establecer
   `SONAR_TOKEN` con un token de esa cuenta. No guardar el token en archivos ni logs.
4. Subir los cambios de `feature/adapter` y ejecutar el workflow del PR. Confirmar
   en los logs que se importaron JaCoCo y los dos LCOV sin archivos sin resolver,
   y que el proyecto público contiene código de `back`, `front` y `scraping`.
   Comprobar que el análisis corresponde al SHA del último commit.
5. Después de recibir el primer análisis completo, verificar en el proyecto privado
   `202602-GrupoH_tp-desarrollo` que no haya otro análisis automático o pipeline
   activo. Desactivar cualquier integración restante que publique checks de ese
   proyecto en este repo. Conservar el proyecto y su historial; no borrarlo.
6. En GitHub, **Settings > Rules > Rulesets** (o **Branches > Branch protection rules**),
   revisar las reglas de la rama destino: requerir `Build, Test & SonarCloud`, que
   espera el gate público. Retirar requisitos obsoletos asociados al análisis privado.
   Si se requiere además el check de SonarCloud, seleccionar el emitido por el
   proyecto público y verificar su enlace. Revisar que no aparezcan análisis
   duplicados en el siguiente commit.

Estos ajustes remotos no se aplican al editar el repositorio. Un gate fallido por
cobertura o issues reales requiere corregir el código o los tests; no desactivar
condiciones para completar la migración.

## Validación local

Desde `back`, ejecutar (en Windows, usar `mvnw.cmd`):

```sh
./mvnw -B clean verify org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=test -DoutputDirectory=target/sonar-libraries
```

Desde `front` y desde `scraping`, ejecutar `npm ci` y `npm run test:coverage`.
En `front`, ejecutar también `npm run build`. Para instalar scraping sin descargar
Chromium, establecer `PUPPETEER_SKIP_DOWNLOAD=true`; los tests existentes no
necesitan descargar el navegador.

Los reportes esperados son `back/target/site/jacoco/jacoco.xml`,
`front/coverage/lcov.info` y `scraping/coverage/lcov.info`. Las entradas `SF:` de
ambos LCOV son relativas a la raíz del repo (`front/src/...`, `scraping/src/...`).
La cobertura incluye código fuente sin tests y no excluye DTOs, modelos ni configuración
productiva. Los tests y sus archivos auxiliares no cuentan como código productivo.

Sonar usa los binarios Java de `target/classes` y `target/test-classes`, más los
JAR de ámbito test copiados a `target/sonar-libraries` (incluyen dependencias de
compilación y ejecución). No ejecutar un segundo scanner Maven.
