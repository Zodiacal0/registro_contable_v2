-- V5: gastos recurrentes (suscripciones, servicios, membresías).
--
-- Una recurrencia describe un cobro que se repite: "cada" N "unidad" (DIAS o MESES) desde
-- "inicio". Mensual = 1 MESES, anual = 12 MESES, cada 15 días = 15 DIAS.
-- generada_hasta: hasta qué fecha ya se generaron sus gastos (optimización; la protección
-- real contra duplicados es el id determinístico de cada gasto generado).

CREATE TABLE recurrencias (
    id             TEXT    PRIMARY KEY,
    descripcion    TEXT    NOT NULL,
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    categoria_id   TEXT    REFERENCES categorias(id),
    cada           INTEGER NOT NULL CHECK (cada > 0),
    unidad         TEXT    NOT NULL CHECK (unidad IN ('DIAS', 'MESES')),
    inicio         TEXT    NOT NULL,
    generada_hasta TEXT,
    activa         INTEGER NOT NULL DEFAULT 1 CHECK (activa IN (0, 1)),
    actualizado_en INTEGER NOT NULL,
    eliminado      INTEGER NOT NULL DEFAULT 0 CHECK (eliminado IN (0, 1))
);

-- Cada gasto generado sabe de qué recurrencia salió (NULL = gasto normal)
ALTER TABLE gastos ADD COLUMN recurrencia_id TEXT REFERENCES recurrencias(id);

CREATE INDEX idx_gastos_recurrencia ON gastos(recurrencia_id);
