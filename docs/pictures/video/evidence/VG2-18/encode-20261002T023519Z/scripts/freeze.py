"""Freeze a no-rerender/no-recomposition encode proposal; never run media."""
import hashlib
import json
import tempfile
from pathlib import Path
import paths
import encoding

BASE=Path(__file__).resolve().parent.parent


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def main():
    assert not (BASE/'packets/wave.json').exists()
    scope=json.loads((BASE/'inputs/scope.json').read_text());oldfile=Path(scope['previousEncodePacket']);oldbase=oldfile.parent.parent
    p=json.loads(oldfile.read_text());p['files'][str(oldfile)]=sha(oldfile)
    p['files'].update({str(f):sha(f) for f in (BASE/'scripts').iterdir() if f.is_file()})
    p['files'][str(BASE/'inputs/scope.json')]=sha(BASE/'inputs/scope.json')
    test=BASE.parents[5]/'tools/video-motion/vg2-saved-source.test.cjs';p['files'][str(test)]=sha(test)
    sourceOwner=Path(scope['completedSourceOwner']);proof=sourceOwner/'live/composition.json';facts=json.loads(proof.read_text())
    assert facts['status']=='COMPOSED_SOURCE_PASS_NOT_WAVE_APPROVAL' and len(facts['frames'])==150
    p['completedComposition']={'path':str(proof),'sha256':sha(proof)}
    p['retainedFrames']=[]
    for i,row in enumerate(facts['frames'],1):
        source=sourceOwner/'live/source'/('frame-%04d.png'%i)
        assert row['frame']==i and sha(source)==row['sourceSha256']
        p['retainedFrames'].append({'frame':i,'path':str(source),'sha256':row['sourceSha256']});p['files'][str(source)]=row['sourceSha256']
    for name in ['live/composition.json','live/failure.json','live/compose-resource.json','checks/failure-analysis.json','checks/local-media-storage.json']:
        f=sourceOwner/name;p['files'][str(f)]=sha(f)
    p['owner']=str(BASE);owner=Path(scope['waveOwner']);work=owner/'live';review=owner/'review/wave.mp4';oldwork=p['destinations']['work']
    p['destinations']={'work':str(work),'review':str(review),'stagedReview':str(review.parent/'.wave.mp4.partial')};p['destinationContract']=paths.capture(p['destinations'])
    scratch=Path(tempfile.mkdtemp(prefix='melotrail-vg2-saved-source-')).resolve();p['scratch']=paths.identity(scratch,paths.directory_info(scratch))
    p['sandboxProfile']='(version 1)\n(allow default)\n(deny network*)\n(deny process-fork)\n(deny file-write* (require-not (require-any (subpath '+json.dumps(str(work))+') (subpath '+json.dumps(str(scratch))+'))))\n'
    for op in p['operations']:op['argv']=[a.replace(str(oldbase/'scripts'),str(BASE/'scripts')).replace(oldwork,str(work)) for a in op['argv']]
    p['operations'][0]['argv']=[p['tools']['node'],str(BASE/'scripts/reuse_source.cjs'),'--copy']
    p['limits']['phaseSeconds']={'compose':180,'encode':60,'validationAndCopy':120}
    assert p['limits']['cumulativeSeconds']==sum(p['limits']['phaseSeconds'].values())==360
    p['nativeCounts'].update(sourceComposites=0,reusedSourceCopies=150)
    p['correction']='Copy the 150 completed and validated source PNGs unchanged. No new composition or rendering. Revalidate source pixels, convert to the verified Rec.709 transport, encode once, scan once and decode once. Existing one-pixel support margin and all numeric thresholds remain.'
    p['admissionBoundary']='FRESH USER PERMISSION REQUIRED AFTER PREPARATION DEADLINE FAILURE. One six-minute/2-GiB attempt, no retries. Source copy/conversion 180s; encode 60s; validation/copy 120s.'
    measured=sum(Path(row['path']).stat().st_size for row in p['retainedFrames'])
    p['newStorageEstimate']={'method':'Three 150-frame sequences at actual corrected-source total, plus 25 percent margin and 32 MiB for MP4/logs/packet','retainedSourceBytes':measured,'estimatedNewBytes':int(measured*3*1.25)+32*1024**2,'maximumBytes':p['limits']['newStorageBytes']}
    assert p['newStorageEstimate']['estimatedNewBytes']<p['limits']['newStorageBytes']
    for f,digest in p['colourProof'].items():assert sha(f)==digest
    encoding.check_packet_filters(p)
    with (BASE/'packets/wave.json').open('x') as f:json.dump(p,f,indent=2);f.write('\n')
    print('FROZEN_COMPLETED_SOURCE_PROPOSAL',sha(BASE/'packets/wave.json'))


if __name__=='__main__':main()
