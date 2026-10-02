"""One retained full decode of the generated action through the production media owner."""
from pathlib import Path
import hashlib,json,subprocess,sys,time,shutil
run=Path(__file__).resolve().parents[1]
scratch=Path(sys.argv[1]).resolve(strict=True)
source=Path((run/'output/published-video.txt').read_text().strip()).resolve(strict=True)
assert source.is_relative_to((run/'output/publication').resolve())
record=run/'checks/review-decode.json'
assert not record.exists(), 'Review decode already consumed'
movie=run/'review/tabi-breath-native.mp4'
assert not movie.exists()
shutil.copyfile(source,movie)
pin=hashlib.sha256(movie.read_bytes()).hexdigest()
assert pin==hashlib.sha256(source.read_bytes()).hexdigest()
(run/'checks/output-sha256.txt').write_text(pin+'\n')
frames=run/'review/frames';frames.mkdir()
tool=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin/ffmpeg')
toolpin='3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9'
cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
args=['/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java','-Djava.awt.headless=true','-cp',cp,'MediaStage',str(tool),toolpin,str(scratch/'native-review-decode'),'60',str(4*1024**3),'-hide_banner','-nostdin','-v','error','-xerror','-i',str(movie),'-an','-fps_mode','passthrough',str(frames/'frame-%04d.png')]
receipt={'status':'STARTED','sourceSha256':pin,'command':args};record.write_text(json.dumps(receipt,indent=2)+'\n')
start=time.monotonic();reason=None
with (run/'checks/review-decode.log').open('x') as log:
 process=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT)
 try:
  while process.poll() is None:
   if sum(p.stat().st_size for p in run.rglob('*') if p.is_file())>2*1024**3:reason='storage cap'
   elif shutil.disk_usage(run).free<20*1024**3:reason='disk reserve'
   elif time.monotonic()-start>70:reason='time cap'
   if reason:process.terminate();break
   time.sleep(1)
  process.wait(timeout=20)
 finally:
  if process.poll() is None:process.terminate();process.wait(timeout=20)
receipt.update(status='PASSED' if process.returncode==0 and not reason else 'FAILED',exitCode=process.returncode,reason=reason,elapsedSeconds=round(time.monotonic()-start,3),frames=len(list(frames.glob('frame-*.png'))))
record.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({k:v for k,v in receipt.items() if k!='command'}))
assert receipt['status']=='PASSED' and receipt['frames']==97
