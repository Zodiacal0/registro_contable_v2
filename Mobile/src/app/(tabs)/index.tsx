import { router } from 'expo-router';
import { useSQLiteContext } from 'expo-sqlite';
import { useEffect, useMemo, useState } from 'react';
import { Alert, Animated, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BotonIcono, Cifra, EncabezadoSeccion, Revelar, SALIDA, s as base } from '@/componentes/base';
import { Icono } from '@/componentes/Icono';
import { categorias, conversor, eliminarGasto, gastosEntre, rangoMes, type Categoria, type Gasto } from '@/db/datos';
import { useEstado, useLectura } from '@/estado';
import { dia, diasDelMes, hoyIso, monto, montoEn, nombreMes, porcentaje, tituloMes } from '@/formato';
import { color, fuente, radio } from '@/tema';

/** Resumen del mes: lo mismo que VistaResumen.java, apilado para una pantalla angosta. */
export default function Resumen() {
  const db = useSQLiteContext();
  const { recargar } = useEstado();
  const insets = useSafeAreaInsets();
  const hoy = hoyIso();
  const [anioHoy, mesHoy] = hoy.split('-').map(Number);
  const [mes, setMes] = useState({ anio: anioHoy, mes: mesHoy });
  const [diaElegido, setDiaElegido] = useState<number | null>(null);

  const datos = useLectura(async () => {
    const [desde, hasta] = rangoMes(mes.anio, mes.mes);
    const anterior = mes.mes === 1 ? { anio: mes.anio - 1, mes: 12 } : { anio: mes.anio, mes: mes.mes - 1 };
    const [desdeAnt, hastaAnt] = rangoMes(anterior.anio, anterior.mes);
    const conv = await conversor(db);
    const gastos = await gastosEntre(db, desde, hasta);
    const previos = await gastosEntre(db, desdeAnt, hastaAnt);
    return {
      gastos,
      conv,
      categorias: await categorias(db),
      total: gastos.reduce((t, g) => t + conv.aPrincipal(g), 0),
      totalAnterior: previos.reduce((t, g) => t + conv.aPrincipal(g), 0),
      anterior,
    };
  }, [mes.anio, mes.mes]);

  const enMesActual = mes.anio === anioHoy && mes.mes === mesHoy;
  const mover = (delta: number) => {
    setDiaElegido(null);
    setMes(({ anio, mes: m }) => {
      const indice = anio * 12 + (m - 1) + delta;
      return { anio: Math.floor(indice / 12), mes: (indice % 12) + 1 };
    });
  };

  return (
    <View style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 20, paddingBottom: 120, gap: 36 }}>
        <View style={st.encabezado}>
          <Text style={st.titulo}>
            {tituloMes(mes.anio, mes.mes)} <Text style={st.tituloTenue}>{mes.anio}</Text>
          </Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <BotonIcono icono="anterior" etiqueta="Mes anterior" alPulsar={() => mover(-1)} />
            <BotonIcono icono="siguiente" etiqueta="Mes siguiente" alPulsar={() => mover(1)} />
          </View>
        </View>

        {datos && (
          <>
            <Revelar indice={0}>
              <Cifra centavos={datos.total} />
              <Variacion total={datos.total} anterior={datos.totalAnterior} mesAnterior={nombreMes(datos.anterior.mes)} />
              <Text style={st.contexto}>
                {datos.gastos.length === 0 ? `Sin gastos en ${nombreMes(mes.mes)}`
                  : `${datos.gastos.length} ${datos.gastos.length === 1 ? 'gasto' : 'gastos'}`}
                {!enMesActual && datos.gastos.length > 0 ? '' : ''}
              </Text>
            </Revelar>

            <Revelar indice={1}>
              <GraficaDias gastos={datos.gastos} aPrincipal={datos.conv.aPrincipal} anio={mes.anio} mes={mes.mes}
                hoy={enMesActual ? Number(hoy.slice(8)) : null} elegido={diaElegido} alElegir={setDiaElegido} />
            </Revelar>

            <Revelar indice={2}>
              <PorCategoria gastos={datos.gastos} categorias={datos.categorias} total={datos.total}
                aPrincipal={datos.conv.aPrincipal} />
            </Revelar>

            <Revelar indice={3}>
              <Movimientos
                gastos={diaElegido ? datos.gastos.filter((g) => Number(g.fecha.slice(8)) === diaElegido) : datos.gastos}
                categorias={datos.categorias}
                hoy={hoy}
                montoDe={(g) => montoEn(g.monto_centavos, datos.conv.monedaDe(g.cuenta_id))}
                subtotal={(lista) => lista.reduce((t, g) => t + datos.conv.aPrincipal(g), 0)}
                filtrado={diaElegido !== null}
                alQuitarFiltro={() => setDiaElegido(null)}
                alEliminar={(g) => Alert.alert('Eliminar gasto', `¿Eliminar "${g.descripcion}"?`, [
                  { text: 'Cancelar', style: 'cancel' },
                  { text: 'Eliminar', style: 'destructive', onPress: async () => { await eliminarGasto(db, g.id); recargar(); } },
                ])}
              />
            </Revelar>
          </>
        )}
      </ScrollView>

      {/* La acción principal: la única píldora clara de la pantalla */}
      <Pressable accessibilityLabel="Nuevo gasto" onPress={() => router.push('/nuevo')}
        style={({ pressed }) => [st.fab, { bottom: 20 }, pressed && { transform: [{ scale: 0.96 }] }]}>
        <Icono nombre="mas" tamano={18} color={color.c0} />
        <Text style={st.fabTexto}>Nuevo gasto</Text>
      </Pressable>
    </View>
  );
}

