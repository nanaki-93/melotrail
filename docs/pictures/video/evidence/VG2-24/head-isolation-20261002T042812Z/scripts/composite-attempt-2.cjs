'use strict';
// Technical rotoscoping/composition of an existing video; no synthesized art.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const ROOT=path.resolve(__dirname,'../../../../../../..');
const {createCanvas,loadImage,ImageData}=require(path.join(ROOT,'tools/video-motion/node_modules/@napi-rs/canvas'));
const W=768,H=448,N=W*H;
const FRAMES=path.join(ROOT,'docs/pictures/video/evidence/VG2-24/head-watch-20261002T035933Z/review/frames');
const PLATE=path.join(ROOT,'docs/pictures/video/tabi-assets/train-actions/20-breathing-book-facing-tabi-clean-cabin-candidate.png');
const PANORAMA=path.join(ROOT,'docs/pictures/video/tabi-assets/scenario/book-panorama-20261002T033127Z/02-sumida-river-skytree-panorama.png');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function pixels(im,cover=false){const c=createCanvas(W,H),x=c.getContext('2d');if(cover){const s=Math.max(W/im.width,H/im.height);x.drawImage(im,(W-im.width*s)/2,(H-im.height*s)/2,im.width*s,im.height*s);}else x.drawImage(im,0,0,W,H);return new Uint8ClampedArray(x.getImageData(0,0,W,H).data);}
function dilate(a,r){const b=new Uint8Array(N);for(let y=1;y<H-1;y++)for(let x=1;x<W-1;x++){if(!a[y*W+x])continue;for(let dy=-r;dy<=r;dy++)for(let dx=-r;dx<=r;dx++){if(dx*dx+dy*dy<=r*r&&x+dx>=0&&x+dx<W&&y+dy>=0&&y+dy<H)b[(y+dy)*W+x+dx]=1;}}return b;}
function fillHoles(a){const seen=new Uint8Array(N),q=new Int32Array(N);let start=0,end=0;q[end++]=0;seen[0]=1;while(start<end){const i=q[start++],x=i%W,y=(i/W)|0;for(const j of [x?i-1:-1,x<W-1?i+1:-1,y?i-W:-1,y<H-1?i+W:-1]){if(j<0||seen[j]||a[j])continue;seen[j]=1;q[end++]=j;}}return a.map((v,i)=>v||!seen[i]?1:0);}
function largest(a){const seen=new Uint8Array(N),q=new Int32Array(N);let best=[];for(let i=0;i<N;i++){if(!a[i]||seen[i])continue;let start=0,end=0;q[end++]=i;seen[i]=1;while(start<end){let j=q[start++],x=j%W,y=(j/W)|0;for(const k of [x?j-1:-1,x<W-1?j+1:-1,y?j-W:-1,y<H-1?j+W:-1])if(k>=0&&!seen[k]&&a[k]){seen[k]=1;q[end++]=k;}}if(end>best.length)best=Array.from(q.subarray(0,end));}const out=new Uint8Array(N);for(const i of best)out[i]=1;return out;}
function boundaryAt(frame){
 const knots=[[0,142,378,78,259],[17,142,378,78,259],[24,126,349,77,255],[32,105,318,86,247],[48,87,303,84,251],[64,85,302,80,253],[96,85,302,80,253],[104,107,322,83,255],[112,142,377,78,259],[128,142,377,78,259]];
 let k=0;while(k<knots.length-2&&knots[k+1][0]<frame)k++;const a=knots[k],b=knots[k+1],u=(frame-a[0])/(b[0]-a[0]);return a.slice(1).map((v,i)=>v+(b[i+1]-v)*u);
}
function matte(p,frame){
 const [left,right,top,bottom]=boundaryAt(frame),seed=new Uint8Array(N);
 for(let y=Math.floor(top);y<304;y++)for(let x=Math.floor(Math.min(left,128));x<Math.ceil(Math.max(right,376));x++){
  const i=y*W+x,o=i*4,r=p[o],g=p[o+1],b=p[o+2];
  const head=y<bottom&&x>=left&&x<right;
  // The seat has low green; skin/fronds have brighter green and a pink/lilac hue.
  const pink=head&&r>130&&g>94&&r-g>12&&b-g>3;
  const coral=head&&r>135&&r-g>45&&b-g>7&&y<230;
  const gold=y>205&&y<281&&x>143&&x<347&&r>95&&g>63&&r-g>27&&g-b>43;
  const coat=y>229&&x>126&&x<360&&r<95&&g>r*1.35&&g>b*1.08;
  if(pink||coral||gold||coat)seed[i]=1;
 }
 let m=fillHoles(largest(dilate(seed,2)));
 // Include the ink outline immediately outside the colored interior.
 const d=dilate(m,2);
 for(let i=0;i<N;i++){if(m[i]||!d[i])continue;const o=i*4;if(p[o]<100&&p[o+1]<95&&p[o+2]<105)m[i]=1;}
 m=fillHoles(m);
 return m;
}
function windowMask(){const c=createCanvas(W,H),x=c.getContext('2d');x.fillStyle='white';x.beginPath();x.moveTo(342,0);x.lineTo(768,0);x.lineTo(768,316);x.lineTo(364,252);x.quadraticCurveTo(342,248,341,222);x.closePath();x.fill();const p=x.getImageData(0,0,W,H).data;return Uint8Array.from({length:N},(_,i)=>p[i*4+3]);}
function rgba(a){const p=new Uint8ClampedArray(N*4);for(let i=0;i<N;i++){p[i*4]=p[i*4+1]=p[i*4+2]=255;p[i*4+3]=a[i]?255:0;}return p;}
async function save(p,file){const c=createCanvas(W,H);c.getContext('2d').putImageData(new ImageData(p,W,H),0,0);const bytes=await c.encode('png');fs.writeFileSync(file,bytes,{flag:'wx'});return sha(bytes);}
function blend(a,b,t){return Math.round(a+(b-a)*t);}
async function main(){const [mode,out]=process.argv.slice(2);if(!['samples','all'].includes(mode)||!out||fs.existsSync(out))throw Error('Use samples|all and a fresh output directory');fs.mkdirSync(out,{recursive:true});const started=Date.now(),first=pixels(await loadImage(path.join(FRAMES,'frame-0001.png'))),plate=pixels(await loadImage(PLATE),true),pan=await loadImage(PANORAMA),old=dilate(matte(first,0),1),glass=windowMask();const reports=[];
 const selected=mode==='all'?Array.from({length:129},(_,i)=>i):[0,16,24,32,48,64,96,104,112,128];
 const pc=createCanvas(W,H),px=pc.getContext('2d');
 for(const f of selected){const sourcePath=path.join(FRAMES,`frame-${String(f+1).padStart(4,'0')}.png`),p=pixels(await loadImage(sourcePath)),m=matte(p,f),result=new Uint8ClampedArray(first);let area=0;
  // One rigid crop; independent exterior motion at 18 native pixels/second.
  const scale=320/pan.height;px.clearRect(0,0,W,H);px.drawImage(pan,330-f/25*18,0,pan.width*scale,320);const city=px.getImageData(0,0,W,H).data;
  for(let y=0;y<H;y++)for(let x=0;x<W;x++){const i=y*W+x,o=i*4;const fade=Math.max(0,Math.min(1,(298-y)/24));
   // Erase only the original moving support; preserve the original lower body/hands/props.
   if(old[i])for(let c=0;c<3;c++)result[o+c]=blend(first[o+c],plate[o+c],fade);
   if(glass[i])for(let c=0;c<3;c++)result[o+c]=blend(result[o+c],city[o+c],glass[i]/255);
   if(m[i]){area++;for(let c=0;c<3;c++)result[o+c]=blend(result[o+c],p[o+c],fade);}
   // Exact original cup, including its top overlapping the glass.
   if(x>=375&&x<=431&&y>=252&&y<=322)for(let c=0;c<3;c++)result[o+c]=first[o+c];
  }
  const name=`frame-${String(f+1).padStart(4,'0')}.png`;const digest=await save(result,path.join(out,name));
  if(mode==='samples')await save(rgba(m),path.join(out,`matte-${String(f+1).padStart(4,'0')}.png`));
  reports.push({frame:f,sourceSha256:sha(fs.readFileSync(sourcePath)),outputSha256:digest,matteArea:area});
  await new Promise(resolve=>setImmediate(resolve));
 }
 fs.writeFileSync(path.join(out,'check.json'),JSON.stringify({mode,width:W,height:H,frames:reports,elapsedMs:Date.now()-started,limitations:['Bounded standalone rotoscoping study, not production integration or artistic acceptance.','Existing empty-cabin plate is used only in the original moving support.','One rigid panorama layer; multi-depth corridor and long-duration joins are unproved.']},null,2)+'\n',{flag:'wx'});
 console.log(JSON.stringify({mode,frames:reports.length,elapsedMs:Date.now()-started,out}));
}
module.exports={matte,pixels};
if(require.main===module)main().catch(e=>{console.error(e.stack);process.exitCode=1;});
