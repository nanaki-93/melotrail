"""Reproduce the retained v2 backing correction in a fresh destination.

The original candidate was made by the equivalent inline calculation. This
helper records that operation without overwriting either retained candidate.
"""
import argparse
import sys
from pathlib import Path
import numpy as np
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("assets", type=Path)
parser.add_argument("neutral", type=Path)
parser.add_argument("deps", type=Path)
parser.add_argument("destination", type=Path)
args = parser.parse_args()
sys.path.insert(0, str(args.deps))
import cv2
cv2.setNumThreads(1)
args.destination.mkdir(parents=True, exist_ok=False)
original = np.array(Image.open(args.neutral).convert("RGB"))
empty = np.array(Image.open(args.assets / "empty-cabin-cup-free.png").convert("RGB").resize((1920, 1080), Image.Resampling.LANCZOS))
body = np.array(Image.open(args.assets / "neutral/mask.png").convert("L"))
body[182:631, 320:919] = 255
cup = np.array(Image.open(args.assets / "neutral-cup-mask.png").convert("L"))
for name, mask in [("fixed-cabin-v2-1080p", body), ("fixed-cabin-cup-free-v2-1080p", np.maximum(body, cup))]:
    support = cv2.dilate(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)))
    support = cv2.GaussianBlur(support, (13, 13), 2)
    support[mask > 0] = 255
    alpha = support[:, :, None] / 255
    pixels = np.rint(original * (1 - alpha) + empty * alpha).astype("uint8")
    assert np.array_equal(pixels[support == 0], original[support == 0])
    Image.fromarray(pixels).save(args.destination / (name + ".png"))
    Image.fromarray(support).save(args.destination / (name + "-support.png"))
