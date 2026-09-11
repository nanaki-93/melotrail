import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { atomic, queue } from './terra-runner.mjs';

// Real coordinator, worktrees, Git commits and subprocesses; only model/build
// executables are fixtures. Their journal lives outside every worker worktree.
const runner = fileURLToPath(new URL('./terra-runner.mjs', import.meta.url));
const chain = '| F01 | Baseline | — | DONE | |\n| M01 | First | F01 | TODO | |\n| M02 | Second | M01 | TODO | |\n| M03 | Third | M02 | TODO | |';
const independent = chain.replace('| M02 | Second | M01 |', '| M02 | Second | F01 |').replace('| M03 | Third | M02 |', '| M03 | Third | M01, M02 |');

function fixture({ markdown = chain, config = {}, scenario = {} } = {}) {
  const root = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'terra-throughput-')));
  const repo = path.join(root, 'repo'), state = path.join(root, 'state'), bin = path.join(root, 'bin');
  const eventsFile = path.join(root, 'events.jsonl'), scenarioFile = path.join(root, 'scenario.json');
  for (const dir of [repo, state, bin, path.join(repo, 'tools'), path.join(repo, 'src')]) fs.mkdirSync(dir, { recursive: true });
  atomic(scenarioFile, scenario);
  const git = (...args) => execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git('init'); git('config', 'user.name', 'Runner fixture'); git('config', 'user.email', 'runner@example.invalid');
  fs.writeFileSync(path.join(repo, 'TASKS.md'), markdown);
  fs.writeFileSync(path.join(repo, 'src/baseline.txt'), 'protected baseline\n');
  fs.writeFileSync(path.join(repo, 'tools/terra-runner.test.mjs'), "import test from 'node:test'; test('fixture coordinator gate', () => {});\n");
  fs.writeFileSync(path.join(repo, 'tools/terra-throughput.test.mjs'), "import test from 'node:test'; test('fixture throughput gate', () => {});\n");
  const support = `const fs = require('node:fs'), path = require('node:path');
const scenario = JSON.parse(fs.readFileSync(${JSON.stringify(scenarioFile)}, 'utf8'));
const eventsFile = ${JSON.stringify(eventsFile)};
const snapshot = () => fs.readdirSync('src').sort();
const emit = e => fs.appendFileSync(eventsFile, JSON.stringify({ ...e, at: Date.now(), cwd: process.cwd(), files: snapshot(), contents: Object.fromEntries(snapshot().map(name => [name, fs.readFileSync(path.join('src', name), 'utf8')])) }) + '\\n');
`;
  const codex = path.join(bin, 'codex');
  fs.writeFileSync(codex, `#!${process.execPath}
${support}
let prompt = '';
process.stdin.on('data', data => prompt += data);
process.stdin.on('end', () => {
  const task = prompt.match(/Assigned task: (\\w+)/)[1];
  const base = prompt.match(/Base commit: (\\w+)/)[1];
  const candidate = prompt.match(/Candidate tree: ([A-Za-z0-9_]+)/)[1];
  const recovery = prompt.match(/Focused recovery subtask ([^:]+):/);
  const review = prompt.includes('Mode: Fresh independent REVIEW');
  const kind = review ? 'review' : 'worker';
  const args = process.argv.slice(2);
  const resultFile = args[args.indexOf('-o') + 1];
  const previous = fs.existsSync(eventsFile) ? fs.readFileSync(eventsFile, 'utf8').trim().split('\\n').filter(Boolean).map(JSON.parse) : [];
  const count = previous.filter(e => e.type === 'start' && e.task === task && e.kind === kind).length;
  const session = args.includes('resume') ? args.find(arg => /^00000000-0000-4000-8000-\\d{12}$/.test(arg))
    : '00000000-0000-4000-8000-' + String(Number(task.replace(/\\D/g, '')) * 100 + count).padStart(12, '0');
  emit({ type: 'start', kind, task, base, candidate, args, prompt, resultFile, session });
  if (!review && !args.includes('resume')) console.log(JSON.stringify({ type: 'thread.started', thread_id: session }));
  const complete = () => {
    if (!review && !recovery) fs.writeFileSync(path.join('src', task + '.txt'), 'implemented ' + task + '\\n');
    if (!review && recovery && !scenario.recoveryNoChange) {
      const index = Number(recovery[1].match(/R(\\d+)$/)[1]) - 1;
      for (const file of scenario.recoveryFindings[index].files) fs.writeFileSync(file,
        scenario.recoveryUnresolved ? 'different but still broken\\n' : 'fixed ' + scenario.recoveryFindings[index].id + '\\n');
      if (scenario.recoveryExtraPath) fs.writeFileSync(scenario.recoveryExtraPath, 'unexpected extra change\\n');
    }
    if (!review && scenario.extraWorkerPaths?.[task]) for (const file of scenario.extraWorkerPaths[task]) fs.writeFileSync(file, 'out-of-scope candidate edit\\n');
    let fail = review && ((scenario.failReviewTasks || []).includes(task) || count < (scenario.failFirstReviews || 0));
    const structured = review && JSON.parse(fs.readFileSync(args[args.indexOf('--output-schema') + 1])).properties.findings;
    const resolved = structured ? (scenario.recoveryFindings || []).filter(f => f.files.length && f.files.every(file => fs.existsSync(file) && fs.readFileSync(file, 'utf8').includes('fixed ' + f.id))).map(f => f.id) : [];
    const findings = structured ? (scenario.recoveryFindings || []).filter(f => !resolved.includes(f.id)) : [];
    if (structured) fail = findings.length > 0;
    const result = { task, base, commit: base, candidate,
      status: review ? (fail ? 'FAIL' : 'PASS') : (scenario.workerStatuses?.[task] || scenario.workerStatus || 'READY_FOR_VALIDATION'),
      summary: fail ? 'Reproduced 6/8 accent defect' : 'Fixture candidate ' + task,
      blocker: fail ? 'Correct compound-meter accent weighting' : review ? '' : (scenario.workerBlocker || ''),
      tests: ['PENDING_COORDINATOR: required gates'], artifacts: [] };
    if (structured) Object.assign(result, { findings, resolvedFindingIds: resolved });
    fs.writeFileSync(resultFile, JSON.stringify(result));
    emit({ type: 'end', kind, task, base, candidate, status: result.status });
    console.log(JSON.stringify({ type: 'turn.completed', usage: { input_tokens: 10, output_tokens: 2 } }));
  };
  setTimeout(complete, review ? (scenario.reviewDelay || 0) : (recovery ? scenario.recoveryDelay || 0 : scenario.workerDelays?.[task] ?? scenario.workerDelay ?? 0));
});
`, { mode: 0o700 });
  fs.writeFileSync(path.join(bin, 'make'), `#!${process.execPath}
${support}
emit({ type: 'check', command: process.argv[2] });
if (scenario.failChecks) { console.error('Reproduced meter alignment failure'); process.exitCode = 1; }
else console.log('coordinator-checked-' + process.argv[2]);
`, { mode: 0o700 });
  git('add', '.'); git('commit', '-m', 'fixture baseline'); git('branch', 'codex/terra');
  const configFile = path.join(root, 'config.json');
  const cfg = { repo, stateDir: state, branch: 'codex/terra', minutes: 1, maxRunsPerDay: 10, maxReportedTokens: 1000,
    video: false, codex, javaHome: root, gradleHome: root, allowedTasks: ['M01', 'M02', 'M03'],
    allowedPaths: { M01: ['src/'], M02: ['src/'], M03: ['src/'] }, ...config };
  atomic(configFile, cfg);
  return { root, repo, state, cfg, git,
    call: command => spawnSync(process.execPath, [runner, command, configFile], {
      encoding: 'utf8', timeout: 30000, env: { ...process.env, PATH: bin + path.delimiter + process.env.PATH },
    }),
    events: () => fs.existsSync(eventsFile) ? fs.readFileSync(eventsFile, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : [],
    readState: () => JSON.parse(fs.readFileSync(path.join(state, 'state.json'), 'utf8')),
    setConfig: patch => atomic(configFile, { ...cfg, ...patch }),
    setScenario: value => atomic(scenarioFile, value),
    close: () => fs.rmSync(root, { recursive: true, force: true }),
  };
}
const completed = f => queue(f.git('show', 'codex/terra:TASKS.md')).filter(t => t.state === 'DONE').map(t => t.id);
const modelStarts = (f, kind) => f.events().filter(e => e.type === 'start' && (!kind || e.kind === kind));

