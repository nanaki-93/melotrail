'use strict';
// Diagnostic frame comparisons only: numbers cannot award artistic or moving approval.
const fs = require('node:fs');
const path = require('node:path');
const { createCanvas, loadImage } = require(path.join(process.cwd(), 'tools/video-motion/node_modules/@napi-rs/canvas'));
const [directory, output] = process.argv.slice(2);
if (!directory || !output || fs.existsSync(output)) throw new Error('Use an existing frames directory and a new report path');
const names = fs.readdirSync(directory).filter(x => /^frame-[0-9]{4}\.png$/.test(x)).sort();
if (names.length !== 129) throw new Error('Expected the complete 129-frame sequence');
const regions = {
  head: [147, 72, 237, 184],
  upperBodyAndHands: [148, 253, 222, 71],
  cabinPoster: [110, 22, 94, 52],
  leftSeat: [3, 203, 59, 235],
  windowExterior: [455, 8, 306, 280],
  cup: [380, 252, 52, 70],
  notebook: [222, 322, 225, 49]
};
const canvas = createCanvas(768,448), context = canvas.getContext('2d');
let first, previous;
const frames = [];
function delta(a,b,box) {
  const [x,y,w,h]=box;let total=0,changed=0,max=0;
  for(let row=y;row<y+h;row++)for(let col=x;col<x+w;col++) {
    const off=(row*768+col)*4;let local=0;
    for(let ch=0;ch<3;ch++){const d=Math.abs(a[off+ch]-b[off+ch]);total+=d;local=Math.max(local,d);max=Math.max(max,d);}
    if(local>10)changed++;
  }
  return {meanAbsoluteChannelDelta:Number((total/(w*h*3)).toFixed(4)),pixelsWithChannelDeltaOver10:changed,pixelCount:w*h,maximumChannelDelta:max};
}
(async()=>{
  for(let index=0;index<names.length;index++){
    const source=await loadImage(path.join(directory,names[index]));
    if(source.width!==768 || source.height!==448)throw new Error('Unexpected frame dimensions');
    context.clearRect(0,0,768,448);context.drawImage(source,0,0);
    const pixels=new Uint8ClampedArray(context.getImageData(0,0,768,448).data);
    if(!first)first=pixels;
    const result={index,seconds:index/25,regions:{}};
    for(const [name,box] of Object.entries(regions))result.regions[name]={fromFirst:delta(pixels,first,box),fromPrevious:delta(pixels,previous||pixels,box)};
    frames.push(result);previous=pixels;
  }
  const summary={};
  for(const name of Object.keys(regions)){
    const peak=frames.reduce((a,b)=>b.regions[name].fromFirst.meanAbsoluteChannelDelta>a.regions[name].fromFirst.meanAbsoluteChannelDelta?b:a);
    summary[name]={peakFromFirstMeanDelta:peak.regions[name].fromFirst.meanAbsoluteChannelDelta,peakFrame:peak.index,lastFromFirst:frames.at(-1).regions[name].fromFirst};
  }
  const result={frameCount:names.length,width:768,height:448,fps:25,regions,summary,frames,interpretation:'Diagnostic raw generated-clip differences. Camera/cabin/prop drift is a defect to inspect; nonzero head changes alone do not establish the requested movement or identity. No automatic artistic approval.'};
  fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  process.stdout.write(JSON.stringify(summary,null,2)+'\n');
})().catch(e=>{process.stderr.write(String(e.stack)+'\n');process.exitCode=1;});
