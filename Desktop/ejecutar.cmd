@echo off
rem Compila y ejecuta la app. Argumentos extra van a la JVM, ej: ejecutar.cmd -Dgastos.db=C:\tmp\prueba.db
setlocal
set "RAIZ=%~dp0"
call "%RAIZ%mvnw.cmd" -q -B compile || exit /b 1
rem JAVA_HOME (JDK 25), no el "java" del PATH. JavaFX va en el module path; la app y SQLite en el classpath.
rem enablePreview: la barra de título propia (HeaderBar) es "preview" en JavaFX 25.
"%JAVA_HOME%\bin\java.exe" --enable-native-access=javafx.graphics,ALL-UNNAMED ^
  -Djavafx.enablePreview=true -Djavafx.suppressPreviewWarning=true ^
  --module-path "%RAIZ%target\javafx" --add-modules javafx.controls ^
  %* -cp "%RAIZ%target\classes;%RAIZ%target\dependency\*" com.registrogastos.App
