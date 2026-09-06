import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { queue, select, mark, effectiveStatus, checkResult, lock, atomic, permitted } from './terra-runner.mjs';
const md = '| F01 | Baseline | — | TODO | |\n| M01 | Music | F01 | TODO | |\n| A01 | Runner | F01 | TODO | |\n| V01 | Video | F01; selected | TODO | |';
test('dependencies, bootstrap priority and selected video', () => {
  assert.equal(select(queue(md), false).id, 'F01');
  const after = mark(md, 'F01', 'DONE', 'abc');
  assert.equal(select(queue(after), false).id, 'A01');
  const waiting = mark(mark(after, 'A01', 'BLOCKED', 'fault'), 'M01', 'WAITING_USER', 'listen');
  assert.equal(select(queue(waiting), false), undefined);
  assert.equal(select(queue(waiting), true).id, 'V01');
});
test('in-progress tasks block duplicate selection', () => {
  assert.throws(() => select(queue(mark(md, 'F01', 'RUNNING', '')), true), /in-progress/);
});
test('malformed queue cannot silently skip dependencies', () => {
  assert.throws(() => queue(''), /Invalid/);
  assert.throws(() => queue(md.replace('F01; selected', 'Q99')), /Unknown/);
  assert.throws(() => queue(md + '\n| F01 | Duplicate | — | TODO | |'), /Invalid/);
});
test('human gates cannot auto-complete', () => {
  for (const id of ['U07', 'Q01', 'Q02', 'Q03', 'V01', 'V02', 'V03', 'V07']) assert.equal(effectiveStatus(id, 'DONE'), 'WAITING_USER');
  assert.equal(effectiveStatus('M04', 'DONE'), 'DONE');
  assert.equal(effectiveStatus('Q01', 'BLOCKED'), 'BLOCKED');
});
test('status updates preserve other rows and contain multiline output', () => {
  const updated = mark(md, 'F01', 'DONE', 'one|two\nthree');
  assert.equal(queue(updated)[0].state, 'DONE');
  assert.equal(updated.split('\n').length, md.split('\n').length);
  assert.ok(updated.endsWith(md.split('\n').slice(1).join('\n')));
});
test('reject false task/base/commit/status result reports', () => {
  const r = { task: 'F01', base: 'abc', commit: 'abc', candidate: 'UNCOMMITTED', status: 'DONE', summary: 'good', blocker: '', tests: ['test'], artifacts: [] };
  assert.equal(checkResult(r, 'F01', 'abc'), r);
  for (const patch of [{ base: 'old' }, { task: 'M01' }, { commit: 'fabricated' }, { status: 'PASS' }, { tests: 'pass' }]) assert.throws(() => checkResult({ ...r, ...patch }, 'F01', 'abc'));
  assert.throws(() => checkResult(r, 'F01', 'abc', true));
});
test('lock excludes concurrent runs, retains evidence, permits explicit clean restart', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'terra-lock-'));
  try {
    const p = path.join(dir, 'lock'), release = lock(p);
    assert.throws(() => lock(p), /EEXIST/);
    assert.equal(JSON.parse(fs.readFileSync(path.join(p, 'owner.json'))).pid, process.pid);
    release(); lock(p)();
    atomic(path.join(dir, 'state.json'), { n: 1 });
    atomic(path.join(dir, 'state.json'), { n: 2 });
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir, 'state.json'))), { n: 2 });
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

