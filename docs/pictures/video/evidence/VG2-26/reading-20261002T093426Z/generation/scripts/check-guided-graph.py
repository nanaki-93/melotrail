"""Validate guide indices and consumption before native submission; no models loaded."""
import copy,json,pathlib
root=pathlib.Path.cwd();run=pathlib.Path(__file__).resolve().parents[1]
original=json.loads((root/'src/main/resources/video/comfyui/short-shot-api.json').read_text())
graph=json.loads((run/'workflow/guided-read-api.json').read_text())
def verify(g):
 assert set(g)==set(original)|{'30','31','32','33'}
 for n in original:
  if n not in {'9','13','14','16'}:assert g[n]==original[n],n
 assert g['30']=={'class_type':'LoadImage','inputs':{'image':'approved-reading-guide.png'}}
 for n,frame,up,image in [('31',64,'8','30'),('32',128,'31','4')]:
  assert g[n]=={'class_type':'LTXVAddGuide','inputs':{'positive':[up,0],'negative':[up,1],'vae':['3',0],'latent':[up,2],'image':[image,0],'frame_idx':frame,'strength':1.0}}
  assert 0<=frame<129 and frame%8==0
 assert g['9']['inputs']['positive']==['32',0] and g['9']['inputs']['negative']==['32',1]
 assert g['13']['inputs']['latent_image']==['32',2]
 assert g['33']=={'class_type':'LTXVCropGuides','inputs':{'positive':['32',0],'negative':['32',1],'latent':['13',1]}}
 assert g['14']['inputs']['samples']==['33',2]
 for n in ['9','13','14','16']:
  x=copy.deepcopy(g[n]);y=copy.deepcopy(original[n])
  for key in {'9':['positive','negative'],'13':['latent_image'],'14':['samples'],'16':['filename_prefix']}[n]:x['inputs'].pop(key);y['inputs'].pop(key)
  assert x==y
verify(graph)
negatives=[]
for label,node,key,value in [('out-of-range guide','32','frame_idx',136),('wrong mid-pose input','31','image',['4',0]),('guide decode leak','14','samples',['13',1]),('sampler ignores guidance','13','latent_image',['8',2])]:
 wrong=copy.deepcopy(graph);wrong[node]['inputs'][key]=value
 try:verify(wrong)
 except AssertionError:negatives.append(label)
 else:raise AssertionError('Invalid graph accepted: '+label)
report={'result':'PASS','guideFramesZeroBased':[64,128],'newConditioningNodes':['LTXVAddGuide','LTXVCropGuides'],'consumedImageNodes':['4','30'],'negativeCasesRejected':negatives,'modelsAndSamplerUnchanged':True,'nativeLaunches':0}
(run/'checks/guided-graph-check.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
