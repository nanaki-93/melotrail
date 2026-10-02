"""Data-only motion/pin regressions; never starts Blender or a media process."""
import copy
import importlib.util
import json
import math
from pathlib import Path
import sys
import unittest

OWNER=Path(__file__).resolve().parent.parent
REPO=OWNER.parents[5]
module=importlib.util.spec_from_file_location('rig_motion',OWNER/'scripts/motion.py')
motion=importlib.util.module_from_spec(module);module.loader.exec_module(motion)
SPEC=json.loads((OWNER/'inputs/rig-v2.json').read_text())
sys.path.insert(0,str(OWNER/'scripts'))
import evaluate


class Motion(unittest.TestCase):
    def test_selected_texture_pins_and_complete_rig(self):
        motion.validate(SPEC,REPO)

    def test_absolute_frames_preserve_lengths_bounds_and_return(self):
        frames=[motion.state(SPEC,i) for i in range(150)]
        for s in frames:
            self.assertAlmostEqual(math.dist(s['shoulder'],s['elbow']),SPEC['lengths'][0],places=10)
            self.assertAlmostEqual(math.dist(s['elbow'],s['wrist']),SPEC['lengths'][1],places=10)
        self.assertEqual(frames[0]['angles'],frames[-1]['angles'])
        self.assertEqual(frames[0]['wrist'],frames[-1]['wrist'])
        self.assertEqual(sum(frames[i]['hand']!=frames[i-1]['hand'] for i in range(1,150)),2)
        self.assertGreater(len(set(tuple(s['wrist']) for s in frames)),40)
        for i in [149,0,75,91,17]:self.assertEqual(motion.state(SPEC,i),frames[i])

    def test_rejects_missing_part_wrong_pin_unsupported_pose_and_conflicts(self):
        for mutation in ['missing','pin','pose','conflict','nan']:
            s=copy.deepcopy(SPEC)
            if mutation=='missing':del s['parts']['forearm']
            elif mutation=='pin':s['textures']['open-front']['sha256']='0'*64
            elif mutation=='pose':s['keys'][5]['angles'][2]=90
            elif mutation=='conflict':s['keys'][5]['forearmOverride']=0
            else:s['keys'][5]['angles'][0]=float('nan')
            with self.assertRaises(AssertionError,msg=mutation):motion.validate(s,REPO)

    def test_every_dependency_changes_fingerprint(self):
        base=motion.fingerprint(SPEC)
        for name in SPEC['textures']:
            s=copy.deepcopy(SPEC);s['textures'][name]['sha256']='0'*64
            self.assertNotEqual(base,motion.fingerprint(s))
        s=copy.deepcopy(SPEC);s['keys'][5]['angles'][2]-=1
        self.assertNotEqual(base,motion.fingerprint(s))

    def test_out_of_range_and_nonfinite_frame_rejected(self):
        for frame in [-1,150,float('nan'),float('inf')]:
            with self.assertRaises(AssertionError):motion.state(SPEC,frame)

    def test_data_only_dispatch_rejects_render_gui_and_extra_operation(self):
        expected=evaluate.commands();evaluate.validate_commands(expected)
        for altered in [expected+expected[:1],[(name,[v for v in argv if v!='--background']) for name,argv in expected],[(name,argv[:-1]+['render']) for name,argv in expected]]:
            with self.assertRaises(RuntimeError):evaluate.validate_commands(altered)

    def test_owned_process_group_and_descendants_are_counted(self):
        rows={1:{'parent':0,'group':1},2:{'parent':1,'group':1},3:{'parent':2,'group':3},4:{'parent':0,'group':4}}
        self.assertEqual(evaluate.owned_ids(rows,1),{1,2,3})

    def test_shared_deadline_rss_and_storage_fail_closed(self):
        evaluate.enforce(10,120,100,100)
        for args in [(120,120,0,0),(10,120,4*1024**3+1,0),(10,120,0,512*1024**2+1)]:
            with self.assertRaises(RuntimeError):evaluate.enforce(*args)

    def test_embedded_blender_cannot_mutate_its_pinned_bytecode_cache(self):
        # Actual attempt 2 failed on a Blender-written .pyc before reopen.
        failure=json.loads((OWNER/'checks/evaluation-failure-v2.json').read_text())
        self.assertEqual(failure['completed'],['build'])
        self.assertIn('Data helper/input changed',failure['error'])
        self.assertIn('sys.dont_write_bytecode = True',(OWNER/'scripts/rig.py').read_text())
        self.assertFalse((OWNER/'scripts/__pycache__/motion.cpython-313.pyc').exists())


if __name__=='__main__':unittest.main()
