"""Prepared follow-up, requiring a fresh explicit user run instruction before execution."""
from pathlib import Path
import importlib.util, json, os, shutil, signal, subprocess, sys, time
sys.dont_write_bytecode=True
run=Path(__file__).resolve().parents[1];repo=Path('/Users/marcoandreose/DEV/lab/melotrail')
scratch=Path(sys.argv[1]).resolve(strict=True)
authorization=run/'checks/isolated-authorization.json'
assert authorization.is_file(),'A fresh explicit run instruction is required after the memory failure.'
approval=json.loads(authorization.read_text());assert approval['authorized'] is True
prior=repo/'docs/pictures/video/evidence/VG4-05/scenery-20261002T100234Z'
spec=importlib.util.spec_from_file_location('observer',prior/'plate-finish/scripts/run.py');observer=importlib.util.module_from_spec(spec);spec.loader.exec_module(observer)
proposal=json.loads((run/'checks/next-correction.json').read_text())
for p in proposal['scriptPins']:assert observer.digest(Path(p['path']))==p['sha256']
admission=json.loads((run/'admission.json').read_text());limits=admission['limits']
java='/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java';node=Path('/opt/homebrew/Cellar/node/25.8.2/bin/node')
cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
env=dict(os.environ)
for key in ('MELOTRAIL_RUN_LIVE_E2E','MELOTRAIL_RESUME_LIVE_E2E'):env.pop(key,None)
phases=[['coverage'],['joins'],['changed-overlap'],['prior-regression']]+[['frame',str(f)] for f in [0,600,1170,1229,1230,1231,1350,1469,1590,1799]]+[['collect']]
with (run/'checks/isolated-launch.json').open('x') as f:json.dump({'phases':phases,'authorizationSha256':observer.digest(authorization),'scriptPins':proposal['scriptPins'],'seconds':120},f)
start=time.monotonic();receipts=[]
try:
 for phase in phases:
  label='-'.join(phase);remaining=int(120-(time.monotonic()-start));assert remaining>0,'Shared source-check deadline'
  cmd=[java,'-Djava.awt.headless=true','-Xmx1g','-cp',cp,'MediaStage',str(node),observer.digest(node),str(run/('isolated-process-'+label)),str(remaining),str(2*1024**3),str(run/'scripts/check-isolated.proposal.cjs'),*phase]
  peak=0;stage_start=time.monotonic();reason=None
  with (run/'checks'/('isolated-'+label+'.log')).open('x') as log:
   process=subprocess.Popen(cmd,env=env,stdout=log,stderr=subprocess.STDOUT)
   try:
    while process.poll() is None:
     rows=observer.process_tree(process.pid);rss=sum(r['rssBytes'] for r in rows);peak=max(peak,rss)
     storage=observer.disk_bytes(run)+observer.disk_bytes(Path(admission['derivedAssetDirectory']))
     pressure=subprocess.check_output(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],text=True,timeout=5).strip()
     if time.monotonic()-start>120:reason='Shared source-check deadline'
     elif rss>limits['observedTreeRssBytes']:reason='Tree RSS cap'
     elif storage>limits['totalNewEvidenceBytes']:reason='Disk cap'
     elif shutil.disk_usage(run).free<limits['freeDiskReserveBytes']:reason='Free disk reserve'
     elif pressure not in ('1','2'):reason='Critical or unknown memory pressure'
     if reason:process.send_signal(signal.SIGTERM);process.wait(timeout=40);break
     time.sleep(0.25)
    code=process.wait()
   finally:
    if process.poll() is None:process.terminate();process.wait(timeout=40)
    receipts.append({'stage':label,'exitCode':process.returncode,'stopReason':reason,'seconds':time.monotonic()-stage_start,'peakObservedTreeRssBytes':peak})
  print(json.dumps(receipts[-1]),flush=True)
  assert code==0 and reason is None,(label,code,reason)
finally:
 (run/'checks/isolated-execution.json').write_text(json.dumps({'stages':receipts,'seconds':time.monotonic()-start,'automaticRetries':0},indent=2)+'\n')
