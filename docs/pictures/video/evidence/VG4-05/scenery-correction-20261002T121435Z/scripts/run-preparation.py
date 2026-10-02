"""One bounded source preparation/check; no movie submission or retry."""
from pathlib import Path
import hashlib, importlib.util, json, os, shutil, signal, subprocess, sys, time
sys.dont_write_bytecode=True
run=Path(__file__).resolve().parents[1]
repo=Path('/Users/marcoandreose/DEV/lab/melotrail')
scratch=Path(sys.argv[1]).resolve(strict=True)
prior=repo/'docs/pictures/video/evidence/VG4-05/scenery-20261002T100234Z'
spec=importlib.util.spec_from_file_location('observer',prior/'plate-finish/scripts/run.py')
observer=importlib.util.module_from_spec(spec);spec.loader.exec_module(observer)
admission=json.loads((run/'admission.json').read_text());limits=admission['limits']
java='/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java'
cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
node='/opt/homebrew/Cellar/node/25.8.2/bin/node';node_sha=observer.digest(Path(node))
env=dict(os.environ)
for key in ('MELOTRAIL_RUN_LIVE_E2E','MELOTRAIL_RESUME_LIVE_E2E'):env.pop(key,None)
with (run/'checks/preparation-launch.json').open('x') as f:json.dump({'runnerSha256':observer.digest(Path(__file__)),'automaticRetries':0,'seconds':120},f)
spent=json.loads((run/'checks/source-check-refusal/preparation-execution.json').read_text())['seconds']
start=time.monotonic()-spent;receipts=[]
try:
    for stage in ('check-scenery',):
        remaining=120-(time.monotonic()-start)
        assert remaining>1,'Preparation budget exhausted'
        cmd=[java,'-Djava.awt.headless=true','-Xmx1g','-cp',cp]
        if stage=='import':cmd+=['SceneryPilotKt',str(run),str(repo),'prepare']
        else:cmd+=['MediaStage',node,node_sha,str(run/('owned-'+stage+'-check-repair')),str(int(remaining)),str(2*1024**3),str(run/'scripts'/(stage+'.cjs'))]
        stage_start=time.monotonic();peak=0;reason=None;seen=set()
        with (run/'checks'/(stage+'.log')).open('x') as log:
            process=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,env=env)
            try:
                while process.poll() is None:
                    rows=observer.process_tree(process.pid);seen.update(r['pid'] for r in rows)
                    rss=sum(r['rssBytes'] for r in rows);peak=max(peak,rss)
                    storage=observer.disk_bytes(run)+observer.disk_bytes(Path(admission['derivedAssetDirectory']))
                    pressure=subprocess.check_output(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],text=True,timeout=5).strip()
                    if time.monotonic()-start>120:reason='Preparation deadline'
                    elif rss>limits['observedTreeRssBytes']:reason='Process-tree RSS limit'
                    elif storage>limits['totalNewEvidenceBytes']:reason='New storage limit'
                    elif shutil.disk_usage(run).free<limits['freeDiskReserveBytes']:reason='Disk reserve'
                    elif pressure not in ('1','2'):reason='Host memory pressure'
                    if reason:process.send_signal(signal.SIGTERM);process.wait(timeout=40);break
                    time.sleep(0.5)
                code=process.wait()
            finally:
                if process.poll() is None:process.terminate();process.wait(timeout=40)
                receipts.append({'stage':stage,'exitCode':process.returncode,'stopReason':reason,'seconds':time.monotonic()-stage_start,'peakObservedTreeRssBytes':peak,'observedPids':sorted(seen)})
        print(json.dumps(receipts[-1]),flush=True)
        assert code==0 and reason is None,(stage,code,reason)
finally:
    (run/'checks/preparation-execution.json').write_text(json.dumps({'stages':receipts,'seconds':time.monotonic()-start,'automaticRetries':0},indent=2)+'\n')
