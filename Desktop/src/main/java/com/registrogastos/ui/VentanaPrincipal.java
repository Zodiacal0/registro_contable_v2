package com.registrogastos.ui;

import com.registrogastos.dominio.Gasto;
import javafx.application.ColorScheme;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Composición de escritorio:
 *
 * ┌─────────────────────────────────────────────── barra de título propia ─┐
 * ├──────┬───────────────────────────────┬──────────────┤
 * │ riel │ vista activa (resumen /        │ panel de     │
 * │      │ categorías), con scroll        │ captura fijo │
 * └──────┴───────────────────────────────┴──────────────┘
 *
 * La barra de título es un HeaderBar (StageStyle.EXTENDED, preview de JavaFX 25): la
 * dibuja la app, pero Windows sigue poniendo sus botones y permitiendo arrastrar,
 * redimensionar y acomodar la ventana con snap.
 */
public final class VentanaPrincipal {

    private static final String[] FUENTES = {
            "Figtree-400", "Figtree-500", "Figtree-600", "Figtree-700", "IBMPlexMono-500", "IBMPlexMono-600"};
    private static final double ALTO_BARRA = 44;
    private static final double ANCHO_PANEL = 360;
    private static final String PREF_PANEL = "panelVisible";

    private final ModeloUi modelo;
    private final BorderPane raiz = new BorderPane();
    private final BorderPane marco = new BorderPane();
    private final VistaResumen vistaResumen;
    private final VistaCategorias vistaCategorias;
    private final VistaRecurrentes vistaRecurrentes;
    private final VistaMetas vistaMetas;
    private final VistaDeudas vistaDeudas;
    private final VistaCuentas vistaCuentas;
    private final VistaAnaliticas vistaAnaliticas;
    private final VistaPresupuesto vistaPresupuesto;
    private final VistaSincronizar vistaSincronizar;
    private final PanelGasto panel;

    /** Si el panel de captura está abierto. Se recuerda entre sesiones. */
    private final BooleanProperty panelVisible = new SimpleBooleanProperty();
    /**
     * java.util.prefs: almacén clave-valor por usuario que trae Java (en Windows vive en el
     * registro, HKCU\Software\JavaSoft\Prefs). Ideal para preferencias chicas como esta.
     */
    private final Preferences preferencias = Preferences.userNodeForPackage(VentanaPrincipal.class);

    public VentanaPrincipal(ModeloUi modelo) {
        this.modelo = modelo;
        this.vistaResumen = new VistaResumen(modelo);
        this.vistaCategorias = new VistaCategorias(modelo);
        this.vistaRecurrentes = new VistaRecurrentes(modelo);
        this.vistaMetas = new VistaMetas(modelo);
        this.vistaDeudas = new VistaDeudas(modelo);
        this.vistaCuentas = new VistaCuentas(modelo);
        this.vistaAnaliticas = new VistaAnaliticas(modelo);
        this.vistaPresupuesto = new VistaPresupuesto(modelo);
        this.vistaSincronizar = new VistaSincronizar(modelo, modelo.sincronizacion());
        this.panel = new PanelGasto(modelo, () -> panelVisible.set(false));

        marco.setLeft(riel());
        marco.setCenter(vistaResumen);
        marco.getStyleClass().add("marco");

        panelVisible.set(preferencias.getBoolean(PREF_PANEL, true));
        if (panelVisible.get()) {
            marco.setRight(panel);
        }
        panelVisible.addListener((obs, antes, visible) -> {
            preferencias.putBoolean(PREF_PANEL, visible);
            animarPanel(visible);
        });
        // Clic en un gasto con el panel oculto: se abre para editarlo
        modelo.seleccionadoProperty().addListener((obs, anterior, gasto) -> {
            if (gasto != null) {
                panelVisible.set(true);
            }
        });

        raiz.setTop(barraDeTitulo());
        raiz.setCenter(marco);
        raiz.getStyleClass().add("ventana");
    }

