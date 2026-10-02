"""Regression evidence for the omitted original frond and feathered solid core."""
from pathlib import Path
import json
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, sys.argv[1])
import cv2
cv2.setNumThreads(1)
run = Path(__file__).resolve().parents[1]
assets = Path.cwd() / "docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z"
neutral = Path.cwd() / "docs/pictures/video/tabi-assets/scenario/scenery-20261002T100234Z/neutral-comfy-1080p.png"
original = np.array(Image.open(neutral).convert("RGB"))
empty = np.array(Image.open(assets / "empty-cabin-cup-free.png").convert("RGB").resize((1920, 1080), Image.Resampling.LANCZOS))
body = np.array(Image.open(assets / "neutral/mask.png").convert("L"))
before = body.copy()
body[182:631, 320:919] = 255
cup = np.array(Image.open(assets / "neutral-cup-mask.png").convert("L"))
def support(mask):
    area = cv2.dilate(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)))
    area = cv2.GaussianBlur(area, (13, 13), 2)
    area[mask > 0] = 255
    return area
assert support(before)[182:631, 320:919].min() < 255, "Old backing negative unexpectedly covers the authored region"
for name, mask in [("fixed-cabin-v2-1080p", body), ("fixed-cabin-cup-free-v2-1080p", np.maximum(body, cup))]:
    area = support(mask)
    actual = np.array(Image.open(assets / (name + ".png")).convert("RGB"))
    alpha = area[:, :, None] / 255
    expected = np.rint(original * (1 - alpha) + empty * alpha).astype("uint8")
    assert np.array_equal(actual, expected), "Backing no longer matches its recorded correction"
    assert np.array_equal(actual[area == 0], original[area == 0])
    assert area[182:631, 320:919].min() == 255
rows = json.loads((run / "inputs/sequence.json").read_text()) + json.loads((run / "inputs/breath-sequence.json").read_text())
checked = 0
feather_negative = False
for row in rows:
    raw = np.array(Image.open(Path(row["output"]) / "instance-1.png").convert("L"))
    core = cv2.erode((raw >= 128).astype("uint8"), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15))) > 0
    alpha = np.array(Image.open(Path(row["refined"]) / "mask.png").convert("L"))
    assert alpha[core].min() == 255, "Semantic foreground core is translucent"
    # The former generic feather could make a protected core boundary translucent.
    broken = cv2.GaussianBlur((alpha >= 128).astype("uint8") * 255, (3, 3), .55)
    if np.any(broken[core] < 255):
        feather_negative = True
    checked += 1
assert checked == 485 and feather_negative
result = {"status": "PASS", "protectedInteriorImages": checked, "genericFeatherNegativeRejected": feather_negative, "oldBackingCoverageNegativeRejected": True, "correctedBackingsExact": 2, "outsideAuthoredBackingSupportUnchanged": True, "artisticApproval": False}
with (run / "checks/backing-interior-regressions.json").open("x") as stream:
    json.dump(result, stream, indent=2)
print(json.dumps(result))
