"""Standalone articulated cutouts. Build/check forbid all frame rendering."""
import bpy
import hashlib
import json
import math
import os
import sys
sys.dont_write_bytecode = True
from pathlib import Path
from mathutils import Vector

BASE = Path(__file__).resolve().parent.parent
REPO = BASE.parents[5]
sys.path.insert(0, str(BASE / 'scripts'))
import motion

SPEC = json.loads((BASE / 'inputs/rig-v2.json').read_text())
SCENE = BASE / 'rig/tabi-wave-v3.blend'
CONTROLS = ('shoulderDeg', 'forearmDeg', 'wristDeg', 'handOpen')


def write(file, value):
    with file.open('x') as f:
        json.dump(value, f, indent=2)
        f.write('\n')


def refuse_render(*args):
    raise RuntimeError('VG2-15 data-only mode: render is not admitted')


def empty(name, parent=None, location=(0, 0, 0)):
    obj = bpy.data.objects.new(name, None)
    bpy.context.scene.collection.objects.link(obj)
    obj.parent, obj.location, obj.rotation_mode = parent, location, 'XYZ'
    return obj


def driver(obj, prop, expression, variables, index=None):
    curve = obj.driver_add(prop) if index is None else obj.driver_add(prop, index)
    drv = curve.driver
    drv.type, drv.expression = 'SCRIPTED', expression
    for name, source in variables.items():
        var = drv.variables.new()
        var.name, var.type = name, 'SINGLE_PROP'
        var.targets[0].id = bpy.data.objects['TABI-controls']
        var.targets[0].data_path = '["' + source + '"]'


def material(name, texture):
    image = bpy.data.images.load(str(REPO / texture['path']), check_existing=True)
    image.colorspace_settings.name, image.alpha_mode = 'sRGB', 'STRAIGHT'
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.surface_render_method = 'BLENDED'
    mat.use_backface_culling = True
    mat.use_transparency_overlap = False
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    tex = nodes.new('ShaderNodeTexImage')
    tex.image, tex.interpolation, tex.extension = image, 'Linear', 'CLIP'
    emit, trans, mix, output = [nodes.new(n) for n in ('ShaderNodeEmission', 'ShaderNodeBsdfTransparent', 'ShaderNodeMixShader', 'ShaderNodeOutputMaterial')]
    emit.inputs['Strength'].default_value = 1
    links.new(tex.outputs['Color'], emit.inputs['Color'])
    links.new(tex.outputs['Alpha'], mix.inputs[0])
    links.new(trans.outputs[0], mix.inputs[1])
    links.new(emit.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], output.inputs['Surface'])
    return mat


def create_part(name, part, controls, mats):
    l, t, w, h = part['crop']
    ax, ay = part['anchor']
    scale = part['scale']
    xy = [(l, t), (l, t+h), (l+w, t+h), (l+w, t)]
    verts = [((x-ax)*scale, -(y-ay)*scale, 0) for x, y in xy]
    mesh = bpy.data.meshes.new(name+'-mesh')
    mesh.from_pydata(verts, [], [(0, 1, 2, 3)])
    mesh.update()
    uv = mesh.uv_layers.new(name='SourceUV')
    tw, th = SPEC['textures'][part['texture']]['size']
    for loop in mesh.loops:
        x, y = xy[loop.vertex_index]
        uv.data[loop.index].uv = (x/tw, 1-y/th)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    obj.parent = controls.get(part['control'])
    obj.location = (0, 0, part['depth'])
    obj.rotation_mode = 'XYZ'
    obj.rotation_euler.z = part['sourceAngle']
    if part['control'] == 'elbow-cover':
        driver(obj, 'rotation_euler', 'f*pi/180', {'f': 'forearmDeg'}, 2)
    if part['hand']:
        driver(obj, 'hide_render', 'h<0.5' if part['hand'] == 'open' else 'h>=0.5', {'h': 'handOpen'})
    obj.data.materials.append(mats[part['texture']])
    obj['sourceSha256'] = SPEC['textures'][part['texture']]['sha256']
    obj['scope'] = 'Selected reusable textured part; no whole-arm frame replacement'
    return obj


