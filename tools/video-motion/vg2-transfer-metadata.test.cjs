'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs'),{spawnSync}=require('node:child_process');
const owner=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-16/transfer-20261002T014011Z');
test('real failed sRGB media and previous encode packet are rejected by corrected guards',()=>{
  const r=spawnSync('/usr/bin/python3',['-B',owner+'/scripts/check-supervision.py'],{encoding:'utf8',timeout:15000,env:{...process.env,PYTHONDONTWRITEBYTECODE:'1'}});
  assert.equal(r.status,0,r.stdout+r.stderr);assert.match(r.stderr,/Ran 20 tests/);assert.match(r.stderr,/OK/);
});
test('metadata repair retains the proven numeric transfer conversion',()=>{
  const previous=path.resolve(owner,'../packets-20261002T011754Z/scripts/colour.cjs');
  assert.equal(fs.readFileSync(owner+'/scripts/colour.cjs','utf8'),fs.readFileSync(previous,'utf8'));
  const colour=require(owner+'/scripts/colour.cjs');assert.equal(colour.forward[128],115);
  assert(Math.abs(colour.inverse[128]-128)>4);
});
