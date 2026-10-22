# Laboratorio aislado Recharts — etapa 5

Evaluación autorizada, **no integración productiva**. Recharts 3.3.0 y react-is 19.3.0 se instalan únicamente en este paquete privado. `front/package.json`, su lockfile, sus gráficos y el backend no se modifican. La copia de trabajo no contiene `.env` ni credenciales. La ruta local `/__lab` muestra datos sintéticos rotulados; no llama a APIs ni usa sesión.

## Reproducir

Requisitos utilizados: Node 22.19.0, npm 10.8.3, Chrome 155.0.8059.85 y Playwright Python ya instalado en el equipo. No se instala un runner adicional. Desde la raíz del repositorio:

```powershell
node experiments/recharts-stage5/prepare.mjs
npm ci --prefix ./experiments/recharts-stage5 --ignore-scripts --no-audit --no-fund --offline=false
npm run measure --prefix ./experiments/recharts-stage5
npm run preview --prefix ./experiments/recharts-stage5
```

Con preview activo, en otra terminal:

```powershell
python experiments/recharts-stage5/browser.py
```

Abrir `http://127.0.0.1:5175/__lab`. Los modos app/baseline/recharts son solo del laboratorio. `prepare.mjs` copia el estado actual de `front/src`, index y public a `.work`, registra hashes iniciales y genera entradas separadas; no elimina el origen. No copiar fuentes del laboratorio a producción. Si se modifica el frontend después de esta evaluación, el reporte anterior deja de describir esa nueva base: revisar hashes y generar una evaluación nueva.

En este entorno el sandbox impidió procesos hijos (`spawn EPERM`), y npm inicialmente usó cache only-if-cached (`ENOTCACHED`). Los comandos de preparación, consulta online, instalación aislada, builds y Chrome se ejecutaron mediante escalación autorizada. Si se reproduce en un entorno con restricciones, conservar el error y usar su mecanismo de autorización; no alterar configuración de seguridad.

## Qué se mide

`measure.mjs` ejecuta secuencialmente el mismo script `npm run build -- --mode MODE`, con Vite 6.4.3, plugin React 4.7.0, mismo lockfile, mismas dependencias, misma minificación y sin sourcemaps:

1. `app`: entrada original, sin importar laboratorio. Copia de aplicación existente.
2. `baseline`: misma aplicación más panel de prueba, fixtures, estados, estilos y tablas; renderer TableOnly.
3. `recharts`: mismo panel, sustituyendo solo renderer por Charts (imports ESM nombrados).

El modo selecciona la entrada antes del procesamiento HTML de Vite y el alias del renderizador. No hay variables de negocio ni archivos `.env`. Todos los builds tienen la biblioteca instalada, pero el build sin imports no la incluye. Gzip se calcula por archivo JS/CSS con `node:zlib.gzipSync` (opciones por defecto); unidades de resultados: **bytes**, no KiB. No son tamaños del paquete npm descomprimido.

`measurement.json` contiene archivos, bytes/gzip, deltas, rutas de paquetes agregadas/cambiadas y comprobación de hashes preexistentes. `build-*.log`, `.work`, `dist-*` y node_modules se ignoran; lockfile y resultados se conservan. La medición falla si cambia un archivo preexistente o si el delta del renderer no es positivo (guardia contra seleccionar accidentalmente la misma entrada).

| Build | JS minificado | JS gzip | CSS minificado | CSS gzip |
|---|---:|---:|---:|---:|
| Aplicación base | 362.304 | 118.337 | 236.875 | 32.734 |
| Aplicación + panel sin gráficos | 365.732 | 119.504 | 237.230 | 32.856 |
| Aplicación + panel con Recharts | 705.802 | 221.995 | 237.230 | 32.856 |
| Delta renderer/adaptador | **340.070** | **102.491** | **0** | **0** |
| Delta completo contra aplicación | **343.498** | **103.658** | **355** | **122** |

El build `app` reproduce incluso los hashes de assets del build original ejecutado desde `front/`. React y React DOM 19.3.0 son los mismos y están deduplicados según `npm ls`. El delta incluye los componentes de adaptación de Charts y dependencias realmente empaquetadas; no es un tamaño universal de Recharts. No se suman dos bundles ni se vuelve a contar React. El gzip depende de compresión conjunta, por lo que no es aditivo por módulo. La imagen de estadio queda idéntica y no integra el delta JS/CSS.

