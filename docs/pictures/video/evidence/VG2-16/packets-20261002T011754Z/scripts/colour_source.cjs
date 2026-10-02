'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const BASE=path.resolve(__dirname,'..'),REPO=path.resolve(BASE,'../../../../../..');
const {createCanvas,loadImage}=require(path.join(REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
async function main(){
  assert.deepEqual(process.argv.slice(2),['--generate']);assert.equal(process.env.MELOTRAIL_PROOF_KIND,'colour');
  assert.equal(process.env.MELOTRAIL_PACKET_FILE,BASE+'/packets/colour-v2.json');
  const p=JSON.parse(fs.readFileSync(process.env.MELOTRAIL_PACKET_FILE)),work=p.destinations.work;
  const c=createCanvas(640,360),ctx=c.getContext('2d'),patches=[];
  ctx.fillStyle='#808080';ctx.fillRect(0,0,640,360);
  const colours=Array.from({length:32},(_,i)=>[Math.round(i*255/31),Math.round(i*255/31),Math.round(i*255/31)]).concat([[255,0,0],[0,255,0],[0,0,255],[255,255,0],[255,0,255],[0,255,255],[80,120,180],[25,100,50],[224,162,58],[245,178,211],[39,116,73],[204,159,111],[40,31,36],[220,215,180],[142,101,72],[250,233,189]]);
  for(let i=0;i<colours.length;i++){const x=(i%16)*40,y=Math.floor(i/16)*50;ctx.fillStyle=`rgb(${colours[i].join(',')})`;ctx.fillRect(x,y,40,50);patches.push({rgb:colours[i],box:[x+12,y+12,16,24]});}
  const art=await loadImage(path.join(REPO,p.artSample.path));ctx.drawImage(art,640,380,640,360,0,150,320,180);ctx.drawImage(art,0,0,1920,1080,320,150,320,180);
  for(let i=1;i<=p.delivery.frames;i++)fs.writeFileSync(path.join(work,'source',`frame-${String(i).padStart(4,'0')}.png`),c.toBuffer('image/png'),{flag:'wx'});
  fs.writeFileSync(path.join(work,'patches.json'),JSON.stringify({patches,artBox:[0,150,640,180],signal:'sRGB code values; explicit conversion occurs in the source stage'},null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify({sourceFrames:p.delivery.frames,dimensions:[640,360],patches:patches.length}));
}
if(require.main===module)main().catch(e=>{console.error(e);process.exitCode=1;});
