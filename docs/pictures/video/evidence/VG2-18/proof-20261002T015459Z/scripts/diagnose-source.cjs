'use strict';
// Read-only diagnosis of retained PNGs; no renderer, media encode or image writes.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const owner=path.resolve(__dirname,'..'),repo=path.resolve(owner,'../../../../../..');
const packetFile=path.join(repo,'docs/pictures/video/evidence/VG2-17/metadata-20261002T015459Z/packets/wave.json');
const packet=JSON.parse(fs.readFileSync(packetFile));
const {rgba}=require(path.join(packet.owner,'scripts/verify_pixels.cjs'));
const digest=b=>crypto.createHash('sha256').update(b).digest('hex');
const started=Date.now();
function compare(actual,expected,allowed,protectedOnly=true){
  let maximum=0,total=0,count=0,pixelsOverTwo=0,worst=null;
  const bounds=[actual.width,actual.height,-1,-1];
  for(let i=0;i<actual.data.length;i+=4){
    if(protectedOnly && allowed.data[i+3])continue;
    let pixelMax=0;
    for(let c=0;c<3;c++){const d=Math.abs(actual.data[i+c]-expected.data[i+c]);pixelMax=Math.max(pixelMax,d);total+=d;count++;}
    const x=(i/4)%actual.width,y=Math.floor(i/4/actual.width);
    if(pixelMax>maximum){maximum=pixelMax;worst={x,y,actual:Array.from(actual.data.slice(i,i+3)),expected:Array.from(expected.data.slice(i,i+3))};}
    if(pixelMax>2){pixelsOverTwo++;bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);}
  }
  return {maximum,meanAbsoluteError:total/count,pixels:count/3,pixelsOverTwo,boundsOverTwo:pixelsOverTwo?bounds:null,worst};
}
async function main(){
  const base=await rgba(path.join(repo,packet.artSample.path)),allowed=await rgba(path.join(repo,packet.allowedMask.path));
  const framePath=n=>path.join(owner,'live/source',`frame-${String(n).padStart(4,'0')}.png`);
  const first=await rgba(framePath(1)),rows=[];
  for(const frame of [1,25,26,76,128,129,150]){
    if(Date.now()-started>60000)throw new Error('Read-only diagnosis exceeded 60 seconds');
    const im=frame===1?first:await rgba(framePath(frame));
    rows.push({frame,sha256:digest(fs.readFileSync(framePath(frame))),protectedAgainstArtwork:compare(im,base,allowed),protectedAgainstFirstFrame:compare(im,first,allowed),wholeAgainstFirstFrame:compare(im,first,allowed,false)});
  }
  console.log(JSON.stringify({status:'READ_ONLY_FAILURE_DIAGNOSIS_NOT_A_PASS',nativeMediaOperations:0,newImageFiles:0,packetSha256:digest(fs.readFileSync(packetFile)),sourceProtectedMaxDelta:packet.pixelContract.sourceProtectedMaxDelta,frames:rows,elapsedSeconds:(Date.now()-started)/1000},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
