import type { SQLiteDatabase } from 'expo-sqlite';

import { MIGRACIONES } from './migraciones.generadas';

/**
 * Lleva la base a la última versión, igual que Migrador.java: PRAGMA user_version guarda la
 * última migración aplicada y solo corren las de número mayor. Las migraciones son las MISMAS
 * del escritorio (las copia scripts/copiar-migraciones.mjs), así ambos lados tienen el mismo
 * esquema y la misma versión, que es lo que la sincronización verifica.
 *
 * Se llama desde el onInit de SQLiteProvider: corre antes de pintar cualquier pantalla.
 */
export async function migrar(db: SQLiteDatabase): Promise<void> {
  // SQLite trae las claves foráneas apagadas; el escritorio las activa en cada conexión
  await db.execAsync('PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;');
  const fila = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  const actual = fila?.user_version ?? 0;
  for (let version = actual + 1; version <= MIGRACIONES.length; version++) {
    const { sql } = MIGRACIONES[version - 1];
    // Cada migración y su número en una sola transacción: o queda aplicada entera o no queda
    await db.withExclusiveTransactionAsync(async (txn) => {
      await txn.execAsync(sql);
      await txn.execAsync(`PRAGMA user_version = ${version}`);
    });
  }
}

export async function versionEsquema(db: SQLiteDatabase): Promise<number> {
  const fila = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  return fila?.user_version ?? 0;
}
