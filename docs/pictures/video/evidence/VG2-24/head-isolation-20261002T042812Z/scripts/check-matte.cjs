
'use strict';
const assert=require('node:assert/strict'),path=require('node:path');
const ROOT=path.resolve(__dirname,'../../../../../../..');
const {loadImage}=require(path.join(ROOT,'tools/video-motion/node_modules/@napi-rs/canvas'));
const {matte,pixels}=require('./composite.cjs');
(async()=>{
 const directory=path.join(ROOT,'docs/pictures/video/evidence/VG2-24/head-watch-20261002T035933Z/review/frames');
 const cases=[{file:65,foreground:[[242,114],[269,128],[285,154],[277,132],[244,102]],background:[[320,117],[94,95],[400,200]]},{file:1,foreground:[[361,197],[349,170],[212,84],[275,177]],background:[[340,80],[130,110],[390,205]]}];
 const failures=[];
 for(const c of cases){const p=pixels(await loadImage(path.join(directory,`frame-${String(c.file).padStart(4,'0')}.png`))),m=matte(p,c.file-1);for(const [x,y] of c.foreground)if(!m[y*768+x])failures.push({frame:c.file,x,y,reason:'visible character removed'});for(const [x,y] of c.background)if(m[y*768+x])failures.push({frame:c.file,x,y,reason:'background retained'});}
 console.log(JSON.stringify({status:failures.length?'FAIL':'PASS',caseCount:cases.length,failures},null,2));assert.equal(failures.length,0,'Real frame foreground/background witnesses');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
