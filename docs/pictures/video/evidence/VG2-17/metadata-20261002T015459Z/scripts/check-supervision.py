"""Non-live regressions: callbacks are data-only spies; no native video delegates."""
import copy
import hashlib
import json
import os
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch
import paths
import preview
import encoding


def fixture_plan():
    values = [('render', 'render', 0, ['/fixture/node','/fixture/colour_source.cjs','--generate']),
              ('source', 'render', 0, ['/fixture/node', '/fixture/verify_pixels.cjs', 'source']),
              ('encode', 'encode', 0, ['/fixture/ffmpeg', '-f', 'image2', '-i', '/fixture/frame-%04d.png', '/fixture/preview.mp4', '-vf', encoding.FILTER, '-pix_fmt', 'yuv420p', '-colorspace', 'bt709', '-color_trc', 'bt709', '-color_primaries', 'bt709', '-color_range', 'tv']),
              ('probe', 'validationAndCopy', 1, ['/fixture/ffprobe', '-count_frames', '-show_frames', '/fixture/preview.mp4']),
              ('decode', 'validationAndCopy', 1, ['/fixture/ffmpeg', '-i', '/fixture/preview.mp4', '/fixture/decoded/frame-%04d.png']),
              ('pixels', 'validationAndCopy', 0, ['/fixture/node', '/fixture/verify_pixels.cjs', 'decoded'])]
    return [{'id': role, 'phase': phase, 'decoderCost': cost, 'argv': argv} for role, phase, cost, argv in values]


