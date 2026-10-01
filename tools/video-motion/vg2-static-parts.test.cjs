'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {pose,states,distance,transform,specs}=require('../../docs/pictures/video/evidence/VG2-13/assembly-20261001T162141Z/scripts/assemble.cjs');
test('static articulated poses keep identical limb lengths, shoulder and scale',()=>{
 for(const {value:p} of states){assert.deepEqual(p.shoulder,[772,619]);assert(Math.abs(distance(p.shoulder,p.elbow)-p.upperLength)<1e-10);assert(Math.abs(distance(p.elbow,p.wrist)-p.forearmLength)<1e-10);}
 assert.deepEqual(pose(59.36,-79.4,0,'neutral'),states[0].value);
 assert.notDeepEqual(states[0].value.wrist,states[1].value.wrist);
});
test('unsupported articulation and extra hand states reject rather than extrapolate',()=>{
 for(const args of [[61,-65,0,'open'],[14,-65,0,'open'],[32,-91,0,'open'],[32,-47,0,'open'],[32,-65,13,'open'],[32,-65,0,'drink'],[NaN,-65,0,'open']])assert.throws(()=>pose(...args));
});
test('admitted colour cleanup removes pink donor contamination but preserves emerald and leopard RGB',()=>{
 const {removeSkin}=require('../../docs/pictures/video/evidence/VG2-13/assembly-20261001T162141Z/scripts/correct.cjs');
 const image={width:4,height:1,data:new Uint8ClampedArray([230,160,210,255,10,100,75,255,230,140,25,255,30,15,7,255])};
 const out=removeSkin(image);assert.equal(out.removed,1);assert.equal(image.data[3],255);assert.equal(out.data[3],0);
 for(let i=4;i<16;i++)assert.equal(out.data[i],image.data[i]);
 for(let i=0;i<3;i++)assert.equal(out.data[i],image.data[i]);
});
test('static admission rejects holes, protected-pixel drift, stretched limbs and fabricated approval',()=>{
 const {validateStatic}=require('../../docs/pictures/video/evidence/VG2-13/assembly-20261001T162141Z/scripts/check-kit.cjs');
 const good=()=>({variant:'v2',technicalPass:true,appearance:'UNREVIEWED',checks:states.map(s=>({state:s.id,geometry:structuredClone(s.value),outsideChanges:0,nonOpaque:0,seam:Array.from({length:4},()=>({holes:0,minAlpha:255}))}))});
 validateStatic(good());
 for(const change of [x=>x.checks[0].seam[0].holes=1,x=>x.checks[0].outsideChanges=1,x=>x.checks[1].geometry.elbow[0]++,x=>x.appearance='APPROVED',x=>x.checks.pop()]){const bad=good();change(bad);assert.throws(()=>validateStatic(bad));}
});
test('registered extraction anchors stay inside each explicit crop',()=>{
 for(const p of specs)for(const a of Object.values(p.anchors)){assert(a[0]>p.crop[0]&&a[0]<p.crop[2]);assert(a[1]>p.crop[1]&&a[1]<p.crop[3]);}
 assert.equal(specs.filter(s=>s.id.startsWith('hand_')).length,2);
 assert.deepEqual(transform([0,0],[7,9],1.25),[7,9]);
});
