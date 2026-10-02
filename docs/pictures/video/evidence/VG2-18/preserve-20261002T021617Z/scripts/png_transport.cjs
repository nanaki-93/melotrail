'use strict';
// A transport metadata change, after numeric sRGB -> Rec.709 conversion.
// The PNG decoder in pinned FFmpeg revision bf1b838 reads these cICP fields.
const assert=require('node:assert/strict');
const SIGNATURE=Buffer.from([137,80,78,71,13,10,26,10]);
const COLOUR=new Set(['cICP','sRGB','iCCP','gAMA','cHRM','cLLI','mDCV','cLLi','mDCv']);
const table=Uint32Array.from({length:256},(_,i)=>{let c=i;for(let j=0;j<8;j++)c=(c&1)?(0xedb88320^(c>>>1)):(c>>>1);return c>>>0;});
function crc32(bytes){let c=0xffffffff;for(const x of bytes)c=table[(c^x)&255]^(c>>>8);return (c^0xffffffff)>>>0;}
function chunks(bytes){
  assert(Buffer.isBuffer(bytes)&&bytes.subarray(0,8).equals(SIGNATURE),'PNG signature');
  const out=[];let offset=8;
  while(offset<bytes.length){
    assert(offset+12<=bytes.length,'truncated PNG chunk');const n=bytes.readUInt32BE(offset),end=offset+n+12;
    assert(end<=bytes.length,'PNG chunk length');const type=bytes.toString('ascii',offset+4,offset+8);
    assert(/^[A-Za-z]{4}$/.test(type),'PNG chunk type');
    assert.equal(crc32(bytes.subarray(offset+4,end-4)),bytes.readUInt32BE(end-4),'PNG CRC');
    out.push({type,data:bytes.subarray(offset+8,end-4),bytes:bytes.subarray(offset,end)});offset=end;
    if(type==='IEND'){assert.equal(offset,bytes.length,'trailing PNG data');break;}
  }
  assert.equal(out[0]?.type,'IHDR');assert.equal(out[0].data.length,13);
  assert.equal(out.filter(c=>c.type==='IHDR').length,1);
  assert.equal(out.at(-1)?.type,'IEND');assert.equal(out.at(-1).data.length,0);
  assert(out.some(c=>c.type==='IDAT'),'PNG image data missing');
  assert(!out.some(c=>['acTL','fcTL','fdAT'].includes(c.type)),'animated PNG unsupported');
  return out;
}
function chunk(type,data){const head=Buffer.from(type),out=Buffer.alloc(data.length+12);out.writeUInt32BE(data.length);head.copy(out,4);data.copy(out,8);out.writeUInt32BE(crc32(Buffer.concat([head,data])),out.length-4);return out;}
function metadataFree(bytes){return Buffer.concat([SIGNATURE,...chunks(bytes).filter(c=>!COLOUR.has(c.type)).map(c=>c.bytes)]);}
function rec709(bytes){
  const parts=chunks(bytes).filter(c=>!COLOUR.has(c.type));
  // Primaries 1, transfer 1, RGB matrix 0, full-range flag 1.
  return Buffer.concat([SIGNATURE,parts[0].bytes,chunk('cICP',Buffer.from([1,1,0,1])),...parts.slice(1).map(c=>c.bytes)]);
}
function requireRec709(bytes){
  const parts=chunks(bytes),colour=parts.filter(c=>COLOUR.has(c.type));
  assert.equal(colour.length,1,'ambiguous/missing PNG colour declaration');
  assert.equal(colour[0].type,'cICP');assert.deepEqual(colour[0].data,Buffer.from([1,1,0,1]));
  assert(parts.indexOf(colour[0])<parts.findIndex(c=>c.type==='IDAT'),'late colour metadata');
}
function idat(bytes){return Buffer.concat(chunks(bytes).filter(c=>c.type==='IDAT').map(c=>c.bytes));}
module.exports={crc32,chunks,metadataFree,rec709,requireRec709,idat,chunk,SIGNATURE};
