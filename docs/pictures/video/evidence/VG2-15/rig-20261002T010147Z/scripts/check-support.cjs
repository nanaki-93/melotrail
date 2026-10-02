'use strict';
// Inspect alpha/UV geometry from Blender's evaluated data; no scene raster/render.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const RUN=path.resolve(__dirname,'..'),REPO=path.resolve(RUN,'../../../../../..');
const {createCanvas,loadImage}=require(path.join(REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function transform(quad,crop,point){
  const u=(point[0]-crop[0])/crop[2],v=(point[1]-crop[1])/crop[3];
  return [quad[0][0]+u*(quad[1][0]-quad[0][0])+v*(quad[3][0]-quad[0][0]),quad[0][1]+u*(quad[1][1]-quad[0][1])+v*(quad[3][1]-quad[0][1])];
}
function alphaAt(im,quad,crop,point){
  const dx=point[0]-quad[0][0],dy=point[1]-quad[0][1],a=quad[1][0]-quad[0][0],b=quad[3][0]-quad[0][0],c=quad[1][1]-quad[0][1],d=quad[3][1]-quad[0][1],det=a*d-b*c;
  assert(det>0,'inverted or collapsed mesh');
  const u=(dx*d-b*dy)/det,v=(a*dy-dx*c)/det;
  if(u<0||v<0||u>=1||v>=1)return 0;
  const x=Math.floor(crop[0]+u*crop[2]),y=Math.floor(crop[1]+v*crop[3]);
  return im.data[(y*im.width+x)*4+3];
}
async function load(spec){
  const result={};
  for(const [id,t]of Object.entries(spec.textures)){
    const bytes=fs.readFileSync(path.join(REPO,t.path));assert.equal(sha(bytes),t.sha256,'changed texture');
    const im=await loadImage(bytes),c=createCanvas(im.width,im.height),ctx=c.getContext('2d');ctx.drawImage(im,0,0);
    result[id]={width:im.width,height:im.height,data:ctx.getImageData(0,0,im.width,im.height).data};
  }
  return result;
}
function checkFrame(spec,images,frame,diagnosticOnly=false){
  assert.deepEqual(Object.keys(frame.parts).sort(),Object.keys(spec.parts).sort(),'missing part');
  const active=Object.entries(spec.parts).filter(([id,p])=>!['background','protected'].includes(id)&&frame.parts[id].visible);
  for(const [id,p]of active){
    const joint={shoulder:'Shoulder',elbow:'Elbow',wrist:'Wrist','shoulder-cover':'Shoulder','elbow-cover':'Elbow'}[p.control];
    const actual=transform(frame.parts[id].quad,p.crop,p.anchor),expected=frame.anchors[joint];
    assert(Math.hypot(actual[0]-expected[0],actual[1]-expected[1])<=spec.limits.jointErrorPixels,'part-to-joint misregistration: '+id);
  }
  function union(point){let transparent=1;for(const[id,p]of active)transparent*=1-alphaAt(images[p.texture],frame.parts[id].quad,p.crop,point)/255;return 255*(1-transparent);}
  const holes=[];
  for(const [joint,point]of Object.entries(frame.anchors))for(let y=-6;y<=6;y++)for(let x=-6;x<=6;x++)if(union([point[0]+x,point[1]+y])<spec.limits.minimumJointAlpha)holes.push({joint,x,y});
  const p=spec.parts[frame.controls.hand+'-front'],q=frame.parts[frame.controls.hand+'-front'].quad,s=spec.wristSpecs[frame.controls.hand];
  const entry=transform(q,p.crop,s.entry);
  for(let y=-2;y<=2;y++)for(let x=-2;x<=2;x++)if(union([entry[0]+x,entry[1]+y])<240)holes.push({joint:'hand-entry',x,y});
  // Preserve a finite corridor over both continuous sleeve segments, not just pivots.
  const jointPoints={...frame.anchors,HandEntry:entry};
  for(const [from,to]of [['Shoulder','Elbow'],['Elbow','Wrist'],['Wrist','HandEntry']]){
    const a=jointPoints[from],b=jointPoints[to],len=Math.hypot(b[0]-a[0],b[1]-a[1]),normal=[-(b[1]-a[1])/len,(b[0]-a[0])/len];
    for(let n=0;n<=20;n++)for(let w=-4;w<=4;w++){
      const point=[a[0]+(b[0]-a[0])*n/20+normal[0]*w,a[1]+(b[1]-a[1])*n/20+normal[1]*w];
      if(union(point)<240)holes.push({joint:from+'-'+to,n,w});
    }
  }
  if(!diagnosticOnly)assert.equal(holes.length,0,'full attachment/limb corridor holes at frame '+frame.frame+': '+JSON.stringify(holes.slice(0,4)));
  return {frame:frame.frame,jointRegionHoles:holes.length,handEntry:entry,holes};
}
function check(spec,images,proof,boundaries){
  assert.equal(proof.frames.length,150);assert.equal(proof.renderedFrames,0);
  const facts=[],first=proof.frames[0];let maxAnchorStep=0,maxAngleStep=0,edgePoints=0,exposedOffCanvas=0;
  for(let i=0;i<150;i++){
    const frame=proof.frames[i];assert.equal(frame.frame,i);facts.push(checkFrame(spec,images,frame));
    for(const [id,p]of Object.entries(spec.parts)){
      assert.deepEqual(frame.parts[id].uv,first.parts[id].uv,'UVs changed');
      if(!frame.parts[id].visible||!boundaries[p.texture])continue;
      const q=frame.parts[id].quad;
      for(const point of boundaries[p.texture]){
        if(point[0]<p.crop[0]||point[1]<p.crop[1]||point[0]>=p.crop[0]+p.crop[2]||point[1]>=p.crop[1]+p.crop[3])continue;
        const [x,y]=transform(q,p.crop,point);edgePoints++;
        if(x<0||y<0||x>=1920||y>=1080)exposedOffCanvas++;
      }
      const width=Math.hypot(q[1][0]-q[0][0],q[1][1]-q[0][1]);
      assert(Math.abs(width/p.crop[2]/p.scale-1)<=spec.limits.scaleRelativeError,'limb scale drift');
    }
    if(i){
      for(const name of ['Shoulder','Elbow','Wrist'])maxAnchorStep=Math.max(maxAnchorStep,Math.hypot(...frame.anchors[name].map((v,k)=>v-proof.frames[i-1].anchors[name][k])));
      frame.controls.angles.forEach((v,k)=>{maxAngleStep=Math.max(maxAngleStep,Math.abs(v-proof.frames[i-1].controls.angles[k]));});
    }
  }
  assert.equal(exposedOffCanvas,0,'texture contour clipped by camera');
  assert(maxAnchorStep<=spec.limits.maximumAnchorStep,'joint teleport');assert(maxAngleStep<=spec.limits.maximumAngleStep,'rotation snap');
  assert.deepEqual(proof.frames[0].parts,proof.frames[149].parts,'return differs');
  // A real discontinuity is recorded for review, not hidden behind a passing geometric metric.
  return {status:'GEOMETRIC_ALPHA_SUPPORT_PASS_NOT_RENDERED',frames:150,edgePoints,exposedOffCanvas,maxAnchorStep,maxAngleStep,facts,handDrawingChanges:[25,128],unproved:['Rendered colour/alpha/edge quality','Normal-speed hand/cuff substitutions','Inherited sleeve/body seam in movement']};
}
async function main(){
  const args=process.argv.slice(2);assert.deepEqual(args,['--check']);
  const spec=JSON.parse(fs.readFileSync(RUN+'/inputs/rig-v2.json'));
  const build=JSON.parse(fs.readFileSync(RUN+'/checks/build-geometry-v3.json')),reopen=JSON.parse(fs.readFileSync(RUN+'/checks/check-geometry-v3.json'));
  assert.deepEqual(build.frames,reopen.frames,'save/reopen drift');assert.equal(build.sceneSha256,reopen.sceneSha256);
  const images=await load(spec),boundaries=JSON.parse(fs.readFileSync(path.join(REPO,spec.boundaries.path)));
  const report=check(spec,images,reopen,boundaries);
  fs.writeFileSync(RUN+'/checks/support.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify({...report,facts:undefined}));
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1;});
module.exports={RUN,REPO,load,checkFrame,check,transform,alphaAt};
