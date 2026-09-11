#!/usr/bin/env node
// Developer-only coordinator. No npm dependencies or application runtime hooks.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const read = p => fs.readFileSync(p, 'utf8');
const json = p => JSON.parse(read(p));
const taskStates = new Set(['TODO', 'DONE', 'RUNNING', 'REVIEW', 'WAITING_USER', 'BLOCKED', 'OPTIONAL']);
const resultFields = new Set(['task', 'base', 'commit', 'candidate', 'summary', 'blocker', 'status', 'tests', 'artifacts']);
const interrupted = ctx => ['budget', 'deadline', 'pause'].includes(ctx?.interruptionReason);
const continuable = ctx => interrupted(ctx) || ctx?.phase === 'READY_FOR_VALIDATION';
const interruption = (message, reason) => Object.assign(new Error(message), { interruptionReason: reason });
export function atomic(p, value) {
  const tmp = `${p}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
  fs.renameSync(tmp, p);
}
export function queue(md) {
  const rows = md.split('\n').filter(l => /^\| [FMUAQV]\d{2}(?:[a-z])? \|/.test(l)).map(l => {
    const c = l.split('|').map(s => s.trim());
    if (c.length < 6 || !c[2] || !taskStates.has(c[4])) throw Error('Invalid task queue');
    return { id: c[1], title: c[2], deps: c[3].match(/[FMUAQV]\d{2}[a-z]?/g) || [], state: c[4] };
  });
  if (!rows.length || new Set(rows.map(t => t.id)).size !== rows.length) throw Error('Invalid task queue');
  const ids = new Set(rows.map(t => t.id));
  if (rows.some(t => t.deps.some(d => !ids.has(d)))) throw Error('Unknown dependency');
  return rows;
}
export function select(rows, video, allowed = rows.map(t => t.id)) {
  if (rows.some(t => ['RUNNING', 'REVIEW'].includes(t.state))) throw Error('Unresolved in-progress task');
  const done = new Set(rows.filter(t => t.state === 'DONE').map(t => t.id));
  const ready = rows.filter(t => t.state === 'TODO' && allowed.includes(t.id) && (video || !t.id.startsWith('V')) && t.deps.every(d => done.has(d)));
  return ready.find(t => t.id === 'A01') || ready[0];
}
export function mark(md, id, state, result) {
  const line = md.split('\n').find(l => l.startsWith(`| ${id} |`));
  if (!line) throw Error(`Missing task ${id}`);
  const c = line.split('|');
  c[4] = ` ${state} `;
  c[5] = ` ${result.replace(/[|\r\n]/g, ' ').slice(0, 550)} `;
  return md.replace(line, c.join('|'));
}
export function effectiveStatus(id, reported) {
  if (reported === 'READY_FOR_VALIDATION') reported = 'DONE';
  // These contracts require a real user decision; code/tests cannot satisfy them.
  return reported === 'DONE' && ['U07', 'Q01', 'Q02', 'Q03', 'V01', 'V02', 'V03', 'V07'].includes(id) ? 'WAITING_USER' : reported;
}
export function permitted(file, paths) {
  return paths.some(p => p.endsWith('/') ? file.startsWith(p) : file === p);
}
export function validateAllowedPaths(paths) {
  if (!Array.isArray(paths) || !paths.length) throw Error('Missing task-specific allowed paths');
  for (const allowed of paths) {
    if (typeof allowed !== 'string') throw Error('Invalid task-specific allowed path');
    const parts = allowed.split('/');
    const finalEmpty = allowed.endsWith('/') && parts.at(-1) === '';
    if (!allowed || allowed.startsWith('/') || allowed.includes('\0') ||
        parts.some((part, index) => !part && !(finalEmpty && index === parts.length - 1) || part === '.' || part === '..') ||
        allowed === '.git' || allowed.startsWith('.git/')) throw Error('Invalid task-specific allowed path');
  }
  if (new Set(paths).size !== paths.length) throw Error('Duplicate task-specific allowed path');
}
export function reportedUsage(usage = {}) {
  const input = Number.isFinite(usage.input_tokens) ? usage.input_tokens : 0;
  const cached = Math.min(input, Math.max(0, Number.isFinite(usage.cached_input_tokens) ? usage.cached_input_tokens : 0));
  const output = Math.max(0, Number.isFinite(usage.output_tokens) ? usage.output_tokens : 0);
  const reasoning = Math.max(0, Number.isFinite(usage.reasoning_output_tokens) ? usage.reasoning_output_tokens : 0);
  return { input, cached, nonCachedInput: input - cached, output, reasoning, modelTokens: input - cached + output + reasoning };
}
export function checkResult(r, task, base, review = false, candidate = 'UNCOMMITTED', structured = false) {
  const allowed = review ? ['PASS', 'FAIL'] : ['READY_FOR_VALIDATION', 'WAITING_USER', 'BLOCKED'];
  const fields = new Set([...resultFields, ...(review && structured ? ['findings', 'resolvedFindingIds'] : [])]);
  if (!r || typeof r !== 'object' || Array.isArray(r) || Object.keys(r).length !== fields.size ||
      Object.keys(r).some(key => !fields.has(key)) || r.task !== task || r.base !== base || !allowed.includes(r.status) ||
      !Array.isArray(r.tests) || !r.tests.every(test => typeof test === 'string') ||
      !Array.isArray(r.artifacts) || !r.artifacts.every(artifact => typeof artifact === 'string') || typeof r.summary !== 'string' ||
      !r.summary.trim() || typeof r.blocker !== 'string' || r.commit !== base || r.candidate !== candidate) throw Error('Invalid/stale agent result');
  return r;
}
const findingFields = ['id', 'kind', 'title', 'files', 'acceptance'];
export function checkFindings(review, paths, recovery = []) {
  if (!Array.isArray(review.findings) || review.findings.length > 8 ||
      (review.status === 'PASS') !== (review.findings.length === 0) ||
      !Array.isArray(review.resolvedFindingIds) ||
      new Set(review.resolvedFindingIds).size !== review.resolvedFindingIds.length ||
      review.resolvedFindingIds.some(id => !recovery.some(s => s.finding.id === id))) throw Error('Invalid review findings/resolutions');
  const ids = new Set();
  for (const f of review.findings) {
    if (!f || Object.keys(f).length !== findingFields.length || Object.keys(f).some(k => !findingFields.includes(k)) ||
        typeof f.id !== 'string' || !/^[a-zA-Z0-9_-]{1,40}$/.test(f.id) || ids.has(f.id) || review.resolvedFindingIds.includes(f.id) ||
        !['code', 'human', 'environment', 'scope'].includes(f.kind) ||
        ![f.title, f.acceptance].every(s => typeof s === 'string' && s.trim() && s.length <= 2000) ||
        !Array.isArray(f.files) || f.files.length > 12 || (f.kind === 'code' && !f.files.length)) throw Error('Invalid concrete review finding');
    if (f.files.length) validateAllowedPaths(f.files);
    if (f.files.some(file => file.endsWith('/') || !permitted(file, paths) ||
        ['AGENTS.md', 'PLAN.md', 'TASKS.md'].includes(file) || file.startsWith('docs/pictures/'))) throw Error('Finding exceeds authorized file scope');
    ids.add(f.id);
  }
  // Every original finding remains explicitly open or independently resolved.
  if (recovery.some(s => !ids.has(s.finding.id) && !review.resolvedFindingIds.includes(s.finding.id)))
    throw Error('Review omitted an original recovery finding');
  return review;
}
export function findingRecoverable(ctx, cfg) {
  return cfg.maxFindingRecoveries > 0 && !ctx?.findingRecoveryStopped &&
    !ctx?.recoverySubtasks?.some(s => ['RUNNING', 'BLOCKED'].includes(s.status)) &&
    ctx?.recoverySubtasks?.some(s => s.status === 'TODO');
}
const git = (cwd, ...args) => execFileSync('git', ['-C', cwd, '-c', 'core.hooksPath=/dev/null', ...args], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 }).trim();
export function lock(dir) {
  fs.mkdirSync(dir); // Atomic exclusion; stale locks never expire on a timer.
  atomic(path.join(dir, 'owner.json'), { pid: process.pid, host: os.hostname(), started: new Date().toISOString() });
  return () => fs.rmSync(dir, { recursive: true });
}
const schema = statuses => ({ type: 'object', additionalProperties: false,
  properties: Object.fromEntries(['task', 'base', 'commit', 'candidate', 'summary', 'blocker'].map(k => [k, { type: 'string' }]).concat([
    ['status', { type: 'string', enum: statuses }],
    ['tests', { type: 'array', items: { type: 'string' } }],
    ['artifacts', { type: 'array', items: { type: 'string' } }]])),
  required: ['task', 'base', 'commit', 'candidate', 'summary', 'blocker', 'status', 'tests', 'artifacts'] });
export function reviewSchema() {
  const s = schema(['PASS', 'FAIL']);
  s.properties.findings = { type: 'array', items: { type: 'object', additionalProperties: false,
    properties: { id: { type: 'string' }, kind: { type: 'string', enum: ['code', 'human', 'environment', 'scope'] },
      title: { type: 'string' }, files: { type: 'array', items: { type: 'string' } }, acceptance: { type: 'string' } }, required: findingFields } };
  s.properties.resolvedFindingIds = { type: 'array', items: { type: 'string' } };
  s.required.push('findings', 'resolvedFindingIds');
  return s;
}

export function pathsOverlap(a, b) {
  return a.some(x => b.some(y => x === y || (x.endsWith('/') && y.startsWith(x)) || (y.endsWith('/') && x.startsWith(y))));
}
export function selectWave(rows, cfg, slots = cfg.maxTasksPerBatch ?? 1) {
  const first = select(rows, cfg.video, cfg.allowedTasks);
  if (!first || slots < 1) return [];
  const wave = [first];
  if ((cfg.maxParallelWorkers ?? 1) < 2 || slots < 2) return wave;
  const group = (cfg.parallelGroups ?? []).find(ids => ids.includes(first.id));
  if (!group) return wave;
  const done = new Set(rows.filter(t => t.state === 'DONE').map(t => t.id));
  for (const t of rows) {
    if (t.id === first.id || !group.includes(t.id) || !cfg.allowedTasks.includes(t.id) ||
        t.state !== 'TODO' || (!cfg.video && t.id.startsWith('V')) || !t.deps.every(d => done.has(d))) continue;
    const paths = cfg.parallelPaths?.[t.id], firstPaths = cfg.parallelPaths?.[first.id];
    // Parallel execution is opt-in, with narrower, provably disjoint ownership.
    if (!paths || !firstPaths || pathsOverlap(firstPaths, paths)) continue;
    wave.push(t); break;
  }
  return wave;
}
export function boundedTail(file, limit = 4000) {
  const fd = fs.openSync(file, 'r');
  try {
    const size = fs.fstatSync(fd).size, bytes = Math.min(size, limit);
    const b = Buffer.alloc(bytes); fs.readSync(fd, b, 0, bytes, size - bytes);
    return b.toString('utf8');
  } finally { fs.closeSync(fd); }
}
export function failureReason(result, feedback) {
  if (feedback?.trim()) return feedback;
  if (result?.status === 'BLOCKED' && result.blocker?.trim()) return result.blocker;
  return 'Implementation or independent review did not pass';
}
function validatePolicy(cfg, rows) {
  if (!/^codex\/[a-zA-Z0-9][a-zA-Z0-9/_-]*$/.test(cfg.branch)) throw Error('Integration requires a codex/ branch');
  for (const key of ['minutes', 'maxRunsPerDay', 'maxReportedTokens'])
    if (!Number.isFinite(cfg[key]) || cfg[key] < 0) throw Error(`Invalid numeric limit: ${key}`);
  for (const [key, max] of [['maxTasksPerBatch', 10], ['maxParallelWorkers', 2], ['maxRecoveryRetries', 2]])
    if (!Number.isInteger(cfg[key]) || cfg[key] < (key === 'maxRecoveryRetries' ? 0 : 1) || cfg[key] > max) throw Error(`Invalid numeric limit: ${key}`);
  if (!Number.isInteger(cfg.maxRunsPerDay)) throw Error('Invalid numeric limit: maxRunsPerDay');
  if (!Array.isArray(cfg.allowedTasks) || !cfg.allowedTasks.length || new Set(cfg.allowedTasks).size !== cfg.allowedTasks.length ||
      cfg.allowedTasks.some(id => typeof id !== 'string' || !rows.some(t => t.id === id))) throw Error('Invalid task allowlist');
  if (typeof cfg.video !== 'boolean') throw Error('Explicit video policy required');
  if (!cfg.allowedPaths || typeof cfg.allowedPaths !== 'object' || Array.isArray(cfg.allowedPaths) ||
      Object.keys(cfg.allowedPaths).some(id => !rows.some(t => t.id === id))) throw Error('Invalid task path policy');
  for (const id of cfg.allowedTasks) {
    if (!Object.hasOwn(cfg.allowedPaths, id)) throw Error(`Missing task-specific allowed paths: ${id}`);
  }
  for (const paths of Object.values(cfg.allowedPaths)) validateAllowedPaths(paths);
  if (!Array.isArray(cfg.parallelGroups) || cfg.parallelGroups.some(ids => !Array.isArray(ids) || ids.length !== 2 ||
      new Set(ids).size !== 2 || ids.some(id => !cfg.allowedTasks.includes(id)))) throw Error('Invalid parallel groups');
  const grouped = cfg.parallelGroups.flat();
  if (new Set(grouped).size !== grouped.length) throw Error('Task belongs to multiple parallel groups');
  for (const id of grouped) {
    validateAllowedPaths(cfg.parallelPaths[id]);
    if (cfg.parallelPaths[id].some(p => !permitted(p, cfg.allowedPaths[id]))) throw Error(`Parallel ownership exceeds allowed paths: ${id}`);
  }
  for (const checks of Object.values(cfg.taskChecks ?? {})) for (const check of checks) {
    if (!check || !Array.isArray(check.argv) || check.argv.some(v => typeof v !== 'string') || check.argv.length < 2 ||
        !['./gradlew', process.execPath].includes(check.argv[0])) throw Error('Invalid configured task check');
  }
}

async function main() {
  let [command = 'status', configFile = path.join(os.homedir(), '.codex/melotrail-terra/config.json')] = process.argv.slice(2);
  const automatic = command === 'advance';
  const cfg = { maxTasksPerBatch: 1, maxParallelWorkers: 1, maxRecoveryRetries: 1, maxContinuations: 3,
    maxFindingRecoveries: 0, findingRecoveryMinutes: 20, findingRecoveryTokens: 150000,
    parallelGroups: [], parallelPaths: {}, ...json(configFile) };
  if (!Number.isInteger(cfg.maxContinuations) || cfg.maxContinuations < 0 || cfg.maxContinuations > 5) throw Error('Invalid continuation limit');
  if (!Number.isInteger(cfg.maxFindingRecoveries) || cfg.maxFindingRecoveries < 0 || cfg.maxFindingRecoveries > 3 ||
      !Number.isFinite(cfg.findingRecoveryMinutes) || cfg.findingRecoveryMinutes <= 0 || cfg.findingRecoveryMinutes > 20 ||
      !Number.isInteger(cfg.findingRecoveryTokens) || cfg.findingRecoveryTokens <= 0 || cfg.findingRecoveryTokens > 150000)
    throw Error('Invalid finding recovery limits');
  const root = cfg.stateDir;
  for (const key of ['repo', 'stateDir', 'codex', 'javaHome', 'gradleHome'])
    if (typeof cfg[key] !== 'string' || !path.isAbsolute(cfg[key])) throw Error(`Absolute path required: ${key}`);
  if (root === cfg.repo || root.startsWith(cfg.repo + path.sep)) throw Error('Keep control state outside the source checkout');
  if (!/^codex\/[a-zA-Z0-9][a-zA-Z0-9/_-]*$/.test(cfg.branch)) throw Error('Integration requires a codex/ branch');
  const verifyBranch = () => {
    if (git(cfg.repo, 'worktree', 'list', '--porcelain').split('\n').includes(`branch refs/heads/${cfg.branch}`))
      throw Error('Integration branch is checked out; use an un-checked-out integration branch');
  };
  fs.mkdirSync(root, { recursive: true });
  const lockDir = path.join(root, 'lock'), stateFile = path.join(root, 'state.json'), pauseFile = path.join(root, 'paused');
  const loadState = () => fs.existsSync(stateFile) ? json(stateFile) : { runs: [], paused: false };
  const entries = s => [s.active, ...(s.parallel ?? [])].filter(Boolean);
  const checkDeadChildren = s => {
    for (const pid of [...(s.childPids ?? []), ...(s.childPid ? [s.childPid] : [])]) {
      if (!Number.isInteger(pid) || pid <= 0) throw Error('Invalid child PID; inspect before recovery');
      try { process.kill(-pid, 0); throw Error('Child process group still alive'); }
      catch (e) { if (e.code !== 'ESRCH') throw e; }
    }
  };
  if (command === 'advance') {
    const s = loadState();
    if (fs.existsSync(lockDir) || s.paused || (fs.existsSync(pauseFile) && read(pauseFile).trim())) {
      console.log('Already running or paused; no admission'); return;
    }
    command = !s.active ? 'run' : s.active.recoverySubtasks ? (findingRecoverable(s.active, cfg) ? 'recover-finding' : 'defer') : continuable(s.active) && (s.active.continuationCount ?? 0) < cfg.maxContinuations
      ? 'continue' : !continuable(s.active) && !s.active.terminalFailure && !s.active.integrationConflict && (s.active.recoveryCount ?? 0) < cfg.maxRecoveryRetries ? 'retry' : 'defer';
  }
  if (command === 'pause' || command === 'resume') {
    if (command === 'resume' && fs.existsSync(lockDir)) throw Error('Wait for active process to stop before resuming');
    fs.writeFileSync(pauseFile, command === 'pause' ? 'paused\n' : '');
    if (command === 'resume') { const s = loadState(); s.paused = false; atomic(stateFile, s); }
    console.log(command); return;
  }
  if (command === 'recover') {
    const owner = json(path.join(lockDir, 'owner.json'));
    if (owner.host !== os.hostname() || !Number.isInteger(owner.pid) || owner.pid <= 0) throw Error('Invalid or foreign lock owner');
    try { process.kill(owner.pid, 0); throw Error('Owner still alive; do not recover'); }
    catch (e) { if (e.code !== 'ESRCH') throw e; }
    checkDeadChildren(loadState());
    fs.renameSync(lockDir, `${lockDir}.recovered.${Date.now()}`);
    console.log('Recovered dead lock. Interrupted worktrees are preserved; inspect state before retry.'); return;
  }
  if (command === 'defer') {
    const release = lock(lockDir);
    try {
      verifyBranch(); const s = loadState(); checkDeadChildren(s);
      if (!s.active) throw Error('No interrupted task to defer');
      const base = git(cfg.repo, 'rev-parse', cfg.branch);
      if (git(cfg.repo, 'merge-base', base, s.active.base) !== s.active.base) throw Error('Integration changed incompatibly; reconcile manually');
      const dest = path.join(root, 'deferred', String(Date.now()));
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      git(cfg.repo, 'worktree', 'add', '--detach', dest, base);
      const p = path.join(dest, 'TASKS.md');
      fs.writeFileSync(p, mark(read(p), s.active.task, 'BLOCKED', `${s.active.feedback || s.active.blocker || s.active.result?.blocker || s.error || 'Interrupted run'}; preserved ${s.active.dir}`));
      git(dest, 'add', 'TASKS.md'); git(dest, 'commit', '-m', `queue: defer ${s.active.task} and preserve unfinished work`);
      git(cfg.repo, 'update-ref', `refs/heads/${cfg.branch}`, git(dest, 'rev-parse', 'HEAD'), base);
      s.lastDeferred = s.active;
      const remaining = s.parallel ?? [];
      s.active = remaining[0]; s.parallel = remaining.slice(1);
      if (!s.active) delete s.active;
      atomic(stateFile, s);
      git(cfg.repo, 'worktree', 'remove', dest);
      console.log(JSON.stringify(s.lastDeferred));
    } finally { release(); }
    return;
  }
  let head = git(cfg.repo, 'rev-parse', cfg.branch), md = git(cfg.repo, 'show', `${head}:TASKS.md`);
  validatePolicy(cfg, queue(md));
  if (command === 'status' || command === 'dry-run') {
    const s = loadState();
    console.log(JSON.stringify({ branch: cfg.branch, head, next: select(queue(md), cfg.video, cfg.allowedTasks) ?? null,
      wave: selectWave(queue(md), cfg).map(t => t.id), locked: fs.existsSync(lockDir), paused: s.paused || (fs.existsSync(pauseFile) && !!read(pauseFile).trim()),
      recoverySubtasks: (s.active?.recoverySubtasks ?? []).map(u => ({ id: u.id, title: u.finding.title, status: u.status,
        modelTokens: u.modelTokens, maxTokens: u.maxTokens, maxMinutes: u.maxMinutes, blocker: u.blocker })),
      limits: { minutes: cfg.minutes, tasks: cfg.maxTasksPerBatch, workers: cfg.maxParallelWorkers, tokens: cfg.maxReportedTokens, dailyAdmissions: cfg.maxRunsPerDay }, state: s }, null, 2)); return;
  }
  if (!['run', 'retry', 'continue', 'recover-finding'].includes(command)) throw Error('Use status, dry-run, advance, run, continue, retry, recover-finding, pause, resume, recover, or defer');
  const release = lock(lockDir), state = loadState();
  const retainedEntries = () => automatic || command === 'continue' ? entries(state).slice(0, 1) : entries(state);
  let batchAdmitted = false;
  const deadline = Date.now() + cfg.minutes * 60000, children = new Set();
  let recoveryUnit, recoveryDeadline;
  const persist = () => atomic(stateFile, state);
  const stop = () => { for (const p of children) { try { process.kill(-p.pid, 'SIGTERM'); } catch {} } };
  const interrupt = () => { state.paused = true; persist(); stop(); };
  process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
  const guard = (model = false) => {
    if (state.paused || (fs.existsSync(pauseFile) && read(pauseFile).trim())) throw interruption('Paused; work preserved', 'pause');
    if (Date.now() >= deadline) throw interruption('Run deadline exhausted; work preserved', 'deadline');
    if (model && state.modelTokens >= cfg.maxReportedTokens) throw interruption('Batch model-call budget exhausted; work preserved', 'budget');
    if (recoveryUnit && (Date.now() >= recoveryDeadline ||
        (model ? recoveryUnit.modelTokens >= recoveryUnit.maxTokens : recoveryUnit.modelTokens > recoveryUnit.maxTokens)))
      throw Error(`Recovery ${recoveryUnit.id} budget exhausted; candidate preserved`);
  };
  const assertHead = () => {
    verifyBranch();
    if (git(cfg.repo, 'rev-parse', cfg.branch) !== head) throw Error('Integration base changed outside this batch; preserve work');
  };
  function account(event, ctx, worker) {
    if (worker && event.type === 'thread.started' && typeof event.thread_id === 'string') { ctx.workerSession = event.thread_id; persist(); }
    if (event.type === 'turn.completed' && event.usage) {
      const u = reportedUsage(event.usage);
      if (recoveryUnit) recoveryUnit.modelTokens += u.modelTokens;
      for (const key of ['input', 'cached', 'nonCachedInput', 'output', 'reasoning']) state.usage[key] += u[key];
      state.modelTokens += u.modelTokens; state.cachedInputTokens += u.cached; persist();
    }
  }
  async function run(ctx, exe, args, label, input = '', model = false, worker = false) {
    guard(model);
    const file = path.join(ctx.dir, model ? 'transcripts' : '', `${label}.log`);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const fd = fs.openSync(file, 'wx', 0o600);
    const env = Object.fromEntries(['HOME', 'USER', 'LOGNAME', 'TMPDIR', 'LANG', 'LC_ALL', 'TERM', 'CODEX_HOME'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
    Object.assign(env, { JAVA_HOME: cfg.javaHome, GRADLE_USER_HOME: cfg.gradleHome, PATH: `${cfg.javaHome}/bin:${process.env.PATH}` });
    let p, timer, poll, timedOut = false, buffer = '';
    const started = Date.now();
    try {
      p = spawn(exe, args, { cwd: ctx.worktree, env, detached: true, stdio: ['pipe', model ? 'pipe' : fd, fd] });
      children.add(p); state.childPids = [...children].map(c => c.pid).filter(Boolean); persist();
      if (model) p.stdout.on('data', chunk => {
        fs.writeSync(fd, chunk); buffer += chunk.toString('utf8');
        let end;
        while ((end = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, end); buffer = buffer.slice(end + 1);
          // Read protocol metadata only. Transcript content is never fed back to agents.
          if (!line.includes('"turn.completed"') && !line.includes('"thread.started"')) continue;
          try { account(JSON.parse(line), ctx, worker); } catch (e) { if (!(e instanceof SyntaxError)) throw e; }
        }
      });
      p.stdin.on('error', () => {}); p.stdin.end(input);
      timer = setTimeout(() => { timedOut = true; try { process.kill(-p.pid, 'SIGTERM'); } catch {}
        setTimeout(() => { try { process.kill(-p.pid, 'SIGKILL'); } catch {} }, 5000).unref();
      }, Math.max(1, Math.min(deadline, recoveryDeadline ?? Infinity) - Date.now()));
      poll = setInterval(() => { if (fs.existsSync(pauseFile) && read(pauseFile).trim()) interrupt(); }, 1000);
      const code = await new Promise((resolve, reject) => { p.on('error', reject); p.on('close', resolve); });
      if (model && buffer.trim()) { try { account(JSON.parse(buffer), ctx, worker); } catch {} }
      if (timedOut && recoveryUnit && Date.now() >= recoveryDeadline) throw Error(`Recovery ${recoveryUnit.id} deadline exhausted; candidate preserved`);
      if (timedOut) throw interruption(`${label} exceeded batch deadline`, 'deadline');
      guard();
      if (!model) ctx.checks.push({ label, argv: [exe, ...args], exitCode: code, durationMs: Date.now() - started, log: file, tail: boundedTail(file) });
      if (code !== 0) throw Error(`${label} failed (${code}); ${file}\n${model ? 'Inspect structured agent result; do not read transcript logs.' : boundedTail(file)}`);
      return file;
    } finally {
      clearTimeout(timer); clearInterval(poll);
      if (p?.pid) { try { process.kill(-p.pid, 'SIGKILL'); } catch {} }
      children.delete(p); state.childPids = [...children].map(c => c.pid).filter(Boolean); persist(); fs.closeSync(fd);
    }
  }
  function evidence(ctx) {
    const p = path.join(ctx.dir, 'evidence', `attempt-${ctx.attempt}.json`);
    fs.mkdirSync(path.dirname(p), { recursive: true });
    atomic(p, { task: ctx.task, base: ctx.base, candidate: ctx.tree ?? 'UNCOMMITTED',
      recoverySubtasks: ctx.recoverySubtasks ?? [], candidateDiff: ctx.recoveryPatch,
      checks: ctx.checks.map(c => ({ ...c, tail: c.tail.slice(-2000) })), feedback: (ctx.feedback ?? '').slice(-8000) });
    return p;
  }
  async function agent(ctx, review = false, repair = false) {
    const label = `${review ? 'review' : 'worker'}-${ctx.attempt}`;
    const schemaFile = path.join(ctx.dir, `${label}.schema.json`), resultFile = path.join(ctx.dir, `${label}.json`);
    const structured = review && cfg.maxFindingRecoveries > 0;
    atomic(schemaFile, structured ? reviewSchema() : schema(review ? ['PASS', 'FAIL'] : ['READY_FOR_VALIDATION', 'WAITING_USER', 'BLOCKED']));
    const candidate = review ? ctx.tree : 'UNCOMMITTED', packet = evidence(ctx);
    const taskMd = read(path.join(ctx.worktree, 'TASKS.md'));
    const contract = ctx.originalTaskContract ??= taskMd.match(new RegExp(`^### ${ctx.task} [\\s\\S]*?(?=^### |^## |$(?![\\s\\S]))`, 'm'))?.[0] ?? '';
    const resume = !review && !repair && !recoveryUnit && ctx.workerSession;
    const prompt = `Assigned task: ${ctx.task}: ${ctx.title}. Base commit: ${ctx.base}. Candidate tree: ${candidate}.
Mode: ${review ? 'Fresh independent REVIEW. Inspect the exact candidate diff and acceptance requirements; return PASS only with no actionable defect or missing required evidence. Do not edit files or rerun checks.' : 'IMPLEMENT only this assigned task. Leave changes uncommitted. Return READY_FOR_VALIDATION, WAITING_USER for an actual human decision, or BLOCKED for a real implementation blocker.'}
${resume ? 'Continue your existing implementation context. Inspect only the new feedback and changed files; reuse the documentation and code already read. Re-read authority documents only if changed.' : 'Read AGENTS.md, PLAN.md, README.md, TASKS.md, docs/ARCHITECTURE.md and the task owner references before editing or reviewing. Read required documents once; use targeted source searches.'}
Task contract:
${contract}
${recoveryUnit ? `Focused recovery subtask ${recoveryUnit.id}: ${JSON.stringify(recoveryUnit.finding)}. Preserve the existing parent implementation. Change only these exact files: ${JSON.stringify(recoveryUnit.finding.files)}. The current binary git diff is ${ctx.recoveryPatch}. Add a regression proving the stated acceptance condition. Do not restart or expand the parent task. Remaining subtask budget: ${Math.max(0, recoveryUnit.maxTokens - recoveryUnit.modelTokens)} reported tokens, ${Math.max(0, Math.ceil((recoveryDeadline - Date.now()) / 60000))} minutes.` : ''}
${structured ? `Return findings (at most eight) and resolvedFindingIds. Each finding needs a stable id, kind (code/human/environment/scope), concrete title, exact repository-relative files (include necessary regression-test files, no directories), and an independently verifiable acceptance condition. Never label missing user approval, rights, budget, credentials or machine capabilities as a code repair. PASS requires no findings; FAIL requires at least one. For every recovery finding in the evidence packet, keep its original id open or list it in resolvedFindingIds only after checking its acceptance condition against this exact tree and completed check evidence. A partial repair may resolve a finding while the parent still FAILs. Do not omit or rename unresolved original findings. No recovery findings means resolvedFindingIds must be empty.` : ''}
Allowed changed paths: ${JSON.stringify(ctx.paths)}. The coordinator owns TASKS status and integration. Preserve source MIDI, accepted candidates, exports, authority and unrelated files. No paid generation or public push/upload. Do not launch other agents. Do not edit AGENTS.md, PLAN.md, TASKS.md, docs/pictures, .git or runner/config files (runner source is allowed only for A01/A02). No legacy compatibility or audio-production runtime.
Validation ownership: the coordinator runs focused checks, make test, make build and git diff --check. Gradle sockets are unavailable in your sandbox: do not run Gradle/make or change permissions. Add regressions and truthfully report PENDING_COORDINATOR. Pending coordinator checks are not an implementation blocker. Human listening/Logic/visual/video decisions require real evidence.
Evidence: read only this completed packet: ${packet}. It names the exact check logs for this candidate. If a check failed, read only that named log as needed. Never read worker/review transcript logs, including your own; never recursively search the run directory or historical execution logs. Limit tool output to relevant ranges (about 200 lines per call). Keep the fresh review tied to this exact Git tree using git diff ${ctx.base} ${review ? candidate : ''}.
${ctx.originalDir ? 'This candidate reuses preserved work; inspect its current diff rather than starting over.' : ''}
${repair ? 'Two implementation/validation attempts failed. Diagnose the concrete failures below and make only scoped repairs. Reuse the existing implementation.' : ''}
Previous concrete feedback: ${(ctx.feedback || 'None.').slice(-8000)}
Return the required JSON with task=${ctx.task} exactly, base=${ctx.base}, commit=${ctx.base}, candidate=${candidate}, summary, blocker, status, tests, artifacts. Do not claim completion or a test pass without coordinator evidence. Remaining batch time: ${Math.max(1, Math.floor((deadline - Date.now()) / 60000))} minutes.`;
    const modelName = repair ? (cfg.repairModel ?? 'gpt-5.6-sol') : (cfg.model ?? 'gpt-5.6-terra');
    const reasoningEffort = repair ? (cfg.repairReasoningEffort ?? 'high') : (cfg.reasoningEffort ?? 'high');
    const common = ['--ignore-user-config', '-m', modelName, '-c', `model_reasoning_effort=${JSON.stringify(reasoningEffort)}`, '-c', 'approval_policy="never"'];
    const args = ['exec', '--sandbox', review ? 'read-only' : 'workspace-write', '-C', ctx.worktree];
    if (resume) args.push('resume');
    args.push(...common);
    if (review) args.push('--ephemeral');
    args.push('--json', '--output-schema', schemaFile, '-o', resultFile);
    if (resume) args.push(ctx.workerSession);
    args.push('-');
    ctx.phase = review ? 'REVIEW' : 'worker'; persist();
    await run(ctx, cfg.codex, args, label, prompt, true, !review);
    const result = checkResult(json(resultFile), ctx.task, ctx.base, review, candidate, structured);
    return structured ? checkFindings(result, ctx.paths, ctx.recoverySubtasks ?? []) : result;
  }
  function stage(ctx) {
    if (git(ctx.worktree, 'rev-parse', 'HEAD') !== ctx.base) throw Error('Worker changed HEAD');
    git(ctx.worktree, 'add', '-A');
    const changed = git(ctx.worktree, 'diff', '--name-only', '--no-renames', '-z', ctx.base).split('\0').filter(Boolean);
    ctx.runnerChanged = changed.some(file => file.startsWith('tools/terra-'));
    if (changed.some(file => !permitted(file, ctx.paths))) throw Error(`Changes outside ${ctx.task} path scope: ${changed.filter(f => !permitted(f, ctx.paths)).join(', ')}`);
    const protectedPaths = ['AGENTS.md', 'PLAN.md', 'TASKS.md', 'docs/pictures', ...(['A01', 'A02'].includes(ctx.task) ? [] : ['tools/terra-runner.mjs', 'tools/terra-runner.test.mjs', 'tools/terra-throughput.test.mjs'])];
    if (git(ctx.worktree, 'diff', ctx.base, '--', ...protectedPaths)) throw Error('Worker changed coordinator-owned files');
    ctx.tree = git(ctx.worktree, 'write-tree');
    if (recoveryUnit?.startTree) {
      const delta = git(ctx.worktree, 'diff', '--name-only', '--no-renames', '-z', recoveryUnit.startTree, ctx.tree).split('\0').filter(Boolean);
      if (delta.some(file => !recoveryUnit.finding.files.includes(file))) throw Error('Recovery changed files outside finding scope');
    }
    if (ctx.result?.status === 'READY_FOR_VALIDATION' && ctx.tree === git(ctx.worktree, 'rev-parse', `${ctx.base}^{tree}`)) throw Error('No-change candidate requires coordinator verification');
    persist();
  }
  function moveToIntegrationBase(ctx) {
    assertHead();
    if (ctx.base === head) return;
    stage(ctx);
    const patchFile = path.join(ctx.dir, `candidate-${ctx.attempt}.patch`);
    fs.writeFileSync(patchFile, execFileSync('git', ['-C', ctx.worktree, 'diff', '--binary', ctx.base], { maxBuffer: 16 * 1024 * 1024 }));
    const newTree = path.join(ctx.dir, `integration-${ctx.attempt}`);
    git(cfg.repo, 'worktree', 'add', '--detach', newTree, head);
    // Applying to a fresh worktree leaves the original candidate intact on conflict.
    ctx.pendingIntegrationWorktree = newTree; persist();
    try {
      if (fs.statSync(patchFile).size) git(newTree, 'apply', '--index', patchFile);
    } catch (e) {
      ctx.rebaseFailure = { worktree: newTree, error: e.message, removed: false };
      if (!git(newTree, 'status', '--porcelain')) {
        git(cfg.repo, 'worktree', 'remove', newTree); ctx.rebaseFailure.removed = true;
      }
      delete ctx.pendingIntegrationWorktree; persist();
      throw Object.assign(new Error(`Integration conflict applying preserved ${ctx.task}; original candidate retained: ${e.message}`), { integrationConflict: true });
    }
    delete ctx.pendingIntegrationWorktree;
    ctx.originalWorktrees = [...(ctx.originalWorktrees ?? []), ctx.worktree];
    ctx.worktree = newTree; ctx.originalBase ??= ctx.base; ctx.base = head;
    // A resumed session is bound to its original checkout. Repairs on the combined
    // candidate need a new session in this checkout, never edits to the old tree.
    delete ctx.workerSession;
    ctx.result = { ...ctx.result, base: head, commit: head }; ctx.tree = undefined; ctx.checks = [];
    ctx.feedback = 'The preceding independent task was integrated. Candidate applied onto the current integration base; all checks and fresh review must validate this combined tree.';
    persist();
  }
  async function validate(ctx) {
    ctx.checks = []; stage(ctx); const tree = ctx.tree;
    await run(ctx, 'git', ['diff', '--check', ctx.base], `whitespace-${ctx.attempt}`);
    for (const [i, check] of (cfg.taskChecks?.[ctx.task] ?? []).entries())
      await run(ctx, check.argv[0], check.argv.slice(1), `task-check-${i}-${ctx.attempt}`);
    if (ctx.runnerChanged || ['A01', 'A02'].includes(ctx.task)) {
      await run(ctx, process.execPath, ['--test', 'tools/terra-runner.test.mjs'], `runner-test-${ctx.attempt}`);
      if (fs.existsSync(path.join(ctx.worktree, 'tools/terra-throughput.test.mjs')))
        await run(ctx, process.execPath, ['--test', 'tools/terra-throughput.test.mjs'], `throughput-test-${ctx.attempt}`);
    }
    await run(ctx, 'make', ['test'], `test-${ctx.attempt}`);
    await run(ctx, 'make', ['build'], `build-${ctx.attempt}`);
    stage(ctx); if (ctx.tree !== tree) throw Error('Candidate changed during validation');
    const review = await agent(ctx, true);
    stage(ctx); if (ctx.tree !== tree) throw Error('Candidate changed after validation/review');
    ctx.lastReview = review; persist();
    if (review.status !== 'PASS') throw Object.assign(new Error(`${review.summary}\n${review.blocker}`), { reviewFailure: true });
    ctx.reviewedTree = tree; persist();
  }
  function integrate(ctx) {
    guard(); assertHead(); stage(ctx);
    if (ctx.base !== head || ctx.tree !== ctx.reviewedTree) throw Error('Stale or unreviewed candidate');
    if (ctx.tree !== git(ctx.worktree, 'rev-parse', `${ctx.base}^{tree}`))
      git(ctx.worktree, 'commit', '-m', `${ctx.task}: ${ctx.title}`);
    const implementation = git(ctx.worktree, 'rev-parse', 'HEAD');
    const status = effectiveStatus(ctx.task, ctx.result.status);
    fs.writeFileSync(path.join(ctx.worktree, 'TASKS.md'), mark(md, ctx.task, status,
      `${implementation.slice(0, 12)}; ${ctx.result.summary}; test/build + fresh review passed; ${status === 'WAITING_USER' ? ctx.result.blocker + '; ' : ''}evidence ${ctx.dir}`));
    git(ctx.worktree, 'add', 'TASKS.md'); git(ctx.worktree, 'commit', '-m', `queue: record ${ctx.task} ${status}`);
    const integrated = git(ctx.worktree, 'rev-parse', 'HEAD');
    git(cfg.repo, 'update-ref', `refs/heads/${cfg.branch}`, integrated, head);
    head = integrated; md = git(cfg.repo, 'show', `${head}:TASKS.md`);
    state.last = { task: ctx.task, status, implementation, integrated, reviewedTree: ctx.tree, dir: ctx.dir, worktree: ctx.worktree,
      recoverySubtasks: ctx.recoverySubtasks ?? [],
      worktreeCleaned: false, modelTokens: state.modelTokens, cachedInputTokens: state.cachedInputTokens, usage: { ...state.usage } };
    ctx.phase = 'INTEGRATED'; ctx.integrated = integrated; ctx.implementation = implementation;
    persist(); // A crash after ref update is reconciled from this commit and queue, never reimplemented.
    const remaining = entries(state).filter(c => c !== ctx);
    state.active = remaining[0]; state.parallel = remaining.slice(1); if (!state.active) delete state.active;
    delete state.error; persist();
    // Only fast-forward an explicitly configured clean live checkout. Never
    // update the ref under a checkout: that leaves the old index/files behind.
    if (cfg.liveBranch) {
      try {
        if (git(cfg.repo, 'branch', '--show-current') !== cfg.liveBranch) throw Error('Live checkout is on another branch');
        if (git(cfg.repo, 'status', '--porcelain', '--untracked-files=no')) throw Error('Live checkout has tracked edits');
        git(cfg.repo, 'merge', '--ff-only', integrated);
        state.last.liveHead = git(cfg.repo, 'rev-parse', 'HEAD');
      } catch (e) { state.last.liveSyncError = e.message; }
      persist();
    }
    try {
      for (const wt of [ctx.worktree, ...(ctx.originalWorktrees ?? [])]) git(cfg.repo, 'worktree', 'remove', '--force', wt);
      state.last.worktreeCleaned = true; persist();
    } catch (e) { state.last.worktreeCleanupError = e.message; persist(); }
    console.log(JSON.stringify(state.last));
  }
  function prepare(task, parallel) {
    assertHead(); guard();
    const today = new Date().toISOString().slice(0, 10);
    if (state.runs.filter(r => r.date === today).length >= cfg.maxRunsPerDay) throw Error('Daily run admission limit reached');
    const dir = path.join(root, 'runs', `${new Date().toISOString().replace(/[:.]/g, '-')}-${task.id}`), worktree = path.join(dir, 'worktree');
    fs.mkdirSync(dir, { recursive: true });
    const ctx = { task: task.id, title: task.title, base: head, dir, worktree, phase: 'creating-worktree', attempt: 0,
      paths: parallel ? cfg.parallelPaths[task.id] : cfg.allowedPaths[task.id], parallelOwner: parallel, checks: [] };
    if (state.active) (state.parallel ??= []).push(ctx); else state.active = ctx;
    state.runs.push({ date: today, task: task.id, dir }); persist();
    git(cfg.repo, 'worktree', 'add', '--detach', worktree, head);
    const seed = cfg.seedPatches?.[task.id];
    if (seed) {
      if (seed.base !== head) throw Error('Preserved patch has a stale integration base');
      const patch = fs.readFileSync(seed.file);
      if (createHash('sha256').update(patch).digest('hex') !== seed.sha256) throw Error('Preserved patch digest mismatch');
      git(worktree, 'apply', '--index', seed.file); ctx.seed = seed; ctx.originalDir = seed.priorRun; persist();
    }
    return ctx;
  }
  async function implement(ctx, repair = false) {
    ctx.resumeStage = 'implement'; persist();
    ctx.result = await agent(ctx, false, repair); stage(ctx);
    if (ctx.result.status === 'BLOCKED') throw Error(ctx.result.blocker || ctx.result.summary);
    ctx.phase = 'READY_FOR_VALIDATION'; ctx.resumeStage = 'validate'; persist();
  }
  async function finish(ctx, alreadyImplemented, startingAttempt = 0) {
    for (let localAttempt = startingAttempt; localAttempt < 3; localAttempt++) {
      try {
        if (!alreadyImplemented || localAttempt > startingAttempt) await implement(ctx, localAttempt === 2);
        moveToIntegrationBase(ctx);
        await validate(ctx); integrate(ctx); return;
      } catch (e) {
        ctx.interruptionReason = e.interruptionReason;
        ctx.integrationConflict = e.integrationConflict;
        ctx.feedback = e.message; ctx.blocker = failureReason(ctx.result, ctx.feedback); ctx.phase = 'BLOCKED';
        if (localAttempt === 2 && !e.interruptionReason) ctx.terminalFailure = true;
        if (localAttempt === 2 && e.reviewFailure && cfg.maxFindingRecoveries > 0 && !ctx.recoverySubtasks &&
            ctx.lastReview.findings.length <= cfg.maxFindingRecoveries && ctx.lastReview.findings.every(f => f.kind === 'code')) {
          ctx.recoverySourceTree = ctx.tree;
          ctx.recoverySubtasks = ctx.lastReview.findings.map((finding, i) => ({
            id: `${ctx.task}/R${i + 1}`, finding, status: 'TODO', modelTokens: 0,
            maxTokens: cfg.findingRecoveryTokens, maxMinutes: cfg.findingRecoveryMinutes,
          }));
          ctx.phase = 'RECOVERY_PENDING';
          atomic(path.join(ctx.dir, 'finding-recovery.json'), { parent: ctx.task, sourceTree: ctx.tree, subtasks: ctx.recoverySubtasks });
        }
        persist();
        if (ctx.result?.status === 'BLOCKED' || e.interruptionReason || e.integrationConflict || /outside.*scope|coordinator-owned|Worker changed|Integration base changed|Candidate changed/.test(e.message) || localAttempt === 2) throw e;
        ctx.nextRepair = localAttempt + 1; ctx.resumeStage = 'implement'; ctx.attempt++; persist();
      }
    }
  }
  async function recoverFinding(ctx) {
    guard(true);
    const preservedTree = ctx.tree;
    stage(ctx);
    if (ctx.tree !== preservedTree) throw Error('Recovery candidate changed outside its preserved checkpoint');
    // Reapply only onto an authorized descendant base, preserving the old tree.
    moveToIntegrationBase(ctx); stage(ctx);
    const unit = ctx.recoverySubtasks.find(s => s.status === 'TODO');
    unit.status = 'RUNNING'; unit.startTree = ctx.tree; unit.started = new Date().toISOString();
    unit.maxTokens = Math.min(unit.maxTokens, cfg.findingRecoveryTokens);
    unit.maxMinutes = Math.min(unit.maxMinutes, cfg.findingRecoveryMinutes);
    recoveryUnit = unit; recoveryDeadline = Date.now() + unit.maxMinutes * 60000;
    delete ctx.lastReview; delete ctx.reviewedTree; delete ctx.workerSession;
    ctx.recoveryPatch = path.join(ctx.dir, `recovery-${ctx.attempt}.patch`);
    fs.writeFileSync(ctx.recoveryPatch, execFileSync('git', ['-C', ctx.worktree, 'diff', '--binary', ctx.base], { maxBuffer: 16 * 1024 * 1024 }));
    persist();
    try {
      await implement(ctx);
      if (ctx.tree === unit.startTree) throw Error(`Recovery ${unit.id} made no code progress`);
      try { await validate(ctx); }
      catch (e) { if (!e.reviewFailure) throw e; }
      const review = ctx.lastReview;
      if (!review?.resolvedFindingIds.includes(unit.finding.id)) throw Error(`Recovery ${unit.id} did not resolve its acceptance condition; stopping repeated attempts`);
      for (const subtask of ctx.recoverySubtasks) if (review.resolvedFindingIds.includes(subtask.finding.id)) {
        subtask.status = 'DONE'; subtask.resolvedTree = ctx.tree;
      }
      unit.finished = new Date().toISOString();
      unit.elapsedMs = Date.now() - Date.parse(unit.started); persist();
      if (review.status === 'PASS') { integrate(ctx); return true; }
      if (!ctx.recoverySubtasks.some(s => s.status === 'TODO')) throw Error('Original findings resolved, but new review defects remain; recovery limit reached');
      ctx.feedback = `${review.summary}\n${review.blocker}`;
      ctx.phase = 'RECOVERY_PENDING'; delete ctx.interruptionReason; persist();
      console.log(JSON.stringify({ task: ctx.task, recovery: unit.id, status: 'DONE', parent: 'RECOVERY_PENDING', dir: ctx.dir }));
      return false;
    } catch (e) {
      if (unit.status !== 'DONE') unit.status = 'BLOCKED';
      unit.finished = new Date().toISOString(); unit.elapsedMs = Date.now() - Date.parse(unit.started);
      unit.blocker = e.message; ctx.findingRecoveryStopped = true; ctx.terminalFailure = true;
      persist(); throw e;
    } finally {
      atomic(path.join(ctx.dir, 'finding-recovery.json'), { parent: ctx.task, sourceTree: ctx.recoverySourceTree, subtasks: ctx.recoverySubtasks, stopped: !!ctx.findingRecoveryStopped });
      recoveryUnit = undefined; recoveryDeadline = undefined;
    }
  }
  try {
    assertHead(); checkDeadChildren(state); delete state.childPid; state.childPids = [];
    if (command === 'run' && entries(state).length) throw Error(`Interrupted run retained at ${state.active.dir}; inspect and use bounded retry`);
    if (['retry', 'continue', 'recover-finding'].includes(command) && !state.active) throw Error('No retained task to retry');
    guard();
    if (['retry', 'continue', 'recover-finding'].includes(command)) for (const ctx of retainedEntries()) {
      if (!cfg.allowedTasks.includes(ctx.task) || (!cfg.video && ctx.task.startsWith('V')))
        throw Error(`Retained task is no longer authorized by the current allowlist/video policy: ${ctx.task}`);
      const currentPaths = ctx.parallelOwner ? cfg.parallelPaths[ctx.task] : cfg.allowedPaths[ctx.task];
      validateAllowedPaths(currentPaths);
      if ((ctx.paths ?? currentPaths).some(p => !permitted(p, currentPaths) || !permitted(p, cfg.allowedPaths[ctx.task])))
        throw Error(`Retained path ownership was narrowed; reconcile the preserved candidate: ${ctx.task}`);
      // Never let a saved wider path policy override current authorization.
      ctx.paths ??= currentPaths;
      if (command === 'recover-finding') {
        if (!findingRecoverable(ctx, cfg) || ctx.recoverySubtasks.length > cfg.maxFindingRecoveries)
          throw Error('No authorized pending finding recovery');
        for (const unit of ctx.recoverySubtasks) if (unit.finding.files.some(file => !permitted(file, currentPaths)))
          throw Error('Finding ownership was narrowed; preserve candidate');
      } else if (ctx.recoverySubtasks) throw Error('Use recover-finding or defer; parent retries cannot reset finding recovery');
    }
    // Refused recovery is not a new failure of the saved candidate. Check
    // admission before replacing its interruption checkpoint or batch usage.
    if (['retry', 'continue', 'recover-finding'].includes(command)) {
      const retained = retainedEntries(), rows = queue(md);
      if (retained.length > cfg.maxTasksPerBatch) throw Error('Retained wave exceeds batch task limit');
      const today = new Date().toISOString().slice(0, 10);
      if (state.runs.filter(r => r.date === today).length + retained.length > cfg.maxRunsPerDay) throw Error('Daily run admission limit reached');
      for (const ctx of retained) {
        if (!fs.existsSync(ctx.worktree) || git(ctx.worktree, 'rev-parse', 'HEAD') !== ctx.base || git(cfg.repo, 'merge-base', ctx.base, head) !== ctx.base)
          throw Error('Retained integration base changed; reconcile manually');
        if (command === 'continue') {
          if (!continuable(ctx) || (ctx.continuationCount ?? 0) >= cfg.maxContinuations) throw Error('Continuation limit reached or failure requires repair');
        } else if (command === 'retry' && (ctx.recoveryCount ?? 0) >= cfg.maxRecoveryRetries) throw Error('Recovery retry limit reached; inspect/defer instead of recycling');
        const row = rows.find(t => t.id === ctx.task);
        if (!row || row.state !== 'TODO' || !row.deps.every(id => rows.find(t => t.id === id)?.state === 'DONE')) throw Error('Retained task is not dependency-ready');
      }
    }
    batchAdmitted = true;
    if (state.batch) (state.batchHistory ??= []).push({ ...state.batch, modelTokens: state.modelTokens, usage: state.usage });
    state.batch = { started: new Date().toISOString(), command, deadline: new Date(deadline).toISOString(), maxTasks: cfg.maxTasksPerBatch, completed: 0 };
    state.modelTokens = 0; state.cachedInputTokens = 0;
    state.usage = { input: 0, cached: 0, nonCachedInput: 0, output: 0, reasoning: 0 }; persist();
    let admitted = 0;
    if (command === 'recover-finding') {
      const ctx = state.active;
      ctx.attempt++; ctx.feedback = ctx.feedback || ctx.blocker || state.error;
      const unit = ctx.recoverySubtasks.find(s => s.status === 'TODO');
      state.runs.push({ date: new Date().toISOString().slice(0, 10), task: ctx.task, dir: ctx.dir, findingRecovery: unit.id }); persist();
      const integrated = await recoverFinding(ctx);
      admitted++; if (integrated) state.batch.completed++; persist();
      // One recovery subtask per wake, even if it completed its parent.
      state.batch.finished = new Date().toISOString(); delete state.error; persist();
      return;
    }
    if (['retry', 'continue'].includes(command)) {
      const retained = retainedEntries();
      if (retained.length > cfg.maxTasksPerBatch) throw Error('Retained wave exceeds batch task limit');
      // Check every retained base before the first integration advances head.
      for (const ctx of retained) {
        if (!fs.existsSync(ctx.worktree) || git(ctx.worktree, 'rev-parse', 'HEAD') !== ctx.base || git(cfg.repo, 'merge-base', ctx.base, head) !== ctx.base)
          throw Error('Retained integration base changed; reconcile manually');
      }
      for (const ctx of retained) {
        if (command === 'continue') {
          if (!continuable(ctx) || (ctx.continuationCount ?? 0) >= cfg.maxContinuations) throw Error('Continuation limit reached or failure requires repair');
        } else if ((ctx.recoveryCount ?? 0) >= cfg.maxRecoveryRetries) throw Error('Recovery retry limit reached; inspect/defer instead of recycling');
        const alreadyImplemented = ctx.result?.status !== 'BLOCKED' && (ctx.phase === 'READY_FOR_VALIDATION' ||
          (command === 'continue' && (ctx.resumeStage === 'validate' || (!ctx.resumeStage && ctx.result?.status === 'READY_FOR_VALIDATION'))));
        const row = queue(md).find(t => t.id === ctx.task);
        if (!row || row.state !== 'TODO' || !row.deps.every(id => queue(md).find(t => t.id === id)?.state === 'DONE')) throw Error('Retained task is not dependency-ready');
        ctx.title = row.title; ctx.paths ??= cfg.allowedPaths[ctx.task]; ctx.checks = [];
        if (command === 'continue') ctx.continuationCount = (ctx.continuationCount ?? 0) + 1;
        else ctx.recoveryCount = (ctx.recoveryCount ?? 0) + 1;
        ctx.attempt = (ctx.attempt ?? 2) + 1;
        ctx.feedback = ctx.feedback || ctx.blocker || state.error || 'Resume the preserved candidate and validate it';
        const today = new Date().toISOString().slice(0, 10);
        if (state.runs.filter(r => r.date === today).length >= cfg.maxRunsPerDay) throw Error('Daily run admission limit reached');
        state.runs.push({ date: today, task: ctx.task, dir: ctx.dir, recovery: ctx.recoveryCount, continuation: ctx.continuationCount }); persist();
        moveToIntegrationBase(ctx);
        await finish(ctx, alreadyImplemented, command === 'continue' ? (ctx.nextRepair ?? 0) : 0); admitted++; state.batch.completed++; persist();
      }
    }
    while (admitted < cfg.maxTasksPerBatch) {
      // A recovered owner may leave a peer with a different failure/stage.
      // Its next wake gets its own admission policy; never abandon or replay it.
      if (entries(state).length) break;
      // Reaching a batch budget after successful integration ends cleanly; a new wake gets a new bounded batch.
      if (Date.now() >= deadline || state.modelTokens >= cfg.maxReportedTokens) break;
      guard(); assertHead();
      const today = new Date().toISOString().slice(0, 10), available = cfg.maxRunsPerDay - state.runs.filter(r => r.date === today).length;
      if (available <= 0) { if (!admitted) throw Error('Daily run admission limit reached'); break; }
      const wave = selectWave(queue(md), cfg, Math.min(cfg.maxTasksPerBatch - admitted, available));
      if (!wave.length) break;
      const contexts = wave.map(t => prepare(t, wave.length > 1)); admitted += contexts.length;
      // Only implementation is parallel. Validation, review and integration stay serialized.
      const results = await Promise.allSettled(contexts.map(c => implement(c)));
      // Persist every owner's outcome before one failure can return the batch.
      // Otherwise a later interrupted peer loses the checkpoint needed to resume.
      for (let i = 0; i < contexts.length; i++) if (results[i].status === 'rejected') {
        contexts[i].interruptionReason = results[i].reason.interruptionReason;
        contexts[i].feedback = results[i].reason.message;
        contexts[i].blocker = contexts[i].feedback; contexts[i].phase = 'BLOCKED';
      }
      persist();
      for (let i = 0; i < contexts.length; i++) {
        if (results[i].status === 'rejected') {
          if (contexts[i].result?.status === 'BLOCKED' || results[i].reason.interruptionReason || /outside.*scope|coordinator-owned|Worker changed/.test(contexts[i].feedback)) throw results[i].reason;
          contexts[i].attempt++; persist();
          contexts[i].nextRepair = 1; persist();
          await finish(contexts[i], false, 1);
        } else {
          await finish(contexts[i], true);
        }
        state.batch.completed++; persist();
      }
    }
    state.batch.finished = new Date().toISOString(); delete state.error; persist();
    console.log(JSON.stringify({ batch: state.batch, modelTokens: state.modelTokens, next: select(queue(md), cfg.video, cfg.allowedTasks)?.id ?? null }));
  } catch (e) {
    // A refused heartbeat is not a new failure of the retained candidate.
    // Preserve the concrete test/review blocker for the recovery coordinator.
    if (!batchAdmitted && state.active) throw e;
    state.error = e.message;
    if (state.active && state.active.phase !== 'INTEGRATED') {
      if (command === 'recover-finding') { state.active.findingRecoveryStopped = true; state.active.terminalFailure = true; }
      state.active.interruptionReason = e.interruptionReason;
      state.active.integrationConflict = e.integrationConflict;
      state.active.feedback = e.message; state.active.blocker = e.message;
      state.active.phase = findingRecoverable(state.active, cfg) ? 'RECOVERY_PENDING' : 'BLOCKED';
    }
    persist(); throw e;
  } finally {
    stop(); release(); process.removeListener('SIGINT', interrupt); process.removeListener('SIGTERM', interrupt);
  }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(e => { console.error(e.message); process.exitCode = 1; });
}
