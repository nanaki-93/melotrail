'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const BASE=path.resolve(__dirname,'..'),REPO=path.resolve(BASE,'../../../../../..');
const {createCanvas,loadImage}=require(path.join(REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
const colour=require('./colour.cjs');const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
async function rgba(file){const im=await loadImage(file),c=createCanvas(im.width,im.height),ctx=c.getContext('2d');ctx.drawImage(im,0,0);return {width:im.width,height:im.height,data:ctx.getImageData(0,0,im.width,im.height).data};}
function png(im){const c=createCanvas(im.width,im.height),ctx=c.getContext('2d'),d=ctx.createImageData(im.width,im.height);d.data.set(im.data);ctx.putImageData(d,0,0);return c.toBuffer('image/png');}
function file(dir,n){return path.join(dir,`frame-${String(n).padStart(4,'0')}.png`);}
function sequence(dir,n){assert.deepEqual(fs.readdirSync(dir).sort(),Array.from({length:n},(_,i)=>path.basename(file(dir,i+1))),'missing/extra sequence frame');}
function mae(a,b,lut=null,box=null,width=0){let error=0,count=0,max=0;
  const one=i=>{for(let c=0;c<3;c++){const d=Math.abs((lut?lut[a[i+c]]:a[i+c])-b[i+c]);error+=d;max=Math.max(max,d);count++;}};
  if(box){for(let y=box[1];y<box[1]+box[3];y++)for(let x=box[0];x<box[0]+box[2];x++)one((y*width+x)*4);}else for(let i=0;i<a.length;i+=4)one(i);
  return {mae:error/count,max};
}
async function source(p){
  const work=p.destinations.work,n=p.delivery.frames;sequence(work+'/source',n);
  const base=await rgba(path.join(REPO,p.artSample.path)),allowed=p.kind==='wave'?await rgba(path.join(REPO,p.allowedMask.path)):null;
  let first,last,changedPixels=0,maxProtectedDelta=0;const frames=[];
  for(let i=1;i<=n;i++){
    const f=file(work+'/source',i),im=await rgba(f);assert.deepEqual([im.width,im.height],[p.delivery.width,p.delivery.height]);
    for(let j=3;j<im.data.length;j+=4)assert.equal(im.data[j],255,'nonopaque rendered scene');
    if(i===1)first=im.data;
    if(allowed){
      for(let j=0;j<im.data.length;j+=4)if(!allowed.data[j+3])for(let c=0;c<3;c++)maxProtectedDelta=Math.max(maxProtectedDelta,Math.abs(im.data[j+c]-base.data[j+c]));
      assert(maxProtectedDelta<=p.pixelContract.sourceProtectedMaxDelta,'fixed source region changed');
      if(i===76)for(let j=0;j<im.data.length;j+=4)if(allowed.data[j+3]&&[0,1,2].some(c=>Math.abs(im.data[j+c]-first[j+c])>8))changedPixels++;
    }
    const converted={...im,data:colour.convert(im.data)};
    const dst=file(work+'/encoded_input',i);
    if(p.kind==='colour'){const reused=file(p.reuseOwner+'/encoded_input',i),bytes=fs.readFileSync(reused);assert.equal(sha(bytes),p.files[reused]);fs.writeFileSync(dst,bytes,{flag:'wx'});}
    else fs.writeFileSync(dst,png(converted),{flag:'wx'});
    const decodedTransport=await rgba(dst);assert.deepEqual(decodedTransport.data,converted.data,'PNG transport changed Rec.709 code values');
    frames.push({frame:i,sourceSha256:sha(fs.readFileSync(f)),convertedSha256:sha(fs.readFileSync(dst))});last=im.data;
  }
  assert.deepEqual(first,last,'lossless neutral return differs');
  if(allowed)assert(changedPixels>=1000,'wave is static or absent');
  return {status:'SOURCE_AND_EXPLICIT_TRANSFER_PASS',frames,maxProtectedDelta,changedPixels,conversion:'sRGB EOTF -> linear -> Rec.709 OETF; RGB transport is already converted before YUV matrix encoding',converterSha256:sha(fs.readFileSync(__dirname+'/colour.cjs'))};
}
async function decoded(p){
  const work=p.destinations.work,n=p.delivery.frames;sequence(work+'/decoded',n);const frames=[];let first,last,patchMaximum=0;
  const patches=p.kind==='colour'?JSON.parse(fs.readFileSync(work+'/patches.json')).patches:[];
  for(let i=1;i<=n;i++){
    const s=await rgba(file(work+'/source',i)),d=await rgba(file(work+'/decoded',i));assert.deepEqual([d.width,d.height],[s.width,s.height]);
    const display=colour.convert(d.data,colour.inverse),facts=mae(display,s.data);
    assert(facts.mae<=p.pixelContract.decodedSrgbMae,'display-equivalent decoded mean error exceeds limit');
    if(p.kind==='wave'){const arm=mae(display,s.data,null,[748,395,255,357],s.width);assert(arm.mae<=p.pixelContract.armSrgbMae,'decoded arm appearance differs');facts.arm=arm;}
    for(const patch of patches){for(let y=patch.box[1];y<patch.box[1]+patch.box[3];y++)for(let x=patch.box[0];x<patch.box[0]+patch.box[2];x++)for(let c=0;c<3;c++)patchMaximum=Math.max(patchMaximum,Math.abs(display[(y*d.width+x)*4+c]-patch.rgb[c]));}
    if(i===1)first=display;last=display;frames.push({frame:i,...facts,decodedSha256:sha(fs.readFileSync(file(work+'/decoded',i)))});
  }
  assert(patchMaximum<=p.pixelContract.patchSrgbMaxDelta,'known patch transfer conversion fails');
  const neutral=mae(last,first);assert(neutral.mae<=p.pixelContract.decodedReturnMae,'decoded neutral return differs');
  return {status:'COMMON_SRGB_DECODE_PASS_NOT_HUMAN_APPROVAL',frames,patchMaximum,neutral,signalComparison:'Decode Rec.709 RGB -> linear -> sRGB, then compare with original sRGB source.'};
}
async function main(){
  assert(['source','decoded'].includes(process.argv[2]));const kind=process.env.MELOTRAIL_PROOF_KIND;assert(['colour','wave'].includes(kind));
  assert.equal(process.env.MELOTRAIL_PACKET_FILE,BASE+'/packets/'+(kind==='colour'?'colour.json':'wave.json'));
  const p=JSON.parse(fs.readFileSync(process.env.MELOTRAIL_PACKET_FILE));assert.equal(p.kind,kind);
  const result=await (process.argv[2]==='source'?source:decoded)(p);
  fs.writeFileSync(path.join(p.destinations.work,process.argv[2]+'-pixels.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify({...result,frames:result.frames.length}));
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1;});
module.exports={rgba,png,mae,source,decoded};
