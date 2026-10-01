'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {runtime,RUN,OWNER,states,transform,distance,sha,ERASE}=require('./assemble.cjs');
async function main(){
 const variant=process.argv[2];assert(['v1','v2'].includes(variant),'v1 or v2 required');
 const r=await runtime(),{owner,createCanvas,loadImage,Path2D,getSource}=r;
 const kit=JSON.parse(fs.readFileSync(RUN+'/checks/parts.json'));
 const images={},records=Object.fromEntries(kit.parts.map(p=>[p.id,p]));
 for(const p of kit.parts){const bytes=fs.readFileSync(OWNER+'/'+p.receipt.destination);assert.equal(sha(bytes),p.sha256,'part pin changed');images[p.id]=await loadImage(bytes);}
 if(variant==='v2'){
  const correction=JSON.parse(fs.readFileSync(RUN+'/checks/correction-atlas.json'));const bytes=fs.readFileSync(OWNER+'/'+correction.receipt.destination);assert.equal(sha(bytes),correction.sha256);
  const atlas=await loadImage(bytes);
  for(const [id,rect]of Object.entries(correction.regions)){const c=createCanvas(rect[2],rect[3]);c.getContext('2d').drawImage(atlas,...rect,0,0,rect[2],rect[3]);images[id]=c;}
 }
 const upperBytes=fs.readFileSync(OWNER+'/'+kit.upperSleeve.path);assert.equal(sha(upperBytes),kit.upperSleeve.sha256);const upper=await loadImage(upperBytes);
 const fgBytes=fs.readFileSync(path.join(process.env.MELOTRAIL_REPO_ROOT,kit.foreground.path));assert.equal(sha(fgBytes),kit.foreground.sha256);const foreground=await loadImage(fgBytes);
 const original=await loadImage(getSource(48)),originalPixels=await owner.rgba(fs.readFileSync(getSource(48)));
 const srcUpperAngle=Math.atan2(984-264,635-628),srcFA=Math.atan2(-74,46),upperScale=states[0].value.upperLength/distance([628,264],[635,984]);
 const output=[],checks=[];
 const allowed=createCanvas(1920,1080),actx=allowed.getContext('2d');actx.fillStyle='white';actx.fill(new Path2D(ERASE));
 // Frozen proposed motion/support envelope, wider than the old whole-arm rectangle.
 actx.fillRect(748,415,247,337);const allow=actx.getImageData(0,0,1920,1080).data;
 function drawAttached(ctx,image,anchor,at,angle,scale=1){ctx.save();ctx.translate(...at);ctx.rotate(angle);ctx.scale(scale,scale);ctx.drawImage(image,-anchor[0],-anchor[1]);ctx.restore();}
 for(const state of states){
  const p=state.value,layer=createCanvas(1920,1080),ctx=layer.getContext('2d');
  const support=images.hidden_shoulder_elbow_overlap;
  for(const [point,sx,size]of [[p.shoulder,0,38],[p.elbow,64,44]])ctx.drawImage(support,sx,0,64,64,point[0]-size/2,point[1]-size/2,size,size);
  drawAttached(ctx,upper,[628,264],p.shoulder,p.upperAngle*Math.PI/180-srcUpperAngle,upperScale);
  const fa=p.forearmAngle*Math.PI/180-srcFA;
  drawAttached(ctx,images.forearm_separate,records.forearm_separate.anchors.elbow,p.elbow,fa);
  const cuffAngle=fa+p.wristAngle*Math.PI/180,cuff=records.cuff_wrist_overlap;
  const delta=[cuff.anchors.hand[0]-cuff.anchors.base[0],cuff.anchors.hand[1]-cuff.anchors.base[1]];
  const handAnchor=transform(delta,p.wrist,cuffAngle,.82);
  const handId=p.hand==='neutral'?'hand_neutral':'hand_wrist_beat';
  const handAngle=p.hand==='neutral'?cuffAngle-(states[0].value.forearmAngle*Math.PI/180-srcFA):cuffAngle;
  drawAttached(ctx,images[handId],records[handId].anchors.wrist,handAnchor,handAngle,p.hand==='neutral'?1:.85);
  drawAttached(ctx,images.cuff_wrist_overlap,cuff.anchors.base,p.wrist,cuffAngle,.82);
  const before=ctx.getImageData(0,0,1920,1080).data;
  const seam=[];
  for(const [joint,at]of Object.entries({shoulder:p.shoulder,elbow:p.elbow,forearmCuff:p.wrist,hand:handAnchor})){
   let holes=0,minAlpha=255;for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++){const x=Math.round(at[0])+dx,y=Math.round(at[1])+dy;const a=before[(y*1920+x)*4+3];if(a<240)holes++;minAlpha=Math.min(minAlpha,a);}
   seam.push({joint,at,holes,minAlpha});
  }
  ctx.globalCompositeOperation='destination-out';ctx.drawImage(foreground,0,0);ctx.globalCompositeOperation='source-over';
  const scene=createCanvas(1920,1080),sctx=scene.getContext('2d');sctx.drawImage(images.revealed_torso_cabin_backing,0,0);sctx.drawImage(layer,0,0);
  // Existing collar is in front of shoulder attachment, never redraw its texture.
  sctx.save();sctx.clip(new Path2D('M 695 574 L 785 548 L 801 571 L 780 622 L 713 653 Z'));sctx.drawImage(images.revealed_torso_cabin_backing,0,0);sctx.restore();
  const decoded=sctx.getImageData(0,0,1920,1080).data;let outsideChanges=0,nonOpaque=0;
  for(let i=0;i<decoded.length;i+=4){if(decoded[i+3]!==255)nonOpaque++;if(!allow[i+3]&&[0,1,2,3].some(k=>decoded[i+k]!==originalPixels.data[i+k]))outsideChanges++;}
  checks.push({state:state.id,geometry:p,handAnchor,seam,outsideChanges,nonOpaque,rgbaSha256:sha(Buffer.from(decoded)),motionEnvelope:[748,415,995,752]});output.push({state:state.id,canvas:scene});
 }
 const sheet=createCanvas(3200,2580),s=sheet.getContext('2d');s.fillStyle='#f4eddf';s.fillRect(0,0,3200,2580);s.font='bold 28px sans-serif';s.fillStyle='#222';
 const panels=[{state:'REFERENCE 48 — unchanged',canvas:original},...output.slice(0,2),output[3]];
 for(let i=0;i<4;i++){const x=(i%2)*1600,y=Math.floor(i/2)*940;s.fillStyle='#222';s.fillText(panels[i].state,x+24,y+32);s.drawImage(panels[i].canvas,x,y+40,1600,900);}
 for(let i=0;i<4;i++){const x=i*800;s.fillStyle='#222';s.fillText(output[i].state+' — joint detail',x+20,1925);s.drawImage(output[i].canvas,735,420,270,340,x+60,1940,500,629.6296296);}
 const token=owner.acquire(OWNER);try{const receipt=owner.reserve(OWNER,token,'staticPass',null,`states/assembled_review_${variant}.png`).receipt;const bytes=sheet.toBuffer('image/png');owner.createOutput(OWNER,token,receipt.id,bytes);
 const record={variant,receipt,sha256:sha(bytes),checks,upperScale,forearmScale:1,cuffScale:.82,handScales:{neutral:1,open:.85},appearance:'UNREVIEWED',technicalPass:checks.every(c=>!c.outsideChanges&&!c.nonOpaque&&c.seam.every(s=>!s.holes)),limitations:['Only four static states, not a 150-frame rig evaluation or moving approval.','New arm silhouette, shoulder, cuff size and neutral hand registration differ from reference48 and require explicit review.','No continuous hand-opening transition has been proved. Two supplied hand drawings only.']};fs.writeFileSync(RUN+`/checks/static-${variant}.json`,JSON.stringify(record,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(record,null,2));
 }finally{owner.release(OWNER,token);}
}
main().catch(e=>{console.error(e);process.exitCode=1});
