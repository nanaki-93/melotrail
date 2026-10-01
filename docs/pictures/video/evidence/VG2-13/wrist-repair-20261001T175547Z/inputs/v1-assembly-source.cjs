'use strict';
// Static composition of imagegen-authored connected wrists. No model/rig/media dispatch.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const RUN=path.resolve(__dirname,'..'),REPO=path.resolve(RUN,'../../../../../..');
const previous=require(path.join(REPO,'docs/pictures/video/evidence/VG2-13/repair-20261001T172254Z/scripts/repair.cjs'));
const old=require(path.join(previous.ASSEMBLY,'scripts/assemble.cjs'));
const inherited=require(path.join(previous.PARENT,'check-preparation.cjs'));
const {createCanvas,loadImage,Path2D}=require(path.join(REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const specs={
  open:{file:'attempts/hand-wave-1.png',sha256:'c5f5bbd41dc709ba83eddf3a4594716f4c09ec300e76134d354ed7d150343d70',base:[550,1120],scale:.14,entry:[592,765],bridge:[520,716,185,72]},
  neutral:{file:'attempts/hand-neutral-1.png',sha256:'1ea8c932f5e7adec901faac367eb6a3ec95571351d8f40fb58fed7815b02a865',base:[620,1120],scale:.14,entry:[650,748],bridge:[587,680,170,67]}
};
function pin(file){return {path:path.relative(REPO,file),sha256:sha(fs.readFileSync(file))};}
function verifyInputs(){
  const a=JSON.parse(fs.readFileSync(path.join(RUN,'inputs/admission.json')));
  assert.deepEqual(a.priorConsumption,{imageAttempts:3,derivativeFiles:15,staticPasses:5});
  assert.deepEqual(a.newLimits,{imageAttempts:4,perHandTarget:2,derivativeFiles:8,staticPasses:3});
  for(const p of a.pins)assert.equal(sha(fs.readFileSync(inherited.safeFile(REPO,p.path))),p.sha256,`changed input ${p.path}`);
  const frozen=JSON.parse(fs.readFileSync(path.join(previous.RUN,'checks/kit-freeze.json')));
  for(const p of frozen.pins)assert.equal(sha(fs.readFileSync(inherited.safeFile(REPO,p.path))),p.sha256,`changed predecessor ${p.path}`);
  for(const s of Object.values(specs))assert.equal(sha(fs.readFileSync(inherited.safeFile(RUN,s.file))),s.sha256);
}
function attached(ctx,image,anchor,at,angle,scale=1){
  ctx.save();ctx.translate(...at);ctx.rotate(angle);ctx.scale(scale,scale);ctx.drawImage(image,-anchor[0],-anchor[1]);ctx.restore();
}
function jointPlacement(state,spec){
  const angle=state.forearmAngle*Math.PI/180-Math.atan2(-74,46)+state.wristAngle*Math.PI/180;
  return {angle,scale:spec.scale,at:state.wrist,base:spec.base};
}
function drawConnected(ctx,image,placement){attached(ctx,image,placement.base,placement.at,placement.angle,placement.scale);}
function transformSource(point,placement){return old.transform([point[0]-placement.base[0],point[1]-placement.base[1]],placement.at,placement.angle,placement.scale);}
async function inputs(){
  verifyInputs();const r=await previous.inputs();
  const record=JSON.parse(fs.readFileSync(path.join(previous.RUN,'checks/static-v5.json')));
  const atlas=await inherited.rgba(fs.readFileSync(path.join(REPO,record.part.path)));
  for(const [id,[x,y,w,h]]of Object.entries(record.regions))r.images[id]=previous.normalizedCrop(atlas,[x,y,x+w,y+h]);
  const wrists={};for(const [id,spec]of Object.entries(specs))wrists[id]=await loadImage(path.join(RUN,spec.file));
  return {...r,wrists};
}
async function compose(r){
  const images=Object.fromEntries(Object.entries(r.images).map(([id,im])=>[id,previous.canvasFrom(im)]));
  const upper=await loadImage(path.join(previous.PARENT,r.kit.upperSleeve.path)),foreground=await loadImage(path.join(REPO,r.kit.foreground.path));
  const upperScale=old.states[0].value.upperLength/old.distance([628,264],[635,984]);
  const allowed=createCanvas(1920,1080),a=allowed.getContext('2d');a.fillStyle='white';a.fill(new Path2D(old.ERASE));a.fillRect(748,395,255,357);
  const allow=a.getImageData(0,0,1920,1080).data,outputs=[],facts=[];
  for(const state of old.states){
    const p=state.value,layer=createCanvas(1920,1080),ctx=layer.getContext('2d');
    for(const [point,sx,size]of [[p.shoulder,0,38],[p.elbow,64,44]])ctx.drawImage(images.hidden_shoulder_elbow_overlap,sx,0,64,64,point[0]-size/2,point[1]-size/2,size,size);
    attached(ctx,upper,[628,264],p.shoulder,p.upperAngle*Math.PI/180-Math.atan2(720,7),upperScale);
    attached(ctx,images.forearm_separate,r.records.forearm_separate.anchors.elbow,p.elbow,p.forearmAngle*Math.PI/180-Math.atan2(-74,46));
    // The wrist, cuff rim and hand are a single authored image under one affine
    // transform. No independent hand scale, clipped wrist or hand-over-cuff pass.
    const spec=specs[p.hand],placement=jointPlacement(p,spec);
    drawConnected(ctx,r.wrists[p.hand],placement);
    const joint=ctx.getImageData(0,0,1920,1080).data;
    const entry=transformSource(spec.entry,placement);let holes=0;
    for(let y=-2;y<=2;y++)for(let x=-2;x<=2;x++)if(joint[((Math.round(entry[1])+y)*1920+Math.round(entry[0])+x)*4+3]<240)holes++;
    ctx.globalCompositeOperation='destination-out';ctx.drawImage(foreground,0,0);ctx.globalCompositeOperation='source-over';
    const scene=createCanvas(1920,1080),s=scene.getContext('2d');s.drawImage(images.revealed_torso_cabin_backing,0,0);s.drawImage(layer,0,0);
    s.save();s.clip(new Path2D('M 695 574 L 785 548 L 801 571 L 780 622 L 713 653 Z'));s.drawImage(images.revealed_torso_cabin_backing,0,0);s.restore();
    const decoded=s.getImageData(0,0,1920,1080).data;let outsideChanges=0,nonOpaque=0;
    for(let i=0;i<decoded.length;i+=4){if(decoded[i+3]!==255)nonOpaque++;if(!allow[i+3]&&[0,1,2,3].some(k=>decoded[i+k]!==r.sources[48].data[i+k]))outsideChanges++;}
    facts.push({state:state.id,placement,wristEntry:entry,wristEntryHoles:holes,outsideChanges,nonOpaque,rgbaSha256:sha(Buffer.from(decoded))});outputs.push({id:state.id,canvas:scene});
  }
  return {outputs,facts};
}
function sheet(outputs,reference){
  const canvas=createCanvas(3200,2580),ctx=canvas.getContext('2d');ctx.fillStyle='#f4eddf';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.font='bold 28px sans-serif';ctx.fillStyle='#222';
  const panels=[{id:'REFERENCE 48 — unchanged',canvas:previous.canvasFrom(reference)},outputs[0],outputs[1],outputs[3]];
  for(let i=0;i<4;i++){const x=i%2*1600,y=Math.floor(i/2)*940;ctx.fillText(panels[i].id+(i?' — connected wrist candidate':''),x+24,y+32);ctx.drawImage(panels[i].canvas,x,y+40,1600,900);}
  for(let i=0;i<4;i++){const x=i*800;ctx.fillText(outputs[i].id+' — wrist enters the cuff',x+20,1925);ctx.drawImage(outputs[i].canvas,735,395,270,365,x+60,1940,450,608.333333333);}
  return canvas;
}
async function prepare(version){
  assert(['v1','v2','v3'].includes(version));verifyInputs();
  const reserved=fs.readdirSync(path.join(RUN,'journal')).filter(n=>n.startsWith('static-'));
  assert(reserved.length<3,'static allowance exhausted');
  const file=`review/connected-wrists-${version}.png`;inherited.safeFile(RUN,file,true);
  const receipt={kind:'staticPass',ordinal:6+reserved.length,version,destination:file,scope:'Four assembled poses and one contact sheet; no media.'};
  fs.writeFileSync(path.join(RUN,`journal/static-${receipt.ordinal}-${version}.json`),JSON.stringify(receipt,null,2)+'\n',{flag:'wx'});
  const r=await inputs(),result=await compose(r);
  fs.writeFileSync(path.join(RUN,file),sheet(result.outputs,r.sources[48]).toBuffer('image/png'),{flag:'wx'});
  const record={version,review:pin(path.join(RUN,file)),specs,facts:result.facts,appearance:'UNREVIEWED',limitations:['Four stills only; no rig/motion approval.','The two generated wrist drawings retain their raw alpha and differ in cuff pixels; transition/cloth consistency remains a later moving proof.']};
  fs.writeFileSync(path.join(RUN,`checks/static-${version}.json`),JSON.stringify(record,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify(record,null,2));
  assert(result.facts.every(f=>f.outsideChanges===0&&f.nonOpaque===0&&f.wristEntryHoles===0),'static technical failure; retained output is not a pass');
}
if(require.main===module)prepare(process.argv[2]).catch(e=>{console.error(e);process.exitCode=1;});
module.exports={RUN,REPO,specs,inputs,compose,attached,jointPlacement,drawConnected,transformSource,verifyInputs};
