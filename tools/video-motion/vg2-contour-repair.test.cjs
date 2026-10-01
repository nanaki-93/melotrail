'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const repair=require('../../docs/pictures/video/evidence/VG2-13/repair-20261001T172254Z/scripts/repair.cjs');

function fixture(t) {
  const root=fs.mkdtempSync(path.join(fs.realpathSync(os.tmpdir()),'melotrail-vg2-contour-'));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  for(const d of ['inputs','journal','parts','review'])fs.mkdirSync(path.join(root,d));
  fs.copyFileSync(path.join(repair.RUN,'inputs/admission.json'),path.join(root,'inputs/admission.json'));
  fs.copyFileSync(path.join(repair.RUN,'inputs/static-allowance-reconciliation.json'),path.join(root,'inputs/static-allowance-reconciliation.json'));
  return root;
}

test('supplied resting and open hand contours fail the old clipped masks and survive repair',async()=>{
  const r=await repair.inputs();
  for(const [id,n,seed]of [['hand_neutral',45,[816,547]],['hand_wrist_beat',47,[890,518]]]) {
    const hand=repair.repairHand(r.sources[n],r.records[id].crop,seed);
    assert.throws(()=>repair.checkHand(r.images[id],r.sources[n],hand),/contour\/outline/);
    repair.checkHand(hand,r.sources[n],hand);
    const damaged={...hand,data:new Uint8ClampedArray(hand.data)};
    const witness=id==='hand_neutral'?[833,512]:[855,529];
    const i=((witness[1]-hand.crop[1])*hand.width+witness[0]-hand.crop[0])*4;
    assert.equal(hand.skin[i/4],1,'witness must be supplied hand skin');
    assert(hand.data[i+3]>240);assert(r.images[id].data[i+3]<240,'old clipping witness must fail');
    damaged.data[i+3]=0;assert.throws(()=>repair.checkHand(damaged,r.sources[n],hand),/contour\/outline/);
    damaged.data.set(hand.data);damaged.data[i]++;assert.throws(()=>repair.checkHand(damaged,r.sources[n],hand),/source RGB/);
  }
});

test('cuff clears the old orphan hand outline; valid sleeve pixels no longer get cut by the diagonal matte',async()=>{
  const before=await repair.inputs(),after=await repair.repairedParts();
  assert(after.proof.cuff.orphanOutlineBefore>0);assert.equal(after.proof.cuff.orphanOutlineAfter,0);
  assert(after.proof.forearm.restoredSuppliedGreenPixels>0);
  let restored=0;
  for(let i=0;i<before.images.forearm_separate.data.length;i+=4) {
    const b=before.images.forearm_separate.data,a=after.images.forearm_separate.data;
    if(a[i+3]===255&&b[i+3]===0&&a[i+1]>a[i]&&a[i+1]>a[i+2])restored++;
  }
  assert(restored>0,'real clipped source pixels must be restored, not merely a count changed');
});

test('hand and cuff share the corrected wave scale and wrist attachment; hand stays above the cuff',()=>{
  assert.deepEqual(repair.registration.cuffBase,[67,104]);
  assert.equal(repair.registration.handScales.open,repair.registration.cuffScale);
  assert.equal(repair.registration.handOnTop,true);
});

test('the supplied warm dark hand outline survives instead of being mistaken for cuff fabric',async()=>{
  const r=await repair.inputs(),hand=repair.repairHand(r.sources[45],r.records.hand_neutral.crop,[816,547]);
  const x=840,y=512,i=((y-hand.crop[1])*hand.width+x-hand.crop[0])*4;
  const source=(y*r.sources[45].width+x)*4;
  assert.deepEqual([...hand.data.subarray(i,i+3)],[101,30,19]);
  assert.deepEqual([...hand.data.subarray(i,i+3)],[...r.sources[45].data.subarray(source,source+3)]);
  assert.equal(hand.data[i+3],255,'intact supplied dark contour must stay opaque');
  const atlas=await require('../../docs/pictures/video/evidence/VG2-13/continuation-20261001-153813Z/check-preparation.cjs').rgba(fs.readFileSync(path.join(repair.RUN,'parts/contour_atlas_v3.png')));
  const old=((y-hand.crop[1]+1100)*atlas.width+x-hand.crop[0]+400)*4;
  assert.equal(atlas.data[old+3],0,'v3 misclassified this real dark outline pixel');
});

