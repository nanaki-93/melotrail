'use strict';
// Deterministic preparation of supplied, already painted layers. No invented fill.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root='/Users/marcoandreose/DEV/lab/melotrail',run=path.resolve(__dirname,'..');
const {createCanvas,loadImage}=require(path.join(root,'tools/video-motion/node_modules/@napi-rs/canvas'));
const transport=require(path.join(root,'docs/pictures/video/evidence/VG2-17/metadata-20261002T015459Z/scripts/png_transport.cjs'));
const asset=path.join(root,'docs/pictures/video/tabi-assets/scenario/scenery-20261002T100234Z');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
async function image(file){return loadImage(transport.metadataFree(fs.readFileSync(file)));}
function write(c,file){fs.writeFileSync(path.join(asset,file),c.toBuffer('image/png'),{flag:'wx'});}
function canvas(w,h){return createCanvas(w,h);}
async function main(){
 fs.mkdirSync(asset);const report={sources:[],art:[],scope:'Main-window scenery proof with fixed approved neutral. Small left panes and moving action envelopes are explicitly not admitted.'};
 const paths={plate:path.join(run,'plate-finish/review/neutral-comfy-1080p.png'),far:path.join(root,'docs/pictures/video/tabi-assets/scenario/tokyo-parallax-far-5600x1080.png'),fullFar:path.join(root,'build/tabi-tokyo-long.58RtOJ/finishing/far-correction-x2.png'),middle:path.join(root,'docs/pictures/video/tabi-assets/scenario/tokyo-parallax-middle-5600x1080.png'),near:path.join(root,'docs/pictures/video/tabi-assets/scenario/tokyo-parallax-near-5600x1080.png'),nearNext:path.join(root,'docs/pictures/video/tabi-assets/scenario/tokyo-clockfront-near-v5/tokyo-near-section-v5-candidate-6200x1080.png')};
 for(const [role,p] of Object.entries(paths))report.sources.push({role,path:p,sha256:sha(fs.readFileSync(p))});
 const plate=await image(paths.plate);assert.deepEqual([plate.width,plate.height],[1920,1080]);
 fs.copyFileSync(paths.plate,path.join(asset,'neutral-comfy-1080p.png'),fs.constants.COPYFILE_EXCL);
 // Far extension consumes only the next 250 painted source pixels, at the prior scale.
 // Existing decoded pixels are copied back exactly through x=3279.
 const far=canvas(5600,1080),fx=far.getContext('2d');fx.drawImage(await image(paths.far),0,0);const prior=fx.getImageData(0,0,5600,1080);
 const full=canvas(4800,800),ux=full.getContext('2d');ux.imageSmoothingQuality='high';ux.drawImage(await image(paths.fullFar),0,0,4800,800);
 const extension=ux.getImageData(2500,0,250,800);fx.putImageData(extension,3280,0);
 const after=fx.getImageData(0,0,5600,1080);let protectedDifferences=0;
 for(let y=0;y<1080;y++)for(let x=0;x<3280;x++)for(let k=0;k<4;k++){const i=(y*5600+x)*4+k;if(prior.data[i]!==after.data[i])protectedDifferences++;}
 assert.equal(protectedDifferences,0);write(far,'far-extended-5600x1080.png');
 report.farExtension={oldAuthoredWorldX:[780,3280],newAuthoredWorldX:[780,3530],paintedHeight:800,sourceCropX2:[3200,0,3520,1024],scale:800/1024,protectedDifferences,notes:'No second tower enters the consumed crop; new columns come from the original ComfyUI painting. New 250px are aspect-preserving samples, not padding or stretching.'};
 // Explicit static main-window aperture in coordinates of the approved 1672x941 plate.
 // Preserve the dark frond outline and the entire face, arm, cup, notebook and cabin.
 const glass=canvas(1920,1080),gx=glass.getContext('2d');gx.scale(1920/1672,1080/941);gx.fillStyle='white';gx.beginPath();gx.moveTo(750,0);gx.lineTo(1672,0);gx.lineTo(1672,666);gx.lineTo(921,548);gx.lineTo(920,535);gx.lineTo(909,529);gx.lineTo(840,529);gx.lineTo(828,533);gx.lineTo(787,526);gx.quadraticCurveTo(758,522,757,502);gx.lineTo(753,432);
 for(const p of [[760,419],[762,404],[775,399],[782,386],[772,380],[780,365],[781,348],[777,337],[771,336],[761,346],[761,334],[756,333],[750,340]])gx.lineTo(...p);
 gx.closePath();gx.fill();
 const g=gx.getImageData(0,0,1920,1080),foreground=canvas(1920,1080),cx=foreground.getContext('2d');cx.drawImage(plate,0,0);const data=cx.getImageData(0,0,1920,1080);let aperture=0;
 const occlusion=canvas(1920,1080),ox=occlusion.getContext('2d'),o=ox.createImageData(1920,1080);
 for(let i=0;i<1920*1080;i++){const a=g.data[i*4+3];data.data[i*4+3]=255-a;o.data[i*4]=o.data[i*4+1]=o.data[i*4+2]=255;o.data[i*4+3]=255-a;if(a)aperture++;}
 cx.putImageData(data,0,0);ox.putImageData(o,0,0);write(foreground,'fixed-neutral-foreground.png');write(occlusion,'fixed-neutral-occlusion.png');write(glass,'main-window-aperture.png');
 // Source coverage checks every frame and shutter over the admitted 60-second range.
 const speed=480/599,shutters=[-1/6,0,1/6];let count=0,minLeft=Infinity,minRight=Infinity,minBottom=Infinity;
 let xmin=1920,xmax=0,ymax=0;for(let y=0;y<1080;y++)for(let x=0;x<1920;x++)if(g.data[(y*1920+x)*4+3]){xmin=Math.min(xmin,x);xmax=Math.max(xmax,x);ymax=Math.max(ymax,y);}
 for(let f=0;f<1800;f++)for(const s of shutters){const travel=Math.max(0,Math.min(1799,f+s))*speed;minLeft=Math.min(minLeft,xmin+travel-780);minRight=Math.min(minRight,3530-(xmax+travel+1));minBottom=Math.min(minBottom,800-ymax-1);assert(minLeft>2&&minRight>2&&minBottom>2);count++;}
 report.coverage={frames:1800,fps:30,seconds:60,shutterEvaluations:count,aperturePixels:aperture,apertureBounds:[xmin,0,xmax+1,ymax+1],minimumPaintedMargins:{left:minLeft,right:minRight,bottom:minBottom},speedPixelsPerFrame:speed,depthMultipliers:[1,2,3],referenceScaleUnchanged:true,full180SecondCoverage:false};
 for(const file of fs.readdirSync(asset)){const p=path.join(asset,file);report.art.push({path:p,bytes:fs.statSync(p).size,sha256:sha(fs.readFileSync(p))});}
 fs.writeFileSync(path.join(run,'checks/preparation.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(report.coverage));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
