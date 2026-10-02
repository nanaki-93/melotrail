"""Motion-aligned three-frame edge preparation for the two reviewed clips.

Only the six-pixel silhouette band may change. Solid character pixels are copied
from the original frame. This is finite external asset preparation, not inference.
"""
import argparse, functools, hashlib, json, resource, sys, time
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

p=argparse.ArgumentParser()
p.add_argument("deps",type=Path);p.add_argument("mode",choices=["diagnose","prepare","check"])
p.add_argument("action",choices=["breath","drink"]);a=p.parse_args()
sys.path.insert(0,str(a.deps));import cv2
cv2.setNumThreads(1);cv2.setRNGSeed(20261002)
run=Path(__file__).resolve().parents[1];root=Path.cwd()
old=root/"docs/pictures/video/evidence/VG2-24/breath-outlines-20261002T130040Z"
prior=root/"docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z"
assets=root/"docs/pictures/video/tabi-assets/train-actions"/run.name
count=97 if a.action=="breath" else 129
source=old/"finish/review/frames" if a.action=="breath" else root/"docs/pictures/video/evidence/VG2-25/drink-guided-repair2-20261002T090557Z/finish/review/frames"
backing=prior/("fixed-cabin-v2-1080p.png" if a.action=="breath" else "fixed-cabin-cup-free-v2-1080p.png")
base=np.asarray(Image.open(backing).convert("RGB"))
yy,xx=np.mgrid[0:1080,0:1920].astype("float32")
started=time.monotonic()
def guard():
    assert time.monotonic()-started<240,"Four-minute stage deadline"
    assert resource.getrusage(resource.RUSAGE_SELF).ru_maxrss<2*1024**3,"2 GiB process memory cap"
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def mask_path(n):
    return prior/a.action/f"frame-{n:04d}"/"mask.png" if a.action=="breath" else prior/"drink/cup-support"/f"mask-{n:04d}.png"
@functools.lru_cache(maxsize=5)
def frame(n):
    rgb=np.asarray(Image.open(source/f"frame-{n:04d}.png").convert("RGB"))
    alpha=np.asarray(Image.open(mask_path(n)).convert("L")).astype("float32")/255
    binary=(alpha>=.5).astype("uint8")
    distance=cv2.distanceTransform(binary,cv2.DIST_L2,5)-cv2.distanceTransform(1-binary,cv2.DIST_L2,5)
    gray=cv2.cvtColor(cv2.resize(rgb,(960,540),interpolation=cv2.INTER_AREA),cv2.COLOR_RGB2GRAY)
    return rgb,alpha,distance,gray
tracker=cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_MEDIUM)
@functools.lru_cache(maxsize=8)
def flow(i,j):
    f=tracker.calc(frame(i)[3],frame(j)[3],None)
    return cv2.resize(f,(1920,1080),interpolation=cv2.INTER_LINEAR)*2
def warped(array,f):
    return cv2.remap(array,xx+f[:,:,0],yy+f[:,:,1],cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT)
def aligned(i,j):
    f=flow(i,j);reverse=warped(flow(j,i),f)
    confidence=np.clip(1-np.linalg.norm(f+reverse,axis=2)/2.0,0,1)
    rgb,alpha,_,_=frame(j)
    return warped(rgb.astype("float32"),f),warped(alpha,f),confidence
def candidate(n):
    rgb,alpha,distance,_=frame(n)
    band=np.abs(distance)<=6
    weight=np.full(alpha.shape,.5,dtype="float32")
    alpha_sum=alpha*.5
    color_sum=rgb.astype("float32")*alpha[:,:,None]*.5
    for neighbor in [n-1,n+1]:
        if not 1<=neighbor<=count:continue
        other,opacity,confidence=aligned(n,neighbor)
        # High residuals indicate an occlusion or wrong correspondence.
        residual=np.abs(rgb.astype("float32")-other).mean(axis=2)
        confidence*=np.clip((65-residual)/35,0,1)
        w=confidence*.25
        weight+=w;alpha_sum+=opacity*w
        color_sum+=other*(opacity*w)[:,:,None]
    result_alpha=alpha_sum/weight
    result_rgb=np.rint(color_sum/np.maximum(alpha_sum[:,:,None],1e-6)).clip(0,255).astype("uint8")
    # A one-pixel spatial finish suppresses subpixel contour stepping.
    result_alpha=cv2.GaussianBlur(result_alpha,(3,3),.55)
    result_alpha[~band]=alpha[~band];result_rgb[~band]=rgb[~band]
    # Never soften the stable face/hand/body core.
    core=cv2.erode((alpha>=.999).astype("uint8"),cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(13,13)))>0
    result_alpha[core]=alpha[core];result_rgb[core]=rgb[core]
    return result_rgb,np.rint(result_alpha*255).astype("uint8"),band,core
def composite(rgb,alpha):
    return np.rint(rgb*(alpha[:,:,None]/255)+base*(1-alpha[:,:,None]/255)).astype("uint8")

