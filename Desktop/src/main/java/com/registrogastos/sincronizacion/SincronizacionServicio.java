package com.registrogastos.sincronizacion;

import com.registrogastos.persistencia.AjustesRepositorio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * El escritorio como servidor de sincronización en la red local. Un servidor HTTP mínimo
 * (el que trae el JDK, sin frameworks) con tres rutas:
 *
 * <pre>
 *   GET  /info       → {app, esquema}                    para que el teléfono se presente
 *   POST /emparejar  {codigo}  → {token}                 una vez, con el código del QR
 *   POST /sync       {desde, cambios} + Bearer token → {hasta, cambios, aplicadas}
 * </pre>
 *
 * El teléfono manda lo que cambió desde su última sync y recibe lo que cambió aquí desde
 * ESA misma marca de tiempo. Ambos aplican con last-writer-wins ({@link Cambios}).
 *
 * Seguridad al nivel de una app personal en tu propio Wi‑Fi: el código del QR dura mientras
 * la pantalla está abierta, el token es aleatorio y largo, y nada sale de la red local.
 */
public final class SincronizacionServicio {

    public static final int PUERTO = 7421;
    private static final String CLAVE_TOKEN = "sync_token";
    private static final String CLAVE_ULTIMA = "sync_ultima";

    private final Cambios cambios;
    private final AjustesRepositorio ajustes;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();
    private HttpServer servidor;
    private volatile String codigo;
    private volatile Consumer<Integer> alAplicar = n -> { };

    public SincronizacionServicio(Cambios cambios, AjustesRepositorio ajustes, Clock reloj) {
        this.cambios = cambios;
        this.ajustes = ajustes;
        this.reloj = reloj;
    }

    // ---- Ciclo de vida ----

    /** Arranca en el puerto fijo; si está ocupado (otra instancia), prueba el siguiente. */
    public synchronized int iniciar() throws IOException {
        if (servidor != null) {
            return servidor.getAddress().getPort();
        }
        IOException ultimo = null;
        for (int puerto = PUERTO; puerto < PUERTO + 5; puerto++) {
            try {
                servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
                break;
            } catch (IOException e) {
                ultimo = e;
            }
        }
        if (servidor == null) {
            throw ultimo;
        }
        servidor.createContext("/info", this::info);
        servidor.createContext("/emparejar", this::emparejar);
        servidor.createContext("/sync", this::sync);
        servidor.setExecutor(Executors.newSingleThreadExecutor(r -> {
            var hilo = new Thread(r, "sync-servidor");
            hilo.setDaemon(true); // no impide que la app cierre
            return hilo;
        }));
        servidor.start();
        return servidor.getAddress().getPort();
    }

    public synchronized void detener() {
        if (servidor != null) {
            servidor.stop(0);
            servidor = null;
        }
    }

    public boolean activo() {
        return servidor != null;
    }

    public int puerto() {
        return servidor == null ? PUERTO : servidor.getAddress().getPort();
    }

    /** Qué hacer cuando un teléfono aplicó cambios aquí (la UI recarga). Se llama en el hilo del servidor. */
    public void alAplicar(Consumer<Integer> accion) {
        this.alAplicar = accion;
    }

    // ---- Emparejamiento ----

    /** Un código nuevo de 6 dígitos para el QR; el anterior deja de valer. */
    public String nuevoCodigo() {
        codigo = String.format("%06d", azar.nextInt(1_000_000));
        return codigo;
    }

    public void olvidarCodigo() {
        codigo = null;
    }

    public List<String> direcciones() {
        return RedLocal.direcciones();
    }

    /** Lo que va dentro del QR: con esto el teléfono sabe adónde conectarse y cómo probar que es él. */
    public String contenidoQr() {
        var json = new JSONObject();
        json.put("app", "registro-gastos");
        json.put("ips", direcciones());
        json.put("puerto", puerto());
        json.put("codigo", codigo == null ? nuevoCodigo() : codigo);
        return json.toString();
    }

    public boolean emparejado() {
        return ajustes.leer(CLAVE_TOKEN).filter(t -> !t.isBlank()).isPresent();
    }

    public Optional<Instant> ultimaSync() {
        return ajustes.leer(CLAVE_ULTIMA).filter(v -> !v.isBlank()).map(v -> Instant.ofEpochMilli(Long.parseLong(v)));
    }

    /** Rompe el emparejamiento: los teléfonos tendrán que escanear el QR otra vez. */
    public void desemparejar() {
        ajustes.guardar(CLAVE_TOKEN, "", reloj.instant());
        ajustes.guardar(CLAVE_ULTIMA, "", reloj.instant());
    }

    // ---- Rutas ----

    private void info(HttpExchange ex) throws IOException {
        var json = new JSONObject();
        json.put("app", "registro-gastos");
        json.put("esquema", cambios.versionEsquema());
        json.put("emparejado", emparejado());
        responder(ex, 200, json);
    }

    private void emparejar(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            responder(ex, 405, error("Usa POST"));
            return;
        }
        JSONObject pedido = leer(ex);
        if (codigo == null || !codigo.equals(pedido.optString("codigo"))) {
            responder(ex, 403, error("El código no coincide. Escanea el QR de la pantalla Sincronizar."));
            return;
        }
        // Un token por emparejamiento; si ya había uno, se reutiliza para que otros teléfonos sigan funcionando
        String token = ajustes.leer(CLAVE_TOKEN).filter(t -> !t.isBlank()).orElseGet(() -> {
            byte[] bytes = new byte[24];
            azar.nextBytes(bytes);
            String nuevo = java.util.HexFormat.of().formatHex(bytes);
            ajustes.guardar(CLAVE_TOKEN, nuevo, reloj.instant());
            return nuevo;
        });
        codigo = null; // el código es de un solo uso
        var json = new JSONObject();
        json.put("token", token);
        json.put("esquema", cambios.versionEsquema());
        responder(ex, 200, json);
    }

    private void sync(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            responder(ex, 405, error("Usa POST"));
            return;
        }
        String autorizacion = ex.getRequestHeaders().getFirst("Authorization");
        String token = ajustes.leer(CLAVE_TOKEN).orElse("");
        if (token.isBlank() || autorizacion == null || !autorizacion.equals("Bearer " + token)) {
            responder(ex, 401, error("Este teléfono no está emparejado"));
            return;
        }
        JSONObject pedido = leer(ex);
        if (pedido.optInt("esquema", -1) != cambios.versionEsquema()) {
            responder(ex, 409, error("Las dos apps tienen versiones distintas de la base. Actualiza las dos."));
            return;
        }
        long desde = pedido.optLong("desde", 0);
        long ahora = reloj.millis();
        // Primero lo que viene: así lo que se devuelve ya incluye cualquier fila que ganó aquí
        int aplicadas = cambios.aplicar(pedido.optJSONObject("cambios", new JSONObject()));
        JSONObject respuesta = new JSONObject();
        respuesta.put("hasta", ahora);
        respuesta.put("cambios", cambios.desde(desde));
        respuesta.put("aplicadas", aplicadas);
        ajustes.guardar(CLAVE_ULTIMA, String.valueOf(ahora), reloj.instant());
        responder(ex, 200, respuesta);
        alAplicar.accept(aplicadas);
    }

    // ---- Utilidades HTTP ----

    private static JSONObject leer(HttpExchange ex) throws IOException {
        String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return cuerpo.isBlank() ? new JSONObject() : new JSONObject(cuerpo);
    }

    private static void responder(HttpExchange ex, int estado, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static JSONObject error(String mensaje) {
        return new JSONObject().put("error", mensaje);
    }
}
