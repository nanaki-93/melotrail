'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const repair=require('./assemble-wrists.cjs'),RUN=repair.RUN,REPO=repair.REPO;
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const pin=file=>({path:path.relative(REPO,file),sha256:sha(fs.readFileSync(file))});
function list(root){return fs.readdirSync(root,{withFileTypes:true}).flatMap(e=>{assert(!e.isSymbolicLink(),'handoff symlink');const p=path.join(root,e.name);return e.isDirectory()?list(p):[p];});}
function facts(){
  repair.verifyInputs();assert(!fs.existsSync(path.join(RUN,'parts-export.lock')));
  const journal=fs.readdirSync(path.join(RUN,'journal')).map(p=>JSON.parse(fs.readFileSync(path.join(RUN,'journal',p))));
  const ord=kind=>journal.filter(r=>r.kind===kind).map(r=>r.ordinal).sort((a,b)=>a-b);
  assert.deepEqual(ord('imageAttempt'),[4,5]);assert.deepEqual(ord('derivative'),[16,17,18,19]);assert.deepEqual(ord('staticPass'),[6,7,8]);
  for(const r of journal.filter(r=>r.kind==='imageAttempt'))assert.equal(r.targetAttempt,1);
  const selected=JSON.parse(fs.readFileSync(path.join(RUN,'checks/static-v3.json')));
  assert.equal(sha(fs.readFileSync(path.join(REPO,selected.review.path))),selected.review.sha256);
  assert.deepEqual(selected.facts.map(f=>f.state),['neutral','lift','wrist_in','wrist_out']);
  for(const f of selected.facts){assert.equal(f.outsideChanges,0);assert.equal(f.nonOpaque,0);assert.equal(f.wristEntryHoles,0);}
  const parts=JSON.parse(fs.readFileSync(path.join(RUN,'checks/parts.json')));
  assert.equal(parts.parts.length,4);for(const p of parts.parts)assert.equal(sha(fs.readFileSync(path.join(RUN,p.destination))),p.sha256);
  return {review:selected.review,consumption:{imageAttempts:5,partDerivatives:19,staticPasses:8},newUse:{imageAttempts:2,partDerivatives:4,staticPasses:3},appearance:'PENDING user VG2-14 decision',limitations:selected.limitations.concat(['The prior inner sleeve/body junction is inherited; this repair does not award full silhouette or moving approval.','Historical build dependencies remain pinned; no clean-checkout or app-delivery claim.'])};
}
function freeze(){
  const state=facts(),files=list(RUN).filter(f=>!f.endsWith('.log')&&!f.endsWith('/checks/kit-freeze.json'));
  files.push(path.join(REPO,'tools/video-motion/vg2-wrist-attachment.test.cjs'));
  fs.writeFileSync(path.join(RUN,'checks/kit-freeze.json'),JSON.stringify({schema:'connected-wrist-handoff-1',...state,pins:files.map(pin)},null,2)+'\n',{flag:'wx'});
}
function check(){
  const c=JSON.parse(fs.readFileSync(path.join(RUN,'checks/kit-freeze.json')));
  assert.equal(c.schema,'connected-wrist-handoff-1');
  for(const p of c.pins)assert.equal(sha(fs.readFileSync(path.join(REPO,p.path))),p.sha256,`changed handoff ${p.path}`);
  const actual=facts();for(const [key,value]of Object.entries(actual))assert.deepEqual(value,c[key]);
  console.log(JSON.stringify({integrity:'PASS',...actual},null,2));
}
try{assert.equal(process.argv.length,3);if(process.argv[2]==='--freeze')freeze();else if(process.argv[2]==='--check')check();else throw Error('Use --freeze or --check');}catch(e){console.error(e);process.exitCode=1;}
