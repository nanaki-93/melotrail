"""Verify recovered ComfyUI batches, then use the production media process for one review encode."""
from pathlib import Path
import hashlib,json,subprocess,sys,time
import numpy as np
from PIL import Image

run=Path(__file__).resolve().parents[1];parent=run.parent;old=parent/'fixed-cabin-proof'
scratch=Path(sys.argv[1]);stage=sys.argv[2];assert stage in ('verify-batches','encode','probe','decode')
record=run/'checks'/f'{stage}.json';assert not record.exists(),'This stage is already consumed'
start=time.monotonic();q=json.loads((old/'request.json').read_text())
spent=json.loads((run/'checks/execution.json').read_text())['elapsedSeconds']+sum(json.loads(p.read_text()).get('seconds',0) for p in (run/'checks').glob('*.json') if p.name in ['verify-batches.json','encode.json','probe.json','decode.json'])
assert spent<300,'Cumulative processing allowance exhausted'
assert sum(p.stat().st_size for p in run.rglob('*') if p.is_file())<1024**3,'New output storage limit'
if stage=='verify-batches':
    record.write_text('{"status":"STARTED"}\n')
    dest=run/'review/frames';dest.mkdir()
    base=np.array(Image.open(q['backing']).convert('RGB'));count=0;maximum=0;negative=False;pins=[]
    for index,b in enumerate(q['batches']):
        path=old/'review'/f'batch-{index}.png' if index<3 else run/'review/batch-0.png'
        pins.append({'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        with Image.open(path) as im,Image.open(b['source']) as source:
            assert im.n_frames==b['frames'] and im.size==(1920,1080)
            for n in range(b['frames']):
                assert spent+time.monotonic()-start<300,'Cumulative processing deadline'
                im.seek(n);source.seek(n);assert im.info.get('duration')==40
                actual=np.array(im.convert('RGB'));layer=np.array(source.convert('RGBA'));alpha=layer[:,:,3:4]/255
                expected=np.rint(layer[:,:,:3]*alpha+base*(1-alpha)).astype('uint8')
                delta=np.abs(actual.astype('int16')-expected.astype('int16'));maximum=max(maximum,int(delta.max()))
                assert delta.max()<=1,(index,n,int(delta.max()))
                assert np.abs(actual[layer[:,:,3]==0].astype('int16')-base[layer[:,:,3]==0].astype('int16')).max()<=1
                if not negative:
                    wrong=actual.copy();wrong[400:420,650:670]=0
                    assert np.abs(wrong.astype('int16')-expected.astype('int16')).max()>1
                    negative=True
                count+=1;Image.fromarray(actual).save(dest/f'frame-{count:04d}.png',compress_level=3)
    assert count==129 and negative
    result={'status':'PASS','frames':count,'maxCompositionRgbError':maximum,'fixedCabinOutsideMovingSupportPass':True,'faceDropoutNegativeRejected':True,'sourceBatches':pins,'seconds':time.monotonic()-start}
else:
    cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
    bindir=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin')
    movie=run/'review/tabi-drink-fixed-cabin-comfy-1080p.mp4'
    if stage=='encode':
        assert json.loads((run/'checks/verify-batches.json').read_text())['status']=='PASS' and not movie.exists()
        tool=bindir/'ffmpeg';pin='3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9'
        arguments=['-hide_banner','-nostdin','-v','error','-xerror','-framerate','25','-start_number','1','-i',str(run/'review/frames/frame-%04d.png'),'-frames:v','129','-an','-vf','scale=in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p','-c:v','h264_videotoolbox','-allow_sw','0','-b:v','20M','-profile:v','high','-bf','0','-fps_mode','cfr','-enc_time_base','1:25','-video_track_timescale','12800','-color_primaries','bt709','-color_trc','iec61966-2-1','-colorspace','bt709','-movflags','+faststart','-n',str(movie)]
    elif stage=='probe':
        tool=bindir/'ffprobe';pin='666c4ecdff7d14153d53e35cd83f0b0f37bffb7250080e994b90b59c575cd264'
        arguments=['-v','error','-count_frames','-show_frames','-show_streams','-show_format','-of','json',str(movie)]
    else:
        tool=bindir/'ffmpeg';pin='3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9'
        (run/'review/decoded').mkdir()
        arguments=['-hide_banner','-nostdin','-v','error','-xerror','-i',str(movie),'-an','-fps_mode','passthrough',str(run/'review/decoded/frame-%04d.png')]
    cmd=['/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem/bin/java','-Djava.awt.headless=true','-cp',cp,'MediaStage',str(tool),pin,str(scratch/('fixed-cabin-'+stage)),str(min(90,int(300-spent))),str(8*1024**3),*arguments]
    record.write_text(json.dumps({'status':'STARTED','command':cmd})+'\n')
    with (run/'checks'/f'{stage}.log').open('x') as log:p=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=100)
    result={'status':'PASS' if p.returncode==0 else 'FAIL','exitCode':p.returncode,'seconds':time.monotonic()-start,'command':cmd}
    record.write_text(json.dumps(result,indent=2)+'\n');assert p.returncode==0
    if stage=='probe':
        facts=json.JSONDecoder().raw_decode((run/'checks/probe.log').read_text())[0];streams=facts['streams'];assert len(streams)==1;v=streams[0]
        assert (v['width'],v['height'],int(v['nb_read_frames']))==(1920,1080,129)
        assert v['avg_frame_rate']=='25/1' and v['codec_name']=='h264' and v['pix_fmt']=='yuv420p'
        times=[float(f['best_effort_timestamp_time']) for f in facts['frames']];assert len(times)==129 and all(abs(t-i/25)<1e-6 for i,t in enumerate(times))
        (run/'checks/media-facts.json').write_text(json.dumps({'stream':v,'format':facts['format'],'exactCadence':True,'noAudio':True,'sha256':hashlib.sha256(movie.read_bytes()).hexdigest()},indent=2)+'\n')
    if stage=='decode':
        values=[]
        for n in range(1,130):
            expected=np.array(Image.open(run/'review/frames'/f'frame-{n:04d}.png').convert('RGB')).astype('float32')
            actual=np.array(Image.open(run/'review/decoded'/f'frame-{n:04d}.png').convert('RGB')).astype('float32')
            mse=np.mean((expected-actual)**2);psnr=float(10*np.log10(255**2/max(mse,1e-9)))
            face=float(np.abs(expected[350:620,500:900]-actual[350:620,500:900]).mean());assert psnr>32 and face<8,(n,psnr,face)
            values.append({'frame':n,'psnrDb':psnr,'faceMae':face})
        (run/'checks/decoded-quality.json').write_text(json.dumps({'status':'PASS','frames':129,'minPsnrDb':min(x['psnrDb'] for x in values),'maxFaceMae':max(x['faceMae'] for x in values),'rows':values},indent=2)+'\n')
result['seconds']=time.monotonic()-start
assert spent+result['seconds']<300,'Cumulative processing deadline'
assert sum(p.stat().st_size for p in run.rglob('*') if p.is_file())<1024**3,'New output storage limit'
record.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k not in ['command','sourceBatches']}))