    /** Las fuentes deben registrarse antes de que el CSS las pida por nombre. */
    public static void cargarFuentes() {
        for (String nombre : FUENTES) {
            var recurso = VentanaPrincipal.class.getResourceAsStream("/ui/fuentes/" + nombre + ".ttf");
            Font.loadFont(Objects.requireNonNull(recurso, nombre), 14);
        }
    }

    /** Configura la ventana (estilo extendido, escena, tamaños) y la muestra. */
    public void mostrar(Stage escenario) {
        escenario.initStyle(StageStyle.EXTENDED);
        HeaderBar.setPrefButtonHeight(escenario, ALTO_BARRA);
        escenario.setTitle("Registro de Gastos");
        escenario.setScene(crearEscena());
        escenario.setMinWidth(1180);
        escenario.setMinHeight(720);
        // Tamaño preferido, pero sin pasarse del área visible (con escalado de Windows al 125%,
        // 860 px lógicos no caben en una pantalla de 1080 y la barra quedaba fuera)
        var area = Screen.getPrimary().getVisualBounds();
        escenario.setWidth(Math.min(1320, area.getWidth() * 0.94));
        escenario.setHeight(Math.min(860, area.getHeight() * 0.94));
        escenario.centerOnScreen();
        escenario.show();
    }

