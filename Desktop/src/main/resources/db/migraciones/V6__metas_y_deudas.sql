-- V6: metas de ahorro y deudas.

-- Una meta junta dinero hacia un objetivo; sus aportes NO son gastos (es dinero apartado).
CREATE TABLE metas (
    id                 TEXT    PRIMARY KEY,
    nombre             TEXT    NOT NULL,
    objetivo_centavos  INTEGER NOT NULL CHECK (objetivo_centavos > 0),
    fecha_objetivo     TEXT,                                  -- opcional
    color              TEXT    NOT NULL,
    actualizado_en     INTEGER NOT NULL,
    eliminado          INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- Aporte positivo = guardar; negativo = retirar de la meta.
CREATE TABLE aportes (
    id             TEXT    PRIMARY KEY,
    meta_id        TEXT    NOT NULL REFERENCES metas(id),
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos <> 0),
    fecha          TEXT    NOT NULL,
    creado_en      INTEGER NOT NULL,
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- DEBO: lo que debo (tarjeta, préstamo). ME_DEBEN: lo que me deben a mí.
CREATE TABLE deudas (
    id             TEXT    PRIMARY KEY,
    nombre         TEXT    NOT NULL,
    tipo           TEXT    NOT NULL CHECK (tipo IN ('DEBO', 'ME_DEBEN')),
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    fecha          TEXT    NOT NULL,                          -- cuándo empezó
    fecha_limite   TEXT,                                      -- opcional
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- Lo que me pagan de una deuda ME_DEBEN (no es un gasto).
CREATE TABLE cobros_deuda (
    id             TEXT    PRIMARY KEY,
    deuda_id       TEXT    NOT NULL REFERENCES deudas(id),
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    fecha          TEXT    NOT NULL,
    creado_en      INTEGER NOT NULL,
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- Un abono a una deuda DEBO ES un gasto: el gasto guarda a qué deuda pagó.
-- Una sola fuente de verdad: borrar el gasto en Movimientos devuelve el saldo a la deuda.
ALTER TABLE gastos ADD COLUMN deuda_id TEXT REFERENCES deudas(id);

CREATE INDEX idx_aportes_meta ON aportes(meta_id);
CREATE INDEX idx_cobros_deuda ON cobros_deuda(deuda_id);
CREATE INDEX idx_gastos_deuda ON gastos(deuda_id);
