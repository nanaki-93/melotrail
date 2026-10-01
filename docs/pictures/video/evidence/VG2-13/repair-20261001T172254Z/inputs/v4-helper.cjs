'use strict';
// Standalone local matte/registration repair. No image provider, rig, media or app dispatch.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const REPO = path.resolve(__dirname, '../../../../../../..');
process.env.MELOTRAIL_REPO_ROOT = REPO;
const RUN = path.resolve(__dirname, '..');
const PARENT = path.join(REPO, 'docs/pictures/video/evidence/VG2-13/continuation-20261001-153813Z');
const ASSEMBLY = path.join(REPO, 'docs/pictures/video/evidence/VG2-13/assembly-20261001T162141Z');
const inherited = require(path.join(PARENT, 'check-preparation.cjs'));
const old = require(path.join(ASSEMBLY, 'scripts/assemble.cjs'));
const {createCanvas, loadImage, Path2D} = require(path.join(REPO, 'tools/video-motion/node_modules/@napi-rs/canvas'));
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const historical = Object.freeze({imageAttempts:3, derivativeFiles:12, staticPasses:2});
const limits = Object.freeze({imageCalls:0, derivativeFiles:4, staticPasses:2});
const registration = Object.freeze({cuffBase:[67,104], cuffScale:.82, handScales:{neutral:1,open:.82}, handOnTop:true});

