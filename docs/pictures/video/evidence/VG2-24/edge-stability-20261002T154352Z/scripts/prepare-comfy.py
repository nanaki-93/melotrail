"""Package finite corrected layers for actual core ComfyUI composition."""
from pathlib import Path
import hashlib,json,sys,time,uuid
import numpy as np
from PIL import Image

run=Path(__file__).resolve().parents[1];scratch=Path(sys.argv[1])
root=Path.cwd();assets=root/"docs/pictures/video/tabi-assets/train-actions"/run.name
prior=root/"docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z"
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
pin=lambda p:{"path":str(p),"bytes":p.stat().st_size,"sha256":sha(p)}
plan=[];started=time.monotonic()
for action,counts in [("breath",[32,32,33]),("drink",[32,32,32,33])]:
    checks=json.loads((run/"checks"/f"{action}-check.json").read_text())
    assert all(checks[k]["reductionFraction"]>.10 for k in ["alpha","edgeRgb"])
    backing=prior/("fixed-cabin-v2-1080p.png" if action=="breath" else "fixed-cabin-cup-free-v2-1080p.png")
    first=1
    for index,count in enumerate(counts):
        assert time.monotonic()-started<120
        stage=run/"comfy"/f"{action}-{index}"
        for sub in ["inputs","checks","review","workflow"]:(stage/sub).mkdir(parents=True)
        frames=[Image.open(assets/action/f"frame-{n:04d}.png").convert("RGBA") for n in range(first,first+count)]
        layer=stage/"inputs/layer.png"
        frames[0].save(layer,save_all=True,append_images=frames[1:],duration=40,loop=0,disposal=0,blend=0,compress_level=3)
        with Image.open(layer) as decoded:
            assert decoded.n_frames==count
            for i,expected in enumerate(frames):
                decoded.seek(i)
                assert np.array_equal(np.array(decoded.convert("RGBA")),np.array(expected))
        for im in frames:im.close()
        graph={
          "1":{"class_type":"LoadImage","inputs":{"image":"layer-0.png"}},
          "2":{"class_type":"InvertMask","inputs":{"mask":["1",1]}},
          "3":{"class_type":"LoadImage","inputs":{"image":"backing.png"}},
          "4":{"class_type":"RepeatImageBatch","inputs":{"image":["3",0],"amount":count}},
          "5":{"class_type":"ImageCompositeMasked","inputs":{"destination":["4",0],"source":["1",0],"x":0,"y":0,"resize_source":False,"mask":["2",0]}},
          "9":{"class_type":"SaveAnimatedPNG","inputs":{"images":["5",0],"filename_prefix":f"stable-{action}-{index}","fps":25,"compress_level":3}}
        }
        workflow=stage/"workflow/composite.json";workflow.write_text(json.dumps(graph,indent=2)+"\n")
        batch={"source":str(layer),"sourceSha256":sha(layer),"graph":str(workflow),"graphSha256":sha(workflow),"firstFrame":first,"frames":count}
        for key in ["promptId","clientId","attemptId","ownershipToken"]:batch[key]=str(uuid.uuid4())
        batch["fingerprint"]=hashlib.sha256(json.dumps(batch,sort_keys=True).encode()).hexdigest()
        request={"evidence":str(stage),"backing":str(backing),"backingSha256":sha(backing),"applicationSupportRoot":"/Users/marcoandreose/Library/Application Support/MelotrailVideo","storageRoots":[str(run),str(assets),str(scratch)],"batches":[batch]}
        (stage/"request.json").write_text(json.dumps(request,indent=2)+"\n")
        admission={"authorization":str(run/"inputs/admission.json"),"native":{"runnerAttempts":1,"automaticRetries":0,"wholeRunnerLimitSeconds":180,"observedProcessTreeRssLimitBytes":8*1024**3,"ownedEvidenceAndSessionLimitBytes":4*1024**3,"minimumFreeDiskBytes":20*1024**3},"pins":[pin(p) for p in [stage/"request.json",layer,workflow,backing,run/"scripts/ComfyEdgeBatch.java",run/"scripts/run-comfy.py",scratch/"classes/ComfyEdgeBatch.class",scratch/"classes/MediaStage.class",scratch/"runtime-classpath.txt"]]}
        (stage/"admission.json").write_text(json.dumps(admission,indent=2)+"\n")
        plan.append({"action":action,"index":index,"evidence":str(stage),"firstFrame":first,"frames":count})
        first+=count
with (run/"inputs/comfy-batches.json").open("x") as f:json.dump(plan,f,indent=2)
print(json.dumps({"status":"READY","graphs":len(plan),"frames":sum(x["frames"] for x in plan),"seconds":time.monotonic()-started}))
