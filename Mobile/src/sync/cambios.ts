import type { SQLiteDatabase } from 'expo-sqlite';

/**
 * El mismo "feed de cambios" que Desktop/.../sincronizacion/Cambios.java, en TypeScript.
 * Los dos lados DEBEN coincidir en estas tres cosas:
 *
 * 1. TABLAS y su orden (padres antes que hijos: las claves foráneas están activas).
 * 2. AJUSTES_LOCALES: lo que es de cada aparato y nunca viaja.
 * 3. La regla de conflicto: gana la última escritura (actualizado_en mayor). El UPSERT solo
 *    pisa la fila local si la que llega es más nueva.
 *
 * Formato: { tabla: [ { columna: valor, ... }, ... ], ... }
 */
export const TABLAS: [tabla: string, clave: string][] = [
  ['categorias', 'id'],
  ['cuentas', 'id'],
  ['recurrencias', 'id'],
  ['metas', 'id'],
  ['deudas', 'id'],
  ['gastos', 'id'],
  ['aportes', 'id'],
  ['cobros_deuda', 'id'],
  ['ingresos', 'id'],
  ['transferencias', 'id'],
  ['plan_lineas', 'id'],
  ['ajustes', 'clave'],
];

export const AJUSTES_LOCALES = ['sync_token', 'sync_ultima', 'sync_host', 'sync_enviado', 'sync_recibido'];

type Fila = Record<string, unknown>;
export type Paquete = Record<string, Fila[]>;

/** Todo lo que cambió DESPUÉS de `desde` (epoch ms, reloj de ESTE aparato). */
export async function cambiosDesde(db: SQLiteDatabase, desde: number): Promise<Paquete> {
  const paquete: Paquete = {};
  for (const [tabla] of TABLAS) {
    // El nombre de la tabla sale de la lista fija de arriba, nunca del usuario: es seguro ponerlo en el SQL
    let filas = await db.getAllAsync<Fila>(`SELECT * FROM ${tabla} WHERE actualizado_en > ?`, desde);
    if (tabla === 'ajustes') {
      filas = filas.filter((f) => !AJUSTES_LOCALES.includes(String(f.clave)));
    }
    if (filas.length > 0) {
      paquete[tabla] = filas;
    }
  }
  return paquete;
}

/**
 * Aplica lo que llegó del otro aparato en UNA transacción exclusiva: o entra todo o nada.
 * @returns cuántas filas cambiaron (las que perdieron por ser más viejas no cuentan)
 */
export async function aplicarCambios(db: SQLiteDatabase, paquete: Paquete): Promise<number> {
  let aplicadas = 0;
  await db.withExclusiveTransactionAsync(async (txn) => {
    for (const [tabla, clave] of TABLAS) {
      const filas = paquete[tabla];
      if (!filas?.length) {
        continue;
      }
      const columnas = (await txn.getAllAsync<{ name: string }>(`PRAGMA table_info(${tabla})`)).map((c) => c.name);
      const sets = columnas.filter((c) => c !== clave).map((c) => `${c} = excluded.${c}`).join(', ');
      const sql = `INSERT INTO ${tabla} (${columnas.join(', ')}) VALUES (${columnas.map(() => '?').join(', ')})
                   ON CONFLICT(${clave}) DO UPDATE SET ${sets}
                   WHERE excluded.actualizado_en > ${tabla}.actualizado_en`;
      for (const fila of filas) {
        if (tabla === 'ajustes' && AJUSTES_LOCALES.includes(String(fila.clave))) {
          continue;
        }
        const valores = columnas.map((c) => (fila[c] === undefined ? null : fila[c])) as (string | number | null)[];
        const resultado = await txn.runAsync(sql, valores);
        aplicadas += resultado.changes;
      }
    }
  });
  return aplicadas;
}
