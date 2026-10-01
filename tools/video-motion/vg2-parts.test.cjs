'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const repo = path.resolve(__dirname,'../..');
process.env.MELOTRAIL_REPO_ROOT = repo;
const run = path.join(repo,'docs/pictures/video/evidence/VG2-13/run-20261001T155758Z');
const {normalize,check} = require(path.join(run,'scripts/upper-sleeve.cjs'));

test('endpoint normalization preserves RGB, intermediate edge alpha, dimensions and input',()=>{
  const data=new Uint8ClampedArray([10,20,30,0,10,20,30,8,10,20,30,9,10,20,30,244,10,20,30,245,10,20,30,253,10,20,30,255]);
  const before=new Uint8ClampedArray(data);
  const output=normalize({width:7,height:1,data});
  assert.deepEqual(data,before);
  assert.deepEqual(Array.from(output.data).filter((_,i)=>i%4===3),[0,0,9,244,255,255,255]);
  for(let i=0;i<data.length;i++) if(i%4!==3) assert.equal(output.data[i],data[i]);
  assert.equal(output.cleared,1);assert.equal(output.solidified,2);
});

test('translucent fabric fails; endpoint derivative passes without permitting opaque RGB edits',()=>{
  const data=new Uint8ClampedArray(1254*1254*4);
  for(let i=0;i<data.length;i+=4) data.set([10,100,80,253],i);
  const original={width:1254,height:1254,data};
  assert.throws(()=>check(original,original),/alpha mismatch/);
  const normalized=normalize(original);
  check(normalized,original);
  const damaged={...normalized,data:new Uint8ClampedArray(normalized.data)};
  damaged.data[(350*damaged.width+600)*4]++;
  assert.throws(()=>check(damaged,original),/opaque RGB changed/);
});
