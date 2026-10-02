"""Prepare the exact correction without native media; no automatic admission."""
import copy
import hashlib
import json
import sys
import tempfile
from pathlib import Path
import encoding
import paths

BASE=Path(__file__).resolve().parent.parent


def sha(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()


def write(p,value):
    with p.open('x') as f:json.dump(value,f,indent=2);f.write('\n')


def main():
    assert sys.argv[1:] in (['--prepare'],['--bind-passing-colour'])
    scope=json.loads((BASE/'inputs/scope.json').read_text())
    if sys.argv[1]=='--bind-passing-colour':
        p=json.loads((BASE/'packets/wave-proposal.json').read_text())
        colour=Path(scope['colourOwner'])/'live';result=json.loads((colour/'result.json').read_text())
        assert result['status']=='TECHNICAL_PROOF_PASS_NOT_MOVING_APPROVAL' and result['decoderTraversals']==2
        assert sha(result['reviewFile'])==result['mp4Sha256']
        p['colourProof']={str(colour/name):sha(colour/name) for name in ['result.json','decoded-pixels.json','source-pixels.json','media-facts.json']}
        p['files'].update(p['colourProof']);p['files'][str(BASE/'packets/colour.json')]=sha(BASE/'packets/colour.json')
        write(BASE/'packets/wave.json',p);print('WAVE_BOUND_TO_CORRECTED_COLOUR',sha(BASE/'packets/wave.json'));return
    previous=Path(scope['previousPacketOwner']);reused=Path(scope['failedColourOwner'])/'live'
    source=json.loads((reused/'source-pixels.json').read_text())
    assert source['status']=='SOURCE_AND_EXPLICIT_TRANSFER_PASS' and len(source['frames'])==6
    extra={str(BASE/'inputs/scope.json'):sha(BASE/'inputs/scope.json')}
    extra.update({str(f):sha(f) for f in (BASE/'scripts').iterdir() if f.is_file()})
    extra[str(reused/'source-pixels.json')]=sha(reused/'source-pixels.json')
    manifest=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin/melotrail-video-tools.json')
    manifestData=json.loads(manifest.read_text());extra[str(manifest)]=sha(manifest)
    for frame in source['frames']:
        for folder,key in [('source','sourceSha256'),('encoded_input','convertedSha256')]:
            f=reused/folder/('frame-%04d.png'%frame['frame']);assert sha(f)==frame[key];extra[str(f)]=frame[key]
    extra[str(reused/'patches.json')]=sha(reused/'patches.json')
    for kind,filename in [('colour','colour.json'),('wave','wave-proposal.json')]:
        p=json.loads((previous/'packets'/filename).read_text());p['owner']=str(BASE)
        p['files'].update(extra);p['files'][str(previous/'packets'/filename)]=sha(previous/'packets'/filename)
        owner=Path(scope[kind+'Owner']);work=owner/'live';review=owner/'review'/(kind+'.mp4')
        oldwork=p['destinations']['work']
        p['destinations']={'work':str(work),'review':str(review),'stagedReview':str(review.parent/('.'+review.name+'.partial'))}
        p['destinationContract']=paths.capture(p['destinations'])
        scratch=Path(tempfile.mkdtemp(prefix='melotrail-vg2-transfer-'+kind+'-')).resolve()
        p['scratch']=paths.identity(scratch,paths.directory_info(scratch))
        p['sandboxProfile']='(version 1)\n(allow default)\n(deny network*)\n(deny process-fork)\n(deny file-write* (require-not (require-any (subpath '+json.dumps(str(work))+') (subpath '+json.dumps(str(scratch))+'))))\n'
        for op in p['operations']:
            op['argv']=[arg.replace(str(previous/'scripts'),str(BASE/'scripts')).replace(oldwork,str(work)) for arg in op['argv']]
            if op['id']=='encode':op['argv'][op['argv'].index('-vf')+1]=encoding.FILTER;encoding.require_frame_tags(op['argv'])
        p['installedManifest']={'path':str(manifest),'sha256':sha(manifest),'ffmpegSha256':manifestData['ffmpegSha256'],'sourceRevision':manifestData['sourceRevision']}
        encoding.check_packet_filters(p)
        p['reuseOwner']=str(reused) if kind=='colour' else None
        if kind=='colour':
            p['nativeCounts'].update(sourceFrames=0,pngConversions=0,reusedSourceCopies=6,reusedConvertedCopies=0,patchReferenceCopies=1,transportMetadataDerivatives=6)
        if kind=='wave':p['nativeCounts']['transportMetadataDerivatives']=150
        p['correction']='Keep converted Rec.709 PNG pixel/IDAT bytes and replace conflicting colour metadata with cICP [1,1,0,1]. The pinned decoder source supports this declaration. Read raw PNG code values for explicit common-sRGB comparison. Validate every filter against the installed manifest before launch; no setparams or source rerender.'
        p['admissionBoundary']='FRESH USER PERMISSION REQUIRED AFTER TWO FAILED COLOUR ATTEMPTS. No automatic retry. Passing colour must be bound before the separately admitted first wave.'
        p['storage']='User selects local media and frames; only scripts, checks and hashes enter Git.'
        write(BASE/'packets'/('colour.json' if kind=='colour' else 'wave-proposal.json'),p)
        print(kind,sha(BASE/'packets'/('colour.json' if kind=='colour' else 'wave-proposal.json')))


if __name__=='__main__':main()
