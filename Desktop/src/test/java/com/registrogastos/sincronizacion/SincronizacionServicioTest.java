package com.registrogastos.sincronizacion;

import com.registrogastos.persistencia.AjustesRepositorio;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.servicio.GastoServicio;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El servidor de verdad, por HTTP de verdad: esta prueba hace lo mismo que el teléfono
 * (Mobile/src/sync/cliente.ts) para verificar el protocolo de punta a punta.
 */
class SincronizacionServicioTest {

    @TempDir
    Path carpeta;

    private SincronizacionServicio servidor;
    private GastoServicio gastos;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void preparar() throws Exception {
        var bd = new BaseDeDatos(carpeta.resolve("pc.db"));
        new Migrador(bd).migrar();
        Clock reloj = Clock.systemUTC();
        gastos = new GastoServicio(new GastoRepositorio(bd), new CategoriaRepositorio(bd), reloj);
        servidor = new SincronizacionServicio(new Cambios(bd), new AjustesRepositorio(bd), reloj);
        base = "http://127.0.0.1:" + servidor.iniciar();
    }

    @AfterEach
    void cerrar() {
        servidor.detener();
    }

    private HttpResponse<String> post(String ruta, JSONObject cuerpo, String token) throws Exception {
        var pedido = HttpRequest.newBuilder(URI.create(base + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void elQrLlevaIpsPuertoYCodigo() {
        var qr = new JSONObject(servidor.contenidoQr());
        assertEquals("registro-gastos", qr.getString("app"));
        assertEquals(servidor.puerto(), qr.getInt("puerto"));
        assertEquals(6, qr.getString("codigo").length());
    }

    @Test
    void emparejarConElCodigoCorrectoDaUnTokenYElCodigoNoSeReutiliza() throws Exception {
        String codigo = servidor.nuevoCodigo();
        assertEquals(403, post("/emparejar", new JSONObject().put("codigo", "000000x"), null).statusCode());

        var ok = post("/emparejar", new JSONObject().put("codigo", codigo), null);
        assertEquals(200, ok.statusCode());
        assertTrue(new JSONObject(ok.body()).getString("token").length() >= 32);
        assertTrue(servidor.emparejado());
        // De un solo uso: escanear la misma foto del QR otra vez ya no sirve
        assertEquals(403, post("/emparejar", new JSONObject().put("codigo", codigo), null).statusCode());
    }

    @Test
    void sincronizarSinTokenSeRechaza() throws Exception {
        assertEquals(401, post("/sync", new JSONObject().put("esquema", 9), null).statusCode());
        assertEquals(401, post("/sync", new JSONObject().put("esquema", 9), "inventado").statusCode());
    }

    @Test
    void unaSyncCompletaIntercambiaEnLosDosSentidos() throws Exception {
        String token = new JSONObject(post("/emparejar",
                new JSONObject().put("codigo", servidor.nuevoCodigo()), null).body()).getString("token");
        gastos.registrar("Anotado en la PC", new BigDecimal("80"), LocalDate.now(), null);

        // Lo que mandaría el teléfono: un gasto suyo, en el mismo formato de filas
        long ahora = System.currentTimeMillis();
        var gastoTelefono = new JSONObject()
                .put("id", "11111111-1111-4111-8111-111111111111").put("descripcion", "Anotado en el teléfono")
                .put("monto_centavos", 450).put("fecha", LocalDate.now().toString())
                .put("categoria_id", JSONObject.NULL).put("recurrencia_id", JSONObject.NULL)
                .put("deuda_id", JSONObject.NULL).put("cuenta_id", "00000000-0000-4000-8000-000000000001")
                .put("creado_en", ahora).put("actualizado_en", ahora).put("eliminado", 0);
        var pedido = new JSONObject().put("esquema", 9).put("desde", 0)
                .put("cambios", new JSONObject().put("gastos", new JSONArray().put(gastoTelefono)));

        var respuesta = post("/sync", pedido, token);
        assertEquals(200, respuesta.statusCode());
        var cuerpo = new JSONObject(respuesta.body());
        assertEquals(1, cuerpo.getInt("aplicadas"));
        assertEquals(2, gastos.delMes(YearMonth.now()).size()); // el del teléfono llegó a la PC
        // y la PC devuelve los suyos (más el que acaba de recibir: el teléfono lo ignora, no es más nuevo)
        assertEquals(2, cuerpo.getJSONObject("cambios").getJSONArray("gastos").length());
        assertTrue(servidor.ultimaSync().isPresent());
    }

    @Test
    void siLasVersionesDeLaBaseNoCoincidenSeAvisa() throws Exception {
        String token = new JSONObject(post("/emparejar",
                new JSONObject().put("codigo", servidor.nuevoCodigo()), null).body()).getString("token");
        assertEquals(409, post("/sync", new JSONObject().put("esquema", 8).put("desde", 0), token).statusCode());
    }
}
