'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),{spawnSync}=require('node:child_process');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-16/packets-20261002T011754Z');
const c=require(owner+'/scripts/colour.cjs');
test('sRGB and Rec709 published breakpoints and mid-grey differ',()=>{
  assert(Math.abs(c.srgbToLinear(.04045)-.00313080495356)<1e-10);
  assert.equal(c.linearTo709(.01),.045);assert.equal(c.forward[0],0);assert.equal(c.forward[255],255);
  assert.equal(c.forward[128],115);assert(c.forward[128]!==128);
});
test('all 8-bit values round-trip within the frozen transport quantization bound',()=>{
  for(let i=0;i<256;i++){assert(Math.abs(c.inverse[c.forward[i]]-i)<=2);if(i)assert(c.forward[i]>=c.forward[i-1]);}
});
test('blindly tagging sRGB as Rec709 fails the patch limit',()=>{
  assert(Math.abs(c.inverse[128]-128)>4);assert(Math.abs(c.inverse[c.forward[128]]-128)<=2);
});
test('explicit conversion preserves alpha and does not mutate original pixels',()=>{
  const src=Uint8ClampedArray.from([128,64,192,255,0,255,128,200]),before=new Uint8ClampedArray(src),out=c.convert(src);
  assert.deepEqual(src,before);assert.equal(out[3],255);assert.equal(out[7],200);assert.equal(out[0],115);
});
test('fresh supervision regressions dispatch only data spies',()=>{
  const r=spawnSync('/usr/bin/python3',['-B',owner+'/scripts/check-supervision.py'],{encoding:'utf8',timeout:15000,env:{...process.env,PYTHONDONTWRITEBYTECODE:'1'}});
  assert.equal(r.status,0,r.stdout+r.stderr);assert.match(r.stderr,/Ran 18 tests/);assert.match(r.stderr,/OK/);
});
