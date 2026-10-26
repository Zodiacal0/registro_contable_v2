import { CameraView, useCameraPermissions } from 'expo-camera';
import { router } from 'expo-router';
import { useSQLiteContext } from 'expo-sqlite';
import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Boton, BotonIcono } from '@/componentes/base';
import { useEstado } from '@/estado';
import { emparejar, sincronizar } from '@/sync/cliente';
import { color, fuente, radio } from '@/tema';

/** Escanea el QR de la pantalla Sincronizar del escritorio, empareja y hace la primera sync. */
export default function Escanear() {
  const db = useSQLiteContext();
  const { recargar } = useEstado();
  const insets = useSafeAreaInsets();
  const [permiso, pedirPermiso] = useCameraPermissions();
  const [estado, setEstado] = useState<string>('Apunta al código QR de la PC');
  const procesando = useRef(false); // la cámara avisa muchas veces por segundo: se atiende solo la primera

  const alEscanear = async (datos: string) => {
    if (procesando.current) {
      return;
    }
    procesando.current = true;
    try {
      setEstado('Conectando con la PC…');
      const host = await emparejar(db, datos);
      setEstado(`Emparejado con ${host}. Sincronizando…`);
      await sincronizar(db);
      recargar();
      router.back();
    } catch (e) {
      setEstado(e instanceof Error ? e.message : String(e));
      // Deja volver a intentar en un momento (por ejemplo, tras permitir el firewall)
      setTimeout(() => { procesando.current = false; }, 2500);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: color.c0 }}>
      {permiso?.granted ? (
        <CameraView style={StyleSheet.absoluteFill} facing="back"
          barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
          onBarcodeScanned={({ data }) => alEscanear(data)} />
      ) : (
        <View style={st.sinPermiso}>
          <Text style={st.texto}>Para emparejar hace falta la cámara: solo se usa para leer el QR.</Text>
          {permiso && <Boton texto="Permitir la cámara" tipo="primario" alPulsar={pedirPermiso} />}
        </View>
      )}

      {/* Marco de enfoque sobre la cámara */}
      {permiso?.granted && <View pointerEvents="none" style={st.marco} />}

      <View style={[st.barra, { paddingTop: insets.top + 12 }]}>
        <BotonIcono icono="anterior" etiqueta="Volver" alPulsar={() => router.back()} />
      </View>
      <View style={[st.estado, { bottom: insets.bottom + 32 }]}>
        <Text style={st.texto}>{estado}</Text>
      </View>
    </View>
  );
}

const st = StyleSheet.create({
  sinPermiso: { flex: 1, justifyContent: 'center', padding: 32, gap: 20 },
  marco: {
    position: 'absolute', alignSelf: 'center', top: '30%', width: 240, height: 240,
    borderRadius: 28, borderWidth: 2, borderColor: color.c5,
  },
  barra: { position: 'absolute', left: 20, right: 20, flexDirection: 'row' },
  estado: {
    position: 'absolute', left: 20, right: 20, backgroundColor: 'rgba(23, 21, 28, 0.88)',
    borderRadius: radio.grande, padding: 16, borderWidth: 1, borderColor: color.linea,
  },
  texto: { color: color.c5, fontFamily: fuente.media, fontSize: 14, textAlign: 'center', lineHeight: 20 },
});