class Guards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='blender-data-only-')
        self.root = Path(self.temp.name).resolve()
        self.plan = fixture_plan()
        self.now = [0.0]
        self.gates = []

    def tearDown(self):
        for gate in self.gates:
            gate.close()
        self.temp.cleanup()  # Only this test's tiny private fixture, never project/media owners.

    def gate(self, maximum=2, recheck=lambda: None):
        g = preview.Budget(self.plan, maximum, self.root / ('reservations-%d.jsonl' % len(self.gates)), 10, recheck, lambda: self.now[0])
        self.gates.append(g)
        return g

    def test_real_plan_two_charges_then_seventh_denied(self):
        g, calls = self.gate(), []
        for op in self.plan:
            g.invoke(op['id'], op['argv'], lambda op=op: calls.append(op['id']))
        self.assertEqual(calls, ['render', 'source', 'encode', 'probe', 'decode', 'pixels'])
        self.assertEqual((g.index, g.spent), (6, 2))
        reservations = [json.loads(x) for x in g.receipt.read_text().splitlines()]
        self.assertEqual([r['decoderCost'] for r in reservations], [0, 0, 0, 1, 1, 0])
        with self.assertRaises(RuntimeError):
            g.invoke('probe', self.plan[3]['argv'], lambda: calls.append('UNEXPECTED'))
        self.assertEqual(len(calls), 6)

    def test_less_than_true_decoder_budget_denies_before_counter_creation(self):
        with self.assertRaises(RuntimeError):
            self.gate(1)
        self.assertFalse(list(self.root.iterdir()))

    def test_failed_delegate_consumes_and_stops(self):
        g = self.gate()
        for op in self.plan[:3]:
            g.invoke(op['id'], op['argv'], lambda: None)
        with self.assertRaises(ValueError):
            g.invoke('probe', self.plan[3]['argv'], lambda: (_ for _ in ()).throw(ValueError('spy launch failed')))
        self.assertEqual(g.spent, 1)
        with self.assertRaises(RuntimeError):
            g.invoke('decode', self.plan[4]['argv'], lambda: self.fail('Retry reached delegate'))

    def test_unknown_changed_and_wrong_order_before_spy(self):
        for role, argv in [('unknown', []), ('render', self.plan[0]['argv'] + ['changed']), ('probe', self.plan[3]['argv'])]:
            g = self.gate()
            with self.assertRaises(RuntimeError):
                g.invoke(role, argv, lambda: self.fail('Unbound delegate'))
            self.assertEqual(g.spent, 0)

    def test_concurrent_duplicate_one_spy(self):
        g, calls, denied = self.gate(), [], []
        def attempt():
            try:
                g.invoke('render', self.plan[0]['argv'], lambda: calls.append('one'))
            except RuntimeError:
                denied.append(True)
        threads = [threading.Thread(target=attempt) for _ in range(2)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertEqual((calls, denied), (['one'], [True]))

    def test_deadline_and_boundary_deny_before_spy(self):
        g = self.gate()
        self.now[0] = 10
        with self.assertRaises(RuntimeError):
            g.invoke('render', self.plan[0]['argv'], lambda: self.fail('Late delegate'))
        g = self.gate(recheck=lambda: (_ for _ in ()).throw(RuntimeError('changed destination')))
        self.now[0] = 0
        with self.assertRaises(RuntimeError):
            g.invoke('render', self.plan[0]['argv'], lambda: self.fail('Changed destination delegate'))

    def test_receipt_substitution_and_no_reset(self):
        g = self.gate()
        original = self.root / 'original-reservation-retained.jsonl'
        g.receipt.rename(original)
        g.receipt.write_text('')  # Same canonical spelling/public content is not original authority.
        with self.assertRaises(RuntimeError):
            g.invoke('render', self.plan[0]['argv'], lambda: self.fail('Forged authority reached spy'))
        with self.assertRaises(FileExistsError):
            preview.Budget(self.plan, 2, g.receipt, 10)
        self.assertEqual(g.spent, 0)

    def test_declared_free_video_operation_and_added_render_rejected(self):
        fake = copy.deepcopy(self.plan)
        fake[0]['argv'] = self.plan[4]['argv']
        with self.assertRaises(RuntimeError):
            preview.Budget(fake, 2, self.root / 'fake.jsonl', 10)
        fake = self.plan + [self.plan[0]]
        with self.assertRaises(RuntimeError):
            preview.Budget(fake, 2, self.root / 'extra.jsonl', 10)

    def test_alias_and_replaced_ancestor_rejected(self):
        parent = self.root / 'review-parent'
        other = self.root / 'outside-owner'
        parent.mkdir(); other.mkdir()
        selected = {'work': str(self.root / 'work'), 'review': str(parent / 'review.bin'), 'stagedReview': str(parent / '.partial.bin')}
        contract = paths.capture(selected)
        parent.rename(self.root / 'original-review-parent')
        parent.symlink_to(other, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            paths.validate(contract, selected, fresh=True)
        self.assertFalse((other / 'review.bin').exists())

    def test_atomic_copy_no_overwrite_and_exact_bytes(self):
        selected = {'work': str(self.root / 'work'), 'review': str(self.root / 'review.bin'), 'stagedReview': str(self.root / '.partial.bin')}
        contract = paths.capture(selected)
        (self.root / 'source.bin').write_bytes(b'owned fixture, not media')
        digest = hashlib.sha256((self.root / 'source.bin').read_bytes()).hexdigest()
        paths.publish(self.root / 'source.bin', contract, digest, lambda: None)
        self.assertEqual((self.root / 'review.bin').read_bytes(), b'owned fixture, not media')
        self.assertFalse((self.root / '.partial.bin').exists())
        with self.assertRaises(RuntimeError):
            paths.publish(self.root / 'source.bin', contract, digest, lambda: None)

    def test_combined_caps_and_group_orphan_accounting(self):
        p = {'limits': {'aggregateRssBytes': 100, 'newStorageBytes': 1000, 'freeDiskReserveBytes': 100,
                        'maximumSwapGrowthBytes': 0}}
        baseline = {'swapUsedBytes': 0}
        r = {'pressureCode': '1', 'swapUsedBytes': 0, 'freeDiskBytes': 1100}
        preview.enforce(p, baseline, r, 100, 1000, 1, 2)
        for rss, storage, now, reading in [(101, 0, 1, r), (1, 1001, 1, r), (1, 0, 2, r),
                                          (1, 0, 1, {**r, 'pressureCode': '2'}),
                                          (1, 0, 1, {**r, 'swapUsedBytes': 1}),
                                          (1, 0, 1, {**r, 'freeDiskBytes': 1099})]:
            with self.assertRaises(RuntimeError): preview.enforce(p, baseline, reading, rss, storage, now, 2)
        rows = {10: {'parent': 1, 'group': 10}, 11: {'parent': 10, 'group': 10},
                12: {'parent': 1, 'group': 10}, 13: {'parent': 11, 'group': 13}, 99: {'parent': 1, 'group': 99}}
        self.assertEqual(preview.owned_ids(rows, 10), {10, 11, 12, 13})

    def test_media_pts_color_and_extra_stream_rejected(self):
        s = {'codec_type': 'video', 'codec_name': 'h264', 'width': 1920, 'height': 1080,
             'sample_aspect_ratio': '1:1', 'avg_frame_rate': '30/1', 'r_frame_rate': '30/1',
             'pix_fmt': 'yuv420p', 'nb_read_frames': '180', 'time_base': '1/15360', 'duration_ts': 180*512,
             'color_space': 'bt709', 'color_transfer': 'bt709', 'color_primaries': 'bt709','color_range':'tv'}
        probe = {'streams': [s], 'frames': [{'best_effort_timestamp': i*512} for i in range(180)]}
        self.assertEqual(preview.media_facts(probe, {'frames':180,'width':1920,'height':1080})['frames'], 180)
        for changed in [{**probe, 'streams': [s, {'codec_type': 'audio'}]},
                        {**probe, 'frames': probe['frames'][:-1]},
                        {**probe, 'streams': [{**s, 'color_space': 'bt601'}]}]:
            with self.assertRaises(RuntimeError): preview.media_facts(changed, {'frames':180,'width':1920,'height':1080})

    def test_missing_final_admission_stops_before_resources_or_writes(self):
        with patch.object(preview, 'APPROVAL', self.root / 'missing.json'), patch.object(preview, 'sample', side_effect=AssertionError('resource call too soon')):
            with self.assertRaises(RuntimeError): preview.run({'operations': self.plan})
        self.assertFalse(list(self.root.iterdir()))

    def test_cancel_is_irreversible_and_does_not_refund_operation(self):
        g=self.gate()
        with self.assertRaises(KeyboardInterrupt):
            g.invoke('render',self.plan[0]['argv'],lambda:(_ for _ in ()).throw(KeyboardInterrupt()))
        self.assertEqual(g.index,1)
        with self.assertRaises(RuntimeError):
            g.invoke('render',self.plan[0]['argv'],lambda:self.fail('Cancellation retried'))

    def test_case_and_exact_destination_aliases_fail(self):
        for duplicate in ['review.bin','REVIEW.bin']:
            with self.assertRaisesRegex(RuntimeError,'alias'):
                paths.capture({'work':str(self.root/'work'),'review':str(self.root/'review.bin'),'stagedReview':str(self.root/duplicate)})

    def test_runtime_inventory_order_regression(self):
        # The first real preflight refused equal file sets in a different order.
        (self.root/'a').mkdir();(self.root/'a/file').write_text('fixture');(self.root/'a.txt').write_text('fixture')
        wrong=[str(p.relative_to(self.root)) for p in sorted(self.root.rglob('*')) if p.is_file()]
        correct=sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*') if p.is_file())
        self.assertEqual(set(wrong),set(correct));self.assertNotEqual(wrong,correct)
        source=(Path(preview.SCOPE['originalPacketOwner'])/'scripts/freeze.py').read_text()
        self.assertIn("runtime['inventories'][root]=sorted(inventory)",source)

    def test_real_failed_packet_requires_frame_metadata_correction(self):
        previous=Path(preview.SCOPE['previousPacketOwner'])
        packet=json.loads((previous/'packets/colour.json').read_text())
        argv=next(x['argv'] for x in packet['operations'] if x['id']=='encode')
        with self.assertRaisesRegex(RuntimeError,'frame colour contract'):encoding.require_frame_tags(argv)
        corrected=list(argv);corrected[corrected.index('-vf')+1]=encoding.FILTER
        encoding.require_frame_tags(corrected)
        wrong=list(corrected);wrong[wrong.index('-color_trc')+1]='iec61966-2-1'
        with self.assertRaises(RuntimeError):encoding.require_frame_tags(wrong)

    def test_actual_srgb_tagged_output_remains_rejected(self):
        failed=Path(preview.SCOPE['failedColourOwner'])/'live/probe.log'
        facts=json.loads(failed.read_text())
        self.assertEqual(facts['streams'][0]['color_transfer'],'iec61966-2-1')
        with self.assertRaisesRegex(RuntimeError,'color mismatch'):
            preview.media_facts(facts,{'frames':6,'width':640,'height':360})

    def test_actual_installed_build_rejects_failed_setparams_command(self):
        facts=json.loads((Path(preview.SCOPE['filterFailureOwner'])/'checks/installed-capabilities.json').read_text())
        packet=json.loads((Path(preview.SCOPE['previousPacketOwner'])/'packets/colour.json').read_text())
        argv=next(o['argv'] for o in packet['operations'] if o['id']=='encode')
        self.assertNotIn('setparams',encoding.declared_filters(facts['manifestBuildOptions']))
        with self.assertRaisesRegex(RuntimeError,'setparams'):encoding.require_available(argv,facts['manifestBuildOptions'])
        corrected=list(argv);corrected[corrected.index('-vf')+1]=encoding.FILTER
        encoding.require_available(corrected,facts['manifestBuildOptions'])

    def test_unrecognized_filter_graph_or_manifest_cannot_pass(self):
        for graph in ['scale=iw:ih;movie=secret','scale=iw:ih,\\setparams=color_trc=bt709']:
            with self.assertRaises(RuntimeError):encoding.require_available(['-vf',graph],['--disable-everything','--enable-filter=scale,format'])
        with self.assertRaises(RuntimeError):encoding.declared_filters(['--enable-filter=scale'])

    def test_failed_preflight_has_no_resource_or_child_dispatch(self):
        (self.root/'checks').mkdir()
        with patch.object(preview,'PROOF',self.root),patch.object(preview,'authorize',lambda p:None),patch.object(preview,'sha',lambda p:'fixture'),patch.object(preview,'verify_bindings',side_effect=RuntimeError('changed pinned file')),patch.object(preview,'stable_resources',side_effect=AssertionError('resource started')),patch.object(preview.subprocess,'Popen',side_effect=AssertionError('child started')):
            with self.assertRaisesRegex(RuntimeError,'changed pinned file'):preview.run({})
        self.assertTrue((self.root/'checks/launch-claim.json').exists())

    def test_actual_phase_dispatch_shared_deadlines_and_publication(self):
        from contextlib import ExitStack
        p={'operations':self.plan,'limits':{'phaseSeconds':preview.PHASE_SECONDS,'aggregateRssBytes':4*1024**3,'newStorageBytes':128*1024**2,'freeDiskReserveBytes':10*1024**3,'maximumSwapGrowthBytes':0},'destinations':{'work':str(self.root),'review':str(self.root/'review'),'stagedReview':str(self.root/'partial')},'destinationContract':{},'tools':{'sandbox':'/fixture/sandbox'},'scratch':{'path':str(self.root)},'sandboxProfile':'data-only'}
        calls=[];now=[1.0]
        class Child:
            pid=1999999999;returncode=0
            def poll(self):return self.returncode
        def child(argv,**kwargs):calls.append(argv);return Child()
        reading={'freeMemoryBytes':4*1024**3,'freeDiskBytes':50*1024**3,'pressureCode':'1','swapUsedBytes':0}
        with ExitStack() as stack:
            for obj,name,value in [(preview.time,'monotonic',lambda:now[0]),(preview,'verify_bindings',lambda p:None),(preview,'authorize',lambda p:None),(preview,'sha',lambda p:'fixture'),(preview,'sample',lambda:reading),(preview,'bytes_under',lambda p:0),(preview,'process_rows',lambda:{os.getpid():{'rss':100,'parent':0,'group':0}}),(preview.paths,'validate',lambda *a,**kw:{}),(preview.subprocess,'Popen',child)]:stack.enter_context(patch.object(obj,name,value))
            supervisor=preview.Supervisor(p,{'swapUsedBytes':0},180,{})
            gate=self.gate(recheck=supervisor.check)
            gate.clock=lambda:now[0];gate.deadline=180
            for instant,op in zip([1,5,10,20,25,30],self.plan):
                now[0]=instant;gate.invoke(op['id'],op['argv'],lambda op=op:supervisor.execute(op))
            self.assertEqual(len(calls),6);self.assertEqual(gate.spent,2)
            self.assertEqual(supervisor.phase_starts,{'render':1,'encode':10,'validationAndCopy':20})
            now[0]=79.9;supervisor.check()
            now[0]=80
            with self.assertRaisesRegex(RuntimeError,'deadline'):supervisor.check()
            supervisor.deadline=50;now[0]=50
            with self.assertRaisesRegex(RuntimeError,'deadline'):supervisor.check()


if __name__=='__main__':unittest.main()
