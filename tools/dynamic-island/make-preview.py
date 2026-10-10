"""Package Skia-rendered PNGs as a GIF; no drawing or animation is implemented here."""
from pathlib import Path
import sys

from PIL import Image

root = Path(sys.argv[1] if len(sys.argv) > 1 else "build/dynamic-island-preview")
paths = sorted((root / "frames").glob("*.png"))
if len(paths) != 720:
    raise SystemExit(f"Expected 720 rendered frames, found {len(paths)}")

# One shared palette keeps the static wallpaper stable across GIF frames.
swatches = Image.new("RGB", (360 * 4, 224 * 3))
for index, frame in enumerate([0, 31, 33, 66, 112, 153, 180, 212, 240, 452, 525, 600]):
    with Image.open(paths[frame]) as source:
        swatches.paste(source.resize((360, 224), Image.Resampling.LANCZOS),
                       ((index % 4) * 360, (index // 4) * 224))
palette = swatches.quantize(colors=256)

frames = []
for path in paths:
    with Image.open(path) as source:
        frame = source.convert("RGB").resize((720, 448), Image.Resampling.LANCZOS)
        frames.append(frame.quantize(palette=palette, dither=Image.Dither.NONE))
durations = [30 if i % 3 else 40 for i in range(len(frames))]
frames[0].save(root / "dynamic-island.gif", save_all=True, append_images=frames[1:],
               duration=durations, loop=0, disposal=1, optimize=True)
print(f"GIF: {(root / 'dynamic-island.gif').resolve()} ({(root / 'dynamic-island.gif').stat().st_size:,} bytes)")
