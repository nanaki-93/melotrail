'use strict';
// Read six retained inputs; check derivatives in memory. No PNG output or media tool.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const BASE=path.resolve(__dirname,'..'),REPO=path.resolve(BASE,'../../../../../..');
const png=require('./png_transport.cjs'),{createCanvas,loadImage}=require(REPO+'/tools/video-motion/node_modules/@napi-rs/canvas');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
async function pixels(bytes){const im=await loadImage(png.metadataFree(bytes)),c=createCanvas(im.width,im.height),ctx=c.getContext('2d');ctx.drawImage(im,0,0);return ctx.getImageData(0,0,im.width,im.height).data;}
async function main(){
  assert.deepEqual(process.argv.slice(2),['--check']);
  const scope=JSON.parse(fs.readFileSync(BASE+'/inputs/scope.json')),origin=scope.failedColourOwner+'/live';
  const source=JSON.parse(fs.readFileSync(origin+'/source-pixels.json')),frames=[];
  assert.equal(source.frames.length,6);
  for(const frame of source.frames){
    const file=origin+`/encoded_input/frame-${String(frame.frame).padStart(4,'0')}.png`,original=fs.readFileSync(file);
    assert.equal(sha(original),frame.convertedSha256);assert.throws(()=>png.requireRec709(original));
    const corrected=png.rec709(original);png.requireRec709(corrected);
    assert.deepEqual(png.idat(corrected),png.idat(original));assert.deepEqual(await pixels(corrected),await pixels(original));
    frames.push({frame:frame.frame,originalSha256:sha(original),inMemoryCorrectedSha256:sha(corrected),idatSha256:sha(png.idat(original)),pngCicp:[1,1,0,1],changedPixelCodes:0});
  }
  console.log(JSON.stringify({status:'SIX_SAVED_FRAMES_METADATA_ONLY_PASS',nativeToolsLaunched:0,outputPngFiles:0,frames},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
