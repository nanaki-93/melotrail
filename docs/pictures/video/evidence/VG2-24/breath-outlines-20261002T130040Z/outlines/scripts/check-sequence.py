"""Independent byte, coverage and temporal checks; does not award artistic acceptance."""
import hashlib,json,sys
from pathlib import Path
import numpy as np
from PIL import Image

run=Path(__file__).resolve().parents[1]
rows=json.loads((run/'inputs/sequence.json').read_text())
report=[];areas={};negative=False
for row in rows:
    source=Path(row['source']);dest=Path(row['refined']);action=dest.parent.name
    if 'sourceSha256' in row:assert hashlib.sha256(source.read_bytes()).hexdigest()==row['sourceSha256']
    original=np.array(Image.open(source).convert('RGB'))
    rgba=np.array(Image.open(dest/'cutout.png').convert('RGBA'))
    alpha=np.array(Image.open(dest/'mask.png').convert('L'))
    assert rgba.shape==(1080,1920,4) and alpha.shape==(1080,1920)
    assert np.array_equal(rgba[:,:,:3],original), 'Character RGB was changed'
    assert np.array_equal(rgba[:,:,3],alpha)
    assert np.count_nonzero(alpha==255)>250000 and np.count_nonzero(alpha==0)>1200000
    if action in ('watch','drink','read'):
        x,y,w,h={'watch':(610,370,60,75),'drink':(630,395,55,60),'read':(630,395,55,60)}[action]
        def face_guard(candidate):
            assert candidate[y:y+h,x:x+w].min()==255, 'Face interior is translucent or missing'
        face_guard(alpha)
        if not negative:
            wrong=alpha.copy();wrong[y+10:y+30,x+10:x+30]=0
            try:face_guard(wrong)
            except AssertionError:negative=True
            else:raise AssertionError('The face-dropout negative was accepted')
        areas.setdefault(action,[]).append(int((alpha>=128).sum()))
    report.append({'mask':str(dest/'mask.png'),'maskSha256':hashlib.sha256((dest/'mask.png').read_bytes()).hexdigest(),'cutoutSha256':hashlib.sha256((dest/'cutout.png').read_bytes()).hexdigest(),'rgbUnchanged':True,'opaquePixels':int((alpha==255).sum())})
continuity={}
for action,values in areas.items():
    assert len(values)==129
    change=max(abs(b-a)/a for a,b in zip(values,values[1:]))
    assert change<.15, 'Abrupt silhouette area change: '+action
    continuity[action]={'frames':len(values),'minimumArea':min(values),'maximumArea':max(values),'maxAdjacentAreaChangeFraction':change}
assert negative
result={'status':'PASS','images':len(rows),'faceDropoutNegativeRejected':negative,'continuity':continuity,'artisticApproval':False,'limitations':'Semantic mattes are candidates; fine frond/window and table contact edge remnants need visual review. Cup pickup/return support is checked separately.','pins':report}
(run/'checks/sequence-checks.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='pins'}))
