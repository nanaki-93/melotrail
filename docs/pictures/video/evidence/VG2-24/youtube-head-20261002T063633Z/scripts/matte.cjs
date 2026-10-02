'use strict';
// Rotoscope the existing native video; this does not create character artwork.
const W=768,H=448,N=W*H;
function dilate(a,r){const b=new Uint8Array(N);for(let y=1;y<H-1;y++)for(let x=1;x<W-1;x++){if(!a[y*W+x])continue;for(let dy=-r;dy<=r;dy++)for(let dx=-r;dx<=r;dx++){if(dx*dx+dy*dy<=r*r&&x+dx>=0&&x+dx<W&&y+dy>=0&&y+dy<H)b[(y+dy)*W+x+dx]=1;}}return b;}
function fillHoles(a){const seen=new Uint8Array(N),q=new Int32Array(N);let start=0,end=0;q[end++]=0;seen[0]=1;while(start<end){const i=q[start++],x=i%W,y=(i/W)|0;for(const j of [x?i-1:-1,x<W-1?i+1:-1,y?i-W:-1,y<H-1?i+W:-1]){if(j<0||seen[j]||a[j])continue;seen[j]=1;q[end++]=j;}}return a.map((v,i)=>v||!seen[i]?1:0);}
function largest(a){const seen=new Uint8Array(N),q=new Int32Array(N);let best=[];for(let i=0;i<N;i++){if(!a[i]||seen[i])continue;let start=0,end=0;q[end++]=i;seen[i]=1;while(start<end){let j=q[start++],x=j%W,y=(j/W)|0;for(const k of [x?j-1:-1,x<W-1?j+1:-1,y?j-W:-1,y<H-1?j+W:-1])if(k>=0&&!seen[k]&&a[k]){seen[k]=1;q[end++]=k;}}if(end>best.length)best=Array.from(q.subarray(0,end));}const out=new Uint8Array(N);for(const i of best)out[i]=1;return out;}
function boundaryAt(frame){
 const knots=[[0,142,378,78,259],[17,142,378,78,259],[24,126,349,77,255],[32,105,318,86,247],[48,87,303,84,251],[64,85,302,80,253],[96,85,302,80,253],[104,107,322,83,255],[112,142,377,78,259],[128,142,377,78,259]];
 let k=0;while(k<knots.length-2&&knots[k+1][0]<frame)k++;const a=knots[k],b=knots[k+1],u=(frame-a[0])/(b[0]-a[0]);return a.slice(1).map((v,i)=>v+(b[i+1]-v)*u);
}
function seededMatte(p,frame){
 const [left,nominalRight,top,bottom]=boundaryAt(frame),right=nominalRight+32,seed=new Uint8Array(N);
 for(let y=Math.floor(top);y<304;y++)for(let x=Math.floor(Math.min(left,128));x<Math.ceil(Math.max(right,376));x++){
  const i=y*W+x,o=i*4,r=p[o],g=p[o+1],b=p[o+2];
  const turn=Math.max(0,Math.min(1,(378-nominalRight)/76));
  const headLeft=y<100?Math.max(left,180):y<116?left+40-16*turn:y<145?Math.max(left,105):left;
  const head=y<bottom&&x>=headLeft&&x<right;
  // The seat has low green; skin/fronds have brighter green and a pink/lilac hue.
  const pink=head&&r>130&&g>94&&r-g>12&&b-g> -23;
  const coral=head&&r>135&&r-g>45&&b-g> -10&&y<230;
  const gold=y>205&&y<281&&x>143&&x<347&&r>95&&g>63&&r-g>27&&g-b>43;
  const coat=y>229&&x>126&&x<360&&r<95&&g>r*1.35&&g>b*1.08;
  // A star/highlight can be warm yellow all the way to the forehead outline.
  // Constrain this exception to the face interior, never the fronds/sky.
  const cx=287+(244-287)*turn,cy=193+(169-193)*turn;
  const faceInterior=((x-cx)/52)**2+((y-cy)/54)**2<1;
  const warmFace=head&&faceInterior&&r>218&&g>148&&r-g>27&&g-b<51;
  if(pink||coral||gold||coat||warmFace)seed[i]=1;
 }
 let m=fillHoles(largest(dilate(seed,2)));
 // Include the ink outline immediately outside the colored interior.
 const d=dilate(m,2);
 for(let i=0;i<N;i++){if(m[i]||!d[i])continue;const o=i*4;if(p[o]<100&&p[o+1]<95&&p[o+2]<105)m[i]=1;}
 m=fillHoles(m);
 return m;
}