// Exercise the real coordinator process with fake model/build executables in a throwaway Git repo.
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const runner = fileURLToPath(new URL('./terra-runner.mjs', import.meta.url));
function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'terra-e2e-'));
  const repo = path.join(root, 'repo'), state = path.join(root, 'state'), bin = path.join(root, 'bin');
  for (const d of [repo, state, bin, path.join(repo, 'tools')]) fs.mkdirSync(d, { recursive: true });
  const git = (...a) => execFileSync('git', ['-C', repo, ...a], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git('init'); git('config', 'user.name', 'Test'); git('config', 'user.email', 'test@example.invalid');
  fs.writeFileSync(path.join(repo, 'TASKS.md'), md);
  fs.writeFileSync(path.join(repo, 'tools/terra-runner.test.mjs'), "import test from 'node:test'; test('fixture', () => {});\n");
  fs.writeFileSync(path.join(bin, 'make'), '#!/bin/sh\nexit 0\n', { mode: 0o700 });
  const fake = path.join(bin, 'codex');
  fs.writeFileSync(fake, `#!/usr/bin/env node
const fs = require('fs'); let input = '';
process.stdin.on('data', b => input += b);
process.stdin.on('end', () => {
  const task = input.match(/Assigned task: (\\w+)/)[1], base = input.match(/Base commit: (\\w+)/)[1];
  const review = input.includes('Mode: Fresh independent REVIEW');
  if (!review) fs.writeFileSync('probe.txt', 'actual new file');
  const result = { task, base, commit: base, candidate: input.match(/Candidate tree: ([A-Za-z0-9_]+)/)[1], status: review ? (fs.existsSync('review-fails') ? 'FAIL' : 'PASS') : 'DONE', summary: 'Fixture only', blocker: review && process.env.FAKE_REVIEW_FAIL ? 'reproduced defect' : '', tests: ['fixture'], artifacts: [] };
  fs.writeFileSync(process.argv[process.argv.indexOf('-o') + 1], JSON.stringify(result));
  console.log(JSON.stringify({type:'turn.completed', usage:{input_tokens:10, output_tokens:2}}));
});`, { mode: 0o700 });
  git('add', '.'); git('commit', '-m', 'fixture'); git('branch', 'codex/terra');
  const config = path.join(root, 'config.json');
  const cfg = { allowedPaths: { F01: ['probe.txt'] }, allowedTasks: ['F01', 'M01', 'A01', 'V01'], repo, stateDir: state, branch: 'codex/terra', minutes: 1, maxRunsPerDay: 2, maxReportedTokens: 1000, video: true, codex: fake, javaHome: root, gradleHome: root };
  atomic(config, cfg);
  const call = (command, extra = {}) => spawnSync(process.execPath, [runner, command, config], { encoding: 'utf8', env: { ...process.env, PATH: bin + ':' + process.env.PATH, ...extra } });
  return { root, repo, state, config, cfg, git, call, close: () => fs.rmSync(root, { recursive: true, force: true }) };
}
test('real coordinator validates, reviews new files, commits and advances only integration branch', () => {
  const f = fixture(); try {
    const head = f.git('rev-parse', 'HEAD');
    const r = f.call('run'); assert.equal(r.status, 0, r.stderr);
    assert.equal(f.git('rev-parse', 'HEAD'), head);
    assert.equal(f.git('status', '--porcelain'), '');
    assert.equal(f.git('show', 'codex/terra:probe.txt'), 'actual new file');
    assert.equal(queue(f.git('show', 'codex/terra:TASKS.md'))[0].state, 'DONE');
    assert.equal(JSON.parse(fs.readFileSync(path.join(f.state, 'state.json'))).last.tokens, 24);
  } finally { f.close(); }
});
test('failed review stops after two retries, preserves work, and explicit defer allows independent work', () => {
  const f = fixture(); try {
    fs.writeFileSync(path.join(f.repo, 'review-fails'), 'fixture'); f.git('add', '.'); f.git('commit', '-m', 'review failure fixture'); f.git('branch', '-f', 'codex/terra', 'HEAD');
    const head = f.git('rev-parse', 'codex/terra');
    const r = f.call('run'); assert.notEqual(r.status, 0);
    assert.equal(f.git('rev-parse', 'codex/terra'), head);
    const s = JSON.parse(fs.readFileSync(path.join(f.state, 'state.json')));
    assert.ok(fs.existsSync(path.join(s.active.worktree, 'probe.txt')));
    assert.ok(fs.existsSync(path.join(s.active.dir, 'review-2.json')));
    assert.equal(fs.existsSync(path.join(s.active.dir, 'review-3.json')), false);
    assert.notEqual(f.call('run').status, 0);
    assert.equal(f.call('defer').status, 0);
    assert.equal(queue(f.git('show', 'codex/terra:TASKS.md'))[0].state, 'BLOCKED');
    assert.ok(fs.existsSync(path.join(s.active.worktree, 'probe.txt')));
  } finally { f.close(); }
});
test('daily admission, token budget and pause prevent unintended work', () => {
  const f = fixture(); try {
    atomic(f.config, { ...f.cfg, maxRunsPerDay: 0 });
    assert.match(f.call('run').stderr, /Daily run/);
    atomic(f.config, { ...f.cfg, maxReportedTokens: 1 });
    assert.match(f.call('run').stderr, /budget|resolution/);
    assert.equal(f.git('show', 'codex/terra:TASKS.md'), md);
    assert.equal(f.call('pause').status, 0);
    assert.equal(f.call('resume').status, 0);
  } finally { f.close(); }
});
test('refuse main or a checked-out integration branch and invalid limits', () => {
  const f = fixture(); try {
    atomic(f.config, { ...f.cfg, branch: 'main' });
    assert.match(f.call('run').stderr, /codex/);
    atomic(f.config, { ...f.cfg, minutes: 'forever' });
    assert.match(f.call('run').stderr, /numeric/);
    atomic(f.config, f.cfg); f.git('checkout', 'codex/terra');
    assert.match(f.call('run').stderr, /checked out/);
  } finally { f.close(); }
});
test('recovery refuses live owners and preserves an interrupted task after dead-lock recovery', () => {
  const f = fixture(); try {
    fs.mkdirSync(path.join(f.state, 'lock'));
    atomic(path.join(f.state, 'lock/owner.json'), { pid: process.pid, host: os.hostname() });
    assert.match(f.call('recover').stderr, /still alive/);
    atomic(path.join(f.state, 'lock/owner.json'), { pid: 2147483647, host: os.hostname() });
    atomic(path.join(f.state, 'state.json'), { runs: [], active: { task: 'F01', dir: 'preserved' } });
    assert.equal(f.call('recover').status, 0);
    assert.match(f.call('run').stderr, /Interrupted run retained/);
    assert.equal(JSON.parse(fs.readFileSync(path.join(f.state, 'state.json'))).active.dir, 'preserved');
  } finally { f.close(); }
});

test('task paths are exact or directory bounded', () => {
  assert.equal(permitted('src/main/file.kt', ['src/main/']), true);
  assert.equal(permitted('src/main-other/file.kt', ['src/main/']), false);
  assert.equal(permitted('README.md.bak', ['README.md']), false);
});
