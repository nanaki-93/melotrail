'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const BASE=path.resolve(__dirname,'..'),REPO=path.resolve(BASE,'../../../../../..');
const {rgba,png}=require('./verify_pixels.cjs');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function imageContract(im){
  assert(Number.isInteger(im.width)&&im.width>0&&Number.isInteger(im.height)&&im.height>0,'invalid image dimensions');
  assert.equal(im.data.length,im.width*im.height*4,'invalid pixel count');
}
function sameSize(a,b){assert.deepEqual([a.width,a.height],[b.width,b.height],'image dimensions differ');}
function compose(rendered,first,artwork,allowed){
  for(const im of [rendered,first,artwork,allowed]){imageContract(im);sameSize(rendered,im);}
  const out=new Uint8ClampedArray(rendered.data.length);let fixed=0,moving=0,rawMaximum=0;
  for(let i=0;i<out.length;i+=4){
    assert.equal(rendered.data[i+3],255,'render must be opaque');
    assert.equal(first.data[i+3],255,'first render must be opaque');
    assert.equal(artwork.data[i+3],255,'fixed artwork must be opaque');
    if(allowed.data[i+3]){out.set(rendered.data.subarray(i,i+4),i);moving++;}
    else{
      for(let c=0;c<4;c++)assert.equal(rendered.data[i+c],first.data[i+c],'unexpected motion outside frozen support mask');
      for(let c=0;c<3;c++)rawMaximum=Math.max(rawMaximum,Math.abs(rendered.data[i+c]-artwork.data[i+c]));
      out.set(artwork.data.subarray(i,i+4),i);fixed++;
    }
  }
  assert(fixed>0&&moving>0,'mask must keep both fixed and moving pixels');
  return {width:rendered.width,height:rendered.height,data:out,fixedPixels:fixed,movingPixels:moving,rawProtectedMaximum:rawMaximum};
}
async function main(){
  const mode=process.argv[2];assert(['--check','--compose'].includes(mode));
  const packetFile=path.join(BASE,'packets/wave.json'),p=JSON.parse(fs.readFileSync(packetFile));
  if(mode==='--compose'){
    assert.equal(process.env.MELOTRAIL_PROOF_KIND,'wave');assert.equal(process.env.MELOTRAIL_PACKET_FILE,packetFile);
    assert.deepEqual(fs.readdirSync(path.join(p.destinations.work,'source')),[]);
  }
  const started=Date.now(),files=p.retainedFrames;assert.equal(files.length,150);
  const artwork=await rgba(path.join(REPO,p.artSample.path)),allowed=await rgba(path.join(REPO,p.allowedMask.path));
  const first=await rgba(files[0].path),results=[];let neutral,mid;
  for(let i=0;i<files.length;i++){
    assert(Date.now()-started<90000,'composition/check exceeds 90 seconds');
    const input=files[i],bytes=fs.readFileSync(input.path);assert.equal(input.frame,i+1);assert.equal(sha(bytes),input.sha256,'retained frame changed');
    const raw=i===0?first:await rgba(input.path);assert.deepEqual([raw.width,raw.height],[p.delivery.width,p.delivery.height]);
    const corrected=compose(raw,first,artwork,allowed);
    if(i===0)neutral=corrected.data;
    if(i===75)mid=corrected.data;
    if(i===149)assert.deepEqual(corrected.data,neutral,'neutral return changed');
    let fixedMax=0,movingMax=0;
    for(let j=0;j<corrected.data.length;j++){
      if(allowed.data[(j-j%4)+3])movingMax=Math.max(movingMax,Math.abs(corrected.data[j]-raw.data[j]));
      else fixedMax=Math.max(fixedMax,Math.abs(corrected.data[j]-artwork.data[j]));
    }
    assert.equal(fixedMax,0);assert.equal(movingMax,0);
    const row={frame:i+1,retainedSha256:input.sha256,composedPixelsSha256:sha(corrected.data),fixedMaximum:fixedMax,movingMaximum:movingMax,rawProtectedMaximum:corrected.rawProtectedMaximum};
    if(mode==='--compose'){
      const out=path.join(p.destinations.work,'source',`frame-${String(i+1).padStart(4,'0')}.png`),encoded=png(corrected);
      fs.writeFileSync(out,encoded,{flag:'wx'});const restored=await rgba(out);assert.deepEqual(restored.data,corrected.data,'composed PNG changed pixels');row.sourceSha256=sha(encoded);
    }
    results.push(row);
  }
  let changedPixels=0;
  for(let i=0;i<neutral.length;i+=4)if(allowed.data[i+3]&&[0,1,2].some(c=>Math.abs(mid[i+c]-neutral[i+c])>8))changedPixels++;
  assert(changedPixels>=1000,'retained wave is static');
  const facts={status:mode==='--check'?'IN_MEMORY_COMPOSITION_PASS_NO_MEDIA':'COMPOSED_SOURCE_PASS_NOT_WAVE_APPROVAL',packetSha256:sha(fs.readFileSync(packetFile)),nativeMediaOperations:0,newImageFiles:mode==='--compose'?150:0,frames:results,changedPixels,neutralReturnMaximum:0,fixedPixels:first.width*first.height-allowed.data.filter((v,i)=>i%4===3&&v>0).length,elapsedSeconds:(Date.now()-started)/1000};
  if(mode==='--compose')fs.writeFileSync(path.join(p.destinations.work,'composition.json'),JSON.stringify(facts,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify(facts,null,2));
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1;});
module.exports={compose};
