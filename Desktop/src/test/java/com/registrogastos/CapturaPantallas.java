package com.registrogastos;

import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.Deuda;
import com.registrogastos.dominio.LineaPlan;
import com.registrogastos.dominio.Moneda;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.servicio.CuentaServicio;
import com.registrogastos.servicio.DeudaServicio;
import com.registrogastos.servicio.GastoServicio;
import com.registrogastos.servicio.MetaServicio;
import com.registrogastos.servicio.RecurrenciaServicio;
import com.registrogastos.servicio.Servicios;
import com.registrogastos.ui.ModeloUi;
import com.registrogastos.ui.VentanaPrincipal;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Herramienta de desarrollo (no es una prueba): abre la UI con datos de ejemplo en una
 * base temporal y guarda capturas PNG de cada pantalla en la carpeta indicada.
 *
 * Uso: java ... com.registrogastos.CapturaPantallas <carpeta-salida>
 */
public final class CapturaPantallas extends Application {

    private Path salida;
    private Scene escena;
    private VentanaPrincipal ventana;
    private GastoServicio servicio;

    @Override
    public void start(Stage escenario) throws IOException {
        salida = Path.of(getParameters().getRaw().getFirst());
        Files.createDirectories(salida);
        var servicios = App.crearServicios(Files.createTempDirectory("capturas").resolve("demo.db"));
        sembrar(servicios);
        servicio = servicios.gastos();

        VentanaPrincipal.cargarFuentes();
        App.iniciarSincronizacion(servicios);
        var modelo = new ModeloUi(servicios);
        ventana = new VentanaPrincipal(modelo);
        ventana.mostrar(escenario);
        escena = escenario.getScene();

        Queue<Runnable> pasos = new ArrayDeque<>(List.of(
                ventana::nuevoGasto,
                () -> capturar(escena.getRoot(), "1-resumen"),
                () -> ((ScrollPane) escena.getRoot().lookup(".vista")).setVvalue(1),
                () -> capturar(escena.getRoot(), "1b-resumen-tabla"),
                () -> ((ScrollPane) escena.getRoot().lookup(".vista")).setVvalue(0),
                () -> ventana.editarGasto(servicio.delMes(servicio.mesActual()).get(2)),
                () -> capturar(escena.getRoot(), "2-editar-gasto"),
                () -> ((DatePicker) escena.getRoot().lookup(".date-picker")).show(),
                () -> Window.getWindows().stream().filter(w -> w instanceof PopupWindow)
                        .findFirst().ifPresentOrElse(w -> capturar(w.getScene().getRoot(), "3-calendario"),
                                () -> System.out.println("Sin popup. Ventanas: " + Window.getWindows())),
                () -> ((DatePicker) escena.getRoot().lookup(".date-picker")).hide(),
                ventana::nuevoGasto,
                () -> ir("Categorías"),
                () -> capturar(escena.getRoot(), "4-categorias"),
                () -> ir("Recurrentes"),
                () -> {
                    // El panel con "Se repite" activado, junto a la vista de recurrentes
                    ((ToggleButton) escena.getRoot().lookup(".repetir")).fire();
                    var espera = new PauseTransition(Duration.millis(400)); // antes del siguiente paso (600 ms)
                    espera.setOnFinished(e -> capturar(escena.getRoot(), "4b-recurrentes"));
                    espera.play();
                },
                () -> ((ToggleButton) escena.getRoot().lookup(".repetir")).fire(),
                // Metas y deudas: un paso extra de espera para que termine la entrada en cascada
                () -> ir("Metas"),
                () -> { },
                () -> capturar(escena.getRoot(), "4c-metas"),
                () -> ir("Deudas"),
                () -> { },
                () -> capturar(escena.getRoot(), "4d-deudas"),
                // El panel con "Va a una deuda o meta": primero una deuda, luego una meta
                () -> ((ToggleButton) escena.getRoot().lookup(".destinar")).fire(),
                () -> capturar(escena.getRoot(), "4e-destino-deuda"),
                () -> escena.getRoot().lookupAll(".chip").stream()
                        .filter(n -> n.getUserData() instanceof com.registrogastos.servicio.EstadoMeta)
                        .findFirst().ifPresent(n -> ((ToggleButton) n).fire()),
                () -> capturar(escena.getRoot(), "4f-destino-meta"),
                // Aportar desde el panel: el destino se apaga solo y queda la confirmación
                () -> {
                    ((TextField) escena.getRoot().lookup(".campo-monto")).setText("150");
                    ((Button) escena.getRoot().lookup(".boton-ancho")).fire();
                },
                () -> capturar(escena.getRoot(), "4g-aporte-hecho"),
                // Cuentas y analíticas (el análisis corre en otro hilo: un paso extra de espera)
                () -> ir("Cuentas"),
                () -> { },
                () -> capturar(escena.getRoot(), "4h-cuentas"),
                () -> ir("Sincronizar"),
                () -> capturar(escena.getRoot(), "4m-sincronizar"),
                () -> ir("Presupuesto"),
                () -> capturar(escena.getRoot(), "4k-presupuesto"),
                () -> escena.getRoot().lookupAll(".vista").stream()
                        .filter(n -> n.getClass().getSimpleName().equals("VistaPresupuesto"))
                        .findFirst().ifPresent(n -> ((ScrollPane) n).setVvalue(1)),
                () -> capturar(escena.getRoot(), "4l-presupuesto-abajo"),
                () -> ir("Analíticas"),
                () -> { },
                () -> capturar(escena.getRoot(), "4i-analiticas"),
                () -> desplazarAnaliticas(0.22),
                () -> capturar(escena.getRoot(), "4i2-analiticas-ritmo"),
                () -> desplazarAnaliticas(0.45),
                () -> capturar(escena.getRoot(), "4i3-analiticas-flujo"),
                () -> desplazarAnaliticas(1),
                () -> capturar(escena.getRoot(), "4j-analiticas-abajo"),
                () -> ir("Resumen"),
                modelo::mesSiguiente,
                () -> capturar(escena.getRoot(), "5-mes-vacio"),
                // Registrar sin monto: el error debe salir junto al campo
                ventana::nuevoGasto,
                () -> ((Button) escena.getRoot().lookup(".boton-ancho")).fire(),
                () -> capturar(escena.getRoot(), "6-error-monto"),
                // Monto como lo escribe una persona, sin descripción: debe guardarse
                () -> ((TextField) escena.getRoot().lookup(".campo-monto")).setText("Q 1,250.50"),
                () -> ((Button) escena.getRoot().lookup(".boton-ancho")).fire(),
                () -> capturar(escena.getRoot(), "7-guardado"),
                // Interacción: filtro por día (como un clic en la barra), búsqueda y categoría
                () -> modelo.alternarDia(servicio.hoy().minusDays(4)),
                () -> capturar(escena.getRoot(), "8-filtro-dia"),
                modelo::quitarFiltros,
                () -> modelo.busquedaProperty().set("super"),
                () -> capturar(escena.getRoot(), "9-busqueda"),
                modelo::quitarFiltros,
                () -> modelo.alternarCategoria(servicio.categorias().getFirst().id()),
                () -> capturar(escena.getRoot(), "10-filtro-categoria"),
                modelo::quitarFiltros,
                ventana::ocultarPanel,
                () -> capturar(escena.getRoot(), "11-panel-oculto"),
                ventana::nuevoGasto, // deja el panel visible: la preferencia se guarda para el usuario real
                Platform::exit));
        siguiente(pasos);
    }

