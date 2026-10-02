"""Prepare fixed cabin plates and track the separately resting cup from both ends.

The action frame pixels and character alpha are immutable inputs. Cup contours are
authored against the retained 200x240 crops. This is reversible external asset preparation.
"""
from pathlib import Path
import hashlib,json,sys
import numpy as np
from PIL import Image,ImageDraw
sys.path.insert(0,sys.argv[1]);import cv2
cv2.setNumThreads(1)
run=Path(__file__).resolve().parents[2]
assets=Path.cwd()/'docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z'
neutral=Path.cwd()/'docs/pictures/video/tabi-assets/scenario/scenery-20261002T100234Z/neutral-comfy-1080p.png'
original=np.array(Image.open(neutral).convert('RGB'))
generated=np.array(Image.open(assets/'empty-cabin-cup-free.png').convert('RGB').resize((1920,1080),Image.Resampling.LANCZOS))
character=np.array(Image.open(assets/'neutral/mask.png').convert('L'))
cup_points=[(31,47),(42,42),(71,39),(104,39),(125,42),(137,48),(138,65),(146,69),(147,79),(140,86),(130,181),(129,190),(119,196),(94,200),(66,198),(48,195),(41,189),(37,175),(26,87),(20,80),(21,70),(27,66)]
cup=Image.new('L',(1920,1080));draw=ImageDraw.Draw(cup);draw.polygon([(920+x,570+y) for x,y in cup_points],fill=255)
cup=np.array(cup)
def blend_area(mask):
    selected=cv2.dilate(mask,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(25,25)))
    selected=cv2.GaussianBlur(selected,(13,13),2)
    selected[mask>0]=255
    return selected
def blend(base,source,alpha):
    a=alpha[:,:,None].astype(float)/255
    return np.rint(base*(1-a)+source*a).astype('uint8')
character_support=blend_area(character)
both_support=blend_area(np.maximum(character,cup))
fixed=blend(original,generated,character_support)
fixed_no_cup=blend(original,generated,both_support)
for name,im in [('fixed-cabin-1080p.png',fixed),('fixed-cabin-cup-free-1080p.png',fixed_no_cup),('neutral-cup-mask.png',cup),('cabin-backing-support.png',both_support)]:
    dest=assets/name;assert not dest.exists();Image.fromarray(im).save(dest)
assert np.array_equal(fixed_no_cup[both_support==0],original[both_support==0])
# Dense flow is used only for this separate prop; it never warps TABI or the source RGB.
frames=Path.cwd()/'docs/pictures/video/evidence/VG2-25/drink-guided-repair2-20261002T090557Z/finish/review/frames'
roi=(650,400,1130,820);x0,y0,x1,y1=roi
gray=[]
for n in range(1,130):
    rgb=np.array(Image.open(frames/f'frame-{n:04d}.png').convert('RGB'))[y0:y1,x0:x1]
    gray.append(cv2.cvtColor(rgb,cv2.COLOR_RGB2GRAY))
height,width=gray[0].shape;gx,gy=np.meshgrid(np.arange(width,dtype='float32'),np.arange(height,dtype='float32'))
tracker=cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_MEDIUM)
tracked={}
for indices in [range(0,65),range(128,64,-1)]:
    previous=None;alpha=cup[y0:y1,x0:x1].copy()
    for index in indices:
        if previous is not None:
            flow=tracker.calc(gray[index],gray[previous],None)
            alpha=cv2.remap(alpha,gx+flow[:,:,0],gy+flow[:,:,1],cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT)
        tracked[index]=alpha.copy();previous=index
destination=assets/'drink/cup-support';destination.mkdir()
rows=[]
for index in range(129):
    subject=np.array(Image.open(assets/'drink'/f'frame-{index+1:04d}'/'mask.png').convert('L'))
    prop=np.zeros((1080,1920),dtype='uint8');prop[y0:y1,x0:x1]=tracked[index]
    combined=np.maximum(subject,prop)
    mask_path=destination/f'mask-{index+1:04d}.png';Image.fromarray(combined).save(mask_path)
    source=np.array(Image.open(frames/f'frame-{index+1:04d}.png').convert('RGB'))
    output=destination/f'rgba-{index+1:04d}.png';Image.fromarray(np.dstack((source,combined))).save(output)
    if index in [0,8,16,24,32,48,64,80,96,104,112,120,128]:
        Image.fromarray(blend(fixed_no_cup,source,combined)).save(run/'outlines/review'/f'fixed-drink-{index+1:04d}.png')
    rows.append({'frame':index+1,'mask':str(mask_path),'rgba':str(output),'maskSha256':hashlib.sha256(mask_path.read_bytes()).hexdigest(),'separateCupPixels':int(((prop>0)&(subject==0)).sum())})
result={'status':'PREPARED_FOR_VISUAL_REVIEW','cupRoi':roi,'cupContourInRetainedCrop':cup_points,'tracking':'Forward from first frame to midpoint; backward from final frame after midpoint. Independent from character matte; source RGB unchanged.','fixedCabinOutsideBackingSupportExact':True,'frames':rows}
(run/'outlines/checks/cabin-cup-support.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['frames','cupContourInRetainedCrop']}))
