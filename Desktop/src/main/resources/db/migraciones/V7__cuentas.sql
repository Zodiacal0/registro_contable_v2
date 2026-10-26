-- V7: cuentas (de dónde sale el dinero), ingresos, transferencias y tipo de cambio.

-- Cada cuenta tiene UNA moneda. El saldo no se guarda: se calcula con el saldo inicial,
-- los ingresos, los gastos y las transferencias.
CREATE TABLE cuentas (
    id                     TEXT    PRIMARY KEY,
    nombre                 TEXT    NOT NULL,
    tipo                   TEXT    NOT NULL CHECK (tipo IN ('EFECTIVO', 'BANCO', 'TARJETA', 'OTRA')),
    moneda                 TEXT    NOT NULL CHECK (moneda IN ('GTQ', 'USD')),
    saldo_inicial_centavos INTEGER NOT NULL DEFAULT 0,          -- puede ser negativo (tarjeta)
    actualizado_en         INTEGER NOT NULL,
    eliminado              INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- Dinero que entra a una cuenta (salario, venta, regalo).
CREATE TABLE ingresos (
    id             TEXT    PRIMARY KEY,
    cuenta_id      TEXT    NOT NULL REFERENCES cuentas(id),
    descripcion    TEXT    NOT NULL,
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    fecha          TEXT    NOT NULL,
    creado_en      INTEGER NOT NULL,
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- Mover dinero entre cuentas. Dos montos porque las monedas pueden ser distintas:
-- salen US$ 100 de una y entran Q 770 a la otra. No es gasto ni ingreso.
CREATE TABLE transferencias (
    id                TEXT    PRIMARY KEY,
    origen_id         TEXT    NOT NULL REFERENCES cuentas(id),
    destino_id        TEXT    NOT NULL REFERENCES cuentas(id),
    salida_centavos   INTEGER NOT NULL CHECK (salida_centavos > 0),
    entrada_centavos  INTEGER NOT NULL CHECK (entrada_centavos > 0),
    fecha             TEXT    NOT NULL,
    creado_en         INTEGER NOT NULL,
    actualizado_en    INTEGER NOT NULL,
    eliminado         INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1)),
    CHECK (origen_id <> destino_id)
);

-- Ajustes clave/valor (por ahora, el tipo de cambio). actualizado_en para sincronizar.
CREATE TABLE ajustes (
    clave          TEXT    PRIMARY KEY,
    valor          TEXT    NOT NULL,
    actualizado_en INTEGER NOT NULL
);

-- La cuenta principal tiene un id FIJO (no aleatorio): así la que crea cada dispositivo es la
-- misma al sincronizar, en vez de terminar con dos "Efectivo".
INSERT INTO cuentas (id, nombre, tipo, moneda, saldo_inicial_centavos, actualizado_en)
VALUES ('00000000-0000-4000-8000-000000000001', 'Efectivo', 'EFECTIVO', 'GTQ', 0, 0);

INSERT INTO ajustes (clave, valor, actualizado_en) VALUES ('tipo_cambio_USD', '7.70', 0);

-- Todo lo registrado hasta ahora sale de la cuenta principal.
ALTER TABLE gastos ADD COLUMN cuenta_id TEXT REFERENCES cuentas(id);
UPDATE gastos SET cuenta_id = '00000000-0000-4000-8000-000000000001';
ALTER TABLE recurrencias ADD COLUMN cuenta_id TEXT REFERENCES cuentas(id);
UPDATE recurrencias SET cuenta_id = '00000000-0000-4000-8000-000000000001';

CREATE INDEX idx_gastos_cuenta ON gastos(cuenta_id);
CREATE INDEX idx_ingresos_cuenta ON ingresos(cuenta_id);
CREATE INDEX idx_transferencias_origen ON transferencias(origen_id);
CREATE INDEX idx_transferencias_destino ON transferencias(destino_id);
