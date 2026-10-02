"""Read-only RGB/alpha checks for the new breathing and tracked cup assets."""
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image

run = Path(__file__).resolve().parents[1]
root = Path.cwd()
assets = root / "docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z"
rows = json.loads((run / "inputs/breath-sequence.json").read_text())
assert len(rows) == 97
pins = []
areas = []
negative = False
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
for row in rows:
    source = Path(row["source"])
    dest = Path(row["refined"])
    rgb = np.array(Image.open(source).convert("RGB"))
    rgba = np.array(Image.open(dest / "cutout.png").convert("RGBA"))
    alpha = np.array(Image.open(dest / "mask.png").convert("L"))
    assert rgba.shape == (1080, 1920, 4)
    assert np.array_equal(rgba[:, :, :3], rgb)
    assert np.array_equal(rgba[:, :, 3], alpha)
    def face_guard(a):
        assert a[395:455, 630:685].min() == 255, "Face interior lost"
    face_guard(alpha)
    if not negative:
        bad = alpha.copy()
        bad[410:425, 645:660] = 0
        try:
            face_guard(bad)
        except AssertionError:
            negative = True
        else:
            raise AssertionError("Dropped face accepted")
    areas.append(int((alpha >= 128).sum()))
    pins.append({"source": str(source), "sourceSha256": sha(source), "mask": str(dest / "mask.png"), "maskSha256": sha(dest / "mask.png"), "rgbaSha256": sha(dest / "cutout.png")})
change = max(abs(b - a) / a for a, b in zip(areas, areas[1:]))
assert change < .15 and negative
cup_pins = []
frames = root / "docs/pictures/video/evidence/VG2-25/drink-guided-repair2-20261002T090557Z/finish/review/frames"
for n in range(1, 130):
    src = frames / f"frame-{n:04d}.png"
    body = np.array(Image.open(assets / "drink" / f"frame-{n:04d}" / "mask.png").convert("L"))
    mask_path = assets / "drink/cup-support" / f"mask-{n:04d}.png"
    rgba_path = assets / "drink/cup-support" / f"rgba-{n:04d}.png"
    alpha = np.array(Image.open(mask_path).convert("L"))
    rgba = np.array(Image.open(rgba_path).convert("RGBA"))
    assert np.array_equal(rgba[:, :, :3], np.array(Image.open(src).convert("RGB")))
    assert np.array_equal(rgba[:, :, 3], alpha)
    assert np.all(alpha >= body), "Cup support erased character"
    cup_pins.append({"frame": n, "maskSha256": sha(mask_path), "rgbaSha256": sha(rgba_path), "extraCupPixels": int(((alpha > 0) & (body == 0)).sum())})
result = {"status": "PASS", "breathFrames": 97, "cupFrames": 129, "rgbUnchanged": True, "faceDropoutNegativeRejected": negative, "breathMaxAdjacentAreaChangeFraction": change, "cupSupportNeverErasesCharacter": True, "artisticApproval": False, "breathPins": pins, "cupPins": cup_pins}
with (run / "checks/breath-cup-checks.json").open("x") as stream:
    json.dump(result, stream, indent=2)
print(json.dumps({key: value for key, value in result.items() if key not in ["breathPins", "cupPins"]}))