    /** Ejecuta un paso cada 600 ms: da tiempo a CSS, layout y animaciones. */
    private static void siguiente(Queue<Runnable> pasos) {
        var pausa = new PauseTransition(Duration.millis(600));
        pausa.setOnFinished(e -> {
            pasos.remove().run();
            if (!pasos.isEmpty()) {
                siguiente(pasos);
            }
        });
        pausa.play();
    }

    /**
     * La ventana se captura como escena (su tamaño exacto): el nodo raíz puede medir más por
     * las sombras que salen de sus bordes, y ese exceso saldría en blanco.
     */
    private void capturar(Node nodo, String nombre) {
        WritableImage imagen = nodo == escena.getRoot() ? escena.snapshot(null) : nodo.snapshot(null, null);
        int ancho = (int) imagen.getWidth();
        int alto = (int) imagen.getHeight();
        var png = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        var lector = imagen.getPixelReader();
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                png.setRGB(x, y, lector.getArgb(x, y));
            }
        }
        try {
            ImageIO.write(png, "png", salida.resolve(nombre + ".png").toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("Captura: " + salida.resolve(nombre + ".png"));
    }

    static void sembrar(Servicios servicios) {
        GastoServicio s = servicios.gastos();
        RecurrenciaServicio r = servicios.recurrencias();
        LocalDate hoy = s.hoy();
        var comida = s.crearCategoria("Comida").id();
        var transporte = s.crearCategoria("Transporte").id();
        var hogar = s.crearCategoria("Hogar").id();
        var ocio = s.crearCategoria("Ocio").id();
        var suscripciones = s.crearCategoria("Suscripciones").id();

        registrar(s, "Café de especialidad", "4.50", hoy, comida);
        registrar(s, "Uber al trabajo", "6.80", hoy, transporte);
        registrar(s, "Supermercado", "86.35", hoy.minusDays(1), comida);
        registrar(s, "Gasolina", "45.00", hoy.minusDays(4), transporte);
        registrar(s, "Cine con amigos", "18.00", hoy.minusDays(4), ocio);
        registrar(s, "Luz", "62.10", hoy.minusDays(7), hogar);
        registrar(s, "Juego en oferta", "29.99", hoy.minusDays(10), ocio);
        registrar(s, "Almuerzo", "12.75", hoy.minusDays(15), comida);
        registrar(s, "Regalo de cumpleaños", "35.00", hoy.minusDays(21), null);

        var mesPasado = hoy.minusMonths(1).withDayOfMonth(12);
        registrar(s, "Supermercado", "120.40", mesPasado, comida);
        registrar(s, "Gasolina", "52.00", mesPasado.plusDays(3), transporte);
        registrar(s, "Luz", "58.70", mesPasado.plusDays(6), hogar);

        // Recurrentes: al crearlas se registran solos los cobros que ya vencieron
        var mensual = Recurrencia.Unidad.MESES;
        r.crear("Netflix", new BigDecimal("15.49"), suscripciones, 1, mensual, hoy.minusDays(2).minusMonths(4));
        r.crear("Spotify", new BigDecimal("10.99"), suscripciones, 1, mensual, hoy.minusDays(20).minusMonths(3));
        r.crear("Agua", new BigDecimal("24.50"), hogar, 2, mensual, hoy.minusDays(10).minusMonths(2));
        r.crear("Dominio web", new BigDecimal("18.00"), suscripciones, 12, mensual, hoy.plusDays(12).minusYears(1));
        r.crear("Gimnasio", new BigDecimal("30.00"), ocio, 1, mensual, hoy.plusDays(6));

        // Metas de ahorro: una a medias con fecha, otra cerca y una ya completada
        MetaServicio m = servicios.metas();
        var emergencia = m.crear("Fondo de emergencia", new BigDecimal("3000"), hoy.plusMonths(8));
        m.aportar(emergencia.id(), new BigDecimal("350"), hoy.minusMonths(2));
        m.aportar(emergencia.id(), new BigDecimal("400"), hoy.minusMonths(1));
        m.aportar(emergencia.id(), new BigDecimal("300"), hoy.minusDays(3));
        var laptop = m.crear("Laptop nueva", new BigDecimal("1200"), hoy.plusMonths(4));
        m.aportar(laptop.id(), new BigDecimal("500"), hoy.minusMonths(1));
        m.aportar(laptop.id(), new BigDecimal("200"), hoy.minusDays(8));
        var bici = m.crear("Bicicleta", new BigDecimal("450"), null);
        m.aportar(bici.id(), new BigDecimal("450"), hoy.minusMonths(2));

        // Deudas: dos que debo (sus abonos son gastos) y una que me deben
        DeudaServicio d = servicios.deudas();
        var tarjeta = d.crear("Tarjeta de crédito", Deuda.Tipo.DEBO, new BigDecimal("1800"),
                hoy.minusMonths(3), hoy.plusMonths(6));
        d.abonar(tarjeta.id(), new BigDecimal("300"), hoy.minusMonths(2), null);
        d.abonar(tarjeta.id(), new BigDecimal("300"), hoy.minusMonths(1), null);
        d.abonar(tarjeta.id(), new BigDecimal("250"), hoy.minusDays(5), null);
        var prestamo = d.crear("Préstamo de Ana", Deuda.Tipo.DEBO, new BigDecimal("500"), hoy.minusMonths(1), null);
        d.abonar(prestamo.id(), new BigDecimal("100"), hoy.minusDays(12), null);
        var concierto = d.crear("Carlos · entradas del concierto", Deuda.Tipo.ME_DEBEN, new BigDecimal("120"),
                hoy.minusDays(10), null);
        d.abonar(concierto.id(), new BigDecimal("40"), hoy.minusDays(2), null);

        // Cuentas: la principal (Efectivo) ya existe; un banco, una tarjeta y una cuenta en dólares
        CuentaServicio c = servicios.cuentas();
        c.editar(Cuenta.PRINCIPAL_ID, "Efectivo", Cuenta.Tipo.EFECTIVO, new BigDecimal("1500"));
        var banco = c.crear("Banco Industrial", Cuenta.Tipo.BANCO, Moneda.GTQ, new BigDecimal("4200"));
        var visa = c.crear("Tarjeta Visa", Cuenta.Tipo.TARJETA, Moneda.GTQ, new BigDecimal("-850"));
        var dolares = c.crear("Ahorro en dólares", Cuenta.Tipo.BANCO, Moneda.USD, new BigDecimal("600"));
        for (int mes = 0; mes < 6; mes++) {
            c.registrarIngreso(banco.id(), "Salario", new BigDecimal(mes == 3 ? "7200" : "6500"),
                    hoy.minusMonths(mes).withDayOfMonth(1));
        }
        c.registrarIngreso(Cuenta.PRINCIPAL_ID, "Venta de la bici vieja", new BigDecimal("900"), hoy.minusMonths(2));
        c.transferir(banco.id(), Cuenta.PRINCIPAL_ID, new BigDecimal("800"), null, hoy.minusDays(9));
        c.transferir(banco.id(), dolares.id(), new BigDecimal("770"), null, hoy.minusDays(20));
        s.registrar("Supermercado", new BigDecimal("212.40"), hoy.minusDays(3), comida, banco.id());
        s.registrar("Juego en Steam", new BigDecimal("12.99"), hoy.minusDays(6), ocio, dolares.id());

        // Presupuesto: el sueldo, lo que normalmente gasta y a qué asigna lo disponible
        var plan = servicios.presupuestos();
        plan.fijarIngresoMensual(new BigDecimal("6500"));
        plan.fijarQuincenal(true);
        var primera = LineaPlan.Quincena.PRIMERA;
        var segunda = LineaPlan.Quincena.SEGUNDA;
        plan.cambiarQuincena(plan.agregar("Renta", new BigDecimal("2200"), LineaPlan.Grupo.GASTO).id(), primera);
        plan.agregar("Comida", new BigDecimal("1200"), LineaPlan.Grupo.GASTO);
        plan.cambiarQuincena(plan.agregar("Luz y agua", new BigDecimal("280"), LineaPlan.Grupo.GASTO).id(), segunda);
        plan.agregar("Transporte", new BigDecimal("450"), LineaPlan.Grupo.GASTO);
        plan.cambiarQuincena(plan.agregar("Internet y celular", new BigDecimal("320"), LineaPlan.Grupo.GASTO).id(), segunda);
        plan.cambiarQuincena(plan.agregar("Tarjeta de crédito", new BigDecimal("300"), LineaPlan.Grupo.GASTO).id(), segunda);
        plan.cambiarQuincena(plan.agregar("Ahorro", new BigDecimal("800"), LineaPlan.Grupo.ASIGNACION).id(), segunda);
        plan.agregar("Fondo de emergencia", new BigDecimal("350"), LineaPlan.Grupo.ASIGNACION);

        // Hábitos para Analíticas: cafés, Ubers y snacks repartidos en los últimos tres meses,
        // y Ocio que sube este mes frente a su costumbre
        for (int i = 0; i < 12; i++) {
            registrar(s, "Café de especialidad", "4.50", hoy.minusDays(i * 7L + 2), comida);
        }
        for (int i = 0; i < 9; i++) {
            s.registrar("Uber al trabajo", new BigDecimal("6.80"), hoy.minusDays(i * 9L + 3), transporte, visa.id());
        }
        for (int i = 0; i < 7; i++) {
            registrar(s, "Snack", "3.25", hoy.minusDays(i * 11L + 5), comida);
        }
        // Historia de los tres meses anteriores, para que "Subieron" compare contra una costumbre real
        for (int mes = 1; mes <= 5; mes++) {
            var dia = hoy.minusMonths(mes).withDayOfMonth(5);
            s.registrar("Supermercado", new BigDecimal("190.00"), dia, comida, banco.id());
            registrar(s, "Almuerzo", "38.00", dia.plusDays(8), comida);
            registrar(s, "Gasolina", "48.00", dia.plusDays(2), transporte);
            registrar(s, "Luz", "60.00", dia.plusDays(4), hogar);
            registrar(s, "Cine", "18.00", dia.plusDays(10), ocio);
        }
    }

    private static void registrar(GastoServicio s, String descripcion, String monto, LocalDate fecha, String categoria) {
        s.registrar(descripcion, new BigDecimal(monto), fecha, categoria);
    }

    /** VistaAnaliticas es privada del paquete ui: se reconoce por el nombre de su clase. */
    private void desplazarAnaliticas(double vvalue) {
        escena.getRoot().lookupAll(".vista").stream()
                .filter(n -> n.getClass().getSimpleName().equals("VistaAnaliticas"))
                .findFirst().ifPresent(n -> ((ScrollPane) n).setVvalue(vvalue));
    }

    /** Cambia de vista con el botón del riel que tiene ese texto (no por posición: el riel crece). */
    private void ir(String texto) {
        escena.getRoot().lookupAll(".nav-item").stream()
                .map(ToggleButton.class::cast)
                .filter(b -> b.getText().equals(texto))
                .findFirst().orElseThrow()
                .fire();
    }

    /** Requiere -Djavafx.enablePreview=true en la línea de comandos (ver App.activarPreview). */
    public static void main(String[] args) {
        launch(args);
    }
}
