// Config plugin: que el C++ de los módulos nativos compile en Windows con este proyecto en una
// ruta larga (C:\Users\Javier Herrera\Desktop\Proyectos\Programas\Registro de Gastos\Mobile).
//
// El problema: CMake nombra cada archivo intermedio con la ruta ABSOLUTA de su fuente
// (…/CMakeFiles/x.dir/C_/Users/…/Mobile/node_modules/<módulo>/…/Archivo.cpp.o). Eso pasa de los
// 260 caracteres, y el ninja que trae el SDK de Android (1.10) no acepta rutas más largas aunque
// Windows las tenga habilitadas. Falla con "Filename longer than 260 characters".
//
// La solución, en dos partes:
//  1. Intermedios en una carpeta corta (C:/rgcxx/<módulo>): ahorra ~120 caracteres.
//  2. Un ninja moderno (1.12 o más) en C:/rgcxx/ninja.exe, que sí maneja rutas largas
//     (instalación en Mobile/README.md). Si no existe, se sigue con el del SDK.
//
// Gradle solo acepta estos ajustes MIENTRAS configura cada módulo, por eso van en dos lugares:
//  - librerías nativas: desde el build.gradle raíz, al aplicarse su plugin de Android;
//  - la app: al final de app/build.gradle (desde la raíz ya es tarde: "too late to set").
// Como android/ se regenera con `expo prebuild`, todo vive aquí y no a mano en los build.gradle.
const { withAppBuildGradle, withProjectBuildGradle } = require('expo/config-plugins');

const MARCA = '// rutas-cortas-cxx';
const EN_WINDOWS = "System.getProperty('os.name').toLowerCase().contains('windows')";
const NINJA = 'C:/rgcxx/ninja.exe';

/** El bloque Groovy que configura un módulo (`modulo` es su objeto `android`). */
function ajustes(modulo, carpeta) {
  return `${modulo}.externalNativeBuild.cmake.buildStagingDirectory = new File("C:/rgcxx/${carpeta}")
    if (new File("${NINJA}").exists()) {
      ${modulo}.defaultConfig.externalNativeBuild.cmake.arguments("-DCMAKE_MAKE_PROGRAM=${NINJA}")
    }`;
}

function conLibrerias(config) {
  return withProjectBuildGradle(config, (gradle) => {
    if (!gradle.modResults.contents.includes(MARCA)) {
      gradle.modResults.contents += `
${MARCA}: en Windows, C++ de las librerías con carpeta corta y ninja moderno (rutas de más de 260 caracteres)
if (${EN_WINDOWS}) {
  subprojects { proyecto ->
    proyecto.plugins.withId('com.android.library') {
      ${ajustes('proyecto.android', '${proyecto.name}')}
    }
  }
}
`;
    }
    return gradle;
  });
}

function conApp(config) {
  return withAppBuildGradle(config, (gradle) => {
    if (!gradle.modResults.contents.includes(MARCA)) {
      gradle.modResults.contents += `
${MARCA}: en Windows, C++ de la app (codegen de los módulos) con carpeta corta y ninja moderno
if (${EN_WINDOWS}) {
  ${ajustes('android', 'app')}
}
`;
    }
    return gradle;
  });
}

module.exports = function rutasCortasCxx(config) {
  return conApp(conLibrerias(config));
};
