import { Tabs } from 'expo-router';

import { Icono } from '@/componentes/Icono';
import { color, fuente } from '@/tema';

/** La barra inferior es el "riel" del escritorio en versión teléfono. */
export default function Pestanas() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        sceneStyle: { backgroundColor: color.c0 },
        tabBarStyle: { backgroundColor: color.riel, borderTopColor: color.linea, height: 64, paddingTop: 6 },
        tabBarActiveTintColor: color.c5,
        tabBarInactiveTintColor: color.c3,
        tabBarLabelStyle: { fontFamily: fuente.media, fontSize: 11 },
      }}>
      <Tabs.Screen
        name="index"
        options={{ title: 'Resumen', tabBarIcon: ({ color: c }) => <Icono nombre="resumen" tamano={20} color={String(c)} /> }}
      />
      <Tabs.Screen
        name="sincronizar"
        options={{ title: 'Sincronizar', tabBarIcon: ({ color: c }) => <Icono nombre="sincronizar" tamano={20} color={String(c)} /> }}
      />
    </Tabs>
  );
}
