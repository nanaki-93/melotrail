'use strict';
// Canonical Node regression entrypoint; Blender/Python proof helpers stay outside
// application/tool-runtime owners. This runs pure data checks, never Blender/media.
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const {spawnSync}=require('node:child_process');
test('standalone wave controls, invalid inputs and data-only process limits pass their nine checks',()=>{
  const helper=path.resolve(__dirname,'../../docs/pictures/video/evidence/VG2-15/rig-20261002T010147Z/scripts/check-data.py');
  const r=spawnSync('/usr/bin/python3',['-B',helper],{encoding:'utf8',timeout:10000,env:{...process.env,PYTHONDONTWRITEBYTECODE:'1'}});
  assert.equal(r.status,0,r.stdout+r.stderr);assert.match(r.stderr,/Ran 9 tests/);assert.match(r.stderr,/OK/);
});