const findingConfig = { maxFindingRecoveries: 3, findingRecoveryTokens: 150000, findingRecoveryMinutes: 20,
  maxRecoveryRetries: 0, model: 'gpt-6-astra', reasoningEffort: 'xhigh' };
const recoveryFindings = [
  { id: 'accepted-review', kind: 'code', title: 'Accepted Review state missing', files: ['src/M01.txt'],
    acceptance: 'Capture accepted Review with enabled Play and Undo and assert it in a regression.' },
  { id: 'ready-export', kind: 'code', title: 'Ready Export result missing', files: ['src/export-test.txt'],
    acceptance: 'Capture the ready Export result with enabled Publish and Reveal and verify its bounds.' },
];
function failedFindingFixture(config = {}, scenario = {}) {
  const f = fixture({ config: { ...findingConfig, ...config }, scenario: { recoveryFindings, ...scenario } });
  const result = f.call('advance');
  assert.notEqual(result.status, 0);
  assert.equal(f.readState().active.phase, 'RECOVERY_PENDING', result.stderr);
  return f;
}
test('terminal review findings become ordered bounded subtasks; preserve code and integrate only after whole-parent review', () => {
  const f = failedFindingFixture();
  try {
    const original = f.readState().active;
    assert.equal(original.recoverySubtasks.length, 2);
    assert.equal(modelStarts(f, 'worker').length, 3);
    assert.equal(f.call('retry').status, 1, 'generic retry must not reset recovery');
    const one = f.call('advance'); assert.equal(one.status, 0, one.stderr);
    const pending = f.readState().active;
    assert.equal(pending.worktree, original.worktree);
    assert.deepEqual(pending.recoverySubtasks.map(s => s.status), ['DONE', 'TODO']);
    assert.deepEqual(completed(f), ['F01']);
    const first = modelStarts(f, 'worker').at(-1);
    assert.ok(!first.args.includes('resume'), 'fresh focused session');
    assert.equal(first.args[first.args.indexOf('-m') + 1], 'gpt-6-astra');
    assert.match(first.prompt, /Focused recovery subtask M01\/R1/);
    assert.match(first.prompt, /Accepted Review state missing/);
    assert.match(first.prompt, /regression/);
    assert.match(fs.readFileSync(pending.recoveryPatch, 'utf8'), /implemented M01/);
    assert.equal(pending.recoverySubtasks[0].modelTokens, 24);
    const two = f.call('advance'); assert.equal(two.status, 0, two.stderr);
    const last = f.readState().last;
    assert.equal(f.readState().active, undefined);
    assert.deepEqual(last.recoverySubtasks.map(s => s.status), ['DONE', 'DONE']);
    assert.equal(f.git('show', 'codex/terra:src/M01.txt'), 'fixed accepted-review');
    assert.equal(f.git('show', 'codex/terra:src/export-test.txt'), 'fixed ready-export');
    assert.deepEqual(completed(f), ['F01', 'M01']);
    assert.deepEqual(f.readState().runs.slice(-2).map(r => r.findingRecovery), ['M01/R1', 'M01/R2']);
    assert.equal(modelStarts(f, 'worker').length, 5);
    assert.ok(modelStarts(f, 'review').every(r => r.args.includes('--ephemeral')));
    assert.equal(modelStarts(f, 'review').at(-1).candidate, last.reviewedTree);
    assert.ok(f.readState().batchHistory.every(b => b.modelTokens > 0), 'prior usage retained');
  } finally { f.close(); }
});
test('unchanged or independently unresolved recovery stops; the next wake defers without another worker', () => {
  for (const scenario of [{ recoveryNoChange: true }, { recoveryUnresolved: true }]) {
    const f = failedFindingFixture({}, scenario);
    try {
      const r = f.call('advance'); assert.notEqual(r.status, 0);
      assert.match(r.stderr, /no code progress|did not resolve/);
      const failed = f.readState().active;
      assert.equal(failed.findingRecoveryStopped, true);
      assert.equal(failed.recoverySubtasks[0].status, 'BLOCKED');
      const count = modelStarts(f).length;
      assert.equal(f.call('advance').status, 0);
      assert.equal(modelStarts(f).length, count);
      assert.equal(queue(f.git('show', 'codex/terra:TASKS.md')).find(t => t.id === 'M01').state, 'BLOCKED');
      assert.ok(fs.existsSync(failed.worktree));
    } finally { f.close(); }
  }
});
test('recovery cannot broaden file ownership, overwrite a changed checkpoint or bypass current authorization', () => {
  for (const mode of ['scope', 'checkpoint', 'allowlist']) {
    const f = failedFindingFixture({}, mode === 'scope' ? { recoveryExtraPath: 'src/baseline.txt' } : {});
    try {
      const ctx = f.readState().active;
      if (mode === 'checkpoint') fs.writeFileSync(path.join(ctx.worktree, 'src/M01.txt'), 'external change\n');
      if (mode === 'allowlist') f.setConfig({ ...findingConfig, allowedTasks: ['M02', 'M03'] });
      const r = f.call('advance'); assert.notEqual(r.status, 0);
      assert.match(r.stderr, /outside finding scope|preserved checkpoint|no longer authorized/);
      assert.deepEqual(completed(f), ['F01']);
      assert.ok(fs.existsSync(ctx.worktree));
      if (mode !== 'scope') assert.equal(modelStarts(f, 'worker').length, 3);
    } finally { f.close(); }
  }
});
test('finding token/time budgets and pause are enforced without resetting parent history', () => {
  for (const mode of ['tokens', 'time', 'pause', 'daily']) {
    const f = failedFindingFixture(mode === 'tokens' ? { findingRecoveryTokens: 12 } : {},
      mode === 'time' ? { recoveryDelay: 150 } : {});
    try {
      if (mode === 'time') f.setConfig({ ...findingConfig, findingRecoveryMinutes: 0.001 });
      if (mode === 'pause') f.call('pause');
      if (mode === 'daily') f.setConfig({ ...findingConfig, maxRunsPerDay: 1 });
      const r = f.call('advance');
      if (mode === 'pause') { assert.equal(r.status, 0); assert.equal(modelStarts(f, 'worker').length, 3); }
      else {
        assert.notEqual(r.status, 0); assert.match(r.stderr, /budget exhausted|deadline exhausted|Daily run/);
        if (mode === 'daily') assert.equal(modelStarts(f, 'worker').length, 3);
        else {
          assert.equal(f.readState().active.findingRecoveryStopped, true);
          const count = modelStarts(f).length; f.call('advance'); assert.equal(modelStarts(f).length, count);
        }
      }
      assert.deepEqual(completed(f), ['F01']);
    } finally { f.close(); }
  }
});
test('human, environment, scope and too many findings never create recovery subtasks', () => {
  for (const kind of ['human', 'environment', 'scope', 'too-many']) {
    const findings = kind === 'too-many' ? Array.from({ length: 4 }, (_, i) => ({ ...recoveryFindings[0], id: `issue-${i}` }))
      : [{ ...recoveryFindings[0], kind, files: [] }];
    const f = fixture({ config: findingConfig, scenario: { recoveryFindings: findings } });
    try {
      assert.notEqual(f.call('advance').status, 0);
      assert.equal(f.readState().active.recoverySubtasks, undefined);
      const count = modelStarts(f).length;
      f.call('advance'); assert.equal(modelStarts(f).length, count);
    } finally { f.close(); }
  }
});
test('a crashed in-flight recovery is preserved and deferred rather than resuming or resetting it', () => {
  const f = failedFindingFixture();
  try {
    const s = f.readState();
    s.active.recoverySubtasks[0].status = 'RUNNING';
    s.active.phase = 'READY_FOR_VALIDATION'; s.active.resumeStage = 'validate';
    s.active.interruptionReason = 'deadline';
    atomic(path.join(f.state, 'state.json'), s);
    const count = modelStarts(f).length;
    const r = f.call('advance'); assert.equal(r.status, 0, r.stderr);
    assert.equal(modelStarts(f).length, count);
    assert.equal(f.readState().lastDeferred.recoverySubtasks[0].status, 'RUNNING');
    assert.equal(f.readState().active, undefined);
    assert.ok(fs.existsSync(s.active.worktree));
  } finally { f.close(); }
});
test('final review usage crossing the finding cap preserves work; exact cap can integrate without another model call', () => {
  for (const cap of [23, 24]) {
    const f = failedFindingFixture({ findingRecoveryTokens: cap }, { recoveryFindings: [recoveryFindings[0]] });
    try {
      const r = f.call('advance');
      if (cap === 23) {
        assert.notEqual(r.status, 0); assert.match(r.stderr, /budget exhausted/);
        assert.equal(f.readState().active.recoverySubtasks[0].modelTokens, 24);
        assert.equal(f.readState().active.findingRecoveryStopped, true);
        assert.deepEqual(completed(f), ['F01']);
      } else {
        assert.equal(r.status, 0, r.stderr);
        assert.deepEqual(completed(f), ['F01', 'M01']);
        assert.equal(f.readState().last.recoverySubtasks[0].modelTokens, 24);
      }
    } finally { f.close(); }
  }
});
test('finding configuration cannot exceed authorized subtask count time or tokens', () => {
  for (const config of [{ maxFindingRecoveries: 4 }, { findingRecoveryMinutes: 20.01 }, { findingRecoveryTokens: 150001 }]) {
    const f = fixture({ config: { ...findingConfig, ...config } });
    try {
      const r = f.call('advance'); assert.notEqual(r.status, 0); assert.match(r.stderr, /Invalid finding recovery limits/);
      assert.equal(modelStarts(f).length, 0);
      assert.equal(fs.existsSync(path.join(f.state, 'state.json')), false);
    } finally { f.close(); }
  }
});

