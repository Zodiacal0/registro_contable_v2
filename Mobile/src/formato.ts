// Formatos de texto, equivalentes a Desktop/.../ui/Formato.java.
// El dinero vive en CENTAVOS enteros (como en la base): nunca en decimales de punto flotante.

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre',
  'octubre', 'noviembre', 'diciembre'];
const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic'];
const DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

/** 125050 -> "1,250.50" */
export function monto(centavos: number): string {
  const negativo = centavos < 0;
  const abs = Math.abs(Math.round(centavos));
  const entero = Math.floor(abs / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const decimales = (abs % 100).toString().padStart(2, '0');
  return `${negativo ? '−' : ''}${entero}.${decimales}`;
}

/** Con símbolo si no es la moneda principal: "US$ 12.99". */
export function montoEn(centavos: number, moneda: string): string {
  return moneda === 'GTQ' ? monto(centavos) : `US$ ${monto(centavos)}`;
}

/** 125050 -> ["1,250", ".50"]: el entero grande y los decimales chicos, como en Binance. */
export function montoPartido(centavos: number): [string, string] {
  const texto = monto(centavos);
  const punto = texto.lastIndexOf('.');
  return [texto.slice(0, punto), texto.slice(punto)];
}

/**
 * Lee lo que el usuario escribió y devuelve centavos. Acepta "12.5", "12,50", "Q 1,250.50" y
 * "1.250,50" (como Formato.leerMonto del escritorio). Lanza Error con un mensaje para mostrar.
 */
export function leerMonto(texto: string): number {
  if (!texto || !texto.trim()) {
    throw new Error('Escribe el monto');
  }
  const limpio = texto.replace(/[^0-9.,]/g, '');
  if (!/\d/.test(limpio)) {
    throw new Error('El monto lleva números, por ejemplo 12.50');
  }
  const separador = Math.max(limpio.lastIndexOf('.'), limpio.lastIndexOf(','));
  const hayAmbos = limpio.includes('.') && limpio.includes(',');
  const esDecimal = separador >= 0 && (hayAmbos
    || (limpio.indexOf(limpio[separador]) === separador && limpio.length - separador - 1 <= 2));
  const entero = (esDecimal ? limpio.slice(0, separador) : limpio).replace(/[.,]/g, '') || '0';
  const decimales = esDecimal ? limpio.slice(separador + 1) : '';
  if (decimales.length > 2) {
    throw new Error('El monto admite máximo 2 decimales');
  }
  const centavos = Number(entero) * 100 + Number(decimales.padEnd(2, '0') || '0');
  if (centavos <= 0) {
    throw new Error('El monto debe ser mayor que cero');
  }
  return centavos;
}

export function tituloMes(anio: number, mes: number): string {
  const nombre = MESES[mes - 1];
  return nombre[0].toUpperCase() + nombre.slice(1);
}

export function nombreMes(mes: number): string {
  return MESES[mes - 1];
}

/** "Hoy", "Ayer" o "Martes 13 de octubre". */
export function dia(fechaIso: string, hoyIso: string): string {
  if (fechaIso === hoyIso) {
    return 'Hoy';
  }
  if (fechaIso === sumarDias(hoyIso, -1)) {
    return 'Ayer';
  }
  const [a, m, d] = fechaIso.split('-').map(Number);
  const fecha = new Date(a, m - 1, d);
  return `${DIAS[fecha.getDay()]} ${d} de ${MESES[m - 1]}`;
}

/** "13 oct" */
export function fechaCorta(fechaIso: string): string {
  const [, m, d] = fechaIso.split('-').map(Number);
  return `${d} ${MESES_CORTOS[m - 1]}`;
}

export function porcentaje(proporcion: number): string {
  return `${Math.round(proporcion * 100)}%`;
}

/** "hace 5 min", "hace 2 h", o la fecha. */
export function hace(epochMs: number): string {
  const minutos = Math.floor((Date.now() - epochMs) / 60000);
  if (minutos < 1) return 'hace un momento';
  if (minutos < 60) return `hace ${minutos} min`;
  if (minutos < 24 * 60) return `hace ${Math.floor(minutos / 60)} h`;
  const fecha = new Date(epochMs);
  return `el ${fecha.getDate()} de ${MESES[fecha.getMonth()]}`;
}

// ---- Fechas ISO (yyyy-mm-dd) en hora LOCAL, como LocalDate en Java ----

export function hoyIso(): string {
  return aIso(new Date());
}

export function aIso(fecha: Date): string {
  const m = (fecha.getMonth() + 1).toString().padStart(2, '0');
  const d = fecha.getDate().toString().padStart(2, '0');
  return `${fecha.getFullYear()}-${m}-${d}`;
}

export function sumarDias(fechaIso: string, dias: number): string {
  const [a, m, d] = fechaIso.split('-').map(Number);
  return aIso(new Date(a, m - 1, d + dias));
}

export function diasDelMes(anio: number, mes: number): number {
  return new Date(anio, mes, 0).getDate();
}
