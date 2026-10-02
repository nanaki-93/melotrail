#!/usr/bin/env python3
"""One private Blender comparison; no app job ledger or production import.
preflight is read-only. run requires a separately admitted frozen packet.
Every child (including verifiers) uses one directly owned supervisor and sandbox.
"""
import copy
import hashlib
import json
import os
import re
import signal
import stat
import subprocess
import sys
import threading
import shutil
import zipfile
import time
from pathlib import Path
import paths

BASE = Path(__file__).resolve().parent.parent
REPO = BASE.parents[5]
KIND = sys.argv[1] if len(sys.argv)>1 and sys.argv[1] in ('colour','wave') else 'colour'
SCOPE = json.loads((BASE/'inputs/scope.json').read_text())
PROOF = Path(SCOPE[KIND+'Owner'])
PACKET=BASE/'packets'/('colour-v2.json' if KIND=='colour' else 'wave.json')
if KIND=='wave' and not PACKET.exists():PACKET=BASE/'packets/wave-proposal-v2.json'
APPROVAL = PROOF / 'admission.json'
PHASE_SECONDS = {'render':60,'encode':60,'validationAndCopy':60} if KIND=='colour' else {'render':600,'encode':180,'validationAndCopy':120}
OPERATION_PHASES = ['render', 'render', 'encode', 'validationAndCopy', 'validationAndCopy', 'validationAndCopy']


def validate_phases(p):
    need(p['limits']['phaseSeconds'] == PHASE_SECONDS, 'Phase budget differs from authorized scope')
    need([o['phase'] for o in p['operations']] == OPERATION_PHASES, 'Operation phase contract mismatch')