/** El triángulo da la dirección; el color se queda en la paleta (sin rojo ni verde). */
function Variacion({ total, anterior, mesAnterior }: { total: number; anterior: number; mesAnterior: string }) {
  if (total === 0) {
    return null;
  }
  if (anterior === 0) {
    return <Text style={[st.contexto, { marginTop: 10 }]}>Sin gastos en {mesAnterior} para comparar</Text>;
  }
  const cambio = ((total - anterior) / anterior) * 100;
  const sube = cambio > 0;
  return (
    <View style={st.variacionFila}>
      <View style={[st.variacion, sube ? st.variacionSube : st.variacionBaja]}>
        <Icono nombre={sube ? 'sube' : 'baja'} tamano={9} color={sube ? color.c5 : color.c4} />
        <Text style={[st.variacionTexto, { color: sube ? color.c5 : color.c4 }]}>{Math.abs(cambio).toFixed(1)}%</Text>
      </View>
      <Text style={st.contexto}>{sube ? 'más' : 'menos'} que {mesAnterior} · {monto(anterior)}</Text>
    </View>
  );
}

/** Barras por día que crecen al aparecer; tocar una filtra los movimientos de ese día. */
function GraficaDias({ gastos, aPrincipal, anio, mes, hoy, elegido, alElegir }: {
  gastos: Gasto[]; aPrincipal: (g: Gasto) => number; anio: number; mes: number; hoy: number | null;
  elegido: number | null; alElegir: (dia: number | null) => void;
}) {
  const dias = diasDelMes(anio, mes);
  const porDia = useMemo(() => {
    const lista = new Array<number>(dias).fill(0);
    gastos.forEach((g) => { lista[Number(g.fecha.slice(8)) - 1] += aPrincipal(g); });
    return lista;
  }, [gastos, aPrincipal, dias]);
  const maximo = Math.max(...porDia, 1);
  const [crecer] = useState(() => new Animated.Value(0));
  useEffect(() => {
    crecer.setValue(0);
    Animated.timing(crecer, { toValue: 1, duration: 700, easing: SALIDA, useNativeDriver: true }).start();
  }, [crecer, gastos]);

  const lectura = elegido
    ? `Día ${elegido} · ${porDia[elegido - 1] === 0 ? 'sin gastos' : monto(porDia[elegido - 1])} · toca de nuevo para ver todo`
    : 'Toca un día para ver sus gastos';
  return (
    <View>
      <Text style={st.lectura}>{lectura}</Text>
      <View style={st.grafica}>
        {porDia.map((valor, i) => {
          const numero = i + 1;
          const activo = elegido === numero;
          const esHoy = hoy === numero;
          return (
            <Pressable key={numero} style={st.columna} hitSlop={{ top: 20, bottom: 10 }}
              onPress={() => alElegir(activo ? null : numero)}>
              <Animated.View style={[st.barra, {
                height: `${Math.max(valor > 0 ? 4 : 2, (valor / maximo) * 100)}%`,
                backgroundColor: activo || esHoy ? color.c5 : valor > 0 ? color.c3 : color.c1,
                opacity: elegido && !activo ? 0.45 : 1,
                transform: [{ scaleY: crecer }],
              }]} />
            </Pressable>
          );
        })}
      </View>
      <View style={st.ejeDias}>
        {[1, 8, 15, 22, 29].filter((d) => d <= dias).map((d) => <Text key={d} style={st.ejeTexto}>{d}</Text>)}
      </View>
    </View>
  );
}

