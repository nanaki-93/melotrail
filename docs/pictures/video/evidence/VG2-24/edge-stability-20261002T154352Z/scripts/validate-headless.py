"""Run the required windowless checks, keeping live logs outside Gradle's docs input."""
from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys, time
import xml.etree.ElementTree as ET

run = Path(__file__).resolve().parents[1]
scratch = Path(sys.argv[1]).resolve(strict=True)
root = Path.cwd()
environment = os.environ.copy()
environment.pop('MELOTRAIL_RUN_LIVE_E2E', None)
environment.pop('MELOTRAIL_RESUME_LIVE_E2E', None)
environment['JAVA_HOME'] = '/Users/marcoandreose/.sdkman/candidates/java/21.0.11-tem'
gradle = './gradlew -I ' + str(run / 'scripts/headless.init.gradle')
started = time.time()
results = []
for target in ['test', 'build']:
    command = ['make', 'GRADLE=' + gradle, target]
    log = scratch / ('make-' + target + '.log')
    stage_start = time.monotonic()
    with log.open('x') as output:
        process = subprocess.run(command, env=environment, stdout=output, stderr=subprocess.STDOUT, timeout=900)
    results.append({'target': target, 'command': command, 'exitCode': process.returncode, 'seconds': time.monotonic() - stage_start})
    (scratch / 'validation-progress.json').write_text(json.dumps(results, indent=2) + '\n')
    assert process.returncode == 0, (target, str(log))
counts = {}
for module in ['', 'desktopApp/']:
    report_root = root / module / 'build/test-results/test'
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    sources = []
    for path in sorted(report_root.glob('TEST-*.xml')):
        result = ET.parse(path).getroot()
        assert 'MidiCoreNativeResponsivenessTest' not in path.name
        for key in totals: totals[key] += int(result.get(key, '0'))
        sources.append({'path': str(path.relative_to(root)), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'mtimeSeconds': path.stat().st_mtime})
    assert totals['tests'] > 0 and not totals['failures'] and not totals['errors']
    counts[module or 'root'] = {'counts': totals, 'reports': sources}
summary = {
    'status': 'PASS', 'startedAtEpochSeconds': started, 'commands': results,
    'headless': True, 'excludedTests': ['**/MidiCoreNativeResponsivenessTest*'],
    'liveE2eFlagsUnset': True, 'testReports': counts,
    'limitation': 'Cached test tasks are reported in the raw Gradle logs; no native-window, Logic/editor or human artistic pass is claimed.'
}
(scratch / 'validation-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({'status': 'PASS', 'commands': results, 'counts': {key: value['counts'] for key, value in counts.items()}}))
