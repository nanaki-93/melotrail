'use strict';
const assert=require('node:assert/strict');
function expand(mask,margin){
  assert.equal(margin,1,'only the proposed one-pixel support margin is allowed');
  assert(Number.isInteger(mask.width)&&mask.width>0&&Number.isInteger(mask.height)&&mask.height>0);
  assert.equal(mask.data.length,mask.width*mask.height*4);
  const data=new Uint8ClampedArray(mask.data.length);
  for(let y=0;y<mask.height;y++)for(let x=0;x<mask.width;x++)if(mask.data[(y*mask.width+x)*4+3]){
    for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
      const a=x+dx,b=y+dy;if(a<0||a>=mask.width||b<0||b>=mask.height)continue;
      const i=(b*mask.width+a)*4;data[i]=data[i+1]=data[i+2]=data[i+3]=255;
    }
  }
  return {width:mask.width,height:mask.height,data};
}
module.exports={expand};
