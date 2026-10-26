import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { useSQLiteContext } from 'expo-sqlite';

import { estado as estadoSync, sincronizar } from '@/sync/cliente';

/**
 * Una sola señal de "cambiaron los datos", como recargar() en ModeloUi.java: cada pantalla
 * vuelve a leer cuando sube `version`. También sincroniza solo al abrir la app y al volver a
 * ella, si el teléfono ya está emparejado con la PC.
 */
type Estado = {
  version: number;
  recargar: () => void;
  sincronizando: boolean;
  /** Último resultado de una sync automática (para mostrarlo en Sincronizar). */
  ultimoError: string | null;
};

const Contexto = createContext<Estado | null>(null);

export function ProveedorEstado({ children }: { children: ReactNode }) {
  const db = useSQLiteContext();
  const [version, setVersion] = useState(0);
  const [sincronizando, setSincronizando] = useState(false);
  const [ultimoError, setUltimoError] = useState<string | null>(null);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  const sincronizarSolo = useCallback(async () => {
    const { host } = await estadoSync(db);
    if (!host) {
      return;
    }
    setSincronizando(true);
    try {
      const { recibidas } = await sincronizar(db);
      setUltimoError(null);
      if (recibidas > 0) {
        recargar();
      }
    } catch (e) {
      // Sin Wi‑Fi o con la PC apagada es normal: no se interrumpe al usuario, se muestra en Sincronizar
      setUltimoError(e instanceof Error ? e.message : String(e));
    } finally {
      setSincronizando(false);
    }
  }, [db, recargar]);

  useEffect(() => {
    // Al abrir la app (en el siguiente ciclo, no durante el render) y cada vez que se vuelve a ella
    const inicial = setTimeout(sincronizarSolo, 0);
    const suscripcion = AppState.addEventListener('change', (ahora) => {
      if (ahora === 'active') {
        sincronizarSolo();
      }
    });
    return () => {
      clearTimeout(inicial);
      suscripcion.remove();
    };
  }, [sincronizarSolo]);

  return (
    <Contexto.Provider value={{ version, recargar, sincronizando, ultimoError }}>{children}</Contexto.Provider>
  );
}

export function useEstado(): Estado {
  const valor = useContext(Contexto);
  if (!valor) {
    throw new Error('useEstado fuera de ProveedorEstado');
  }
  return valor;
}

/**
 * Corre una lectura cada vez que cambian los datos (o sus dependencias) y devuelve el
 * resultado; null mientras carga la primera vez.
 */
export function useLectura<T>(leer: () => Promise<T>, dependencias: unknown[]): T | null {
  const { version } = useEstado();
  const [valor, setValor] = useState<T | null>(null);
  useEffect(() => {
    let vigente = true;
    leer().then((v) => {
      if (vigente) {
        setValor(v);
      }
    });
    return () => {
      vigente = false; // si la pantalla cambió antes de que termine, el resultado viejo se descarta
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version, ...dependencias]);
  return valor;
}
