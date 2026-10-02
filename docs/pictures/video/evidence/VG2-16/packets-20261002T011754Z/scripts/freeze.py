"""Freeze exact non-live packets; this script never launches native tools."""
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path
import paths

BASE=Path(__file__).resolve().parent.parent
REPO=BASE.parents[5]
RIG=REPO/'docs/pictures/video/evidence/VG2-15/rig-20261002T010147Z'


def sha(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()


def write(p,value):
    with p.open('x') as f:json.dump(value,f,indent=2);f.write('\n')


def pin(p):return {'path':str(p.relative_to(REPO)),'sha256':sha(p)}


def main():
    assert sys.argv[1:] in (['--prepare'],['--bind-passing-colour'])
    scope=json.loads((BASE/'inputs/scope.json').read_text())
    if sys.argv[1]=='--bind-passing-colour':
        p=json.loads((BASE/'packets/wave-proposal-v2.json').read_text())
        colour=Path(scope['colourOwner'])/'live'
        result=json.loads((colour/'result.json').read_text())
        assert result['status']=='TECHNICAL_PROOF_PASS_NOT_MOVING_APPROVAL' and result['decoderTraversals']==2
        assert sha(result['reviewFile'])==result['mp4Sha256']
        p['colourProof']={str(colour/name):sha(colour/name) for name in ['result.json','decoded-pixels.json','source-pixels.json','media-facts.json']}
        p['files'].update(p['colourProof'])
        p['files'][str(BASE/'packets/colour-v2.json')]=sha(BASE/'packets/colour-v2.json')
        write(BASE/'packets/wave.json',p)
        print('WAVE_PACKET_BOUND_TO_PASSING_COLOUR',sha(BASE/'packets/wave.json'));return
    old=json.loads((REPO/'build/vg2-blender-phase-3noEBfTw/packet.json').read_text())
    tools=old['tools'];tools['python']=str(Path(sys.executable).resolve())
    assert sha(tools['blender'])==json.loads((RIG/'checks/evaluation-claim-v3.json').read_text())['blenderSha256']
    for name in ['ffmpeg','ffprobe','node']:assert sha(tools[name])==old['runtime']['files'][tools[name]],'Selected installed tool changed'
    runtime={'files':{},'links':{},'inventories':{}}
    for root in old['runtime']['inventories']:
        inventory=[]
        for f in sorted(Path(root).rglob('*')):
            if f.is_symlink():runtime['links'][str(f)]=os.readlink(f)
            if f.is_file() or f.is_symlink():inventory.append(str(f.relative_to(root)))
            if f.is_file():runtime['files'][str(f.resolve())]=sha(f.resolve())
        runtime['inventories'][root]=sorted(inventory)
    for value in list(tools.values())+['/bin/ps','/usr/bin/vm_stat','/usr/sbin/sysctl']:
        p=Path(value).resolve();runtime['files'][str(p)]=sha(p)
    write(BASE/'inputs/runtime-v2.json',runtime)
    node,ff,probe,blender=[tools[k] for k in ['node','ffmpeg','ffprobe','blender']]
    files={str(f):sha(f) for f in (BASE/'scripts').iterdir() if f.is_file()}
    files[str(BASE/'inputs/scope.json')]=sha(BASE/'inputs/scope.json')
    for p in json.loads((RIG/'checks/handoff-final.json').read_text())['pins']:files[str(REPO/p['path'])]=p['sha256']
    for name in ['handoff-final.json','support-final.json']:files[str(RIG/'checks'/name)]=sha(RIG/'checks'/name)
    for kind in ['colour','wave']:
        owner=Path(scope[kind+'Owner']);work=owner/'live';review=owner/'review'/(kind+'.mp4')
        destinations={'work':str(work),'review':str(review),'stagedReview':str(review.parent/('.'+review.name+'.partial'))}
        scratch=Path(tempfile.mkdtemp(prefix='melotrail-vg2-'+kind+'-')).resolve()
        frames,width,height=(6,640,360) if kind=='colour' else (150,1920,1080)
        phase={'render':60,'encode':60,'validationAndCopy':60} if kind=='colour' else {'render':600,'encode':180,'validationAndCopy':120}
        render=[node,str(BASE/'scripts/colour_source.cjs'),'--generate'] if kind=='colour' else [blender,'--factory-startup','--background','--disable-autoexec','--threads','4',str(RIG/'rig/tabi-wave-v3.blend'),'--python-exit-code','1','--python',str(BASE/'scripts/render_wave.py'),'--','render']
        ops=[
            {'id':'render','phase':'render','decoderCost':0,'argv':render},
            {'id':'source','phase':'render','decoderCost':0,'argv':[node,str(BASE/'scripts/verify_pixels.cjs'),'source']},
            {'id':'encode','phase':'encode','decoderCost':0,'argv':[ff,'-nostdin','-hide_banner','-v','error','-xerror','-n','-protocol_whitelist','file,pipe','-f','image2','-framerate','30','-start_number','1','-i',str(work/'encoded_input/frame-%04d.png'),'-frames:v',str(frames),'-map','0:v:0','-an','-sn','-dn','-c:v','h264_videotoolbox','-allow_sw','0','-b:v','12M','-vf','scale=iw:ih:in_range=full:out_range=tv:out_color_matrix=bt709','-pix_fmt','yuv420p','-aspect',str(width)+':'+str(height),'-colorspace','bt709','-color_trc','bt709','-color_primaries','bt709','-color_range','tv','-video_track_timescale','15360','-movflags','+faststart','-f','mp4',str(work/'preview.mp4')]},
            {'id':'probe','phase':'validationAndCopy','decoderCost':1,'argv':[probe,'-v','error','-count_frames','-show_streams','-show_frames','-show_format','-of','json',str(work/'preview.mp4')]},
            {'id':'decode','phase':'validationAndCopy','decoderCost':1,'argv':[ff,'-nostdin','-v','error','-xerror','-n','-threads','1','-protocol_whitelist','file,pipe','-i',str(work/'preview.mp4'),'-map','0:v:0','-an','-sn','-dn','-fps_mode','passthrough','-vf','scale=iw:ih:in_range=tv:out_range=full:in_color_matrix=bt709','-pix_fmt','rgba','-threads','1','-start_number','1',str(work/'decoded/frame-%04d.png')]},
            {'id':'pixels','phase':'validationAndCopy','decoderCost':0,'argv':[node,str(BASE/'scripts/verify_pixels.cjs'),'decoded']}]
        profile='(version 1)\n(allow default)\n(deny network*)\n(deny process-fork)\n(deny file-write* (require-not (require-any (subpath '+json.dumps(str(work))+') (subpath '+json.dumps(str(scratch))+'))))\n'
        p={'status':'FROZEN_PROPOSAL_NOT_AUTHORIZED','kind':kind,'owner':str(BASE),'tools':tools,'files':files,
           'runtimeFile':{'path':str(BASE/'inputs/runtime-v2.json'),'sha256':sha(BASE/'inputs/runtime-v2.json')},
           'destinations':destinations,'destinationContract':paths.capture(destinations),'scratch':paths.identity(scratch,paths.directory_info(scratch)),
           'sandboxProfile':profile,'operations':ops,
           'limits':{'attempts':1,'retries':0,'fallbacks':0,'cumulativeSeconds':sum(phase.values()),'phaseSeconds':phase,'aggregateRssBytes':4*1024**3,'newStorageBytes':(128*1024**2 if kind=='colour' else 2*1024**3),'minimumFreeDiskBytes':18*1024**3,'minimumFreeMemoryBytes':3*1024**3,'normalMemoryPressureSamples':3,'maximumSwapGrowthBytes':0,'freeDiskReserveBytes':10*1024**3},
           'delivery':{'frames':frames,'fps':30,'seconds':frames/30,'width':width,'height':height,'codec':'h264','audioStreams':0},
           'pixelContract':{'sourceProtectedMaxDelta':2,'decodedSrgbMae':5,'armSrgbMae':6,'patchSrgbMaxDelta':4,'decodedReturnMae':2.5},
           'artSample':pin(RIG/'textures/revealed_torso_cabin_backing.png'),'allowedMask':pin(RIG/'textures/allowed.png'),
           'rig':pin(RIG/'rig/tabi-wave-v3.blend'),'rigHelper':pin(RIG/'scripts/rig.py'),'geometry':pin(RIG/'checks/check-geometry-v3.json'),
           'colourProof':None,'colourPath':'Explicit CPU sRGB EOTF -> Rec.709 OETF PNG transport, then Rec.709 RGB/YUV matrix. Decoded comparisons invert both transfer functions into common sRGB.',
           'nativeCounts':{'renderBatches':0 if kind=='colour' else 1,'sourceFrames':frames,'pngConversions':frames,'encodes':1,'probeScans':1,'fullDecodes':1,'validatedReviewCopies':1},
           'admissionBoundary':'A fresh per-packet admission binds the user continuation instruction to these exact operations and finite limits. Wave requires a separate final packet with passing colour evidence. No media runs from preflight/tests.',
           'storage':'Retain immutable local source/converted/decoded frames, failed outputs and review MP4 under this proof owner. MP4 is ignored; later tracking/archival selection must preserve exact bytes and hashes.',
           'limitOfProof':'One local colour experiment or one five-second standalone rig wave; no production import, selected take, app feature, second activity or full-film acceptance.'}
        write(BASE/'packets'/('colour-v2.json' if kind=='colour' else 'wave-proposal-v2.json'),p)
        print(kind,sha(BASE/'packets'/('colour-v2.json' if kind=='colour' else 'wave-proposal-v2.json')))


if __name__=='__main__':main()
