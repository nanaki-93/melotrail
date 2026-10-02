'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-18/edge-20261002T022147Z');
const {expand}=require(owner+'/scripts/support.cjs'),{compose}=require(owner+'/scripts/compose_frames.cjs');
function image(w,h,rgba=[20,44,30,255]){const data=new Uint8ClampedArray(w*h*4);for(let i=0;i<data.length;i+=4)data.set(rgba,i);return {width:w,height:h,data};}
test('a one-pixel margin includes diagonals and nothing beyond its boundary',()=>{
  const mask=image(5,5,[0,0,0,0]);mask.data[(2*5+2)*4+3]=255;const out=expand(mask,1);
  for(let y=0;y<5;y++)for(let x=0;x<5;x++)assert.equal(out.data[(y*5+x)*4+3],x>=1&&x<=3&&y>=1&&y<=3?255:0);
});
test('support expansion clips only to the actual image dimensions',()=>{
  const mask=image(3,3,[0,0,0,0]);mask.data[3]=255;const out=expand(mask,1);
  assert.equal([...out.data].filter((v,i)=>i%4===3&&v).length,4);assert.equal(mask.data[7],0);
});
test('unproposed larger margins and malformed masks are rejected',()=>{
  const mask=image(5,5);for(const n of [0,2,-1,1.5])assert.throws(()=>expand(mask,n));
  assert.throws(()=>expand({...mask,data:new Uint8ClampedArray(3)},1));
});
test('observed antialias edge is retained, while the original mask still rejects it',()=>{
  const mask=image(5,5,[0,0,0,0]);mask.data[(2*5+2)*4+3]=255;
  const first=image(5,5),raw=image(5,5),art=image(5,5);raw.data[(2*5+1)*4]+=5;
  assert.throws(()=>compose(raw,first,art,mask),/unexpected motion outside/);
  assert.equal(compose(raw,first,art,expand(mask,1)).data[(2*5+1)*4],25);
});
test('new out-of-support movement still fails rather than being painted over',()=>{
  const mask=image(5,5,[0,0,0,0]);mask.data[(2*5+2)*4+3]=255;
  const first=image(5,5),raw=image(5,5);raw.data[0]++;
  assert.throws(()=>compose(raw,first,first,expand(mask,1)),/unexpected motion outside/);
});
