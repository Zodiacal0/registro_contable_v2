-- V1: esquema inicial.
-- Regla de oro: una migración ya aplicada NUNCA se edita. Los cambios van en V2, V3...
--
-- Columnas pensadas para la futura sincronización Desktop <-> Mobile:
--   id             UUID en texto: cada dispositivo crea ids sin chocar con el otro.
--   actualizado_en milisegundos epoch: gana el cambio más reciente.
--   eliminado      borrado lógico (0/1): el borrado también debe viajar al otro dispositivo.

CREATE TABLE categorias (
    id             TEXT    PRIMARY KEY,
    nombre         TEXT    NOT NULL,
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

CREATE TABLE gastos (
    id             TEXT    PRIMARY KEY,
    descripcion    TEXT    NOT NULL,
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    fecha          TEXT    NOT NULL,                -- ISO-8601: 2026-10-25
    categoria_id   TEXT    REFERENCES categorias(id),
    creado_en      INTEGER NOT NULL,
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

CREATE INDEX idx_gastos_fecha ON gastos(fecha);
CREATE INDEX idx_gastos_categoria ON gastos(categoria_id);
