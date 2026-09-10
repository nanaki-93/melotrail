// Local cutout study. No network calls, model, soundtrack, or MIDI dependencies.
// NODE_PATH=<installed sharp modules> node render.cjs <temporary frames directory>
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const OUT = __dirname;
const source = path.join(OUT, '..', 'tabi-eki-channel-banner-final-v3-master.png');
const framesDir = process.argv[2];
const FPS = 24, COUNT = 192, W = 1672, H = 940;
const smooth = x => { x=Math.max(0, Math.min(1,x)); return x*x*(3-2*x); };
function motion(t) {
  const lift = smooth((t-1.0)/1.8)*(1-smooth((t-4.8)/2.0));
  const tilt = smooth((t-3.0)/0.6)*(1-smooth((t-4.2)/0.6));
  return {lift, tilt, x:479+2*lift, y:671-137*lift};
}
function overlay(t, fabric) {
  const {lift,tilt,x,y}=motion(t), ly=y+14, lx=x-36, rx=x+36;
  const steam=Array.from({length:3},(_,i)=>{
    const phase=(t/2.0+i/3)%1, sy=y-25-phase*47;
    const sx=x-17+i*15+Math.sin(t*2+i)*3;
    return `<path d="M${sx},${sy} c-9,-10 10,-15 0,-26" fill="none" stroke="#fff2d5" stroke-width="2" opacity="${0.4*Math.sin(Math.PI*phase)*(1-.85*tilt)}"/>`;
  }).join('');
  return `<svg width="${W}" height="${H}" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <pattern id="cloth" width="32" height="32" patternUnits="userSpaceOnUse"><image href="data:image/png;base64,${fabric}" width="32" height="32"/></pattern>
    <pattern id="spots" width="17" height="16" patternUnits="userSpaceOnUse" patternTransform="rotate(12)"><rect width="17" height="16" fill="#dba36d"/><path d="M2 3l3-1 2 3-2 3-3-1z M11 10l3-1 2 2-1 3-3 0z" fill="#896452" stroke="#3e3042" stroke-width="1.7"/></pattern>
    <linearGradient id="cup" x2="1" y2="0"><stop stop-color="#e2c7a0"/><stop offset=".3" stop-color="#f7e4bc"/><stop offset=".75" stop-color="#f8e8c6"/><stop offset="1" stop-color="#e4cca9"/></linearGradient>
  </defs>
  <!-- Restore coat underneath the two original resting hands; retain legs and silhouette. -->
  <path d="M326 590 Q350 579 377 596 L468 627 Q511 593 543 610 L551 696 Q534 711 505 711 L440 708 Q403 690 364 677 Q341 649 326 590Z" fill="url(#cloth)"/>
  <path d="M475 590L479 706" fill="none" stroke="#d8a06b" stroke-width="8"/><path d="M475 590L479 706" fill="none" stroke="#3d3040" stroke-width="1.8" stroke-dasharray="3 6"/>
  <!-- Arms keep their shoulders attached to the original coat. -->
  <path d="M522 591 Q555 608 553 641 Q551 659 537 645 L${rx-8} ${ly+15} Q${rx-19} ${ly-3} ${rx-1} ${ly-17} L514 617 Q510 601 522 591Z" fill="url(#cloth)" stroke="#352d38" stroke-width="2.2"/>
  <path d="M357 590 Q330 610 342 642 Q350 661 367 646 L${lx+7} ${ly+14} Q${lx+18} ${ly-3} ${lx+1} ${ly-17} L384 615 Q379 599 357 590Z" fill="url(#cloth)" stroke="#352d38" stroke-width="2.2"/>
  <g transform="translate(${x} ${y}) rotate(${-8*tilt})">
    <!-- Handle, ceramic body, elliptical coffee surface. -->
    <path d="M33-10 C65-17 65 27 36 26" fill="none" stroke="#3d3044" stroke-width="12"/>
    <path d="M33-10 C65-17 65 27 36 26" fill="none" stroke="#e8cba6" stroke-width="7"/>
    <path d="M-37-19 Q-38 9-31 26 Q-25 39 0 40 Q27 40 33 26 L38-19Z" fill="url(#cup)" stroke="#3d3044" stroke-width="2.3"/>
    <path d="M-28 25Q-8 33 24 28" fill="none" stroke="#d4b796" stroke-width="1.4"/>
    <ellipse cx="0" cy="-19" rx="38" ry="10" fill="#f9e9c9" stroke="#3d3044" stroke-width="2"/>
    <ellipse cx="0" cy="-18" rx="31" ry="6" fill="#705043"/>
    <path d="M-20-19Q-4-24 16-19" fill="none" stroke="#c19774" stroke-width="1.4"/>
    <path d="M-28-6L-26 14" stroke="#fff2d7" stroke-width="3" opacity=".75" stroke-linecap="round"/>
  </g>
  <!-- Cuffs and paws wrap around the cup; the originals are covered, not duplicated. -->
  <g transform="translate(${lx-5} ${ly+2}) rotate(${-10-20*lift})">
    <path d="M-18-15Q-12-23 4-18L12 13Q-2 21-16 14Z" fill="url(#spots)" stroke="#3d3044" stroke-width="1.8"/>
    <path d="M5-14C12-22 29-18 31-8Q36 4 26 12Q12 18 8 8Z" fill="#b6a0b8" stroke="#3d3044" stroke-width="1.8"/>
    <path d="M21-6Q27 1 24 8M14-3Q21 3 18 11" fill="none" stroke="#665168" stroke-width="1.4"/>
  </g>
  <g transform="translate(${rx+5} ${ly+1}) rotate(${12+15*lift})">
    <path d="M-3-17Q13-21 18-12L15 17Q0 22-9 13Z" fill="url(#spots)" stroke="#3d3044" stroke-width="1.8"/>
    <path d="M-5-14C-14-19-26-16-28-5Q-33 7-22 13Q-10 18-5 7Z" fill="#b6a0b8" stroke="#3d3044" stroke-width="1.8"/>
    <path d="M-20-5Q-25 3-22 9M-13-3Q-20 4-17 11" fill="none" stroke="#665168" stroke-width="1.4"/>
  </g>
  ${steam}
  </svg>`;
}
(async()=>{
  if(!framesDir) throw Error('Pass a temporary frames directory');
  fs.mkdirSync(framesDir,{recursive:true});
  const fabric=await sharp(source).extract({left:351,top:578,width:24,height:24}).resize(32,32).png().toBuffer();
  const base=await sharp(source).raw().toBuffer();
  for(let i=0;i<COUNT;i++) {
    const layer=Buffer.from(overlay(i/FPS,fabric.toString('base64')));
    await sharp(base,{raw:{width:W,height:H,channels:3}}).composite([{input:layer}]).png().toFile(path.join(framesDir,`${String(i).padStart(4,'0')}.png`));
    if([0,84,120,191].includes(i)) fs.copyFileSync(path.join(framesDir,`${String(i).padStart(4,'0')}.png`),path.join(OUT,`review-${String(i).padStart(4,'0')}.png`));
    if(i%48===0) process.stdout.write(`Rendered ${i}/${COUNT}\n`);
  }
  process.stdout.write(`Rendered ${COUNT} frames: ${framesDir}\n`);
})().catch(e=>{console.error(e);process.exit(1)});
