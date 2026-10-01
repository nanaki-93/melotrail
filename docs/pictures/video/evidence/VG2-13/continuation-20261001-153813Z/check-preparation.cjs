'use strict';
// Standalone proof bookkeeping. No provider, media, or production-project dispatch.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const repo = process.env.MELOTRAIL_REPO_ROOT;
assert(repo && path.isAbsolute(repo) && path.resolve(repo) === repo && fs.realpathSync(repo) === repo &&
  fs.existsSync(path.join(repo, 'settings.gradle.kts')) && fs.existsSync(path.join(repo, 'tools/video-motion/node_modules/@napi-rs/canvas')),
  'verified absolute MELOTRAIL_REPO_ROOT required');
const {loadImage, createCanvas} = require(path.join(repo, 'tools/video-motion/node_modules/@napi-rs/canvas'));
const PREDECESSOR = 'build/vg2-13-coherent-wave-20261001-b1';
const PREPARATION_SHA = 'f9d51b127a9960b134578f92417c39efbf36099fe1aa800a7a116cee139194d6';
const PREDECESSOR_FILES_SHA = '70a090fcbe20f20b35658d78abb7cfc6ec40ea2ebd9d6c176d1ad9654af798c7';
const SUCCESSOR = 'docs/pictures/video/evidence/VG2-13/continuation-20261001-153813Z';
const IDS = Object.freeze(['upper_sleeve_separate', 'forearm_separate', 'cuff_wrist_overlap', 'hand_neutral', 'hand_wrist_beat', 'hidden_shoulder_elbow_overlap', 'revealed_torso_cabin_backing', 'rig_state_foreground_occlusion']);
const LIMITS = Object.freeze({attemptsPerTarget: 2, attemptsTotal: 16, derivativeFiles: 12, staticCandidatePasses: 3});
const FIXED_IDS = Object.freeze(['fixed_head_fronds','unaffected_torso_islands','lower_contact']);
// Measured on 45/46/47: the face tapers to the chin at y=606; the hand begins
// at the right cheek. Keep the selection stable, not adjustable after comparison.
const HEAD_POLYGONS = Object.freeze([
  [[600,475],[867,475],[851,490],[834,509],[816,519],[805,530],[786,541],[759,551],[735,566],[721,589],[712,606],[703,589],[690,571],[674,553],[647,542],[616,528],[600,515]],
  [[340,475],[620,475],[620,519],[602,525],[580,525],[545,527],[510,525],[480,520],[455,531],[418,534],[390,522],[360,516]],
  [[850,475],[900,475],[900,505],[850,514]],
]);
const FACE_WITNESSES = Object.freeze([[690,535],[710,555],[711,585],[712,600]]);
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const existsEntry = file => { try { fs.lstatSync(file); return true; } catch (e) { if (e.code === 'ENOENT') return false; throw e; } };
function components(file, root, allowMissingLeaf = false) {
  assert(path.isAbsolute(root) && path.resolve(root) === root, 'absolute root required');
  const relative = path.relative(root, file);
  assert(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'outside owner');
  assert(!relative.split(path.sep).some(c => c === '.' || c === '..' || !c), 'traversal');
  // Reject textual traversal too, even when path.resolve would normalize it inside root.
  let cursor = path.parse(root).root;
  for (const part of path.resolve(root).slice(cursor.length).split(path.sep).concat(relative.split(path.sep))) {
    cursor = path.join(cursor, part);
    if (!existsEntry(cursor)) {
      assert(allowMissingLeaf && cursor === file, `missing ancestor: ${cursor}`);
      return false;
    }
    const stat = fs.lstatSync(cursor);
    assert(!stat.isSymbolicLink(), `symlink alias: ${cursor}`);
    if (cursor !== file) assert(stat.isDirectory(), `non-directory ancestor: ${cursor}`);
    else assert(stat.isFile(), `not a regular file: ${cursor}`);
  }
  return true;
}
function safeFile(root, rel, absent = false) {
  assert(typeof rel === 'string' && rel.length && !path.isAbsolute(rel) && !rel.split(/[\\/]/).some(c => !c || c === '.' || c === '..'), 'unsafe relative path');
  const file = path.join(root, rel);
  const exists = components(file, root, absent);
  if (absent) assert(!exists, `destination exists: ${rel}`);
  else assert(exists, `missing file: ${rel}`);
  return file;
}
function config(root) {
  const c = JSON.parse(fs.readFileSync(safeFile(root, 'preparation.json')));
  assert.equal(c.schema, 1);
  assert.equal(path.resolve(repo, c.owner), root, 'owner identity changed');
  const original = JSON.parse(fs.readFileSync(safeFile(repo, `${PREDECESSOR}/preparation.json`)));
  assert.equal(sha(fs.readFileSync(safeFile(repo, `${PREDECESSOR}/preparation.json`))), PREPARATION_SHA, 'predecessor preparation changed');
  assert.equal(original.owner, PREDECESSOR);
  assert.equal(c.predecessor?.owner, PREDECESSOR, 'predecessor identity changed');
  if (root === __dirname) assert.equal(c.owner, SUCCESSOR, 'successor identity changed');
  else assert(process.env.NODE_TEST_CONTEXT && root.startsWith(fs.realpathSync(os.tmpdir()) + path.sep), 'successor identity changed');
  for (const field of ['sourceOwner','sourceInspectionSha256','sources','targets','limits','permission','referenceNeutral','fixedFoundation'])
    assert.deepEqual(c[field], original[field], `predecessor field changed: ${field}`);
  assert(!existsEntry(path.join(repo, PREDECESSOR, 'writer.lock')) && !existsEntry(path.join(repo, PREDECESSOR, 'preparation.pending')),
    'predecessor unresolved writer/pending write');
  const historical = ['preparation.json','check-preparation.cjs','preparation.test.cjs',
    ...fs.readdirSync(path.join(repo, PREDECESSOR, 'journal')).map(n => `journal/${n}`),
    ...fs.readdirSync(path.join(repo, PREDECESSOR, 'parts')).map(n => `parts/${n}`)].sort();
  assert.equal(sha(JSON.stringify(c.predecessor.files)), PREDECESSOR_FILES_SHA, 'predecessor manifest changed');
  assert.deepEqual(c.predecessor.files.map(f => f.path).sort(), historical, 'predecessor files changed');
  assert.deepEqual(fs.readdirSync(path.join(repo, PREDECESSOR, 'attempts')), [], 'predecessor attempt output unresolved');
  assert.deepEqual(fs.readdirSync(path.join(repo, PREDECESSOR, 'states')), [], 'predecessor static output unresolved');
  for (const pin of c.predecessor.files) {
    assert.match(pin.sha256, /^[a-f0-9]{64}$/);
    assert.equal(sha(fs.readFileSync(safeFile(repo, `${PREDECESSOR}/${pin.path}`))), pin.sha256, `predecessor changed: ${pin.path}`);
    if (pin.path.startsWith('journal/') || pin.path.startsWith('parts/'))
      assert.equal(sha(fs.readFileSync(safeFile(root, pin.path))), pin.sha256, `inherited bytes changed: ${pin.path}`);
  }
  const inherited = c.predecessor.files.filter(f => f.path.startsWith('journal/')).map(f =>
    JSON.parse(fs.readFileSync(safeFile(root, f.path))));
  assert.equal(inherited.length, 4, 'historical allowance changed');
  assert.deepEqual(inherited.map(r => r.ordinal).sort(), [1,2,3,4]);
  assert(inherited.every(r => r.kind === 'derivative'), 'historical reservation changed');
  assert.deepEqual(c.targets, IDS);
  assert.deepEqual(c.limits, LIMITS);
  assert.match(c.permission, /local proof derivatives only/);
  assert.equal(c.sourceOwner, 'build/vg2-12-pool-20261001-a1/inspection.json');
  const bytes = fs.readFileSync(safeFile(repo, c.sourceOwner));
  assert.equal(sha(bytes), c.sourceInspectionSha256, 'inspection pin changed');
  const inspection = JSON.parse(bytes);
  assert.equal(c.referenceNeutral, inspection.referenceNeutral);
  assert.deepEqual(c.sources, inspection.sources.map(({path, sha256, width, height, alpha}) => ({path, sha256, width, height, alpha})));
  assert.equal(c.sources.length, 31);
  for (const source of c.sources) assert.equal(sha(fs.readFileSync(safeFile(repo, source.path))), source.sha256, `source changed: ${source.path}`);
  return c;
}
function audit(root, opts = {}) {
  const c = config(root);
  // Unlike reservations, audit is read-only: it neither repairs receipts nor releases a lock.
  const dir = path.join(root, 'journal');
  components(path.join(dir, 'unused'), root, true);
  const entries = fs.readdirSync(dir).sort();
  const reservedExact = new Set(); const reservedFolded = new Set();
  const counts = {attempt: 0, derivative: 0, staticPass: 0};
  const ordinals = {attempt: [], derivative: [], staticPass: []};
  const perTarget = Object.fromEntries(IDS.map(id => [id, 0]));
  for (const name of entries) {
    assert.match(name, /^[a-f0-9]{32}\.json$/);
    const r = JSON.parse(fs.readFileSync(safeFile(root, `journal/${name}`)));
    assert.equal(r.id + '.json', name);
    assert(['attempt','derivative','staticPass'].includes(r.kind));
    assert(Number.isSafeInteger(r.ordinal) && r.ordinal > 0);
    if (r.kind === 'attempt') assert(IDS.includes(r.target));
    if (r.kind === 'derivative') assert(IDS.includes(r.target) || FIXED_IDS.includes(r.target));
    if (r.kind === 'attempt') {
      perTarget[r.target]++;
      assert.equal(r.destination, `attempts/${r.target}-${r.ordinal}.png`);
    } else if (r.kind === 'derivative') assert.match(r.destination, /^parts\/[a-z0-9_-]+\.png$/);
    else assert.match(r.destination, /^states\/[a-z0-9_-]+\.png$/);
    counts[r.kind]++;
    ordinals[r.kind].push(r.ordinal);
    const destination = safeFile(root, r.destination, falseIfAbsent(root, r.destination));
    const key = destination.normalize('NFC').toLowerCase();
    assert(!reservedFolded.has(key), 'destination collision');
    reservedFolded.add(key);
    reservedExact.add(r.destination);
  }
  assert(counts.derivative >= 4, 'historical derivatives refunded');
  assert(!existsEntry(path.join(root, 'preparation.pending')), 'pending preparation write requires reconciliation');
  // Every owned output must have a prior receipt, including failed/interrupted attempts.
  // lstat (via safeFile) rejects symlinks, subdirectories and other non-regular entries.
  for (const dir of ['attempts', 'parts', 'states']) {
    components(path.join(root, dir, 'unused'), root, true);
    for (const name of fs.readdirSync(path.join(root, dir))) {
      safeFile(root, `${dir}/${name}`);
      assert(reservedExact.has(`${dir}/${name}`), `unreserved output: ${dir}/${name}`);
    }
  }
  for (const kind of Object.keys(counts)) assert.deepEqual(ordinals[kind].sort((a,b) => a-b), Array.from({length:counts[kind]}, (_,i) => i+1), `non-contiguous ${kind} reservations`);
  assert(counts.attempt <= LIMITS.attemptsTotal && counts.derivative <= LIMITS.derivativeFiles && counts.staticPass <= LIMITS.staticCandidatePasses);
  for (const n of Object.values(perTarget)) assert(n <= LIMITS.attemptsPerTarget);
  assert(c.fixedFoundation || !entries.some(n => {
    const r=JSON.parse(fs.readFileSync(safeFile(root, `journal/${n}`)));
    return r.kind === 'derivative' && FIXED_IDS.includes(r.target);
  }), 'fixed output reserved but foundation record missing');
  if (c.fixedFoundation) {
    assert.equal(c.fixedFoundation.referenceRole, 'opaque appearance reference only; never a body layer');
    assert.equal(c.fixedFoundation.cabinStatus, 'opaque candidate; revealed areas not certified clean');
    assert.equal(c.fixedFoundation.torsoStatus, 'selected unaffected islands only; obscured torso unresolved');
    assert.equal(c.fixedFoundation.unresolved.length, 4, 'missing fixed-region limitations');
    assert(c.fixedFoundation.unresolved.every(x => typeof x === 'string' && x.length > 30));
    assert.deepEqual(c.fixedFoundation.raster, {width:1920, height:1080, origin:'top-left', pixelCenters:'x+0.5,y+0.5', bounds:'half-open', alpha:'straight RGBA'});
    assert.deepEqual(c.fixedFoundation.pins, {
      cabin: c.sources.find(s => s.path.includes('/28-breathing-')).sha256,
      subject: c.sources.find(s => s.path.includes('/45-matching-neutral-')).sha256,
      neutral: c.sources.find(s => s.path === c.referenceNeutral).sha256,
    });
    if (reservedExact.has('parts/fixed_head_fronds.png')) {
      assert.deepEqual(c.fixedFoundation.supersededHead, {
        path:'parts/fixed_head_fronds.png',
        sha256:'61fdcf0d61460b012528915f525e4514450a0e476c03acc258aec86f6efe5c12',
        reason:'first reserved derivative cuts face below eyes at y=515; retained, not reused or refunded'
      });
      assert.equal(sha(fs.readFileSync(safeFile(root,c.fixedFoundation.supersededHead.path))),c.fixedFoundation.supersededHead.sha256,'superseded head changed');
    } else assert.equal(c.fixedFoundation.supersededHead,undefined,'unreserved old head');
    assert.deepEqual(c.fixedFoundation.parts.map(p => p.id), FIXED_IDS);
    for (const part of c.fixedFoundation.parts) {
      assert.equal(part.path, part.id === 'fixed_head_fronds' ? 'parts/fixed_head_fronds_face.png' : `parts/${part.id}.png`);
      if (part.id === 'fixed_head_fronds') {
        assert.deepEqual(part.crop, [340,198,900,607], 'head/face crop cutoff');
        assert.deepEqual(part.polygons, HEAD_POLYGONS, 'head/face selection changed');
        assert.deepEqual(part.sharedPoseSources, [46,47], 'moving hand exclusion changed');
        for (const [x,y] of FACE_WITNESSES) assert(part.regions.some(([l,t,r,b]) => x>=l && x<r && y>=t && y<b) || part.polygons.some(p => insidePolygon(x+0.5,y+0.5,p)), `missing face witness ${x},${y}`);
      }
      assert.equal(part.source, c.sources.find(s => s.path.includes('/45-matching-neutral-')).path);
      assert.equal(part.placement[0], part.crop[0]); assert.equal(part.placement[1], part.crop[1]);
      assert(part.regions.length && part.regions.every(b => b.length === 4 && b.every(Number.isInteger) && b[0] >= part.crop[0] && b[1] >= part.crop[1] && b[2] <= part.crop[2] && b[3] <= part.crop[3] && b[0] < b[2] && b[1] < b[3]));
      assert.equal(part.width, part.crop[2]-part.crop[0]); assert.equal(part.height, part.crop[3]-part.crop[1]);
      assert.equal(part.sourceSha256, c.fixedFoundation.pins.subject);
      assert.match(part.sha256, /^[a-f0-9]{64}$/);
      assert.equal(sha(fs.readFileSync(safeFile(root, part.path))), part.sha256, `changed part: ${part.id}`);
      const r = entries.map(n => JSON.parse(fs.readFileSync(safeFile(root, `journal/${n}`)))).find(r => r.destination === part.path);
      assert(r && r.kind === 'derivative' && r.target === part.id, `unreserved fixed part: ${part.path}`);
    }
  }
  const missing = ['parts','registration','neutral','usefulExtreme','technicalChecks'].filter(k => !c.readiness[k]);
  if (opts.requireReady) assert.equal(missing.length, 0, `incomplete preparation: ${missing.join(', ')}`);
  return {counts, perTarget, missing, lockPresent: fs.existsSync(path.join(root, 'writer.lock'))};
}
function falseIfAbsent(root, rel) {
  // Require an absent leaf, or a regular file at the exact reserved location.
  const file = path.join(root, rel);
  return !existsEntry(file);
}
function acquire(root) {
  components(path.join(root, 'preparation.json'), root);
  audit(root); // No lock is created over a pending write or mismatched historical pin.
  const lock = path.join(root, 'writer.lock');
  fs.mkdirSync(lock); // atomic exclusive ownership; interrupted locks require explicit reconciliation
  const token = crypto.randomBytes(16).toString('hex');
  try { fs.writeFileSync(path.join(lock, 'token'), token, {flag: 'wx'}); }
  catch (e) { fs.rmdirSync(lock); throw e; }
  return token;
}
function release(root, token) {
  const lock = path.join(root, 'writer.lock');
  assert.equal(fs.readFileSync(path.join(lock, 'token'), 'utf8'), token, 'not lock owner');
  fs.unlinkSync(path.join(lock, 'token')); fs.rmdirSync(lock);
}
function requireWriter(root, token) {
  assert.equal(fs.readFileSync(safeFile(root, 'writer.lock/token'), 'utf8'), token, 'writer lock required');
}
function reserve(root, token, kind, target, destination) {
  requireWriter(root, token);
  assert(['attempt','derivative','staticPass'].includes(kind));
  if (kind === 'attempt') assert(IDS.includes(target), 'unauthorized target');
  if (kind === 'derivative') assert(IDS.includes(target) || FIXED_IDS.includes(target), 'unauthorized target');
  const state = audit(root);
  assert(!state.lockPresent || fs.readFileSync(safeFile(root, 'writer.lock/token'), 'utf8') === token, 'concurrent writer');
  assert(state.counts[kind] < (kind === 'attempt' ? 16 : kind === 'derivative' ? 12 : 3), 'allowance exhausted');
  if (kind === 'attempt') assert(state.perTarget[target] < 2, 'target attempts exhausted');
  const ordinal = state.counts[kind] + 1;
  if (kind === 'attempt') destination = `attempts/${target}-${ordinal}.png`;
  else if (kind === 'derivative') assert(/^parts\/[a-z0-9_-]+\.png$/.test(destination), 'invalid derivative destination');
  else assert(/^states\/[a-z0-9_-]+\.png$/.test(destination), 'invalid static destination');
  safeFile(root, destination, true);
  // Case-insensitive collision (macOS) with any previous reservation.
  const prior = fs.readdirSync(path.join(root, 'journal')).map(n => JSON.parse(fs.readFileSync(safeFile(root, `journal/${n}`))));
  assert(!prior.some(r => r.destination.normalize('NFC').toLowerCase() === destination.normalize('NFC').toLowerCase()), 'reserved collision');
  const id = crypto.randomBytes(16).toString('hex');
  const receipt = {id, kind, ordinal, ...(kind === 'staticPass' ? {} : {target}), destination};
  const receiptFile = path.join(root, 'journal', `${id}.json`);
  const out = fs.openSync(receiptFile, 'wx', 0o600);
  try { fs.writeSync(out, JSON.stringify(receipt) + '\n'); fs.fsyncSync(out); }
  finally { fs.closeSync(out); }
  // Reservation is durable before any caller's fake/real dispatch or output creation.
  const fd = fs.openSync(path.join(root, 'journal'), 'r');
  try { fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
  return {receipt}; // No writable path handed to dispatch; use createOutput with this receipt ID.
}
// The only owned output creation route: receipt first, then exclusive creation.
// Never unlink a failed/partial write: it consumes the reserved allowance.
function createOutput(root, token, receiptId, bytes) {
  requireWriter(root, token);
  assert.match(receiptId, /^[a-f0-9]{32}$/);
  assert(Buffer.isBuffer(bytes), 'output bytes required');
  const receipt = JSON.parse(fs.readFileSync(safeFile(root, `journal/${receiptId}.json`)));
  assert.equal(receipt.id, receiptId, 'receipt identity changed');
  audit(root); // Includes all budget, pin and output reconciliation checks.
  const file = safeFile(root, receipt.destination, true);
  const fd = fs.openSync(file, fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
  try {
    let offset = 0;
    while (offset < bytes.length) offset += fs.writeSync(fd, bytes, offset, bytes.length - offset);
    fs.fsyncSync(fd);
  } finally { fs.closeSync(fd); }
  return file;
}
// Decoded-pixel proof uses only explicit source regions; transparent backing remains empty.
async function rgba(bytes) {
  const image = await loadImage(bytes);
  const canvas = createCanvas(image.width, image.height);
  const ctx = canvas.getContext('2d');
  ctx.drawImage(image, 0, 0);
  return {width:image.width, height:image.height, data:ctx.getImageData(0, 0, image.width, image.height).data};
}
function insidePolygon(x, y, polygon) {
  let inside=false;
  for(let i=0,j=polygon.length-1;i<polygon.length;j=i++) {
    const [ax,ay]=polygon[i], [bx,by]=polygon[j];
    if ((ay>y)!==(by>y) && x<(bx-ax)*(y-ay)/(by-ay)+ax) inside=!inside;
  }
  return inside;
}
function pixelsForRegion(source, crop, regions, polygons=[], comparisons=[]) {
  const [left,top,right,bottom] = crop;
  assert(source.width >= right && source.height >= bottom && left >= 0 && top >= 0);
  const out = new Uint8ClampedArray((right-left)*(bottom-top)*4);
  for (let y=top;y<bottom;y++) for (let x=left;x<right;x++) {
    if (!regions.some(([l,t,r,b]) => x>=l && x<r && y>=t && y<b) && !polygons.some(p=>insidePolygon(x+0.5,y+0.5,p))) continue;
    const from=(y*source.width+x)*4, to=((y-top)*(right-left)+x-left)*4;
    // The changing neutral hand touches the fixed cheek; it is NOT head art.
    if (comparisons.some(other=>[0,1,2,3].some(k=>source.data[from+k]!==other.data[from+k]))) continue;
    out.set(source.data.subarray(from,from+4),to);
  }
  return out;
}
function alphaFacts(data, width, height) {
  let zero=0, partial=0, opaque=0, bounds=[width,height,0,0];
  for(let y=0;y<height;y++) for(let x=0;x<width;x++) {
    const a=data[(y*width+x)*4+3];
    if(!a) zero++;
    else {
      if(a===255) opaque++; else partial++;
      bounds=[Math.min(bounds[0],x),Math.min(bounds[1],y),Math.max(bounds[2],x+1),Math.max(bounds[3],y+1)];
    }
  }
  assert(partial+opaque>0, 'empty fixed region');
  return {zero,partial,opaque,bounds};
}
function verifyProtected(source, part, spec, comparisons=[]) {
  assert.equal(part.width,spec.width); assert.equal(part.height,spec.height);
  const expected=pixelsForRegion(source,spec.crop,spec.regions,spec.polygons||[],comparisons);
  if (spec.id === 'fixed_head_fronds') {
    assert.deepEqual(spec.polygons,HEAD_POLYGONS);
    assert.deepEqual(spec.crop,[340,198,900,607]);
    assert.equal(comparisons.length,2,'both pose donors required at cheek boundary');
    for (const [x,y] of FACE_WITNESSES) {
      const i=((y-spec.crop[1])*part.width+x-spec.crop[0])*4;
      assert(expected[i+3]>0 && part.data[i+3]>0, `face/chin cropped at ${x},${y}`);
    }
  }
  // Canvas normalizes fully transparent RGB; all painted RGBA bytes must be exact.
  for(let i=0;i<expected.length;i+=4) {
    if (!expected[i+3]) assert.equal(part.data[i+3],0, `unprotected pixel at ${i/4}`);
    else for(let channel=0;channel<4;channel++) assert.equal(part.data[i+channel],expected[i+channel], `protected RGBA mismatch at ${i/4}:${channel}`);
  }
  const facts=alphaFacts(part.data,part.width,part.height);
  assert.deepEqual(facts,spec.decodedAlpha, `decoded alpha/bounds changed: ${spec.id}`);
  assert.equal(sha(Buffer.from(part.data)),spec.protectedRgbaSha256, `protected RGBA witness changed: ${spec.id}`);
}
async function auditFixed(root) {
  const c=config(root);
  if (!c.fixedFoundation) return;
  const source=await rgba(fs.readFileSync(safeFile(repo,c.fixedFoundation.parts[0].source)));
  const comparisons=await Promise.all([46,47].map(async n => {
    const pin=c.sources.find(s=>s.path.includes(`/train-actions/${n}-matching-`));
    assert(pin,'missing head pose pin');
    return rgba(fs.readFileSync(safeFile(repo,pin.path)));
  }));
  for (const spec of c.fixedFoundation.parts) {
    const part=await rgba(fs.readFileSync(safeFile(root,spec.path)));
    verifyProtected(source,part,spec,spec.id === 'fixed_head_fronds' ? comparisons : []);
  }
  // An opaque neutral is inspected for appearance, never decoded as a part.
  const neutral=await rgba(fs.readFileSync(safeFile(repo,c.referenceNeutral)));
  assert.deepEqual(alphaFacts(neutral.data,neutral.width,neutral.height),c.fixedFoundation.neutralDecodedAlpha);
}
async function prepareFixed(root, definitions) {
  const token=acquire(root);
  try {
    const c=config(root);
    assert(!c.fixedFoundation, 'fixed foundation already prepared');
    const sourcePin=c.sources.find(s=>s.path.includes('/45-matching-neutral-'));
    const source=await rgba(fs.readFileSync(safeFile(repo,sourcePin.path)));
    const neutral=await rgba(fs.readFileSync(safeFile(repo,c.referenceNeutral)));
    const poseDonors=await Promise.all([46,47].map(async n => rgba(fs.readFileSync(safeFile(repo,c.sources.find(s=>s.path.includes(`/train-actions/${n}-matching-`)).path)))));
    const parts=[];
    assert.deepEqual(definitions.map(d=>d.id),FIXED_IDS);
    for (const def of definitions) {
      const width=def.crop[2]-def.crop[0], height=def.crop[3]-def.crop[1];
      const comparisons=def.id === 'fixed_head_fronds' ? poseDonors : [];
      const data=pixelsForRegion(source,def.crop,def.regions,def.polygons||[],comparisons);
      const canvas=createCanvas(width,height),ctx=canvas.getContext('2d');
      const image=ctx.createImageData(width,height); image.data.set(data);ctx.putImageData(image,0,0);
      const bytes=canvas.toBuffer('image/png');
      assert(FIXED_IDS.includes(def.id), 'unauthorized fixed part');
      const r=reserve(root,token,'derivative',def.id,def.path);
      createOutput(root,token,r.receipt.id,bytes);
      const decoded=await rgba(bytes);
      // Reject lossy alpha/colour round trips rather than publishing invented pixels.
      const spec={...def,source:sourcePin.path,sourceSha256:sourcePin.sha256,placement:def.crop.slice(0,2),width,height,
        sha256:sha(bytes),decodedAlpha:alphaFacts(decoded.data,width,height),protectedRgbaSha256:sha(Buffer.from(decoded.data))};
      verifyProtected(source,decoded,spec,comparisons);
      parts.push(spec);
    }
    c.fixedFoundation={referenceRole:'opaque appearance reference only; never a body layer',
      cabinStatus:'opaque candidate; revealed areas not certified clean',
      torsoStatus:'selected unaffected islands only; obscured torso unresolved',
      unresolved:[
        'Face and chin from source 45 are isolated through y=606; cheek pixels differing in 46/47 at the hand boundary are excluded. No hand or joint pixels are claimed as fixed; hidden face/neck under the hand or collar is unresolved.',
        'Torso under neutral arm and sleeve is obscured; selected visible islands are not a complete body layer.',
        'Cabin 28 is opaque but its exposed-under-arm areas have not been certified clean; no backing derivative exists.',
        'Lower contact is copied from source 45, including pixels hidden by the foreground table in composed 48; contact in a new composite remains untested.'
      ],
      raster:{width:1920,height:1080,origin:'top-left',pixelCenters:'x+0.5,y+0.5',bounds:'half-open',alpha:'straight RGBA'},
      pins:{cabin:c.sources.find(s=>s.path.includes('/28-breathing-')).sha256,subject:sourcePin.sha256,neutral:c.sources.find(s=>s.path===c.referenceNeutral).sha256},
      neutralDecodedAlpha:alphaFacts(neutral.data,neutral.width,neutral.height),parts};
    // One writer; retain a prior record if a write cannot complete.
    const pending=path.join(root,'preparation.pending');
    fs.writeFileSync(pending,JSON.stringify(c,null,2)+'\n',{flag:'wx'});
    fs.renameSync(pending,safeFile(root,'preparation.json'));
    return parts;
  } finally { release(root,token); }
}
if (require.main === module) {
  (async()=>{
    assert.deepEqual(process.argv.slice(2).sort(), process.argv.includes('--require-ready') ? ['--check','--require-ready'] : ['--check'], 'usage: --check [--require-ready]');
    const result = audit(__dirname, {requireReady: process.argv.includes('--require-ready')});
    await auditFixed(__dirname);
    console.log(`preparation integrity OK; consumed ${JSON.stringify(result.counts)}; ${result.missing.length ? 'INCOMPLETE: '+result.missing.join(', ') : 'technical record complete; human VG2-14 pending'}${result.lockPresent ? '; writer lock present' : ''}`);
  })().catch(e=>{console.error(e);process.exitCode=1});
}
module.exports = {audit, auditFixed, acquire, release, reserve, createOutput, safeFile, rgba, pixelsForRegion, verifyProtected, alphaFacts, prepareFixed, HEAD_POLYGONS};
