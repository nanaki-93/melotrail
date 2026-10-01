'use strict';
// Immutable standalone handoff; no assembly, rig, media or artistic decision.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const repair=require('./repair.cjs');
const REPO=path.resolve(repair.RUN,'../../../../../..');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const pin=file=>({path:path.relative(REPO,file),sha256:sha(fs.readFileSync(file))});
function validate(record) {
  assert.equal(record.variant,'v5');assert.equal(record.appearance,'UNREVIEWED');assert.equal(record.technicalPass,true);
  assert.deepEqual(record.checks.map(c=>c.state),['neutral','lift','wrist_in','wrist_out']);
  for(const c of record.checks) {
    assert.equal(c.outsideChanges,0);assert.equal(c.nonOpaque,0);assert.equal(c.seams.length,4);assert(c.seams.every(s=>s.holes===0));
  }
  for(const h of Object.values(record.proof.hands)) {
    assert(h.oldFails.includes('clippedSkin'));assert.deepEqual(h.new,{clippedSkin:0,clippedOutline:0,changedOpaqueRgb:0,foreignSkin:0,gold:0});
  }
  assert(record.proof.cuff.orphanOutlineBefore>0);assert.equal(record.proof.cuff.orphanOutlineAfter,0);
  assert(record.proof.forearm.restoredSuppliedGreenPixels>0);
  for(const p of [record.part,record.review])assert.equal(sha(fs.readFileSync(path.join(REPO,p.path))),p.sha256,'published candidate changed');
}
function files(root) {
  return fs.readdirSync(root,{withFileTypes:true}).flatMap(e=> {
    const file=path.join(root,e.name);assert(!e.isSymbolicLink(),'handoff symlink');
    return e.isDirectory()?files(file):e.isFile()?[file]:[];
  });
}
function freeze() {
  const counts=repair.audit();assert.equal(counts.lockPresent,false);
  assert.deepEqual(counts.aggregate,{imageAttempts:3,derivativeFiles:15,staticPasses:5});
  const record=JSON.parse(fs.readFileSync(path.join(repair.RUN,'checks/static-v5.json')));validate(record);
  const owned=files(repair.RUN).filter(f=>!f.endsWith('.log')&&!f.endsWith('checks/kit-freeze.json'));
  const pins=owned.map(pin).concat([pin(path.join(REPO,'tools/video-motion/vg2-contour-repair.test.cjs'))]);
  const c={schema:'standalone-static-repair-handoff-1',candidate:'v5',appearance:'PENDING renewed VG2-14',counts,review:record.review,part:record.part,pins,
    limitations:['Four static states only; no Blender rig, all-frame support, hand-opening transition or moving approval.',
      'Changed neutral/cuff/hand placement and inner sleeve/backing junction require explicit review against unchanged reference48.',
      'V3 and v4, their helper/test snapshots and every historical candidate remain retained. All five aggregate static passes are consumed.',
      'Historical build inputs remain hash-pinned local dependencies; no clean-checkout or app-delivery claim.']};
  fs.writeFileSync(path.join(repair.RUN,'checks/kit-freeze.json'),JSON.stringify(c,null,2)+'\n',{flag:'wx'});
}
function check() {
  const c=JSON.parse(fs.readFileSync(path.join(repair.RUN,'checks/kit-freeze.json')));
  assert.equal(c.schema,'standalone-static-repair-handoff-1');assert.equal(c.appearance,'PENDING renewed VG2-14');
  for(const p of c.pins)assert.equal(sha(fs.readFileSync(path.join(REPO,p.path))),p.sha256,`handoff pin changed: ${p.path}`);
  assert.deepEqual(repair.audit(),c.counts);validate(JSON.parse(fs.readFileSync(path.join(repair.RUN,'checks/static-v5.json'))));
  console.log(JSON.stringify({technicalStaticRepair:'PASS',appearance:c.appearance,review:c.review,consumption:c.counts.aggregate,limitations:c.limitations},null,2));
}
if(require.main===module)try {
  assert.equal(process.argv.length,3);if(process.argv[2]==='--freeze')freeze();else if(process.argv[2]==='--check')check();else throw Error('Use --freeze or --check');
}catch(e){console.error(e);process.exitCode=1;}
module.exports={validate};
