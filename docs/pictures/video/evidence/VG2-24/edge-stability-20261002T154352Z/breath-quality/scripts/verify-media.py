"""Two bounded read-only media traversals; encoding belongs exclusively to ComfyUI."""
from pathlib import Path
import hashlib, json, subprocess, sys, time, shutil

run=Path(__file__).resolve().parents[1]
scratch=Path(sys.argv[1]).resolve(strict=True)
name=sys.argv[2]
assert name in ('probe','decode')
movie=run/'review/tabi-breath-comfy-1080p.mp4'
assert hashlib.sha256(movie.read_bytes()).hexdigest()==(run/'checks/output-sha256.txt').read_text().strip()
record=run/'checks'/('verify-'+name+'.json')
assert not record.exists(),'Verification traversal already consumed'
bin_dir=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin')
if name=='probe':
    tool=bin_dir/'ffprobe';pin='666c4ecdff7d14153d53e35cd83f0b0f37bffb7250080e994b90b59c575cd264'
    args=['-v','error','-count_frames','-show_frames','-show_streams','-show_format','-of','json',str(movie)]
else:
    tool=bin_dir/'ffmpeg';pin='3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9'
    (run/'review/frames').mkdir()
    args=['-hide_banner','-nostdin','-v','error','-xerror','-i',str(movie),'-an','-fps_mode','passthrough',str(run/'review/frames/frame-%04d.png')]
assert hashlib.sha256(tool.read_bytes()).hexdigest()==pin
assert shutil.disk_usage(run).free>20*1024**3
cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
spent=json.loads((run/'checks/execution.json').read_text())['elapsedSeconds']
spent+=sum(json.loads(p.read_text()).get('seconds',0) for p in (run/'checks').glob('verify-*.json'))
limit=min(60,int(180-spent));assert limit>0
cmd=['/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java','-Djava.awt.headless=true','-cp',cp,'MediaStage',str(tool),pin,str(scratch/('verify-'+name)),str(limit),str(4*1024**3),*args]
receipt={'command':cmd,'status':'STARTED'};record.write_text(json.dumps(receipt,indent=2)+'\n')
start=time.monotonic();reason=None
with (run/'checks'/('verify-'+name+'.log')).open('w') as log:
    process=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT)
    while process.poll() is None:
        if sum(p.stat().st_size for p in run.rglob('*') if p.is_file())>512*1024**2:reason='512 MiB output cap'
        if time.monotonic()-start>limit+10:reason='outer time cap'
        if reason:process.terminate();break
        time.sleep(1)
    process.wait(timeout=20)
receipt.update(status='PASSED' if process.returncode==0 and not reason else 'FAILED',exitCode=process.returncode,reason=reason,seconds=round(time.monotonic()-start,3));record.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({k:v for k,v in receipt.items() if k!='command'}));assert receipt['status']=='PASSED'
if name=='probe':
    facts=json.JSONDecoder().raw_decode((run/'checks/verify-probe.log').read_text())[0]
    streams=facts['streams'];assert len(streams)==1;v=streams[0];frames=facts['frames']
    assert v['codec_name']=='h264' and v['pix_fmt']=='yuv420p'
    assert (v['width'],v['height'],int(v['nb_read_frames']))==(1920,1080,97)
    assert v['r_frame_rate']==v['avg_frame_rate']=='25/1'
    assert len(frames)==97 and abs(float(v['duration'])-3.88)<0.00001
    times=[float(f['best_effort_timestamp_time']) for f in frames]
    assert all(abs(t-i/25)<0.000001 for i,t in enumerate(times)),times
    (run/'checks/media-facts.json').write_text(json.dumps({'streams':streams,'format':facts['format'],'orderedPresentationSeconds':times,'fullDecodeAndExactCadencePass':True,'noAudio':True},indent=2)+'\n')
    print(json.dumps({'resolution':'1920x1080','frames':97,'fps':25,'duration':v['duration'],'colour':[v.get('color_space'),v.get('color_transfer'),v.get('color_primaries')],'exactCadencePass':True}))
