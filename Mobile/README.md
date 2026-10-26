# Registro de Gastos · Android

La app del teléfono: React Native con **Expo SDK 58** y Expo Router, compilada en tu PC (sin
EAS ni nube). Comparte con el escritorio el esquema de la base, la línea gráfica y los datos
(por sincronización QR en la red local; ver `Desktop/docs/09-sincronizacion.md`).

## Compilar e instalar en tu teléfono

Requisitos: Node 22+, Android Studio (SDK y su JDK), y el teléfono con **depuración USB**
activada (Ajustes → Acerca del teléfono → toca 7 veces "Número de compilación" → Opciones de
desarrollador → Depuración USB).

```powershell
# Una sola vez: el SDK de Android en una ruta SIN espacios (ver "Dos límites de Windows")
cmd /c mklink /J C:\Users\Public\AndroidSdk "$env:LOCALAPPDATA\Android\Sdk"

cd Mobile
npm install
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\Public\AndroidSdk'
npm run android        # copia las migraciones, genera android/ y compila e instala
```

### Alternativa: compilar en WSL (Ubuntu)

En Linux no existen los límites de rutas de Windows. Desde PowerShell, en la raíz del repo:

```powershell
wsl -d Ubuntu -- bash Mobile/scripts/compilar-wsl.sh
```

La primera vez instala en tu usuario de Linux, sin `sudo`, Node 22 y el SDK de Android
(~3 GB, porque WSL no puede usar el SDK de Windows). Después copia el código a
`~/registro-gastos` (compilar sobre `/mnt/c` es varias veces más lento) y deja el APK release
en `Mobile\dist\RegistroDeGastos.apk`. Las siguientes veces solo copia y compila.

### Dos límites de Windows (y cómo quedaron resueltos)

Los módulos nativos (expo-modules-core, expo-sqlite, react-native-screens) compilan C++ con
CMake y ninja. En esta PC eso choca con dos límites:

1. **Rutas de más de 260 caracteres.** CMake guarda cada archivo intermedio dentro del módulo
   y además repite la ruta absoluta del fuente; con el proyecto en una carpeta larga, pasa de
   260 y ninja falla con `mkdir(...): No such file or directory`. Lo resuelve el config
   plugin `plugins/rutas-cortas-cxx.js`, que manda los intermedios a `C:\rgcxx`. (`subst`
   no sirve: Node resuelve la ruta real y Gradle termina mezclando `R:\` y `C:\`.)
2. **Espacios en la ruta del SDK** (`C:\Users\Javier Herrera\...`). CMake llama a todo
   ejecutable con espacios en su ruta por su nombre corto 8.3: `clang++.exe` pasa a ser
   `CLANG_~1.EXE`. Como clang decide si enlaza C o C++ por su propio nombre, enlaza sin la
   librería de C++ y aparecen errores `undefined symbol: std::__ndk1::...`. Lo resuelve el
   *junction* `C:\Users\Public\AndroidSdk`: es la misma carpeta (no copia nada ni pide
   administrador) vista desde una ruta sin espacios.

`.npmrc` lleva `legacy-peer-deps=true`: `react-dom` (solo para web) pide `react` 19.3.1
exacto y Expo SDK 58 fija 19.3.0. No afecta a Android.

`npm run android` deja corriendo Metro (el servidor de JavaScript): mientras esté abierto,
los cambios en `src/` aparecen al instante en el teléfono.

## Estructura

```
scripts/copiar-migraciones.mjs   copia los .sql del escritorio (una sola fuente de verdad)
src/
  app/                    pantallas (Expo Router: cada archivo es una ruta)
    _layout.tsx           fuentes, base (migrada en onInit), estado compartido
    (tabs)/index.tsx      Resumen
    (tabs)/sincronizar.tsx
    nuevo.tsx             Nuevo gasto (hoja modal)
    escanear.tsx          cámara para el QR de la PC
  db/                     migrar, consultas (datos.ts) y migraciones generadas
  sync/                   cambios.ts (feed LWW, gemelo de Cambios.java) y cliente.ts
  componentes/            piezas de UI e íconos (los mismos trazos SVG del escritorio)
  tema.ts                 tokens de DESIGN.md
  estado.tsx              señal "cambiaron los datos" y sync automática
```

## Ícono y pantalla de arranque

El ícono es el logo del escritorio (tres cápsulas apiladas en `#716879`, `#AAA2B3` y
`#E2DDE7` sobre `#17151C`). `scripts/generar-icono.py` lo dibuja en vectores con la misma
geometría del CSS del escritorio y produce todas las variantes de `assets/`: el ícono
clásico, el adaptativo de Android (logo dentro de la zona segura y fondo aparte), el
monocromo para los íconos temáticos de Android 13+ y la imagen del splash. Si cambia el
logo: `py -3 scripts/generar-icono.py` y volver a compilar.

## Decisiones

- **El dinero en centavos enteros** también aquí (como `monto_centavos` en la base): nada de
  decimales de punto flotante.
- **`android/` no se versiona:** se regenera con `expo prebuild` desde `app.json` (Continuous
  Native Generation). Los permisos y HTTP local se configuran con plugins, no a mano.
- **Fuentes y paleta** son las del escritorio (`assets/fuentes`, `src/tema.ts`).
