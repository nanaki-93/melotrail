"use strict";
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=process.cwd(),stage=path.resolve(process.argv[2]);
const {createCanvas,loadImage}=require(path.join(root,'tools/video-motion/node_modules/@napi-rs/canvas'));
const transport=require(path.join(root,'docs/pictures/video/evidence/VG2-17/metadata-20261002T015459Z/scripts/png_transport.cjs'));
(async()=>{const sequences=[Array.from({length:13},(_,i)=>1+8*i)];
for(let first=1;first<=97;first+=20)sequences.push(Array.from({length:Math.min(20,98-first)},(_,i)=>first+i));
let page=0;for(const sequence of sequences){const w=384,h=224,pad=22,cols=4,rows=Math.ceil(sequence.length/cols),c=createCanvas(cols*w,rows*(h+pad)),ctx=c.getContext('2d');ctx.fillStyle='#202020';ctx.fillRect(0,0,c.width,c.height);ctx.font='14px sans-serif';
for(let n=0;n<sequence.length;n++){let f=sequence[n],p=path.join(stage,'review/frames',`frame-${String(f).padStart(4,'0')}.png`);const im=await loadImage(transport.metadataFree(fs.readFileSync(p)));const x=n%cols*w,y=Math.floor(n/cols)*(h+pad);ctx.drawImage(im,x,y,w,h);ctx.fillStyle='white';ctx.fillText(`Frame ${f} | ${((f-1)/25).toFixed(2)}s`,x+6,y+h+16);}
fs.writeFileSync(path.join(stage,'review',`contact-${page++}.png`),c.toBuffer('image/png'),{flag:'wx'});}
console.log(JSON.stringify({pages:sequences.length,all97FramesRepresented:true}));})().catch(e=>{console.error(e.stack);process.exitCode=1;});
