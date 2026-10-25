# 01 · Núcleo y persistencia

Fase 1: dominio + SQLite + migraciones + pruebas, con una consola temporal.
Todavía sin UI gráfica.

## Estructura

```
Desktop/
├── pom.xml                         build, dependencias, plugins
├── mvnw / mvnw.cmd                 Maven Wrapper (no hace falta instalar Maven)
├── .mvn/wrapper/maven-wrapper.properties   versión exacta de Maven (3.9.16)
├── ejecutar.cmd                    compila y lanza la app con el JDK 25
├── src/main/java/com/registrogastos/
│   ├── App.java                    raíz de composición
│   ├── dominio/      Gasto, Categoria
│   ├── persistencia/ BaseDeDatos, Migrador, *Repositorio, ErrorDePersistencia
│   ├── servicio/     GastoServicio
│   └── consola/      MenuConsola (temporal)
├── src/main/resources/db/migraciones/V1__esquema_inicial.sql
└── src/test/java/...               JUnit 6 contra SQLite real
```

Las dependencias van en un solo sentido: `consola → servicio → persistencia → dominio`.
El dominio no importa nada del proyecto. Por eso, cuando llegue JavaFX, solo se
reemplaza `consola/` y nada más cambia.

## Decisiones del build

**Maven y no Gradle.** El POM es declarativo: lo que lees es lo que hay. En Gradle
el build es código (Kotlin/Groovy), más flexible pero más difícil de leer mientras
aprendes el lenguaje. El wrapper fija la versión de Maven, así que el proyecto
compila igual en cualquier máquina.

**`maven.compiler.release=25`** y no `source`/`target`: `release` además verifica que
no uses APIs que no existen en esa versión del JDK.

**BOM de JUnit** (`dependencyManagement` + `scope import`): importas un POM que fija
las versiones de todos los artefactos de JUnit, y `junit-jupiter` va sin versión.

**`--enable-native-access=ALL-UNNAMED`**: sqlite-jdbc trae SQLite compilado en C y
lo carga por JNI. Desde Java 24 (JEP 472) la JVM avisa cuando un jar carga código
nativo; con este flag lo autorizas de forma explícita. Está en surefire (pruebas) y
en `ejecutar.cmd`.

**`ejecutar.cmd` y no `exec-maven-plugin`.** Probé `exec:exec` y no le pasa stdin al
proceso hijo, así que el menú se quedaba colgado. El script compila (y la fase
`compile` copia el jar de SQLite a `target/dependency/`) y luego llama a
`%JAVA_HOME%\bin\java.exe`. Usa la ruta completa porque en tu PATH el `java`
es el 17.

## Dominio: `Gasto` y `Categoria`

Son **records**: inmutables, con `equals`/`hashCode`/`toString` generados. Lo
interesante:

- **Constructor compacto** (`public Gasto { ... }`): corre antes de asignar los
  campos. Puedes validar y también *reasignar parámetros* (`monto = monto.setScale(2, ...)`),
  y el campo queda con el valor normalizado. Resultado: no se puede crear un
  `Gasto` inválido, venga de la UI, de la base o del celular.
- **Métodos que "modifican"** (`editar`, `eliminar`) devuelven una copia nueva. Así el
  estado no cambia por debajo, algo que importa cuando haya hilos (la UI y la sync).
- **`BigDecimal` para el dinero.** La prueba `totalDelMesSumaExacto` lo demuestra: 0.10 + 0.20
  da 0.30 exacto (con `double` da 0.30000000000000004). `RoundingMode.UNNECESSARY` hace
  que `setScale` lance excepción en vez de redondear en silencio; antes se rechaza
  cualquier monto con más de 2 decimales.
- **Factories estáticas** (`Gasto.nuevo(...)`): ponen nombre a la intención y generan
  el UUID. `new Gasto(...)` con los 8 campos queda para reconstruir desde la base.
- **El tiempo entra como parámetro** (`Instant ahora`), no con `Instant.now()` adentro.
  El dominio queda determinista y fácil de probar.

## Esquema pensado para la sincronización

`V1__esquema_inicial.sql` ya trae lo que pide la sync Desktop ↔ Mobile:

