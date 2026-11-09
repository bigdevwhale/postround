"""Builds the Google Play graphics in docs/google-play/graphics from the README screenshots.

python tools/play.py

Takes docs/screenshots/<lang>/*.jpg (1080 px wide renders of the demo, see tools/shots.py) and writes
the 512 px icon, the 1024x500 feature graphics and eight 1080x1920 captioned screenshots per language.
Play wants screenshots no longer than twice their width, so each screen is cut short under its caption:
the map screens keep their top, the sheets (door, letter, morning) keep their bottom, where the card is.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/google-play/graphics"
SHOTS = ROOT / "docs/screenshots"
FONT = ROOT / "app/src/main/res/font/m6x11.ttf"

BLUE = (0x2F, 0x4A, 0x80)
BLUE_DEEP = (0x1A, 0x2A, 0x4A)
LIGHT = (0xFB, 0xF3, 0xDC)
INK = (0x22, 0x16, 0x0F)
LAMP = (0xF4, 0xCC, 0x5A)
RED = (0xA3, 0x3A, 0x2C)

# The order they appear in the store, which end of the screen to keep, and the caption: a light line and a lamp one.
PAGES = [
    ("1-map", "top", ("Your own streets,", "real house numbers"), ("Ваши улицы,", "настоящие номера домов")),
    ("5-door", "bottom", ("Mail is handed over", "only at the door"), ("Письмо вручают", "только у двери")),
    ("2-bag", "top", ("A morning bag", "sized by your steps"), ("Утренняя сумка", "по вашим шагам")),
    ("4-window", "top", ("Sixty stories", "between the houses"), ("Шестьдесят историй", "между домами")),
    ("7-riddle", "bottom", ("Letters with", "no address"), ("Письма", "без адреса")),
    ("8-letter", "bottom", ("The residents write", "to the postman"), ("Жильцы пишут", "почтальону")),
    ("3-stamps", "top", ("A stamp", "for every street"), ("Марка", "для каждой улицы")),
    ("6-morning", "bottom", ("Rain or snow:", "the real weather"), ("Дождь или снег —", "настоящая погода")),
]


def font(size):
    return ImageFont.truetype(str(FONT), size)


def fit(text, size, width):
    """m6x11 is drawn on a 16-unit grid, so it shrinks a whole pixel step at a time and stays crisp."""
    while font(size).getlength(text) > width:
        size -= 16
    return font(size)


def airmail(d, y, w, h=24, step=48):
    """The red and blue stripes of an airmail envelope's edge."""
    for x in range(-h, w + h, step):
        for dx, colour in ((0, RED), (step // 2, BLUE)):
            x0 = x + dx
            d.polygon([(x0, y), (x0 + step // 4, y), (x0 + step // 4 + h, y + h), (x0 + h, y + h)], fill=colour)


def board(w, h):
    g = Image.new("RGB", (w, h), BLUE_DEEP)
    d = ImageDraw.Draw(g)
    d.rectangle((0, 0, w, 24), fill=LIGHT)
    airmail(d, 0, w)
    return g.convert("RGBA")


def lay(g, card, x, y):
    """A screen on the board: a soft shadow and a thick pixel-ink frame round it."""
    sh = Image.new("RGBA", (card.width + 80, card.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle((40, 50, 40 + card.width, 50 + card.height), fill=(6, 10, 22, 150))
    g.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)), (x - 40, y - 40))
    g.alpha_composite(card.convert("RGBA"), (x, y))
    ImageDraw.Draw(g).rectangle((x - 6, y - 6, x + card.width + 5, y + card.height + 5), outline=INK, width=6)


def screenshot(src, end, caption):
    W, H = 1080, 1920
    g = board(W, H)
    d = ImageDraw.Draw(g)
    d.fontmode = "1"
    size = min(fit(line, 96, W - 120).size for line in caption)
    title = font(size)
    d.text((W / 2, 130), caption[0], font=title, fill=LIGHT, anchor="mm")
    d.text((W / 2, 230), caption[1], font=title, fill=LAMP, anchor="mm")
    im = Image.open(src).convert("RGB")
    top = 330
    if end == "top":
        w = 840
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        # The screen runs off the bottom edge, like a street that goes on.
        lay(g, im.crop((0, 0, w, H - top + 20)), (W - w) // 2, top)
    else:
        # A sheet: only its paper card, without the dimmed map above it, larger and centred in the space.
        card = im.crop((0, sheet_top(im), im.width, im.height))
        w = 940
        card = card.resize((w, round(card.height * w / card.width)), Image.LANCZOS)
        room = H - top - 40
        if card.height > room:
            card = card.crop((0, card.height - room, w, card.height))
        lay(g, card, (W - w) // 2, top + (room - card.height) // 2)
    return g.convert("RGB")


def sheet_top(im):
    """The first row of the sheet's light paper card, a few pixels above it to keep its edge."""
    grey = im.convert("L")
    x0, x1 = im.width // 4, im.width * 3 // 4
    for y in range(im.height // 5, im.height):
        row = [grey.getpixel((x, y)) for x in range(x0, x1, 8)]
        if sum(row) / len(row) > 170:
            return max(0, y - 12)
    return 0


def logo(size):
    """The README logo has rounded corners; its own two blues fill them so Play's mask finds a full square."""
    im = Image.open(ROOT / "docs/logo.png").convert("RGBA")
    bg = Image.new("RGBA", im.size)
    w, h = im.size
    band = round(h * 0.29)  # the lighter blue sky above the envelope
    ImageDraw.Draw(bg).rectangle((0, 0, w, band), fill=im.getpixel((w // 2, 4)))
    ImageDraw.Draw(bg).rectangle((0, band, w, h), fill=im.getpixel((w // 2, h - 5)))
    bg.alpha_composite(im)
    return bg.convert("RGB").resize((size, size), Image.NEAREST if size % w == 0 else Image.LANCZOS)


def feature(lang):
    W, H = 1024, 500
    g = board(W, H)
    d = ImageDraw.Draw(g)
    d.fontmode = "1"
    d.rectangle((0, H - 24, W, H), fill=LIGHT)
    airmail(d, H - 24, W)
    # The district with its tags and arrows, from the top of the map screen under the header.
    district = Image.open(SHOTS / lang / "1-map.jpg").convert("RGB").crop((0, 150, 1080, 1250))
    district = district.resize((round(district.width * 380 / district.height), 380), Image.LANCZOS)
    lay(g, district, W - district.width - 52, 60)
    mark = Image.open(ROOT / "docs/logo.png").convert("RGBA").crop((78, 144, 434, 398))
    mark = mark.resize((mark.width * 3 // 8, mark.height * 3 // 8), Image.NEAREST)
    g.alpha_composite(mark, (48, 66))
    name, lines = ("Poste Restante", ["The postman of your", "own neighbourhood"]) if lang == "en" else \
        ("До востребования", ["Почтальон", "своего района"])
    left = W - district.width - 52 - 48 - 40
    d.text((48, 186), name, font=fit(name, 80, left), fill=LIGHT)
    d.rectangle((48, 280, 48 + left, 285), fill=BLUE)
    sub = fit(max(lines, key=lambda l: font(48).getlength(l)), 48, left)
    for i, l in enumerate(lines):
        d.text((48, 306 + i * 58), l, font=sub, fill=LAMP if i == 1 else LIGHT)
    g.convert("RGB").save(OUT / f"feature-graphic-{lang}.png", optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    logo(512).save(OUT / "icon-512.png", optimize=True)
    for lang in ("en", "ru"):
        feature(lang)
        folder = OUT / f"screenshots-{lang}"
        folder.mkdir(exist_ok=True)
        for i, (name, end, en, ru) in enumerate(PAGES, 1):
            screenshot(SHOTS / lang / f"{name}.jpg", end, en if lang == "en" else ru).save(folder / f"{i}.png", optimize=True)
    print("done:", OUT)


if __name__ == "__main__":
    main()