function configuration(root=RUN) {
  assert(root === RUN || (process.env.NODE_TEST_CONTEXT && root.startsWith(fs.realpathSync(os.tmpdir()) + path.sep)), 'unowned repair root');
  const c = JSON.parse(fs.readFileSync(inherited.safeFile(root,'inputs/admission.json')));
  assert.equal(c.owner, path.relative(REPO,RUN));
  assert.deepEqual(c.historicalConsumption, historical, 'historical consumption changed');
  assert.deepEqual(c.additionalLimits, limits, 'repair allowance changed');
  assert.deepEqual(c.aggregateCeilings,{imageAttempts:16,derivativeFiles:16,staticPasses:5});
  assert.equal(c.repairAttempts,3);
  assert.equal(c.approval,'PENDING renewed VG2-14; rejection and all previous bytes remain authoritative');
  assert.equal(process.version,c.runtime.node);
  assert.equal(sha(fs.readFileSync(c.runtime.executable)),c.runtime.sha256,'Node pin changed');
  for (const pin of c.pins) assert.equal(sha(fs.readFileSync(inherited.safeFile(REPO,pin.path))),pin.sha256,`predecessor changed: ${pin.path}`);
  assert.deepEqual(inherited.audit(PARENT).counts,{attempt:3,derivative:12,staticPass:2});
  assert.equal(inherited.audit(PARENT).lockPresent,false,'historical writer unresolved');
  return c;
}
function audit(root=RUN) {
  configuration(root);
  const counts={derivative:0,staticPass:0}, destinations=new Set(), folded=new Set(), ordinals={derivative:[],staticPass:[]};
  for(const name of fs.readdirSync(path.join(root,'journal')).sort()) {
    assert.match(name,/^[a-f0-9]{32}\.json$/);
    const r=JSON.parse(fs.readFileSync(inherited.safeFile(root,`journal/${name}`)));
    assert.equal(name,r.id+'.json'); assert(['derivative','staticPass'].includes(r.kind),'unadmitted operation');
    assert.match(r.destination,r.kind==='derivative'?/^parts\/[a-z0-9_-]+\.png$/:/^review\/[a-z0-9_-]+\.png$/);
    const f=r.destination.normalize('NFC').toLowerCase(); assert(!folded.has(f),'reserved alias'); folded.add(f); destinations.add(r.destination);
    counts[r.kind]++; ordinals[r.kind].push(r.ordinal);
  }
  for(const [kind,cap,spent] of [['derivative',4,12],['staticPass',2,2]]) {
    assert(counts[kind]<=cap,'repair allowance exhausted');
    assert.deepEqual(ordinals[kind].sort((a,b)=>a-b),Array.from({length:counts[kind]},(_,i)=>spent+i+1),'reservation ordinals changed');
  }
  for(const dir of ['parts','review']) for(const name of fs.readdirSync(path.join(root,dir))) {
    inherited.safeFile(root,`${dir}/${name}`); assert(destinations.has(`${dir}/${name}`),`unreserved output ${dir}/${name}`);
  }
  return {additional:counts,aggregate:{imageAttempts:3,derivativeFiles:12+counts.derivative,staticPasses:2+counts.staticPass},lockPresent:fs.existsSync(path.join(root,'writer.lock'))};
}
function acquire(root=RUN) {
  audit(root); fs.mkdirSync(path.join(root,'writer.lock'));
  const token=crypto.randomBytes(16).toString('hex');
  fs.writeFileSync(path.join(root,'writer.lock/token'),token,{flag:'wx'}); return token;
}
function release(root,token) {
  assert.equal(fs.readFileSync(inherited.safeFile(root,'writer.lock/token'),'utf8'),token,'not lock owner');
  fs.unlinkSync(path.join(root,'writer.lock/token')); fs.rmdirSync(path.join(root,'writer.lock'));
}
function reserve(root,token,kind,destination) {
  assert.equal(fs.readFileSync(inherited.safeFile(root,'writer.lock/token'),'utf8'),token,'writer lock required');
  assert(['derivative','staticPass'].includes(kind),'unadmitted operation');
  assert.match(destination,kind==='derivative'?/^parts\/[a-z0-9_-]+\.png$/:/^review\/[a-z0-9_-]+\.png$/);
  const state=audit(root), count=state.additional[kind];
  assert(count<(kind==='derivative'?4:2),'repair allowance exhausted');
  inherited.safeFile(root,destination,true);
  for(const name of fs.readdirSync(path.join(root,'journal'))) {
    const r=JSON.parse(fs.readFileSync(path.join(root,'journal',name)));
    assert.notEqual(r.destination.normalize('NFC').toLowerCase(),destination.normalize('NFC').toLowerCase(),'reserved alias');
  }
  const r={id:crypto.randomBytes(16).toString('hex'),kind,ordinal:(kind==='derivative'?12:2)+count+1,destination};
  const fd=fs.openSync(path.join(root,'journal',r.id+'.json'),'wx',0o600);
  try {fs.writeSync(fd,JSON.stringify(r)+'\n');fs.fsyncSync(fd);} finally {fs.closeSync(fd);}
  return r;
}
function publish(root,token,receipt,bytes) {
  assert.equal(fs.readFileSync(inherited.safeFile(root,'writer.lock/token'),'utf8'),token,'writer lock required');
  audit(root);
  const recorded=JSON.parse(fs.readFileSync(inherited.safeFile(root,`journal/${receipt.id}.json`))); assert.deepEqual(recorded,receipt);
  const file=inherited.safeFile(root,receipt.destination,true);
  const fd=fs.openSync(file,fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_WRONLY|fs.constants.O_NOFOLLOW,0o600);
  try {fs.writeFileSync(fd,bytes);fs.fsyncSync(fd);} finally {fs.closeSync(fd);}
  return {path:path.relative(REPO,file),sha256:sha(bytes)};
}
const isSkin=(r,g,b)=>r>80&&b>80&&r>g*1.1&&b>g*.9;
// Warm brown is also the supplied hand's dark outline. Only bright ochre
// fabric is excluded here; dropping every brown pixel makes the edge ragged.
const isGold=(r,g,b)=>r>120&&g>70&&r>g*1.2&&g>b*1.4;
const BACKING_REPAIR='M 780 685 Q 797 679 811 696 Q 824 716 825 742 L 789 743 Q 790 727 777 716 Z';
function normalizedCrop(source,crop) {
  const [l,t,r,b]=crop,w=r-l,h=b-t,data=new Uint8ClampedArray(w*h*4);
  for(let y=0;y<h;y++) for(let x=0;x<w;x++) {
    const i=(y*w+x)*4,j=((y+t)*source.width+x+l)*4; data.set(source.data.subarray(j,j+4),i);
    if(data[i+3]<=8)data[i+3]=0; else if(data[i+3]>=245)data[i+3]=255;
  }
  return {width:w,height:h,data};
}
function skinComponent(image,seed) {
  const mask=new Uint8Array(image.width*image.height),visited=new Uint8Array(mask.length),queue=[seed[1]*image.width+seed[0]];
  for(let q=0;q<queue.length;q++) {
    const p=queue[q]; if(visited[p])continue; visited[p]=1;
    const i=p*4; if(!image.data[i+3]||!isSkin(...image.data.subarray(i,i+3)))continue;
    mask[p]=1; const x=p%image.width,y=Math.floor(p/image.width);
    for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++) {
      const nx=x+dx,ny=y+dy;if(nx>=0&&nx<image.width&&ny>=0&&ny<image.height&&!visited[ny*image.width+nx])queue.push(ny*image.width+nx);
    }
  }
  assert(mask.some(Boolean),'missing hand seed'); return mask;
}
function expand(mask,width,height,radius) {
  const out=new Uint8Array(mask.length);
  for(let p=0;p<mask.length;p++)if(mask[p]) {
    const x=p%width,y=Math.floor(p/width);
    for(let dy=-radius;dy<=radius;dy++)for(let dx=-radius;dx<=radius;dx++) {
      const nx=x+dx,ny=y+dy;if(nx>=0&&nx<width&&ny>=0&&ny<height&&dx*dx+dy*dy<=radius*radius)out[ny*width+nx]=1;
    }
  }
  return out;
}
function repairHand(source,crop,seed) {
  const raw=normalizedCrop(source,crop),skin=skinComponent(raw,[seed[0]-crop[0],seed[1]-crop[1]]),support=expand(skin,raw.width,raw.height,4);
  const data=new Uint8ClampedArray(raw.data);
  for(let p=0;p<support.length;p++) {
    const i=p*4;
    if(!support[p] || (!skin[p] && (isSkin(...data.subarray(i,i+3)) || isGold(...data.subarray(i,i+3)))))data[i+3]=0;
  }
  return {...raw,data,skin,support,crop};
}
function checkHand(hand,source,expected) {
  const raw=normalizedCrop(source,expected.crop); let clippedSkin=0,clippedOutline=0,changedOpaqueRgb=0,foreignSkin=0,gold=0;
  for(let p=0;p<expected.skin.length;p++) {
    const i=p*4;
    if(expected.skin[p]&&hand.data[i+3]<raw.data[i+3])clippedSkin++;
    // The selected 4px rim retains supplied dark outline pixels, including finger valleys.
    if(!expected.skin[p]&&expected.support[p]&&raw.data[i+3]&&!isSkin(...raw.data.subarray(i,i+3))&&!isGold(...raw.data.subarray(i,i+3))&&hand.data[i+3]<raw.data[i+3])clippedOutline++;
    if(hand.data[i+3]===255&&[0,1,2].some(k=>hand.data[i+k]!==raw.data[i+k]))changedOpaqueRgb++;
    if(hand.data[i+3]&&isSkin(...hand.data.subarray(i,i+3))&&!expected.skin[p])foreignSkin++;
    if(hand.data[i+3]&&isGold(...hand.data.subarray(i,i+3)))gold++;
  }
  const facts={clippedSkin,clippedOutline,changedOpaqueRgb,foreignSkin,gold};
  assert.deepEqual(facts,{clippedSkin:0,clippedOutline:0,changedOpaqueRgb:0,foreignSkin:0,gold:0},'hand contour/outline or source RGB mismatch');return facts;
}
function repairCuff(raw,hand,crop) {
  const data=new Uint8ClampedArray(raw.data);let removed=0;
  for(let y=0;y<raw.height;y++)for(let x=0;x<raw.width;x++) {
    const i=(y*raw.width+x)*4,hx=x+crop[0]-hand.crop[0],hy=y+crop[1]-hand.crop[1];
    const handSupport=hx>=0&&hx<hand.width&&hy>=0&&hy<hand.height&&hand.support[hy*hand.width+hx];
    if(data[i+3]&&(handSupport||isSkin(...data.subarray(i,i+3)))) {data[i+3]=0;removed++;}
  }
  return {...raw,data,removed};
}
function cuffDebris(cuff,hand,crop) {
  let count=0;
  for(let y=0;y<cuff.height;y++)for(let x=0;x<cuff.width;x++) {
    const i=(y*cuff.width+x)*4,hx=x+crop[0]-hand.crop[0],hy=y+crop[1]-hand.crop[1];
    if(cuff.data[i+3]&&hx>=0&&hx<hand.width&&hy>=0&&hy<hand.height&&hand.support[hy*hand.width+hx]&&!isGold(...cuff.data.subarray(i,i+3)))count++;
  }
  return count;
}
function repairForearm(raw) {
  const skin=Uint8Array.from({length:raw.width*raw.height},(_,p)=>raw.data[p*4+3]&&isSkin(...raw.data.subarray(p*4,p*4+3))?1:0);
  const otherHand=expand(skin,raw.width,raw.height,3),data=new Uint8ClampedArray(raw.data);
  for(let p=0;p<otherHand.length;p++)if(otherHand[p])data[p*4+3]=0;
  return {...raw,data};
}
function canvasFrom(image) {
  const c=createCanvas(image.width,image.height),ctx=c.getContext('2d'),d=ctx.createImageData(image.width,image.height);d.data.set(image.data);ctx.putImageData(d,0,0);return c;
}
async function inputs() {
  configuration(); await inherited.auditFixed(PARENT);
  const kit=JSON.parse(fs.readFileSync(path.join(ASSEMBLY,'checks/parts.json'))),preparation=JSON.parse(fs.readFileSync(path.join(PARENT,'preparation.json')));
  const sources={}; for(const n of [45,47,48])sources[n]=await inherited.rgba(fs.readFileSync(path.join(REPO,preparation.sources.find(p=>p.path.includes(`/train-actions/${n}-`)).path)));
  const images={},records=Object.fromEntries(kit.parts.map(p=>[p.id,p]));
  for(const p of kit.parts)images[p.id]=await inherited.rgba(fs.readFileSync(path.join(PARENT,p.receipt.destination)));
  const correction=JSON.parse(fs.readFileSync(path.join(ASSEMBLY,'checks/correction-atlas.json'))),atlas=await inherited.rgba(fs.readFileSync(path.join(PARENT,correction.receipt.destination)));
  for(const [id,rect]of Object.entries(correction.regions))images[id]=normalizedCrop(atlas,[rect[0],rect[1],rect[0]+rect[2],rect[1]+rect[3]]);
  return {kit,preparation,sources,images,records};
}
async function repairedParts() {
  const r=await inputs();
  const hands={hand_neutral:repairHand(r.sources[45],r.records.hand_neutral.crop,[816,547]),hand_wrist_beat:repairHand(r.sources[47],r.records.hand_wrist_beat.crop,[890,518])};
  const proof={hands:{},cuff:{}};
  for(const [id,hand]of Object.entries(hands)) {
    const before=r.images[id],source=r.sources[id==='hand_neutral'?45:47];
    let failingBefore;try {checkHand(before,source,hand);}catch(e){failingBefore=e.message;}
    assert(failingBefore,'missing old contour regression');
    proof.hands[id]={source:r.records[id].source,oldFails:failingBefore,new:checkHand(hand,source,hand)};
    r.images[id]=hand;
  }
  const cuffRaw=await inherited.rgba(fs.readFileSync(path.join(PARENT,r.records.cuff_wrist_overlap.receipt.destination)));
  const cuff=repairCuff(cuffRaw,hands.hand_wrist_beat,r.records.cuff_wrist_overlap.crop);
  proof.cuff={orphanOutlineBefore:cuffDebris(r.images.cuff_wrist_overlap,hands.hand_wrist_beat,r.records.cuff_wrist_overlap.crop),orphanOutlineAfter:cuffDebris(cuff,hands.hand_wrist_beat,r.records.cuff_wrist_overlap.crop),removed:cuff.removed};
  assert(proof.cuff.orphanOutlineBefore>0);assert.equal(proof.cuff.orphanOutlineAfter,0);r.images.cuff_wrist_overlap=cuff;
  const rawForearm=await inherited.rgba(fs.readFileSync(path.join(PARENT,r.records.forearm_separate.receipt.destination)));
  const forearm=repairForearm(rawForearm);let restored=0;
  for(let i=0;i<forearm.data.length;i+=4)if(forearm.data[i+3]>r.images.forearm_separate.data[i+3]&&forearm.data[i+1]>forearm.data[i]&&forearm.data[i+1]>forearm.data[i+2])restored++;
  assert(restored>0,'missing old sleeve clipping witness');proof.forearm={restoredSuppliedGreenPixels:restored};r.images.forearm_separate=forearm;
  // The previous arm-erasure contour left a hard rectangular cloth tail at
  // the table. Restore only that declared overlap from the supplied cabin.
  // The planted other hand and its outline retain their existing pixels.
  const base=canvasFrom(r.images.revealed_torso_cabin_backing),b=base.getContext('2d');
  const cabinPin=r.preparation.sources.find(p=>p.path.includes('/28-breathing-'));
  const cabin=await inherited.rgba(fs.readFileSync(path.join(REPO,cabinPin.path)));
  b.save();b.clip(new Path2D(BACKING_REPAIR));b.drawImage(canvasFrom(cabin),0,0);b.restore();
  const contactCrop=[735,670,815,752],contact=normalizedCrop(r.sources[48],contactCrop);
  const planted=expand(skinComponent(contact,[759-contactCrop[0],718-contactCrop[1]]),contact.width,contact.height,3);
  const repaired=b.getImageData(0,0,1920,1080),before=r.images.revealed_torso_cabin_backing;
  let protectedContact=0;
  for(let y=0;y<contact.height;y++)for(let x=0;x<contact.width;x++)if(planted[y*contact.width+x]) {
    const i=((y+contactCrop[1])*1920+x+contactCrop[0])*4;
    repaired.data.set(before.data.subarray(i,i+4),i);protectedContact++;
  }
  b.putImageData(repaired,0,0);
  const witnesses=[[809,731],[811,732]];
  for(const [x,y]of witnesses) {
    const i=(y*1920+x)*4;assert.notDeepEqual([...before.data.subarray(i,i+4)],[...cabin.data.subarray(i,i+4)],'missing hard-tail witness');
    assert.deepEqual([...repaired.data.subarray(i,i+4)],[...cabin.data.subarray(i,i+4)],'tail must use exact cabin pixels');
  }
  proof.backing={mask:BACKING_REPAIR,source:cabinPin,protectedPlantedHandPixels:protectedContact,cabinWitnesses:witnesses};
  r.images.revealed_torso_cabin_backing={width:1920,height:1080,data:repaired.data};
  return {...r,proof};
}
function drawAttached(ctx,image,anchor,at,angle,scale=1) {
  ctx.save();ctx.translate(...at);ctx.rotate(angle);ctx.scale(scale,scale);ctx.drawImage(image,-anchor[0],-anchor[1]);ctx.restore();
}
async function compose(r) {
  const images=Object.fromEntries(Object.entries(r.images).map(([id,im])=>[id,canvasFrom(im)]));
  const upper=await loadImage(path.join(PARENT,r.kit.upperSleeve.path)),foreground=await loadImage(path.join(REPO,r.kit.foreground.path));
  const upperScale=old.states[0].value.upperLength/old.distance([628,264],[635,984]),srcUpperAngle=Math.atan2(720,7),srcFA=Math.atan2(-74,46);
  const allowed=createCanvas(1920,1080),a=allowed.getContext('2d');a.fillStyle='white';a.fill(new Path2D(old.ERASE));a.fillRect(748,415,247,337);const allow=a.getImageData(0,0,1920,1080).data;
  const outputs=[],checks=[];
  for(const state of old.states) {
    const p=state.value,layer=createCanvas(1920,1080),ctx=layer.getContext('2d');
    for(const [point,sx,size]of [[p.shoulder,0,38],[p.elbow,64,44]])ctx.drawImage(images.hidden_shoulder_elbow_overlap,sx,0,64,64,point[0]-size/2,point[1]-size/2,size,size);
    drawAttached(ctx,upper,[628,264],p.shoulder,p.upperAngle*Math.PI/180-srcUpperAngle,upperScale);
    const fa=p.forearmAngle*Math.PI/180-srcFA;
    drawAttached(ctx,images.forearm_separate,r.records.forearm_separate.anchors.elbow,p.elbow,fa);
    const cuffAngle=fa+p.wristAngle*Math.PI/180,cuff=r.records.cuff_wrist_overlap;
    const delta=[cuff.anchors.hand[0]-registration.cuffBase[0],cuff.anchors.hand[1]-registration.cuffBase[1]];
    const handAnchor=old.transform(delta,p.wrist,cuffAngle,registration.cuffScale),handId=p.hand==='neutral'?'hand_neutral':'hand_wrist_beat';
    drawAttached(ctx,images.cuff_wrist_overlap,registration.cuffBase,p.wrist,cuffAngle,registration.cuffScale);
    const handAngle=p.hand==='neutral'?cuffAngle-(old.states[0].value.forearmAngle*Math.PI/180-srcFA):cuffAngle;
    drawAttached(ctx,images[handId],r.records[handId].anchors.wrist,handAnchor,handAngle,registration.handScales[p.hand]);
    const data=ctx.getImageData(0,0,1920,1080).data,seams=[];
    for(const [joint,at]of Object.entries({shoulder:p.shoulder,elbow:p.elbow,forearmCuff:p.wrist,hand:handAnchor})) {
      let holes=0;for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++)if(data[((Math.round(at[1])+dy)*1920+Math.round(at[0])+dx)*4+3]<240)holes++;
      seams.push({joint,holes});
    }
    ctx.globalCompositeOperation='destination-out';ctx.drawImage(foreground,0,0);ctx.globalCompositeOperation='source-over';
    const scene=createCanvas(1920,1080),s=scene.getContext('2d');s.drawImage(images.revealed_torso_cabin_backing,0,0);s.drawImage(layer,0,0);
    s.save();s.clip(new Path2D('M 695 574 L 785 548 L 801 571 L 780 622 L 713 653 Z'));s.drawImage(images.revealed_torso_cabin_backing,0,0);s.restore();
    const decoded=s.getImageData(0,0,1920,1080).data;let outsideChanges=0,nonOpaque=0;
    for(let i=0;i<decoded.length;i+=4) {if(decoded[i+3]!==255)nonOpaque++;if(!allow[i+3]&&[0,1,2,3].some(k=>decoded[i+k]!==r.sources[48].data[i+k]))outsideChanges++;}
    checks.push({state:state.id,seams,outsideChanges,nonOpaque,rgbaSha256:sha(Buffer.from(decoded))});outputs.push({state:state.id,canvas:scene});
  }
  return {outputs,checks};
}
function reviewSheet(outputs,reference) {
  const sheet=createCanvas(3200,2580),s=sheet.getContext('2d');s.fillStyle='#f4eddf';s.fillRect(0,0,3200,2580);s.font='bold 28px sans-serif';
  const panels=[{state:'REFERENCE 48 — unchanged',canvas:canvasFrom(reference)},outputs[0],outputs[1],outputs[3]];
  for(let i=0;i<4;i++) {const x=i%2*1600,y=Math.floor(i/2)*940;s.fillStyle='#222';s.fillText(panels[i].state+(i?' — local repair candidate':''),x+24,y+32);s.drawImage(panels[i].canvas,x,y+40,1600,900);}
  for(let i=0;i<4;i++) {const x=i*800;s.fillStyle='#222';s.fillText(outputs[i].state+' — hand / cuff / arm',x+20,1925);s.drawImage(outputs[i].canvas,735,420,270,340,x+60,1940,500,629.6296296);}
  return sheet;
}
async function prepare(variant) {
  assert(['v3','v4'].includes(variant),'bounded variant required');const token=acquire();
  try {
    const partReceipt=reserve(RUN,token,'derivative',`parts/contour_atlas_${variant}.png`),passReceipt=reserve(RUN,token,'staticPass',`review/assembled_review_${variant}.png`);
    const r=await repairedParts(),atlas=createCanvas(2048,1280),a=atlas.getContext('2d'),regions={};
    const positions={revealed_torso_cabin_backing:[0,0],forearm_separate:[0,1100],cuff_wrist_overlap:[200,1100],hand_neutral:[400,1100],hand_wrist_beat:[520,1100]};
    for(const [id,[x,y]]of Object.entries(positions)) {const im=r.images[id];a.drawImage(canvasFrom(im),x,y);regions[id]=[x,y,im.width,im.height];}
    const part=publish(RUN,token,partReceipt,atlas.toBuffer('image/png'));
    const {outputs,checks}=await compose(r);const review=publish(RUN,token,passReceipt,reviewSheet(outputs,r.sources[48]).toBuffer('image/png'));
    const record={variant,part,regions,review,registration,proof:r.proof,checks,technicalPass:checks.every(c=>!c.outsideChanges&&!c.nonOpaque&&c.seams.every(s=>!s.holes)),appearance:'UNREVIEWED',scope:'Four static poses only. No rig, all-frame evaluation, hand-opening transition or moving approval.'};
    fs.writeFileSync(path.join(RUN,`checks/static-${variant}.json`),JSON.stringify(record,null,2)+'\n',{flag:'wx'});
    console.log(JSON.stringify(record,null,2));assert(record.technicalPass,'static support/protected-pixel failure; preserve candidate');
  } finally {release(RUN,token);}
}
if(require.main===module) {
  const args=process.argv.slice(2);
  (async()=>{if(args.length===1&&args[0]==='--check')console.log(JSON.stringify(audit(),null,2));else if(args.length===2&&args[0]==='--prepare')await prepare(args[1]);else throw Error('Use --check or --prepare v3/v4');})().catch(e=>{console.error(e);process.exitCode=1});
}
module.exports={RUN,PARENT,ASSEMBLY,registration,audit,acquire,release,reserve,publish,normalizedCrop,skinComponent,expand,repairHand,checkHand,repairCuff,cuffDebris,repairForearm,inputs,repairedParts,compose,canvasFrom};
