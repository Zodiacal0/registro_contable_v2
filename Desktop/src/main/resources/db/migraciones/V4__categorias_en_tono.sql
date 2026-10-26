-- V4: los colores de categoría pasan a tintes de la misma familia gris violácea de la UI.
-- Seis tintes que se separan por luminosidad Y por un giro de tono (lavanda, violeta, malva,
-- ciruela oscura, pervinca, ciruela), sin usar #E2DDE7, que es el color del texto y de la
-- acción principal. Traducción por posición desde la paleta de V3.

UPDATE categorias SET color = CASE color
    WHEN '#9cc2e8' THEN '#cbbfe0'
    WHEN '#b6a6e9' THEN '#8a7aa8'
    WHEN '#93d3c3' THEN '#d9a9c4'
    WHEN '#e8b394' THEN '#6f6488'
    WHEN '#e5a3d0' THEN '#a6b0d9'
    WHEN '#b4d39a' THEN '#b38aa6'
    WHEN '#dccb95' THEN '#cbbfe0'
    WHEN '#cfa2df' THEN '#8a7aa8'
    ELSE color
END;
