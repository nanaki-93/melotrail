"""Prepare one retained-frame composition packet without producing media."""
import copy
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
    assert not (BASE/'packets/wave.json').exists(), 'No packet overwrite'
    scope=json.loads((BASE/'inputs/scope.json').read_text())
    p=json.loads(Path(scope['previousWavePacket']).read_text())
    p['files'][scope['previousWavePacket']]=sha(scope['previousWavePacket'])
    failed_proposal=json.loads(Path(scope['previousCompositionPacket']).read_text())
    p['files'].update(failed_proposal['files'])
    p['files'][scope['previousCompositionPacket']]=sha(scope['previousCompositionPacket'])
    p['files'].update({str(f):sha(f) for f in (BASE/'scripts').iterdir() if f.is_file()})
    p['files'][str(BASE/'inputs/scope.json')]=sha(BASE/'inputs/scope.json')
    repo=BASE.parents[5]
    test=repo/'tools/video-motion/vg2-support-margin.test.cjs';p['files'][str(test)]=sha(test)
    prior=Path(scope['retainedWaveOwner']);inventory=prior/'checks/local-media-storage.json'
    p['files'][str(inventory)]=sha(inventory)
    for name in ['live/rendered-geometry.json','live/failure.json','checks/source-diagnosis.json','checks/failure-analysis.json','checks/all-frames-temporal-diagnosis.json']:
        f=prior/name;p['files'][str(f)]=sha(f)
    rows=json.loads(inventory.read_text())['mediaFiles'];assert len(rows)==150
    p['retainedFrames']=[]
    for n,row in enumerate(rows,1):
        f=Path(row['path']);assert f.name=='frame-%04d.png'%n and sha(f)==row['sha256']
        p['files'][str(f)]=row['sha256'];p['retainedFrames'].append({'frame':n,'path':str(f),'sha256':row['sha256']})
    assert p['colourProof']
    for f,digest in p['colourProof'].items():assert sha(f)==digest
    p['supportMarginPixels']=1
    p['supportReason']='Explicit 1-pixel Chebyshev dilation of the original frozen mask: captures measured x=747 antialias pixels in six frames. Original-mask failure remains; no source/colour tolerance changes.'
    p['owner']=str(BASE);owner=Path(scope['waveOwner']);work=owner/'live';review=owner/'review/wave.mp4'
    oldwork=p['destinations']['work'];oldbase=Path(scope['previousWavePacket']).parent.parent
    p['destinations']={'work':str(work),'review':str(review),'stagedReview':str(review.parent/'.wave.mp4.partial')}
    p['destinationContract']=paths.capture(p['destinations'])
    scratch=Path(tempfile.mkdtemp(prefix='melotrail-vg2-fixed-artwork-')).resolve()
    p['scratch']=paths.identity(scratch,paths.directory_info(scratch))
    p['sandboxProfile']='(version 1)\n(allow default)\n(deny network*)\n(deny process-fork)\n(deny file-write* (require-not (require-any (subpath '+json.dumps(str(work))+') (subpath '+json.dumps(str(scratch))+'))))\n'
    for op in p['operations']:
        op['argv']=[arg.replace(str(oldbase/'scripts'),str(BASE/'scripts')).replace(oldwork,str(work)) for arg in op['argv']]
    p['operations'][0]={'id':'compose','phase':'compose','decoderCost':0,'argv':[p['tools']['node'],str(BASE/'scripts/compose_frames.cjs'),'--compose']}
    p['operations'][1]['phase']='compose'
    p['limits']['cumulativeSeconds']=360
    p['limits']['phaseSeconds']={'compose':120,'encode':120,'validationAndCopy':120}
    p['nativeCounts']={'renderBatches':0,'retainedFrameReads':150,'sourceComposites':150,'pngConversions':150,'encodes':1,'probeScans':1,'fullDecodes':1,'validatedReviewCopies':1,'transportMetadataDerivatives':150}
    p['correction']='Use each saved render unchanged inside the explicitly expanded one-pixel support mask and original artwork pixels outside it. Before composition reject any temporal change outside the mask. No further mask expansion, fades, new artwork, rig/motion changes or Blender rerender. Preserve strict source and decoded colour contracts.'
    p['admissionBoundary']='FRESH USER PERMISSION REQUIRED AFTER FAILED FIRST WAVE. Six minutes and 2 GiB maximum, one attempt, no retries; passing colour proof retained and hash-bound.'
    p['newStorageEstimate']={'method':'Three 150-frame sequences at measured retained-frame total, plus 25 percent margin and 32 MiB for MP4, logs and packet','retainedSourceBytes':sum(r['bytes'] for r in rows),'estimatedNewBytes':int(sum(r['bytes'] for r in rows)*3*1.25)+32*1024**2,'maximumBytes':p['limits']['newStorageBytes']}
    assert p['newStorageEstimate']['estimatedNewBytes']<p['limits']['newStorageBytes']
    encoding.check_packet_filters(p)
    with (BASE/'packets/wave.json').open('x') as f:json.dump(p,f,indent=2);f.write('\n')
    print('FROZEN_COMPOSITION_PROPOSAL_NO_ADMISSION',sha(BASE/'packets/wave.json'))


if __name__=='__main__':main()