def build():
    assert not SCENE.exists(), 'No overwrite'
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_EEVEE'
    scene.render.use_sequencer = scene.render.use_compositing = False
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 100
    scene.render.pixel_aspect_x = scene.render.pixel_aspect_y = 1
    scene.render.fps, scene.render.fps_base = 30, 1
    scene.frame_start, scene.frame_end = 0, 149
    scene.view_settings.view_transform, scene.view_settings.look = 'Standard', 'None'
    scene.view_settings.exposure, scene.view_settings.gamma = 0, 1
    scene.render.dither_intensity = 0
    scene.render.film_transparent = False
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode, scene.render.image_settings.color_depth = 'RGBA', '8'
    scene.render.threads_mode, scene.render.threads = 'FIXED', 4
    scene.render.filepath = str(BASE / 'RENDER_NOT_ADMITTED')
    scene.world.color = (0, 0, 0)
    camera_data = bpy.data.cameras.new('Fixed-orthographic')
    camera = bpy.data.objects.new('Fixed-orthographic', camera_data)
    scene.collection.objects.link(camera)
    camera.location, camera.rotation_euler = (960, -540, 2000), (0, 0, 0)
    camera_data.type, camera_data.ortho_scale = 'ORTHO', 1920
    camera_data.clip_start, camera_data.clip_end = 1, 3000
    scene.camera = camera
    rig = empty('TABI-controls', location=(772, -619, 0))
    for name, bounds in zip(CONTROLS, [(15, 60), (-90, -48), (-12, 12), (0, 1)]):
        rig[name] = 0.0
        rig.id_properties_ui(name).update(min=bounds[0], max=bounds[1])
    for frame in range(150):
        state = motion.state(SPEC, frame)
        for name, value in zip(CONTROLS, state['angles']+[float(state['hand'] == 'open')]):
            rig[name] = value
            rig.keyframe_insert(data_path='["'+name+'"]', frame=frame, group='Wave')
    action = rig.animation_data.action
    action.name = 'TABI-wave-five-seconds-v1'
    action.use_fake_user = True
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    for point in curve.keyframe_points:
                        point.interpolation = 'CONSTANT' if 'handOpen' in curve.data_path else 'LINEAR'
    shoulder = empty('Shoulder', rig)
    driver(shoulder, 'rotation_euler', '-a*pi/180', {'a':'shoulderDeg'}, 2)
    elbow = empty('Elbow', shoulder, (SPEC['lengths'][0], 0, 0))
    driver(elbow, 'rotation_euler', '(a-f)*pi/180', {'a':'shoulderDeg', 'f':'forearmDeg'}, 2)
    wrist = empty('Wrist', elbow, (SPEC['lengths'][1], 0, 0))
    driver(wrist, 'rotation_euler', '-w*pi/180', {'w':'wristDeg'}, 2)
    controls = {'shoulder-cover':rig, 'elbow-cover':elbow, 'shoulder':shoulder, 'elbow':elbow, 'wrist':wrist}
    mats = {name:material(name, t) for name, t in SPEC['textures'].items() if name not in ('allowed', 'occlusion')}
    for name, part in SPEC['parts'].items():
        create_part(name, part, controls, mats)
    scene['inputFingerprint'] = motion.fingerprint(SPEC)
    scene['scope'] = 'Standalone rig geometry proof; no renderer, media, app or moving acceptance'
    scene['renderAdmitted'] = False
    scene.frame_set(0)
    bpy.ops.wm.save_as_mainfile(filepath=str(SCENE), check_existing=True)


