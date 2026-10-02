"""One additional full decode plus PNG comparison within the shared video budget."""
from pathlib import Path
import importlib.util, json, os, shutil, signal, subprocess, sys, time
sys.dont_write_bytecode=True
run=Path(__file__).resolve().parents[1];repo=Path('/Users/marcoandreose/DEV/lab/melotrail');scratch=Path(sys.argv[1]).resolve(strict=True)
spec=importlib.util.spec_from_file_location('observer',repo/'docs/pictures/video/evidence/VG4-05/scenery-20261002T100234Z/plate-finish/scripts/run.py');observer=importlib.util.module_from_spec(spec);spec.loader.exec_module(observer)
admission=json.loads((run/'admission.json').read_text());limits=admission['limits'];spent=json.loads((run/'checks/native-execution.json').read_text())['elapsedSeconds']
movie=run/'review/tokyo-window-join-10s-1080p.mp4';assert observer.digest(movie)==json.loads((run/'checks/native-result.json').read_text())['movieSha256']
maximum=min(180,int(900-spent));assert maximum>0
java='/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java';cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
ffmpeg=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin/ffmpeg');ffmpeg_sha='3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9'
node=Path('/opt/homebrew/Cellar/node/25.8.2/bin/node');(run/'review/decoded').mkdir()
with (run/'checks/review-launch.json').open('x') as f:json.dump({'nativeSecondsSpent':spent,'maximumSeconds':maximum,'additionalFullDecodes':1,'additionalProbes':0,'movieSha256':observer.digest(movie),'checkScriptSha256':observer.digest(run/'scripts/check-decoded.cjs')},f)
phases=[('decode',ffmpeg,ffmpeg_sha,['-hide_banner','-nostdin','-v','error','-xerror','-protocol_whitelist','file,pipe','-i',str(movie),'-map','0:v:0','-an','-sn','-dn','-fps_mode','passthrough','-pix_fmt','rgb24',str(run/'review/decoded/frame-%04d.png')]),('compare',node,observer.digest(node),[str(run/'scripts/check-decoded.cjs')])]
env=dict(os.environ)
for key in ('MELOTRAIL_RUN_LIVE_E2E','MELOTRAIL_RESUME_LIVE_E2E'):env.pop(key,None)
start=time.monotonic();receipts=[]
try:
 for name,tool,pin,args in phases:
  remaining=int(maximum-(time.monotonic()-start));assert remaining>0
  cmd=[java,'-Djava.awt.headless=true','-Xmx1g','-cp',cp,'MediaStage',str(tool),pin,str(run/('review-process-'+name)),str(remaining),str(limits['nativeProcessMemoryBytes']),*args]
  peak=0;peak_disk=0;reason=None;stage_start=time.monotonic()
  with (run/'checks'/('review-'+name+'.log')).open('x') as log:
   process=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,env=env)
   try:
    while process.poll() is None:
     rss=sum(r['rssBytes'] for r in observer.process_tree(process.pid));peak=max(peak,rss)
     size=observer.disk_bytes(run)+observer.disk_bytes(Path(admission['priorOwnedEvidenceDirectory']))+observer.disk_bytes(Path(admission['derivedAssetDirectory']));peak_disk=max(peak_disk,size)
     pressure=subprocess.check_output(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],text=True,timeout=5).strip()
     if time.monotonic()-start>maximum:reason='Shared review time limit'
     elif rss>limits['observedTreeRssBytes']:reason='Observed tree RSS limit'
     elif size>limits['totalNewEvidenceBytes']:reason='Total new storage limit'
     elif shutil.disk_usage(run).free<limits['freeDiskReserveBytes']:reason='Free disk reserve'
     elif pressure not in ('1','2'):reason='Critical or unknown memory pressure'
     if reason:process.send_signal(signal.SIGTERM);process.wait(timeout=40);break
     time.sleep(0.5)
    code=process.wait()
   finally:
    if process.poll() is None:process.terminate();process.wait(timeout=40)
    receipts.append({'stage':name,'exitCode':process.returncode,'stopReason':reason,'seconds':time.monotonic()-stage_start,'peakObservedTreeRssBytes':peak,'peakOwnedEvidenceBytes':peak_disk})
  print(json.dumps(receipts[-1]),flush=True);assert code==0 and reason is None
finally:
 (run/'checks/review-execution.json').write_text(json.dumps({'stages':receipts,'seconds':time.monotonic()-start,'sharedNativeSeconds':spent+time.monotonic()-start,'automaticRetries':0,'additionalFullDecodes':1,'additionalProbes':0},indent=2)+'\n')
