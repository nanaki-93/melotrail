'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const repair=require('../../docs/pictures/video/evidence/VG2-13/wrist-repair-20261001T175547Z/scripts/assemble-wrists.cjs');
const v1=require(path.join(repair.RUN,'inputs/v1-assembly-source.cjs'));
const v2=require(path.join(repair.RUN,'inputs/v2-assembly-source.cjs'));
const {createCanvas,loadImage}=require('./node_modules/@napi-rs/canvas');
function native(image,draw,spec){
  const c=createCanvas(image.width,image.height),ctx=c.getContext('2d');
  draw(ctx,image,{base:[0,0],at:[0,0],angle:0,scale:1,cuffContour:spec.cuffContour});
  return ctx.getImageData(0,0,c.width,c.height);
}
function bridgeEqual(a,b,rect){
  const [x,y,w,h]=rect;let pixels=0;
  for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++){
    const i=(yy*a.width+xx)*4;assert.deepEqual([...a.data.subarray(i,i+4)],[...b.data.subarray(i,i+4)],'authored wrist bridge/rim was cut or repainted');
    if(a.data[i+3]>=240)pixels++;
  }
  assert(pixels>w*h*.98,'wrist bridge must have real opaque support');
}
test('the full authored hand/wrist/cuff bridge survives; a slit missed by joint-centre checks is rejected',async()=>{
  for(const spec of Object.values(repair.specs)){
    const image=await loadImage(path.join(repair.RUN,spec.file));
    const source=native(image,(ctx,im)=>ctx.drawImage(im,0,0),spec),front=native(image,repair.drawConnected,spec);
    bridgeEqual(source,front,spec.bridge);
    const cut={width:front.width,height:front.height,data:new Uint8ClampedArray(front.data)},[x,y,w]=spec.bridge;
    for(let yy=y;yy<y+8;yy++)for(let xx=x;xx<x+w;xx++)cut.data[(yy*cut.width+xx)*4+3]=0;
    const entry=(Math.round(spec.entry[1])*cut.width+Math.round(spec.entry[0]))*4;
    assert(cut.data[entry+3]>=240,'negative deliberately preserves the old centre sample');
    assert.throws(()=>bridgeEqual(source,cut,spec.bridge),/bridge\/rim/);
  }
});
test('generated green fabric is behind the existing sleeve, while wrist and cuff are drawn exactly once',async()=>{
  for(const spec of Object.values(repair.specs)){
    const image=await loadImage(path.join(repair.RUN,spec.file));
    const old=native(image,v1.drawConnected,spec),front=native(image,repair.drawConnected,spec),back=native(image,repair.drawSleeveOverlap,spec);
    const green=(1220*image.width+550)*4;
    assert(old.data[green+3]>240,'v1 actually placed this extra green patch above the sleeve');
    assert.equal(front.data[green+3],0);assert(back.data[green+3]>240);
    const [x,y,w,h]=spec.bridge;
    for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)assert.equal(back.data[(yy*image.width+xx)*4+3],0,'back overlap must not double-draw the wrist');
  }
});
test('transparent input margins are real alpha; supplied generated files remain hash-bound',async()=>{
  repair.verifyInputs();
  for(const spec of Object.values(repair.specs)){
    const im=await loadImage(path.join(repair.RUN,spec.file)),d=native(im,(ctx,img)=>ctx.drawImage(img,0,0),spec).data;
    let transparent=0,solid=0;for(let i=3;i<d.length;i+=4){if(d[i]===0)transparent++;if(d[i]>=240)solid++;}
    assert(transparent>im.width*im.height*.3);assert(solid>im.width*im.height*.25);
  }
});
test('only faint generated edge pixels can be confined; meaningful out-of-support art fails',()=>{
  const mask=Uint8ClampedArray.from([255,255,255,255,0,0,0,0]);
  const faint=Uint8ClampedArray.from([20,80,40,253,10,20,30,1]);
  assert.deepEqual(repair.confineFaintEdge(faint,mask),{pixels:1,maxAlpha:1});
  assert.deepEqual([...faint],[20,80,40,253,0,0,0,0]);
  const visible=Uint8ClampedArray.from([20,80,40,253,10,20,30,9]);
  assert.throws(()=>repair.confineFaintEdge(visible,mask),/visible artwork/);
  assert.equal(visible[7],9,'failed preflight must not erase the witness');
  const failed=JSON.parse(fs.readFileSync(path.join(repair.RUN,'checks/static-v1.json')));
  assert.equal(failed.facts[0].outsideChanges,1,'retained actual v1 mismatch');
});
test('the full cuff boundary has sleeve support in all four poses, including the real v2 gap witnesses',async()=>{
  const r=await repair.inputs(),before=await v2.compose(r),after=await repair.compose(r);
  const counts=[];
  for(let k=0;k<after.outputs.length;k++){
    const a=after.outputs[k].canvas.getContext('2d').getImageData(0,0,1920,1080).data;
    const b=before.outputs[k].canvas.getContext('2d').getImageData(0,0,1920,1080).data;
    const [wx,wy]=after.facts[k].placement.at;let repaired=0;
    for(let y=Math.floor(wy)-8;y<wy+28;y++)for(let x=Math.floor(wx)-65;x<wx+65;x++){
      const i=(y*1920+x)*4;
      // Existing window/cabin was visibly exposed under the cuff. The rear
      // overlap replaces those warm pixels with actual generated green cloth.
      if(b[i]>b[i+1]*1.08&&b[i+1]>b[i+2]*1.08&&a[i+1]>a[i]*1.3&&a[i+1]>a[i+2])repaired++;
    }
    counts.push(repaired);assert(repaired>20,'missing actual cuff-gap repair');
    assert.equal(after.facts[k].outsideChanges,0);assert.equal(after.facts[k].nonOpaque,0);assert.equal(after.facts[k].wristEntryHoles,0);
  }
  assert.equal(counts.length,4);
});
test('the exported front and overlap PNGs retain their registered canvas and source coverage',async()=>{
  const manifest=JSON.parse(fs.readFileSync(path.join(repair.RUN,'checks/parts.json')));
  assert.equal(manifest.parts.length,4);
  for(const part of manifest.parts){
    const image=await loadImage(path.join(repair.RUN,part.destination)),source=await loadImage(path.join(repair.RUN,part.source));
    assert.equal(image.width,part.width);assert.equal(image.height,part.height);
    const actual=native(image,(ctx,im)=>ctx.drawImage(im,0,0),repair.specs[part.hand]);
    const expected=native(source,part.layer==='front'?repair.drawConnected:repair.drawSleeveOverlap,repair.specs[part.hand]);
    let maxAlphaDelta=0,maxSolidRgbDelta=0;
    for(let i=0;i<actual.data.length;i+=4){
      maxAlphaDelta=Math.max(maxAlphaDelta,Math.abs(actual.data[i+3]-expected.data[i+3]));
      if(expected.data[i+3]>=240)for(let k=0;k<3;k++)maxSolidRgbDelta=Math.max(maxSolidRgbDelta,Math.abs(actual.data[i+k]-expected.data[i+k]));
    }
    assert.equal(maxAlphaDelta,0,'export must retain exact matte coverage');assert(maxSolidRgbDelta<=1,'PNG round-trip may not repaint the registered part');
  }
});
