"""One headless, data-only build/reopen pair; no render/encode/decode entrypoint."""
import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path
from rig_guard import need, sha, fresh_json, bytes_under, process_rows, owned_ids

BASE=Path(__file__).resolve().parent.parent
REPO=BASE.parents[5]
BLENDER=Path('/Applications/Blender.app/Contents/MacOS/Blender')
EXPECTED_BLENDER='f5c1f2a0c0bca3389ec772d355242b700975ea84e66cbb3f5257a7bce8e5adeb'


def commands(base=BASE):
    prefix=[str(BLENDER),'--factory-startup','--background','--disable-autoexec','--threads','4']
    tail=['--python-exit-code','1','--python',str(base/'scripts/rig.py'),'--']
    return [('build',prefix+tail+['build']),('check',prefix+[str(base/'rig/tabi-wave-v3.blend')]+tail+['check'])]


def validate_commands(plan,base=BASE):
    need(plan==commands(base),'Changed or unadmitted data operation')
    need(all('--background' in cmd and '--disable-autoexec' in cmd for _,cmd in plan),'Visible/autoexec operation')


def enforce(now,deadline,rss,storage):
    need(now<deadline,'Cumulative 120-second data evaluation expired')
    need(rss<=4*1024**3,'Owned RSS exceeds 4 GiB')
    need(storage<=512*1024**2,'New rig storage exceeds 512 MiB')


def run():
    started=time.monotonic()
    spent=sum(json.loads((BASE/p).read_text())['elapsedSeconds'] for p in ['checks/evaluation-result.json','checks/evaluation-failure-v2.json'])
    deadline=started+120-spent
    need(sha(BLENDER)==EXPECTED_BLENDER,'Installed Blender changed')
    plan=commands();validate_commands(plan)
    admission=json.loads((BASE/'inputs/admission.json').read_text())
    scratch=Path(admission['scratch'])/'blender-runtime'
    scratch.mkdir()
    pins={str(p):sha(p) for root in ['scripts','textures','inputs'] for p in (BASE/root).rglob('*') if p.is_file() and '__pycache__' not in p.parts}
    fresh_json(BASE/'checks/evaluation-claim-v3.json',{'commands':plan,'pins':pins,'blenderSha256':EXPECTED_BLENDER,'limits':admission['limits'],'renderedFrames':0})
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',TMPDIR=str(scratch)+'/',BLENDER_USER_CONFIG=str(scratch),BLENDER_USER_SCRIPTS=str(scratch))
    for key in ['PYTHONPATH','PYTHONHOME','DYLD_LIBRARY_PATH','DYLD_INSERT_LIBRARIES','LD_PRELOAD']:env.pop(key,None)
    peak=0;completed=[];proc=None
    try:
        for ordinal,(mode,argv) in enumerate(plan,1):
            for p,digest in pins.items():need(sha(p)==digest,'Data helper/input changed')
            enforce(time.monotonic(),deadline,0,bytes_under(BASE))
            fresh_json(BASE/('checks/'+mode+'-reservation-v3.json'),{'ordinal':ordinal,'argv':argv,'renderCost':0,'mediaTraversals':0})
            with (BASE/('checks/'+mode+'-blender-v3.log')).open('xb') as output:
                proc=subprocess.Popen(argv,stdout=output,stderr=subprocess.STDOUT,cwd=BASE,start_new_session=True,env=env)
                while True:
                    rows=process_rows();owned=owned_ids(rows,proc.pid)
                    rss=sum(rows[i]['rss'] for i in owned)+rows.get(os.getpid(),{}).get('rss',0);peak=max(peak,rss)
                    enforce(time.monotonic(),deadline-5,rss,bytes_under(BASE))
                    if proc.poll() is not None:
                        need(proc.returncode==0,'Data evaluation failed: '+mode)
                        need(not owned_ids(process_rows(),proc.pid),'Owned child survived completion')
                        completed.append(mode);proc=None;break
                    time.sleep(.25)
        fresh_json(BASE/'checks/evaluation-result-v3.json',{'status':'DATA_ONLY_BUILD_REOPEN_PASS','completed':completed,'elapsedSeconds':time.monotonic()-started,'sampledPeakAggregateRssBytes':peak,'newStorageBytes':bytes_under(BASE),'renderedFrames':0,'mediaTraversals':0})
        print('DATA_ONLY_BUILD_REOPEN_PASS')
    except BaseException as error:
        if proc is not None:
            try:os.killpg(proc.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            try:proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=2)
            need(not owned_ids(process_rows(),proc.pid),'Owned teardown unconfirmed')
        fresh_json(BASE/'checks/evaluation-failure-v3.json',{'error':str(error),'completed':completed,'elapsedSeconds':time.monotonic()-started,'sampledPeakAggregateRssBytes':peak,'renderedFrames':0})
        raise
    finally:
        # Only this empty runtime scratch is disposable; retained outputs stay in BASE.
        files=list(scratch.iterdir())
        need(not files,'Unexpected Blender scratch must be inspected before cleanup')
        scratch.rmdir()


if __name__=='__main__':
    need(sys.argv[1:]==['--evaluate'],'Use --evaluate; no media entrypoint')
    run()
