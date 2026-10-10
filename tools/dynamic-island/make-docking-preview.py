"""Package the 60 FPS docking renderer's frames without implementing animation."""
from pathlib import Path
import sys

from PIL import Image

root = Path(sys.argv[1] if len(sys.argv) > 1 else "build/dynamic-island-preview/docking")
paths = sorted((root / "frames").glob("*.png"))
if len(paths) != 210:
    raise SystemExit(f"Expected 210 rendered frames, found {len(paths)}")

# A shared palette keeps the game screenshot stable through the glass transition.
swatches = Image.new("RGB", (300 * 5, 133 * 2))
for index, number in enumerate([0, 32, 39, 57, 98, 105, 128, 155, 194, 209]):
    with Image.open(paths[number]) as source:
        swatches.paste(source.convert("RGB").resize((300, 133), Image.Resampling.LANCZOS),
                       ((index % 5) * 300, (index // 5) * 133))
palette = swatches.quantize(colors=256)
frames = []
for path in paths:
    with Image.open(path) as source:
        frames.append(source.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE))

# GIF time is quantized to 10 ms; each three frames retain exactly 50 ms.
durations = [10 if index % 3 == 0 else 20 for index in range(len(frames))]
destination = root / "docking.gif"
frames[0].save(destination, save_all=True, append_images=frames[1:], duration=durations,
               loop=0, disposal=1, optimize=True)
with Image.open(destination) as result:
    total = 0
    for index in range(result.n_frames):
        result.seek(index)
        total += result.info.get("duration", 0)
    if total != 3500:
        raise AssertionError(f"Expected 3500 ms, found {total}")
print(f"GIF: {destination.resolve()} ({destination.stat().st_size:,} bytes, 3500 ms)")
