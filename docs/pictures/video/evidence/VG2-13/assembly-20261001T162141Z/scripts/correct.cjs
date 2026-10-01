'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict');
const {runtime,RUN,OWNER,sha,ERASE}=require('./assemble.cjs');
function isSkin(r,g,b){return r>80&&b>80&&r>g*1.1&&b>g*.9;}
function removeSkin(image){const data=new Uint8ClampedArray(image.data);let removed=0;for(let i=0;i<data.length;i+=4)if(data[i+3]&&isSkin(data[i],data[i+1],data[i+2])){data[i+3]=0;removed++;}return {...image,data,removed};}
async function main(){
 assert.deepEqual(process.argv.slice(2),['--prepare']);
 const {owner,createCanvas,loadImage,Path2D,getSource}=await runtime();
 const kit=JSON.parse(fs.readFileSync(RUN+'/checks/parts.json')),record=id=>kit.parts.find(p=>p.id===id);
 const atlas=createCanvas(2048,1280),ctx=atlas.getContext('2d'),regions={},diagnostics={};
 for(const [id,x]of [['forearm_separate',0],['cuff_wrist_overlap',200]]){
  const p=record(id),raw=await owner.rgba(fs.readFileSync(OWNER+'/'+p.receipt.destination));
  const cleaned=removeSkin(raw);assert(cleaned.removed>0,'failing-before contamination missing');
  const out=createCanvas(raw.width,raw.height),oc=out.getContext('2d'),im=oc.createImageData(raw.width,raw.height);im.data.set(cleaned.data);oc.putImageData(im,0,0);
  if(id==='forearm_separate'){oc.globalCompositeOperation='destination-in';oc.fillStyle='white';oc.fill(new Path2D('M 0 0 H 160 V 154 H 55 Q 33 144 25 126 Q 14 112 0 100 Z'));oc.globalCompositeOperation='source-over';}
  const checked=oc.getImageData(0,0,raw.width,raw.height);assert.equal(removeSkin({width:raw.width,height:raw.height,data:checked.data}).removed,0);
  ctx.drawImage(out,x,1100);regions[id]=[x,1100,raw.width,raw.height];diagnostics[id]={skinPixelsBefore:cleaned.removed,skinPixelsAfter:0};
 }
 const old=await loadImage(OWNER+'/'+record('revealed_torso_cabin_backing').receipt.destination);
 const base=createCanvas(1920,1080),b=base.getContext('2d');b.drawImage(old,0,0);
 const donor=createCanvas(1920,1080),d=donor.getContext('2d');d.drawImage(await loadImage(getSource(28)),0,0);d.drawImage(await loadImage(getSource(47)),0,0);
 // Restore the revealed cheek including its dark outline from existing source47,
 // strictly inside the removed-hand mask. No face painting or generated anatomy.
 b.save();b.clip(new Path2D(ERASE));b.clip(new Path2D('M 790 490 L 849 490 L 840 504 L 832 511 L 826 519 L 818 526 L 809 533 L 800 538 L 790 539 Z'));b.drawImage(donor,0,0);b.restore();
 const before=await owner.rgba(fs.readFileSync(OWNER+'/'+record('revealed_torso_cabin_backing').receipt.destination));
 const after=b.getImageData(0,0,1920,1080).data,expected=d.getImageData(0,0,1920,1080).data;
 for(const [x,y]of [[815,517],[820,515],[825,510]]){const i=(y*1920+x)*4;assert.notDeepEqual(Array.from(before.data.slice(i,i+4)),Array.from(expected.slice(i,i+4)),'missing failing-before cheek witness');assert.deepEqual(Array.from(after.slice(i,i+4)),Array.from(expected.slice(i,i+4)),'cheek must match supplied donor');}
 ctx.drawImage(base,0,0);regions.revealed_torso_cabin_backing=[0,0,1920,1080];
 const token=owner.acquire(OWNER);
 try{
  const receipt=owner.reserve(OWNER,token,'derivative','revealed_torso_cabin_backing','parts/corrected_parts_atlas_v2.png').receipt;
  const bytes=atlas.toBuffer('image/png');owner.createOutput(OWNER,token,receipt.id,bytes);
  const proof={receipt,sha256:sha(bytes),regions,diagnostics,cheekWitnesses:[[815,517],[820,515],[825,510]],sources:kit.parts.filter(p=>Object.keys(regions).includes(p.id)).map(p=>({path:p.receipt.destination,sha256:p.sha256})),scope:'One atlas file contains corrections for three already admitted parts; counts as the final derivative file (12/12). No new target or image call. Original derivatives and failed first static sheet remain immutable.'};
  fs.writeFileSync(RUN+'/checks/correction-atlas.json',JSON.stringify(proof,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(proof,null,2));
 }finally{owner.release(OWNER,token);}
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1});
module.exports={isSkin,removeSkin};