function PorCategoria({ gastos, categorias, total, aPrincipal }: {
  gastos: Gasto[]; categorias: Categoria[]; total: number; aPrincipal: (g: Gasto) => number;
}) {
  const grupos = useMemo(() => {
    const porId = new Map(categorias.map((c) => [c.id, c]));
    const sumas = new Map<string, { categoria: Categoria | null; total: number; cuantos: number }>();
    for (const g of gastos) {
      const categoria = (g.categoria_id && porId.get(g.categoria_id)) || null;
      const clave = categoria?.id ?? '';
      const actual = sumas.get(clave) ?? { categoria, total: 0, cuantos: 0 };
      actual.total += aPrincipal(g);
      actual.cuantos += 1;
      sumas.set(clave, actual);
    }
    return [...sumas.values()].sort((a, b) => b.total - a.total);
  }, [gastos, categorias, aPrincipal]);

  if (grupos.length === 0) {
    return null;
  }
  return (
    <View>
      <EncabezadoSeccion titulo="Por categoría" />
      <View style={st.distribucion}>
        {grupos.map((g) => (
          <View key={g.categoria?.id ?? 'sin'} style={{
            flex: g.total, minWidth: 4, borderRadius: radio.pildora,
            backgroundColor: g.categoria?.color ?? color.sinCategoria,
          }} />
        ))}
      </View>
      <View style={{ gap: 12, marginTop: 16 }}>
        {grupos.map((g) => (
          <View key={g.categoria?.id ?? 'sin'} style={st.leyenda}>
            <View style={[st.muestra, { backgroundColor: g.categoria?.color ?? color.sinCategoria }]} />
            <View style={{ flex: 1 }}>
              <Text style={st.leyendaNombre}>{g.categoria?.nombre ?? 'Sin categoría'}</Text>
              <Text style={st.leyendaDetalle}>{g.cuantos} {g.cuantos === 1 ? 'gasto' : 'gastos'}</Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={st.leyendaMonto}>{monto(g.total)}</Text>
              <Text style={st.leyendaDetalle}>{porcentaje(g.total / total)}</Text>
            </View>
          </View>
        ))}
      </View>
    </View>
  );
}

