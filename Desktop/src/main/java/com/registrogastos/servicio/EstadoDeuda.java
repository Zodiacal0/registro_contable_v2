package com.registrogastos.servicio;

import com.registrogastos.dominio.Deuda;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Una deuda con lo pagado hasta ahora; saldo y progreso se derivan. */
public record EstadoDeuda(Deuda deuda, BigDecimal pagado) {

    public BigDecimal saldo() {
        return deuda.monto().subtract(pagado).max(BigDecimal.ZERO);
    }

    public double proporcionPagada() {
        return pagado.divide(deuda.monto(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    public boolean saldada() {
        return saldo().signum() == 0;
    }
}
