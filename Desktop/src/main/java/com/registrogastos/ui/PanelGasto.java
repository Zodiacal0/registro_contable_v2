package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Panel fijo a la derecha para registrar gastos, siempre visible (como el panel de
 * órdenes de Binance). Si el modelo tiene un gasto seleccionado, el mismo panel pasa a
 * modo edición; así no hace falta un modal.
 */
final class PanelGasto extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ModeloUi modelo;
    private final Label titulo = Nodos.etiqueta("", "panel-titulo");
    private final Button salirEdicion = Iconos.CERRAR.boton("Salir de la edición", this::salirEdicion);
    private final TextField monto = new TextField();
    private final TextField descripcion = new TextField();
    private final DatePicker fecha = new DatePicker();
    private final FlowPane chips = new FlowPane(8, 8);
    private final ToggleGroup categorias = new ToggleGroup();
    private final Label error = Nodos.etiqueta("", "error");
    private final Label errorMonto = Nodos.etiqueta("", "error");
    private final VBox cajaMonto = new VBox(4);
    private final Button guardar = new Button();
    private final Button eliminar = new Button("Eliminar gasto");
    private final Label hoyDetalle = Nodos.etiqueta("", "hoy-detalle");
    private final Label hoyTotal = Nodos.etiqueta("", "hoy-total");
    private final VBox contenido;

    // "Se repite": convierte el gasto nuevo en una recurrencia
    private final ToggleButton repetir = new ToggleButton();
    private final SelectorFrecuencia frecuencia = new SelectorFrecuencia();
    private final Label etiquetaFecha = Nodos.etiqueta("Fecha", "campo-etiqueta");
    private final VBox cajaRepetir;

    /** @param alOcultar qué hacer con el botón "»" (la ventana decide cómo se oculta el panel) */
    PanelGasto(ModeloUi modelo, Runnable alOcultar) {
        this.modelo = modelo;
        getStyleClass().add("panel");
        setMinWidth(340);
        setPrefWidth(360);
        setMaxWidth(360);

        configurarCampos();
        salirEdicion.getStyleClass().add("boton-icono-chico");
        var ocultar = Iconos.OCULTAR_PANEL.boton("Ocultar panel", alOcultar);
        ocultar.getStyleClass().add("boton-icono-chico");
        var encabezado = new HBox(6, titulo, Nodos.espaciador(), salirEdicion, ocultar);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        cajaMonto.getChildren().addAll(Nodos.etiqueta("Monto", "campo-etiqueta"), monto);
        cajaMonto.getStyleClass().add("caja-monto");
        // Todo el recuadro se comporta como el campo: un clic en cualquier parte pone el cursor
        cajaMonto.setOnMouseClicked(e -> monto.requestFocus());
        errorMonto.managedProperty().bind(errorMonto.textProperty().isNotEmpty());
        // Al corregir el monto, el aviso se va
        monto.textProperty().addListener((obs, antes, ahora) -> {
            errorMonto.setText("");
            cajaMonto.getStyleClass().remove("caja-monto-error");
        });

        guardar.getStyleClass().addAll("boton", "boton-primario", "boton-ancho");
        guardar.setMaxWidth(Double.MAX_VALUE);
        guardar.setOnAction(e -> guardar());
        eliminar.getStyleClass().addAll("boton", "boton-peligro", "boton-ancho");
        eliminar.setMaxWidth(Double.MAX_VALUE);
        eliminar.setOnAction(e -> eliminar());
        error.managedProperty().bind(error.textProperty().isNotEmpty());

        cajaRepetir = construirRepetir();

        var hoy = new HBox(new VBox(2, Nodos.etiqueta("Hoy", "hoy-titulo"), hoyDetalle), Nodos.espaciador(), hoyTotal);
        hoy.setAlignment(Pos.CENTER_LEFT);
        hoy.getStyleClass().add("hoy");

        var relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        contenido = new VBox(20,
                encabezado,
                new VBox(8, cajaMonto, errorMonto),
                campo("Descripción", descripcion),
                new VBox(8, etiquetaFecha, fecha),
                campo("Categoría", chips),
                cajaRepetir,
                error,
                relleno,
                hoy,
                new VBox(10, guardar, eliminar),
                Nodos.etiqueta("Enter guarda  ·  Esc limpia  ·  Ctrl+N vuelve aquí", "panel-atajos"));
        // Si la ventana es baja, el panel se desplaza en vez de cortar lo de abajo;
        // fitToHeight estira el contenido cuando sí cabe, así el botón queda al fondo
        var desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.setFitToHeight(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        getChildren().add(desplazable);

        // Esc: sale de la edición o limpia el formulario
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                salirEdicion();
                e.consume();
            }
        });
        // Siempre debe quedar una categoría elegida: un clic sobre la activa no la apaga.
        // Si el anterior ya no está en el grupo (se repintaron los chips), no se toca.
        categorias.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null && categorias.getToggles().contains(anterior)) {
                anterior.setSelected(true);
            }
        });
        modelo.seleccionadoProperty().addListener((obs, anterior, gasto) -> cargar(gasto));
        modelo.resumenProperty().addListener((obs, anterior, resumen) -> {
            pintarChips(categoriaElegidaId());
            pintarHoy();
        });
        cargar(null);
        pintarHoy();
    }

    /**
     * Interruptor "Se repite" y, al activarlo, la frecuencia. La fecha pasa a llamarse
     * "Primer cobro" y el botón "Crear recurrente".
     */
    private VBox construirRepetir() {
        var perilla = new Region();
        perilla.getStyleClass().add("interruptor-perilla");
        repetir.setGraphic(perilla);
        repetir.getStyleClass().addAll("interruptor", "repetir");
        repetir.setAccessibleText("Se repite");

        var fila = new HBox(10, Iconos.REPETIR.crear("icono-chico"), Nodos.etiqueta("Se repite", "repetir-texto"),
                Nodos.espaciador(), repetir);
        fila.setAlignment(Pos.CENTER_LEFT);
        // Toda la fila activa el interruptor, no solo la perilla
        fila.setOnMouseClicked(e -> {
            if (e.getTarget() != repetir) {
                repetir.fire();
            }
        });

        frecuencia.visibleProperty().bind(repetir.selectedProperty());
        frecuencia.managedProperty().bind(frecuencia.visibleProperty());
        etiquetaFecha.textProperty().bind(Bindings.when(repetir.selectedProperty())
                .then("Primer cobro").otherwise("Fecha"));
        repetir.selectedProperty().addListener((obs, antes, activo) -> {
            if (modelo.seleccionadoProperty().get() == null) {
                guardar.setText(activo ? "Crear recurrente" : "Registrar gasto");
            }
        });

        var caja = new VBox(12, fila, frecuencia);
        caja.getStyleClass().add("caja-repetir");
        return caja;
    }

    /** Lo gastado hoy: el contexto que importa al anotar el siguiente gasto. */
    private void pintarHoy() {
        var deHoy = modelo.gastosDeHoy();
        hoyTotal.setText(Formato.monto(deHoy.stream().map(Gasto::monto).reduce(BigDecimal.ZERO, BigDecimal::add)));
        hoyDetalle.setText(switch (deHoy.size()) {
            case 0 -> "Nada registrado aún";
            case 1 -> "1 gasto";
            default -> deHoy.size() + " gastos";
        });
    }

    void enfocar() {
        monto.requestFocus();
    }

    private void configurarCampos() {
        monto.getStyleClass().add("campo-monto");
        monto.setPromptText("0.00");
        descripcion.getStyleClass().add("campo");
        descripcion.setPromptText("¿En qué fue? (opcional)");
        fecha.setEditable(false); // solo calendario: evita fechas mal escritas
        fecha.setMaxWidth(Double.MAX_VALUE);
        fecha.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate valor) {
                return valor == null ? "" : valor.format(FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {
                return texto == null || texto.isBlank() ? null : LocalDate.parse(texto, FECHA);
            }
        });
        monto.setOnAction(e -> guardar());
        descripcion.setOnAction(e -> guardar());
    }

    private static Node campo(String etiqueta, Node control) {
        return new VBox(8, Nodos.etiqueta(etiqueta, "campo-etiqueta"), control);
    }

    /** Llena el panel con un gasto (modo edición) o lo deja listo para uno nuevo. */
    private void cargar(Gasto gasto) {
        boolean editando = gasto != null;
        titulo.setText(editando ? "Editar gasto" : "Nuevo gasto");
        guardar.setText(editando ? "Guardar cambios" : "Registrar gasto");
        salirEdicion.setVisible(editando);
        eliminar.setVisible(editando);
        eliminar.setManaged(editando);
        eliminar.setText("Eliminar gasto");
        eliminar.getStyleClass().remove("boton-peligro-confirmar");
        limpiarErrores();
        // "Se repite" solo aplica a gastos nuevos; editar un gasto no lo vuelve recurrente
        repetir.setSelected(false);
        cajaRepetir.setVisible(!editando);
        cajaRepetir.setManaged(!editando);

        if (editando) {
            monto.setText(gasto.monto().toPlainString());
            descripcion.setText(gasto.descripcion());
            fecha.setValue(gasto.fecha());
            pintarChips(gasto.categoriaId());
        } else {
            monto.clear();
            descripcion.clear();
            fecha.setValue(modelo.hoy());
            pintarChips(null);
        }

        // Transición corta al cambiar de modo: el panel "respira" en vez de saltar
        var fundido = new FadeTransition(Duration.millis(160), contenido);
        fundido.setFromValue(0.35);
        fundido.setToValue(1);
        fundido.play();
    }

    private void pintarChips(String categoriaId) {
        categorias.getToggles().clear();
        chips.getChildren().setAll(chip("Sin categoría", null));
        modelo.resumen().categorias().forEach(c -> chips.getChildren().add(chip(c.nombre(), c)));
        categorias.getToggles().stream()
                .filter(t -> t.getUserData() instanceof Categoria c && c.id().equals(categoriaId))
                .findFirst()
                .orElse(categorias.getToggles().getFirst())
                .setSelected(true);
    }

    private ToggleButton chip(String texto, Categoria categoria) {
        var chip = new ToggleButton(texto, Nodos.punto(Paleta.colorDe(categoria)));
        chip.getStyleClass().add("chip");
        chip.setUserData(categoria);
        chip.setToggleGroup(categorias);
        return chip;
    }

    private String categoriaElegidaId() {
        var elegido = categorias.getSelectedToggle();
        return elegido != null && elegido.getUserData() instanceof Categoria c ? c.id() : null;
    }

    private void guardar() {
        limpiarErrores();
        BigDecimal valor;
        try {
            valor = Formato.leerMonto(monto.getText());
        } catch (IllegalArgumentException e) {
            marcarErrorMonto(e.getMessage());
            return;
        }

        var categoria = (Categoria) categorias.getSelectedToggle().getUserData();
        // La descripción es opcional: sin ella, el gasto se nombra como su categoría
        String texto = descripcion.getText().isBlank()
                ? (categoria == null ? "Gasto" : categoria.nombre())
                : descripcion.getText();
        try {
            Gasto editado = modelo.seleccionadoProperty().get();
            if (editado == null && repetir.isSelected()) {
                // Crea la recurrencia; si el primer cobro es hoy o antes, ya queda registrado
                modelo.crearRecurrencia(texto, valor, fecha.getValue(), categoria,
                        frecuencia.cada(), frecuencia.unidad());
                monto.clear();
                descripcion.clear();
                repetir.setSelected(false);
                monto.requestFocus();
            } else if (editado == null) {
                modelo.registrar(texto, valor, fecha.getValue(), categoria);
                // Captura en ráfaga: se limpian monto y descripción, se conservan fecha y categoría
                monto.clear();
                descripcion.clear();
                monto.requestFocus();
            } else {
                modelo.editar(editado.id(), texto, valor, fecha.getValue(), categoria);
            }
        } catch (IllegalArgumentException e) {
            // Validaciones del dominio (p. ej. monto en cero): las del monto van junto a su campo
            if (e.getMessage().toLowerCase().contains("monto")) {
                marcarErrorMonto(e.getMessage());
            } else {
                error.setText(e.getMessage());
            }
        }
    }

    private void marcarErrorMonto(String mensaje) {
        errorMonto.setText(mensaje);
        cajaMonto.getStyleClass().add("caja-monto-error");
        monto.requestFocus();
        monto.selectAll();
    }

    private void limpiarErrores() {
        error.setText("");
        errorMonto.setText("");
        cajaMonto.getStyleClass().remove("caja-monto-error");
    }

    /** Primer clic arma el botón, el segundo elimina. */
    private void eliminar() {
        if (!eliminar.getStyleClass().contains("boton-peligro-confirmar")) {
            eliminar.setText("Confirmar: eliminar definitivamente");
            eliminar.getStyleClass().add("boton-peligro-confirmar");
            return;
        }
        modelo.eliminar(modelo.seleccionadoProperty().get().id());
    }

    private void salirEdicion() {
        if (modelo.seleccionadoProperty().get() != null) {
            modelo.seleccionadoProperty().set(null);
        } else {
            cargar(null);
        }
    }
}
