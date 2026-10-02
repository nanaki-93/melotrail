'use strict';
// Read-only diagnosis of the retained candidate after the full-range refusal.
// Does not repair inputs, launch video encoding, or bypass the blocked admission.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..');
const {createCanvas,loadImage}=require(path.join(root,'tools/video-motion/node_modules/@napi-rs/canvas'));
const production=require(path.join(root,'tools/video-motion/render.cjs'));
const {stateForFrame}=require(path.join(root,'tools/video-motion/scenery.cjs'));
(async()=>{
 const request=JSON.parse(fs.readFileSync(path.join(run,'render/motion-request.json'))),images=new Map();
 for(const [list,prefix] of [[request.preparedScene.layers,'layer'],[request.preparedScene.poses,'pose'],[request.preparedScene.masks,'mask']])for(const item of list)images.set(`${prefix}:${item.id}`,await loadImage(path.join(run,'render/project',item.image.artifact.relativePath)));
 const v=production.validateRequest(request);let joinError=null;try{production.validateSceneryJoins(v,images);}catch(e){joinError=e.message;}
 const capability=request.preparedScene.motionCapabilities.find(c=>c.targetId==='near-new-coverage'&&c.control==='TRANSLATE_X');const requiredTravel=3*480*1799/599;
 const facts={status:'BLOCKED',full60SecondRequiredNearTravel:requiredTravel,allowedNearTravel:-capability.minimum,missingCanvasPixels:requiredTravel+capability.minimum,minimumCanvasWidth:Math.ceil(1920+requiredTravel),proposedCanvasWidth:6264,proposedFilterMargin:6264-1920-requiredTravel,shortRangeTypedValidation:true,shortRangeExactOverlap:!joinError,joinError,videoRenders:0,videoEncodes:0,normalSpeedReview:false,diagnosticOnly:true};
 if(!joinError){
  const sheet=createCanvas(960,540*3),sx=sheet.getContext('2d');
  for(const [i,f] of [1170,1230,1469].entries()){
   const one={...request,frameRange:{startFrame:f,frameCount:1},...(f===1170?{}:{initialState:stateForFrame(v.scenery,request.seed,f)})};
   const c=production.renderFrame(production.validateRequest(one),images,f).canvas;fs.writeFileSync(path.join(run,`review/diagnostic-${f}.png`),c.toBuffer('image/png'),{flag:'wx'});sx.drawImage(c,0,i*540,960,540);
  }
  fs.writeFileSync(path.join(run,'review/blocked-scenery-diagnostics.png'),sheet.toBuffer('image/png'),{flag:'wx'});
 }
 fs.writeFileSync(path.join(run,'checks/blocker-diagnosis.json'),JSON.stringify(facts,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(facts));
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
