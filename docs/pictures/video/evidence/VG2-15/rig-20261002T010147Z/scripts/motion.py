"""One bounded absolute-frame wave; no bpy, rendering, or process launch."""
import hashlib
import json
import math
from pathlib import Path


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def validate(spec, repo, verify=True):
    assert spec['schema'] == 'private-vg2-15-cutout-proof-1'
    assert spec['frames'] == 150 and spec['fps'] == 30
    assert spec['dimensions'] == [1920, 1080]
    assert set(spec['parts']) == {'background', 'shoulder-cover', 'elbow-cover', 'upper', 'forearm', 'neutral-front', 'neutral-overlap', 'open-front', 'open-overlap', 'protected', 'wrist-support'}
    assert spec['shoulder'] == [772, 619]
    assert all(math.isfinite(v) and v > 0 for v in spec['lengths'])
    keys = spec['keys']
    assert [k['frame'] for k in keys] == sorted(set(k['frame'] for k in keys))
    assert keys[0]['frame'] == 0 and keys[-1]['frame'] == 149
    assert keys[0]['angles'] == keys[-1]['angles']
    for key in keys:
        assert set(key) == {'frame', 'angles', 'hand'}, 'conflicting/unknown controls'
        assert key['hand'] in ('neutral', 'open'), 'unsupported hand'
        assert len(key['angles']) == 3
        for v, name in zip(key['angles'], ['shoulder', 'forearm', 'wrist']):
            assert math.isfinite(v) and spec['limits'][name][0] <= v <= spec['limits'][name][1], 'unsupported pose'
    assert spec['handSwitchFrames'] == [25, 128]
    for part in spec['parts'].values():
        assert part['texture'] in spec['textures'], 'missing part texture'
        assert part['control'] in ('fixed', 'shoulder-cover', 'elbow-cover', 'shoulder', 'elbow', 'wrist')
        assert part['scale'] > 0
        assert part['hand'] in (None, 'neutral', 'open')
    if verify:
        for pin in list(spec['textures'].values()) + spec['dependencies'] + [spec['boundaries'], spec['neutralReference']]:
            p = repo / pin['path']
            assert p.is_file() and p.resolve() == p and not p.is_symlink(), 'missing/noncanonical input'
            assert digest(p) == pin['sha256'], 'changed input pin: ' + str(p)


def state(spec, frame):
    assert isinstance(frame, (int, float)) and math.isfinite(frame) and 0 <= frame <= 149
    keys = spec['keys']
    lo, hi = keys[0], keys[-1]
    for a, b in zip(keys, keys[1:]):
        if a['frame'] <= frame <= b['frame']:
            lo, hi = a, b
            break
    t = (frame-lo['frame'])/(hi['frame']-lo['frame'])
    ease = t*t*(3-2*t)
    angles = [a+(b-a)*ease for a, b in zip(lo['angles'], hi['angles'])]
    hand = 'open' if spec['handSwitchFrames'][0] <= frame < spec['handSwitchFrames'][1] else 'neutral'
    shoulder = spec['shoulder']
    def end(start, length, degrees):
        a = math.radians(degrees)
        return [start[0]+length*math.cos(a), start[1]+length*math.sin(a)]
    elbow = end(shoulder, spec['lengths'][0], angles[0])
    wrist = end(elbow, spec['lengths'][1], angles[1])
    return {'frame': frame, 'angles': angles, 'hand': hand, 'shoulder': shoulder, 'elbow': elbow, 'wrist': wrist}


def fingerprint(spec):
    return hashlib.sha256(json.dumps(spec, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