/** Lista agrupada por día, con el subtotal del día (como las listas agrupadas de Apple). */
function Movimientos({ gastos, categorias, hoy, montoDe, subtotal, filtrado, alQuitarFiltro, alEliminar }: {
  gastos: Gasto[]; categorias: Categoria[]; hoy: string; montoDe: (g: Gasto) => string;
  subtotal: (lista: Gasto[]) => number; filtrado: boolean; alQuitarFiltro: () => void; alEliminar: (g: Gasto) => void;
}) {
  const porId = new Map(categorias.map((c) => [c.id, c]));
  const dias = useMemo(() => {
    const mapa = new Map<string, Gasto[]>();
    gastos.forEach((g) => mapa.set(g.fecha, [...(mapa.get(g.fecha) ?? []), g]));
    return [...mapa.entries()];
  }, [gastos]);

  return (
    <View>
      <EncabezadoSeccion titulo="Movimientos" detalle={filtrado ? 'Filtrado por día' : `${gastos.length}`} />
      {filtrado && (
        <Pressable onPress={alQuitarFiltro} style={{ marginBottom: 12 }}>
          <Text style={[st.leyendaDetalle, { color: color.c5 }]}>Quitar filtro</Text>
        </Pressable>
      )}
      {dias.length === 0 && (
        <View style={base.tarjeta}><Text style={st.leyendaDetalle}>Nada registrado. Toca “Nuevo gasto” para empezar.</Text></View>
      )}
      <View style={{ gap: 18 }}>
        {dias.map(([fecha, lista]) => (
          <View key={fecha}>
            <View style={st.cabeceraDia}>
              <Text style={st.dia}>{dia(fecha, hoy)}</Text>
              <Text style={st.diaTotal}>{monto(subtotal(lista))}</Text>
            </View>
            <View style={st.tabla}>
              {lista.map((g, i) => {
                const categoria = g.categoria_id ? porId.get(g.categoria_id) : undefined;
                return (
                  <Pressable key={g.id} onLongPress={() => alEliminar(g)}
                    style={({ pressed }) => [st.fila, i > 0 && st.filaBorde, pressed && { backgroundColor: color.velo }]}>
                    <View style={[st.punto, { backgroundColor: categoria?.color ?? color.sinCategoria }]} />
                    <View style={{ flex: 1 }}>
                      <Text style={st.filaNombre} numberOfLines={1}>{g.descripcion}</Text>
                      <Text style={st.leyendaDetalle}>
                        {categoria?.nombre ?? 'Sin categoría'}{g.recurrencia_id ? ' · recurrente' : ''}{g.deuda_id ? ' · abono' : ''}
                      </Text>
                    </View>
                    <Text style={st.filaMonto}>{montoDe(g)}</Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        ))}
      </View>
      {gastos.length > 0 && <Text style={[st.leyendaDetalle, { marginTop: 14 }]}>Mantén presionado un gasto para eliminarlo.</Text>}
    </View>
  );
}

const st = StyleSheet.create({
  encabezado: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  titulo: { color: color.c5, fontFamily: fuente.semi, fontSize: 30 },
  tituloTenue: { color: color.c3 },
  contexto: { color: color.c4, fontFamily: fuente.normal, fontSize: 13, marginTop: 8 },
  variacionFila: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 10 },
  variacion: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 4, paddingHorizontal: 10, borderRadius: radio.pildora },
  variacionSube: { backgroundColor: 'rgba(226, 221, 231, 0.12)' },
  variacionBaja: { backgroundColor: color.c1 },
  variacionTexto: { fontFamily: fuente.semi, fontSize: 12 },
  lectura: { color: color.c4, fontFamily: fuente.normal, fontSize: 12, marginBottom: 12 },
  grafica: { flexDirection: 'row', alignItems: 'flex-end', height: 120, gap: 3 },
  columna: { flex: 1, height: '100%', justifyContent: 'flex-end' },
  barra: { borderRadius: 3, transformOrigin: 'bottom' },
  ejeDias: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 },
  ejeTexto: { color: color.texto3, fontFamily: fuente.mono, fontSize: 11 },
  distribucion: { flexDirection: 'row', height: 12, gap: 2 },
  leyenda: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  muestra: { width: 10, height: 10, borderRadius: 3 },
  leyendaNombre: { color: color.c5, fontFamily: fuente.media, fontSize: 14 },
  leyendaDetalle: { color: color.texto3, fontFamily: fuente.normal, fontSize: 12 },
  leyendaMonto: { color: color.c5, fontFamily: fuente.mono, fontSize: 14 },
  cabeceraDia: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8, paddingHorizontal: 4 },
  dia: { color: color.c4, fontFamily: fuente.media, fontSize: 13 },
  diaTotal: { color: color.c4, fontFamily: fuente.mono, fontSize: 13 },
  tabla: { backgroundColor: color.superficie, borderRadius: radio.grande, borderWidth: 1, borderColor: color.linea, overflow: 'hidden' },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 13, paddingHorizontal: 16 },
  filaBorde: { borderTopWidth: 1, borderTopColor: color.linea },
  punto: { width: 8, height: 8, borderRadius: 4 },
  filaNombre: { color: color.c5, fontFamily: fuente.media, fontSize: 15 },
  filaMonto: { color: color.c5, fontFamily: fuente.mono, fontSize: 15 },
  fab: {
    position: 'absolute', right: 20, flexDirection: 'row', alignItems: 'center', gap: 8,
    backgroundColor: color.c5, paddingVertical: 15, paddingHorizontal: 22, borderRadius: radio.pildora,
    elevation: 6,
  },
  fabTexto: { color: color.c0, fontFamily: fuente.semi, fontSize: 15 },
});
