"""One installed RealESRGAN finishing pass on RGB video-frame crops, no new action synthesis.
Raw video-frame buffers are decoded/composited by the Node media stage. No still art is painted.
"""
from pathlib import Path
import sys, json, hashlib, time
import numpy as np
import torch
from spandrel import ModelLoader
run=Path(__file__).resolve().parents[1]
model=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/models/comfyui/upscale_models/RealESRGAN_x2plus.pth')
assert hashlib.sha256(model.read_bytes()).hexdigest()=='49fafd45f8fd7aa8d31ab2a22d14d91b536c34494a5cfe31eb5d89c2fa266abb'
assert torch.backends.mps.is_available(), 'Selected Apple GPU is unavailable; no automatic fallback'
inputs=json.loads((run/'checks/preparation.json').read_text())
assert len(inputs['frames'])==129
out=run/'review/enhanced-motion';out.mkdir()
started=time.monotonic();limit=float(sys.argv[1]);net=ModelLoader().load_from_state_dict(torch.load(model,map_location='cpu',weights_only=True).get('params_ema')).eval().to('mps')
assert net.scale==2
checks=[];torch.set_num_threads(4)
with torch.inference_mode():
 for item in inputs['frames']:
  assert time.monotonic()-started<limit, 'Finishing time limit'
  p=run/item['crop'];raw=p.read_bytes();assert hashlib.sha256(raw).hexdigest()==item['cropSha256']
  arr=np.frombuffer(raw,dtype=np.uint8).reshape(232,304,3).copy()
  tensor=torch.from_numpy(arr).permute(2,0,1).unsqueeze(0).to('mps',dtype=torch.float32)/255.0
  enhanced=net(tensor);torch.mps.synchronize()
  assert list(enhanced.shape)==[1,3,464,608]
  raw_out=(enhanced.clamp(0,1)*255.0).round().to(torch.uint8).squeeze(0).permute(1,2,0).cpu().numpy().tobytes()
  dest=out/f"frame-{item['frame']+1:04d}.rgb";dest.write_bytes(raw_out)
  checks.append({'frame':item['frame'],'path':str(dest.relative_to(run)),'sha256':hashlib.sha256(raw_out).hexdigest(),'bytes':len(raw_out)})
  p.unlink() # Consumed disposable RGB crop; native source and its pin remain retained.
  del tensor,enhanced;torch.mps.empty_cache()
  assert torch.mps.driver_allocated_memory()<12*1024**3, 'GPU allocation limit'
  if len(checks)%16==0: print(json.dumps({'completedFrames':len(checks),'elapsedSeconds':round(time.monotonic()-started,2)}),flush=True)
(run/'checks/finishing.json').write_text(json.dumps({'status':'COMPLETE','model':str(model),'scale':2,'nativeCrop':[304,232],'enhancedCrop':[608,464],'frames':checks,'elapsedSeconds':round(time.monotonic()-started,3),'interpretation':'Model-enhanced existing motion, not native 1080p generation.'},indent=2)+'\n')
print('FINISHING_COMPLETE',len(checks),flush=True)
