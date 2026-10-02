"""Preserve headless results, then remove only this run's new root test reports."""
from pathlib import Path
import hashlib, json, shutil, sys, zipfile

run = Path(__file__).resolve().parents[1]
scratch = Path(sys.argv[1]).resolve(strict=True)
root = Path.cwd()
summary = json.loads((scratch / 'validation-summary.json').read_text())
assert summary['status'] == 'PASS'
for name in ['make-test.log', 'make-build.log', 'headless-build-task-graph.log', 'validation-summary.json']:
    target = run / 'checks' / name
    assert not target.exists()
    shutil.copyfile(scratch / name, target)
    assert (scratch / name).read_bytes() == target.read_bytes()
archive = run / 'checks/headless-validation-reports.zip'
manifest = []
roots = [root / 'build/test-results/test', root / 'build/reports/tests/test']
with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED) as bundle:
    for folder in roots:
        for path in sorted(folder.rglob('*')):
            if not path.is_file(): continue
            assert not path.is_symlink()
            data = path.read_bytes()
            name = str(path.relative_to(root))
            bundle.writestr(name, data)
            manifest.append({'path': str(path), 'archiveName': name, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data), 'ownedNewReport': path.stat().st_mtime >= summary['startedAtEpochSeconds']})
    for label, files in [
        ('focused', sorted((scratch / 'focused-reports').glob('TEST-*.xml'))),
        ('cached-desktop', sorted((root / 'desktopApp/build/test-results/test').glob('TEST-*.xml'))),
    ]:
        for path in files:
            data = path.read_bytes()
            name = label + '/' + path.name
            bundle.writestr(name, data)
            manifest.append({'path': str(path), 'archiveName': name, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data), 'ownedNewReport': False})
with zipfile.ZipFile(archive) as bundle:
    for record in manifest:
        data = bundle.read(record['archiveName'])
        assert len(data) == record['bytes'] and hashlib.sha256(data).hexdigest() == record['sha256']
removed = []
for record in manifest:
    if not record['ownedNewReport']: continue
    path = Path(record['path'])
    assert any(path.is_relative_to(folder) for folder in roots)
    assert not path.is_symlink() and hashlib.sha256(path.read_bytes()).hexdigest() == record['sha256']
    path.unlink()
    removed.append(str(path))
for folder in roots:
    for path in sorted([p for p in folder.rglob('*') if p.is_dir()], key=lambda p: len(p.parts), reverse=True):
        if not path.is_symlink() and not any(path.iterdir()): path.rmdir()
receipt = {
    'status': 'PASS', 'archive': str(archive), 'archiveSha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
    'archiveBytes': archive.stat().st_size, 'allCopiesVerified': True,
    'removedOwnedRootReports': removed, 'files': manifest,
    'preserved': 'Cached desktop reports, compiled classes, Gradle caches, unrelated files and prior evidence.',
}
with (run / 'checks/validation-report-cleanup.json').open('x') as output: json.dump(receipt, output, indent=2)
print(json.dumps({key: value for key, value in receipt.items() if key not in ['files', 'removedOwnedRootReports']} | {'removedReportCount': len(removed), 'retainedReportCount': len(manifest)}))
