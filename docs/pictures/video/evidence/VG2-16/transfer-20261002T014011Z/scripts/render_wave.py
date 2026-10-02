"""One exact 150-frame batch from the frozen articulated rig; no setup/repair."""
import bpy
import hashlib
import importlib.util
import json
import os
import sys
from pathlib import Path
sys.dont_write_bytecode=True
BASE=Path(__file__).resolve().parent.parent
REPO=BASE.parents[5]


def main():
    assert bpy.app.background
    assert sys.argv[sys.argv.index('--')+1:]==['render']
    assert os.environ.get('MELOTRAIL_PROOF_KIND')=='wave'
    packet=BASE/'packets/wave.json'
    assert hashlib.sha256(packet.read_bytes()).hexdigest()==os.environ['MELOTRAIL_BLENDER_PACKET']
    p=json.loads(packet.read_text());work=Path(p['destinations']['work'])
    rig=REPO/p['rig']['path'];assert Path(bpy.data.filepath).resolve()==rig
    assert hashlib.sha256(rig.read_bytes()).hexdigest()==p['rig']['sha256']
    helper=REPO/p['rigHelper']['path']
    spec=importlib.util.spec_from_file_location('proved_rig',helper);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    module.motion.validate(module.SPEC,REPO)
    facts=module.geometry()
    expected=json.loads((REPO/p['geometry']['path']).read_text())['frames'];assert facts==expected
    scene=bpy.context.scene;count=[0]
    def bounded(scene,*args):
        assert count[0]<150 and scene.frame_current==count[0]
        count[0]+=1
    bpy.app.handlers.render_pre.append(bounded)
    for frame in range(150):
        scene.frame_set(frame)
        destination=work/'source'/('frame-%04d.png'%(frame+1));assert not destination.exists()
        scene.render.filepath=str(destination)
        bpy.ops.render.render(write_still=True)
        assert destination.is_file()
    assert count[0]==150
    with (work/'rendered-geometry.json').open('x') as f:json.dump({'renderedFrames':150,'frames':facts},f)


if __name__=='__main__':main()