    public Scene crearEscena() {
        var escena = new Scene(raiz, 1320, 860);
        // Los botones de Windows (minimizar, maximizar, cerrar) se dibujan en su versión oscura
        escena.getPreferences().setColorScheme(ColorScheme.DARK);
        escena.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/ui/estilos.css")).toExternalForm());
        escena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::nuevoGasto);
        return escena;
    }

    /** Abre el panel (si estaba oculto) en modo "nuevo" y pone el cursor en el monto. */
    public void nuevoGasto() {
        modelo.seleccionadoProperty().set(null);
        panelVisible.set(true);
        panel.enfocar();
    }

    public void ocultarPanel() {
        panelVisible.set(false);
    }

    /**
     * El panel se desliza: su ancho va de 360 a 0 (o al revés) y se recorta con un clip para
     * que el contenido no se desborde mientras se angosta. Al cerrar, sale del BorderPane y el
     * contenido llega hasta el borde; el botón para reabrirlo aparece en el riel izquierdo.
     */
    private void animarPanel(boolean abrir) {
        if (abrir && marco.getRight() != panel) {
            fijarAnchoPanel(0);
            marco.setRight(panel);
        }
        var recorte = new Rectangle();
        recorte.widthProperty().bind(panel.widthProperty());
        recorte.heightProperty().bind(panel.heightProperty());
        panel.setClip(recorte);

        Movimiento.progreso(Duration.millis(360), Movimiento.SUAVE, f -> {
            double t = abrir ? f : 1 - f;
            fijarAnchoPanel(ANCHO_PANEL * t);
            panel.setOpacity(t);
            if (f >= 1) {
                panel.setClip(null);
                if (!abrir) {
                    marco.setRight(null);
                }
            }
        });
    }

    private void fijarAnchoPanel(double ancho) {
        panel.setMinWidth(ancho);
        panel.setPrefWidth(ancho);
        panel.setMaxWidth(ancho);
    }

    public void editarGasto(Gasto gasto) {
        modelo.seleccionadoProperty().set(gasto);
    }

    /**
     * Barra superior unida al riel (la "L" oscura del cliente de Steam): marca y nombre a la
     * izquierda, estado a la derecha. Las zonas vacías de un HeaderBar arrastran la ventana.
     */
    private HeaderBar barraDeTitulo() {
        var nombre = Nodos.etiqueta("Registro de Gastos", "barra-nombre");
        var izquierda = new HBox(14, marca(), nombre);
        izquierda.setAlignment(Pos.CENTER_LEFT);
        izquierda.getStyleClass().add("barra-izquierda");

        var punto = new Region();
        punto.getStyleClass().add("estado-punto");
        var estado = new HBox(8, punto, Nodos.etiqueta("Local · sin nube", "estado-texto"));
        estado.setAlignment(Pos.CENTER);
        estado.getStyleClass().add("barra-derecha");

        var barra = new HeaderBar(izquierda, null, estado);
        barra.getStyleClass().add("barra-titulo");
        barra.setMinHeight(ALTO_BARRA);
        barra.setPrefHeight(ALTO_BARRA);
        return barra;
    }

    /**
     * Con el panel cerrado, "+ Nuevo" aparece en el riel, debajo de la navegación (como el
     * "+ Agregar un juego" de la barra lateral de Steam). El círculo claro es la acción principal.
     */
    private Node botonCapturar() {
        var circulo = new StackPane(Iconos.MAS.crear());
        circulo.getStyleClass().add("capturar-circulo");

        var abrir = new Button("Nuevo", circulo);
        abrir.setContentDisplay(ContentDisplay.TOP);
        abrir.getStyleClass().add("capturar");
        abrir.setAccessibleText("Nuevo gasto (Ctrl+N)");
        abrir.setTooltip(new Tooltip("Nuevo gasto  ·  Ctrl+N"));
        abrir.setOnAction(e -> nuevoGasto());

        var separador = new Region();
        separador.getStyleClass().add("riel-separador");
        var bloque = new VBox(14, separador, abrir);
        bloque.setAlignment(Pos.TOP_CENTER);
        // Solo con el panel cerrado; va debajo de la navegación, así nada se mueve al aparecer
        bloque.visibleProperty().bind(panelVisible.not());
        bloque.managedProperty().bind(bloque.visibleProperty());
        bloque.visibleProperty().addListener((obs, antes, visible) -> {
            if (visible) {
                Movimiento.entrar(bloque);
            }
        });
        return bloque;
    }

    private VBox riel() {
        var grupo = new ToggleGroup();
        var resumen = itemNavegacion("Resumen", Iconos.RESUMEN, grupo, vistaResumen);
        var presupuesto = itemNavegacion("Presupuesto", Iconos.PRESUPUESTO, grupo, vistaPresupuesto);
        var analiticas = itemNavegacion("Analíticas", Iconos.ANALITICAS, grupo, vistaAnaliticas);
        var categorias = itemNavegacion("Categorías", Iconos.CATEGORIAS, grupo, vistaCategorias);
        var recurrentes = itemNavegacion("Recurrentes", Iconos.REPETIR, grupo, vistaRecurrentes);
        var cuentas = itemNavegacion("Cuentas", Iconos.CUENTA, grupo, vistaCuentas);
        var metas = itemNavegacion("Metas", Iconos.META, grupo, vistaMetas);
        var deudas = itemNavegacion("Deudas", Iconos.DEUDA, grupo, vistaDeudas);
        var sincronizar = itemNavegacion("Sincronizar", Iconos.SINCRONIZAR, grupo, vistaSincronizar);
        resumen.setSelected(true);
        // Un ToggleGroup permite deseleccionar todo con un clic; aquí siempre debe haber uno activo
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                anterior.setSelected(true);
            }
        });

        var relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        var riel = new VBox(10, resumen, presupuesto, analiticas, categorias, recurrentes, cuentas, metas, deudas, sincronizar, botonCapturar(), relleno);
        riel.setAlignment(Pos.TOP_CENTER);
        riel.getStyleClass().add("riel");
        return riel;
    }

    /** Tres cápsulas superpuestas en los tonos de la paleta: el mismo gesto de la imagen de origen. */
    private static Node marca() {
        var capsulas = new VBox(-4);
        for (String clase : new String[] {"marca-capsula-1", "marca-capsula-2", "marca-capsula-3"}) {
            var capsula = new Region();
            capsula.getStyleClass().addAll("marca-capsula", clase);
            capsulas.getChildren().add(capsula);
        }
        capsulas.setAlignment(Pos.CENTER);
        return capsulas;
    }

    private ToggleButton itemNavegacion(String texto, Iconos icono, ToggleGroup grupo, Node vista) {
        var item = new ToggleButton(texto, icono.crear("icono-nav"));
        item.setContentDisplay(ContentDisplay.TOP);
        item.setToggleGroup(grupo);
        item.getStyleClass().add("nav-item");
        item.selectedProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                marco.setCenter(vista);
                if (vista instanceof Revelable revelable) {
                    revelable.revelar(); // entrada en cascada
                }
            }
        });
        return item;
    }
}
