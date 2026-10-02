'use strict';
// Technical video composition. Existing high-resolution artwork remains the fixed source.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const ROOT=path.resolve(__dirname,'../../../../../../..'),RUN=path.resolve(__dirname,'..');
const {createCanvas,loadImage,ImageData}=require(path.join(ROOT,'tools/video-motion/node_modules/@napi-rs/canvas'));
const {matte,dilate,W,H}=require('./matte.cjs');
const colour=require(path.join(ROOT,'docs/pictures/video/evidence/VG2-17/metadata-20261002T015459Z/scripts/colour.cjs'));
const transport=require(path.join(ROOT,'docs/pictures/video/evidence/VG2-17/metadata-20261002T015459Z/scripts/png_transport.cjs'));
const N=W*H,OW=1920,OH=1080,S=OH/H,OX=(OW-W*S)/2,CROP=[80,72,304,232];
const FRAMES=path.join(ROOT,'docs/pictures/video/evidence/VG2-24/head-watch-20261002T035933Z/review/frames');
const SOURCE=path.join(ROOT,'docs/pictures/video/tabi-assets/train-actions/book-panorama-20261002T033127Z/02-reading-book-facing-tabi.png');
const PLATE=path.join(ROOT,'docs/pictures/video/tabi-assets/train-actions/20-breathing-book-facing-tabi-clean-cabin-candidate.png');
const CITY=path.join(ROOT,'docs/pictures/video/tabi-assets/scenario/book-panorama-20261002T033127Z/02-sumida-river-skytree-panorama.png');
const SHA=b=>crypto.createHash('sha256').update(b).digest('hex');
const fileName=f=>`frame-${String(f+1).padStart(4,'0')}`;
function dataCanvas(data,w,h){const c=createCanvas(w,h);c.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(data),w,h),0,0);return c;}
async function native(f){const bytes=fs.readFileSync(path.join(FRAMES,fileName(f)+'.png')),im=await loadImage(bytes),c=createCanvas(W,H),x=c.getContext('2d');x.drawImage(im,0,0);return {pixels:x.getImageData(0,0,W,H).data,sha256:SHA(bytes)};}
function diskBytes(p){let n=0;for(const x of fs.readdirSync(p,{withFileTypes:true})){const q=path.join(p,x.name);n+=x.isDirectory()?diskBytes(q):fs.statSync(q).size;}return n;}
function budget(){if(diskBytes(RUN)>512*1024*1024)throw Error('512 MiB evidence limit');}
async function prepare(){const rawDir=path.join(RUN,'inputs/motion-crops'),alphaDir=path.join(RUN,'inputs/mattes');fs.mkdirSync(rawDir);fs.mkdirSync(alphaDir);const frames=[],regressions=[];let previous=null;
 const witnesses=[{frame:0,foreground:[[361,197],[349,170],[212,84],[275,177]],background:[[377,206],[371,222],[368,230]]},{frame:64,foreground:[[242,114],[269,128],[285,154],[277,132],[244,102]],background:[[320,117],[94,95],[400,200]]},{frame:104,foreground:[[329,172],[330,189],[333,137]],background:[[353,201],[360,220]]}];
 for(let f=0;f<129;f++){const src=await native(f),{alpha,edge}=matte(src.pixels,f);const rgb=Buffer.alloc(304*232*3),mask=Buffer.alloc(304*232);let area=0,boundaryDelta=0;
  for(let y=0;y<232;y++)for(let x=0;x<304;x++){const i=(y+72)*W+x+80,o=(y*304+x)*3;rgb[o]=src.pixels[i*4];rgb[o+1]=src.pixels[i*4+1];rgb[o+2]=src.pixels[i*4+2];mask[y*304+x]=alpha[i];if(alpha[i]>127)area++;}
  if(previous)for(let y=112;y<=248;y++)boundaryDelta=Math.max(boundaryDelta,Math.abs(edge[y]-previous[y]));previous=edge;
  for(const w of witnesses.filter(x=>x.frame===f)){for(const [x,y] of w.foreground)if(alpha[y*W+x]<150)regressions.push({f,x,y,reason:'missing subject',alpha:alpha[y*W+x]});for(const [x,y] of w.background)if(alpha[y*W+x])regressions.push({f,x,y,reason:'retained background',alpha:alpha[y*W+x]});}
  const crop=path.join(rawDir,fileName(f)+'.rgb'),mat=path.join(alphaDir,fileName(f)+'.alpha');fs.writeFileSync(crop,rgb,{flag:'wx'});fs.writeFileSync(mat,mask,{flag:'wx'});
  frames.push({frame:f,sourceSha256:src.sha256,crop:path.relative(RUN,crop),cropSha256:SHA(rgb),matte:path.relative(RUN,mat),matteSha256:SHA(mask),area,maximumRowBoundaryChange:boundaryDelta});
 }
 const result={nativeSize:[W,H],crop:CROP,frames,regressions,claim:'Actual-frame silhouette study; point witnesses alone do not pass visual inspection.'};fs.writeFileSync(path.join(RUN,'checks/preparation.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});budget();console.log(JSON.stringify({stage:'prepare',frames:frames.length,regressions}));if(regressions.length)throw Error('Real-frame witness regression');
}
function glassPath(x){x.beginPath();x.moveTo(OX+342*S,0);x.lineTo(OW,0);x.lineTo(OW,OH*.709);x.lineTo(OX+364*S,252*S);x.quadraticCurveTo(OX+342*S,248*S,OX+341*S,222*S);x.closePath();}
function cupPath(x){x.beginPath();x.moveTo(382,253);x.quadraticCurveTo(397,250,419,253);x.lineTo(426,258);x.lineTo(428,269);x.lineTo(424,275);x.lineTo(420,317);x.quadraticCurveTo(402,323,383,317);x.lineTo(379,276);x.lineTo(375,270);x.lineTo(377,259);x.closePath();}
async function render(mode){const prep=JSON.parse(fs.readFileSync(path.join(RUN,'checks/preparation.json'))),finish=JSON.parse(fs.readFileSync(path.join(RUN,'checks/finishing.json')));if(prep.regressions.length||finish.frames.length!==129)throw Error('Unproved matte or incomplete enhancement');
 const directory=path.join(RUN,'review',mode==='samples'?'full-hd-samples':'encoded-frames');fs.mkdirSync(directory);const selected=mode==='samples'?[0,20,24,28,32,64,100,104,108,128]:Array.from({length:129},(_,i)=>i);
 const source=await loadImage(SOURCE),plate=await loadImage(PLATE),city=await loadImage(CITY),base=createCanvas(OW,OH),bx=base.getContext('2d');bx.imageSmoothingQuality='high';bx.drawImage(source,0,0,OW,OH);const basePixels=bx.getImageData(0,0,OW,OH).data;
 const first=await native(0),old=dilate(Uint8Array.from(matte(first.pixels,0).alpha,v=>v>0?1:0),3),oldRGBA=new Uint8ClampedArray(N*4);for(let i=0;i<N;i++){oldRGBA[i*4]=oldRGBA[i*4+1]=oldRGBA[i*4+2]=255;oldRGBA[i*4+3]=old[i]?255:0;}
 const eraseCanvas=dataCanvas(oldRGBA,W,H),clean=createCanvas(OW,OH),cx=clean.getContext('2d');cx.drawImage(plate,0,0,OW,OH);cx.globalCompositeOperation='destination-in';cx.drawImage(eraseCanvas,OX,0,W*S,H*S);
 const canvas=createCanvas(OW,OH),ctx=canvas.getContext('2d');const records=[];let totalBytes=0;
 for(const f of selected){const frame=prep.frames[f],enhanced=finish.frames[f],rgbPath=path.join(RUN,enhanced.path),rgb=fs.readFileSync(rgbPath),alphaBytes=fs.readFileSync(path.join(RUN,frame.matte));if(SHA(rgb)!==enhanced.sha256||SHA(alphaBytes)!==frame.matteSha256)throw Error('Frame input pin drift');
  const subjectRGBA=new Uint8ClampedArray(608*464*4);for(let i=0;i<608*464;i++){subjectRGBA[i*4]=rgb[i*3];subjectRGBA[i*4+1]=rgb[i*3+1];subjectRGBA[i*4+2]=rgb[i*3+2];subjectRGBA[i*4+3]=255;}
  const subject=dataCanvas(subjectRGBA,608,464),sx=subject.getContext('2d'),aRGBA=new Uint8ClampedArray(304*232*4);for(let i=0;i<alphaBytes.length;i++){aRGBA[i*4]=aRGBA[i*4+1]=aRGBA[i*4+2]=255;aRGBA[i*4+3]=alphaBytes[i];}sx.globalCompositeOperation='destination-in';sx.imageSmoothingEnabled=true;sx.drawImage(dataCanvas(aRGBA,304,232),0,0,608,464);
  ctx.globalCompositeOperation='source-over';ctx.clearRect(0,0,OW,OH);ctx.imageSmoothingQuality='high';ctx.drawImage(base,0,0);ctx.drawImage(clean,0,0);ctx.save();glassPath(ctx);ctx.clip();const panScale=320*S/city.height;ctx.drawImage(city,OX+330*S-f/25*18*S,0,city.width*panScale,320*S);ctx.restore();ctx.drawImage(subject,OX+80*S,72*S,304*S,232*S);
  let pixels=ctx.getImageData(0,0,OW,OH);const p=pixels.data;
  for(let y=Math.floor(274*S);y<Math.ceil(305*S);y++){const fade=Math.max(0,Math.min(1,(298-y/S)/24));for(let x=Math.floor(OX+125*S);x<Math.ceil(OX+375*S);x++)for(let k=0;k<3;k++){const i=(y*OW+x)*4+k;p[i]=Math.round(basePixels[i]+(p[i]-basePixels[i])*fade);}}
  ctx.putImageData(pixels,0,0);ctx.save();ctx.translate(OX,0);ctx.scale(S,S);cupPath(ctx);ctx.clip();ctx.setTransform(1,0,0,1,0,0);ctx.drawImage(base,0,0);ctx.restore();
  // Outside the union of the moving head/bust and the window the full-size source stays exact.
  const srgb=ctx.getImageData(0,0,OW,OH);let fixedChanged=0;for(const [xx,yy,ww,hh] of [[570,810,550,85],[946,650,55,90],[55,50,140,320]])for(let y=yy;y<yy+hh;y++)for(let x=xx;x<xx+ww;x++)for(let k=0;k<3;k++)if(srgb.data[(y*OW+x)*4+k]!==basePixels[(y*OW+x)*4+k])fixedChanged++;
  if(mode==='all')ctx.putImageData(new ImageData(colour.convert(srgb.data),OW,OH),0,0);
  let png=await canvas.encode('png');if(mode==='all'){png=transport.rec709(png);transport.requireRec709(png);}const dest=path.join(directory,fileName(f)+'.png');fs.writeFileSync(dest,png,{flag:'wx'});totalBytes+=png.length;
  records.push({frame:f,sha256:SHA(png),bytes:png.length,fixedRegionChangedChannels:fixedChanged});if(mode==='all')fs.unlinkSync(rgbPath);budget();await new Promise(resolve=>setImmediate(resolve));
 }
 fs.writeFileSync(path.join(RUN,'checks',mode==='samples'?'sample-composition.json':'composition.json'),JSON.stringify({mode,dimensions:[OW,OH],fps:25,source:SOURCE,headDetail:'RealESRGAN x2 on the accepted native motion; not native 1080p synthesis',frameCount:records.length,frames:records,totalBytes,colour:mode==='all'?'Numeric sRGB to Rec.709 plus matching cICP before YUV encoding':'sRGB inspection PNG'},null,2)+'\n',{flag:'wx'});
 console.log(JSON.stringify({stage:mode,frames:records.length,totalBytes,allListedFixedRegionsExact:records.every(f=>f.fixedRegionChangedChannels===0)}));
}
(async()=>{const mode=process.argv[2];if(mode==='prepare')await prepare();else if(['samples','all'].includes(mode))await render(mode);else throw Error('Use prepare, samples or all');})().catch(e=>{console.error(e.stack);process.exitCode=1;});
