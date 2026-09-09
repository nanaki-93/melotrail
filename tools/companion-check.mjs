import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Coordinator adapter only. The companion remains independently built by Swift.
export function checkCompanion(run = spawnSync, root = process.cwd()) {
  const steps = [
    ['./companion/scripts/verify-boundary.sh', []],
    ['./companion/scripts/test.sh', []],
    ['swift', ['build', '--disable-sandbox', '--package-path', 'companion', '-c', 'release']],
  ];
  for (const [command, args] of steps) {
    const result = run(command, args, { cwd: root, stdio: 'inherit', timeout: 600_000 });
    if (result.error || result.signal || result.status !== 0) {
      return Number.isInteger(result.status) && result.status > 0 ? result.status : 1;
    }
  }
  return 0;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = checkCompanion();
}
