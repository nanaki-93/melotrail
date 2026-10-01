'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const repair=require('./assemble-wrists.cjs');
const {createCanvas,loadImage}=require(path.join(repair.REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
const safe=require(path.join(repair.REPO,'docs/pictures/video/evidence/VG2-13/continuation-20261001-153813Z/check-preparation.cjs')).safeFile;
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
async function main(){
  assert.deepEqual(process.argv.slice(2),['--export']);repair.verifyInputs();
  const run=repair.RUN,lock=path.join(run,'parts-export.lock');fs.mkdirSync(lock);
  try {
    const existing=fs.readdirSync(path.join(run,'journal')).filter(n=>n.startsWith('derivative-'));
    assert.equal(existing.length,0,'parts export already attempted; preserve its allowance');
    const parts=[];let ordinal=15;
    for(const [hand,spec]of Object.entries(repair.specs))for(const layer of ['front','overlap']){
      const file=`parts/${hand}-${layer}.png`,destination=safe(run,file,true);
      const receipt={kind:'derivative',ordinal:++ordinal,hand,layer,destination:file};
      fs.writeFileSync(path.join(run,`journal/derivative-${ordinal}-${hand}-${layer}.json`),JSON.stringify(receipt,null,2)+'\n',{flag:'wx'});
      const im=await loadImage(path.join(run,spec.file)),c=createCanvas(im.width,im.height),ctx=c.getContext('2d');
      const placement={base:[0,0],at:[0,0],angle:0,scale:1,cuffContour:spec.cuffContour};
      (layer==='front'?repair.drawConnected:repair.drawSleeveOverlap)(ctx,im,placement);
      const bytes=c.toBuffer('image/png');fs.writeFileSync(destination,bytes,{flag:'wx'});
      parts.push({...receipt,sha256:sha(bytes),width:im.width,height:im.height,source:spec.file,sourceSha256:spec.sha256,anchor:spec.base,sceneScale:spec.scale});
    }
    const m={schema:'standalone-wrist-layer-exports-1',parts,drawOrder:['upper sleeve','wrist overlap','existing forearm','connected hand/wrist/cuff front','existing foreground'],registration:'Front and overlap share native canvas, origin, scale and transform. Never scale or shift the hand independently.',alpha:'Raw generation files retained; exported layers use only the declared complementary cuff masks, with no RGB painting.',appearance:'PENDING VG2-14',limitations:['Separate neutral/open cuff drawings are not yet proven in motion.','Four reviewed-pose candidates only; no rig/all-frame/hand-opening transition proof.']};
    fs.writeFileSync(path.join(run,'checks/parts.json'),JSON.stringify(m,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(m,null,2));
  }finally{fs.rmdirSync(lock);}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
