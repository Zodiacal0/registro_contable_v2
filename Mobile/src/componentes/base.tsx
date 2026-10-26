import { useEffect, useState, type ReactNode } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';

import { montoPartido } from '@/formato';
import { color, fuente, radio } from '@/tema';

import { Icono, type NombreIcono } from './Icono';

// Piezas compartidas por todas las pantallas: equivalen a Nodos.java y a las clases del CSS.

/** Curva suave de entrada (la misma SALIDA de Movimiento.java: 0, 0, 0.2, 1). */
export const SALIDA = Easing.bezier(0, 0, 0.2, 1);

export function Texto({ estilo, children, lineas }: { estilo?: StyleProp<TextStyle>; children: ReactNode; lineas?: number }) {
  return <Text style={[s.texto, estilo]} numberOfLines={lineas}>{children}</Text>;
}

/** Saldo partido: el entero grande y los decimales chicos y tenues (Binance, Apple Wallet). */
export function Cifra({ centavos, tamano = 56 }: { centavos: number; tamano?: number }) {
  const [entero, decimales] = montoPartido(centavos);
  return (
    <Text style={[s.cifra, { fontSize: tamano, lineHeight: tamano * 1.1 }]}>
      {entero}
      <Text style={[s.cifraDecimales, { fontSize: Math.round(tamano * 0.53) }]}>{decimales}</Text>
    </Text>
  );
}

/** Píldora seleccionable (categorías, cuentas). */
export function Chip({ texto, activo, alPulsar, punto, icono }: {
  texto: string; activo: boolean; alPulsar: () => void; punto?: string; icono?: NombreIcono;
}) {
  return (
    <Pressable onPress={alPulsar} style={({ pressed }) => [s.chip, activo && s.chipActivo, pressed && s.presionado]}>
      {punto && <View style={[s.punto, { backgroundColor: punto }]} />}
      {icono && <Icono nombre={icono} tamano={12} color={activo ? color.c5 : color.c4} />}
      <Text style={[s.chipTexto, activo && s.chipTextoActivo]}>{texto}</Text>
    </Pressable>
  );
}

/** Botón: "primario" es la única píldora clara (la acción principal), "texto" es secundario. */
export function Boton({ texto, alPulsar, tipo = 'pildora', icono, deshabilitado }: {
  texto: string; alPulsar: () => void; tipo?: 'primario' | 'pildora' | 'texto'; icono?: NombreIcono; deshabilitado?: boolean;
}) {
  const primario = tipo === 'primario';
  return (
    <Pressable
      onPress={alPulsar}
      disabled={deshabilitado}
      style={({ pressed }) => [
        s.boton, primario && s.botonPrimario, tipo === 'texto' && s.botonTexto,
        pressed && s.presionado, deshabilitado && { opacity: 0.4 },
      ]}>
      {icono && <Icono nombre={icono} tamano={14} color={primario ? color.c0 : color.c5} />}
      <Text style={[s.botonTextoBase, primario && { color: color.c0 }]}>{texto}</Text>
    </Pressable>
  );
}

export function BotonIcono({ icono, alPulsar, etiqueta }: { icono: NombreIcono; alPulsar: () => void; etiqueta: string }) {
  return (
    <Pressable accessibilityLabel={etiqueta} onPress={alPulsar} hitSlop={8}
      style={({ pressed }) => [s.botonIcono, pressed && s.presionado]}>
      <Icono nombre={icono} tamano={16} color={color.c5} />
    </Pressable>
  );
}

export function EncabezadoSeccion({ titulo, detalle }: { titulo: string; detalle?: string }) {
  return (
    <View style={s.encabezadoSeccion}>
      <Text style={s.seccionTitulo}>{titulo}</Text>
      {detalle ? <Text style={s.seccionDetalle} numberOfLines={1}>{detalle}</Text> : null}
    </View>
  );
}

export function Tarjeta({ children, estilo }: { children: ReactNode; estilo?: StyleProp<ViewStyle> }) {
  return <View style={[s.tarjeta, estilo]}>{children}</View>;
}

/** Entrada en cascada (Movimiento.revelar): aparece y sube 10 px, 55 ms después de la anterior. */
export function Revelar({ indice = 0, children }: { indice?: number; children: ReactNode }) {
  // useState (no useRef): el valor animado se crea una vez y se puede leer durante el render
  const [avance] = useState(() => new Animated.Value(0));
  useEffect(() => {
    Animated.timing(avance, {
      toValue: 1, duration: 420, delay: indice * 55, easing: SALIDA, useNativeDriver: true,
    }).start();
  }, [avance, indice]);
  return (
    <Animated.View style={{
      opacity: avance,
      transform: [{ translateY: avance.interpolate({ inputRange: [0, 1], outputRange: [10, 0] }) }],
    }}>
      {children}
    </Animated.View>
  );
}

export const s = StyleSheet.create({
  texto: { color: color.c5, fontFamily: fuente.normal, fontSize: 14 },
  cifra: { color: color.c5, fontFamily: fuente.semi, letterSpacing: -1 },
  cifraDecimales: { color: color.c4, fontFamily: fuente.semi, letterSpacing: 0 },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: 7, paddingVertical: 8, paddingHorizontal: 13,
    borderRadius: radio.pildora, backgroundColor: color.velo, borderWidth: 1, borderColor: 'transparent',
  },
  chipActivo: { backgroundColor: color.c2, borderColor: color.c4 },
  chipTexto: { color: color.c4, fontFamily: fuente.media, fontSize: 13 },
  chipTextoActivo: { color: color.c5 },
  punto: { width: 7, height: 7, borderRadius: 4 },
  boton: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
    paddingVertical: 12, paddingHorizontal: 18, borderRadius: radio.pildora, backgroundColor: color.c1,
  },
  botonPrimario: { backgroundColor: color.c5, paddingVertical: 15 },
  botonTexto: { backgroundColor: 'transparent', paddingHorizontal: 8 },
  botonTextoBase: { color: color.c5, fontFamily: fuente.semi, fontSize: 14 },
  botonIcono: {
    width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: color.c1,
  },
  presionado: { opacity: 0.7 },
  encabezadoSeccion: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, marginBottom: 12 },
  seccionTitulo: { color: color.c5, fontFamily: fuente.semi, fontSize: 17 },
  seccionDetalle: { color: color.c4, fontFamily: fuente.normal, fontSize: 12, flexShrink: 1 },
  tarjeta: {
    backgroundColor: color.superficie, borderRadius: radio.grande, borderWidth: 1, borderColor: color.linea, padding: 16,
  },
});
