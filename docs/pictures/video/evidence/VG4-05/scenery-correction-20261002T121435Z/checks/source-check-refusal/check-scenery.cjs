'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..');
const {createCanvas,loadImage}=require(path.join(root,'tools/video-motion/node_modules/@napi-rs/canvas'));
const production=require(path.join(root,'tools/video-motion/render.cjs'));
const {stateForFrame}=require(path.join(root,'tools/video-motion/scenery.cjs'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
async function main(){
 const project=path.join(run,'render/project'),request=JSON.parse(fs.readFileSync(path.join(run,'render/motion-request.json'))),scene=request.preparedScene,images=new Map();
 for(const [list,prefix] of [[scene.layers,'layer'],[scene.poses,'pose'],[scene.masks,'mask']])for(const item of list){const bytes=fs.readFileSync(path.join(project,item.image.artifact.relativePath));assert.equal(sha(bytes),item.image.artifact.sha256);images.set(`${prefix}:${item.id}`,await loadImage(bytes));}
 const full=structuredClone(request);full.frameRange={startFrame:0,frameCount:300};full.scenery.camera={...full.scenery.camera,startFrame:0,durationFrames:1800,travelXPixels:480*1799/599};
 for(const plane of full.scenery.planes)for(const [i,sec] of plane.sections.entries()){sec.worldX=0;sec.startFrame=i===0?0:1230;sec.endFrameExclusive=i===0&&plane.sections.length>1?1230:1800;}
 const fullValid=production.validateRequest(full);
 function at(f,n=1){return production.validateRequest({...full,frameRange:{startFrame:f,frameCount:n},...(f?{initialState:stateForFrame(fullValid.scenery,full.seed,f)}:{})});}
 for(let start=0;start<1800;start+=300){const v=at(start,300);production.validateSceneryJoins(v,images);assert.deepEqual(v.scenery.planes.map(p=>p.depthFactor),[1,2,3]);}
 // Reproduce the actual prior failure, rather than weakening the equality check.
 const prior=path.join(root,'docs/pictures/video/evidence/VG4-05/scenery-20261002T100234Z');
 const priorRequest=JSON.parse(fs.readFileSync(path.join(prior,'render/motion-request.json'))),priorImages=new Map();
 for(const item of priorRequest.preparedScene.layers){const bytes=fs.readFileSync(path.join(prior,'render/project',item.image.artifact.relativePath));assert.equal(sha(bytes),item.image.artifact.sha256);priorImages.set(`layer:${item.id}`,await loadImage(bytes));}
 assert.throws(()=>production.validateSceneryJoins(production.validateRequest(priorRequest),priorImages),/near-old-coverage.*do not match exactly/);
 priorImages.clear();
 const v=production.validateRequest(request);production.validateSceneryJoins(v,images);let bad=structuredClone(request);bad.scenery.camera.travelXPixels*=10;
 assert.throws(()=>production.validateRequest(bad));
 const replacement=createCanvas(5600,1080),rx=replacement.getContext('2d');rx.drawImage(images.get('layer:far-new'),0,0);rx.fillStyle='#000000';rx.fillRect(1700,400,25,25);const badImages=new Map(images);badImages.set('layer:far-new',replacement);assert.throws(()=>production.validateSceneryJoins(v,badImages),/do not match exactly/);
 const plate=images.get('layer:finished-scene'),p=createCanvas(1920,1080),px=p.getContext('2d');px.drawImage(plate,0,0);const pp=px.getImageData(0,0,1920,1080).data;
 const mask=await loadImage(path.join(root,'docs/pictures/video/tabi-assets/scenario/scenery-20261002T100234Z/main-window-aperture.png'));px.clearRect(0,0,1920,1080);px.drawImage(mask,0,0);const mp=px.getImageData(0,0,1920,1080).data;
 const samples=[0,600,1170,1229,1230,1231,1350,1469,1590,1799],rows=[];const sheet=createCanvas(1280,360*5),sx=sheet.getContext('2d');
 for(const [index,f] of samples.entries()){
  const r=production.renderFrame(at(f),images,f).canvas,ctx=r.getContext('2d'),rgb=ctx.getImageData(0,0,1920,1080).data;let protectedDifferences=0,transparent=0;
  for(let i=0;i<1920*1080;i++){if(rgb[i*4+3]!==255)transparent++;if(mp[i*4+3]===0)for(let k=0;k<4;k++)if(rgb[i*4+k]!==pp[i*4+k])protectedDifferences++;}
  assert.equal(protectedDifferences,0,`Outside aperture frame ${f}`);assert.equal(transparent,0);
  const file=`source-sample-${String(f).padStart(4,'0')}.png`,bytes=r.toBuffer('image/png');fs.writeFileSync(path.join(run,'review',file),bytes,{flag:'wx'});rows.push({frame:f,sha256:sha(bytes),protectedDifferences,transparent});
  sx.drawImage(r,index%2*640,Math.floor(index/2)*360,640,360);sx.fillStyle='#101010';sx.fillRect(index%2*640,Math.floor(index/2)*360,165,25);sx.fillStyle='white';sx.font='17px sans-serif';sx.fillText(`t=${(f/30).toFixed(2)}s`,index%2*640+8,Math.floor(index/2)*360+19);
 }
 fs.writeFileSync(path.join(run,'review/source-contact-sheet.png'),sheet.toBuffer('image/png'),{flag:'wx'});
 const prep=JSON.parse(fs.readFileSync(path.join(run,'checks/preparation.json')));assert(prep.coverage.minimumPaintedMargins.right>2);assert(prep.coverage.minimumPaintedMargins.right-250<0,'Old far art really fails the 60s bound');
 const report={status:'PASS',coverageSeconds:60,sourceJoinFrames:v.scenery.joins.map(j=>({plane:j.planeId,frame:j.boundaryFrame,sampleFrames:j.sampleFrames})),exactViewportOverlap:true,negativeChecks:['Prior different-size near sections fail exact rendered overlap','Changed visible overlap rejected','Excessive travel rejected','Old 2500px painted far extent fails 60s'],sourceSamples:rows,scope:'Main-window static-neutral proof. Real source frames, not encoded video or moving-character envelope approval.'};fs.writeFileSync(path.join(run,'checks/source-checks.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({status:'PASS',samples:rows.length,joins:report.sourceJoinFrames.length,coverageSeconds:60}));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
