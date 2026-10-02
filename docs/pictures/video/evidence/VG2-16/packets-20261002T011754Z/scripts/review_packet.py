"""Independent read-only packet audit plus real supervisor calls with child spies."""
import copy
import json
import os
import sys
import tempfile
from pathlib import Path
from contextlib import ExitStack
from unittest.mock import patch
import preview


def review():
    p=preview.config();preview.preflight(p)
    expected_frames=6 if preview.KIND=='colour' else 150
    assert p['delivery']['frames']==expected_frames
    assert p['nativeCounts']=={'renderBatches':0 if preview.KIND=='colour' else 1,'sourceFrames':expected_frames,'pngConversions':expected_frames,'encodes':1,'probeScans':1,'fullDecodes':1,'validatedReviewCopies':1}
    assert p['pixelContract']=={'sourceProtectedMaxDelta':2,'decodedSrgbMae':5,'armSrgbMae':6,'patchSrgbMaxDelta':4,'decodedReturnMae':2.5}
    assert p['limits']['attempts']==1 and p['limits']['retries']==p['limits']['fallbacks']==0
    assert sum(preview.decoder_cost(o) for o in p['operations'])==2
    assert p['operations'][2]['argv'][p['operations'][2]['argv'].index('-i')+1].endswith('/encoded_input/frame-%04d.png')
    assert '-n' in p['operations'][2]['argv'] and '-n' in p['operations'][4]['argv']
    calls=[];now=[1.0]
    reading={'freeMemoryBytes':4*1024**3,'freeDiskBytes':50*1024**3,'pressureCode':'1','swapUsedBytes':0}
    class Child:
        pid=1999999999;returncode=0
        def poll(self):return self.returncode
    def child(argv,**kwargs):calls.append(argv);return Child()
    with tempfile.TemporaryDirectory(prefix='melotrail-packet-review-') as temp:
        q=copy.deepcopy(p);q['destinations']['work']=temp
        with ExitStack() as stack:
            for obj,name,value in [(preview.time,'monotonic',lambda:now[0]),(preview,'verify_bindings',lambda p:None),(preview,'authorize',lambda p:None),(preview,'sha',lambda p:'fixture'),(preview,'sample',lambda:reading),(preview,'bytes_under',lambda p:0),(preview,'process_rows',lambda:{os.getpid():{'rss':100,'parent':0,'group':0}}),(preview.paths,'validate',lambda *a,**kw:{}),(preview.subprocess,'Popen',child)]:stack.enter_context(patch.object(obj,name,value))
            supervisor=preview.Supervisor(q,{'swapUsedBytes':0},p['limits']['cumulativeSeconds'],{})
            budget=preview.Budget(p['operations'],2,Path(temp)/'reservations.jsonl',p['limits']['cumulativeSeconds'],recheck=supervisor.check,clock=lambda:now[0])
            try:
                for instant,op in zip([1,5,10,20,25,30],p['operations']):
                    now[0]=instant;budget.invoke(op['id'],op['argv'],lambda op=op:supervisor.execute(op))
                assert len(calls)==6 and budget.spent==2
                assert [argv[3:] for argv in calls]==[op['argv'] for op in p['operations']]
                end=20+p['limits']['phaseSeconds']['validationAndCopy']
                now[0]=end-.1;supervisor.check()
                now[0]=end
                try:supervisor.check()
                except RuntimeError as e:assert 'deadline' in str(e)
                else:raise AssertionError('Publication renewed phase deadline')
            finally:budget.close()
    print(json.dumps({'status':'EXACT_PACKET_DATA_REVIEW_PASS','kind':preview.KIND,'nativeToolsLaunched':0,'dataChildSpies':len(calls),'decoderReservationsInSpies':2,'packetSha256':preview.sha(preview.PACKET)}))


if __name__=='__main__':
    assert sys.argv[1:] in (['colour'],['wave']);review()
