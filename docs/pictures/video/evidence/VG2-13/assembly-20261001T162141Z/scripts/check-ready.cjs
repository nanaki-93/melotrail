'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {runtime,RUN,OWNER,sha}=require('./assemble.cjs');
const {validateStatic}=require('./check-kit.cjs');
function validateReadiness(preparation,pins,finalPin){
 const ready=preparation.readiness;assert.equal(ready.humanAppearance,'pending VG2-14');
 for(const field of ['parts','registration','neutral','usefulExtreme','technicalChecks']){
  const ref=ready[field];assert(ref&&typeof ref==='object',`missing typed ${field}`);
  const expected=field==='technicalChecks'?finalPin:pins.find(p=>p.path===ref.record);
  assert(expected,`unbound ${field}`);assert.equal(ref.record,expected.path);assert.equal(ref.sha256,expected.sha256,`stale ${field}`);
 }
 assert.equal(ready.neutral.state,'neutral');assert.deepEqual(ready.usefulExtreme.states,['lift','wrist_in','wrist_out']);
}
async function main(){
 assert.deepEqual(process.argv.slice(2),['--check']);
 const {owner}=await runtime(),repo=process.env.MELOTRAIL_REPO_ROOT;
 const file=RUN+'/checks/kit-freeze-final.json',bytes=fs.readFileSync(file),freeze=JSON.parse(bytes);
 assert.equal(freeze.approval,'PENDING VG2-14');assert.equal(process.version,freeze.runtime.node);
 assert.equal(sha(fs.readFileSync(freeze.runtime.executable)),freeze.runtime.sha256,'Node executable changed');
 for(const p of freeze.pins)assert.equal(sha(fs.readFileSync(owner.safeFile(repo,p.path))),p.sha256,`dependency changed: ${p.path}`);
 const preparation=JSON.parse(fs.readFileSync(OWNER+'/preparation.json'));
 validateReadiness(preparation,freeze.pins,{path:path.relative(repo,file),sha256:sha(bytes)});
 const state=owner.audit(OWNER,{requireReady:true});assert.deepEqual(state.counts,freeze.consumption);assert.equal(state.lockPresent,false);
 const s=JSON.parse(fs.readFileSync(RUN+'/checks/static-v2.json'));validateStatic(s);
 console.log(JSON.stringify({technicalPreparation:'PASS',appearance:'PENDING VG2-14',consumption:state.counts,reviewSha256:s.sha256,scope:'Four static states only; no rig, moving or release approval.'},null,2));
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1});
module.exports={validateReadiness};
