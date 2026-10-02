'use strict';
// Explicit signal conversion: sRGB -> linear light -> Rec.709 OETF.
// References: https://www.w3.org/Graphics/Color/srgb
// https://www.itu.int/rec/R-REC-BT.709/en
const clamp=x=>Math.max(0,Math.min(1,x));
const srgbToLinear=v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4;
const linearToSrgb=v=>v<=.0031308?v*12.92:1.055*v**(1/2.4)-.055;
const linearTo709=v=>v<.018?4.5*v:1.099*v**.45-.099;
const rec709ToLinear=v=>v<.081?v/4.5:((v+.099)/1.099)**(1/.45);
const to709=v=>Math.round(255*clamp(linearTo709(srgbToLinear(v/255))));
const toSrgb=v=>Math.round(255*clamp(linearToSrgb(rec709ToLinear(v/255))));
const forward=Uint8Array.from({length:256},(_,i)=>to709(i));
const inverse=Uint8Array.from({length:256},(_,i)=>toSrgb(i));
function convert(data,lut=forward){const out=new Uint8ClampedArray(data);for(let i=0;i<out.length;i+=4){for(let c=0;c<3;c++)out[i+c]=lut[out[i+c]];}return out;}
module.exports={srgbToLinear,linearToSrgb,linearTo709,rec709ToLinear,forward,inverse,convert};
