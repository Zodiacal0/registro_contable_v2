@echo off
rem Compila y ejecuta la app. Argumentos extra van a la JVM, ej: ejecutar.cmd -Dgastos.db=C:\tmp\prueba.db
setlocal
set "RAIZ=%~dp0"
call "%RAIZ%mvnw.cmd" -q -B compile || exit /b 1
rem JAVA_HOME (JDK 25), no el "java" del PATH
"%JAVA_HOME%\bin\java.exe" --enable-native-access=ALL-UNNAMED %* -cp "%RAIZ%target\classes;%RAIZ%target\dependency\*" com.registrogastos.App