const FRONT=[[78,224],[110,278],[118,328],[122,359],[136,370],[143,367],[150,370],[155,367],[164,365],[173,359],[180,347],[185,366],[193,368],[200,367],[210,361],[217,355],[222,348],[230,342],[242,335],[250,318],[260,319],[270,331],[280,343],[300,372]];
const F24=[[78,222],[110,300],[116,333],[124,344],[137,349],[150,348],[160,343],[175,344],[188,342],[200,342],[211,338],[225,333],[237,327],[250,317],[260,327],[280,359],[300,373]];
const F28=[[78,309],[92,310],[101,317],[112,322],[125,324],[140,325],[152,326],[164,326],[180,324],[197,325],[211,321],[222,319],[230,311],[250,322],[260,329],[280,359],[300,373]];
const F32=[[78,275],[90,282],[104,292],[112,295],[122,300],[135,307],[150,311],[163,314],[177,314],[188,318],[200,316],[211,309],[221,301],[230,307],[242,315],[260,329],[280,358],[300,373]];
const WATCH=[[78,252],[90,252],[100,263],[110,262],[120,270],[130,277],[140,283],[150,289],[160,293],[173,296],[182,300],[194,295],[205,286],[215,270],[224,295],[232,300],[241,305],[250,315],[260,326],[280,358],[300,373]];
const F100=[[78,293],[90,303],[103,310],[115,313],[130,314],[146,317],[164,320],[180,319],[195,315],[210,310],[222,301],[232,305],[250,320],[260,330],[280,359],[300,373]];
const F104=[[78,215],[99,276],[110,325],[118,344],[130,349],[140,349],[153,345],[160,338],[170,346],[185,343],[198,340],[210,334],[223,331],[239,315],[250,322],[260,333],[280,359],[300,373]];
const GUIDES=[[0,FRONT],[20,FRONT],[24,F24],[28,F28],[32,F32],[48,WATCH],[96,WATCH],[100,F100],[104,F104],[108,FRONT],[128,FRONT]];
function rowAt(points,y){if(y<=points[0][0])return points[0][1];for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i];if(y<=b[0])return a[1]+(b[1]-a[1])*(y-a[0])/(b[0]-a[0]);}return points.at(-1)[1];}
function guideAt(frame,y){let k=0;while(k<GUIDES.length-2&&GUIDES[k+1][0]<frame)k++;const a=GUIDES[k],b=GUIDES[k+1],u=(frame-a[0])/(b[0]-a[0]);return rowAt(a[1],y)*(1-u)+rowAt(b[1],y)*u;}
function inkEdge(p,frame){
 const first=112,last=248,radius=6,size=radius*2+1,history=[],costs=[];let prev=new Float64Array(size),oldGuide=guideAt(frame,first);
 for(let y=first;y<=last;y++){
  const guide=guideAt(frame,y),center=Math.round(guide),now=new Float64Array(size),back=new Int16Array(size);
  for(let j=0;j<size;j++){
   const x=center+j-radius,o=(y*W+x)*4,L=(p[o]+p[o+1]+p[o+2])/3;
   const left=(y*W+x-2)*4,right=(y*W+x+2)*4;
   const contrast=Math.abs((p[left]+p[left+1]+p[left+2])-(p[right]+p[right+1]+p[right+2]))/3;
   const local=L*.09+Math.abs(x-guide)*1.7-Math.min(80,contrast)*.045;
   let best=Infinity,from=0;
   for(let k=0;k<size;k++){const delta=(x-(Math.round(oldGuide)+k-radius))-(guide-oldGuide);const v=prev[k]+Math.abs(delta)*.65;if(v<best){best=v;from=k;}}
   now[j]=local+(y===first?0:best);back[j]=from;
  }
  history.push(back);costs.push(now);prev=now;oldGuide=guide;
 }
 let pos=0;for(let j=1;j<size;j++)if(prev[j]<prev[pos])pos=j;
 const edge=new Float32Array(H);for(let y=0;y<H;y++)edge[y]=guideAt(frame,y);
 for(let y=last;y>=first;y--){edge[y]=Math.round(guideAt(frame,y))+pos-radius+1.25;pos=history[y-first][pos];}
 return edge;
}
function matte(p,frame){
 const seed=seededMatte(p,frame),edge=inkEdge(p,frame),alpha=new Uint8ClampedArray(N);
 for(let y=0;y<H;y++)for(let x=0;x<W;x++){
  const i=y*W+x;if(!seed[i])continue;
  // Stroke-constrained edge, not an interpolated rectangular head crop.
  const contour=y>=249?rowAt([[249,336],[258,340],[270,350],[280,370],[300,376],[310,376]],y):y>=112?edge[y]:guideAt(frame,y)+2;
  if(x>contour+1)continue;
  let a=255*Math.max(0,Math.min(1,contour+1-x));
  // A half-pixel antialias only at the measured outline.
  if(x&&x<W-1&&y&&y<H-1){const n=seed[i-1]+seed[i+1]+seed[i-W]+seed[i+W];if(n<4)a*=.6+.1*n;}
  alpha[i]=Math.round(a);
 }
 return {alpha,edge};
}
module.exports={matte,seededMatte,guideAt,inkEdge,dilate,W,H};
