'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const {audit, auditFixed, acquire, release, reserve, createOutput, rgba, pixelsForRegion, verifyProtected, alphaFacts} = require('./check-preparation.cjs');
const owner = __dirname;
const repo = process.env.MELOTRAIL_REPO_ROOT;
function fixture(t) {
  const root = fs.mkdtempSync(path.join(fs.realpathSync(os.tmpdir()), 'melotrail-vg2-13-'));
  t.after(() => fs.rmSync(root, {recursive:true, force:true}));
  for (const dir of ['journal','parts','states','attempts']) fs.mkdirSync(path.join(root, dir));
  const c = JSON.parse(fs.readFileSync(path.join(owner, 'preparation.json')));
  c.owner = root;
  // Fixture is the inherited incomplete checkpoint, not mutable current readiness.
  c.readiness = JSON.parse(fs.readFileSync(path.join(repo, c.predecessor.owner, 'preparation.json'))).readiness;
  fs.writeFileSync(path.join(root, 'preparation.json'), JSON.stringify(c));
  for (const pin of c.predecessor.files.filter(f => /^(journal|parts)\//.test(f.path)))
    fs.copyFileSync(path.join(owner, pin.path), path.join(root, pin.path));
  return root;
}
const counts = {attempt:0, derivative:4, staticPass:0};
test('inherited receipts, bytes and four spent slots survive relocation; incomplete is read-only', t => {
  const root = fixture(t), before = fs.readFileSync(path.join(root, 'preparation.json'));
  assert.deepEqual(audit(root).counts, counts);
  assert.deepEqual(audit(root).missing, ['parts','registration','neutral','usefulExtreme','technicalChecks']);
  assert.throws(() => audit(root, {requireReady:true}), /incomplete preparation/);
  assert.deepEqual(fs.readFileSync(path.join(root, 'preparation.json')), before);
  assert.equal(fs.readdirSync(path.join(root,'journal')).length, 4);
});
test('changed source/predecessor pins and missing or altered inherited records fail closed', t => {
  const root = fixture(t), file = path.join(root,'preparation.json');
  const original = fs.readFileSync(file);
  const mutate = fn => { const c=JSON.parse(original); fn(c); fs.writeFileSync(file,JSON.stringify(c)); assert.throws(() => audit(root)); fs.writeFileSync(file,original); };
  mutate(c => c.sources[0].sha256 = '0'.repeat(64));
  mutate(c => c.sourceInspectionSha256 = '0'.repeat(64));
  mutate(c => c.predecessor.files[0].sha256 = '0'.repeat(64));
  mutate(c => c.predecessor.files = c.predecessor.files.filter(f => !f.path.startsWith('journal/')));
  mutate(c => c.limits.derivativeFiles = 16);
  const pin=JSON.parse(original).predecessor.files.find(f=>f.path.startsWith('journal/'));
  const receipt=path.join(root,pin.path), bytes=fs.readFileSync(receipt);
  fs.writeFileSync(receipt,JSON.stringify({...JSON.parse(bytes),ordinal:99}));
  assert.throws(() => audit(root), /inherited bytes changed/);
  fs.writeFileSync(receipt,bytes);
  const part=path.join(root,'parts/fixed_head_fronds.png');
  fs.rmSync(part); assert.throws(() => audit(root), /missing/);
});
test('one writer, immutable reservations before output and no refund on reopen', t => {
  const root=fixture(t), token=acquire(root);
  assert.throws(() => acquire(root), /EEXIST/);
  const first=reserve(root,token,'attempt','hand_neutral');
  assert.equal(first.receipt.ordinal,1);
  assert.deepEqual(audit(root).counts,{...counts,attempt:1});
  assert.throws(() => reserve(root,'wrong','attempt','hand_neutral'),/writer lock required/);
  release(root,token);
  const next=acquire(root);
  const second=reserve(root,next,'attempt','hand_neutral');
  assert.equal(second.receipt.ordinal,2);
  assert.throws(() => reserve(root,next,'attempt','hand_neutral'),/target attempts exhausted/);
  release(root,next);
  assert.deepEqual(audit(root).counts,{...counts,attempt:2});
});
test('derivative and static limits count historical reservations even after writer restart', t => {
  const root=fixture(t); let token=acquire(root);
  for(let i=0;i<3;i++) reserve(root,token,'staticPass',null,`states/state-${i}.png`);
  for(let i=0;i<4;i++) reserve(root,token,'derivative','forearm_separate',`parts/new-${i}.png`);
  release(root,token); token=acquire(root);
  for(let i=4;i<8;i++) reserve(root,token,'derivative','forearm_separate',`parts/new-${i}.png`);
  assert.throws(()=>reserve(root,token,'derivative','forearm_separate','parts/overflow.png'),/allowance exhausted/);
  assert.throws(()=>reserve(root,token,'staticPass',null,'states/overflow.png'),/allowance exhausted/);
  release(root,token);
  assert.deepEqual(audit(root).counts,{attempt:0,derivative:12,staticPass:3});
});
test('aliases, collisions and occupied files cannot be published or silently counted', t => {
  const root=fixture(t), token=acquire(root);
  for(const dest of ['parts/../escape.png','/tmp/out.png','parts/../../escape.png'])
    assert.throws(()=>reserve(root,token,'derivative','hand_neutral',dest));
  const r=reserve(root,token,'derivative','hand_neutral','parts/hand.png');
  assert.equal(r.receipt.ordinal,5);
  assert.throws(()=>reserve(root,token,'derivative','hand_neutral','parts/hand.png'),/reserved collision/);
  const file=path.join(root,r.receipt.destination);
  fs.writeFileSync(file,'occupied');
  assert.throws(()=>createOutput(root,token,r.receipt.id,Buffer.from('new')),/destination exists|EEXIST/);
  assert.equal(fs.readFileSync(file,'utf8'),'occupied');
  fs.unlinkSync(file); fs.symlinkSync(path.join(root,'preparation.json'),file);
  assert.throws(()=>createOutput(root,token,r.receipt.id,Buffer.from('new')),/symlink alias/);
  fs.unlinkSync(file);
  const extra=path.join(root,'parts/extra.png'); fs.writeFileSync(extra,'extra');
  assert.throws(()=>audit(root),/unreserved output/); fs.unlinkSync(extra);
  fs.renameSync(path.join(root,'parts'),path.join(root,'real-parts'));
  fs.symlinkSync(path.join(root,'real-parts'),path.join(root,'parts'));
  assert.throws(()=>audit(root),/symlink alias/);
  fs.unlinkSync(path.join(root,'parts'));fs.renameSync(path.join(root,'real-parts'),path.join(root,'parts'));
  createOutput(root,token,r.receipt.id,Buffer.from('new'));
  assert.throws(()=>createOutput(root,token,r.receipt.id,Buffer.from('again')),/destination exists/);
  release(root,token);
});
test('case-variant output cannot impersonate an exact-path reservation', t => {
  const root = fixture(t), token = acquire(root);
  const r = reserve(root, token, 'derivative', 'hand_neutral', 'parts/hand.png');
  const caseVariant = path.join(root, 'parts/HAND.png');
  fs.writeFileSync(caseVariant, 'unreserved');
  assert.throws(() => audit(root), /unreserved output: parts\/HAND\.png/);
  assert.throws(() => reserve(root, token, 'derivative', 'hand_neutral', 'parts/next.png'), /unreserved output/);
  assert.throws(() => createOutput(root, token, r.receipt.id, Buffer.from('owned')), /unreserved output/);
  assert.equal(fs.readFileSync(caseVariant, 'utf8'), 'unreserved');
  // On case-sensitive hosts the exact reserved file can also coexist with the alias.
  if (!fs.existsSync(path.join(root, r.receipt.destination))) {
    fs.writeFileSync(path.join(root, r.receipt.destination), 'owned');
    assert.throws(() => audit(root), /unreserved output: parts\/HAND\.png/);
  }
  release(root, token);
});
test('changed donor and protected-pixel edits reject exact RGBA witnesses', () => {
  const source = {width:4, height:3, data:new Uint8ClampedArray(4*3*4)};
  source.data.set([45,90,135,255], (1*4+1)*4);
  const crop = [0,0,4,3], regions = [[1,1,2,2]];
  const expected = pixelsForRegion(source, crop, regions);
  const part = {width:4, height:3, data:new Uint8ClampedArray(expected)};
  const spec = {id:'synthetic', crop, regions, width:4, height:3,
    decodedAlpha:alphaFacts(expected,4,3),
    protectedRgbaSha256:createHash('sha256').update(Buffer.from(expected)).digest('hex')};
  verifyProtected(source, part, spec);
  const changed = {...source, data:new Uint8ClampedArray(source.data)};
  changed.data[(1*4+1)*4] = 46;
  assert.throws(() => verifyProtected(changed, part, spec), /protected RGBA mismatch/);
  part.data[(1*4+1)*4+2] = 136;
  assert.throws(() => verifyProtected(source, part, spec), /protected RGBA mismatch/);
  part.data.set(expected);
  part.data[3] = 255; // Unapproved backing outside the protected region.
  assert.throws(() => verifyProtected(source, part, spec), /unprotected pixel/);
});
test('interrupted lock and pending write block new spending', t => {
  const root=fixture(t), token=acquire(root);
  reserve(root,token,'attempt','upper_sleeve_separate');
  assert.equal(audit(root).lockPresent,true);
  assert.throws(()=>acquire(root),/EEXIST/);
  fs.writeFileSync(path.join(root,'preparation.pending'),'interrupted');
  assert.throws(()=>audit(root),/pending preparation write/);
  assert.throws(()=>reserve(root,token,'attempt','hand_neutral'),/pending preparation write/);
  fs.unlinkSync(path.join(root,'preparation.pending'));
  release(root,token);
  assert.equal(audit(root).counts.attempt,1);
});
test('actual inherited fixed pixels and alpha remain exactly pinned', async t => {
  const root = fixture(t);
  assert.deepEqual(audit(root).counts, counts);
  await auditFixed(root);
  assert.deepEqual(audit(root).missing,['parts','registration','neutral','usefulExtreme','technicalChecks']);
});
test('retained cropped-head derivative is rejected; face/chin and cheek boundary are protected', async t => {
  const root = fixture(t);
  const c = JSON.parse(fs.readFileSync(path.join(root, 'preparation.json')));
  const spec = c.fixedFoundation.parts[0];
  const source = await rgba(fs.readFileSync(path.join(repo, spec.source)));
  const part = await rgba(fs.readFileSync(path.join(root, spec.path)));
  const comparisons = await Promise.all([46,47].map(async n => {
    const pin = c.sources.find(s => s.path.includes(`/train-actions/${n}-matching-`));
    return rgba(fs.readFileSync(path.join(repo, pin.path)));
  }));
  const at = (x,y) => ((y-spec.crop[1])*part.width+x-spec.crop[0])*4;
  for (const [x,y] of [[690,535],[710,555],[711,585],[712,600]]) {
    const i = at(x,y), original = (y*source.width+x)*4;
    assert(part.data[i+3]>0, `face missing at ${x},${y}`);
    assert.deepEqual([...part.data.slice(i,i+4)], [...source.data.slice(original,original+4)]);
  }
  assert.equal(part.data[at(810,520)+3], 0, 'changing hand must remain unclaimed');
  const oldPath = path.join(root, c.fixedFoundation.supersededHead.path);
  const truncated = await rgba(fs.readFileSync(oldPath));
  assert.equal(createHash('sha256').update(fs.readFileSync(oldPath)).digest('hex'), c.fixedFoundation.supersededHead.sha256);
  assert.throws(() => verifyProtected(source, truncated, spec, comparisons), /Expected values|width|height/);
  const oldCutoff = {...part, data:new Uint8ClampedArray(part.data)};
  oldCutoff.data.fill(0, at(711,585), at(711,585)+4);
  assert.throws(() => verifyProtected(source, oldCutoff, spec, comparisons), /face\/chin cropped/);
  const changedSource = {...source, data:new Uint8ClampedArray(source.data)};
  changedSource.data[(585*source.width+711)*4] ^= 1;
  assert.throws(() => verifyProtected(changedSource, part, spec, comparisons), /face\/chin cropped|protected RGBA mismatch/);
  verifyProtected(source, part, spec, comparisons);
});
