'use strict';
// Bounded local derivative of one raw VG2-13 candidate; no inference/media dispatch.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const ROOT = path.resolve(__dirname, '..');
const OWNER = path.resolve(ROOT, '../continuation-20261001-153813Z');
const RAW_SHA = '64858095d4e71cbd679caa42168b3ec2cdd4508c28a8cbc6584549ad6e07069a';
const rawPath = 'attempts/upper_sleeve_separate-1.png';
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
// Endpoint normalization only: no RGB painting, reconstruction, resizing or registration.
// The model produced alpha=253 throughout the fabric despite requesting opaque fabric.
function normalize(image) {
  const {width, height, data} = image;
  assert.equal(data.length, width * height * 4);
  const out = new Uint8ClampedArray(data);
  let cleared = 0, solidified = 0;
  for (let i = 3; i < out.length; i += 4) {
    if (out[i] > 0 && out[i] <= 8) { out[i] = 0; cleared++; }
    else if (out[i] >= 245 && out[i] < 255) { out[i] = 255; solidified++; }
  }
  return {data: out, width, height, cleared, solidified};
}
function check(image, original) {
  assert.equal(image.width, original.width); assert.equal(image.height, original.height);
  const expected = normalize(original);
  for (let i = 0; i < image.data.length; i += 4) {
    assert.equal(image.data[i+3], expected.data[i+3], `alpha mismatch ${i/4}`);
    // Raster encoding may normalize zero-alpha RGB and round partial-alpha RGB.
    if (expected.data[i+3] === 255)
      for (let c = 0; c < 3; c++) assert.equal(image.data[i+c], original.data[i+c], `opaque RGB changed ${i/4}`);
  }
  for (const [x,y] of [[600,350],[600,600],[650,850],[625,1000]])
    assert.equal(image.data[(y*image.width+x)*4+3], 255, `translucent fabric ${x},${y}`);
}
async function main() {
  const owner = require(OWNER+'/check-preparation.cjs');
  const bytes = fs.readFileSync(owner.safeFile(OWNER, rawPath));
  assert.equal(hash(bytes), RAW_SHA, 'raw candidate changed');
  const original = await owner.rgba(bytes);
  assert.equal(original.width,1254); assert.equal(original.height,1254);
  assert.deepEqual(process.argv.slice(2), ['--prepare'], 'explicit --prepare required; consumes one derivative slot');
  const token = owner.acquire(OWNER);
  try {
    const receipt = owner.reserve(OWNER,token,'derivative','upper_sleeve_separate','parts/upper_sleeve_candidate_v1.png').receipt;
    const {createCanvas} = require(path.join(process.env.MELOTRAIL_REPO_ROOT,'tools/video-motion/node_modules/@napi-rs/canvas'));
    const normalized = normalize(original);
    const canvas=createCanvas(original.width,original.height),ctx=canvas.getContext('2d');
    const pixels=ctx.createImageData(original.width,original.height); pixels.data.set(normalized.data);ctx.putImageData(pixels,0,0);
    const output=canvas.toBuffer('image/png');
    owner.createOutput(OWNER,token,receipt.id,output); // failed candidates remain spent and immutable
    const decoded=await owner.rgba(output);
    check(decoded,original);
    const record={receipt,rawSha256:RAW_SHA,outputSha256:hash(output),width:decoded.width,height:decoded.height,
      alpha:owner.alphaFacts(decoded.data,decoded.width,decoded.height),
      policy:{clearAlphaAtMost:8,opaqueAlphaAtLeast:245,rgb:'unchanged at opaque witnesses; no painting or scaling'},
      clearedPixels:normalized.cleared,solidifiedPixels:normalized.solidified,
      registration:null,assembledNeutral:null,extremeSupport:null,appearanceApproval:null,
      limitation:'Isolated candidate only, not a ready articulated part. No anchors or overlap coverage inferred from alpha bounds.'};
    fs.writeFileSync(path.join(ROOT,'checks/upper-sleeve-derivative.json'),JSON.stringify(record,null,2)+'\n',{flag:'wx'});
    console.log(JSON.stringify(record,null,2));
  } finally { owner.release(OWNER,token); }
}
if(require.main===module) main().catch(e=>{console.error(e);process.exitCode=1});
module.exports={normalize,check};
