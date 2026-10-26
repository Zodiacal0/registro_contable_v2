-- V8: plan mensual armado a mano.

-- Cada línea es algo que el usuario espera cada mes. Dos grupos:
--   GASTO:      lo que normalmente paga (renta, luz, comida...). Se resta del sueldo.
--   ASIGNACION: a qué destina lo que queda disponible (ahorro, ocio, imprevistos...).
-- Montos en moneda principal. El orden es el de creación.
CREATE TABLE plan_lineas (
    id             TEXT    PRIMARY KEY,
    nombre         TEXT    NOT NULL,
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    grupo          TEXT    NOT NULL CHECK (grupo IN ('GASTO', 'ASIGNACION')),
    orden          INTEGER NOT NULL,
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- El sueldo (ingreso esperado al mes) es un ajuste más; vacío = usar lo registrado en Cuentas.
INSERT INTO ajustes (clave, valor, actualizado_en) VALUES ('ingreso_mensual', '', 0);
