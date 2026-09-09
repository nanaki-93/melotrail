import test from 'node:test';
import assert from 'node:assert/strict';
import { checkCompanion } from './companion-check.mjs';

test('native companion validation runs boundary, media regression and release build without Java', () => {
  const calls = [];
  assert.equal(checkCompanion((...args) => { calls.push(args); return { status: 0 }; }, '/owned project'), 0);
  assert.deepEqual(calls.map(([command]) => command), ['./companion/scripts/verify-boundary.sh', './companion/scripts/test.sh', 'swift']);
  assert.deepEqual(calls[2][1], ['build', '--disable-sandbox', '--package-path', 'companion', '-c', 'release']);
  assert.ok(calls.every(([, , opts]) => opts.cwd === '/owned project' && opts.timeout === 600_000));
});
test('every native failure, signal or missing tool stops before later checks and fails the gate', () => {
  for (const failed of [0, 1, 2]) for (const failure of [{ status: 7 }, { status: null, signal: 'SIGTERM' }, { error: new Error('ENOENT') }]) {
    let calls = 0;
    assert.notEqual(checkCompanion(() => calls++ === failed ? failure : { status: 0 }), 0);
    assert.equal(calls, failed + 1);
  }
});
