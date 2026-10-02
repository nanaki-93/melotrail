'use strict';
// Reuse the six preserved source frames. This performs byte copies, no synthesis.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const BASE=path.resolve(__dirname,'..'),sha=b=>crypto.createHash('sha256').update(b).digest('hex');
assert.deepEqual(process.argv.slice(2),['--generate']); // Frozen supervisor interface; reuse is declared in the packet.
assert.equal(process.env.MELOTRAIL_PROOF_KIND,'colour');
assert.equal(process.env.MELOTRAIL_PACKET_FILE,BASE+'/packets/colour.json');
const p=JSON.parse(fs.readFileSync(process.env.MELOTRAIL_PACKET_FILE));
assert.equal(sha(fs.readFileSync(process.env.MELOTRAIL_PACKET_FILE)),process.env.MELOTRAIL_BLENDER_PACKET);
assert.equal(p.delivery.frames,6);assert(p.reuseOwner);
for(const name of ['patches.json',...Array.from({length:6},(_,i)=>`source/frame-${String(i+1).padStart(4,'0')}.png`)]){
  const source=p.reuseOwner+'/'+name,bytes=fs.readFileSync(source);assert.equal(sha(bytes),p.files[source]);
  fs.writeFileSync(p.destinations.work+'/'+name,bytes,{flag:'wx'});
}
console.log('COPIED_SIX_HASH_BOUND_SOURCE_FRAMES_AND_PATCH_REFERENCE_NO_GENERATION');
