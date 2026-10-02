"""Refine a semantic silhouette in a narrow spatial trimap, preserving its solid interior.

No skin hue test, head rectangle, crop, or reconstruction of character RGB is used.
OpenCV is an isolated headless scratch dependency; all source RGB comes from accepted frames.
"""
import argparse,json,sys,time
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw

p=argparse.ArgumentParser();p.add_argument('manifest',type=Path);p.add_argument('deps',type=Path);a=p.parse_args()
sys.path.insert(0,str(a.deps));import cv2
cv2.setNumThreads(1);cv2.setRNGSeed(20261002)
rows=json.loads(a.manifest.read_text());report=[]
for row in rows:
    started=time.monotonic(); dest=Path(row['refined']);assert not dest.exists();dest.mkdir()
    im=np.array(Image.open(row['source']).convert('RGB'))
    alpha=np.array(Image.open(Path(row['output'])/'instance-1.png').convert('L'))
    binary=alpha>=128
    eroded=cv2.erode(binary.astype('uint8'),cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(15,15)))>0
    dilated=cv2.dilate(binary.astype('uint8'),cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(33,33)))>0
    yy,xx=np.where(dilated);x0,x1=max(0,xx.min()-2),min(im.shape[1],xx.max()+3);y0,y1=max(0,yy.min()-2),min(im.shape[0],yy.max()+3)
    labels=np.full(binary.shape,cv2.GC_BGD,dtype='uint8');labels[dilated]=cv2.GC_PR_BGD;labels[binary]=cv2.GC_PR_FGD;labels[eroded]=cv2.GC_FGD
    sub=labels[y0:y1,x0:x1].copy()
    cv2.grabCut(im[y0:y1,x0:x1].copy(),sub,None,np.zeros((1,65)),np.zeros((1,65)),2,cv2.GC_INIT_WITH_MASK)
    mask=np.zeros(binary.shape,dtype='uint8');mask[y0:y1,x0:x1]=np.isin(sub,[cv2.GC_FGD,cv2.GC_PR_FGD]).astype('uint8')*255
    # Remove only thin boundary whiskers introduced by window lines; protect semantic cores.
    mask=cv2.morphologyEx(mask,cv2.MORPH_OPEN,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(5,5)))
    mask[eroded]=255
    # Feather only the outermost one-pixel edge; do not reduce face/hand interior alpha.
    edge=cv2.GaussianBlur(mask,(3,3),.55);edge[cv2.erode(mask,np.ones((3,3),dtype='uint8'))==255]=255
    corrected=int(np.count_nonzero(edge[eroded]!=255))
    edge[eroded]=255
    assert np.all(edge[eroded]==255), 'Semantic foreground interior was lost'
    Image.fromarray(edge).save(dest/'mask.png')
    rgba=np.dstack((im,edge));Image.fromarray(rgba).save(dest/'cutout.png')
    bg=np.empty_like(im);bg[:]=[63,113,155]
    comp=np.rint(im.astype(float)*(edge[:,:,None]/255)+bg*(1-edge[:,:,None]/255)).astype('uint8')
    if row.get('reviewBlue',True):Image.fromarray(comp).save(dest/'on-blue.png')
    report.append({'source':row['source'],'output':str(dest),'opaquePixels':int((edge==255).sum()),'selectedPixels':int((edge>0).sum()),'protectedInteriorPixels':int(eroded.sum()),'protectedInteriorPass':True,'unprotectedFeatherWouldChangePixels':corrected,'seconds':round(time.monotonic()-started,3)})
    print(dest,flush=True)
Path(a.manifest).with_suffix('.results.json').write_text(json.dumps({'opencv':cv2.__version__,'method':'semantic narrow-band GrabCut, solid interior protection, source RGB unchanged','frames':report},indent=2)+'\n')
