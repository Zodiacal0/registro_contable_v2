#!/usr/bin/env bash
# Compila el APK release de la app dentro de WSL (Ubuntu) y lo deja en Mobile/dist/ (en Windows).
#
# Uso, desde PowerShell en la raíz del repo:
#   wsl -d Ubuntu -- bash Mobile/scripts/compilar-wsl.sh
#
# La primera vez instala lo que falte en tu usuario de Linux, sin sudo (~3 GB de descarga):
#   - Node 22 en ~/.local/node
#   - SDK de Android en ~/Android/Sdk (WSL no puede usar el de Windows: son .exe)
# Las siguientes veces solo copia el código y compila.
#
# Por qué se copia el repo a ~/registro-gastos: compilar sobre /mnt/c (el disco de Windows) es
# varias veces más lento, y los node_modules de Windows traen binarios de Windows.
set -euo pipefail

NODE_VERSION="22.18.0"
HERRAMIENTAS="commandlinetools-linux-11076708_latest.zip"
# Las mismas versiones que pide el proyecto (ver la salida de [ExpoRootProject] al compilar)
PAQUETES=("platform-tools" "platforms;android-37" "build-tools;37.0.0" "ndk;27.1.12297006" "cmake;3.22.1")

ORIGEN="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESTINO="$HOME/registro-gastos"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$HOME/.local/node/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

paso() { printf '\n\033[1;35m▸ %s\033[0m\n' "$*"; }

# ---- 1. Herramientas (solo la primera vez) ----

if ! command -v node >/dev/null || [[ "$(node --version)" != v22* ]]; then
  paso "Instalando Node $NODE_VERSION en ~/.local/node"
  mkdir -p "$HOME/.local/node"
  curl -fsSL "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-x64.tar.xz" \
    | tar -xJ -C "$HOME/.local/node" --strip-components=1
fi

if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  paso "Instalando las herramientas del SDK de Android en $ANDROID_HOME"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  temporal="$(mktemp -d)"
  curl -fsSL -o "$temporal/herramientas.zip" "https://dl.google.com/android/repository/$HERRAMIENTAS"
  unzip -q "$temporal/herramientas.zip" -d "$temporal"
  mv "$temporal/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$temporal"
fi

if [[ ! -d "$ANDROID_HOME/ndk/27.1.12297006" ]]; then
  paso "Aceptando licencias e instalando paquetes del SDK (NDK, CMake, build-tools)"
  yes | sdkmanager --licenses >/dev/null
  sdkmanager "${PAQUETES[@]}"
fi

if ! command -v java >/dev/null; then
  echo "Falta Java 17: sudo apt install openjdk-17-jdk-headless" >&2
  exit 1
fi

# ---- 2. Copiar el código a disco de Linux ----

paso "Copiando el código a $DESTINO"
mkdir -p "$DESTINO"
# Solo lo que hace falta: Mobile/ y las migraciones del escritorio (el script las copia a la app)
tar -C "$ORIGEN" \
  --exclude='Mobile/node_modules' --exclude='Mobile/android' --exclude='Mobile/dist' --exclude='Mobile/.expo' \
  -cf - Mobile Desktop/src/main/resources/db/migraciones \
  | tar -C "$DESTINO" -xf -

# ---- 3. Compilar ----

cd "$DESTINO/Mobile"
paso "Instalando dependencias (npm ci)"
npm ci --no-audit --no-fund
npm run migraciones

paso "Generando el proyecto Android (expo prebuild)"
CI=1 npx expo prebuild --platform android --clean --no-install

paso "Compilando el APK release (arm64-v8a)"
cd android
# WSL tiene ~7 GB de RAM: 4 GB para Gradle alcanza y deja aire al resto
./gradlew assembleRelease --no-daemon -PreactNativeArchitectures=arm64-v8a -Dorg.gradle.jvmargs=-Xmx4g

mkdir -p "$ORIGEN/Mobile/dist"
cp app/build/outputs/apk/release/app-release.apk "$ORIGEN/Mobile/dist/RegistroDeGastos.apk"
paso "Listo: Mobile/dist/RegistroDeGastos.apk"
