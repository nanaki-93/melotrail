'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const BASE=path.resolve(__dirname,'..');
const digest=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function copyVerified(input,destination,expected){
  assert.equal(fs.realpathSync(input),input,'source path is not canonical');
  assert(!fs.lstatSync(input).isSymbolicLink(),'source is a symlink');
  assert.equal(digest(fs.readFileSync(input)),expected,'source PNG changed');
  fs.copyFileSync(input,destination,fs.constants.COPYFILE_EXCL);
  assert.equal(digest(fs.readFileSync(destination)),expected,'copied PNG differs');
}
async function main(){
  assert(['--check','--copy'].includes(process.argv[2]));const check=process.argv[2]==='--check';
  const packetFile=BASE+'/packets/wave.json',p=JSON.parse(fs.readFileSync(packetFile));
  if(!check){assert.equal(process.env.MELOTRAIL_PROOF_KIND,'wave');assert.equal(process.env.MELOTRAIL_PACKET_FILE,packetFile);}
  const proofBytes=fs.readFileSync(p.completedComposition.path);assert.equal(digest(proofBytes),p.completedComposition.sha256);
  const proof=JSON.parse(proofBytes);assert.equal(proof.status,'COMPOSED_SOURCE_PASS_NOT_WAVE_APPROVAL');assert.equal(proof.frames.length,150);assert.equal(p.retainedFrames.length,150);
  const {rgba}=require('./verify_pixels.cjs'),rows=[],started=Date.now();
  for(let i=0;i<150;i++){
    assert(Date.now()-started<90000,'source reuse/check deadline');
    const row=p.retainedFrames[i],receipt=proof.frames[i];assert.equal(row.frame,i+1);assert.equal(receipt.frame,i+1);assert.equal(row.sha256,receipt.sourceSha256);
    assert.equal(receipt.fixedMaximum,0);assert.equal(receipt.movingMaximum,0);
    const bytes=fs.readFileSync(row.path);assert.equal(digest(bytes),row.sha256,'source PNG changed');
    if(check){const im=await rgba(row.path);assert.deepEqual([im.width,im.height],[1920,1080]);assert.equal(digest(im.data),receipt.composedPixelsSha256,'saved PNG does not match validated pixels');}
    else copyVerified(row.path,path.join(p.destinations.work,'source',`frame-${String(i+1).padStart(4,'0')}.png`),row.sha256);
    rows.push({frame:i+1,sourceSha256:row.sha256,composedPixelsSha256:receipt.composedPixelsSha256});
  }
  const result={status:check?'SAVED_SOURCE_REUSE_CHECK_PASS_NO_MEDIA':'VERIFIED_SOURCE_COPY_PASS',newImageFiles:check?0:150,recompositions:0,renderBatches:0,frames:rows,elapsedSeconds:(Date.now()-started)/1000};
  if(!check)fs.writeFileSync(path.join(p.destinations.work,'source-copy.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify(result,null,2));
}
if(require.main===module)main().catch(error=>{console.error(error);process.exitCode=1;});
module.exports={copyVerified};
