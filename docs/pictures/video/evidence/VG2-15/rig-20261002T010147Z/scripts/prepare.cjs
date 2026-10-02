'use strict';
// Texture packing and geometric witnesses only. No new artwork or frame rendering.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const RUN=path.resolve(__dirname,'..'),REPO=path.resolve(RUN,'../../../../../..');
const WRIST=path.join(REPO,'docs/pictures/video/evidence/VG2-13/wrist-repair-20261001T175547Z');
const current=require(WRIST+'/scripts/assemble-wrists.cjs');
const old=require(path.join(REPO,'docs/pictures/video/evidence/VG2-13/assembly-20261001T162141Z/scripts/assemble.cjs'));
const previous=require(path.join(REPO,'docs/pictures/video/evidence/VG2-13/repair-20261001T172254Z/scripts/repair.cjs'));
const {createCanvas,loadImage,Path2D}=require(path.join(REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const pin=f=>({path:path.relative(REPO,f),sha256:sha(fs.readFileSync(f))});
function canvas(im){return previous.canvasFrom(im);}
function alphaBoundary(im){
  const points=[];
  // Every boundary of alpha > 8, not just a rectangle or joint-centre sample.
  const alpha=(x,y)=>x>=0&&y>=0&&x<im.width&&y<im.height?im.data[(y*im.width+x)*4+3]:0;
  for(let y=0;y<im.height;y++)for(let x=0;x<im.width;x++)if(alpha(x,y)>8 && [[-1,0],[1,0],[0,-1],[0,1]].some(([dx,dy])=>alpha(x+dx,y+dy)<=8))points.push([x+.5,y+.5]);
  return points;
}
async function prepare(){
  const r=await current.inputs(),parts={},textures={};
  const write=(id,c)=>{const f=path.join(RUN,'textures',id+'.png');fs.writeFileSync(f,c.toBuffer('image/png'),{flag:'wx'});return {...pin(f),size:[c.width,c.height]};};
  for(const id of ['revealed_torso_cabin_backing','forearm_separate','hidden_shoulder_elbow_overlap'])textures[id]=write(id,canvas(r.images[id]));
  for(const [id,file]of Object.entries({upper:'parts/upper_sleeve_candidate_v1.png'})){
    const f=path.join(previous.PARENT,file),im=await loadImage(f);textures[id]={...pin(f),size:[im.width,im.height]};
  }
  for(const hand of ['neutral','open'])for(const layer of ['front','overlap']){
    const f=path.join(WRIST,'parts',hand+'-'+layer+'.png'),im=await loadImage(f);textures[hand+'-'+layer]={...pin(f),size:[im.width,im.height]};
  }
  const allowed=createCanvas(1920,1080),a=allowed.getContext('2d');a.fillStyle='white';a.fill(new Path2D(old.ERASE));a.fillRect(748,395,255,357);
  textures.allowed=write('allowed',allowed);
  const foreground=await loadImage(path.join(REPO,r.kit.foreground.path));
  const mask=createCanvas(1920,1080),m=mask.getContext('2d');m.fillStyle='white';m.fillRect(0,0,1920,1080);m.globalCompositeOperation='destination-out';m.drawImage(allowed,0,0);m.globalCompositeOperation='source-over';m.drawImage(foreground,0,0);m.fill(new Path2D('M 695 574 L 785 548 L 801 571 L 780 622 L 713 653 Z'));
  textures.occlusion=write('occlusion',mask);
  const front=canvas(r.images.revealed_torso_cabin_backing),f=front.getContext('2d');f.globalCompositeOperation='destination-in';f.drawImage(mask,0,0);textures.protected=write('protected',front);
  const add=(id,texture,control,anchor,scale,sourceAngle,depth,crop,hand)=>{
    parts[id]={texture,control,anchor,scale,sourceAngle,depth,crop:crop||[0,0,...textures[texture].size],hand:hand??null};
  };
  add('background','revealed_torso_cabin_backing','fixed',[0,0],1,0,0);
  add('shoulder-cover','hidden_shoulder_elbow_overlap','shoulder-cover',[32,32],38/64,0,10,[0,0,64,64]);
  add('elbow-cover','hidden_shoulder_elbow_overlap','elbow-cover',[96,32],44/64,0,20,[64,0,64,64]);
  add('upper','upper','shoulder',[628,264],Math.hypot(45,76)/Math.hypot(7,720),Math.atan2(720,7),30);
  add('forearm','forearm_separate','elbow',r.records.forearm_separate.anchors.elbow,1,Math.atan2(-74,46),50);
  for(const hand of ['neutral','open'])for(const layer of ['front','overlap'])add(hand+'-'+layer,hand+'-'+layer,'wrist',current.specs[hand].base,.14,Math.atan2(-74,46),layer==='front'?60:40,null,hand);
  add('protected','protected','fixed',[0,0],1,0,100);
  const spec={schema:'private-vg2-15-cutout-proof-1',textures,parts,dimensions:[1920,1080],frames:150,fps:30,shoulder:[772,619],lengths:[Math.hypot(45,76),Math.hypot(46,-74)],
    limits:{shoulder:[15,60],forearm:[-90,-48],wrist:[-12,12],jointErrorPixels:.001,scaleRelativeError:.00001,supportAlpha:8,minimumJointAlpha:240,minimumJointHalfWidth:2,minimumSeamHalfWidth:6,maximumAnchorStep:6,maximumAngleStep:3},
    keys:[{frame:0,angles:[59.36,-79.4,0],hand:'neutral'},{frame:9,angles:[59.36,-79.4,0],hand:'neutral'},{frame:14,angles:[60,-80,0],hand:'neutral'},{frame:38,angles:[32,-65,0],hand:'open'},{frame:46,angles:[32,-65,-10],hand:'open'},{frame:60,angles:[32,-65,10],hand:'open'},{frame:76,angles:[32,-65,-10],hand:'open'},{frame:90,angles:[32,-65,10],hand:'open'},{frame:102,angles:[32,-65,0],hand:'open'},{frame:112,angles:[32,-65,0],hand:'open'},{frame:140,angles:[59.36,-79.4,0],hand:'neutral'},{frame:149,angles:[59.36,-79.4,0],hand:'neutral'}],
    handSwitchFrames:[25,128],wristSpecs:current.specs,
    alphaPolicy:'Straight source alpha; one emission/transparent mix per texture, ordered depths; front and overlap use complementary source masks. No fades. Protected backdrop restores foreground and fixed-pixel region.',
    neutralReference:pin(path.join(WRIST,'review/connected-wrists-v3.png')),
    limitations:['Two registered hand drawings switch once during lift and once during lower; cuff/hand transition quality requires moving review.','Inherited inner sleeve/body seam is unchanged.','Geometry/support evaluation is not Blender pixel/colour or moving approval.']};
  const boundaries={};
  for(const [id,t]of Object.entries(textures))if(!['allowed','occlusion','protected','revealed_torso_cabin_backing'].includes(id)){
    const im=await loadImage(path.join(REPO,t.path)),c=createCanvas(im.width,im.height),cx=c.getContext('2d');cx.drawImage(im,0,0);boundaries[id]=alphaBoundary({width:im.width,height:im.height,data:cx.getImageData(0,0,im.width,im.height).data});
  }
  fs.writeFileSync(path.join(RUN,'inputs/boundaries.json'),JSON.stringify(boundaries)+'\n',{flag:'wx'});
  spec.boundaries=pin(path.join(RUN,'inputs/boundaries.json'));
  spec.dependencies=[pin(__filename),pin(path.join(WRIST,'checks/kit-freeze.json')),pin(path.join(WRIST,'scripts/assemble-wrists.cjs')),r.kit.foreground];
  fs.writeFileSync(path.join(RUN,'inputs/rig.json'),JSON.stringify(spec,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify({status:'PACKED_UNCHANGED_TEXTURES',textures:Object.keys(textures).length,parts:Object.keys(parts).length,boundaryPoints:Object.values(boundaries).reduce((n,a)=>n+a.length,0)}));
}
if(require.main===module)prepare().catch(e=>{console.error(e);process.exitCode=1;});
module.exports={RUN,REPO,alphaBoundary};
