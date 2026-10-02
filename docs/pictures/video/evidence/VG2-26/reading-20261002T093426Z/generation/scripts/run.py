"""Bounded observer for one VG2-26 JVM; model/media ownership stays in production adapters."""
import argparse, datetime, hashlib, json, os, shutil, signal, subprocess, time
from pathlib import Path

def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda:stream.read(1024*1024), b""): h.update(block)
    return h.hexdigest()

def process_tree(pid):
    result=subprocess.run(["/bin/ps","-axo","pid=,ppid=,rss=,comm="],capture_output=True,text=True,timeout=5,check=True)
    rows={}
    for line in result.stdout.splitlines():
        pieces=line.split(None,3)
        if len(pieces)==4: rows[int(pieces[0])]={"pid":int(pieces[0]),"parent":int(pieces[1]),"rssBytes":int(pieces[2])*1024,"command":pieces[3]}
    owned={pid}
    while True:
        more={p for p,r in rows.items() if r["parent"] in owned}
        if more.issubset(owned): break
        owned.update(more)
    return [rows[p] for p in sorted(owned) if p in rows]

def disk_bytes(folder):
    total=0
    if folder.is_dir():
        for parent,dirs,files in os.walk(folder,followlinks=False):
            for name in files:
                p=Path(parent)/name
                try:
                    if not p.is_symlink(): total+=p.stat().st_size
                except FileNotFoundError: pass
    return total

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("evidence",type=Path);parser.add_argument("scratch",type=Path);parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    evidence=args.evidence.resolve(strict=True);scratch=args.scratch.resolve(strict=True)
    root=Path.cwd().resolve(); admission=json.loads((evidence/"admission.json").read_text())
    request=json.loads((evidence/"request.json").read_text()); limits=admission["native"]
    assert evidence==Path(request["outputDirectory"]).parent
    assert not (evidence/"output").exists(), "Run output already exists; no automatic replay"
    assert limits["inferenceAttempts"]==1 and limits["automaticRetries"]==0
    for pin in admission["pins"]:
        p=Path(pin["path"]);p=p if p.is_absolute() else root/p
        assert p.is_file() and not p.is_symlink() and p.stat().st_size==pin["bytes"] and digest(p)==pin["sha256"], str(p)
    assert digest(Path(request["composedImage"]))==request["composedImageSha256"]
    assert shutil.disk_usage(evidence).free>=limits["minimumFreeDiskBytes"]
    cp=(scratch/"runtime-classpath.txt").read_text().strip()
    classes=scratch/"classes"
    class_pins={p.name:digest(p) for p in classes.glob("*.class")}
    assert {"ActionPilot.class","MotionTools.class"} <= class_pins.keys()
    if args.check:
        print(json.dumps({"status":"READY","pins":len(admission["pins"]),"classPins":class_pins,"modelLaunches":0}));return
    command=["/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java","-Djava.awt.headless=true","-cp",str(classes)+os.pathsep+cp,"ActionPilot",str(evidence/"request.json")]
    launch={"startedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"classPins":class_pins,"admissionSha256":digest(evidence/"admission.json"),"commandOwner":"ActionPilot via production ComfyVideoRuntime/VideoJobCoordinator"}
    with (evidence/"checks/launch.json").open("x") as out:json.dump(launch,out,indent=2)
    started=time.monotonic();stop_reason=None;stop_at=None;observed=set();peak=0;previous=None
    with (evidence/"checks/generation.log").open("x") as log, (evidence/"checks/host-samples.jsonl").open("x") as samples:
        process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        (scratch/"java.pid").write_text(str(process.pid))
        print("JVM_PID="+str(process.pid),flush=True)
        try:
            while process.poll() is None:
                rows=process_tree(process.pid);observed.update(r["pid"] for r in rows)
                rss=sum(r["rssBytes"] for r in rows);peak=max(peak,rss)
                pressure=subprocess.run(["/usr/sbin/sysctl","-n","kern.memorystatus_vm_pressure_level"],capture_output=True,text=True,timeout=5,check=True).stdout.strip()
                swap=subprocess.run(["/usr/sbin/sysctl","-n","vm.swapusage"],capture_output=True,text=True,timeout=5,check=True).stdout.strip()
                session_file=evidence/"output/session-path.txt"; session=None
                if session_file.is_file():
                    session=Path(session_file.read_text().strip())
                    assert session.is_relative_to(Path(request["applicationSupportRoot"])/"runs") and session.name.startswith("comfy-")
                storage=disk_bytes(evidence)+(disk_bytes(session) if session else 0)
                elapsed=time.monotonic()-started
                row={"at":datetime.datetime.now(datetime.timezone.utc).isoformat(),"elapsedSeconds":round(elapsed,3),"pressureCode":pressure,"swap":swap,"ownedProcessTreeRssBytes":rss,"ownedStorageBytes":storage,"processes":rows}
                samples.write(json.dumps(row)+"\n");samples.flush()
                status=(int(elapsed)//20,pressure)
                if status!=previous:
                    print(f"elapsed={elapsed:.0f}s pressure={pressure} ownedRssGiB={rss/1024**3:.2f} storageMiB={storage/1024**2:.1f}",flush=True);previous=status
                if stop_reason is None:
                    if elapsed>limits["wholeRunnerLimitSeconds"]:stop_reason="whole runner time limit"
                    elif rss>limits["observedProcessTreeRssLimitBytes"]:stop_reason="sampled owned RSS limit"
                    elif storage>limits["ownedEvidenceAndSessionLimitBytes"]:stop_reason="owned storage limit"
                    elif shutil.disk_usage(evidence).free<limits["minimumFreeDiskBytes"]:stop_reason="disk reserve"
                    elif pressure not in {"1","2"}:stop_reason="critical or unknown host pressure"
                    if stop_reason:
                        stop_at=time.monotonic();print("STOP_REQUESTED="+stop_reason,flush=True)
                        process.send_signal(signal.SIGTERM) # JVM hook invokes owned runtime close/reaping.
                if stop_at and time.monotonic()-stop_at>45:
                    raise RuntimeError("Graceful shutdown unconfirmed; retain process ownership and inspect before any retry")
                time.sleep(2)
            code=process.wait()
        except BaseException:
            if process.poll() is None:
                process.send_signal(signal.SIGTERM)
                try:process.wait(timeout=30)
                except subprocess.TimeoutExpired:pass
            raise
        finally:
            state={"exitCode":process.poll(),"stopReason":stop_reason,"elapsedSeconds":round(time.monotonic()-started,3),"peakSampledOwnedRssBytes":peak,"observedPids":sorted(observed),"note":"RSS samples exclude some Metal/GPU allocations; production pressure/swap safeguards remain unchanged."}
            (evidence/"checks/execution.json").write_text(json.dumps(state,indent=2)+"\n")
    print("GENERATION_EXIT="+str(code),flush=True)
    raise SystemExit(code if code>=0 else 128-code)

if __name__=="__main__":main()
