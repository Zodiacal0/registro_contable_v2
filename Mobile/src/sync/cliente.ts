import type { SQLiteDatabase } from 'expo-sqlite';

import { guardarAjuste, leerAjuste } from '@/db/datos';
import { versionEsquema } from '@/db/migrar';

import { aplicarCambios, cambiosDesde } from './cambios';

/**
 * El teléfono como cliente del servidor de sincronización del escritorio
 * (Desktop/.../sincronizacion/SincronizacionServicio.java).
 *
 * Dos marcas de tiempo, porque cada aparato tiene su propio reloj y pueden no coincidir:
 *  - sync_enviado:  reloj del TELÉFONO en la última sync. Elige qué filas locales mandar.
 *  - sync_recibido: reloj de la PC (el "hasta" que devolvió). Se lo pasamos como "desde"
 *                   para que la PC elija qué mandar según SU reloj.
 * Si se usara una sola marca y el teléfono fuera atrasado unos minutos, un gasto anotado justo
 * después de sincronizar podría quedar "antes" de la marca y no viajar nunca.
 */

/** Lo que trae el QR de la pantalla Sincronizar del escritorio. */
type DatosQr = { app: string; ips: string[]; puerto: number; codigo: string };

export type Estado = { host: string | null; ultima: number | null };

const ESPERA_MS = 4000;

export async function estado(db: SQLiteDatabase): Promise<Estado> {
  const host = await leerAjuste(db, 'sync_host');
  const ultima = Number((await leerAjuste(db, 'sync_enviado')) ?? '0');
  return { host: host || null, ultima: ultima > 0 ? ultima : null };
}

/** fetch con tiempo máximo: en una red local, si no responde en 4 s no va a responder. */
async function pedir(url: string, init: RequestInit = {}): Promise<Response> {
  const control = new AbortController();
  const reloj = setTimeout(() => control.abort(), ESPERA_MS);
  try {
    return await fetch(url, { ...init, signal: control.signal });
  } finally {
    clearTimeout(reloj);
  }
}

async function errorDe(respuesta: Response): Promise<Error> {
  try {
    const cuerpo = await respuesta.json();
    return new Error(cuerpo.error ?? `La PC respondió ${respuesta.status}`);
  } catch {
    return new Error(`La PC respondió ${respuesta.status}`);
  }
}

/**
 * Empareja con la PC a partir del texto del QR: prueba cada IP que anunció (la PC puede
 * tener varias redes) hasta dar con una que responda, y canjea el código por un token.
 */
export async function emparejar(db: SQLiteDatabase, textoQr: string): Promise<string> {
  let datos: DatosQr;
  try {
    datos = JSON.parse(textoQr);
  } catch {
    throw new Error('Ese QR no es el de Registro de Gastos');
  }
  if (datos.app !== 'registro-gastos' || !Array.isArray(datos.ips)) {
    throw new Error('Ese QR no es el de Registro de Gastos');
  }
  let host: string | null = null;
  for (const ip of datos.ips) {
    try {
      const info = await pedir(`http://${ip}:${datos.puerto}/info`);
      if (info.ok) {
        host = `${ip}:${datos.puerto}`;
        break;
      }
    } catch {
      // esa IP no es alcanzable desde el teléfono (otra red, VPN...): se prueba la siguiente
    }
  }
  if (!host) {
    throw new Error('No se pudo llegar a la PC. ¿Están en el mismo Wi‑Fi? Si Windows preguntó por el firewall, permite Java en redes privadas.');
  }
  const respuesta = await pedir(`http://${host}/emparejar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ codigo: datos.codigo }),
  });
  if (!respuesta.ok) {
    throw await errorDe(respuesta);
  }
  const { token } = await respuesta.json();
  await guardarAjuste(db, 'sync_host', host);
  await guardarAjuste(db, 'sync_token', token);
  await guardarAjuste(db, 'sync_enviado', '0');
  await guardarAjuste(db, 'sync_recibido', '0');
  return host;
}

export async function olvidar(db: SQLiteDatabase): Promise<void> {
  for (const clave of ['sync_host', 'sync_token', 'sync_enviado', 'sync_recibido']) {
    await guardarAjuste(db, clave, '');
  }
}

export type Resultado = { enviadas: number; recibidas: number };

/** Una ronda completa: manda lo local nuevo, recibe lo de la PC y lo aplica. */
export async function sincronizar(db: SQLiteDatabase): Promise<Resultado> {
  const host = await leerAjuste(db, 'sync_host');
  const token = await leerAjuste(db, 'sync_token');
  if (!host || !token) {
    throw new Error('Primero empareja el teléfono con la PC');
  }
  const enviado = Number((await leerAjuste(db, 'sync_enviado')) ?? '0') || 0;
  const recibido = Number((await leerAjuste(db, 'sync_recibido')) ?? '0') || 0;
  const marca = Date.now(); // se toma ANTES de leer: lo que se anote durante la sync viaja la próxima vez
  const locales = await cambiosDesde(db, enviado);
  const enviadas = Object.values(locales).reduce((n, filas) => n + filas.length, 0);

  let respuesta: Response;
  try {
    respuesta = await pedir(`http://${host}/sync`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ esquema: await versionEsquema(db), desde: recibido, cambios: locales }),
    });
  } catch {
    throw new Error('La PC no responde. ¿Está abierta la app y en el mismo Wi‑Fi?');
  }
  if (!respuesta.ok) {
    throw await errorDe(respuesta);
  }
  const { hasta, cambios } = await respuesta.json();
  const recibidas = await aplicarCambios(db, cambios ?? {});
  await guardarAjuste(db, 'sync_enviado', String(marca));
  await guardarAjuste(db, 'sync_recibido', String(hasta));
  return { enviadas, recibidas };
}
