"""Retain the exact success/failure split and source/resource evidence, without native work."""
from pathlib import Path
import datetime, hashlib, json, os, sys

run = Path(__file__).resolve().parents[1]
stage = run / 'breath-quality'
scratch = Path(sys.argv[1]).resolve(strict=True)
assets = Path.cwd() / 'docs/pictures/video/tabi-assets/train-actions' / run.name
def read(path): return json.loads(path.read_text())
def digest(path):
    sha = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024**2), b''): sha.update(chunk)
    return sha.hexdigest()
def size(path): return sum(p.stat().st_size for p in path.rglob('*') if p.is_file() and not p.is_symlink())
pins = read(run / 'checks/protected-inputs.json')['inputs']
pins += read(run / 'inputs/admission.json')['sourceMovies']
for pin in pins:
    path = Path(pin['path'])
    assert path.is_file() and not path.is_symlink()
    assert digest(path) == pin['sha256'] and path.stat().st_size == pin['bytes']
layers = read(run / 'checks/local-assets.json')
assert layers['frames'] == 226
for pin in layers['files']:
    path = Path(pin['path'])
    assert path.is_relative_to(assets) and digest(path) == pin['sha256']
cleanup = []
for receipt in sorted((run / 'comfy').glob('*/checks/runtime-cleanup.json')) + [stage / 'checks/runtime-cleanup.json']:
    record = read(receipt)
    assert record['runtimeState'] == 'STOPPED' and record['ownedSessionRemoved']
    assert not Path(record['originalSession']).exists()
    assert record.get('allCopiesOrImmutableReferencesVerified', record.get('allCopiesVerified', False))
    execution = read(receipt.with_name('execution.json'))
    for pid in execution['observedPids']:
        try: os.kill(pid, 0)
        except ProcessLookupError: pass
        else: raise AssertionError(f'Observed PID still exists or was reused: {pid}; inspect before cleanup')
    cleanup.append(str(receipt.relative_to(run)))
assert len(cleanup) == 8
comfy = read(run / 'checks/comfy-completion.json')
first_seconds = comfy['seconds'] + sum(read(p)['seconds'] for p in (run / 'checks').glob('assembly-*.json'))
quality_seconds = read(stage / 'checks/execution.json')['elapsedSeconds']
quality_seconds += sum(read(p)['seconds'] for p in (stage / 'checks').glob('verify-*.json'))
quality_seconds += read(stage / 'checks/breath-exported-edge-check.json')['seconds']
quality_seconds += read(stage / 'checks/pixel-quality-audit.json')['seconds']
initial_bytes = size(run) - size(stage) + size(assets) + size(scratch)
assert first_seconds < 600 and quality_seconds < 180
assert initial_bytes < 4 * 1024**3 and size(stage) < 512 * 1024**2
drink = read(run / 'checks/drink-exported-edge-check.json')
breath = read(stage / 'checks/breath-exported-edge-check.json')
assert drink['status'] == 'PASS' and breath['status'] == 'REVIEW_REQUIRED'
movies = [run / 'review/drink/tabi-drink-stable-edges-comfy-1080p.mp4', stage / 'review/tabi-breath-comfy-1080p.mp4']
result = {
    'recordedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'status': 'DRINK_TECHNICAL_PASS_BREATH_TEMPORAL_GATE_FAILED',
    'newHumanAppearanceAcceptance': False,
    'movies': [{'path': str(p), 'sha256': digest(p), 'bytes': p.stat().st_size} for p in movies],
    'protectedInputCount': len(pins),
    'protectedInputsUnchanged': True,
    'derivedAssetFramesVerified': 226,
    'stoppedRuntimeCleanupReceipts': cleanup,
    'initialNativeAndMediaSeconds': first_seconds,
    'qualityNativeVerificationAndCachedFrameAuditSeconds': quality_seconds,
    'initialScopeBytesBeforeValidationReports': initial_bytes,
    'qualityScopeRetainedBytes': size(stage),
    'nativeMotionInferences': 0,
    'encodes': {'drink': 1, 'breathInitial': 1, 'breathExplicitlyApprovedQuality': 1},
    'nativeRetries': 0,
    'preflightHarnessRepair': 'Unique loopback ports after one JVM-only occupied-port rejection; first completed batch reused.',
    'decoderTraversalsPerExport': 2,
    'drinkEdgeReductionFraction': drink['reductionFraction'],
    'breathEdgeReductionFraction': breath['reductionFraction'],
    'unchangedEdgeGateReductionFraction': .05,
    'remainingWork': 'Stabilize breathing source linework and moving matte before a separately admitted export. Do not repeat encoding alone or lower the gate.',
    'queue': {'VG2-24': 'REVIEW', 'VG2-27': 'WAITING_USER'},
    'mediaStorage': 'Local only; scripts/checks and owner documentation may be versioned.',
}
with (run / 'checks/outcome.json').open('x') as output: json.dump(result, output, indent=2)
print(json.dumps({k: v for k, v in result.items() if k not in ['movies', 'stoppedRuntimeCleanupReceipts']}))
