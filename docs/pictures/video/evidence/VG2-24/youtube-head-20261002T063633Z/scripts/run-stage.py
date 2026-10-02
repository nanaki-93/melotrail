"""Explicit one-use stages under the existing production media-process owner."""
from pathlib import Path
import datetime,hashlib,json,os,subprocess,sys,time,shutil
run=Path(__file__).resolve().parents[1]
a=json.loads((run/'admission-correction.json').read_text());scratch=Path(a['scratch'])
state_path=run/'checks/execution.json';state=json.loads(state_path.read_text()) if state_path.exists() else {'stages':[],'activeSeconds':0}
name=sys.argv[1];assert name in a['stages'];assert all(x['name']!=name for x in state['stages']),'Stage already consumed; no automatic retries'
for pin in a['pins']:
 p=Path(pin['path']);assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==pin['sha256'],f'Pin drift: {p}'
assert shutil.disk_usage(run).free>20*1024**3
stage=a['stages'][name];remaining=600-state['activeSeconds'];assert remaining>0
limit=min(stage['seconds'],int(remaining));cp=str(scratch/'classes')+':'+(scratch/'runtime-classpath.txt').read_text().strip()
command=[a['java'],'-Djava.awt.headless=true','-cp',cp,'MediaStage',stage['executable'],stage['sha256'],str(scratch/('stage-'+name)),str(limit),str(stage.get('rssBytes',4*1024**3)),*stage['argv']]
record={'name':name,'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':command,'status':'STARTED'};state['stages'].append(record);state_path.write_text(json.dumps(state,indent=2)+'\n')
start=time.monotonic();reason=None
with (run/'checks'/f'{name}.log').open('w') as out:
 p=subprocess.Popen(command,stdout=out,stderr=subprocess.STDOUT)
 while p.poll() is None:
  elapsed=time.monotonic()-start
  disk=sum(x.stat().st_size for x in run.rglob('*') if x.is_file())
  pressure=subprocess.run(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],capture_output=True,text=True,timeout=2)
  code=pressure.stdout.strip()
  if elapsed>limit+10:reason='outer time guard'
  elif disk>512*1024**2:reason='512 MiB output guard'
  elif shutil.disk_usage(run).free<20*1024**3:reason='20 GiB free reserve'
  elif pressure.returncode or code not in ['1','2']:reason='critical or unknown memory pressure'
  if reason:p.terminate();break
  time.sleep(1)
 try:p.wait(timeout=20)
 except subprocess.TimeoutExpired:raise RuntimeError('Production process-owner shutdown unconfirmed; retain scratch')
record.update({'status':'PASSED' if p.returncode==0 and reason is None else 'FAILED','exitCode':p.returncode,'stopReason':reason,'elapsedSeconds':round(time.monotonic()-start,3)});state['activeSeconds']+=record['elapsedSeconds'];state_path.write_text(json.dumps(state,indent=2)+'\n')
log=run/'checks'/f'{name}.log';log.write_text('\n'.join(x.rstrip() for x in log.read_text().splitlines()).rstrip()+'\n')
print(json.dumps({k:v for k,v in record.items() if k!='command'}));print(log.read_text()[-6000:]);raise SystemExit(0 if record['status']=='PASSED' else 1)
