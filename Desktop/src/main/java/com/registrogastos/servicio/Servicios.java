package com.registrogastos.servicio;

/**
 * Todos los casos de uso de la app, ya conectados a la misma base. Un record como "tupla con
 * nombre": evita constructores con una lista de parámetros que crece con cada función nueva.
 */
public record Servicios(
        GastoServicio gastos,
        RecurrenciaServicio recurrencias,
        MetaServicio metas,
        DeudaServicio deudas,
        CuentaServicio cuentas,
        AnaliticaServicio analitica,
        PresupuestoServicio presupuestos) {
}
