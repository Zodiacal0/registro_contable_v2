package com.registrogastos.ui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.registrogastos.sincronizacion.SincronizacionServicio;
import javafx.animation.Animation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;

/**
 * Sincronizar: muestra el QR que escanea el teléfono para emparejarse, el estado del servidor
 * y la última sincronización. El QR lleva las IPs de esta PC, el puerto y un código de un
 * solo uso; se genera uno nuevo cada vez que se entra a la vista.
 */
final class VistaSincronizar extends ScrollPane implements Revelable {

    private static final int MODULO = 6; // píxeles por cuadrito del QR

    private final ModeloUi modelo;
    private final SincronizacionServicio sync;
    private final VBox contenido = new VBox(48);
    private Animation revelando;

    VistaSincronizar(ModeloUi modelo, SincronizacionServicio sync) {
        this.modelo = modelo;
        this.sync = sync;
        contenido.getStyleClass().add("contenido");
        setContent(contenido);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(this);
        modelo.resumenProperty().addListener((obs, anterior, resumen) -> pintar(false));
        pintar(true);
    }

    @Override
    public void revelar() {
        pintar(true); // código nuevo cada vez que se entra
        if (revelando != null) {
            revelando.stop();
        }
        revelando = Movimiento.revelar(List.copyOf(contenido.getChildren()));
    }

    private void pintar(boolean codigoNuevo) {
        if (codigoNuevo) {
            sync.nuevoCodigo();
        }
        contenido.getChildren().setAll(encabezado(), emparejar(), estado());
    }

    private Node encabezado() {
        var fila = new HBox(10, Nodos.etiqueta("Sincronizar", "titulo"),
                Nodos.etiqueta("con el teléfono", "titulo", "titulo-tenue"), Nodos.espaciador(),
                Nodos.etiqueta(sync.activo() ? "Servidor activo en tu Wi‑Fi  ·  puerto " + sync.puerto()
                        : "El servidor no pudo iniciar", "seccion-detalle"));
        fila.setAlignment(Pos.BASELINE_LEFT);
        fila.getStyleClass().add("encabezado");
        return fila;
    }

    /** El QR a la izquierda y los pasos a la derecha. */
    private Node emparejar() {
        String contenido = sync.contenidoQr();
        var qr = new StackPane(new ImageView(qr(contenido)));
        qr.getStyleClass().add("qr");
        qr.setMaxWidth(Region.USE_PREF_SIZE);

        List<String> ips = sync.direcciones();
        var pasos = new VBox(14,
                Nodos.etiqueta("Emparejar un teléfono", "seccion-titulo"),
                paso("1", "Conecta el teléfono al mismo Wi‑Fi que esta PC."),
                paso("2", "En la app del teléfono, entra a Sincronizar y escanea este código."),
                paso("3", "Listo: desde ahí se sincronizan solos cuando estén en la misma red."),
                Nodos.etiqueta(ips.isEmpty() ? "No se encontró una red local. Revisa el Wi‑Fi."
                        : "Esta PC: " + String.join("  ·  ", ips) + "  ·  código " + new org.json.JSONObject(contenido).getString("codigo"),
                        "leyenda-detalle"),
                Nodos.boton("Generar otro código", () -> pintar(true), "boton-texto"));
        HBox.setHgrow(pasos, Priority.ALWAYS);

        var fila = new HBox(40, qr, pasos);
        fila.setAlignment(Pos.TOP_LEFT);
        return fila;
    }

    private static Node paso(String numero, String texto) {
        var etiqueta = Nodos.etiqueta(texto, "subtitulo");
        etiqueta.setWrapText(true);
        etiqueta.setMinHeight(Region.USE_PREF_SIZE);
        var fila = new HBox(12, Nodos.etiqueta(numero, "analitica-puesto"), etiqueta);
        fila.setAlignment(Pos.TOP_LEFT);
        return fila;
    }

    private Node estado() {
        String ultima = sync.ultimaSync().map(i -> "Última sincronización " + Formato.cuandoInstante(i, modelo.hoy()))
                .orElse("Todavía no se ha sincronizado");
        var datos = new HBox(8, VistaListaDetalle.dato(sync.emparejado() ? "Teléfono emparejado" : "Sin teléfonos"),
                VistaListaDetalle.dato(ultima));
        var acciones = new HBox(4);
        if (sync.emparejado()) {
            acciones.getChildren().add(VistaListaDetalle.botonConfirmar("Olvidar teléfonos",
                    "Confirmar: tendrán que escanear de nuevo", () -> {
                        sync.desemparejar();
                        pintar(true);
                    }));
            acciones.setTranslateX(-8);
        }
        var nota = Nodos.etiqueta("Cómo funciona: cada aparato guarda cuándo cambió cada cosa. Al sincronizar se "
                + "intercambian solo lo que cambió desde la última vez y, si los dos tocaron lo mismo, gana el "
                + "cambio más reciente. Nada sale de tu red: no hay nube ni cuentas.", "leyenda-detalle");
        nota.setWrapText(true);
        nota.setMinHeight(Region.USE_PREF_SIZE);
        return new VBox(14, Nodos.etiqueta("Estado", "seccion-titulo"), datos, acciones, nota);
    }

    /** Dibuja el QR a mano sobre una imagen: cuadritos claros sobre el fondo del panel. */
    private static WritableImage qr(String contenido) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            int ancho = matriz.getWidth();
            int alto = matriz.getHeight();
            var imagen = new WritableImage(ancho * MODULO, alto * MODULO);
            PixelWriter pw = imagen.getPixelWriter();
            Color claro = Color.web("#e2dde7");
            Color oscuro = Color.web("#17151c");
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    Color color = matriz.get(x, y) ? claro : oscuro;
                    for (int dy = 0; dy < MODULO; dy++) {
                        for (int dx = 0; dx < MODULO; dx++) {
                            pw.setColor(x * MODULO + dx, y * MODULO + dy, color);
                        }
                    }
                }
            }
            return imagen;
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
    }
}
