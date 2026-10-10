"""Package actual Skia frames; animation and glass are rendered by the production HUD."""
from pathlib import Path
from PIL import Image
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "build/dynamic-island-preview/wave-music-round")
paths = sorted((root / "frames").glob("*.png"))
assert len(paths) in (210, 330)
swatches = Image.new("RGB", (820 * 4, 270 * 2))
for i, number in enumerate([0, 36, 54, 72, 90, 126, 159, 195] if len(paths) == 210 else [0, 45, 70, 95, 150, 190, 215, 315]):
    with Image.open(paths[number]) as source:
        swatches.paste(source.convert("RGB"), ((i % 4) * 820, (i // 4) * 270))
palette = swatches.quantize(colors=256)
frames = []
for path in paths:
    with Image.open(path) as source:
        frames.append(source.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE))
destination = root / ("lyrics.gif" if root.name == "lyrics-motion" else "rescue-triple.gif" if len(paths) == 330 else "wave-countdown.gif" if root.name == "wave-countdown-motion" else "wave-music.gif")
frames[0].save(destination, save_all=True, append_images=frames[1:],
               duration=[40 if i % 3 == 2 else 30 for i in range(len(paths))], loop=0, disposal=1, optimize=True)
with Image.open(destination) as result:
    duration = 0
    for i in range(result.n_frames):
        result.seek(i)
        duration += result.info.get("duration", 0)
    assert duration == len(paths) * 100 // 3, duration
print(f"Preview: {destination.resolve()} ({destination.stat().st_size:,} bytes, {duration / 1000:g} seconds)")