test('the old hard backing tail is replaced by exact cabin pixels while planted contact survives',async()=>{
  const before=await repair.inputs(),after=await repair.repairedParts(),base=after.images.revealed_torso_cabin_backing;
  assert(after.proof.backing.protectedPlantedHandPixels>1000);
  for(const [x,y]of after.proof.backing.cabinWitnesses) {
    const i=(y*1920+x)*4;
    assert.notDeepEqual([...before.images.revealed_torso_cabin_backing.data.subarray(i,i+4)],[...base.data.subarray(i,i+4)]);
  }
  // These actual other-hand/contact pixels must not be erased with the tail.
  for(const [x,y]of [[759,718],[785,724],[772,736]]) {
    const i=(y*1920+x)*4;
    assert.deepEqual([...base.data.subarray(i,i+4)],[...before.images.revealed_torso_cabin_backing.data.subarray(i,i+4)]);
  }
});

test('repair preserves historical counts, rejects raised caps and never admits image/media operations',t=>{
  const root=fixture(t),file=path.join(root,'inputs/admission.json');
  assert.deepEqual(repair.audit(root).aggregate,{imageAttempts:3,derivativeFiles:12,staticPasses:2});
  const original=fs.readFileSync(file),c=JSON.parse(original);c.additionalLimits.derivativeFiles=5;
  fs.writeFileSync(file,JSON.stringify(c));assert.throws(()=>repair.audit(root),/allowance changed/);fs.writeFileSync(file,original);
  const token=repair.acquire(root);
  try {
    for(const kind of ['attempt','imageCall','render','media'])assert.throws(()=>repair.reserve(root,token,kind,'parts/no.png'),/unadmitted operation/);
    for(let n=0;n<4;n++)repair.reserve(root,token,'derivative',`parts/d${n}.png`);
    for(let n=0;n<3;n++)repair.reserve(root,token,'staticPass',`review/s${n}.png`);
    assert.throws(()=>repair.reserve(root,token,'derivative','parts/overflow.png'),/allowance exhausted/);
    assert.throws(()=>repair.reserve(root,token,'staticPass','review/overflow.png'),/allowance exhausted/);
  } finally {repair.release(root,token);}
  assert.deepEqual(repair.audit(root).aggregate,{imageAttempts:3,derivativeFiles:16,staticPasses:5});
});

test('reserved outputs are exclusive, confined and still consumed after reopening',t=>{
  const root=fixture(t),token=repair.acquire(root);
  try {
    assert.throws(()=>repair.acquire(root),/EEXIST/);
    assert.throws(()=>repair.reserve(root,token,'derivative','parts/../outside.png'));
    const receipt=repair.reserve(root,token,'derivative','parts/hand.png');
    assert.equal(receipt.ordinal,13);
    repair.publish(root,token,receipt,Buffer.from('owned fixture'));
    assert.throws(()=>repair.publish(root,token,receipt,Buffer.from('overwrite')),/destination exists/);
    assert.equal(fs.readFileSync(path.join(root,'parts/hand.png'),'utf8'),'owned fixture');
    assert.throws(()=>repair.reserve(root,token,'derivative','parts/HAND.png'),/destination exists|reserved alias|regular expression/);
    const next=repair.reserve(root,token,'derivative','parts/next.png');
    fs.symlinkSync(path.join(root,'inputs/admission.json'),path.join(root,next.destination));
    assert.throws(()=>repair.publish(root,token,next,Buffer.from('unsafe')),/symlink alias/);
    fs.unlinkSync(path.join(root,next.destination));
  } finally {repair.release(root,token);}
  assert.equal(repair.audit(root).aggregate.derivativeFiles,14);
});
