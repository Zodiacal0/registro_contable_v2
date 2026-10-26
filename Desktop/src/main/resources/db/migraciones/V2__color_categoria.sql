-- V2: cada categoría guarda su color (#RRGGBB) para que sea el mismo en todos los dispositivos.
-- SQLite solo permite ADD COLUMN con DEFAULT constante; las filas existentes toman el azul base.

ALTER TABLE categorias ADD COLUMN color TEXT NOT NULL DEFAULT '#66c0f4';
