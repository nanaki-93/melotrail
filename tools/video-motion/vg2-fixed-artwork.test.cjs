'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-18/preserve-20261002T021617Z');
const {compose}=require(owner+'/scripts/compose_frames.cjs');
function fixture(){
  const im=values=>({width:2,height:1,data:new Uint8ClampedArray(values)});
  return {raw:im([99,82,54,255,10,20,30,255]),art:im([20,44,30,255,40,50,60,255]),mask:im([0,0,0,0,255,255,255,255])};
}
test('fixed artwork is exact while every moving-channel value is preserved',()=>{
  const {raw,art,mask}=fixture(),saved=raw.data.slice(),actual=compose(raw,raw,art,mask);
  assert.deepEqual([...actual.data],[20,44,30,255,10,20,30,255]);assert.equal(actual.rawProtectedMaximum,79);assert.deepEqual(raw.data,saved);
});
test('motion outside the existing support mask cannot be hidden by composition',()=>{
  const {raw,art,mask}=fixture(),moved={...raw,data:raw.data.slice()};moved.data[0]++;
  assert.throws(()=>compose(moved,raw,art,mask),/unexpected motion outside/);
});
test('motion inside the support mask remains visible',()=>{
  const {raw,art,mask}=fixture(),moved={...raw,data:raw.data.slice()};moved.data[4]=210;
  assert.equal(compose(moved,raw,art,mask).data[4],210);
});
test('different dimensions and incomplete pixel data are rejected',()=>{
  const {raw,art,mask}=fixture();assert.throws(()=>compose(raw,raw,{...art,width:1},mask));
  assert.throws(()=>compose(raw,raw,art,{...mask,data:new Uint8ClampedArray(4)}));
});
test('transparent source or fixed-artwork pixels are rejected',()=>{
  for(const which of ['raw','art']){const f=fixture();f[which].data[3]=254;assert.throws(()=>compose(f.raw,f.raw,f.art,f.mask),/opaque/);}
});
test('empty and full replacement masks cannot erase the fixed/moving boundary',()=>{
  const {raw,art,mask}=fixture();for(const alpha of [0,255]){mask.data[3]=mask.data[7]=alpha;assert.throws(()=>compose(raw,raw,art,mask),/both fixed and moving/);}
});
test('actual measured mismatch exceeds the unchanged pixel limit until fixed artwork is restored',()=>{
  const fs=require('node:fs'),packet=JSON.parse(fs.readFileSync(owner+'/inputs/scope.json'));
  const prior=JSON.parse(fs.readFileSync(packet.previousWavePacket));
  const measured=JSON.parse(fs.readFileSync(packet.retainedWaveOwner+'/checks/source-diagnosis.json')).frames[0].protectedAgainstArtwork.worst;
  const {raw,art,mask}=fixture();raw.data.set(measured.actual,0);art.data.set(measured.expected,0);
  const fixed=compose(raw,raw,art,mask);assert.equal(fixed.rawProtectedMaximum,79);assert(fixed.rawProtectedMaximum>prior.pixelContract.sourceProtectedMaxDelta);
  assert.deepEqual([...fixed.data.slice(0,3)],measured.expected);
});
