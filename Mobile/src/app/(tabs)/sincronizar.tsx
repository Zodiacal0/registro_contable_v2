import { router } from 'expo-router';
import { useSQLiteContext } from 'expo-sqlite';
import { useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Boton, EncabezadoSeccion, Revelar, Tarjeta } from '@/componentes/base';
import { useEstado, useLectura } from '@/estado';
import { hace } from '@/formato';
import { estado, olvidar, sincronizar } from '@/sync/cliente';
import { color, fuente } from '@/tema';

/** Sincronizar con la PC: emparejar escaneando su QR y sincronizar a mano o solo. */
export default function Sincronizar() {
  const db = useSQLiteContext();
  const { recargar, sincronizando, ultimoError } = useEstado();
  const insets = useSafeAreaInsets();
  const [ocupado, setOcupado] = useState(false);
  const [mensaje, setMensaje] = useState<string | null>(null);
  const info = useLectura(() => estado(db), []);

  const ahora = async () => {
    setOcupado(true);
    setMensaje(null);
    try {
      const { enviadas, recibidas } = await sincronizar(db);
      setMensaje(`Listo: enviaste ${enviadas} ${enviadas === 1 ? 'cambio' : 'cambios'} y llegaron ${recibidas}.`);
      recargar();
    } catch (e) {
      setMensaje(e instanceof Error ? e.message : String(e));
    } finally {
      setOcupado(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 20, paddingBottom: 40, gap: 32 }}>
      <Text style={st.titulo}>Sincronizar <Text style={st.tenue}>con la PC</Text></Text>

      {info && (
        <Revelar indice={0}>
          {info.host ? (
            <Tarjeta estilo={{ gap: 14 }}>
              <Text style={st.estado}>Emparejado con la PC</Text>
              <Text style={st.detalle}>
                {info.host} · {info.ultima ? `última sincronización ${hace(info.ultima)}` : 'todavía sin sincronizar'}
              </Text>
              {(mensaje ?? ultimoError) && <Text style={st.aviso}>{mensaje ?? ultimoError}</Text>}
              <Boton texto={ocupado || sincronizando ? 'Sincronizando…' : 'Sincronizar ahora'} tipo="primario"
                icono="repetir" alPulsar={ahora} deshabilitado={ocupado || sincronizando} />
              <View style={{ flexDirection: 'row', gap: 4 }}>
                <Boton texto="Escanear otra PC" tipo="texto" alPulsar={() => router.push('/escanear')} />
                <Boton texto="Olvidar" tipo="texto" alPulsar={() => Alert.alert('Olvidar la PC',
                  'Tendrás que escanear su QR otra vez. Tus datos se quedan en el teléfono.', [
                    { text: 'Cancelar', style: 'cancel' },
                    { text: 'Olvidar', style: 'destructive', onPress: async () => { await olvidar(db); recargar(); } },
                  ])} />
              </View>
            </Tarjeta>
          ) : (
            <Tarjeta estilo={{ gap: 16 }}>
              <Text style={st.estado}>Sin emparejar</Text>
              <Paso numero="1" texto="Conecta el teléfono al mismo Wi‑Fi que la PC." />
              <Paso numero="2" texto="En la PC, abre Registro de Gastos y entra a Sincronizar: verás un código QR." />
              <Paso numero="3" texto="Escanéalo aquí. Desde ese momento se sincronizan solos al abrir la app." />
              <Boton texto="Escanear QR de la PC" tipo="primario" icono="sincronizar" alPulsar={() => router.push('/escanear')} />
            </Tarjeta>
          )}
        </Revelar>
      )}

      <Revelar indice={1}>
        <EncabezadoSeccion titulo="Cómo funciona" />
        <Text style={st.detalle}>
          Cada aparato guarda cuándo cambió cada cosa. Al sincronizar se intercambian solo los cambios
          desde la última vez y, si los dos tocaron lo mismo, gana el más reciente. Los datos viajan
          directo por tu Wi‑Fi: no hay nube ni cuentas.
        </Text>
      </Revelar>
    </ScrollView>
  );
}

function Paso({ numero, texto }: { numero: string; texto: string }) {
  return (
    <View style={{ flexDirection: 'row', gap: 12 }}>
      <Text style={st.numero}>{numero}</Text>
      <Text style={[st.detalle, { flex: 1, color: color.c4 }]}>{texto}</Text>
    </View>
  );
}

const st = StyleSheet.create({
  titulo: { color: color.c5, fontFamily: fuente.semi, fontSize: 30 },
  tenue: { color: color.c3 },
  estado: { color: color.c5, fontFamily: fuente.semi, fontSize: 17 },
  detalle: { color: color.texto3, fontFamily: fuente.normal, fontSize: 13, lineHeight: 19 },
  aviso: { color: color.c5, fontFamily: fuente.media, fontSize: 13, backgroundColor: color.velo, borderRadius: 10, padding: 12 },
  numero: { color: color.c4, fontFamily: fuente.monoSemi, fontSize: 13, width: 14 },
});
