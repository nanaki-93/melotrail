'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-15/rig-20261002T010147Z');
const s=require(owner+'/scripts/check-support.cjs');
const read=p=>JSON.parse(fs.readFileSync(owner+'/'+p));
const spec=read('inputs/rig-v2.json'),proof=read('checks/check-geometry-v3.json'),boundaries=read('inputs/boundaries.json');
let images;test.before(async()=>{images=await s.load(spec);});
test('all 150 reopened frames retain complete joint regions, sleeve corridors, UVs and neutral return',()=>{
  assert.deepEqual(proof.frames,read('checks/build-geometry-v3.json').frames);
  const result=s.check(spec,images,proof,boundaries);assert.equal(result.frames,150);assert.equal(result.exposedOffCanvas,0);
});
test('actual initial cuff seam fails the unchanged full attachment test',()=>{
  const before=read('checks/check-geometry.json');
  assert.throws(()=>s.checkFrame(read('inputs/rig.json'),images,before.frames[0]),/attachment\/limb corridor holes/);
  assert.equal(read('checks/initial-support-failure.json').failures.length,120);
});
test('moving cuff away from forearm is rejected even when its wrist-entry pixels remain intact',()=>{
  const frame=structuredClone(proof.frames[60]);
  for(const id of ['open-front','open-overlap','wrist-support'])for(const p of frame.parts[id].quad)p[0]+=30;
  assert.throws(()=>s.checkFrame(spec,images,frame),/part-to-joint misregistration/);
});
test('clipping a sleeve cross-section fails rather than passing pivot-centre witnesses',()=>{
  const bad={...images,forearm_separate:{...images.forearm_separate,data:new Uint8ClampedArray(images.forearm_separate.data)}};
  const im=bad.forearm_separate;
  for(let y=65;y<80;y++)for(let x=0;x<im.width;x++)im.data[(y*im.width+x)*4+3]=0;
  assert.throws(()=>s.checkFrame(spec,bad,proof.frames[60]),/attachment\/limb corridor holes/);
});
test('flipped mesh and moving UVs fail',()=>{
  const flipped=structuredClone(proof.frames[60]);flipped.parts.forearm.quad.reverse();
  assert.throws(()=>s.alphaAt(images.forearm_separate,flipped.parts.forearm.quad,spec.parts.forearm.crop,flipped.anchors.Elbow),/inverted/);
  const uv=structuredClone(proof);uv.frames[1].parts.upper.uv[0][0]+=.02;
  assert.throws(()=>s.check(spec,images,uv,boundaries),/UVs changed/);
});
test('an altered neutral return cannot pass unchanged geometric checks',()=>{
  const changed=structuredClone(proof);changed.frames[149].parts.background.quad[0][0]+=.2;
  assert.throws(()=>s.check(spec,images,changed,boundaries),/return differs/);
});
