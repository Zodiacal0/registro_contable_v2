import { router } from 'expo-router';
import { useSQLiteContext } from 'expo-sqlite';
import { useState } from 'react';
import { KeyboardAvoidingView, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Boton, BotonIcono, Chip } from '@/componentes/base';
import { CUENTA_PRINCIPAL, categorias, cuentas, registrarGasto } from '@/db/datos';
import { useEstado, useLectura } from '@/estado';
import { dia, hoyIso, leerMonto, sumarDias } from '@/formato';
import { color, fuente, radio } from '@/tema';

/** El panel "Nuevo gasto" del escritorio, como hoja que sube desde abajo. */
export default function NuevoGasto() {
  const db = useSQLiteContext();
  const { recargar } = useEstado();
  const insets = useSafeAreaInsets();
  const hoy = hoyIso();
  const [montoTexto, setMontoTexto] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [fecha, setFecha] = useState(hoy);
  const [categoriaId, setCategoriaId] = useState<string | null>(null);
  const [cuentaId, setCuentaId] = useState(CUENTA_PRINCIPAL);
  const [error, setError] = useState<string | null>(null);

  const opciones = useLectura(async () => ({ categorias: await categorias(db), cuentas: await cuentas(db) }), []);
  const moneda = opciones?.cuentas.find((c) => c.id === cuentaId)?.moneda ?? 'GTQ';

  const guardar = async () => {
    try {
      const centavos = leerMonto(montoTexto);
      const categoria = opciones?.categorias.find((c) => c.id === categoriaId);
      // La descripción es opcional: sin ella, el gasto se llama como su categoría (igual que en la PC)
      const texto = descripcion.trim() || categoria?.nombre || 'Gasto';
      await registrarGasto(db, { descripcion: texto, montoCentavos: centavos, fecha, categoriaId, cuentaId });
      recargar();
      router.back();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const fechas = [hoy, sumarDias(hoy, -1), sumarDias(hoy, -2)];
  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: color.panel }} behavior="padding">
      <ScrollView keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ padding: 20, paddingTop: insets.top + 12, paddingBottom: insets.bottom + 24, gap: 22 }}>
        <View style={st.encabezado}>
          <Text style={st.titulo}>Nuevo gasto</Text>
          <BotonIcono icono="cerrar" etiqueta="Cerrar" alPulsar={() => router.back()} />
        </View>

        <Pressable style={[st.cajaMonto, error && /monto/i.test(error) && st.cajaError]}>
          <Text style={st.etiqueta}>{moneda === 'GTQ' ? 'Monto' : 'Monto en dólares'}</Text>
          <TextInput
            value={montoTexto}
            onChangeText={(t) => { setMontoTexto(t); setError(null); }}
            placeholder="0.00"
            placeholderTextColor={color.c3}
            keyboardType="decimal-pad"
            autoFocus
            style={st.campoMonto}
          />
        </Pressable>
        {error && <Text style={st.error}>{error}</Text>}

        <View style={{ gap: 8 }}>
          <Text style={st.etiqueta}>Descripción</Text>
          <TextInput value={descripcion} onChangeText={setDescripcion} placeholder="¿En qué fue? (opcional)"
            placeholderTextColor={color.c3} style={st.campo} returnKeyType="done" onSubmitEditing={guardar} />
        </View>

        <View style={{ gap: 8 }}>
          <Text style={st.etiqueta}>Fecha</Text>
          <View style={st.chips}>
            {fechas.map((f) => <Chip key={f} texto={dia(f, hoy)} activo={fecha === f} alPulsar={() => setFecha(f)} />)}
          </View>
        </View>

        {opciones && opciones.cuentas.length > 1 && (
          <View style={{ gap: 8 }}>
            <Text style={st.etiqueta}>Sale de</Text>
            <View style={st.chips}>
              {opciones.cuentas.map((c) => (
                <Chip key={c.id} icono="cuenta" texto={c.moneda === 'GTQ' ? c.nombre : `${c.nombre} · US$`}
                  activo={cuentaId === c.id} alPulsar={() => setCuentaId(c.id)} />
              ))}
            </View>
          </View>
        )}

        <View style={{ gap: 8 }}>
          <Text style={st.etiqueta}>Categoría</Text>
          <View style={st.chips}>
            <Chip texto="Sin categoría" punto={color.sinCategoria} activo={categoriaId === null} alPulsar={() => setCategoriaId(null)} />
            {opciones?.categorias.map((c) => (
              <Chip key={c.id} texto={c.nombre} punto={c.color} activo={categoriaId === c.id} alPulsar={() => setCategoriaId(c.id)} />
            ))}
          </View>
        </View>

        <Boton texto="Registrar gasto" tipo="primario" alPulsar={guardar} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  encabezado: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  titulo: { color: color.c5, fontFamily: fuente.semi, fontSize: 22 },
  cajaMonto: { backgroundColor: color.c0, borderRadius: radio.grande, borderWidth: 1.5, borderColor: color.c3, padding: 18, gap: 4 },
  cajaError: { borderColor: color.c5 },
  etiqueta: { color: color.c4, fontFamily: fuente.media, fontSize: 12 },
  campoMonto: { color: color.c5, fontFamily: fuente.semi, fontSize: 44, padding: 0 },
  campo: {
    color: color.c5, fontFamily: fuente.normal, fontSize: 15, backgroundColor: color.c0, borderRadius: 12,
    borderWidth: 1, borderColor: color.linea, paddingHorizontal: 14, paddingVertical: 12,
  },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  error: { color: color.c5, fontFamily: fuente.media, fontSize: 13, marginTop: -12 },
});
