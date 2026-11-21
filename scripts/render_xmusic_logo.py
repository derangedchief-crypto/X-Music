"""Render X-Music's vector into the existing Android icon variants.

Requires Pillow. Run from the repository root.
"""

from pathlib import Path

from PIL import Image, ImageDraw

size = 1024
mask = Image.new("L", (size, size))
draw = ImageDraw.Draw(mask)
for points in [[(25,24),(40,24),(83,84),(68,84)], [(68,24),(83,24),(62,53),(54,42)], [(46,55),(54,66),(40,84),(25,84)]]:
    draw.polygon([(round(x * size / 108), round(y * size / 108)) for x, y in points], fill=255)
gradient = Image.new("RGBA", (size, size))
pixels = gradient.load()
for y in range(size):
    for x in range(size):
        t = min(1, max(0, ((x + y) * 108 / size - 49) / 118))
        pixels[x, y] = tuple(round(a + (b - a) * t) for a, b in zip((64, 227, 239), (167, 120, 255))) + (255,)
gradient.putalpha(mask)
logo = Image.new("RGBA", (size, size), "#090D17")
logo.alpha_composite(gradient)
logo.resize((512,512), Image.Resampling.LANCZOS).save("assets/xmusic/xmusic-logo.png")
gradient.resize((512,512), Image.Resampling.LANCZOS).save("shared/src/commonMain/composeResources/drawable/logo.png")
directory = Path("androidApp/src/main/res/drawable-nodpi")
for path in directory.glob("app_icon_*_foreground.webp"):
    image = gradient.copy()
    if "zenith_black" in path.name:
        image = Image.new("RGBA", (size, size), "#090D17")
    elif "zenith_white" in path.name:
        image = Image.new("RGBA", (size, size), "white")
    image.putalpha(mask)
    image.resize((512,512), Image.Resampling.LANCZOS).save(path, lossless=True)
for path in directory.glob("app_icon_*_full.webp"):
    name = path.name.replace("_full", "_foreground")
    foreground = Image.open(directory / name).convert("RGBA")
    background_path = directory / path.name.replace("_full", "_background")
    if background_path.exists():
        background = Image.open(background_path).convert("RGBA").resize(foreground.size)
    else:
        background = Image.new("RGBA", foreground.size, "white" if "zenith_black" in name else "#090D17")
    background.alpha_composite(foreground)
    background.save(path, lossless=True)
