'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-18/encode-20261002T023519Z');
const {copyVerified}=require(owner+'/scripts/reuse_source.cjs');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function fixture(body){const root=fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(),'melotrail-source-copy-test-')));try{const source=root+'/source.bin';fs.writeFileSync(source,'tiny data fixture');body(root,source,sha(fs.readFileSync(source)));}finally{fs.rmSync(root,{recursive:true,force:false});}}
test('validated source is copied byte-for-byte without altering the original',()=>fixture((root,source,digest)=>{
  const before=fs.readFileSync(source);copyVerified(source,root+'/copied.bin',digest);assert.deepEqual(fs.readFileSync(root+'/copied.bin'),before);assert.deepEqual(fs.readFileSync(source),before);
}));
test('a changed source fails before creating a destination',()=>fixture((root,source,digest)=>{
  fs.appendFileSync(source,'changed');assert.throws(()=>copyVerified(source,root+'/copied.bin',digest),/source PNG changed/);assert(!fs.existsSync(root+'/copied.bin'));
}));
test('existing output and a source symlink cannot be silently accepted',()=>fixture((root,source,digest)=>{
  fs.writeFileSync(root+'/copied.bin','protected');assert.throws(()=>copyVerified(source,root+'/copied.bin',digest));assert.equal(fs.readFileSync(root+'/copied.bin','utf8'),'protected');
  fs.symlinkSync(source,root+'/alias.bin');assert.throws(()=>copyVerified(root+'/alias.bin',root+'/other.bin',digest),/not canonical/);assert(!fs.existsSync(root+'/other.bin'));
}));
test('the prepared six-minute budget reserves more time for conversion and schedules no recomposition',()=>{
  const p=JSON.parse(fs.readFileSync(owner+'/packets/wave.json'));assert.equal(p.limits.cumulativeSeconds,360);
  assert.deepEqual(p.limits.phaseSeconds,{compose:180,encode:60,validationAndCopy:120});assert.equal(p.nativeCounts.sourceComposites,0);assert.equal(p.nativeCounts.renderBatches,0);assert.equal(p.nativeCounts.reusedSourceCopies,150);
  assert.equal(path.basename(p.operations[0].argv[1]),'reuse_source.cjs');assert.equal(p.operations[0].argv[2],'--copy');
  assert.equal(p.limits.retries,0);assert.equal(p.limits.attempts,1);
});
