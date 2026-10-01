'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {runtime,RUN,OWNER,sha,states,distance}=require('./assemble.cjs');
function validateStatic(s){
 assert.equal(s.variant,'v2');assert.equal(s.technicalPass,true);
 assert.deepEqual(s.checks.map(c=>c.state),states.map(s=>s.id));
 for(const c of s.checks){
  assert.equal(c.outsideChanges,0);assert.equal(c.nonOpaque,0);
  assert.equal(c.seam.length,4);for(const seam of c.seam){assert.equal(seam.holes,0);assert(seam.minAlpha>=240);}
  assert(Math.abs(distance(c.geometry.shoulder,c.geometry.elbow)-c.geometry.upperLength)<1e-9);
  assert(Math.abs(distance(c.geometry.elbow,c.geometry.wrist)-c.geometry.forearmLength)<1e-9);
 }
 assert.equal(s.appearance,'UNREVIEWED','static technical proof must not award approval');
}
async function main(){
 assert.deepEqual(process.argv.slice(2),['--check']);
 const {owner}=await runtime(),repo=process.env.MELOTRAIL_REPO_ROOT;
 const freeze=JSON.parse(fs.readFileSync(RUN+'/checks/kit-freeze.json'));
 for(const pin of freeze.pins)assert.equal(sha(fs.readFileSync(owner.safeFile(repo,pin.path))),pin.sha256,`dependency changed: ${pin.path}`);
 const state=owner.audit(OWNER);assert.deepEqual(state.counts,freeze.consumption);assert.equal(state.lockPresent,false);
 const s=JSON.parse(fs.readFileSync(RUN+'/checks/static-v2.json'));validateStatic(s);
 const proof=JSON.parse(fs.readFileSync(RUN+'/checks/correction-atlas.json'));
 for(const d of Object.values(proof.diagnostics)){assert(d.skinPixelsBefore>0);assert.equal(d.skinPixelsAfter,0);}
 assert.equal(freeze.approval,'PENDING VG2-14');
 console.log(JSON.stringify({integrity:'PASS',staticStates:4,consumption:state.counts,reviewSha256:s.sha256,appearance:'PENDING VG2-14',limitations:s.limitations},null,2));
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1});
module.exports={validateStatic};
