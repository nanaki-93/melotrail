"""Validate actual ComfyUI composites and encode each corrected review clip once."""
from pathlib import Path
import hashlib,json,subprocess,sys,time
import numpy as np
from PIL import Image

run=Path(__file__).resolve().parents[1];scratch=Path(sys.argv[1])
action=sys.argv[2];stage=sys.argv[3]
assert action in ["breath","drink"] and stage in ["flatten","encode","probe","decode"]
count=97 if action=="breath" else 129
assets=Path.cwd()/"docs/pictures/video/tabi-assets/train-actions"/run.name
dest=run/"review"/action;dest.mkdir(exist_ok=True)
movie=dest/f"tabi-{action}-stable-edges-comfy-1080p.mp4"
record=run/"checks"/f"assembly-{action}-{stage}.json"
assert not record.exists(),"This stage is already consumed"
comfy=json.loads((run/"checks/comfy-completion.json").read_text());assert comfy["status"]=="PASS"
spent=comfy["seconds"]+sum(json.loads(p.read_text()).get("seconds",0) for p in (run/"checks").glob("assembly-*.json"))
assert spent<600
started=time.monotonic()
def guard():
    assert spent+time.monotonic()-started<600,"Cumulative native/verification deadline"
    size=sum(p.stat().st_size for folder in [run,assets,scratch] for p in folder.rglob("*") if p.is_file() and not p.is_symlink())
    assert size<4*1024**3,"4 GiB new-file cap"
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
guard();record.write_text('{"status":"STARTED"}\n')
if stage=="flatten":
    (dest/"frames").mkdir()
    maximum=0;negative=False;index=0
    for batch in json.loads((run/"inputs/comfy-batches-final.json").read_text()):
        if batch["action"]!=action:continue
        owner=Path(batch["evidence"]);request=json.loads((owner/"request.json").read_text())
        base=np.array(Image.open(request["backing"]).convert("RGB"))
        with Image.open(owner/"review/batch-0.png") as im:
            assert im.n_frames==batch["frames"] and im.size==(1920,1080)
            for k in range(batch["frames"]):
                index+=1;im.seek(k);assert im.info["duration"]==40
                actual=np.array(im.convert("RGB"))
                layer=np.array(Image.open(assets/action/f"frame-{index:04d}.png").convert("RGBA"))
                a=layer[:,:,3:4]/255
                expected=np.rint(layer[:,:,:3]*a+base*(1-a)).astype("uint8")
                difference=np.abs(actual.astype("int16")-expected.astype("int16"))
                maximum=max(maximum,int(difference.max()));assert difference.max()<=1
                assert np.abs(actual[layer[:,:,3]==0].astype("int16")-base[layer[:,:,3]==0].astype("int16")).max()<=1
                if not negative:
                    bad=actual.copy();bad[400:420,650:670]=0
                    assert np.abs(bad.astype("int16")-expected.astype("int16")).max()>1;negative=True
                Image.fromarray(actual).save(dest/"frames"/f"frame-{index:04d}.png",compress_level=3)
                if index%24==0:guard()
    assert index==count and negative
    result={"status":"PASS","frames":index,"maximumComfyCompositionRgbError":maximum,"fixedCabinOutsideMovingSupport":True,"faceDropoutNegativeRejected":True}
else:
    cp=str(scratch/"classes")+":"+(scratch/"runtime-classpath.txt").read_text().strip()
    bindir=Path("/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin")
    ffmpeg_pin="3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9"
    if stage=="encode":
        assert json.loads((run/"checks"/f"assembly-{action}-flatten.json").read_text())["status"]=="PASS"
        assert not movie.exists()
        tool=bindir/"ffmpeg";pin=ffmpeg_pin
        args=["-hide_banner","-nostdin","-v","error","-xerror","-framerate","25","-start_number","1","-i",str(dest/"frames/frame-%04d.png"),"-frames:v",str(count),"-an","-vf","scale=in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p","-c:v","h264_videotoolbox","-allow_sw","0","-b:v","20M","-profile:v","high","-bf","0","-fps_mode","cfr","-enc_time_base","1:25","-video_track_timescale","12800","-color_primaries","bt709","-color_trc","iec61966-2-1","-colorspace","bt709","-movflags","+faststart","-n",str(movie)]
    elif stage=="probe":
        tool=bindir/"ffprobe";pin="666c4ecdff7d14153d53e35cd83f0b0f37bffb7250080e994b90b59c575cd264"
        args=["-v","error","-count_frames","-show_frames","-show_streams","-show_format","-of","json",str(movie)]
    else:
        tool=bindir/"ffmpeg";pin=ffmpeg_pin
        (dest/"decoded").mkdir()
        args=["-hide_banner","-nostdin","-v","error","-xerror","-i",str(movie),"-an","-fps_mode","passthrough",str(dest/"decoded/frame-%04d.png")]
    limit=min(90,int(600-spent))
    command=["/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java","-Djava.awt.headless=true","-cp",cp,"MediaStage",str(tool),pin,str(scratch/(action+"-"+stage)),str(limit),str(8*1024**3),*args]
    logpath=run/"checks"/f"assembly-{action}-{stage}.log"
    with logpath.open("x") as log:
        process=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=limit+10)
    assert process.returncode==0
    result={"status":"PASS","exitCode":process.returncode,"command":command}
    if stage=="probe":
        facts=json.JSONDecoder().raw_decode(logpath.read_text())[0]
        assert len(facts["streams"])==1
        video=facts["streams"][0]
        assert video["codec_name"]=="h264" and video["pix_fmt"]=="yuv420p"
        assert (video["width"],video["height"],int(video["nb_read_frames"]))==(1920,1080,count)
        assert video["avg_frame_rate"]==video["r_frame_rate"]=="25/1"
        pts=[float(f["best_effort_timestamp_time"]) for f in facts["frames"]]
        assert len(pts)==count and all(abs(t-i/25)<1e-6 for i,t in enumerate(pts))
        assert abs(float(video["duration"])-count/25)<1e-6
        (run/"checks"/f"{action}-media-facts.json").write_text(json.dumps({"status":"PASS","sha256":digest(movie),"stream":video,"format":facts["format"],"exactCadence":True,"noAudio":True},indent=2)+"\n")
    elif stage=="decode":
        quality=[]
        for n in range(1,count+1):
            original=np.array(Image.open(dest/"frames"/f"frame-{n:04d}.png").convert("RGB")).astype("float32")
            actual=np.array(Image.open(dest/"decoded"/f"frame-{n:04d}.png").convert("RGB")).astype("float32")
            psnr=float(10*np.log10(255**2/max(float(np.mean((original-actual)**2)),1e-9)))
            face=float(np.abs(original[350:620,500:900]-actual[350:620,500:900]).mean())
            assert psnr>32 and face<8,(n,psnr,face)
            quality.append({"frame":n,"psnrDb":psnr,"faceMae":face})
        (run/"checks"/f"{action}-decoded-quality.json").write_text(json.dumps({"status":"PASS","frames":count,"minPsnrDb":min(x["psnrDb"] for x in quality),"maxFaceMae":max(x["faceMae"] for x in quality),"rows":quality},indent=2)+"\n")
guard();result["seconds"]=time.monotonic()-started
record.write_text(json.dumps(result,indent=2)+"\n")
print(json.dumps({k:v for k,v in result.items() if k!="command"}))
