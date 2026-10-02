'use strict';
// Read existing pixels only, to make the next correction precise.
const fs=require('node:fs'),path=require('node:path');
const root='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..');
const {createCanvas,loadImage}=require(path.join(root,'tools/video-motion/node_modules/@napi-rs/canvas'));
(async()=>{
 const base=path.join(root,'docs/pictures/video/tabi-assets/scenario');
 async function pixels(file){const im=await loadImage(file),c=createCanvas(im.width,im.height),x=c.getContext('2d');x.drawImage(im,0,0);return {width:im.width,data:x.getImageData(0,0,im.width,im.height).data};}
 const a=await pixels(path.join(base,'tokyo-parallax-near-5600x1080.png')),b=await pixels(path.join(base,'tokyo-clockfront-near-v5/tokyo-near-section-v5-candidate-6200x1080.png'));
 let pixelsDifferent=0,maximumChannelDelta=0,firstNewAlpha=6200,lastOldAlpha=0;const examples=[];
 for(let y=0;y<1080;y++)for(let x=0;x<6200;x++){
  const j=(y*b.width+x)*4;
  if(x<a.width&&a.data[(y*a.width+x)*4+3]>0)lastOldAlpha=Math.max(lastOldAlpha,x);
  if(x>4128&&b.data[j+3]>0)firstNewAlpha=Math.min(firstNewAlpha,x);
  if(x>=4900)continue;
  const i=(y*a.width+x)*4;let delta=0;for(let k=0;k<4;k++)delta=Math.max(delta,Math.abs(a.data[i+k]-b.data[j+k]));
  if(delta){pixelsDifferent++;maximumChannelDelta=Math.max(maximumChannelDelta,delta);if(examples.length<8)examples.push({x,y,old:[...a.data.slice(i,i+4)],candidate:[...b.data.slice(j,j+4)]});}
 }
 const result={pixelsDifferentBefore4900:pixelsDifferent,maximumChannelDeltaBefore4900:maximumChannelDelta,firstNewNonzeroAlphaColumn:firstNewAlpha,lastOldNonzeroAlphaColumn:lastOldAlpha,joinRightmostSourceCoordinate:1920+3*480*(1230+1/6)/599,proposedExactOldPrefixEndExclusive:4900,examples,scope:'Read-only difference diagnosis. No source repair or native movie attempt.'};
 fs.writeFileSync(path.join(run,'checks/overlap-difference.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
