"""Read sample tables only; no additional probe or compressed-media traversal."""
from pathlib import Path
from collections import Counter
import hashlib,json,struct
run=Path(__file__).resolve().parents[1];movie=run/'review/tokyo-window-join-10s-1080p.mp4';data=movie.read_bytes()
tables={};top=[];tracks=[]
def u32(i):return struct.unpack_from('>I',data,i)[0]
def walk(start,end,parent=''):
 while start<end:
  size=u32(start);kind=data[start+4:start+8].decode('ascii');payload=start+8
  if size==1:size=struct.unpack_from('>Q',data,payload)[0];payload+=8
  assert size>=payload-start and start+size<=end
  if not parent:top.append({'type':kind,'offset':start,'bytes':size})
  if kind in ('moov','trak','mdia','minf','stbl','edts'):walk(payload,start+size,parent+'/'+kind)
  elif kind in ('stts','ctts'):
   version=data[payload];assert version in (0,1);count=u32(payload+4);fmt='>Ii' if kind=='ctts' and version==1 else '>II'
   tables[kind]=[struct.unpack_from(fmt,data,payload+8+i*8) for i in range(count)]
  elif kind in ('mdhd','mvhd'):
   version=data[payload];assert version in (0,1);offset=payload+(20 if version==1 else 12)
   scale=u32(offset);duration=struct.unpack_from('>Q' if version==1 else '>I',data,offset+4)[0]
   tables[kind]={'timescale':scale,'duration':duration}
  elif kind=='elst':
   version=data[payload];assert version in (0,1);fmt='>Qqhh' if version else '>Iihh';width=struct.calcsize(fmt)
   tables[kind]=[struct.unpack_from(fmt,data,payload+8+i*width) for i in range(u32(payload+4))]
  elif kind=='stsz':tables['samples']=u32(payload+8)
  elif kind=='stsd':
   assert u32(payload+4)==1;entry=payload+8;tables['codec']=data[entry+4:entry+8].decode('ascii')
   tables['width'],tables['height']=struct.unpack_from('>HH',data,entry+32)
  elif kind=='hdlr':tracks.append(data[payload+8:payload+12].decode('ascii'))
  start+=size
 assert start==end
walk(0,len(data))
assert tracks==['vide'];assert tables['codec']=='avc1';assert (tables['width'],tables['height'],tables['samples'])==(1920,1080,300)
durations=[delta for count,delta in tables['stts'] for _ in range(count)]
offsets=[offset for count,offset in tables.get('ctts',[(len(durations),0)]) for _ in range(count)]
assert len(durations)==len(offsets)==300
edits=tables.get('elst',[]);assert len(edits)<=1
if edits:assert edits[0][1]>=0 and edits[0][2:]==(1,0)
shift=edits[0][1] if edits else 0;dts=0;pts=[]
for duration,offset in zip(durations,offsets):pts.append(dts+offset-shift);dts+=duration
ordered=sorted(pts);scale=tables['mdhd']['timescale'];intervals=Counter(b-a for a,b in zip(ordered,ordered[1:]));expected=scale//30
cadence=scale%30==0 and set(intervals)=={expected} and ordered[0]==0 and ordered[-1]+expected==10*scale
duration=tables['mvhd']['duration']/tables['mvhd']['timescale'];fast=next(a['offset'] for a in top if a['type']=='moov')<next(a['offset'] for a in top if a['type']=='mdat')
result={'status':'PASS' if cadence and duration==10 and fast else 'FAIL','movieSha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'width':1920,'height':1080,'frames':300,'fps':30,'durationSeconds':duration,'fastStart':fast,'audioStreams':0,'pixelAspect':'1:1 (production counted-probe gate)','codec':'H.264','exactPresentationCadence':cadence,'presentationIntervalTicks':dict(intervals),'timescale':scale,'sampleTables':tables,'humanReview':'PENDING','colorMetadata':'Not certified by this table inspection'}
(run/'checks/container.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='sampleTables'}));assert result['status']=='PASS'
