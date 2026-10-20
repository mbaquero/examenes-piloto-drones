# Exámenes Piloto Drones

Aplicación Android para preparar el examen teórico de piloto a distancia de UAS (categoría «abierta»,
formación A1/A3) con el temario oficial de AESA **«FOR-UAS-P01-DT01 Ed.09 — Versión 9 (julio 2024)»**.
El temario viene **incluido en la app**: el PDF de esta carpeta se empaqueta en el APK y se procesa
automáticamente la primera vez que se abre. Funciona totalmente sin conexión.

## El temario y las preguntas

El PDF de AESA es una presentación de 161 diapositivas con **teoría únicamente: no contiene preguntas,
respuestas ni formato de examen**. Por eso:

- **La teoría sale del PDF** (texto, negritas e imágenes de las diapositivas), organizada en sus tres
  módulos y en los apartados de sus índices.
- **Las preguntas son de elaboración propia** y están en `app/src/main/assets/preguntas.json`
  (288 preguntas de 3 opciones). No son preguntas oficiales del examen de AESA, y la app lo indica en
  las pantallas de práctica y examen. Cada pregunta cita **la página y la frase literal del PDF** que
  justifica la respuesta correcta. Al procesar el temario, la app comprueba que esa frase aparece
  realmente en esa página. Si no aparece, la pregunta se marca y **no se usa nunca**. La justificación
  que se muestra al corregir es el texto del propio PDF, no una transcripción.
- **No hay «examen oficial».** El PDF no define número de preguntas, tiempo ni nota de corte, así que
  el modo examen usa una configuración personalizable (por defecto 20 preguntas, 20 minutos, apto con
  el 50 % y sin penalización por fallo). No se inventa un formato.

| Módulo | Apartados (código · páginas · preguntas) |
|---|---|
| 1. Concepto normativo | 1.1 Conceptos básicos (4–7, 12) · 1.2 Contexto histórico (8, 6) · 1.3 Marco normativo (9–12, 14) · 1.4 Definiciones importantes (13–16, 17) · 1.5 Procedimientos principales aplicables al uso de UAS (17–50, 70) · 1.R Resumen (51–54, 1) |
| 2. Conocimiento general del vuelo con UAS | 2.1 Principios de vuelo (60–69, 23) · 2.2 Conocimiento general del UAS (70–89, 36) · 2.3 Espacio aéreo (90–114, 44) · 2.4 Limitaciones de factores humanos (115–120, 14) · 2.5 Privacidad y protección de datos (121–124, 8) · 2.6 Seguridad física de UAS (125–128, 9) · 2.7 Seguro y accidentes e incidentes de UAS (129–132, 8) · 2.R Resumen (133–139, 1) |
| 3. Procedimientos operacionales | 3.1 Previos al vuelo (143–149, 10) · 3.2 Durante el vuelo (150–156, 11) · 3.3 Posteriores al vuelo (157, 3) · 3.R Resumen (158–160, 1) |

### Añadir o corregir preguntas

Edita `app/src/main/assets/preguntas.json`. Cada entrada tiene este formato:

```json
{ "id": "p007-1", "page": 7,
  "question": "¿Quién tiene la potestad sancionadora…?",
  "options": ["EASA", "AESA", "La Comisión Europea"],
  "answer": 1,
  "source": "AESA es quien tiene la potestad sancionadora." }
```

- `page` es el número de página del PDF; el tema de la pregunta se deduce de esa página.
- `answer` es el índice (empezando en 0) de la opción correcta.
- `source` debe ser texto literal de esa página. Se ignoran las mayúsculas, las tildes, los espacios
  y la puntuación.

Después ejecuta los tests (`RealPdfParsingTest` comprueba que todas las citas se encuentran) y
recompila. Al cambiar el PDF o el JSON, la app vuelve a procesar el temario en el siguiente arranque
**sin tocar el historial**.

## Funciones

- **Estudiar temario**: temas agrupados por módulo, con búsqueda, progreso, subapartados (salto
  directo al apartado) y marcadores. Lectura en scroll continuo, con la posición guardada, la opción
  de marcar como completado y el paso al siguiente tema.
- **Preguntas en vivo**: por temas o aleatoria general, con prioridad a las falladas y no respondidas.
  Al responder se muestra la corrección inmediata, la frase del temario que la justifica (con su
  página) y la teoría relacionada. Al final hay un resumen por tema y la opción de repetir las falladas.
- **Modo examen**: examen personalizado (temas, número de preguntas, tiempo, penalización y nota de
  corte). No muestra respuestas ni justificaciones hasta entregar. Después: resultado, comparación con
  exámenes anteriores y revisión con filtros.
- **Progreso**: historial persistente, media, mejor nota, tendencia, acierto por tema, temas fuertes
  y débiles, y las preguntas más falladas.
- **Ajustes**: tema claro/oscuro, idioma, tamaño de texto, valores por defecto, exportación del
  historial a CSV/JSON y borrado del progreso (con confirmación escrita).

