import { randomUUID } from 'expo-crypto';
import type { SQLiteDatabase } from 'expo-sqlite';

import { diasDelMes } from '@/formato';

// Lecturas y escrituras de la app. Mismas reglas que el escritorio:
// - el dinero en centavos enteros, las fechas en texto ISO (yyyy-mm-dd);
// - cada cambio pone actualizado_en = ahora (es lo que usa la sincronización);
// - nada se borra: eliminado = 1 (borrado lógico, para que el borrado también viaje).

/** La cuenta que crea la migración V7, con id fijo (el mismo en todos los aparatos). */
export const CUENTA_PRINCIPAL = '00000000-0000-4000-8000-000000000001';

export type Categoria = { id: string; nombre: string; color: string };
export type Cuenta = { id: string; nombre: string; tipo: string; moneda: string };
export type Gasto = {
  id: string;
  descripcion: string;
  monto_centavos: number;
  fecha: string;
  categoria_id: string | null;
  recurrencia_id: string | null;
  deuda_id: string | null;
  cuenta_id: string | null;
  creado_en: number;
};

/** "Qué moneda tiene cada cuenta y a cuánto se cambia": lo mismo que Conversor.java. */
export type Conversor = { monedaDe: (cuentaId: string | null) => string; aPrincipal: (g: Gasto) => number };

export async function categorias(db: SQLiteDatabase): Promise<Categoria[]> {
  return db.getAllAsync<Categoria>(
    'SELECT id, nombre, color FROM categorias WHERE eliminado = 0 ORDER BY nombre COLLATE NOCASE');
}

export async function cuentas(db: SQLiteDatabase): Promise<Cuenta[]> {
  return db.getAllAsync<Cuenta>(
    'SELECT id, nombre, tipo, moneda FROM cuentas WHERE eliminado = 0 ORDER BY id = ? DESC, nombre COLLATE NOCASE',
    CUENTA_PRINCIPAL);
}

export async function gastosEntre(db: SQLiteDatabase, desde: string, hasta: string): Promise<Gasto[]> {
  return db.getAllAsync<Gasto>(
    `SELECT id, descripcion, monto_centavos, fecha, categoria_id, recurrencia_id, deuda_id, cuenta_id, creado_en
     FROM gastos WHERE eliminado = 0 AND fecha BETWEEN ? AND ? ORDER BY fecha DESC, creado_en DESC`,
    desde, hasta);
}

export function rangoMes(anio: number, mes: number): [string, string] {
  const mm = mes.toString().padStart(2, '0');
  return [`${anio}-${mm}-01`, `${anio}-${mm}-${diasDelMes(anio, mes).toString().padStart(2, '0')}`];
}

export async function leerAjuste(db: SQLiteDatabase, clave: string): Promise<string | null> {
  const fila = await db.getFirstAsync<{ valor: string }>('SELECT valor FROM ajustes WHERE clave = ?', clave);
  return fila?.valor ?? null;
}

export async function guardarAjuste(db: SQLiteDatabase, clave: string, valor: string): Promise<void> {
  await db.runAsync(
    `INSERT INTO ajustes (clave, valor, actualizado_en) VALUES (?, ?, ?)
     ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor, actualizado_en = excluded.actualizado_en`,
    clave, valor, Date.now());
}

/** Monedas de todas las cuentas (también las eliminadas: sus gastos siguen) y el tipo de cambio. */
export async function conversor(db: SQLiteDatabase): Promise<Conversor> {
  const filas = await db.getAllAsync<{ id: string; moneda: string }>('SELECT id, moneda FROM cuentas');
  const monedas = new Map(filas.map((f) => [f.id, f.moneda]));
  const tasa = Number((await leerAjuste(db, 'tipo_cambio_USD')) ?? '1') || 1;
  const monedaDe = (cuentaId: string | null) => monedas.get(cuentaId ?? CUENTA_PRINCIPAL) ?? 'GTQ';
  return {
    monedaDe,
    aPrincipal: (g) => (monedaDe(g.cuenta_id) === 'GTQ' ? g.monto_centavos : Math.round(g.monto_centavos * tasa)),
  };
}

export type NuevoGasto = {
  descripcion: string;
  montoCentavos: number;
  fecha: string;
  categoriaId: string | null;
  cuentaId: string;
};

export async function registrarGasto(db: SQLiteDatabase, g: NuevoGasto): Promise<string> {
  const id = randomUUID();
  const ahora = Date.now();
  await db.runAsync(
    `INSERT INTO gastos (id, descripcion, monto_centavos, fecha, categoria_id, cuenta_id, creado_en, actualizado_en, eliminado)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)`,
    id, g.descripcion.trim(), g.montoCentavos, g.fecha, g.categoriaId, g.cuentaId, ahora, ahora);
  return id;
}

export async function eliminarGasto(db: SQLiteDatabase, id: string): Promise<void> {
  await db.runAsync('UPDATE gastos SET eliminado = 1, actualizado_en = ? WHERE id = ?', Date.now(), id);
}
