"""Observe a single production-controlled attempt; production owns/cancels media children."""
from pathlib import Path
import datetime, hashlib, importlib.util, json, os, shutil, signal, subprocess, sys, time

run=Path(__file__).resolve().parents[1]
scratch=Path(sys.argv[1]).resolve(strict=True)
repo=Path('/Users/marcoandreose/DEV/lab/melotrail')
spec=importlib.util.spec_from_file_location('observer',run/'plate-finish/scripts/run.py')
observer=importlib.util.module_from_spec(spec);spec.loader.exec_module(observer)
admission=json.loads((run/'admission.json').read_text());limits=admission['limits']
request=json.loads((run/'render/job-request.json').read_text())
for pin in request['input']['dependencyPins']:
    assert observer.digest(Path(pin['ownedPath']))==pin['sha256'],pin['id']
assert json.loads((run/'checks/source-checks.json').read_text())['status']=='PASS'
assert shutil.disk_usage(run).free>=limits['freeDiskReserveBytes']
classes=scratch/'classes';cp=str(classes)+':'+(scratch/'runtime-classpath.txt').read_text().strip()
cmd=['/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java','-Djava.awt.headless=true','-Xmx2g','-cp',cp,'SceneryPilotKt',str(run),str(repo),'render']
pins=[{'path':str(p),'sha256':observer.digest(p)} for p in sorted(classes.glob('*')) if p.is_file()]
launch={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':cmd,'classPins':pins,'jobRequestSha256':observer.digest(run/'render/job-request.json'),'runnerSha256':observer.digest(Path(__file__)),'admissionSha256':observer.digest(run/'admission.json')}
with (run/'checks/native-launch.json').open('x') as f:json.dump(launch,f,indent=2)
start=time.monotonic();peak=0;storage_peak=0;seen=set();reason=None;stop=None;last=None;encode_start=None
with (run/'checks/native.log').open('x') as log,(run/'checks/native-host-samples.jsonl').open('x') as samples:
    env=dict(os.environ);env.pop('MELOTRAIL_RUN_LIVE_E2E',None);env.pop('MELOTRAIL_RESUME_LIVE_E2E',None)
    p=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,env=env)
    try:
        while p.poll() is None:
            rows=observer.process_tree(p.pid);seen.update(r['pid'] for r in rows);rss=sum(r['rssBytes'] for r in rows);peak=max(peak,rss)
            elapsed=time.monotonic()-start;storage=observer.disk_bytes(run);storage_peak=max(storage_peak,storage)
            pressure=subprocess.check_output(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],text=True,timeout=5).strip()
            stage='STARTING';ledger=run/'render/jobs/video-jobs.json'
            if ledger.is_file():
                data=json.loads(ledger.read_text());text=json.dumps(data)
                if 'ENCODING' in text or 'COMPLETED' in text:
                    if encode_start is None:encode_start=time.monotonic()
                    stage='ENCODING_OR_VERIFYING'
                else:stage='RENDERING'
            if elapsed>limits['wholeNativeRunnerSeconds']:reason='whole runner deadline'
            elif encode_start is None and elapsed>admission['stages']['renderMaximumSeconds']:reason='render deadline'
            elif encode_start and time.monotonic()-encode_start>admission['stages']['encodeAndBuiltInChecksMaximumSeconds']:reason='encode/check deadline'
            elif rss>limits['observedTreeRssBytes']:reason='observed process tree RSS limit'
            elif storage>limits['totalNewEvidenceBytes']:reason='evidence storage limit'
            elif shutil.disk_usage(run).free<limits['freeDiskReserveBytes']:reason='disk reserve'
            elif pressure not in ('1','2'):reason='critical or unknown host pressure'
            samples.write(json.dumps({'seconds':elapsed,'stage':stage,'rssBytes':rss,'storageBytes':storage,'pressure':pressure,'processes':rows})+'\n');samples.flush()
            status=(int(elapsed)//20,stage,pressure)
            if status!=last:print(f'elapsed={elapsed:.0f}s stage={stage} rssGiB={rss/1024**3:.2f} storageMiB={storage/1024**2:.1f}',flush=True);last=status
            if reason and stop is None:stop=time.monotonic();p.send_signal(signal.SIGTERM)
            if stop and time.monotonic()-stop>40:raise RuntimeError('Cleanup unconfirmed; inspect owned process before any retry')
            time.sleep(2)
        code=p.wait()
    finally:
        if p.poll() is None:p.terminate();p.wait(timeout=40)
        receipt={'exitCode':p.returncode,'stopReason':reason,'elapsedSeconds':time.monotonic()-start,'peakObservedTreeRssBytes':peak,'peakOwnedEvidenceBytes':storage_peak,'observedPids':sorted(seen),'automaticRetries':0,'rssExcludesSomeGpuMemory':True}
        (run/'checks/native-execution.json').write_text(json.dumps(receipt,indent=2)+'\n')
assert code==0 and reason is None,(code,reason)
print('NATIVE_PASS',flush=True)
