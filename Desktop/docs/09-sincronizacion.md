# 09 · Sincronización con el teléfono (QR, red local)

## Qué hace

La PC y el teléfono se sincronizan directo por tu Wi‑Fi, sin nube ni cuentas.

1. En la PC, vista **Sincronizar** (riel): muestra un **QR**.
2. En el teléfono, **Sincronizar → Escanear QR de la PC**.
3. Quedan emparejados. Desde ahí el teléfono sincroniza solo cada vez que abres la app (o
   vuelves a ella) y con el botón "Sincronizar ahora".

## Cómo funciona

```
Teléfono (cliente)                         PC (servidor HTTP, puerto 7421)
  escanea QR {ips, puerto, codigo} ─────▶
  GET  /info            (prueba cada IP)   ◀── {app, esquema}
  POST /emparejar {codigo}             ───▶ token (y el código deja de valer)
  POST /sync {esquema, desde, cambios} ───▶ aplica lo del teléfono
                                       ◀─── {hasta, cambios de la PC}
  aplica lo de la PC
```

- **El servidor es el que trae el JDK** (`com.sun.net.httpserver.HttpServer`): sin
  frameworks. Arranca con la app, en un hilo *daemon* (no impide cerrar), y se detiene en
  `App.stop()`. Si el puerto 7421 está ocupado prueba los 4 siguientes.
- **El QR** lleva las IPs privadas de la PC (`RedLocal` deja al final las de VirtualBox,
  Hyper‑V o VPN, que el teléfono no alcanza), el puerto y un **código de 6 dígitos de un
  solo uso**. Se dibuja a mano con ZXing sobre un `WritableImage`, en los colores de la
  paleta.
- **El token** (48 caracteres aleatorios de `SecureRandom`) se guarda en `ajustes` y va en
  cada `/sync` como `Authorization: Bearer`. "Olvidar teléfonos" lo borra.

## El feed de cambios (`Cambios.java` y `Mobile/src/sync/cambios.ts`)

Todo el esquema se diseñó para esto desde la fase 1: ids UUID, `actualizado_en` en cada fila
y borrado lógico. Por eso sincronizar es genérico:

- **Qué cambió:** `SELECT * FROM tabla WHERE actualizado_en > desde`, en cada tabla de la
  lista `TABLAS`. Las columnas se leen con `PRAGMA table_info`, así que una columna nueva
  sincroniza sin tocar este código. Una tabla nueva solo hay que agregarla a la lista (en los
  DOS lados).
- **Aplicar:** un UPSERT con **gana la última escritura** (last-writer-wins):
  `INSERT ... ON CONFLICT(id) DO UPDATE SET ... WHERE excluded.actualizado_en > tabla.actualizado_en`.
  Si la fila local es más nueva, el UPDATE no ocurre. Todo en una transacción: o entra todo o
  nada.
- **Los borrados viajan** como una fila más (`eliminado = 1`) y no "resucitan".
- **El orden de `TABLAS` importa:** las claves foráneas están activas, así que las tablas
  padre (categorías, cuentas, recurrencias...) van antes que los gastos.
- **Ajustes locales:** el token, la dirección de la PC y las marcas de tiempo de sync son de
  cada aparato y nunca viajan (`AJUSTES_LOCALES`, la misma lista en los dos lados). El resto
  de ajustes (tipo de cambio, sueldo, pago quincenal) sí.
- **Los cobros recurrentes no se duplican** aunque los dos aparatos los generen: su id es
  determinístico (fase 5), así que la PC y el teléfono producen el mismo UUID para "Netflix
  del 5 de noviembre".

### Dos relojes, dos marcas

Cada aparato tiene su propio reloj y pueden no coincidir. Por eso el teléfono guarda dos
marcas:

- `sync_enviado` (reloj del teléfono): desde cuándo mandar sus filas.
- `sync_recibido` (reloj de la PC, el `hasta` que ella devolvió): lo que le pide a la PC.

Con una sola marca, si el teléfono va unos minutos atrasado, un gasto anotado justo después
de sincronizar quedaría "antes" de la marca y no viajaría nunca.

### Misma versión de esquema

El teléfono usa **las mismas migraciones** que la PC: `Mobile/scripts/copiar-migraciones.mjs`
las copia de `Desktop/src/main/resources/db/migraciones` (corre solo antes de compilar). Si
las versiones no coinciden, `/sync` responde 409 y el teléfono pide actualizar las dos apps.

## Seguridad (al nivel de una app personal en tu Wi‑Fi)

- Nada sale de la red local: HTTP simple entre tu PC y tu teléfono. Android bloquea HTTP sin
  cifrar por defecto; se habilita con `usesCleartextTraffic` (expo-build-properties).
- Sin el token, `/sync` responde 401. El código del QR es de un solo uso y cambia cada vez
  que entras a la vista.
- No hay cifrado: alguien en tu misma red que capture el tráfico podría ver los datos. Para
  una red de casa es aceptable; si sincronizas en redes públicas, el siguiente paso sería TLS
  con un certificado autofirmado anclado en el QR.

## Firewall de Windows

La primera vez que la app abre el puerto, Windows pregunta si permite a **Java** en la red.
Marca **redes privadas** y acepta; si no, el teléfono no llega a la PC (el teléfono lo dice:
"No se pudo llegar a la PC").

## Pruebas

- `CambiosTest` (4): dos bases que se intercambian cambios; gana el más reciente; los
  borrados viajan y no resucitan; solo viaja lo nuevo y los ajustes locales no.
- `SincronizacionServicioTest` (5): el servidor real por HTTP real, haciendo de teléfono:
  el QR, emparejar (código de un solo uso), 401 sin token, una sync en los dos sentidos y
  409 si las versiones no coinciden.

Total: 76.
