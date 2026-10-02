"""Execute each admitted ComfyUI batch once, with a fresh owned runtime."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time

run=Path(__file__).resolve().parents[1];scratch=Path(sys.argv[1])
sha=lambda p:hashlib.file_digest(p.open("rb"),"sha256").hexdigest()
receipt=run/"checks/comfy-execution.json"
assert not receipt.exists(),"Native batch allowance already consumed"
plan=json.loads((run/"inputs/comfy-batches.json").read_text())
receipt.write_text('{"status":"STARTED"}\n')
results=[];started=time.monotonic()
for batch in plan:
    assert time.monotonic()-started<420,"Insufficient remaining cumulative allowance for another batch"
    stage=Path(batch["evidence"])
    command=[sys.executable,str(run/"scripts/run-comfy.py"),str(stage),str(scratch)]
    subprocess.run(command+["--check"],check=True,timeout=30,stdout=subprocess.DEVNULL)
    print("COMFY_BATCH="+stage.name,flush=True)
    subprocess.run(command,check=True,timeout=225)
    execution=json.loads((stage/"checks/execution.json").read_text())
    assert execution["stopReason"] is None and execution["exitCode"]==0
    assert "RUNTIME_FINAL=STOPPED" in (stage/"checks/generation.log").read_text()
    for pid in execution["observedPids"]:
        try:os.kill(pid,0)
        except ProcessLookupError:continue
        raise AssertionError("Owned PID remains: "+str(pid))
    session=Path((stage/"output/session-path.txt").read_text().strip())
    owner=Path("/Users/marcoandreose/Library/Application Support/MelotrailVideo/runs")
    assert session.parent==owner and session.name.startswith("comfy-") and not session.is_symlink()
    request=json.loads((stage/"request.json").read_text())
    mappings={"input/layer-0.png":Path(request["batches"][0]["source"]),"input/backing.png":Path(request["backing"])}
    output_pin=sha(stage/"review/batch-0.png")
    preserved=[]
    for p in sorted(session.rglob("*")):
        assert not p.is_symlink()
        if not p.is_file():continue
        relative=str(p.relative_to(session));digest=sha(p)
        if relative in mappings:
            dest=mappings[relative];assert sha(dest)==digest
        elif relative.startswith("output/") and digest==output_pin:
            dest=stage/"review/batch-0.png"
        else:
            assert p.stat().st_size<64*1024**2,relative
            dest=stage/"runtime-metadata"/relative
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest);assert sha(dest)==digest
        preserved.append({"runtimeFile":relative,"retained":str(dest),"sha256":digest})
    shutil.rmtree(session)
    (stage/"checks/runtime-cleanup.json").write_text(json.dumps({"status":"PASS","runtimeState":"STOPPED","ownedSessionRemoved":True,"originalSession":str(session),"allCopiesOrImmutableReferencesVerified":True,"files":preserved},indent=2)+"\n")
    results.append({"stage":stage.name,"seconds":execution["elapsedSeconds"],"peakSampledRssBytes":execution["peakSampledOwnedRssBytes"]})
    receipt.write_text(json.dumps({"status":"RUNNING","seconds":time.monotonic()-started,"batches":results},indent=2)+"\n")
receipt.write_text(json.dumps({"status":"PASS","seconds":time.monotonic()-started,"batches":results,"newMotionInferences":0},indent=2)+"\n")
print("ALL_COMFY_BATCHES_COMPLETED",flush=True)
