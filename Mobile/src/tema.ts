// La misma línea gráfica que el escritorio: tokens de DESIGN.md (raíz del repo) y del contrato
// al inicio de Desktop/src/main/resources/ui/estilos.css. Paleta obligatoria gris violácea.

export const color = {
  c0: '#17151c', // carbón violeta: fondo
  c1: '#292531', // pizarra profunda
  c2: '#45404f', // ciruela humo
  c3: '#716879', // malva ceniza
  c4: '#aaa2b3', // lila niebla: texto secundario
  c5: '#e2dde7', // lavanda hueso: texto principal y acción
  riel: '#121016',
  panel: '#211e28',
  superficie: '#1f1c26',
  texto3: '#8e8597',
  linea: 'rgba(226, 221, 231, 0.07)',
  velo: 'rgba(226, 221, 231, 0.06)',
  veloFuerte: 'rgba(226, 221, 231, 0.11)',
  sinCategoria: '#5e5669',
} as const;

/** Las claves con que se cargan las fuentes en src/app/_layout.tsx (useFonts). */
export const fuente = {
  normal: 'Figtree',
  media: 'Figtree-Medium',
  semi: 'Figtree-SemiBold',
  mono: 'Mono-Medium',
  monoSemi: 'Mono-SemiBold',
} as const;

export const radio = { chico: 10, medio: 14, grande: 18, pildora: 999 } as const;

/** Series de gráficas (validadas en el escritorio: ΔE 30 contra el fondo, también con daltonismo). */
export const serie = { principal: '#e2dde7', comparacion: '#8a7aa8' } as const;
