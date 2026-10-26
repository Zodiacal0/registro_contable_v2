@echo off
rem Abre la app con datos de ejemplo en una base temporal (no toca tus datos reales).
setlocal
set "RAIZ=%~dp0"
call "%RAIZ%mvnw.cmd" -q -B test-compile || exit /b 1
start "" "%JAVA_HOME%\bin\javaw.exe" --enable-native-access=javafx.graphics,ALL-UNNAMED ^
  -Djavafx.enablePreview=true -Djavafx.suppressPreviewWarning=true ^
  --module-path "%RAIZ%target\javafx" --add-modules javafx.controls ^
  -cp "%RAIZ%target\classes;%RAIZ%target\test-classes;%RAIZ%target\dependency\*" com.registrogastos.Demo
