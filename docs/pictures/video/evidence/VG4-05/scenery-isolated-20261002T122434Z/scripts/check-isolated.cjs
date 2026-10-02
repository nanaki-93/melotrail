'use strict';
// Proposed correction: each native invocation owns only one verification stage/frame.
// Not authorized to run by the exhausted preparation attempt.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const repo='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..'),mode=process.argv[2];
const {createCanvas,loadImage}=require(path.join(repo,'tools/video-motion/node_modules/@napi-rs/canvas'));
const production=require(path.join(repo,'tools/video-motion/render.cjs'));
const {stateForFrame}=require(path.join(repo,'tools/video-motion/scenery.cjs'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const samples=[0,600,1170,1229,1230,1231,1350,1469,1590,1799];
const save=(name,data)=>fs.writeFileSync(path.join(run,'checks',name+'.json'),JSON.stringify(data,null,2)+'\n',{flag:'wx'});
const read=name=>JSON.parse(fs.readFileSync(path.join(run,'checks',name+'.json')));
async function load(request,project){
 const images=new Map();
 for(const [list,prefix] of [[request.preparedScene.layers,'layer'],[request.preparedScene.poses,'pose'],[request.preparedScene.masks,'mask']])for(const item of list){
  const bytes=fs.readFileSync(path.join(project,item.image.artifact.relativePath));assert.equal(sha(bytes),item.image.artifact.sha256);images.set(`${prefix}:${item.id}`,await loadImage(bytes));
 }
 return images;
}
async function main(){
 const request=JSON.parse(fs.readFileSync(path.join(run,'render/motion-request.json')));
 const full=structuredClone(request);full.frameRange={startFrame:0,frameCount:300};full.scenery.camera={...full.scenery.camera,startFrame:0,durationFrames:1800,travelXPixels:480*1799/599};
 for(const plane of full.scenery.planes)for(const [i,section] of plane.sections.entries()){section.worldX=0;section.startFrame=i===0?0:1230;section.endFrameExclusive=i===0&&plane.sections.length>1?1230:1800;}
 const fullValid=production.validateRequest(full);
 const at=(f,n=1)=>production.validateRequest({...full,frameRange:{startFrame:f,frameCount:n},...(f?{initialState:stateForFrame(fullValid.scenery,full.seed,f-1)}:{})});
 if(mode==='coverage'){
  for(let start=0;start<1800;start+=300)assert.deepEqual(at(start,300).scenery.planes.map(p=>p.depthFactor),[1,2,3]);
  const bad=structuredClone(request);bad.scenery.camera.travelXPixels*=10;assert.throws(()=>production.validateRequest(bad));
  const wrong={...full,frameRange:{startFrame:300,frameCount:300},initialState:stateForFrame(fullValid.scenery,full.seed,300)};
  assert.throws(()=>production.validateRequest(wrong),/immediately previous/);
  const prep=read('preparation');assert(prep.coverage.minimumPaintedMargins.right>2);assert(prep.coverage.minimumPaintedMargins.right-250<0);
  save('isolated-coverage',{status:'PASS',seconds:60,frames:1800,depthFactors:[1,2,3],negativeChecks:['Excessive travel rejected','Current-frame continuation rejected','Old far painted extent fails 60s']});return;
 }
 if(mode==='prior-regression'){
  const prior=path.join(repo,'docs/pictures/video/evidence/VG4-05/scenery-20261002T100234Z');
  const old=JSON.parse(fs.readFileSync(path.join(prior,'render/motion-request.json')));
  const images=await load(old,path.join(prior,'render/project'));
  assert.throws(()=>production.validateSceneryJoins(production.validateRequest(old),images),/near-old-coverage.*do not match exactly/);
  save('isolated-prior-regression',{status:'PASS',negative:'Actual prior different-width near sections fail exact rendered overlap'});return;
 }
 if(mode==='collect'){
  const reports=['coverage','joins','changed-overlap','prior-regression'].map(n=>read('isolated-'+n));assert(reports.every(r=>r.status==='PASS'));
  const sheet=createCanvas(1280,1800),ctx=sheet.getContext('2d'),rows=[];
  for(const [i,f] of samples.entries()){
   const report=read('isolated-frame-'+f);assert.equal(report.status,'PASS');rows.push(report);
   const bytes=fs.readFileSync(path.join(run,'review',report.file));assert.equal(sha(bytes),report.sha256);
   const image=await loadImage(bytes);ctx.drawImage(image,i%2*640,Math.floor(i/2)*360,640,360);
   ctx.fillStyle='#101010';ctx.fillRect(i%2*640,Math.floor(i/2)*360,170,25);ctx.fillStyle='white';ctx.font='17px sans-serif';ctx.fillText(`t=${(f/30).toFixed(2)}s`,i%2*640+8,Math.floor(i/2)*360+19);
   await new Promise(resolve=>setImmediate(resolve));
  }
  fs.writeFileSync(path.join(run,'review/source-contact-sheet.png'),sheet.toBuffer('image/png'),{flag:'wx'});
  save('source-checks',{status:'PASS',coverageSeconds:60,exactViewportOverlap:true,sourceJoinFrames:reports[1].joins,sourceSamples:rows,negativeChecks:reports.flatMap(r=>r.negativeChecks||[r.negative]).filter(Boolean),scope:'Static neutral and main window only. Human motion review and moving action support remain pending.'});return;
 }
 const images=await load(request,path.join(run,'render/project')),valid=production.validateRequest(request);
 if(mode==='joins'){
  // The trajectory declares the same handoff in every chunk. Check it once, exactly.
  production.validateSceneryJoins(at(1200,300),images);
  await new Promise(resolve=>setImmediate(resolve));
  production.validateSceneryJoins(valid,images);
  save('isolated-joins',{status:'PASS',joins:valid.scenery.joins.map(j=>({plane:j.planeId,frame:j.boundaryFrame,sampleFrames:j.sampleFrames}))});return;
 }
 if(mode==='changed-overlap'){
  const altered=createCanvas(5600,1080),ctx=altered.getContext('2d');ctx.drawImage(images.get('layer:far-new'),0,0);ctx.fillStyle='#000';ctx.fillRect(1700,400,25,25);images.set('layer:far-new',altered);
  assert.throws(()=>production.validateSceneryJoins(valid,images),/do not match exactly/);
  save('isolated-changed-overlap',{status:'PASS',negative:'Visible changed overlap rejected'});return;
 }
 assert.equal(mode,'frame');const f=Number(process.argv[3]);assert(samples.includes(f));
 const rendered=production.renderFrame(at(f),images,f).canvas,rgb=rendered.getContext('2d').getImageData(0,0,1920,1080).data;
 const comparison=createCanvas(1920,1080),ctx=comparison.getContext('2d');ctx.drawImage(images.get('layer:finished-scene'),0,0);const pp=ctx.getImageData(0,0,1920,1080).data;
 ctx.clearRect(0,0,1920,1080);ctx.drawImage(await loadImage(path.join(repo,'docs/pictures/video/tabi-assets/scenario/scenery-20261002T100234Z/main-window-aperture.png')),0,0);const mask=ctx.getImageData(0,0,1920,1080).data;
 let protectedDifferences=0,transparent=0;
 for(let i=0;i<1920*1080;i++){if(rgb[i*4+3]!==255)transparent++;if(mask[i*4+3]===0)for(let k=0;k<4;k++)if(rgb[i*4+k]!==pp[i*4+k])protectedDifferences++;}
 assert.equal(protectedDifferences,0,`Outside aperture frame ${f}`);assert.equal(transparent,0);
 const file=`source-sample-${String(f).padStart(4,'0')}.png`,bytes=rendered.toBuffer('image/png');fs.writeFileSync(path.join(run,'review',file),bytes,{flag:'wx'});
 save('isolated-frame-'+f,{status:'PASS',frame:f,file,sha256:sha(bytes),protectedDifferences,transparent});
}
main().then(()=>console.log('PASS '+mode)).catch(e=>{console.error(e.stack);process.exitCode=1;});
