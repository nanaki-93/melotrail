'use strict';
// Deterministic placement of existing supplied layers; no generated or stretched artwork.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const repo='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..');
const prior=path.join(repo,'docs/pictures/video/evidence/VG4-05/scenery-20261002T100234Z');
const {createCanvas,loadImage}=require(path.join(repo,'tools/video-motion/node_modules/@napi-rs/canvas'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const pixels=(image,x,w)=>{const c=createCanvas(image.width,image.height);const ctx=c.getContext('2d');ctx.drawImage(image,0,0);return ctx.getImageData(x,0,w,1080);};
async function main(){
 const old=JSON.parse(fs.readFileSync(path.join(prior,'checks/preparation.json')));
 for(const p of [...old.sources,...old.art])assert.equal(sha(fs.readFileSync(p.path)),p.sha256,p.path);
 const sources=['near','nearNext'].map(role=>old.sources.find(p=>p.role===role));
 const images=await Promise.all(sources.map(p=>loadImage(fs.readFileSync(p.path))));
 assert.deepEqual(images.map(i=>[i.width,i.height]),[[5600,1080],[6200,1080]]);
 const prefix=pixels(images[0],0,4900);
 assert(Buffer.from(prefix.data).equals(Buffer.from(pixels(images[1],0,4900).data)),'Prior original overlap really agrees');
 const art=JSON.parse(fs.readFileSync(path.join(run,'admission.json'))).derivedAssetDirectory,outputs=[];
 for(const [i,image] of images.entries()){
  const c=createCanvas(6264,1080),ctx=c.getContext('2d');ctx.drawImage(image,0,0);ctx.putImageData(prefix,0,0);
  const file=path.join(art,i?'near-new-6264x1080.png':'near-old-6264x1080.png'),bytes=c.toBuffer('image/png');
  fs.writeFileSync(file,bytes,{flag:'wx'});
  const decoded=await loadImage(bytes);
  assert(Buffer.from(pixels(decoded,0,4900).data).equals(Buffer.from(prefix.data)),'Protected original decoded prefix');
  assert(Buffer.from(pixels(decoded,4956,image.width-4956).data).equals(Buffer.from(pixels(image,4956,image.width-4956).data)),'Existing far-right artwork preserved');
  const margin=pixels(decoded,image.width,6264-image.width).data;
  assert(margin.every((v,k)=>k%4!==3||v===0),'Additional margin is transparent, never counted as artwork');
  outputs.push({path:file,sha256:sha(bytes),bytes:bytes.length,width:6264,height:1080});
 }
 const report={...old,art:[...old.art,...outputs],inheritedPreparation:{path:path.join(prior,'checks/preparation.json'),sha256:sha(fs.readFileSync(path.join(prior,'checks/preparation.json')))},nearCorrection:{sources,outputs,sharedPrefixExclusive:4900,preservedNewArtFromX:4956,protectedDecodedDifferences:0,canvasWidth:6264,maximumTravelPixels:480*1799/599*3,remainingBoundPixels:6264-1920-480*1799/599*3,artCoverage:'Transparent margins are backed by existing painted planes and do not count as variety or painted coverage.',exactRenderedOverlap:'Pending production source checks'},scope:'Static-neutral main-window short join with 60-second source coverage. No action composition, left-pane replacement or full route.'};
 fs.writeFileSync(path.join(run,'checks/preparation.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx'});
 console.log(JSON.stringify({status:'PASS',derived:outputs,protectedDecodedDifferences:0}));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