No hay configuración especial de tree shaking: imports ESM desde `recharts`, distribución es6 y `sideEffects: false` del paquete; Vite/Rollup descartan lo no usado. No se importa UMD ni se fuerza un chunk completo de dependencias. El build con Recharts avisa chunk >500 kB; no se aumentó el umbral para ocultarlo. Una integración futura puede evaluar carga diferida por ruta y medir su resultado; no se implementó en esta prueba.

## Compatibilidad y dependencias

Se consultaron `npm view recharts@3.3.0 version peerDependencies dependencies dist.integrity --json --offline=false` y `npm view react-is@19.3.0 version --json --offline=false`, además del [manifest oficial versionado](https://raw.githubusercontent.com/recharts/recharts/v3.3.0/package.json) y [README oficial v3.3.0](https://github.com/recharts/recharts/tree/v3.3.0). Candidata elegida para una comparación acotada, no afirmación de ser la versión más reciente.

Recharts 3.3.0 admite React/React DOM/react-is 19 en sus peer ranges. Se alineó react-is 19.3.0 con React 19.3.0. `npm ls react react-dom react-is recharts --all` finalizó con código 0; react-is 17.0.2 de pretty-format queda anidado para las herramientas de tests, no reemplaza al peer del gráfico. No hubo conflicto de peers. La compatibilidad se apoya además en build y ejecución real de Chrome, no solo en rangos declarados.

Lockfile respecto de la base: **40 rutas agregadas y una ruta con versión distinta**. La ruta cambiada es react-is raíz (17.0.2 → 19.3.0); la versión anterior permanece bajo pretty-format. El listado exacto está en measurement.json. No equivale a 40 paquetes nuevos incluidos en runtime: hay tipos, dependencias de tests reubicadas y versiones anidadas. Transitivas incluyen Redux Toolkit 2.13.0, react-redux 9.3.0, redux 5.0.1, redux-thunk 3.1.0, reselect 5.1.1, immer 10.2.0/11.2.7, es-toolkit 1.52.0, decimal.js-light 2.5.1, eventemitter3 5.0.4, use-sync-external-store 1.7.0, clsx 2.1.1, tiny-invariant 1.3.3, victory-vendor 37.3.6 y familia d3/internmap, además de @types y @standard-schema. Ninguna se añadió a la aplicación principal.

## Prueba de navegador y límites

`browser.py` utiliza preview del build de producción, una misma ventana redimensionada a 360/768/1280 × 900, seis escenarios y dos tablas siempre disponibles. Registra evidencia en `evidence/browser-results.json`, capturas y snapshots del árbol accesible.

- Completo: dos líneas, dona, fecha/estrategia/valores en tooltip de líneas, formato `es-AR` hasta cuatro decimales de fixture; 90.1234 → 90,1234; cero permanece cero.
- Vacío/nulos: no se dibuja una serie falsa. Incompleto: hueco sin unir puntos y dona suprimida para no normalizar un subconjunto como 100% de cartera.
- Total cero: líneas con seis marcadores; dona sin proporción inventada. Punto único: dos marcadores, no evolución inferida. Sin paths NaN.
- No hubo overflow horizontal de página, errores JavaScript ni requests `/api/` en 18 combinaciones. Tablas pueden desplazarse internamente a 360 px.
- Las flechas en la línea mostraron fechas 02/10 y 03/10, estrategia y valores correctos; el tooltip de dona apareció con puntero. Las flechas no activaron tooltip de dona ni en el SVG ni en su foco interno. Dos Tab abandonan la dona hacia las tablas; no hubo trampa de teclado. La integración futura debe decidir si añade selección accesible de sectores o presenta la dona como complemento visual de la tabla.
- El tooltip de dona de esta prueba muestra valor y unidad; falta personalizar la identificación explícita del jugador en ese tooltip antes de una integración. Leyenda y tabla sí identifican cada jugador. No se declara este prototipo diseño final.
- Se inspeccionaron visualmente capturas completo a 360/1280 e incompleto a 768. El árbol accesible incluye títulos, tablas, encabezados y valores; no acredita pronunciación, anuncios ni legibilidad efectiva con lector de pantalla. **NVDA/VoiceOver no ejecutados**.

No se midió SLA de rendering, memoria, latencia de red ni volumen real: tres puntos y dos posiciones sintéticas no acreditan SC-001. El cambio de estrategia en fixtures prueba la etiqueta del tooltip; no determina comparabilidad financiera. Los conectores, redondeo, fecha civil y unidad del laboratorio son decisiones experimentales, pendientes de contrato para producción. Fuente de accesibilidad consultada: [Recharts accessibility](https://github.com/recharts/recharts/wiki/Recharts-and-accessibility); los resultados observados tienen prioridad sobre una afirmación general de soporte.
