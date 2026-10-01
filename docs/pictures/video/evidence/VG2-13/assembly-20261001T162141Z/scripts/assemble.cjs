'use strict';
// Standalone static preparation. No Blender, media, production schema or provider dispatch.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const RUN=path.resolve(__dirname,'..'),REPO=process.env.MELOTRAIL_REPO_ROOT;
const OWNER=path.resolve(RUN,'../continuation-20261001-153813Z');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const specs=[
 {id:'forearm_separate',source:47,crop:[772,583,932,737],mask:'M 798 588 Q 829 619 864 628 Q 898 638 926 610 Q 933 661 902 701 Q 869 739 824 731 Q 780 730 778 686 Q 771 638 798 588 Z',anchors:{elbow:[828,693],wrist:[874,619]}},
 {id:'cuff_wrist_overlap',source:47,crop:[807,515,947,638],mask:'M 845 517 Q 832 524 820 540 Q 805 565 818 590 Q 835 616 866 626 Q 904 639 925 613 Q 943 592 943 567 Q 940 548 923 536 L 908 554 Q 880 567 858 553 Q 839 542 845 517 Z',anchors:{base:[874,611],hand:[883,550]}},
 {id:'hand_neutral',source:45,crop:[777,505,855,589],mask:'M 808 532 Q 802 522 814 515 Q 829 507 840 518 Q 855 533 846 550 Q 838 564 832 576 Q 816 590 800 580 Q 781 574 781 560 Q 782 546 798 539 Z',anchors:{wrist:[815,577]}},
 {id:'hand_wrist_beat',source:47,crop:[840,433,956,565],mask:'M 855 515 Q 844 498 850 482 Q 858 470 866 485 L 870 455 Q 871 438 882 441 Q 896 441 891 474 Q 892 444 902 441 Q 918 441 915 461 L 907 484 Q 918 454 929 459 Q 943 465 933 485 L 923 503 Q 934 478 944 484 Q 956 491 944 512 L 923 540 Q 918 550 913 557 L 880 555 Q 860 546 855 515 Z',anchors:{wrist:[883,550]}}
];
const ERASE='M 805 506 Q 833 499 850 516 Q 862 535 860 554 Q 886 574 897 610 Q 915 647 903 690 Q 894 722 862 733 Q 827 744 808 730 L 805 703 Q 785 680 779 659 L 769 644 Q 785 622 794 598 Q 792 582 785 576 Q 773 562 782 544 Q 787 535 805 529 Z';
function transform(point,origin,angle,scale=1){const x=point[0]*scale,y=point[1]*scale;return [origin[0]+x*Math.cos(angle)-y*Math.sin(angle),origin[1]+x*Math.sin(angle)+y*Math.cos(angle)];}
function distance(a,b){return Math.hypot(a[0]-b[0],a[1]-b[1]);}
function pose(upperAngle,forearmAngle,wristAngle,hand){
 assert(['neutral','open'].includes(hand),'unsupported hand');
 assert(upperAngle>=15&&upperAngle<=60&&forearmAngle>=-90&&forearmAngle<=-48&&Math.abs(wristAngle)<=12,'unsupported pose');
 const shoulder=[772,619],upperLength=Math.hypot(45,76),forearmLength=Math.hypot(46,-74);
 const elbow=transform([upperLength,0],shoulder,upperAngle*Math.PI/180);
 const wrist=transform([forearmLength,0],elbow,forearmAngle*Math.PI/180);
 return {shoulder,elbow,wrist,upperAngle,forearmAngle,wristAngle,hand,upperLength,forearmLength};
}
const states=[{id:'neutral',value:pose(59.36,-79.4,0,'neutral')},{id:'lift',value:pose(32,-65,0,'open')},{id:'wrist_in',value:pose(32,-65,-12,'open')},{id:'wrist_out',value:pose(32,-65,12,'open')}];
async function runtime(){
 assert(REPO&&path.isAbsolute(REPO),'absolute repo required');
 const owner=require(OWNER+'/check-preparation.cjs');owner.audit(OWNER);await owner.auditFixed(OWNER);
 const {createCanvas,loadImage,Path2D}=require(path.join(REPO,'tools/video-motion/node_modules/@napi-rs/canvas'));
 const c=JSON.parse(fs.readFileSync(OWNER+'/preparation.json'));
 const getSource=n=>path.join(REPO,c.sources.find(s=>s.path.includes(`/train-actions/${n}-`)).path);
 const normalized=async bytes=>{const im=await owner.rgba(bytes);const canvas=createCanvas(im.width,im.height),ctx=canvas.getContext('2d'),d=ctx.createImageData(im.width,im.height);d.data.set(im.data);for(let i=3;i<d.data.length;i+=4){if(d.data[i]<=8)d.data[i]=0;else if(d.data[i]>=245)d.data[i]=255;}ctx.putImageData(d,0,0);return canvas;};
 return {owner,createCanvas,loadImage,Path2D,c,getSource,normalized};
}
async function prepare(){
 const r=await runtime(),{owner,createCanvas,loadImage,Path2D,getSource,normalized}=r;
 const token=owner.acquire(OWNER);const parts=[];
 async function publish(id,canvas,details){
  const receipt=owner.reserve(OWNER,token,'derivative',id,`parts/${id}_assembly_v1.png`).receipt;
  const bytes=canvas.toBuffer('image/png');owner.createOutput(OWNER,token,receipt.id,bytes);
  const im=await owner.rgba(bytes);const record={id,receipt,sha256:sha(bytes),width:im.width,height:im.height,alpha:owner.alphaFacts(im.data,im.width,im.height),...details};parts.push(record);return record;
 }
 try{
  for(const spec of specs){
   const [l,t,rr,b]=spec.crop,canvas=createCanvas(rr-l,b-t),ctx=canvas.getContext('2d');
   ctx.translate(-l,-t);ctx.clip(new Path2D(spec.mask));ctx.drawImage(await normalized(fs.readFileSync(getSource(spec.source))),0,0);
   await publish(spec.id,canvas,{source:path.relative(REPO,getSource(spec.source)),sourceSha256:sha(fs.readFileSync(getSource(spec.source))),crop:spec.crop,mask:spec.mask,anchors:Object.fromEntries(Object.entries(spec.anchors).map(([k,v])=>[k,[v[0]-l,v[1]-t]])),alphaPolicy:'source alpha <=8 cleared, >=245 opaque; clipped to explicit selected contour; no RGB painting'});
  }
  const canvas=createCanvas(128,64),ctx=canvas.getContext('2d');
  const sleeve=await loadImage(OWNER+'/parts/upper_sleeve_candidate_v1.png');
  for(const x of [32,96]){ctx.save();ctx.beginPath();ctx.ellipse(x,32,28,28,0,0,Math.PI*2);ctx.clip();ctx.drawImage(sleeve,540,420,160,160,x-28,4,56,56);ctx.restore();}
  await publish('hidden_shoulder_elbow_overlap',canvas,{source:'parts/upper_sleeve_candidate_v1.png',sourceSha256:sha(fs.readFileSync(OWNER+'/parts/upper_sleeve_candidate_v1.png')),anchors:{shoulder:[32,32],elbow:[96,32]},purpose:'Two hidden circular fabric support patches; no new anatomy. World size fixed at registration.'});
  const neutral=await loadImage(getSource(48)),cabin=await loadImage(getSource(28)),headSpec=r.c.fixedFoundation.parts[0];
  const base=createCanvas(1920,1080),bctx=base.getContext('2d');bctx.drawImage(neutral,0,0);
  bctx.save();bctx.clip(new Path2D(ERASE));bctx.drawImage(cabin,0,0);
  const panelBytes=fs.readFileSync(OWNER+'/attempts/revealed_torso_cabin_backing-3.png');
  const panel=await normalized(panelBytes);
  bctx.save();bctx.clip(new Path2D('M 742 610 Q 782 611 801 637 Q 821 670 817 735 L 758 745 Z'));
  bctx.drawImage(panel,210,180,840,910,727,610,112,121.3333333333);bctx.restore();
  const raised=await loadImage(getSource(47));bctx.save();bctx.clip(new Path2D('M 736 563 L 803 527 L 812 541 L 803 578 L 782 621 L 750 644 Z'));bctx.drawImage(raised,0,0);bctx.restore();
  bctx.drawImage(await loadImage(OWNER+'/'+headSpec.path),...headSpec.placement);
  bctx.restore();
  await publish('revealed_torso_cabin_backing',base,{reference:path.relative(REPO,getSource(48)),referenceSha256:sha(fs.readFileSync(getSource(48))),eraseMask:ERASE,panelSource:'attempts/revealed_torso_cabin_backing-3.png',panelSha256:sha(panelBytes),role:'Fixed five-second scene backing with original arm removed only inside the declared mask; not neutral48 used as a body layer or a long-film scenery solution.',protected:'Exact decoded RGB outside the explicit erase mask; old head/props/contact remain unchanged outside it.'});
  const record={schema:'standalone-static-proof-1',parts,states,foreground:r.c.sources.find(s=>s.path==='build/vg2-character-art-a3/full-foreground.png'),upperSleeve:{path:'parts/upper_sleeve_candidate_v1.png',sha256:sha(fs.readFileSync(OWNER+'/parts/upper_sleeve_candidate_v1.png')),anchors:{shoulder:[628,264],elbow:[635,984]}},readiness:'Unreviewed derivatives; static support checks and appearance pending. Not an app schema.'};
  fs.writeFileSync(RUN+'/checks/parts.json',JSON.stringify(record,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify(owner.audit(OWNER)));
 }finally{owner.release(OWNER,token);}
}
if(require.main===module){assert.deepEqual(process.argv.slice(2),['--prepare']);prepare().catch(e=>{console.error(e);process.exitCode=1});}
module.exports={transform,distance,pose,states,specs,ERASE,runtime,RUN,OWNER,sha};