report=[];sample_numbers=[9,17,25,33,49,65,81,min(count,97)]
if a.mode=="prepare":
    destination=assets/a.action;destination.mkdir(parents=True,exist_ok=False)
    for n in range(1,count+1):
        guard();rgb,alpha,band,core=candidate(n)
        original,old_alpha,_,_=frame(n)
        assert np.array_equal(rgb[~band],original[~band])
        assert np.array_equal(rgb[core],original[core]) and np.all(alpha[core]==255)
        area_change=abs(int((alpha>=128).sum())-int((old_alpha>=.5).sum()))/max(1,int((old_alpha>=.5).sum()))
        assert area_change<.01,(n,area_change)
        path=destination/f"frame-{n:04d}.png"
        Image.fromarray(np.dstack((rgb,alpha))).save(path,compress_level=3)
        if n in sample_numbers:
            before=composite(original,np.rint(old_alpha*255).astype("uint8"))
            after=composite(rgb,alpha)
            crop=(310,170,1020,630)
            panel=Image.new("RGB",(1420,460))
            panel.paste(Image.fromarray(before).crop(crop),(0,0))
            panel.paste(Image.fromarray(after).crop(crop),(710,0))
            panel.save(run/"review"/f"{a.action}-edge-before-after-{n:04d}.png")
        report.append({"frame":n,"rgbaSha256":digest(path),"sourceSha256":digest(source/f"frame-{n:04d}.png"),"sourceMaskSha256":digest(mask_path(n)),"maxAreaChangeFraction":area_change,"solidInteriorExact":True,"outsideSixPixelBandExact":True})
        if n%24==0:print(a.action,n,flush=True)
elif a.mode=="diagnose":
    for n in range(2,count+1):
        guard();rgb,alpha,distance,_=frame(n)
        previous,prev_alpha,confidence=aligned(n,n-1)
        band=(np.abs(distance)<=6)&(confidence>.5)
        alpha_error=float(np.abs(alpha-prev_alpha)[band].mean())
        rgb_band=band&(alpha>.7)&(prev_alpha>.7)
        rgb_error=float(np.abs(rgb.astype("float32")-previous)[rgb_band].mean())
        report.append({"frame":n,"motionAlignedAlphaMae":alpha_error,"motionAlignedEdgeRgbMae":rgb_error,"measuredBandPixels":int(band.sum())})
    report.sort(key=lambda row:row["motionAlignedAlphaMae"],reverse=True)
    for row in report[:3]:
        n=row["frame"];panel=Image.new("RGB",(1920,600))
        for k,m in enumerate([n-1,n,min(count,n+1)]):
            rgb,alpha,_,_=frame(m)
            crop=Image.fromarray(composite(rgb,np.rint(alpha*255).astype("uint8"))).crop((300,170,1100,920)).resize((640,600))
            panel.paste(crop,(k*640,0))
        panel.save(run/"review"/f"{a.action}-jitter-triplet-{n:04d}.png")
else:
    for n in range(2,count+1):
        guard();rgb,alpha,distance,_=frame(n)
        current=np.asarray(Image.open(assets/a.action/f"frame-{n:04d}.png").convert("RGBA"))
        previous=np.asarray(Image.open(assets/a.action/f"frame-{n-1:04d}.png").convert("RGBA"))
        prev_rgb,prev_alpha,confidence=aligned(n,n-1)
        f=flow(n,n-1);new_alpha=current[:,:,3].astype("float32")/255
        prev_new_alpha=warped(previous[:,:,3].astype("float32")/255,f)
        band=(np.abs(distance)<=6)&(confidence>.5)
        old_error=float(np.abs(alpha-prev_alpha)[band].mean())
        new_error=float(np.abs(new_alpha-prev_new_alpha)[band].mean())
        color_band=band&(alpha>.7)&(prev_alpha>.7)
        before=float(np.abs(rgb.astype("float32")-prev_rgb)[color_band].mean())
        after=float(np.abs(current[:,:,:3].astype("float32")-warped(previous[:,:,:3].astype("float32"),f))[color_band].mean())
        report.append({"frame":n,"alphaBefore":old_error,"alphaAfter":new_error,"edgeRgbBefore":before,"edgeRgbAfter":after})
result={"action":a.action,"mode":a.mode,"frames":count,"seconds":time.monotonic()-started,"peakRssBytes":resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,"rows":report,"humanApproval":False}
if a.mode=="diagnose":
    result["meanAlphaMae"]=float(np.mean([x["motionAlignedAlphaMae"] for x in report]))
    result["meanEdgeRgbMae"]=float(np.mean([x["motionAlignedEdgeRgbMae"] for x in report]))
if a.mode=="check":
    for metric in ["alpha","edgeRgb"]:
        before=float(np.mean([x[metric+"Before"] for x in report]));after=float(np.mean([x[metric+"After"] for x in report]))
        result[metric]={"before":before,"after":after,"reductionFraction":1-after/before}
with (run/"checks"/f"{a.action}-{a.mode}.json").open("x") as f:json.dump(result,f,indent=2)
print(json.dumps({k:v for k,v in result.items() if k!="rows"}))
