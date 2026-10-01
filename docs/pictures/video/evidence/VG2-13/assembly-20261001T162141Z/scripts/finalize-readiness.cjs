'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {runtime,RUN,OWNER,sha}=require('./assemble.cjs');
const {validateStatic}=require('./check-kit.cjs');
async function main(){
 assert.deepEqual(process.argv.slice(2),['--finalize']);
 const {owner}=await runtime(),repo=process.env.MELOTRAIL_REPO_ROOT;
 const prior=JSON.parse(fs.readFileSync(RUN+'/checks/kit-freeze.json'));
 const changed=new Set(['tools/video-motion/vg2-static-parts.test.cjs',path.relative(repo,OWNER+'/preparation.test.cjs')]);
 for(const p of prior.pins)if(!changed.has(p.path))assert.equal(sha(fs.readFileSync(owner.safeFile(repo,p.path))),p.sha256,`previous kit changed: ${p.path}`);
 validateStatic(JSON.parse(fs.readFileSync(RUN+'/checks/static-v2.json')));
 const file=RUN+'/checks/kit-freeze-final.json';
 const pins=prior.pins.map(p=>({...p,sha256:sha(fs.readFileSync(owner.safeFile(repo,p.path)))}));
 for(const name of ['check-ready.cjs','finalize-readiness.cjs']){const file=RUN+'/scripts/'+name;pins.push({path:path.relative(repo,file),sha256:sha(fs.readFileSync(file))});}
 const freeze={...prior,predecessorFreezeSha256:sha(fs.readFileSync(RUN+'/checks/kit-freeze.json')),pins};
 const bytes=Buffer.from(JSON.stringify(freeze,null,2)+'\n');
 fs.writeFileSync(file,bytes,{flag:'wx'});
 const ref=file=>({record:path.relative(repo,file),sha256:sha(fs.readFileSync(file))});
 const token=owner.acquire(OWNER);
 try{
  const c=JSON.parse(fs.readFileSync(OWNER+'/preparation.json'));
  for(const k of ['parts','registration','neutral','usefulExtreme','technicalChecks'])assert.equal(c.readiness[k],null,'already finalized; no overwrite');
  c.readiness={parts:ref(RUN+'/checks/parts.json'),registration:ref(RUN+'/checks/static-v2.json'),neutral:{...ref(OWNER+'/states/assembled_review_v2.png'),state:'neutral'},usefulExtreme:{...ref(OWNER+'/states/assembled_review_v2.png'),states:['lift','wrist_in','wrist_out']},technicalChecks:ref(file),humanAppearance:'pending VG2-14'};
  const pending=OWNER+'/preparation.pending',fd=fs.openSync(pending,'wx');
  try{fs.writeFileSync(fd,JSON.stringify(c,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
  fs.renameSync(pending,OWNER+'/preparation.json');
  const dir=fs.openSync(OWNER,'r');try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
 }finally{owner.release(OWNER,token);}
 console.log('Technical references finalized; human appearance remains pending. No allowance consumed.');
}
main().catch(e=>{console.error(e);process.exitCode=1});
