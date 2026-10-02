'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const support=require('./check-support.cjs'),{RUN,REPO}=support;
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const read=f=>JSON.parse(fs.readFileSync(path.join(RUN,f)));
const pins=()=>{
  const spec=read('inputs/rig-v2.json'),files=new Set([...Object.values(spec.textures),...spec.dependencies,spec.boundaries,spec.neutralReference].map(p=>p.path));
  for(const dir of ['inputs','scripts','textures','rig'])for(const f of fs.readdirSync(path.join(RUN,dir))){const full=path.join(RUN,dir,f);assert(!fs.lstatSync(full).isSymbolicLink());if(fs.statSync(full).isFile())files.add(path.relative(REPO,full));}
  for(const name of ['build-geometry-v3.json','check-geometry-v3.json','support-final.json','initial-support-failure.json','evaluation-result.json','evaluation-failure-v2.json','evaluation-result-v3.json'])files.add(path.relative(REPO,path.join(RUN,'checks',name)));
  for(const name of ['vg2-rig-motion.test.cjs','vg2-rig-support.test.cjs'])files.add('tools/video-motion/'+name);
  return [...files].sort().map(p=>({path:p,sha256:sha(fs.readFileSync(path.join(REPO,p)))}));
};
async function check(){
  const freeze=read(fs.existsSync(RUN+'/checks/handoff-final.json')?'checks/handoff-final.json':'checks/handoff.json');
  for(const pin of freeze.pins)assert.equal(sha(fs.readFileSync(path.join(REPO,pin.path))),pin.sha256,'changed frozen input '+pin.path);
  const spec=read('inputs/rig-v2.json'),a=read('checks/build-geometry-v3.json'),b=read('checks/check-geometry-v3.json');
  assert.deepEqual(a.frames,b.frames);assert.equal(a.sceneSha256,sha(fs.readFileSync(RUN+'/rig/tabi-wave-v3.blend')));
  support.check(spec,await support.load(spec),b,read('inputs/boundaries.json'));
  return {status:'RIG_HANDOFF_INTEGRITY_PASS',pins:freeze.pins.length,frames:150,renderedFrames:0};
}
(async()=>{
  assert(['--freeze','--check'].includes(process.argv[2]));
  if(process.argv[2]==='--freeze')fs.writeFileSync(RUN+'/checks/handoff.json',JSON.stringify({scope:'Standalone data-only rig; no rendered/moving acceptance',pins:pins(),selectedScene:'rig/tabi-wave-v3.blend',limitations:read('inputs/rig-v2.json').limitations},null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify(await check()));
})().catch(e=>{console.error(e);process.exitCode=1;});
