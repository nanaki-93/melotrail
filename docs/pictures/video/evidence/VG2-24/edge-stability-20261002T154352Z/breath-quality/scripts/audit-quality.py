"""Read cached PNGs only; retain the failed temporal gate without re-encoding."""
from pathlib import Path
import json, resource, time
import numpy as np
from PIL import Image

stage = Path(__file__).resolve().parents[1]
run = stage.parent
started = time.monotonic()
rows = []
for number in range(1, 98):
    assert time.monotonic() - started < 120
    assert resource.getrusage(resource.RUSAGE_SELF).ru_maxrss < 2 * 1024**3
    source = np.asarray(Image.open(run / 'review/breath/frames' / f'frame-{number:04d}.png').convert('RGB'), dtype='float32')
    decoded = np.asarray(Image.open(stage / 'review/frames' / f'frame-{number:04d}.png').convert('RGB'), dtype='float32')
    psnr = float(10 * np.log10(255**2 / max(float(np.mean((source - decoded)**2)), 1e-9)))
    face = float(np.abs(source[350:620, 500:900] - decoded[350:620, 500:900]).mean())
    assert psnr > 32 and face < 8, (number, psnr, face)
    rows.append({'frame': number, 'psnrDb': psnr, 'faceMae': face})
edge = json.loads((stage / 'checks/breath-exported-edge-check.json').read_text())
assert edge['status'] == 'REVIEW_REQUIRED' and edge['reductionFraction'] < .05
# Descriptive decomposition of the existing check, never a new acceptance rule.
groups = {}
for label, selected in [
    ('originalResidualBelow2', [r for r in edge['rows'] if r['oldExportEdgeMae'] < 2]),
    ('originalResidualAtLeast2', [r for r in edge['rows'] if r['oldExportEdgeMae'] >= 2]),
]:
    groups[label] = {
        'pairs': len(selected),
        'oldMean': float(np.mean([r['oldExportEdgeMae'] for r in selected])),
        'newMean': float(np.mean([r['newExportEdgeMae'] for r in selected])),
    }
result = {
    'status': 'PIXEL_FIDELITY_PASS_TEMPORAL_GATE_FAILED',
    'frames': 97,
    'minPsnrDb': min(r['psnrDb'] for r in rows),
    'maxFaceMae': max(r['faceMae'] for r in rows),
    'seconds': time.monotonic() - started,
    'peakProcessRssBytes': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
    'additionalVideoTraversals': 0,
    'additionalEncodes': 0,
    'temporalGateUnchanged': 'At least 5 percent reduction; this candidate achieves only 3.4484 percent.',
    'descriptiveResidualGroups': groups,
    'limitations': [
        'Residual groups describe measured variation, not independent movement or perceptual quality.',
        'The old breathing clip animated the whole scene; this candidate composites a moving matte over a fixed cabin.',
        'Source linework variation and compositing already consume most of the margin before encoding.',
        'Human appearance acceptance is still required; no long-film readiness follows.',
    ],
    'rows': rows,
}
with (stage / 'checks/pixel-quality-audit.json').open('x') as output:
    json.dump(result, output, indent=2)
print(json.dumps({key: value for key, value in result.items() if key != 'rows'}))