| Columna | Por qué |
|---|---|
| `id TEXT` (UUID) | Con autoincrement, PC y celular generarían el id 5 cada uno y chocarían. Con UUID, cada dispositivo crea ids sin coordinarse. |
| `actualizado_en` | Para resolver conflictos con *last-write-wins*: gana el cambio más reciente. |
| `eliminado` (borrado lógico) | Si borras la fila, el otro dispositivo no tiene forma de saber que se borró y te la vuelve a mandar. La "lápida" sí viaja. |
| `monto_centavos INTEGER` | SQLite no tiene decimal exacto (REAL es `double`). Un entero en centavos es exacto y React Native lo lee igual. |
| `fecha TEXT` ISO | `2026-10-25` ordena como texto igual que como fecha, así que `BETWEEN` funciona. |

Las mismas columnas existirán en el SQLite del celular. Ese es el contrato.

## Persistencia

**`BaseDeDatos`** centraliza la URL y la configuración. Detalle importante: **SQLite
trae las foreign keys apagadas por defecto** y se activan *por conexión*. Por eso se
activan en `abrirConexion()` y hay una prueba que lo verifica
(`lasLlavesForaneasEstanActivas`).

**Una conexión por operación** con try-with-resources: en SQLite abrir una conexión
cuesta muy poco (es un archivo local, no un servidor) y te aseguras de que nada
queda abierto. Fíjate en el orden de cierre: `try (var c = ...; var ps = ...)`
cierra `ps` y después `c`, al revés de como se abrieron.

**`Migrador`** usa `PRAGMA user_version`, un entero libre en la cabecera del archivo
SQLite, como número de versión del esquema. Sin Flyway ni dependencias extra. Cada
migración corre en una transacción junto con el cambio de versión: o queda todo
aplicado o no queda nada. La lista de migraciones es explícita porque dentro de un
jar no se pueden listar los recursos de una carpeta de forma confiable.

**Upsert** (`INSERT ... ON CONFLICT(id) DO UPDATE`): `guardar` sirve para crear y
para editar. Es justo la operación que necesita la sync ("aplica este registro que
viene del otro dispositivo"). `excluded.x` es el valor que se intentó insertar.

**`ErrorDePersistencia` (unchecked).** `SQLException` es checked: si se dejara subir,
el servicio y la UI tendrían que declarar `throws SQLException` y quedarían
acoplados a JDBC. Se envuelve conservando la causa (`super(mensaje, causa)`), así
el stack trace original no se pierde.

## Servicio

`GastoServicio` reúne los casos de uso y las reglas que cruzan entidades (por ejemplo,
"la categoría debe existir y no estar eliminada"). Recibe un **`Clock`** por
constructor: en producción `Clock.systemDefaultZone()` y en las pruebas
`Clock.fixed(...)`, que es la forma estándar en `java.time` de controlar el "ahora".

No hice interfaces para los repositorios (`GastoRepositorio` + `SqliteGastoRepositorio`)
porque hoy hay una sola implementación y las pruebas usan SQLite real. Si mañana
hiciera falta, extraer una interfaz es un refactor mecánico.

## `App` y la consola

`App` es la **raíz de composición**: el único lugar donde se hace `new` de las piezas
y se conectan. Es inyección de dependencias a mano, sin framework.

`MenuConsola` usa **`java.lang.IO`** (Java 25, JEP 512): `IO.println` / `IO.readln`
sin `System.out` ni `Scanner`. `readln` devuelve `null` al fin de la entrada, y eso
se usa para salir.

## Pruebas

JUnit 6 + `@TempDir`: cada prueba recibe una carpeta nueva con su propio `.db`, que
se borra al terminar. Se prueba contra SQLite real y no contra mocks, porque lo que
interesa verificar es justamente el SQL, los tipos y las restricciones.

## Comandos

```
cd Desktop
mvnw.cmd test                    9 pruebas
ejecutar.cmd                     usa %USERPROFILE%\.registro-gastos\gastos.db
ejecutar.cmd -Dgastos.db=C:\tmp\prueba.db    otra base
```
