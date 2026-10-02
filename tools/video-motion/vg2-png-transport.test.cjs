'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),{spawnSync}=require('node:child_process');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-17/metadata-20261002T015459Z');
const png=require(owner+'/scripts/png_transport.cjs');
const {createCanvas,loadImage}=require('./node_modules/@napi-rs/canvas');
function fixture(){const c=createCanvas(2,1),ctx=c.getContext('2d'),pixels=ctx.createImageData(2,1);pixels.data.set([115,48,189,255,0,255,0,255]);ctx.putImageData(pixels,0,0);return c.toBuffer('image/png');}
async function pixels(bytes){const im=await loadImage(png.metadataFree(bytes)),c=createCanvas(im.width,im.height),ctx=c.getContext('2d');ctx.drawImage(im,0,0);return ctx.getImageData(0,0,im.width,im.height).data;}
test('PNG CRC implementation matches the standard check vector',()=>assert.equal(png.crc32(Buffer.from('123456789')),0xcbf43926));
test('metadata correction preserves every compressed image byte and raw code value',async()=>{
  const original=fixture(),before=Buffer.from(original),fixed=png.rec709(original);png.requireRec709(fixed);
  assert.deepEqual(original,before);assert.deepEqual(png.idat(fixed),png.idat(original));
  assert.deepEqual(await pixels(fixed),await pixels(original));assert.deepEqual([...await pixels(fixed)],[115,48,189,255,0,255,0,255]);
  assert.deepEqual(png.rec709(fixed),fixed);
});
test('old sRGB transport and contradictory colour declarations fail',()=>{
  const original=fixture();assert.throws(()=>png.requireRec709(original));
  const fixed=png.rec709(original),parts=png.chunks(fixed);
  const wrong=Buffer.concat([png.SIGNATURE,parts[0].bytes,png.chunk('sRGB',Buffer.from([0])),...parts.slice(1).map(c=>c.bytes)]);
  assert.throws(()=>png.requireRec709(wrong),/ambiguous/);
});
test('damaged, truncated and trailing PNG bytes are rejected',()=>{
  const original=fixture(),bad=Buffer.from(original);bad[bad.length-1]^=1;
  for(const bytes of [bad,original.subarray(0,original.length-1),Buffer.concat([original,Buffer.from([0])])])assert.throws(()=>png.rec709(bytes));
});
test('installed filter regression and supervision run without native media tools',()=>{
  const r=spawnSync('/usr/bin/python3',['-B',owner+'/scripts/check-supervision.py'],{encoding:'utf8',timeout:15000,env:{...process.env,PYTHONDONTWRITEBYTECODE:'1'}});
  assert.equal(r.status,0,r.stdout+r.stderr);assert.match(r.stderr,/Ran 22 tests/);assert.match(r.stderr,/OK/);
});