def geometry():
    scene, rig = bpy.context.scene, bpy.data.objects['TABI-controls']
    assert scene['inputFingerprint'] == motion.fingerprint(SPEC), 'Changed rig input'
    assert not scene.render.use_sequencer and not scene.render.use_compositing
    assert scene.camera.data.type == 'ORTHO' and scene.camera.data.ortho_scale == 1920
    assert not any(o.type in ('LIGHT', 'ARMATURE') for o in scene.objects)
    assert set(o.name for o in scene.objects if o.type == 'MESH') == set(SPEC['parts']), 'Missing/unexpected part'
    assert len(bpy.data.actions) == 1, 'Conflicting action'
    assert not rig.animation_data.nla_tracks and not rig.constraints
    samples=[]
    for frame in range(150):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        expected = motion.state(SPEC, frame)
        assert all(abs(float(rig[n])-v)<1e-4 for n,v in zip(CONTROLS,expected['angles']+[float(expected['hand']=='open')])), 'Control drift'
        anchors={}
        for name, wanted in [('Shoulder',expected['shoulder']),('Elbow',expected['elbow']),('Wrist',expected['wrist'])]:
            p=bpy.data.objects[name].matrix_world.translation
            point=[float(p.x),float(-p.y)]
            assert math.dist(point,wanted)<=SPEC['limits']['jointErrorPixels'], 'Disconnected joint'
            anchors[name]=point
        parts={}
        for name, part in SPEC['parts'].items():
            obj=bpy.data.objects[name]
            assert tuple(obj.scale)==(1,1,1) and not obj.modifiers and not obj.constraints, 'Scale/deformation conflict'
            assert obj['sourceSha256']==SPEC['textures'][part['texture']]['sha256']
            assert len(obj.data.vertices)==4 and len(obj.data.polygons)==1
            assert abs(obj.matrix_world.determinant()-1)<1e-5, 'Mesh inversion or scale drift'
            mat=obj.data.materials[0]
            assert mat.surface_render_method=='BLENDED' and not mat.use_transparency_overlap
            tex=next(n for n in mat.node_tree.nodes if n.type=='TEX_IMAGE')
            assert tex.image.alpha_mode=='STRAIGHT' and tex.image.colorspace_settings.name=='sRGB'
            assert Path(bpy.path.abspath(tex.image.filepath)).resolve()==REPO/SPEC['textures'][part['texture']]['path']
            assert obj.hide_render == (part['hand'] is not None and part['hand']!=expected['hand'])
            points=[]
            for i in [0,3,2,1]:
                p=obj.matrix_world@obj.data.vertices[i].co
                points.append([float(p.x),float(-p.y),float(p.z)])
            parts[name]={'quad':points,'visible':not obj.hide_render,'uv':[list(v.uv) for v in obj.data.uv_layers[0].data]}
        samples.append({'frame':frame,'controls':expected,'anchors':anchors,'parts':parts})
    assert samples[0]['parts']==samples[-1]['parts'], 'Neutral return differs'
    for key in ['background','protected']:
        assert all(s['parts'][key]==samples[0]['parts'][key] for s in samples), 'Fixed contact moved'
    # Determinism of nonsequential absolute-frame evaluation, without rerendering.
    for frame in [149,17,91,0,75,38,127,1]:
        scene.frame_set(frame);bpy.context.view_layer.update()
        p=bpy.data.objects['Wrist'].matrix_world.translation
        assert [float(p.x),float(-p.y)]==samples[frame]['anchors']['Wrist']
    scene.frame_set(0)
    return samples


def main():
    assert bpy.app.background and bpy.app.version_string=='5.2.2 LTS'
    args=sys.argv[sys.argv.index('--')+1:]
    assert len(args)==1 and args[0] in ('build','check'), 'Data-only entrypoints'
    bpy.app.handlers.render_pre.append(refuse_render)
    motion.validate(SPEC,REPO)
    mode=args[0]
    if mode=='build': build()
    else: assert Path(bpy.data.filepath).resolve()==SCENE
    samples=geometry()
    write(BASE/('checks/'+mode+'-geometry-v3.json'),{'status':'DATA_GEOMETRY_PASS_NOT_PIXELS','mode':mode,'sceneSha256':motion.digest(SCENE),'inputFingerprint':motion.fingerprint(SPEC),'renderedFrames':0,'frames':samples})
    print(json.dumps({'status':'DATA_GEOMETRY_PASS_NOT_PIXELS','mode':mode,'framesEvaluated':150,'renderedFrames':0}))


if __name__=='__main__': main()