test('budget interruption resumes validation without replaying implementation or erasing usage', () => {
  const f = fixture({ config: { maxReportedTokens: 12, maxRecoveryRetries: 0 } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    const retained = f.readState().active;
    assert.equal(retained.resumeStage, 'validate');
    assert.deepEqual(modelStarts(f).map(e => e.kind), ['worker']);
    const patch = f.git('-C', retained.worktree, 'diff', '--binary', retained.base);
    f.setConfig({ maxReportedTokens: 1000, maxRecoveryRetries: 0 });
    assert.equal(f.call('advance').status, 0);
    assert.deepEqual(modelStarts(f).map(e => e.kind), ['worker', 'review']);
    assert.equal(f.readState().batchHistory[0].modelTokens, 12);
    assert.equal(f.readState().runs.at(-1).continuation, 1);
    assert.ok(patch.includes('implemented M01'));
    assert.deepEqual(completed(f), ['F01', 'M01']);
  } finally { f.close(); }
});

test('continuation reapplies preserved code onto a newer coordinator base and reviews that base', () => {
  const f = fixture({ config: { maxReportedTokens: 12 } });
  try {
    f.call('run');
    const old = f.git('rev-parse', 'codex/terra');
    fs.writeFileSync(path.join(f.repo, 'README.md'), 'New coordinator instructions\n');
    f.git('add', 'README.md'); f.git('commit', '-m', 'coordinator update');
    const next = f.git('rev-parse', 'HEAD'); f.git('update-ref', 'refs/heads/codex/terra', next, old);
    f.setConfig({ maxReportedTokens: 1000 });
    const result = f.call('continue'); assert.equal(result.status, 0, result.stderr);
    assert.equal(modelStarts(f, 'worker').length, 1);
    assert.equal(modelStarts(f, 'review')[0].base, next);
    assert.equal(f.git('show', 'codex/terra:README.md'), 'New coordinator instructions');
  } finally { f.close(); }
});

test('configured Astra extra-high reaches implementation retry and review while Sol repair stays high', () => {
  const f = fixture({ config: { model: 'gpt-6-astra', reasoningEffort: 'xhigh' },
    scenario: { failReviewTasks: ['M01'] } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    const workers = modelStarts(f, 'worker');
    assert.deepEqual(workers.map(e => e.args[e.args.indexOf('-m') + 1]),
      ['gpt-6-astra', 'gpt-6-astra', 'gpt-5.6-sol']);
    for (const event of [...workers.slice(0, 2), ...modelStarts(f, 'review')]) {
      assert.equal(event.args[event.args.indexOf('-m') + 1], 'gpt-6-astra');
      assert.ok(event.args.includes('model_reasoning_effort="xhigh"'));
    }
    assert.ok(workers[2].args.includes('model_reasoning_effort="high"'));
    assert.equal(workers.length, 3, 'model selection does not add repair attempts');
  } finally { f.close(); }
});

test('advance defers an exhausted failure and admits unrelated work on its next wake', () => {
  const f = fixture({ markdown: independent, scenario: { failReviewTasks: ['M01'] } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    const workers = modelStarts(f, 'worker');
    assert.deepEqual(workers.map(e => e.args[e.args.indexOf('-m') + 1]), ['gpt-5.6-terra', 'gpt-5.6-terra', 'gpt-5.6-sol']);
    const retained = f.readState().active;
    assert.equal(retained.terminalFailure, true);
    assert.equal(f.call('advance').status, 0);
    assert.ok(fs.existsSync(retained.worktree));
    assert.equal(queue(f.git('show', 'codex/terra:TASKS.md')).find(t => t.id === 'M01').state, 'BLOCKED');
    f.setScenario({});
    assert.equal(f.call('advance').status, 0);
    assert.deepEqual(completed(f), ['F01', 'M02']);
  } finally { f.close(); }
});

test('integration conflicts preserve the original without spending model repairs or leaking a worktree', () => {
  const f = fixture({ config: { maxReportedTokens: 12 } });
  try {
    f.call('run'); const original = f.readState().active;
    fs.writeFileSync(path.join(f.repo, 'src/M01.txt'), 'conflicting coordinator content\n');
    f.git('add', 'src/M01.txt'); f.git('commit', '-m', 'overlapping integration change');
    f.git('update-ref', 'refs/heads/codex/terra', f.git('rev-parse', 'HEAD'), original.base);
    f.setConfig({ maxReportedTokens: 1000 });
    const result = f.call('advance'); assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Integration conflict/);
    assert.equal(modelStarts(f).length, 1, 'no Terra or Sol repair runs against the stale base');
    const retained = f.readState().active;
    assert.equal(retained.integrationConflict, true);
    assert.equal(retained.worktree, original.worktree);
    assert.equal(fs.readFileSync(path.join(original.worktree, 'src/M01.txt'), 'utf8'), 'implemented M01\n');
    assert.equal(retained.rebaseFailure.removed, true);
    assert.equal(fs.existsSync(retained.rebaseFailure.worktree), false);
    assert.equal(f.call('advance').status, 0, 'conflict is deferred rather than recycled');
    assert.match(f.git('show', 'codex/terra:TASKS.md'), /M01.*BLOCKED.*Integration conflict/);
    assert.ok(fs.existsSync(original.worktree));
  } finally { f.close(); }
});

test('live checkout fast-forwards files and index; unrelated tracked edits are preserved', () => {
  for (const dirty of [false, true]) {
    const f = fixture();
    try {
      const branch = f.git('branch', '--show-current'); f.setConfig({ liveBranch: branch });
      if (dirty) fs.writeFileSync(path.join(f.repo, 'src/baseline.txt'), 'user edit\n');
      const result = f.call('run'); assert.equal(result.status, 0, result.stderr);
      if (dirty) {
        assert.equal(fs.readFileSync(path.join(f.repo, 'src/baseline.txt'), 'utf8'), 'user edit\n');
        assert.match(f.readState().last.liveSyncError, /tracked edits/);
      } else {
        assert.equal(f.git('rev-parse', 'HEAD'), f.git('rev-parse', 'codex/terra'));
        assert.equal(fs.readFileSync(path.join(f.repo, 'src/M01.txt'), 'utf8'), 'implemented M01\n');
        assert.equal(f.git('status', '--porcelain'), '');
      }
    } finally { f.close(); }
  }
});

test('continuation cap preserves the candidate instead of recycling budget failures forever', () => {
  const f = fixture({ config: { maxReportedTokens: 12, maxContinuations: 0 } });
  try {
    f.call('run'); const retained = f.readState().active;
    assert.notEqual(f.call('continue').status, 0);
    assert.equal(modelStarts(f).length, 1);
    assert.equal(f.call('advance').status, 0);
    assert.ok(fs.existsSync(retained.worktree));
    assert.equal(f.readState().active, undefined);
  } finally { f.close(); }
});

test('explicit pause prevents admission and resumed validation does not consume a code retry', () => {
  const f = fixture({ config: { maxReportedTokens: 12, maxRecoveryRetries: 0 } });
  try {
    f.call('run');
    const state = f.readState(); state.active.feedback = 'Paused; work preserved'; state.active.interruptionReason = 'pause';
    atomic(path.join(f.state, 'state.json'), state);
    f.call('pause');
    assert.equal(f.call('advance').status, 0);
    assert.equal(modelStarts(f).length, 1);
    f.call('resume'); f.setConfig({ maxReportedTokens: 1000, maxRecoveryRetries: 0 });
    const result = f.call('advance'); assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(modelStarts(f).map(e => e.kind), ['worker', 'review']);
    assert.equal(f.readState().runs.at(-1).recovery, undefined);
  } finally { f.close(); }
});

test('worker blocker text cannot impersonate a coordinator interruption', () => {
  const f = fixture({ config: { maxRecoveryRetries: 0 }, scenario: {
    workerStatus: 'BLOCKED', workerBlocker: 'worker-0 exceeded batch deadline',
  } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    assert.equal(f.readState().active.interruptionReason, undefined);
    assert.notEqual(f.call('continue').status, 0);
    assert.equal(modelStarts(f, 'worker').length, 1);
    assert.equal(f.call('advance').status, 0);
    assert.equal(f.readState().lastDeferred.task, 'M01');
  } finally { f.close(); }
});

test('one admission immediately executes dependency-ready tasks up to the batch bound', () => {
  for (const [config, expected] of [[{}, ['M01']], [{ maxTasksPerBatch: 2 }, ['M01', 'M02']]]) {
    const f = fixture({ config });
    try {
      const original = f.git('rev-parse', 'HEAD');
      const result = f.call('run'); assert.equal(result.status, 0, result.stderr);
      assert.deepEqual(modelStarts(f, 'worker').map(e => e.task), expected);
      assert.deepEqual(completed(f), ['F01', ...expected]);
      assert.equal(f.readState().runs.length, expected.length, 'daily task admission remains bounded within batches');
      assert.equal(f.git('rev-parse', 'HEAD'), original);
      assert.equal(f.git('status', '--porcelain'), '');
      for (const task of expected) assert.equal(f.git('show', `codex/terra:src/${task}.txt`), `implemented ${task}`);
      if (expected.length === 2) assert.ok(modelStarts(f, 'worker')[1].files.includes('M01.txt'), 'dependent implementation sees the integrated predecessor');
    } finally { f.close(); }
  }
});

test('the model budget is shared across tasks rather than reset at each integration', () => {
  const f = fixture({ config: { maxTasksPerBatch: 3, maxReportedTokens: 25 } });
  try {
    f.call('run');
    assert.deepEqual(completed(f), ['F01', 'M01']);
    assert.deepEqual(modelStarts(f, 'review').map(e => e.task), ['M01']);
    assert.equal(modelStarts(f).some(e => e.task === 'M03'), false);
    assert.ok(f.readState().modelTokens >= 24);
    assert.ok(f.readState().modelTokens <= 36, 'at most the in-flight call can cross the model budget');
  } finally { f.close(); }
});

test('the batch deadline stops a later worker even though it has used less than a full task window', () => {
  const f = fixture({ config: { maxTasksPerBatch: 3, minutes: 0.07 },
    scenario: { workerDelays: { M01: 1700, M02: 2600 } } });
  try {
    const result = f.call('run'); assert.notEqual(result.status, 0);
    assert.deepEqual(completed(f), ['F01', 'M01']);
    assert.deepEqual(modelStarts(f, 'worker').map(e => e.task), ['M01', 'M02']);
    assert.deepEqual(modelStarts(f, 'review').map(e => e.task), ['M01']);
    assert.match(result.stderr, /deadline|timed out|budget/i);
    assert.ok(fs.existsSync(f.readState().active.worktree));
  } finally { f.close(); }
});

test('repairs reuse worker context while reviews stay fresh and consume bounded check evidence', () => {
  const f = fixture({ scenario: { failFirstReviews: 1 } });
  try {
    const result = f.call('run'); assert.equal(result.status, 0, result.stderr);
    const workers = modelStarts(f, 'worker'), reviews = modelStarts(f, 'review');
    assert.equal(workers.length, 2); assert.equal(reviews.length, 2);
    assert.equal(workers[0].args.includes('--ephemeral'), false);
    assert.equal(workers[1].args.includes('resume'), true);
    assert.equal(workers[1].args.includes(workers[0].session), true);
    assert.match(workers[1].prompt, /6\/8 accent defect|compound-meter accent/);
    for (const review of reviews) {
      assert.equal(review.args.includes('--ephemeral'), true);
      assert.equal(review.args.includes('resume'), false);
      assert.match(review.prompt, /(?:never|do not|must not)[^\n]{0,180}transcript/i);
      assert.match(review.prompt, /evidence\/attempt-\d+\.json/);
      const dir = path.dirname(review.resultFile);
      const evidence = fs.readdirSync(path.join(dir, 'evidence')).filter(name => /^attempt-\d+\.json$/.test(name));
      assert.ok(evidence.length > 0);
      for (const name of evidence) {
        const bytes = fs.readFileSync(path.join(dir, 'evidence', name), 'utf8');
        assert.ok(bytes.length < 50000, 'review evidence stays bounded');
        assert.match(bytes, /test-\d+\.log/);
        assert.match(bytes, /build-\d+\.log/);
      }
      assert.equal(fs.existsSync(path.join(dir, path.basename(review.resultFile, '.json') + '.log')), false);
      assert.ok(fs.existsSync(path.join(dir, 'transcripts', path.basename(review.resultFile, '.json') + '.log')));
    }
  } finally { f.close(); }
});

test('concrete reviewer and coordinator failures override pending worker-validation text', () => {
  for (const [scenario, expected] of [
    [{ failReviewTasks: ['M01'] }, /6\/8 accent defect|compound-meter accent/],
    [{ failChecks: true }, /test.*failed|meter alignment failure/],
  ]) {
    const f = fixture({ scenario: { ...scenario, workerBlocker: 'PENDING_COORDINATOR: make test and build' } });
    try {
      const original = f.git('rev-parse', 'codex/terra');
      assert.notEqual(f.call('run').status, 0);
      assert.equal(f.git('rev-parse', 'codex/terra'), original);
      assert.match(f.readState().active.blocker, expected);
      assert.doesNotMatch(f.readState().active.blocker, /PENDING_COORDINATOR/);
      assert.ok(fs.existsSync(f.readState().active.worktree));
    } finally { f.close(); }
  }
});

test('retry preserves the existing candidate and every prior result and transcript', () => {
  const f = fixture({ scenario: { failReviewTasks: ['M01'] } });
  try {
    assert.notEqual(f.call('run').status, 0);
    const retained = f.readState().active;
    const evidence = [];
    for (const dir of [retained.dir, path.join(retained.dir, 'transcripts'), path.join(retained.dir, 'evidence')]) {
      for (const name of fs.readdirSync(dir)) {
        const file = path.join(dir, name);
        if (fs.statSync(file).isFile()) evidence.push([file, fs.readFileSync(file)]);
      }
    }
    fs.writeFileSync(path.join(retained.worktree, 'src/M01.txt'), 'retained edit awaiting repair\n');
    const count = modelStarts(f, 'worker').length;
    const latestSession = modelStarts(f, 'worker').at(-1).session;
    f.setScenario({});
    const result = f.call('retry'); assert.equal(result.status, 0, result.stderr);
    const resumed = modelStarts(f, 'worker')[count];
    assert.equal(resumed.cwd, retained.worktree);
    assert.equal(resumed.contents['M01.txt'], 'retained edit awaiting repair\n');
    assert.equal(resumed.args.includes('resume'), true);
    assert.equal(resumed.args.includes(latestSession), true);
    for (const [file, bytes] of evidence) assert.deepEqual(fs.readFileSync(file), bytes, `prior evidence changed: ${file}`);
    assert.deepEqual(completed(f), ['F01', 'M01']);
    assert.equal(fs.existsSync(retained.worktree), false, 'successful candidate is cleaned only after integration');
    assert.equal(f.readState().last.dir, retained.dir);
  } finally { f.close(); }
});

const parallelConfig = { maxTasksPerBatch: 2, maxParallelWorkers: 2, parallelGroups: [['M01', 'M02']],
  parallelPaths: { M01: ['src/M01.txt'], M02: ['src/M02.txt'] } };
test('blocked first owner cannot erase a later peer deadline checkpoint', () => {
  const f = fixture({ markdown: independent,
    config: { ...parallelConfig, minutes: 0.035, maxRecoveryRetries: 0 },
    scenario: { workerStatuses: { M01: 'BLOCKED' }, workerBlocker: 'Missing input', workerDelays: { M02: 3000 } } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    assert.equal(f.readState().active.result.status, 'BLOCKED');
    assert.equal(f.readState().parallel[0].interruptionReason, 'deadline');
    assert.equal(f.call('advance').status, 0);
    assert.equal(f.readState().active.task, 'M02');
    f.setConfig({ minutes: 1, maxRecoveryRetries: 0 }); f.setScenario({});
    const result = f.call('advance'); assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(completed(f), ['F01', 'M02']);
    assert.equal(f.readState().runs.at(-1).continuation, 1);
  } finally { f.close(); }
});
test('mixed interrupted and blocked parallel owners advance independently without deadlock', () => {
  const f = fixture({ markdown: independent,
    config: { ...parallelConfig, maxReportedTokens: 24, maxRecoveryRetries: 0 },
    scenario: { workerStatuses: { M02: 'BLOCKED' }, workerBlocker: 'Required implementation input missing' } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    const original = f.readState();
    assert.equal(original.active.interruptionReason, 'budget');
    assert.equal(original.parallel[0].result.status, 'BLOCKED');
    f.setConfig({ maxReportedTokens: 1000, maxRecoveryRetries: 0 });
    const result = f.call('advance'); assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(completed(f), ['F01', 'M01']);
    assert.equal(f.readState().active.task, 'M02');
    assert.equal(modelStarts(f, 'worker').length, 2, 'neither preserved implementation is replayed');
    assert.equal(f.call('advance').status, 0);
    assert.equal(f.readState().active, undefined);
    assert.ok(fs.existsSync(original.parallel[0].worktree));
    assert.match(f.git('show', 'codex/terra:TASKS.md'), /M02.*BLOCKED.*Required implementation input missing/);
  } finally { f.close(); }
});
test('advance defers a failed parallel owner then validates its ready peer without reimplementation', () => {
  const f = fixture({ markdown: independent, config: parallelConfig, scenario: { failReviewTasks: ['M01'] } });
  try {
    assert.notEqual(f.call('advance').status, 0);
    const failed = f.readState().active;
    assert.equal(failed.terminalFailure, true);
    assert.equal(f.call('advance').status, 0);
    assert.equal(f.readState().active.task, 'M02');
    assert.ok(fs.existsSync(failed.worktree));
    f.setScenario({});
    const result = f.call('advance'); assert.equal(result.status, 0, result.stderr);
    assert.equal(modelStarts(f, 'worker').filter(e => e.task === 'M02').length, 1);
    assert.deepEqual(completed(f), ['F01', 'M02']);
  } finally { f.close(); }
});
test('explicit independent workers overlap, then integrate serially with fresh checks on the combined tree', () => {
  const f = fixture({ markdown: independent, config: parallelConfig, scenario: { workerDelay: 500 } });
  try {
    const original = f.git('rev-parse', 'codex/terra');
    const result = f.call('run'); assert.equal(result.status, 0, result.stderr);
    const workers = modelStarts(f, 'worker'), ends = f.events().filter(e => e.type === 'end' && e.kind === 'worker');
    assert.equal(workers.length, 2);
    assert.ok(Math.max(...workers.map(e => e.at)) < Math.min(...ends.map(e => e.at)), 'both workers start before either finishes');
    assert.deepEqual(new Set(workers.map(e => e.base)), new Set([original]));
    assert.deepEqual(completed(f), ['F01', 'M01', 'M02']);
    for (const task of ['M01', 'M02']) assert.equal(f.git('show', `codex/terra:src/${task}.txt`), `implemented ${task}`);
    const finalReview = modelStarts(f, 'review').at(-1);
    assert.notEqual(finalReview.base, original, 'second integration is reviewed against the advanced base');
    assert.ok(finalReview.files.includes('M01.txt') && finalReview.files.includes('M02.txt'));
    for (const command of ['test', 'build']) assert.ok(f.events().some(e => e.type === 'check' && e.command === command && e.cwd === finalReview.cwd && e.files.includes('M01.txt') && e.files.includes('M02.txt')), 'combined candidate passes ' + command);
    assert.equal(f.readState().active, undefined);
    assert.ok(!f.readState().parallel?.length);
    assert.equal(f.git('status', '--porcelain'), '');
  } finally { f.close(); }
});

test('parallel groups never bypass dependencies or run colliding or undeclared owners together', () => {
  for (const [markdown, patch] of [
    [chain, {}],
    [independent, { parallelGroups: [] }],
    [independent, { parallelPaths: { M01: ['src/'], M02: ['src/M02.txt'] } }],
    [independent, { parallelPaths: { M01: ['src/M01.txt'] } }],
  ]) {
    const f = fixture({ markdown, config: { ...parallelConfig, ...patch }, scenario: { workerDelay: 100 } });
    try {
      const result = f.call('run');
      const starts = modelStarts(f, 'worker'), ends = f.events().filter(e => e.type === 'end' && e.kind === 'worker');
      if (result.status !== 0) {
        assert.equal(starts.length, 0, result.stderr);
        assert.match(result.stderr, /parallel|ownership|overlap|path/i);
      } else {
        assert.equal(starts.length, 2);
        assert.ok(starts[1].at >= ends[0].at, 'unsafe pair falls back to serial execution');
        if (markdown === chain) assert.ok(starts[1].files.includes('M01.txt'));
        assert.deepEqual(completed(f), ['F01', 'M01', 'M02']);
      }
    } finally { f.close(); }
  }
});

test('a parallel worker cannot use the broad default scope to modify its peer ownership', () => {
  const f = fixture({ markdown: independent, config: parallelConfig,
    scenario: { extraWorkerPaths: { M01: ['src/M02.txt'] } } });
  try {
    const original = f.git('rev-parse', 'codex/terra');
    const result = f.call('run'); assert.notEqual(result.status, 0);
    assert.match(result.stderr, /outside M01 path scope.*M02\.txt/);
    assert.equal(f.git('rev-parse', 'codex/terra'), original);
    assert.equal(modelStarts(f, 'review').length, 0);
    const state = f.readState();
    for (const candidate of [state.active, ...state.parallel]) assert.ok(fs.existsSync(candidate.worktree));
  } finally { f.close(); }
});

test('retry of a retained parallel wave reconciles the second candidate after the first integrates', () => {
  const f = fixture({ markdown: independent, config: parallelConfig, scenario: { failReviewTasks: ['M01'] } });
  try {
    assert.notEqual(f.call('run').status, 0);
    const state = f.readState(), retained = [state.active, ...state.parallel];
    assert.deepEqual(retained.map(c => c.task), ['M01', 'M02']);
    const priorResults = retained.flatMap(c => fs.readdirSync(c.dir).filter(name => /^(worker|review)-\d+\.json$/.test(name)).map(name => {
      const file = path.join(c.dir, name); return [file, fs.readFileSync(file)];
    }));
    f.setScenario({});
    const result = f.call('retry'); assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(completed(f), ['F01', 'M01', 'M02']);
    assert.equal(modelStarts(f, 'worker').filter(e => e.task === 'M02').length, 1, 'ready peer implementation is reused during recovery');
    for (const task of ['M01', 'M02']) assert.equal(f.git('show', `codex/terra:src/${task}.txt`), `implemented ${task}`);
    for (const [file, bytes] of priorResults) assert.deepEqual(fs.readFileSync(file), bytes);
    const finalReview = modelStarts(f, 'review').at(-1);
    assert.ok(finalReview.files.includes('M01.txt') && finalReview.files.includes('M02.txt'));
    assert.equal(f.readState().active, undefined);
    assert.ok(!f.readState().parallel?.length);
  } finally { f.close(); }
});

test('invalid throughput bounds fail before launching any worker', () => {
  for (const config of [{ maxTasksPerBatch: 0 }, { maxTasksPerBatch: 1.5 }, { maxParallelWorkers: 3 }, { maxParallelWorkers: 0 }]) {
    const f = fixture({ config });
    try {
      const result = f.call('run'); assert.notEqual(result.status, 0);
      assert.match(result.stderr, /numeric limit|batch|parallel/i);
      assert.deepEqual(modelStarts(f), []);
      assert.equal(fs.existsSync(path.join(f.state, 'runs')), false);
    } finally { f.close(); }
  }
});

for (const [name, task, initial, changed, expected] of [
  ['withdrawn task authorization', 'M01', {}, { allowedTasks: ['M02', 'M03'] }, /allowlist|authorized/],
  ['narrowed file ownership', 'M01', {}, { allowedPaths: { M01: ['src/allowed-only/'], M02: ['src/'], M03: ['src/'] } }, /path|ownership|scope/],
  ['withdrawn video authorization', 'V04', {
    markdown: '| F01 | Baseline | — | DONE | |\n| V04 | Video fixture | F01 | TODO | |',
    config: { video: true, allowedTasks: ['V04'], allowedPaths: { V04: ['src/'] } },
  }, { video: false }, /video|authorized/],
]) {
  test(`retry respects ${name} before running a model or integrating preserved work`, () => {
    const f = fixture({ ...initial, scenario: { failReviewTasks: [task] } });
    try {
      const original = f.git('rev-parse', 'codex/terra');
      assert.notEqual(f.call('run').status, 0);
      const retained = f.readState().active;
      assert.equal(retained.task, task);
      const candidateFile = path.join(retained.worktree, 'src', `${task}.txt`);
      const candidateBytes = fs.readFileSync(candidateFile);
      const calls = modelStarts(f).length;
      const results = fs.readdirSync(retained.dir).filter(name => /^(worker|review)-\d+\.json$/.test(name)).map(name => {
        const file = path.join(retained.dir, name); return [file, fs.readFileSync(file)];
      });
      f.setConfig(changed);
      f.setScenario({});
      const result = f.call('retry'); assert.notEqual(result.status, 0);
      assert.match(result.stderr, expected);
      assert.equal(modelStarts(f).length, calls, 'authorization is checked before resuming the worker');
      assert.equal(f.git('rev-parse', 'codex/terra'), original);
      assert.deepEqual(fs.readFileSync(candidateFile), candidateBytes);
      for (const [file, bytes] of results) assert.deepEqual(fs.readFileSync(file), bytes);
      assert.equal(f.readState().active.blocker, retained.blocker, 'refused retry preserves the actual implementation blocker');
      assert.equal(f.git('status', '--porcelain'), '');
    } finally { f.close(); }
  });
}

test('retained parallel ownership cannot override a narrowed primary scope after its group is removed', () => {
  const f = fixture({ markdown: independent,
    config: { ...parallelConfig, parallelPaths: { ...parallelConfig.parallelPaths, M01: ['src/M01.txt', 'src/extra.txt'] } },
    scenario: { failReviewTasks: ['M01'], extraWorkerPaths: { M01: ['src/extra.txt'] } } });
  try {
    const original = f.git('rev-parse', 'codex/terra');
    assert.notEqual(f.call('run').status, 0);
    const state = f.readState(), retained = [state.active, ...state.parallel];
    assert.deepEqual(retained.map(c => c.task), ['M01', 'M02']);
    const extra = path.join(state.active.worktree, 'src/extra.txt'), candidate = fs.readFileSync(extra);
    const evidence = retained.flatMap(c => [c.dir, path.join(c.dir, 'transcripts'), path.join(c.dir, 'evidence')].flatMap(dir =>
      fs.readdirSync(dir).filter(name => fs.statSync(path.join(dir, name)).isFile()).map(name => {
        const file = path.join(dir, name); return [file, fs.readFileSync(file)];
      })));
    const calls = modelStarts(f).length;
    f.setConfig({ parallelGroups: [], allowedPaths: { ...f.cfg.allowedPaths, M01: ['src/M01.txt'] } });
    f.setScenario({});
    const result = f.call('retry'); assert.notEqual(result.status, 0);
    assert.match(result.stderr, /path|ownership|scope/);
    assert.equal(modelStarts(f).length, calls);
    assert.equal(f.git('rev-parse', 'codex/terra'), original);
    assert.deepEqual(fs.readFileSync(extra), candidate);
    for (const [file, bytes] of evidence) assert.deepEqual(fs.readFileSync(file), bytes);
    assert.equal(f.readState().active.blocker, state.active.blocker);
  } finally { f.close(); }
});
