"""Read saved decoded frames to compare exported outline residuals."""
from pathlib import Path
import functools,json,resource,sys,time
import numpy as np
from PIL import Image
sys.path.insert(0,sys.argv[1]);import cv2
cv2.setNumThreads(1)
run=Path(__file__).resolve().parents[1];root=Path.cwd();action=sys.argv[2]
assert action in ["breath","drink"]
old=root/"docs/pictures/video/evidence/VG2-24/breath-outlines-20261002T130040Z"
prior=root/"docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z"
count=97 if action=="breath" else 129
previous=old/("finish/review/frames" if action=="breath" else "fixed-cabin-recovery/review/decoded")
new=run/"review/frames"
yy,xx=np.mgrid[0:1080,0:1920].astype("float32")
tracker=cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_MEDIUM)
@functools.lru_cache(maxsize=3)
def frame(n):
    original=np.asarray(Image.open(previous/f"frame-{n:04d}.png").convert("RGB"))
    revised=np.asarray(Image.open(new/f"frame-{n:04d}.png").convert("RGB"))
    path=prior/action/f"frame-{n:04d}"/"mask.png" if action=="breath" else prior/"drink/cup-support"/f"mask-{n:04d}.png"
    alpha=np.asarray(Image.open(path).convert("L"))
    gray=cv2.cvtColor(cv2.resize(original,(960,540),interpolation=cv2.INTER_AREA),cv2.COLOR_RGB2GRAY)
    return original,revised,alpha,gray
def warp(im,f):return cv2.remap(im,xx+f[:,:,0],yy+f[:,:,1],cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT)
rows=[];start=time.monotonic()
for n in range(2,count+1):
    assert time.monotonic()-start<180 and resource.getrusage(resource.RUSAGE_SELF).ru_maxrss<2*1024**3
    old_rgb,new_rgb,alpha,gray=frame(n);old_prev,new_prev,alpha_prev,gray_prev=frame(n-1)
    f=cv2.resize(tracker.calc(gray,gray_prev,None),(1920,1080))*2
    rev=cv2.resize(tracker.calc(gray_prev,gray,None),(1920,1080))*2
    confidence=np.linalg.norm(f+warp(rev,f),axis=2)<1
    binary=(alpha>=128).astype("uint8")
    distance=cv2.distanceTransform(binary,cv2.DIST_L2,5)-cv2.distanceTransform(1-binary,cv2.DIST_L2,5)
    band=(np.abs(distance)<=6)&confidence&(alpha>178)&(warp(alpha_prev,f)>178)
    before=float(np.abs(old_rgb.astype("float32")-warp(old_prev.astype("float32"),f))[band].mean())
    after=float(np.abs(new_rgb.astype("float32")-warp(new_prev.astype("float32"),f))[band].mean())
    rows.append({"frame":n,"oldExportEdgeMae":before,"newExportEdgeMae":after,"bandPixels":int(band.sum())})
before=float(np.mean([x["oldExportEdgeMae"] for x in rows]))
after=float(np.mean([x["newExportEdgeMae"] for x in rows]))
result={"status":"PASS" if after<before*.95 else "REVIEW_REQUIRED","action":action,"frames":count,"oldExportEdgeMae":before,"newExportEdgeMae":after,"reductionFraction":1-after/before,"seconds":time.monotonic()-start,"rows":rows,"humanAppearanceApproval":False,"limitation":"This is a motion-aligned edge-variation metric, not a perceptual flicker score or a claim that source artwork variation is eliminated."}
with (run/"checks"/f"{action}-exported-edge-check.json").open("x") as f:json.dump(result,f,indent=2)
print(json.dumps({k:v for k,v in result.items() if k!="rows"}))
assert result["status"]=="PASS"
