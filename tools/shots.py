"""Turns the screen renders into the README screenshots.

./gradlew testDebugUnitTest -Prender --tests '*RenderScreens*'
python tools/shots.py

The renders are every main screen drawn by Robolectric over the demo store
(app/build/art/screens-<lang>); this scales them to phone width and writes docs/screenshots/<lang>/*.jpg.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
WIDTH = 1080


def main():
    for lang in ("en", "ru"):
        out = ROOT / f"docs/screenshots/{lang}"
        out.mkdir(parents=True, exist_ok=True)
        for f in sorted((ROOT / f"app/build/art/screens-{lang}").glob("*.png")):
            im = Image.open(f).convert("RGB")
            im = im.resize((WIDTH, round(im.height * WIDTH / im.width)), Image.LANCZOS)
            im.save(out / f"{f.stem}.jpg", quality=86, optimize=True)


if __name__ == "__main__":
    main()
