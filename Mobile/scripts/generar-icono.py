"""Genera los íconos de la app a partir del logo del escritorio: tres cápsulas apiladas.

La geometría y los colores salen de Desktop/.../ui/estilos.css (.marca-capsula) y de
VentanaPrincipal.marca(): cápsulas de 26×10 con radio 5, superpuestas 4 px (VBox(-4)), en
-c-3 / -c-4 / -c-5, cada una con una sombra suave hacia arriba sobre la anterior. Se dibuja en
vectores (a 4× y luego reducido), así sale nítido a cualquier tamaño.

Uso:  py -3 Mobile/scripts/generar-icono.py     (deja los PNG en Mobile/assets)
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

FONDO = (23, 21, 28)  # #17151c
CAPSULAS = [(113, 104, 121), (170, 162, 179), (226, 221, 231)]  # -c-3, -c-4, -c-5 (de arriba abajo)
ANCHO, ALTO, RADIO, PASO = 26, 10, 5, 6  # unidades del CSS: alto 10 - superposición 4 = paso 6
ALTO_TOTAL = ALTO + PASO * 2  # 22
SUPER = 4  # se dibuja a 4× y se reduce: bordes suaves sin depender del antialias de PIL

ASSETS = Path(__file__).resolve().parent.parent / "assets"


def logo(ancho_px: int, monocromo: bool = False) -> Image.Image:
    """El logo en RGBA, de `ancho_px` de ancho, sobre fondo transparente."""
    escala = ancho_px * SUPER / ANCHO
    margen = round(6 * escala)  # aire para que la sombra no se corte
    w = round(ANCHO * escala) + margen * 2
    h = round(ALTO_TOTAL * escala) + margen * 2
    lienzo = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def forma(i: int, crecer: float = 0) -> Image.Image:
        """La cápsula i como máscara; `crecer` la agranda parejo hacia afuera (en unidades CSS)."""
        mascara = Image.new("L", (w, h), 0)
        g = round(crecer * escala)
        y = margen + round(i * PASO * escala)
        ImageDraw.Draw(mascara).rounded_rectangle(
            (margen - g, y - g, margen + round(ANCHO * escala) - 1 + g, y + round(ALTO * escala) - 1 + g),
            radius=round(RADIO * escala) + g, fill=255)
        return mascara

    for i, color in enumerate(CAPSULAS):
        mascara = forma(i)
        if monocromo:
            # Ícono temático de Android: una sola tinta. Las capas se distinguen con un corte
            # transparente alrededor de la cápsula de delante (en vez de la sombra)
            corte = forma(i, crecer=0.8)
            lienzo.putalpha(ImageChops.subtract(lienzo.getchannel("A"), corte))
            lienzo.paste((255, 255, 255, 255), (0, 0), mascara)
            continue
        if i > 0:
            # dropshadow(gaussian, rgba(0,0,0,0.5), 5, 0, 0, -2): sombra hacia ARRIBA
            sombra = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            alfa = mascara.point(lambda v: v * 0.5)
            alfa = ImageChops.offset(alfa, 0, -round(2 * escala)).filter(ImageFilter.GaussianBlur(2.5 * escala))
            sombra.putalpha(alfa)
            lienzo = Image.alpha_composite(lienzo, sombra)
        capa = Image.new("RGBA", (w, h), color + (255,))
        capa.putalpha(mascara)
        lienzo = Image.alpha_composite(lienzo, capa)

    lienzo = lienzo.resize((w // SUPER, h // SUPER), Image.LANCZOS)
    # Recorta el aire extra y deja solo lo que se ve (incluida la sombra)
    return lienzo.crop(lienzo.getbbox())


def centrado(fondo: Image.Image, figura: Image.Image) -> Image.Image:
    x = (fondo.width - figura.width) // 2
    y = (fondo.height - figura.height) // 2
    fondo.alpha_composite(figura, (x, y))
    return fondo


def main() -> None:
    lado = 1024
    # Ícono clásico (y el de la tienda): fondo de la app con el logo al 56 % del ancho
    icono = centrado(Image.new("RGBA", (lado, lado), FONDO + (255,)), logo(round(lado * 0.56)))
    icono.convert("RGB").save(ASSETS / "icon.png")

    # Adaptativo: Android recorta el lienzo (círculo, squircle...). Lo seguro es el círculo
    # central de 66/108 del lado: el logo al 46 % del ancho cabe con su diagonal adentro
    centrado(Image.new("RGBA", (lado, lado), (0, 0, 0, 0)), logo(round(lado * 0.46))) \
        .save(ASSETS / "android-icon-foreground.png")
    Image.new("RGB", (lado, lado), FONDO).save(ASSETS / "android-icon-background.png")
    centrado(Image.new("RGBA", (lado, lado), (0, 0, 0, 0)), logo(round(lado * 0.46), monocromo=True)) \
        .save(ASSETS / "android-icon-monochrome.png")

    # Splash (pantalla de arranque): solo el logo; el fondo lo pone app.json
    logo(512).save(ASSETS / "splash-icon.png")
    # Favicon (solo web), por consistencia
    centrado(Image.new("RGBA", (48, 48), FONDO + (255,)), logo(30)).save(ASSETS / "favicon.png")
    print("Íconos generados en", ASSETS)


if __name__ == "__main__":
    main()
