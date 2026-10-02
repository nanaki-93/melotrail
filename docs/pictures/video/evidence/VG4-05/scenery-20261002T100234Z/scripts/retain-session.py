"""Retain and verify only the stopped runtime session named by this proof."""
from pathlib import Path
import datetime,hashlib,json,os,shutil,sys
stage=Path(sys.argv[1]).resolve(strict=True)
execution=json.loads((stage/'checks/execution.json').read_text())
assert 'RUNTIME_FINAL=STOPPED' in (stage/'checks/generation.log').read_text()
for pid in execution['observedPids']:
 try:os.kill(pid,0)
 except ProcessLookupError:continue
 raise RuntimeError('Observed process is still present: '+str(pid))
session=Path((stage/'output/session-path.txt').read_text().strip())
owner=Path('/Users/marcoandreose/Library/Application Support/MelotrailVideo/runs')
assert session.parent==owner and session.name.startswith('comfy-')
assert session.is_dir() and not session.is_symlink() and session.resolve().parent==owner.resolve()
paths=list(session.rglob('*'));assert all(not p.is_symlink() for p in paths)
destination=stage/'runtime-session';assert not destination.exists()
shutil.copytree(session,destination)
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
files=[]
for p in sorted(paths):
 if p.is_file():
  rel=p.relative_to(session);copy=destination/rel;digest=sha(p)
  assert sha(copy)==digest
  files.append({'path':str(rel),'bytes':p.stat().st_size,'sha256':digest})
shutil.rmtree(session)
assert not session.exists()
record={'recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'runtimeState':'STOPPED','observedPidsGone':execution['observedPids'],'originalSession':str(session),'retainedSession':str(destination),'files':files,'allCopiesVerified':True,'ownedSessionRemoved':True}
(stage/'checks/runtime-cleanup.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'status':'PASS','filesRetained':len(files),'ownedSessionRemoved':True}))