def need(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def fresh_json(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as f:
        json.dump(value, f, indent=2)
        f.write('\n')
        f.flush()
        os.fsync(f.fileno())


def config():
    p = json.loads(PACKET.read_text())
    need(p['status'] == 'FROZEN_PROPOSAL_NOT_AUTHORIZED', 'Invalid packet status')
    need(p['owner'] == str(BASE) and p['kind']==KIND, 'Packet owner/kind differs')
    validate_phases(p)  # Reject mismatch before admission, work claim, reservation or child launch.
    return p


def verify_bindings(p, runtime=True):
    need(p == config(), 'Frozen packet drift')
    runtime_pins=json.loads(Path(p['runtimeFile']['path']).read_text())
    need(sha(p['runtimeFile']['path'])==p['runtimeFile']['sha256'],'Runtime record changed')
    sets = [p['files'], runtime_pins['files']] if runtime else [p['files']]
    for pins in sets:
        for name, digest in pins.items():
            f = Path(name)
            need(f.is_absolute() and f.is_file() and not f.is_symlink() and f.resolve() == f, 'Noncanonical file pin: ' + name)
            need(sha(f) == digest, 'Changed bound file: ' + name)
    if runtime:
        for name, target in runtime_pins['links'].items():
            need(Path(name).is_symlink() and os.readlink(name) == target, 'Changed runtime link')
        # Inventory changes must not hide a new shadow module/resource.
        for root, entries in runtime_pins['inventories'].items():
            actual = sorted(str(f.relative_to(root)) for f in Path(root).rglob('*') if f.is_file() or f.is_symlink())
            need(actual == entries, 'Runtime inventory drift: ' + root)
    paths.validate(p['destinationContract'], p['destinations'])
    scratch=Path(p['scratch']['path'])
    need(paths.identity(scratch,paths.directory_info(scratch))==p['scratch'],'Changed scratch boundary')


def authorize(p):
    need(APPROVAL.is_file() and not APPROVAL.is_symlink(), 'Separate final native admission is missing')
    a = json.loads(APPROVAL.read_text())
    need(a.get('status') == 'AUTHORIZED' and a.get('exactUserMessage') and a.get('packetSha256') == sha(PACKET), 'Final human event does not bind this packet')
    need(a.get('limits') == p['limits'] and a.get('operations') == p['operations'], 'Admitted budget/operation drift')
    if p['kind']=='wave':
        need(p.get('colourProof') and PACKET.name=='wave.json','Passing colour proof is not bound')
        for name,digest in p['colourProof'].items():need(sha(name)==digest,'Changed colour proof')


def decoder_cost(operation):
    # Exact argument vectors are separately bound; no count/PTS scan is free metadata.
    args, role = operation['argv'], operation['id']
    executable = Path(args[0]).name
    need(executable == {'render': ('node' if KIND=='colour' else 'Blender'), 'source': 'node', 'encode': 'ffmpeg', 'probe': 'ffprobe', 'decode': 'ffmpeg', 'pixels': 'node'}.get(role), 'Unknown executable/role')
    if role == 'probe':
        need('-count_frames' in args and '-show_frames' in args and args[-1].endswith('.mp4'), 'Unknown probe')
        return 1
    if role == 'decode':
        need(args.count('-i') == 1 and args[args.index('-i') + 1].endswith('.mp4'), 'Unknown video decode')
        return 1
    if role == 'encode':
        need(args.count('-i') == 1 and args[args.index('-i') + 1].endswith('frame-%04d.png') and '-f' in args and 'image2' in args, 'Encode is not the bound PNG sequence')
        return 0
    need(role in ('render', 'source', 'pixels'), 'Unknown operation')
    if role == 'render':
        if KIND=='colour':
            need(len(args)==3 and Path(args[1]).name=='colour_source.cjs' and args[2]=='--generate','Unknown synthetic operation')
        else:
            need('--background' in args and args[-2:] == ['--', 'render'] and Path(args[-3]).name=='render_wave.py', 'Unknown Blender operation')
    else:
        need(len(args) == 3 and Path(args[1]).name == 'verify_pixels.cjs' and args[2] == ('source' if role == 'source' else 'decoded'), 'Unknown verifier operation')
    return 0


class Budget:
    """Direct-child authority, irreversible receipts, no shared socket/client trust."""
    def __init__(self, operations, maximum, receipt, deadline, recheck=lambda: None, clock=time.monotonic):
        self.plan = copy.deepcopy(operations)
        need([o['id'] for o in self.plan] == ['render', 'source', 'encode', 'probe', 'decode', 'pixels'], 'Exactly one fixed six-operation plan required')
        need([o['phase'] for o in self.plan] == OPERATION_PHASES, 'Phase plan drift')
        need(sum(decoder_cost(o) for o in self.plan) == maximum == 2, 'True plan requires exactly two MP4 traversals')
        self.maximum, self.deadline, self.clock, self.recheck = maximum, deadline, clock, recheck
        self.index = self.spent = 0
        self.failed = False
        self.lock = threading.Lock()
        self.receipt = Path(receipt)
        self.fd = os.open(self.receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        self.inode = os.fstat(self.fd).st_ino
        self.content = b''

    def close(self):
        os.close(self.fd)

    def invoke(self, role, argv, delegate):
        with self.lock:
            need(not self.failed and self.clock() < self.deadline, 'Failed/expired attempt; no renewal')
            need(self.index < len(self.plan), 'Operation count exhausted')
            op = self.plan[self.index]
            need(op['id'] == role and op['argv'] == argv, 'Unknown/changed/out-of-order operation')
            self.recheck()
            info = self.receipt.lstat()
            need(stat.S_ISREG(info.st_mode) and info.st_ino == self.inode and self.receipt.read_bytes() == self.content, 'Lost/replaced/truncated reservation authority')
            cost = decoder_cost(op)
            need(cost == op['decoderCost'] and self.spent + cost <= self.maximum, 'Full MP4 decoder ceiling exceeded')
            self.index += 1
            self.spent += cost
            line = (json.dumps({'sequence': self.index, 'operation': role, 'decoderCost': cost,
                               'decoderReservations': self.spent, 'argv': argv}, sort_keys=True) + '\n').encode()
            try:
                need(os.write(self.fd, line) == len(line), 'Short reservation write')
                os.fsync(self.fd)
                self.content += line
                need(self.receipt.read_bytes() == self.content, 'Reservation not durable/current')
                return delegate()
            except BaseException:
                self.failed = True  # A launch/error/cancel consumes the reservation and ends this attempt.
                raise


def sample():
    vm = subprocess.check_output(['/usr/bin/vm_stat'], text=True, timeout=3)
    page = int(re.search(r'page size of (\d+) bytes', vm).group(1))
    free = int(re.search(r'Pages free:\s*(\d+)', vm).group(1)) * page
    pressure = subprocess.check_output(['/usr/sbin/sysctl', '-n', 'kern.memorystatus_vm_pressure_level'], text=True, timeout=3).strip()
    swap = subprocess.check_output(['/usr/sbin/sysctl', '-n', 'vm.swapusage'], text=True, timeout=3)
    used = re.search(r'used = ([\d.]+)M', swap)
    need(used is not None, 'Unknown swap')
    fs = os.statvfs(REPO)
    return {'freeMemoryBytes': free, 'pressureCode': pressure, 'swapUsedBytes': int(float(used.group(1)) * 1048576),
            'freeDiskBytes': fs.f_bavail * fs.f_frsize}


def stable_resources(p):
    readings = []
    for _ in range(3):
        r = sample()
        need(r['pressureCode'] == '1' and r['freeMemoryBytes'] >= p['limits']['minimumFreeMemoryBytes'], 'No NORMAL/free-memory admission')
        need(r['freeDiskBytes'] >= p['limits']['minimumFreeDiskBytes'], 'Insufficient disk admission')
        readings.append(r)
        time.sleep(1)
    return readings


def bytes_under(root):
    if not os.path.lexists(root):
        return 0
    need(not Path(root).is_symlink(), 'Symlink storage root')
    if Path(root).is_file():
        return Path(root).stat().st_size
    total = 0
    for parent, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            f = Path(parent) / name
            need(not f.is_symlink(), 'Symlink in owned storage')
            if f.is_file():
                need(stat.S_ISREG(f.lstat().st_mode), 'Nonregular owned artifact')
                total += f.stat().st_size
    return total


def process_rows():
    text = subprocess.check_output(['/bin/ps', '-axo', 'pid=,ppid=,pgid=,rss=,lstart='], text=True, timeout=3)
    rows = {}
    for line in text.splitlines():
        x = line.split(maxsplit=4)
        if len(x) == 5:
            rows[int(x[0])] = {'parent': int(x[1]), 'group': int(x[2]), 'rss': int(x[3]) * 1024, 'birth': x[4]}
    return rows


def owned_ids(rows, pid):
    found = {i for i, row in rows.items() if i == pid or row['group'] == pid}
    while True:
        children = {i for i, row in rows.items() if row['parent'] in found}
        if children <= found:
            return found
        found |= children


def enforce(p, baseline, reading, rss, storage, now, deadline):
    need(now < deadline, 'Shared phase/cumulative deadline expired')
    need(rss <= p['limits']['aggregateRssBytes'], 'Aggregate Python/owned-child RSS ceiling exceeded')
    need(reading['pressureCode'] == '1', 'Memory pressure is not NORMAL')
    need(reading['swapUsedBytes'] - baseline['swapUsedBytes'] <= p['limits']['maximumSwapGrowthBytes'], 'Swap growth ceiling exceeded')
    need(storage <= p['limits']['newStorageBytes'], 'Combined new storage ceiling exceeded')
    reserve = p['limits']['freeDiskReserveBytes'] + max(0, p['limits']['newStorageBytes'] - storage)
    need(reading['freeDiskBytes'] >= reserve, 'Unspent storage/free-disk reserve exceeded')


def media_facts(probe, delivery=None):
    delivery=delivery or config()['delivery']
    frames,width,height=delivery['frames'],delivery['width'],delivery['height']
    need(len(probe.get('streams', [])) == 1 and len(probe.get('frames', [])) == frames, 'Wrong streams/frame count')
    s = probe['streams'][0]
    expected = {'codec_type': 'video', 'codec_name': 'h264', 'width': width, 'height': height,
                'sample_aspect_ratio': '1:1', 'avg_frame_rate': '30/1', 'r_frame_rate': '30/1',
                'pix_fmt': 'yuv420p', 'nb_read_frames': str(frames), 'time_base': '1/15360',
                'color_space': 'bt709', 'color_transfer': 'bt709', 'color_primaries': 'bt709','color_range':'tv'}
    need(all(s.get(k) == v for k, v in expected.items()), 'Media codec/geometry/cadence/color mismatch')
    need(int(s['duration_ts']) == frames * 512, 'Wrong duration')
    need([int(f['best_effort_timestamp']) for f in probe['frames']] == [i * 512 for i in range(frames)], 'Nonuniform PTS')
    return {'frames': frames, 'fps': 30, 'seconds': frames/30, 'audioStreams': 0, 'codec': 'h264', 'width': width,
            'height': height, 'sar': '1:1', 'pixFmt': 'yuv420p', 'timeBase': '1/15360', 'ptsStep': 512, 'color': 'bt709'}


class Supervisor:
    def __init__(self, p, baseline, deadline, owned_anchors):
        validate_phases(p)
        self.p, self.baseline, self.deadline, self.anchors = p, baseline, deadline, owned_anchors
        self.work = Path(p['destinations']['work'])
        self.phase_starts = {}
        self.active_phase = None
        self.peak_rss = self.peak_storage = 0

    def check(self, deadline=None, rss=None):
        paths.validate(self.p['destinationContract'], self.p['destinations'])
        for name, expected in self.anchors.items():
            need(paths.identity(Path(name), paths.directory_info(Path(name))) == expected, 'Changed owned directory')
        storage = bytes_under(BASE) + bytes_under(PROOF) + bytes_under(self.p['scratch']['path'])
        if rss is None:
            rows = process_rows()
            rss = rows.get(os.getpid(), {}).get('rss', 0)
        self.peak_rss, self.peak_storage = max(self.peak_rss, rss), max(self.peak_storage, storage)
        phase_end = self.deadline if self.active_phase is None else self.phase_starts[self.active_phase] + self.p['limits']['phaseSeconds'][self.active_phase]
        enforce(self.p, self.baseline, sample(), rss, storage, time.monotonic(), min(deadline or self.deadline, phase_end, self.deadline))

    def execute(self, op):
        phase = op['phase']
        self.phase_starts.setdefault(phase, time.monotonic())
        self.active_phase = phase
        deadline = min(self.deadline, self.phase_starts[phase] + self.p['limits']['phaseSeconds'][phase])
        self.check(deadline)
        verify_bindings(self.p)
        authorize(self.p)
        self.check(deadline)
        log = self.work / (op['id'] + '.log')
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1',
                   MELOTRAIL_BLENDER_PACKET=sha(PACKET), MELOTRAIL_PROOF_KIND=KIND, MELOTRAIL_PACKET_FILE=str(PACKET), TMPDIR=self.p['scratch']['path']+'/',
                   BLENDER_USER_CONFIG=self.p['scratch']['path'], BLENDER_USER_SCRIPTS=self.p['scratch']['path'])
        for name in ('PYTHONPATH', 'PYTHONHOME', 'NODE_OPTIONS', 'NODE_PATH', 'DYLD_LIBRARY_PATH', 'DYLD_INSERT_LIBRARIES', 'LD_PRELOAD'):
            env.pop(name, None)
        command = [self.p['tools']['sandbox'], '-p', self.p['sandboxProfile'], *op['argv']]
        started = time.monotonic()
        with log.open('xb') as output:
            proc = subprocess.Popen(command, cwd=self.work, start_new_session=True, stdout=output, stderr=subprocess.STDOUT, env=env)
            try:
                while True:
                    rows = process_rows()
                    ids = owned_ids(rows, proc.pid)
                    rss = sum(rows[i]['rss'] for i in ids) + rows.get(os.getpid(), {}).get('rss', 0)
                    # Leave twenty seconds for bounded metadata calls and owned-group teardown.
                    self.check(deadline - 20, rss)
                    need(log.stat().st_size <= 8 * 1024 * 1024, 'Owned log ceiling exceeded')
                    if proc.poll() is not None:
                        need(proc.returncode == 0, 'Stage failed; evidence retained, no retry: ' + op['id'])
                        need(not owned_ids(process_rows(), proc.pid), 'Descendant teardown unconfirmed')
                        break
                    time.sleep(0.5)
            except BaseException:
                if proc.poll() is None or owned_ids(process_rows(), proc.pid):
                    try:
                        os.killpg(proc.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
                    try:
                        proc.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        os.killpg(proc.pid, signal.SIGKILL)
                        proc.wait(timeout=2)
                    if owned_ids(process_rows(), proc.pid):
                        try:
                            os.killpg(proc.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        need(not owned_ids(process_rows(), proc.pid), 'STOP UNCONFIRMED; never relaunch')
                raise
        self.check(deadline)
        fresh_json(self.work / (op['id'] + '-resource.json'), {'elapsedSeconds': time.monotonic() - started,
                   'sampledPeakAggregateRssBytes': self.peak_rss, 'peakNewStorageBytes': self.peak_storage})


def preflight(p):
    verify_bindings(p)
    paths.validate(p['destinationContract'], p['destinations'], fresh=True)
    need(sum(decoder_cost(o) for o in p['operations']) == 2, 'Untrue decoder plan')
    need(not APPROVAL.exists(), 'This read-only preflight is for the unadmitted packet')
    print('READ_ONLY_PREFLIGHT_PASS_NO_RENDER_NO_MEDIA_NO_LIVE_ADMISSION')


def run(p):
    # Deny before resource sampling, work/claim creation or any native/video stage.
    authorize(p)
    start = time.monotonic()
    fresh_json(PROOF/'checks/launch-claim.json',{'packetSha256':sha(PACKET),'userInstruction':'you can continue with the point 2 of the list','kind':KIND,'startedUnixSeconds':time.time()})
    verify_bindings(p)
    paths.validate(p['destinationContract'], p['destinations'], fresh=True)
    readings = stable_resources(p)
    authorize(p)
    paths.validate(p['destinationContract'], p['destinations'], fresh=True)
    work = Path(p['destinations']['work'])
    anchors = {a['path']: a for a in p['destinationContract']['ancestors']}
    info = paths.fresh_directory(work, anchors)
    anchors[str(work)] = info
    for name in ('source', 'decoded', 'encoded_input'):
        info = paths.fresh_directory(work / name, anchors)
        anchors[str(work / name)] = info
    fresh_json(work / 'attempt-claim.json', {'packetSha256': sha(PACKET), 'startedUtcUnixSeconds': time.time(),
               'cumulativeSeconds': p['limits']['cumulativeSeconds'], 'baselineSamples': readings, 'ownedDirectories': anchors})
    deadline = start + p['limits']['cumulativeSeconds']
    supervisor = Supervisor(p, readings[0], deadline, anchors)
    budget = Budget(p['operations'], 2, work / 'reservations.jsonl', deadline, recheck=supervisor.check)
    try:
        for op in p['operations']:
            if op['id'] == 'encode':
                need((work / 'source-pixels.json').is_file(), 'Source proof required before encode')
            if op['id'] == 'decode':
                facts = media_facts(json.loads((work / 'probe.log').read_text()))
                fresh_json(work / 'media-facts.json', facts)
            budget.invoke(op['id'], op['argv'], lambda op=op: supervisor.execute(op))
        need(budget.index == 6 and budget.spent == 2, 'Incomplete actual operation plan')
        need((work / 'decoded-pixels.json').is_file(), 'Decoded proof missing')
        need(supervisor.active_phase == 'validationAndCopy', 'Publication must share validation deadline')
        mp4 = work / 'preview.mp4'
        digest = sha(mp4)
        supervisor.check()
        review = paths.publish(mp4, p['destinationContract'], digest, supervisor.check)
        need(sha(review) == digest, 'Review publication bytes differ')
        fresh_json(work / 'result.json', {'status': 'TECHNICAL_PROOF_PASS_NOT_MOVING_APPROVAL', 'packetSha256': sha(PACKET),
                   'mp4Sha256': digest, 'reviewFile': str(review), 'decoderTraversals': budget.spent,
                   'elapsedSeconds': time.monotonic() - start, 'sampledPeakAggregateRssBytes': supervisor.peak_rss,
                   'newStorageBytes': bytes_under(BASE) + bytes_under(PROOF), 'productionImports': 0,
                   'humanReview': 'Normal-speed appearance/cadence/identity/contact/return pending; no full-film claim'})
    except BaseException as error:
        fresh_json(work / 'failure.json', {'status': 'STOPPED_NO_RETRY', 'error': str(error),
                   'decoderReservations': budget.spent, 'operationsReserved': budget.index,
                   'sampledPeakAggregateRssBytes': supervisor.peak_rss, 'peakNewStorageBytes': supervisor.peak_storage,
                   'elapsedSeconds': time.monotonic() - start})
        raise
    finally:
        budget.close()


def cleanup(p):
    scratch=Path(p['scratch']['path'])
    need(scratch.is_dir() and not scratch.is_symlink(),'Scratch boundary changed')
    need(paths.identity(scratch,paths.directory_info(scratch))==p['scratch'],'Scratch identity changed')
    entries=list(scratch.rglob('*'))
    need(all(not f.is_symlink() for f in entries),'Unsafe scratch link; retained for inspection')
    files=[f for f in entries if f.is_file()]
    if files:
        archive=PROOF/'checks/scratch-files.zip'
        with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as z:
            for f in files:z.write(f,str(f.relative_to(scratch)))
        with zipfile.ZipFile(archive) as z:
            need(z.testzip() is None,'Scratch preservation failed')
            for f in files:need(z.read(str(f.relative_to(scratch)))==f.read_bytes(),'Scratch archive mismatch')
    shutil.rmtree(scratch)
    fresh_json(PROOF/'checks/cleanup.json',{'removedScratch':str(scratch),'retainedScratchFiles':len(files)})


if __name__ == '__main__':
    need(len(sys.argv)==3 and sys.argv[1] in ('colour','wave') and sys.argv[2] in ('preflight','run'),'Use colour|wave preflight|run; no stage/retry entrypoints')
    p=config()
    if sys.argv[2]=='preflight':preflight(p)
    else:
        try:run(p)
        except BaseException as e:
            if not (PROOF/'checks/stopped.json').exists():fresh_json(PROOF/'checks/stopped.json',{'status':'STOPPED_NO_RETRY','error':str(e),'packetSha256':sha(PACKET)})
            raise
        finally:cleanup(p)
