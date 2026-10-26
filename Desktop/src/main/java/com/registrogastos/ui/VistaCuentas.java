package com.registrogastos.ui;

import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.Moneda;
import com.registrogastos.servicio.EstadoCuenta;
import com.registrogastos.servicio.MovimientoCuenta;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cuentas (de dónde sale el dinero): lista con el saldo de cada una y el patrimonio total; en
 * el detalle, registrar ingresos, transferir entre cuentas y el historial de movimientos.
 * Arriba de la lista, el tipo de cambio con que se suman las cuentas en dólares.
 */
final class VistaCuentas extends VistaListaDetalle {

    private enum Modo { VER, CREAR, EDITAR }

    private String elegidaId;
    private Modo modo = Modo.VER;
    private final TextField cambio = new TextField();

    VistaCuentas(ModeloUi modelo) {
        super(modelo, "Cuentas");
        ponerAccion(Nodos.boton("+  Nueva cuenta", () -> cambiarModo(Modo.CREAR), "boton-pildora"), tipoDeCambio());
        pintar();
    }

    /** "1 US$ = Q [7.70]": se guarda con Enter o al salir del campo. */
    private Node tipoDeCambio() {
        cambio.getStyleClass().addAll("campo", "campo-chico", "campo-cambio");
        cambio.setText(modelo.tipoCambio(Moneda.USD).toPlainString());
        var error = etiquetaError();
        Runnable guardar = () -> ejecutar(error, () -> {
            BigDecimal valor = new BigDecimal(cambio.getText().strip().replace(',', '.'));
            if (valor.compareTo(modelo.tipoCambio(Moneda.USD)) != 0) {
                modelo.fijarTipoCambio(Moneda.USD, valor);
            }
        });
        cambio.setOnAction(e -> guardar.run());
        cambio.focusedProperty().addListener((obs, antes, enfocado) -> {
            if (!enfocado) {
                guardar.run();
            }
        });
        var fila = new HBox(8, Nodos.etiqueta("Tipo de cambio", "leyenda-detalle"), Nodos.espaciador(),
                Nodos.etiqueta("1 US$ = Q", "leyenda-detalle"), cambio);
        fila.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, fila, error);
    }

    private void cambiarModo(Modo nuevo) {
        modo = nuevo;
        pintar();
    }

    @Override
    protected void pintar() {
        List<EstadoCuenta> cuentas = modelo.cuentas();
        subtitulo.setText("Tienes " + Formato.montoConSimbolo(modelo.patrimonio(), Moneda.PRINCIPAL) + " en total");
        if (!cambio.isFocused()) {
            cambio.setText(modelo.tipoCambio(Moneda.USD).toPlainString());
        }

        Optional<EstadoCuenta> elegida = cuentas.stream()
                .filter(e -> e.cuenta().id().equals(elegidaId))
                .findFirst()
                .or(() -> cuentas.stream().findFirst());

        lista.getChildren().clear();
        for (EstadoCuenta e : cuentas) {
            boolean esta = modo != Modo.CREAR && elegida.filter(e::equals).isPresent();
            lista.getChildren().add(itemLista(item(e), esta, () -> {
                elegidaId = e.cuenta().id();
                cambiarModo(Modo.VER);
            }));
        }

        if (modo == Modo.CREAR) {
            detalle.getChildren().setAll(formulario(null));
        } else if (modo == Modo.EDITAR && elegida.isPresent()) {
            detalle.getChildren().setAll(formulario(elegida.get().cuenta()));
        } else {
            detalle.getChildren().setAll(elegida.map(this::verCuenta).orElseGet(List::of));
        }
    }

    private Node item(EstadoCuenta e) {
        Cuenta c = e.cuenta();
        var titulo = new HBox(10, Iconos.CUENTA.crear("icono-cuenta"), Nodos.etiqueta(c.nombre(), "leyenda-nombre"),
                Nodos.espaciador(), Nodos.etiqueta(Formato.montoConSimbolo(e.saldo(), c.moneda()), "leyenda-monto"));
        titulo.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, titulo, Nodos.etiqueta(c.tipo().nombre() + "  ·  " + c.moneda().nombre(), "leyenda-detalle"));
    }

    // ---- Ver ----

    private List<Node> verCuenta(EstadoCuenta e) {
        Cuenta c = e.cuenta();
        var acciones = new HBox(4, Nodos.boton("Editar", () -> cambiarModo(Modo.EDITAR), "boton-texto"));
        if (!Cuenta.PRINCIPAL_ID.equals(c.id())) {
            acciones.getChildren().add(botonConfirmar("Eliminar", "Confirmar: eliminar cuenta", () -> {
                modelo.eliminarCuenta(c.id());
                elegidaId = null;
            }));
        }
        acciones.setTranslateX(-8);
        var encabezado = new VBox(4, Nodos.etiqueta(c.nombre(), "titulo"),
                Nodos.etiqueta(c.tipo().nombre() + "  ·  " + c.moneda().nombre(), "subtitulo"), acciones);
        encabezado.getStyleClass().add("encabezado");

        var cifra = new HBox(10, Nodos.etiqueta(Formato.montoConSimbolo(e.saldo(), c.moneda()), "cifra-grande"),
                Nodos.etiqueta("saldo", "subtitulo"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        var datos = new HBox(8, dato("Saldo inicial " + Formato.montoConSimbolo(c.saldoInicial(), c.moneda())));
        if (!c.moneda().esPrincipal()) {
            datos.getChildren().add(dato("≈ " + Formato.montoConSimbolo(e.saldoPrincipal(), Moneda.PRINCIPAL)));
        }
        var saldo = new VBox(14, cifra, datos);

        var secciones = new ArrayList<Node>(List.of(encabezado, saldo, ingreso(c)));
        if (modelo.cuentas().size() > 1) {
            secciones.add(transferir(c));
        }
        secciones.add(historial(c));
        return secciones;
    }

    private Node ingreso(Cuenta c) {
        TextField monto = Campos.monto(null);
        TextField descripcion = Campos.texto("", "Ej. Salario");
        DatePicker fecha = Campos.fecha(modelo.hoy());
        var error = etiquetaError();
        Runnable registrar = () -> ejecutar(error, () -> modelo.registrarIngreso(c.id(),
                descripcion.getText().isBlank() ? "Ingreso" : descripcion.getText(),
                Formato.leerMonto(monto.getText()), fecha.getValue()));
        monto.setOnAction(e -> registrar.run());
        descripcion.setOnAction(e -> registrar.run());

        var fila = new HBox(10, Campos.etiquetado("Monto", monto), Campos.etiquetado("Descripción", descripcion),
                Campos.etiquetado("Fecha", fecha));
        HBox.setHgrow(fila.getChildren().get(1), Priority.ALWAYS);
        return new VBox(14, encabezadoSeccion("Registrar ingreso", "Salario, ventas, regalos"), fila, error,
                Nodos.boton("Registrar ingreso", registrar, "boton-pildora"));
    }

    /**
     * Transferir a otra cuenta. Si las monedas difieren aparece "Llega", con la conversión ya
     * calculada: se puede corregir con lo que de verdad dio el banco.
     */
    private Node transferir(Cuenta origen) {
        var destinos = new FlowPane(8, 8);
        var grupo = new ToggleGroup();
        for (EstadoCuenta e : modelo.cuentas()) {
            if (e.cuenta().id().equals(origen.id())) {
                continue;
            }
            var chip = new ToggleButton(e.cuenta().nombre(), Iconos.CUENTA.crear("icono-chico"));
            chip.getStyleClass().add("chip");
            chip.setUserData(e.cuenta());
            chip.setToggleGroup(grupo);
            destinos.getChildren().add(chip);
        }
        ((ToggleButton) destinos.getChildren().getFirst()).setSelected(true);
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                anterior.setSelected(true);
            }
        });

        TextField monto = Campos.monto(null);
        TextField llega = Campos.monto(null);
        var campoLlega = Campos.etiquetado("Llega", llega);
        Runnable recalcular = () -> {
            Cuenta destino = (Cuenta) grupo.getSelectedToggle().getUserData();
            boolean distintas = destino.moneda() != origen.moneda();
            campoLlega.setVisible(distintas);
            campoLlega.setManaged(distintas);
            if (distintas) {
                try {
                    llega.setText(modelo.conversor().convertir(Formato.leerMonto(monto.getText()),
                            origen.moneda(), destino.moneda()).toPlainString());
                } catch (IllegalArgumentException sinMontoTodavia) {
                    llega.clear();
                }
            }
        };
        monto.textProperty().addListener((obs, antes, ahora) -> recalcular.run());
        grupo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                recalcular.run();
            }
        });
        recalcular.run();

        DatePicker fecha = Campos.fecha(modelo.hoy());
        var error = etiquetaError();
        Runnable transferir = () -> ejecutar(error, () -> {
            Cuenta destino = (Cuenta) grupo.getSelectedToggle().getUserData();
            BigDecimal entrada = campoLlega.isVisible() ? Formato.leerMonto(llega.getText()) : null;
            modelo.transferir(origen.id(), destino.id(), Formato.leerMonto(monto.getText()), entrada, fecha.getValue());
        });
        monto.setOnAction(e -> transferir.run());

        var fila = new HBox(10, Campos.etiquetado("Sale (" + origen.moneda().simbolo() + ")", monto), campoLlega,
                Campos.etiquetado("Fecha", fecha));
        HBox.setHgrow(fila.getChildren().getFirst(), Priority.ALWAYS);
        return new VBox(14, encabezadoSeccion("Transferir", "No es gasto ni ingreso: solo cambia de lugar"),
                destinos, fila, error, Nodos.boton("Transferir", transferir, "boton-pildora"));
    }

    private Node historial(Cuenta c) {
        List<MovimientoCuenta> movimientos = modelo.movimientosDe(c.id());
        if (movimientos.isEmpty()) {
            return new VBox(14, encabezadoSeccion("Historial", ""), vacio("Todavía no hay movimientos en esta cuenta."));
        }
        var filas = new ArrayList<Node>();
        for (MovimientoCuenta m : movimientos.stream().limit(40).toList()) {
            filas.add(fila(m, c.moneda()));
        }
        String detalle = movimientos.size() > 40 ? "Los 40 más recientes de " + movimientos.size()
                : movimientos.size() + (movimientos.size() == 1 ? " movimiento" : " movimientos");
        return new VBox(14, encabezadoSeccion("Historial", detalle), tabla(filas.toArray(Node[]::new)));
    }

    /** Fecha, descripción, monto con signo y, si se puede quitar desde aquí, la ✕. */
    private Node fila(MovimientoCuenta m, Moneda moneda) {
        String signo = m.monto().signum() > 0 ? "+" : "";
        var monto = Nodos.etiqueta(signo + Formato.montoConSimbolo(m.monto(), moneda), "celda-monto");
        var descripcion = Nodos.etiqueta(m.descripcion(), "celda");
        var fila = new HBox(12, Nodos.etiqueta(Formato.fechaCorta(m.fecha(), modelo.hoy()), "celda", "celda-fecha"),
                descripcion, Nodos.espaciador(), monto);
        if (m.seQuitaAqui()) {
            var quitar = Iconos.CERRAR.boton("Quitar", () -> modelo.quitarMovimientoCuenta(m));
            quitar.getStyleClass().addAll("boton-icono-chico", "fila-quitar");
            fila.getChildren().add(quitar);
        }
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tabla-fila");
        return fila;
    }

    // ---- Crear / editar ----

    /** @param cuenta null = crear una nueva */
    private List<Node> formulario(Cuenta cuenta) {
        boolean nueva = cuenta == null;
        TextField nombre = Campos.texto(nueva ? "" : cuenta.nombre(), "Ej. Banco Industrial");
        TextField saldo = Campos.texto(nueva ? "" : cuenta.saldoInicial().toPlainString(), "0.00");
        saldo.getStyleClass().add("campo-cifra");

        var tipos = new ToggleGroup();
        var filaTipos = segmentado(tipos, Cuenta.Tipo.values(), nueva ? Cuenta.Tipo.BANCO : cuenta.tipo(), Cuenta.Tipo::nombre);
        var monedas = new ToggleGroup();
        var filaMonedas = segmentado(monedas, Moneda.values(), Moneda.PRINCIPAL, Moneda::nombre);

        var error = etiquetaError();
        Runnable guardar = () -> ejecutar(error, () -> {
            var tipo = (Cuenta.Tipo) tipos.getSelectedToggle().getUserData();
            BigDecimal inicial = leerSaldo(saldo.getText());
            if (nueva) {
                elegidaId = modelo.crearCuenta(nombre.getText(), tipo,
                        (Moneda) monedas.getSelectedToggle().getUserData(), inicial).id();
            } else {
                modelo.editarCuenta(cuenta.id(), nombre.getText(), tipo, inicial);
            }
            cambiarModo(Modo.VER);
        });

        var botones = new HBox(4, Nodos.boton(nueva ? "Crear cuenta" : "Guardar cambios", guardar, "boton-pildora"),
                Nodos.boton("Cancelar", () -> cambiarModo(Modo.VER), "boton-texto"));
        botones.setAlignment(Pos.CENTER_LEFT);

        var encabezado = new VBox(4, Nodos.etiqueta(nueva ? "Nueva cuenta" : "Editar cuenta", "titulo"),
                Nodos.etiqueta(nueva ? "Efectivo, una cuenta de banco o una tarjeta." : cuenta.nombre(), "subtitulo"));
        encabezado.getStyleClass().add("encabezado");
        var campos = new VBox(16, Campos.etiquetado("Nombre", nombre), Campos.etiquetado("Tipo", filaTipos));
        if (nueva) {
            // La moneda solo se elige al crear: cambiarla reinterpretaría todo lo registrado
            campos.getChildren().add(Campos.etiquetado("Moneda", filaMonedas));
        }
        var ayudaSaldo = Nodos.etiqueta("Lo que tenía la cuenta al empezar a usar la app. En una tarjeta, "
                + "lo que ya debías va en negativo (ej. -1500).", "leyenda-detalle");
        ayudaSaldo.setWrapText(true);
        campos.getChildren().addAll(new VBox(8, Campos.etiquetado("Saldo inicial", saldo), ayudaSaldo), error, botones);
        campos.setMaxWidth(520);
        return List.of(encabezado, campos);
    }

    /** Control segmentado (como el de Deudas) con una opción por valor; siempre queda una elegida. */
    private static <T> HBox segmentado(ToggleGroup grupo, T[] opciones, T elegida, Function<T, String> texto) {
        var fila = new HBox();
        fila.getStyleClass().add("segmentado");
        for (T opcion : opciones) {
            var boton = new ToggleButton(texto.apply(opcion));
            boton.getStyleClass().add("segmento");
            boton.setUserData(opcion);
            boton.setToggleGroup(grupo);
            boton.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(boton, Priority.ALWAYS);
            boton.setSelected(opcion == elegida);
            fila.getChildren().add(boton);
        }
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                anterior.setSelected(true);
            }
        });
        return fila;
    }

    /** El saldo inicial sí puede ser negativo o cero: "-1,500.50" -> -1500.50; vacío -> 0. */
    private static BigDecimal leerSaldo(String texto) {
        String limpio = texto.strip();
        if (limpio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        boolean negativo = limpio.startsWith("-") || limpio.startsWith("−");
        BigDecimal valor = Formato.leerMonto(negativo ? limpio.substring(1) : limpio);
        return negativo ? valor.negate() : valor;
    }
}
