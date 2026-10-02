"""One no-retry headless raster preparation command; stop only its own process group."""
import argparse, importlib.util, json, os, signal, subprocess, time
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('manifest',type=Path)
args=parser.parse_args()
spec=json.loads(args.manifest.read_text())
owner=Path(__file__).resolve().parents[2]
module_spec=importlib.util.spec_from_file_location('generation_guard',owner/'generation/scripts/run.py')
guard=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(guard)
receipt=Path(spec['receipt']);assert not receipt.exists(), 'Consumed run; no automatic retry'
assert spec['rssBytes'] <= 8*1024**3 and spec['seconds'] <= 900
receipt.write_text(json.dumps({'status':'STARTED','manifest':str(args.manifest.resolve())})+'\n')
start=time.monotonic();peak=0;reason=None
with receipt.with_suffix('.log').open('x') as log:
    p=subprocess.Popen(spec['command'],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    try:
        while p.poll() is None:
            peak=max(peak,sum(row['rssBytes'] for row in guard.process_tree(p.pid)))
            elapsed=time.monotonic()-start
            if peak>spec['rssBytes']:reason='owned RSS limit'
            if elapsed>spec['seconds']:reason='time limit'
            if guard.disk_bytes(owner/'outlines')>spec['storageBytes']:reason='evidence storage limit'
            if reason:os.killpg(p.pid,signal.SIGTERM);break
            time.sleep(.5)
        p.wait(timeout=20)
    finally:
        if p.poll() is None:
            os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=20)
report={'status':'PASS' if p.returncode==0 and reason is None else 'FAIL','exitCode':p.returncode,
    'stopReason':reason,'seconds':round(time.monotonic()-start,3),'peakSampledRssBytes':peak,
    'command':spec['command'],'limits':{k:spec[k] for k in ['seconds','rssBytes','storageBytes']}}
receipt.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
assert report['status']=='PASS'