## Cómo se interpreta el PDF

Ver `app/src/main/java/.../parser/DocumentParser.kt`:

- Cada módulo empieza con una portada («Versión 9 (Julio 2024)»), seguida de las diapositivas de
  «Índice». Las entradas `1) …` son los temas, y las `a) …` / `1. …` sus subapartados.
- Cada diapositiva de contenido se asigna, en orden, al tema del índice que mejor coincide con su
  cabecera (la ruta «Concepto Normativo › Marco normativo») y con su título. Las diapositivas
  «Resumen/RECUERDA» forman un tema de resumen por módulo. Se descartan el pie de página, el número
  de página y la diapositiva de cierre.
- Las diapositivas colocan cuadros de texto en paralelo (dos columnas, leyendas). El ensamblador
  separa los cuadros por el hueco horizontal, y cada párrafo solo continúa con las líneas de su misma
  columna. Las etiquetas sueltas de los diagramas («15 m», «VLOS») se omiten, porque las imágenes de
  la diapositiva se muestran junto al texto.
- Las viñetas de fuentes Symbol/Wingdings (U+F0A7, U+F076, U+F0D8…) se convierten en listas, y los
  títulos repetidos en diapositivas consecutivas se muestran una sola vez.

Resultado con este PDF: 18 temas, 158 imágenes y 288/288 preguntas con su cita encontrada, sin avisos.
Las tablas que el PDF dibuja como texto suelto (p. ej. las clases de espacio aéreo de la pág. 98) se
leen por fragmentos.

## Arquitectura

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Coroutines/Flow · MVVM.

```
app/src/main/java/com/examenesdrones/app/
  parser/   Lógica pura de Kotlin: glifos → líneas → diapositivas → temas; banco de preguntas y citas
  pdf/      PdfBox-Android (texto + fuente + imágenes) del PDF integrado
  domain/   Selección de preguntas, generación de exámenes, barajado seguro de opciones, puntuación
  data/     Room (entidades, DAO), repositorios, DataStore, exportación
  ui/       Pantallas Compose y ViewModels
app/src/main/assets/preguntas.json   Banco de preguntas (con cita literal de cada respuesta)
```

Integridad de datos:

- El historial (exámenes, prácticas, estadísticas, marcadores y lectura) no tiene claves foráneas
  hacia el temario. Cada respuesta de examen guarda una copia del enunciado, de las opciones
  mostradas, de la respuesta correcta y de la justificación. Las preguntas se identifican por una
  clave estable (un hash del contenido), así que actualizar el PDF o el banco de preguntas no borra
  ni corrompe el historial.
- Las prácticas y exámenes se guardan tras cada respuesta y pueden reanudarse tras cerrar la app.
- No hay `fallbackToDestructiveMigration`: los cambios de esquema necesitan migraciones explícitas
  (`AppDatabase.MIGRATIONS`), y los esquemas se exportan en `app/schemas/`.
- Las opciones solo se barajan si ninguna hace referencia a otras («Las dos anteriores»…).

## Compilar e instalar

Requisitos: JDK 17 y Android SDK (plataforma 35). En este equipo:

```bash
export JAVA_HOME=$HOME/.jdks/jdk-17.0.20.1+1
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # todos los tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La compilación copia el único PDF de la raíz del proyecto a `assets/temario.pdf`, y falla si no hay
exactamente uno. `local.properties` apunta a `sdk.dir=/home/mario/android-sdk`. El identificador de
la app es `com.examenesdrones.app`, así que se instala por separado de cualquier otra app.

## Tests

`./gradlew :app:testDebugUnitTest` (JVM + Robolectric, no necesita emulador):

- `RealPdfParsingTest`: lee el PDF real con PDFBox de escritorio y comprueba los 18 temas, sus
  páginas y títulos, la lectura correcta de columnas y frases cortadas, y que las 288 preguntas son
  utilizables, citan texto de su página, tienen teoría relacionada y reparten la respuesta correcta
  entre A/B/C.
- `AndroidPdfExtractorTest`: el extractor de la app (PdfBox-Android) da exactamente los mismos temas,
  texto y preguntas que el de escritorio, y extrae las imágenes.
- `DocumentParserTest`: presentaciones sintéticas. Cubre módulos e índices, la asignación de
  diapositivas a temas, los resúmenes, las columnas, los títulos repetidos, las etiquetas, y que las
  citas que no aparecen en la página, las respuestas inválidas, las preguntas con pocas opciones y
  los duplicados se marquen sin adivinar nada.
- `QuestionLogicTest`, `RepositoryTest`, `BundledContentTest`, `MigrationTest`: selección, barajado y
  puntuación; persistencia, reanudación y reimportación sin pérdida de historial; carga única del
  contenido integrado; esquema de Room.
- `AppFlowTest`: interfaz Compose real recorriendo los tres modos.

Los datos sintéticos de prueba (`TestContent`) solo existen en el código de test y no se incluyen en la app.
