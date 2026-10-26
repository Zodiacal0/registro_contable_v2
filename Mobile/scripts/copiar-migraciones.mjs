// Copia las migraciones SQL del escritorio a un módulo TypeScript.
//
// El esquema tiene UNA sola fuente de verdad: Desktop/src/main/resources/db/migraciones.
// La sincronización exige que el teléfono y la PC tengan exactamente las mismas tablas y la
// misma versión (PRAGMA user_version), así que en vez de copiar el SQL a mano se genera.
//
// Uso: npm run migraciones   (también corre solo antes de `npm run android`)
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const origen = join(aqui, '..', '..', 'Desktop', 'src', 'main', 'resources', 'db', 'migraciones');
const destino = join(aqui, '..', 'src', 'db', 'migraciones.generadas.ts');

// V1__esquema_inicial.sql, V2__..., ordenadas por su número (no alfabéticamente: V10 va después de V9)
const archivos = readdirSync(origen)
  .filter((n) => /^V\d+__.+\.sql$/.test(n))
  .sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]));

archivos.forEach((nombre, i) => {
  const numero = Number(nombre.match(/^V(\d+)/)[1]);
  if (numero !== i + 1) {
    throw new Error(`Falta la migración V${i + 1} (se encontró ${nombre})`);
  }
});

const cuerpo = archivos
  .map((nombre) => `  {\n    nombre: ${JSON.stringify(nombre)},\n    sql: ${JSON.stringify(readFileSync(join(origen, nombre), 'utf8'))},\n  },`)
  .join('\n');

writeFileSync(
  destino,
  `// GENERADO por scripts/copiar-migraciones.mjs a partir de Desktop/.../db/migraciones.\n` +
    `// No editar a mano: cambia el .sql del escritorio y vuelve a correr \`npm run migraciones\`.\n\n` +
    `export const MIGRACIONES: { nombre: string; sql: string }[] = [\n${cuerpo}\n];\n`,
);
console.log(`Migraciones copiadas: ${archivos.length} (V1–V${archivos.length})`);
