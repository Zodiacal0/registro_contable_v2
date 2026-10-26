-- V9: plan por quincena.

-- En qué quincena se paga cada línea: PRIMERA, SEGUNDA o AMBAS (mitad en cada una).
ALTER TABLE plan_lineas ADD COLUMN quincena TEXT NOT NULL DEFAULT 'AMBAS'
    CHECK (quincena IN ('PRIMERA', 'SEGUNDA', 'AMBAS'));

-- Si el usuario cobra por quincena ('1') o una vez al mes ('0').
INSERT INTO ajustes (clave, valor, actualizado_en) VALUES ('pago_quincenal', '0', 0);
