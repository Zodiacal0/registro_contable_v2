import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { SQLiteProvider } from 'expo-sqlite';
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { migrar } from '@/db/migrar';
import { ProveedorEstado } from '@/estado';
import { color } from '@/tema';

/**
 * Raíz: fuentes, la base (migrada antes de pintar nada, en onInit) y el estado compartido.
 * Las mismas fuentes del escritorio: Figtree para el texto, IBM Plex Mono para las cifras.
 */
export default function Raiz() {
  const [fuentesListas] = useFonts({
    Figtree: require('../../assets/fuentes/Figtree-400.ttf'),
    'Figtree-Medium': require('../../assets/fuentes/Figtree-500.ttf'),
    'Figtree-SemiBold': require('../../assets/fuentes/Figtree-600.ttf'),
    'Mono-Medium': require('../../assets/fuentes/IBMPlexMono-500.ttf'),
    'Mono-SemiBold': require('../../assets/fuentes/IBMPlexMono-600.ttf'),
  });
  if (!fuentesListas) {
    return <View style={{ flex: 1, backgroundColor: color.c0 }} />;
  }
  return (
    <SQLiteProvider databaseName="gastos.db" onInit={migrar}>
      <ProveedorEstado>
        <StatusBar style="light" />
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: color.c0 },
            animation: 'fade_from_bottom',
          }}>
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="nuevo" options={{ presentation: 'modal', animation: 'slide_from_bottom' }} />
          <Stack.Screen name="escanear" options={{ animation: 'slide_from_right' }} />
        </Stack>
      </ProveedorEstado>
    </SQLiteProvider>
  );
}
