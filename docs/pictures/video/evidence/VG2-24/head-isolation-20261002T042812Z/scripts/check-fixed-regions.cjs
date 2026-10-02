'use strict';
const path=require('node:path'),fs=require('node:fs');
const root=path.resolve(__dirname,'../../../../../../..'),run=path.resolve(__dirname,'..');
const {createCanvas,loadImage}=require(path.join(root,'tools/video-motion/node_modules/@napi-rs/canvas'));
const regions={cup:[375,252,57,71],bookAndPen:[220,321,233,62],posterAboveHead:[113,30,60,44],leftCabin:[1,1,87,175]};
(async()=>{const c=createCanvas(768,448),ctx=c.getContext('2d');async function pixels(p){ctx.clearRect(0,0,768,448);ctx.drawImage(await loadImage(p),0,0);return ctx.getImageData(0,0,768,448).data;}
 const ref=await pixels(path.join(root,'docs/pictures/video/evidence/VG2-24/head-watch-20261002T035933Z/review/frames/frame-0001.png')),frames=[];
 for(const name of fs.readdirSync(path.join(run,'review/attempt-3-samples')).filter(n=>/^frame-\d{4}\.png$/.test(n)).sort()){
  const p=await pixels(path.join(run,'review/attempt-3-samples',name)),results={};for(const [label,[x,y,w,h]] of Object.entries(regions)){let changed=0,max=0;for(let row=y;row<y+h;row++)for(let col=x;col<x+w;col++){let local=0;for(let k=0;k<3;k++)local=Math.max(local,Math.abs(p[(row*768+col)*4+k]-ref[(row*768+col)*4+k]));if(local)changed++;max=Math.max(max,local);}results[label]={changedPixels:changed,maxChannelDelta:max};}frames.push({name,results});
 }
 const result={frames,regions,interpretation:'Exact pixels only in these sampled fixed regions. These numbers do not pass the visibly defective moving matte, whole-cabin preservation or long-film continuity.'};fs.writeFileSync(path.join(run,'checks/fixed-regions.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({sampleCount:frames.length,allListedRegionsUnchanged:frames.every(f=>Object.values(f.results).every(v=>v.changedPixels===0))}));
})().catch(e=>{console.error(e);process.exitCode=1;});
