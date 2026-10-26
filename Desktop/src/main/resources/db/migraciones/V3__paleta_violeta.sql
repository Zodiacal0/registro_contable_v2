-- V3: la línea gráfica pasó a la paleta gris violácea; los colores de categoría guardados
-- con la paleta anterior se traducen a su equivalente (misma posición) en la nueva.
-- Migración de DATOS, no de esquema: también se versiona y nunca se edita una vez aplicada.

UPDATE categorias SET color = CASE color
    WHEN '#66c0f4' THEN '#9cc2e8'
    WHEN '#a78bfa' THEN '#b6a6e9'
    WHEN '#2dd4bf' THEN '#93d3c3'
    WHEN '#fb923c' THEN '#e8b394'
    WHEN '#f472b6' THEN '#e5a3d0'
    WHEN '#a3e635' THEN '#b4d39a'
    WHEN '#818cf8' THEN '#dccb95'
    WHEN '#e879f9' THEN '#cfa2df'
    ELSE color
END;
