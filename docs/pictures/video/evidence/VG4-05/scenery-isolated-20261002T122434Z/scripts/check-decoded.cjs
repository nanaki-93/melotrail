'use strict';
// Inspect the one retained decode against the actual frozen encoder input.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const repo='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..');
const {createCanvas,loadImage}=require(path.join(repo,'tools/video-motion/node_modules/@napi-rs/canvas'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const W=1920,H=1080,face={x:560,y:310,w:270,h:265};
function compare(a,b){let absolute=0,squared=0,faceAbs=0,faceCount=0;
 for(let y=0;y<H;y++)for(let x=0;x<W;x++){const offset=(y*W+x)*4,selected=x>=face.x&&x<face.x+face.w&&y>=face.y&&y<face.y+face.h;for(let c=0;c<3;c++){const d=Math.abs(a[offset+c]-b[offset+c]);absolute+=d;squared+=d*d;if(selected){faceAbs+=d;faceCount++;}}}
 const mae=absolute/(W*H*3),psnr=10*Math.log10(255**2/(squared/(W*H*3)));return {mae,psnr,faceMae:faceAbs/faceCount};
}
const pass=f=>f.mae<20&&f.faceMae<18;
async function main(){
 const output=path.join(run,'render/project/controlled-output'),attempts=fs.readdirSync(output).filter(n=>n.startsWith('attempt-'));assert.equal(attempts.length,1);
 const source=path.join(output,attempts[0],'encode-frames'),decoded=path.join(run,'review/decoded');
 assert.equal(fs.readdirSync(source).filter(n=>n.endsWith('.png')).length,300);assert.equal(fs.readdirSync(decoded).filter(n=>n.endsWith('.png')).length,300);
 const left=createCanvas(W,H),right=createCanvas(W,H),lc=left.getContext('2d'),rc=right.getContext('2d');
 const sampleNumbers=[0,58,59,60,61,149,239,299],sheet=createCanvas(1280,1440),sc=sheet.getContext('2d'),rows=[];
 let previous=null,duplicates=0,negative=null;
 for(let i=0;i<300;i++){
  const a=fs.readFileSync(path.join(source,`frame-${String(i).padStart(8,'0')}.png`)),b=fs.readFileSync(path.join(decoded,`frame-${String(i+1).padStart(4,'0')}.png`));
  const [ai,bi]=await Promise.all([loadImage(a),loadImage(b)]);assert.deepEqual([ai.width,ai.height,bi.width,bi.height],[W,H,W,H]);
  lc.clearRect(0,0,W,H);rc.clearRect(0,0,W,H);lc.drawImage(ai,0,0);rc.drawImage(bi,0,0);
  const ap=lc.getImageData(0,0,W,H).data,bp=rc.getImageData(0,0,W,H).data,facts=compare(ap,bp),hash=sha(bp);if(hash===previous)duplicates++;previous=hash;
  rows.push({frame:i,absoluteFrame:1170+i,...facts,structuralRetentionPass:pass(facts),sourceSha256:sha(a),decodedSha256:sha(b)});
  if(i===60){const damaged=new Uint8ClampedArray(bp);for(let y=face.y;y<face.y+face.h;y++)for(let x=face.x;x<face.x+face.w;x++){const o=(y*W+x)*4;damaged[o]=damaged[o+1]=damaged[o+2]=0;}negative=compare(ap,damaged);assert(!pass(negative),'Face dropout must be rejected');}
  if(sampleNumbers.includes(i)){const index=sampleNumbers.indexOf(i);sc.drawImage(bi,index%2*640,Math.floor(index/2)*360,640,360);sc.fillStyle='#111';sc.fillRect(index%2*640,Math.floor(index/2)*360,210,25);sc.fillStyle='white';sc.font='17px sans-serif';sc.fillText(`clip ${(i/30).toFixed(2)}s / #${1170+i}`,index%2*640+8,Math.floor(index/2)*360+19);}
  await new Promise(resolve=>setImmediate(resolve));
 }
 const report={status:rows.every(r=>r.structuralRetentionPass)&&duplicates===0?'PASS':'FAIL',frames:300,dimensions:[W,H],noConsecutiveIdenticalDecodedFrames:duplicates===0,faceRegion:face,maximumFaceMae:Math.max(...rows.map(r=>r.faceMae)),maximumFrameMae:Math.max(...rows.map(r=>r.mae)),minimumPsnr:Math.min(...rows.map(r=>r.psnr)),negativeFaceDropout:negative,rows,scope:'All decoded frames compared with frozen encode inputs. Thresholds detect gross damage/dropout; artistic motion quality and compression acceptability remain human review.'};
 fs.writeFileSync(path.join(run,'checks/decoded-quality.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx'});fs.writeFileSync(path.join(run,'review/decoded-contact-sheet.png'),sheet.toBuffer('image/png'),{flag:'wx'});
 console.log(JSON.stringify({status:report.status,frames:300,maximumFaceMae:report.maximumFaceMae,minimumPsnr:report.minimumPsnr}));assert.equal(report.status,'PASS');
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
