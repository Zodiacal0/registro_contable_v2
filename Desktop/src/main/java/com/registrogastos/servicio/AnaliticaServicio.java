package com.registrogastos.servicio;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Ingreso;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.IngresoRepositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Analiza los hábitos de gasto, todo en el equipo (sin servicios externos). Reglas simples y
 * explicables a propósito: cada número de la vista se puede rastrear hasta su cálculo.
 *
 * La parte que calcula ({@link #analizar}) es estática y recibe todos los datos: no toca la
 * base, así que las pruebas le pasan listas armadas a mano.
 */
public final class AnaliticaServicio {

    /** Ventana de análisis: 90 días (≈ 3 meses), suficiente para ver hábitos sin arrastrar el pasado. */
    static final int DIAS = 90;
    private static final BigDecimal MESES = BigDecimal.valueOf(3);
    /** Una compra de hasta este monto (en moneda principal) cuenta como "hormiga". */
    static final BigDecimal UMBRAL_HORMIGA = new BigDecimal("50.00");
    /** Veces mínimas en el periodo para llamarlo hábito. */
    static final int MIN_VECES = 3;
    /** Una categoría "subió" si pasa su promedio en al menos 20 % y Q20. */
    private static final BigDecimal SUBIDA_MINIMA_PCT = BigDecimal.valueOf(20);
    private static final BigDecimal SUBIDA_MINIMA_MONTO = new BigDecimal("20");
    /** Meses del comparativo de ingresos y gastos (incluido el actual). */
    static final int MESES_FLUJO = 6;

    private final GastoRepositorio gastos;
    private final IngresoRepositorio ingresos;
    private final GastoServicio gastoServicio;
    private final RecurrenciaServicio recurrencias;
    private final MetaServicio metas;
    private final Supplier<Conversor> conversor;
    private final Clock reloj;

    public AnaliticaServicio(GastoRepositorio gastos, IngresoRepositorio ingresos, GastoServicio gastoServicio,
                             RecurrenciaServicio recurrencias, MetaServicio metas, Supplier<Conversor> conversor,
                             Clock reloj) {
        this.gastos = gastos;
        this.ingresos = ingresos;
        this.gastoServicio = gastoServicio;
        this.recurrencias = recurrencias;
        this.metas = metas;
        this.conversor = conversor;
        this.reloj = reloj;
    }

    /** Lee lo necesario y analiza. Puede correr fuera del hilo de la interfaz. */
    public Analisis analizar() {
        LocalDate hoy = LocalDate.now(reloj);
        // La ventana más larga que se necesita: 120 días (90 + 30 de tendencias) o los meses del flujo
        LocalDate inicioFlujo = YearMonth.from(hoy).minusMonths(MESES_FLUJO - 1).atDay(1);
        LocalDate inicioTendencias = hoy.minusDays(DIAS + 29);
        LocalDate desde = inicioTendencias.isBefore(inicioFlujo) ? inicioTendencias : inicioFlujo;
        List<Gasto> lista = gastos.listarEntre(desde, hoy);
        List<EstadoMeta> estados = metas.listar();
        Map<String, List<Abono>> aportes = new HashMap<>();
        estados.forEach(e -> aportes.put(e.meta().id(), metas.aportesDe(e.meta().id())));
        return analizar(lista, ingresos.listarEntre(desde, hoy), gastoServicio.categorias(), recurrencias.listar(),
                estados, aportes, conversor.get(), hoy);
    }

    /** Sin ingresos: a las pruebas de hábitos no les hacen falta. */
    static Analisis analizar(List<Gasto> todos, List<Categoria> categorias, List<Recurrencia> recurrentes,
                             List<EstadoMeta> estadosMeta, Map<String, List<Abono>> aportes,
                             Conversor conv, LocalDate hoy) {
        return analizar(todos, List.of(), categorias, recurrentes, estadosMeta, aportes, conv, hoy);
    }

    static Analisis analizar(List<Gasto> todos, List<Ingreso> ingresosLista, List<Categoria> categorias,
                             List<Recurrencia> recurrentes, List<EstadoMeta> estadosMeta,
                             Map<String, List<Abono>> aportes, Conversor conv, LocalDate hoy) {
        LocalDate desde = hoy.minusDays(DIAS - 1);
        List<Gasto> sinDeudas = todos.stream().filter(g -> !g.esAbono()).toList();
        List<Gasto> periodo = sinDeudas.stream().filter(g -> !g.fecha().isBefore(desde) && !g.fecha().isAfter(hoy)).toList();
        BigDecimal totalPeriodo = suma(periodo, conv);
        BigDecimal gastoMensual = porMes(totalPeriodo);

        // Lo recurrente ya tiene su sección: los hábitos son lo que se compra "a mano"
        List<Gasto> aMano = periodo.stream().filter(g -> !g.esRecurrente()).toList();

        List<Analisis.GastoFrecuente> frecuentes = frecuentes(aMano, conv);
        Analisis.Hormiga hormiga = hormiga(aMano, conv, totalPeriodo);
        List<Analisis.ComparativoCategoria> porCategoria = porCategoria(sinDeudas, categorias, conv, hoy);
        List<Analisis.Tendencia> tendencias = tendencias(porCategoria);
        Analisis.Suscripciones suscripciones = suscripciones(recurrentes, conv, gastoMensual);
        List<Analisis.IdeaAhorro> ideas = ideas(aMano, frecuentes, tendencias, suscripciones, conv);

        BigDecimal ahorro = ideas.stream().filter(Analisis.IdeaAhorro::suma)
                .map(Analisis.IdeaAhorro::porMes).reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<Analisis.ImpactoMeta> impacto = impacto(estadosMeta, aportes, ahorro, desde);

        return new Analisis(desde, hoy, periodo.size(), gastoMensual, frecuentes, hormiga, tendencias,
                suscripciones, ideas, impacto, ritmo(todos, conv, hoy), flujo(todos, ingresosLista, conv, hoy),
                porCategoria);
    }

    // ---- Cada sección ----

    /** Misma descripción (sin importar mayúsculas ni tildes) al menos MIN_VECES; las 5 más repetidas. */
    private static List<Analisis.GastoFrecuente> frecuentes(List<Gasto> aMano, Conversor conv) {
        return agrupar(aMano).values().stream()
                .filter(grupo -> grupo.size() >= MIN_VECES)
                .map(grupo -> {
                    BigDecimal total = suma(grupo, conv);
                    // La lista viene del más reciente al más antiguo: el nombre es como lo escribiste último
                    return new Analisis.GastoFrecuente(grupo.getFirst().descripcion(), grupo.size(), total, porMes(total));
                })
                .sorted(Comparator.comparingInt(Analisis.GastoFrecuente::veces).reversed()
                        .thenComparing(Analisis.GastoFrecuente::total, Comparator.reverseOrder()))
                .limit(5)
                .toList();
    }

    private static Analisis.Hormiga hormiga(List<Gasto> aMano, Conversor conv, BigDecimal totalPeriodo) {
        List<Gasto> chicas = aMano.stream().filter(g -> conv.aPrincipal(g).compareTo(UMBRAL_HORMIGA) <= 0).toList();
        BigDecimal total = suma(chicas, conv);
        List<String> ejemplos = agrupar(chicas).values().stream()
                .sorted(Comparator.comparingInt(List<Gasto>::size).reversed())
                .limit(3)
                .map(grupo -> grupo.getFirst().descripcion())
                .toList();
        return new Analisis.Hormiga(UMBRAL_HORMIGA, chicas.size(), porMes(total), proporcion(total, totalPeriodo), ejemplos);
    }

    /**
     * Cada categoría con gasto reciente o anterior: últimos 30 días contra el promedio mensual
     * de los 90 anteriores. De mayor a menor gasto reciente.
     */
    private static List<Analisis.ComparativoCategoria> porCategoria(List<Gasto> sinDeudas, List<Categoria> categorias,
                                                                    Conversor conv, LocalDate hoy) {
        LocalDate inicioReciente = hoy.minusDays(29);
        LocalDate inicioPrevio = inicioReciente.minusDays(DIAS);
        Map<String, Categoria> porId = categorias.stream().collect(Collectors.toMap(Categoria::id, c -> c));
        Map<String, BigDecimal> reciente = new HashMap<>();
        Map<String, BigDecimal> previo = new HashMap<>();
        for (Gasto g : sinDeudas) {
            // Categoría eliminada o ninguna: todo cae en "" (sin categoría)
            String clave = porId.containsKey(g.categoriaId()) ? g.categoriaId() : "";
            if (!g.fecha().isBefore(inicioReciente) && !g.fecha().isAfter(hoy)) {
                reciente.merge(clave, conv.aPrincipal(g), BigDecimal::add);
            } else if (!g.fecha().isBefore(inicioPrevio) && g.fecha().isBefore(inicioReciente)) {
                previo.merge(clave, conv.aPrincipal(g), BigDecimal::add);
            }
        }
        var claves = new HashSet<>(reciente.keySet());
        claves.addAll(previo.keySet());
        return claves.stream()
                .map(clave -> new Analisis.ComparativoCategoria(porId.get(clave),
                        reciente.getOrDefault(clave, BigDecimal.ZERO), porMes(previo.getOrDefault(clave, BigDecimal.ZERO))))
                .sorted(Comparator.comparing(Analisis.ComparativoCategoria::ultimos30).reversed())
                .toList();
    }

    /** Las que subieron: pasan su promedio en al menos 20 % y Q20 (sin historia no hay costumbre que comparar). */
    private static List<Analisis.Tendencia> tendencias(List<Analisis.ComparativoCategoria> porCategoria) {
        var resultado = new ArrayList<Analisis.Tendencia>();
        for (var c : porCategoria) {
            BigDecimal promedio = c.promedio();
            if (promedio.signum() == 0) {
                continue;
            }
            BigDecimal exceso = c.ultimos30().subtract(promedio);
            BigDecimal variacion = exceso.multiply(BigDecimal.valueOf(100)).divide(promedio, 1, RoundingMode.HALF_UP);
            if (variacion.compareTo(SUBIDA_MINIMA_PCT) >= 0 && exceso.compareTo(SUBIDA_MINIMA_MONTO) >= 0) {
                resultado.add(new Analisis.Tendencia(c.categoria(), c.ultimos30(), promedio, variacion));
            }
        }
        resultado.sort(Comparator.comparing(Analisis.Tendencia::exceso).reversed());
        return resultado.stream().limit(4).toList();
    }

    /** Acumulado diario del mes en curso (hasta hoy) y del anterior (completo). Con abonos, como el Resumen. */
    private static Analisis.Ritmo ritmo(List<Gasto> todos, Conversor conv, LocalDate hoy) {
        YearMonth mes = YearMonth.from(hoy);
        YearMonth anterior = mes.minusMonths(1);
        return new Analisis.Ritmo(mes, acumulado(todos, mes, hoy.getDayOfMonth(), conv),
                acumulado(todos, anterior, anterior.lengthOfMonth(), conv));
    }

    private static List<BigDecimal> acumulado(List<Gasto> todos, YearMonth mes, int dias, Conversor conv) {
        var porDia = new BigDecimal[dias];
        Arrays.fill(porDia, BigDecimal.ZERO);
        for (Gasto g : todos) {
            if (YearMonth.from(g.fecha()).equals(mes) && g.fecha().getDayOfMonth() <= dias) {
                int i = g.fecha().getDayOfMonth() - 1;
                porDia[i] = porDia[i].add(conv.aPrincipal(g));
            }
        }
        var resultado = new ArrayList<BigDecimal>(dias);
        BigDecimal suma = BigDecimal.ZERO;
        for (BigDecimal valor : porDia) {
            suma = suma.add(valor);
            resultado.add(suma);
        }
        return resultado;
    }

    /** Ingresos y gastos (con abonos: también es dinero que sale) de los últimos MESES_FLUJO meses. */
    private static List<Analisis.PuntoMes> flujo(List<Gasto> todos, List<Ingreso> ingresosLista, Conversor conv,
                                                  LocalDate hoy) {
        var resultado = new ArrayList<Analisis.PuntoMes>();
        for (int k = MESES_FLUJO - 1; k >= 0; k--) {
            YearMonth mes = YearMonth.from(hoy).minusMonths(k);
            BigDecimal gastado = todos.stream().filter(g -> YearMonth.from(g.fecha()).equals(mes))
                    .map(conv::aPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ingresado = ingresosLista.stream().filter(i -> YearMonth.from(i.fecha()).equals(mes))
                    .map(i -> conv.aPrincipal(i.monto(), conv.monedaDe(i.cuentaId())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            resultado.add(new Analisis.PuntoMes(mes, gastado, ingresado));
        }
        return resultado;
    }

    private static Analisis.Suscripciones suscripciones(List<Recurrencia> recurrentes, Conversor conv,
                                                       BigDecimal gastoMensual) {
        List<Analisis.CostoRecurrente> lista = recurrentes.stream()
                .filter(Recurrencia::activa)
                .map(r -> {
                    BigDecimal mes = conv.aPrincipal(r.costoMensual(), conv.monedaDe(r.cuentaId()));
                    return new Analisis.CostoRecurrente(r, mes, mes.multiply(BigDecimal.valueOf(12)));
                })
                .sorted(Comparator.comparing(Analisis.CostoRecurrente::porMes).reversed())
                .toList();
        BigDecimal mes = lista.stream().map(Analisis.CostoRecurrente::porMes).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Analisis.Suscripciones(lista, mes, mes.multiply(BigDecimal.valueOf(12)), proporcion(mes, gastoMensual));
    }

    /**
     * Ideas concretas, de mayor a menor ahorro. Las que "suman" son disjuntas a propósito: la
     * hormiga se calcula SIN los gastos de los frecuentes que ya tienen su propia idea.
     */
    private static List<Analisis.IdeaAhorro> ideas(List<Gasto> aMano, List<Analisis.GastoFrecuente> frecuentes,
                                                   List<Analisis.Tendencia> tendencias,
                                                   Analisis.Suscripciones suscripciones, Conversor conv) {
        var ideas = new ArrayList<Analisis.IdeaAhorro>();
        List<Analisis.GastoFrecuente> top = frecuentes.stream().limit(3).toList();
        Set<String> cubiertos = top.stream().map(f -> clave(f.nombre())).collect(Collectors.toSet());
        for (var f : top) {
            ideas.add(new Analisis.IdeaAhorro(Analisis.IdeaAhorro.Tipo.REDUCIR_FRECUENTE, f.nombre(), mitad(f.porMes()), true));
        }

        List<Gasto> chicasRestantes = aMano.stream()
                .filter(g -> conv.aPrincipal(g).compareTo(UMBRAL_HORMIGA) <= 0)
                .filter(g -> !cubiertos.contains(clave(g.descripcion())))
                .toList();
        if (chicasRestantes.size() >= MIN_VECES) {
            ideas.add(new Analisis.IdeaAhorro(Analisis.IdeaAhorro.Tipo.REDUCIR_HORMIGA, "",
                    mitad(porMes(suma(chicasRestantes, conv))), true));
        }
        for (var t : tendencias) {
            ideas.add(new Analisis.IdeaAhorro(Analisis.IdeaAhorro.Tipo.VOLVER_AL_PROMEDIO,
                    t.categoria() == null ? "Sin categoría" : t.categoria().nombre(), t.exceso(), false));
        }
        suscripciones.lista().stream().findFirst().ifPresent(c -> ideas.add(new Analisis.IdeaAhorro(
                Analisis.IdeaAhorro.Tipo.REVISAR_RECURRENTE, c.recurrencia().descripcion(), c.porMes(), false)));

        ideas.removeIf(i -> i.porMes().signum() <= 0);
        // Primero las que forman el total (lo que el número grande promete), luego las demás
        ideas.sort(Comparator.comparing(Analisis.IdeaAhorro::suma).reversed()
                .thenComparing(Analisis.IdeaAhorro::porMes, Comparator.reverseOrder()));
        return ideas;
    }

    /**
     * La meta pendiente más avanzada (la primera de {@link MetaServicio#listar()}): en cuántos
     * meses llega al ritmo de aportes de los últimos 90 días y en cuántos si se le suma el ahorro.
     */
    private static Optional<Analisis.ImpactoMeta> impacto(List<EstadoMeta> estados, Map<String, List<Abono>> aportes,
                                                          BigDecimal ahorro, LocalDate desde) {
        if (ahorro.signum() <= 0) {
            return Optional.empty();
        }
        return estados.stream().filter(e -> !e.completada()).findFirst().map(e -> {
            BigDecimal aportado = aportes.getOrDefault(e.meta().id(), List.of()).stream()
                    .filter(a -> !a.fecha().isBefore(desde))
                    .map(Abono::monto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ritmo = porMes(aportado);
            Integer alRitmo = ritmo.signum() > 0 ? meses(e.restante(), ritmo) : null;
            int conAhorro = meses(e.restante(), ritmo.max(BigDecimal.ZERO).add(ahorro));
            return new Analisis.ImpactoMeta(e.meta().nombre(), e.restante(), alRitmo, conAhorro, ahorro);
        });
    }

    // ---- Utilidades ----

    /** Agrupa por descripción normalizada, conservando el orden (más reciente primero). */
    private static Map<String, List<Gasto>> agrupar(List<Gasto> lista) {
        return lista.stream().collect(Collectors.groupingBy(g -> clave(g.descripcion()), LinkedHashMap::new,
                Collectors.toList()));
    }

    /** "  Café " y "cafe" son lo mismo: minúsculas, sin tildes ni espacios sobrantes. */
    static String clave(String descripcion) {
        return Normalizer.normalize(descripcion.strip().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static BigDecimal suma(List<Gasto> lista, Conversor conv) {
        return lista.stream().map(conv::aPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal porMes(BigDecimal totalDelPeriodo) {
        return totalDelPeriodo.divide(MESES, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal mitad(BigDecimal monto) {
        return monto.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
    }

    private static double proporcion(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? 0 : parte.divide(total, 4, RoundingMode.HALF_UP).doubleValue();
    }

    /** Meses enteros para juntar {@code restante} a {@code porMes}: se redondea hacia arriba. */
    private static int meses(BigDecimal restante, BigDecimal porMes) {
        return restante.divide(porMes, 0, RoundingMode.CEILING).max(BigDecimal.ONE).intValue();
    }
}
