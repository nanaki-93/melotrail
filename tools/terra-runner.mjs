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
export function checkResult(r, task, base, review = false, candidate = 'UNCOMMITTED') {
  const allowed = review ? ['PASS', 'FAIL'] : ['READY_FOR_VALIDATION', 'WAITING_USER', 'BLOCKED'];
  if (!r || typeof r !== 'object' || Array.isArray(r) || Object.keys(r).length !== resultFields.size ||
      Object.keys(r).some(key => !resultFields.has(key)) || r.task !== task || r.base !== base || !allowed.includes(r.status) ||
      !Array.isArray(r.tests) || !r.tests.every(test => typeof test === 'string') ||
      !Array.isArray(r.artifacts) || !r.artifacts.every(artifact => typeof artifact === 'string') || typeof r.summary !== 'string' ||
      !r.summary.trim() || typeof r.blocker !== 'string' || r.commit !== base || r.candidate !== candidate) throw Error('Invalid/stale agent result');
  return r;
}
const git = (cwd, ...args) => execFileSync('git', ['-C', cwd, '-c', 'core.hooksPath=/dev/null', ...args], { encoding: 'utf8' }).trim();
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

async function main() {
  const [command = 'status', configFile = path.join(os.homedir(), '.codex/melotrail-terra/config.json')] = process.argv.slice(2);
  const cfg = json(configFile), root = cfg.stateDir;
  if (!/^codex\/[a-zA-Z0-9][a-zA-Z0-9/_-]*$/.test(cfg.branch)) throw Error('Integration requires a codex/ branch');
  for (const key of ['minutes', 'maxRunsPerDay', 'maxReportedTokens'])
    if (!Number.isFinite(cfg[key]) || cfg[key] < 0) throw Error(`Invalid numeric limit: ${key}`);
  for (const key of ['repo', 'stateDir', 'codex', 'javaHome', 'gradleHome'])
    if (typeof cfg[key] !== 'string' || !path.isAbsolute(cfg[key])) throw Error(`Absolute path required: ${key}`);
  if (root === cfg.repo || root.startsWith(cfg.repo + path.sep)) throw Error('Keep control state outside the source checkout');
  const verifyBranch = () => {
    if (git(cfg.repo, 'worktree', 'list', '--porcelain').split('\n').includes(`branch refs/heads/${cfg.branch}`))
      throw Error('Integration branch is checked out; detach its worktree before automatic integration');
  };
  fs.mkdirSync(root, { recursive: true });
  const lockDir = path.join(root, 'lock');
  const stateFile = path.join(root, 'state.json');
  const loadState = () => fs.existsSync(stateFile) ? json(stateFile) : { runs: [], modelTokens: 0, cachedInputTokens: 0, paused: false };
  if (command === 'pause' || command === 'resume') {
    if (command === 'resume' && fs.existsSync(lockDir)) throw Error('Wait for active process to stop before resuming');
    fs.writeFileSync(path.join(root, 'paused'), command === 'pause' ? 'paused\n' : '');
    if (command === 'resume') { const s = loadState(); s.paused = false; atomic(stateFile, s); }
    console.log(command); return;
  }
  if (command === 'recover') {
    const owner = json(path.join(lockDir, 'owner.json'));
    if (owner.host !== os.hostname()) throw Error('Lock belongs to another host');
    try { process.kill(owner.pid, 0); throw Error('Owner still alive; do not recover'); }
    catch (e) { if (e.code !== 'ESRCH') throw e; }
    const s = loadState();
    if (s.childPid) {
      try { process.kill(-s.childPid, 0); throw Error('Child process group still alive'); }
      catch (e) { if (e.code !== 'ESRCH') throw e; }
    }
    fs.renameSync(lockDir, `${lockDir}.recovered.${Date.now()}`);
    console.log('Recovered dead lock. Interrupted worktree is preserved; inspect state before running.'); return;
  }
  if (command === 'defer') {
    const release = lock(lockDir);
    try {
      verifyBranch();
      const s = loadState();
      if (!s.active) throw Error('No interrupted task to defer');
      if (s.childPid) throw Error('Unresolved child PID; inspect before deferring');
      const base = git(cfg.repo, 'rev-parse', cfg.branch);
      if (base !== s.active.base) throw Error('Integration changed; reconcile manually');
      const dest = path.join(root, 'deferred', String(Date.now()));
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      git(cfg.repo, 'worktree', 'add', '--detach', dest, base);
      const p = path.join(dest, 'TASKS.md');
      fs.writeFileSync(p, mark(read(p), s.active.task, 'BLOCKED', `${s.active.blocker || s.error || 'Interrupted run'}; preserved ${s.active.dir}`));
      git(dest, 'add', 'TASKS.md'); git(dest, 'commit', '-m', `queue: defer ${s.active.task} and preserve unfinished work`);
      git(cfg.repo, 'update-ref', `refs/heads/${cfg.branch}`, git(dest, 'rev-parse', 'HEAD'), base);
      s.lastDeferred = s.active; delete s.active; atomic(stateFile, s);
      console.log(JSON.stringify(s.lastDeferred));
    } finally { release(); }
    return;
  }
  const head = git(cfg.repo, 'rev-parse', cfg.branch);
  const md = git(cfg.repo, 'show', `${head}:TASKS.md`);
  if (!Array.isArray(cfg.allowedTasks) || !cfg.allowedTasks.length) throw Error('Explicit task allowlist required');
  const tasks = queue(md);
  if (cfg.allowedTasks.some(id => typeof id !== 'string') || new Set(cfg.allowedTasks).size !== cfg.allowedTasks.length ||
      cfg.allowedTasks.some(id => !tasks.some(task => task.id === id))) throw Error('Invalid task allowlist');
  if (typeof cfg.video !== 'boolean') throw Error('Explicit video policy required');
  if (!cfg.allowedPaths || typeof cfg.allowedPaths !== 'object' || Array.isArray(cfg.allowedPaths) ||
      Object.keys(cfg.allowedPaths).some(id => !tasks.some(task => task.id === id))) throw Error('Invalid task path policy');
  for (const id of cfg.allowedTasks)
    if (!Object.hasOwn(cfg.allowedPaths, id)) throw Error(`Missing task-specific allowed paths: ${id}`);
  for (const paths of Object.values(cfg.allowedPaths)) validateAllowedPaths(paths);
  const next = select(tasks, cfg.video, cfg.allowedTasks);
  if (command === 'status' || command === 'dry-run') {
    console.log(JSON.stringify({ branch: cfg.branch, head, next: next || null, locked: fs.existsSync(lockDir),
      paused: fs.existsSync(path.join(root, 'paused')) && !!read(path.join(root, 'paused')).trim(),
      state: loadState() }, null, 2)); return;
  }
  if (command !== 'run') throw Error('Use status, dry-run, run, pause, resume, recover, or defer');
  const release = lock(lockDir);
  let state = loadState();
  const start = Date.now(), deadline = start + cfg.minutes * 60000;
  let child;
  const terminate = () => { if (child?.pid) { try { process.kill(-child.pid, 'SIGTERM'); } catch {} } };
  const interrupted = () => { state.paused = true; atomic(stateFile, state); terminate(); };
  process.on('SIGINT', interrupted); process.on('SIGTERM', interrupted);
  try {
    verifyBranch();
    if (state.active) throw Error(`Interrupted run retained at ${state.active.dir}; coordinator must inspect and resolve it before retrying`);
    const pauseFile = path.join(root, 'paused');
    if (state.paused || (fs.existsSync(pauseFile) && read(pauseFile).trim())) throw Error('Paused');
    const today = new Date().toISOString().slice(0, 10);
    if (state.runs.filter(r => r.date === today).length >= cfg.maxRunsPerDay) throw Error('Daily run admission limit reached');
    if (!next) { console.log('No dependency-ready task; inspect human gates.'); return; }
    validateAllowedPaths(cfg.allowedPaths?.[next.id]);
    if (git(cfg.repo, 'rev-parse', cfg.branch) !== head) throw Error('Integration base changed');
    const runDir = path.join(root, 'runs', `${new Date().toISOString().replace(/[:.]/g, '-')}-${next.id}`);
    const worktree = path.join(runDir, 'worktree');
    fs.mkdirSync(runDir, { recursive: true });
    state.runs.push({ date: today, task: next.id, dir: runDir });
    state.active = { task: next.id, base: head, dir: runDir, worktree, phase: 'creating-worktree' };
    state.modelTokens = 0;
    state.cachedInputTokens = 0;
    state.usage = { input: 0, cached: 0, nonCachedInput: 0, output: 0, reasoning: 0 };
    atomic(stateFile, state);
    git(cfg.repo, 'worktree', 'add', '--detach', worktree, head);
    const seed = cfg.seedPatches?.[next.id];
    if (seed) {
      if (seed.base !== head) throw Error('Preserved patch has a stale integration base');
      const patch = fs.readFileSync(seed.file);
      if (createHash('sha256').update(patch).digest('hex') !== seed.sha256) throw Error('Preserved patch digest mismatch');
      // No three-way merge or silent overwrite: conflicting seeds fail closed.
      git(worktree, 'apply', '--index', seed.file);
      state.active.seed = { file: seed.file, sha256: seed.sha256, priorRun: seed.priorRun };
    }
    state.active.phase = 'worker'; atomic(stateFile, state);
    const env = Object.fromEntries(['HOME', 'USER', 'LOGNAME', 'TMPDIR', 'LANG', 'LC_ALL', 'TERM', 'CODEX_HOME'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
    Object.assign(env, { JAVA_HOME: cfg.javaHome, GRADLE_USER_HOME: cfg.gradleHome, PATH: `${cfg.javaHome}/bin:${process.env.PATH}` });
    async function run(exe, args, label, input = '', requiresModelBudget = false) {
      if (state.paused || (fs.existsSync(pauseFile) && read(pauseFile).trim())) throw Error('Paused');
      if (Date.now() >= deadline) throw Error('Run deadline exhausted');
      if (requiresModelBudget && state.modelTokens >= cfg.maxReportedTokens) throw Error('Terra-call budget exhausted');
      const out = fs.openSync(path.join(runDir, `${label}.log`), 'w');
      child = spawn(exe, args, { cwd: worktree, env, detached: true, stdio: ['pipe', out, out] });
      fs.closeSync(out);
      state.childPid = child.pid; atomic(stateFile, state);
      child.stdin.on('error', () => {}); child.stdin.end(input);
      let timedOut = false;
      const timer = setTimeout(() => {
        timedOut = true;
        terminate();
        const pid = child?.pid;
        const killer = setTimeout(() => { try { process.kill(-pid, 'SIGKILL'); } catch {} }, 5000);
        killer.unref();
      }, Math.max(1, deadline - Date.now()));
      const poll = setInterval(() => {
        if (fs.existsSync(pauseFile) && read(pauseFile).trim()) { state.paused = true; terminate(); }
      }, 1000);
      let code;
      try { code = await new Promise((resolve, reject) => { child.on('error', reject); child.on('close', resolve); }); }
      finally {
        clearTimeout(timer); clearInterval(poll);
        // A terminated parent may leave subprocesses behind; never release their lock.
        try { process.kill(-child.pid, 'SIGKILL'); } catch {}
        child = null; delete state.childPid; atomic(stateFile, state);
      }
      if (timedOut || Date.now() >= deadline) throw Error(`${label} exceeded run deadline`);
      if (state.paused) throw Error('Run cancelled; work preserved');
      if (code !== 0) throw Error(`${label} failed (${code}); see ${runDir}/${label}.log`);
    }
    async function agent(review, attempt, feedback, candidate = 'UNCOMMITTED') {
      const label = `${review ? 'review' : 'worker'}-${attempt}`;
      const schemaFile = path.join(runDir, `${label}.schema.json`), resultFile = path.join(runDir, `${label}.json`);
      atomic(schemaFile, schema(review ? ['PASS', 'FAIL'] : ['READY_FOR_VALIDATION', 'WAITING_USER', 'BLOCKED']));
      const prompt = `${read(path.join(worktree, 'TASKS.md')).split('## Reusable agent prompt')[1] || ''}
Assigned task: ${next.id}: ${next.title}. Base commit: ${head}. Candidate tree: ${candidate}.
Mode: ${review ? 'Fresh independent REVIEW. Read the complete staged and unstaged diff against the base, verify task acceptance and actual coordinator logs. Do not edit files or rerun Gradle in the read-only sandbox. Return PASS only with no actionable defect or missing required evidence; otherwise FAIL with concrete fixes.' : 'IMPLEMENT only the assigned task. Inspect first; make focused regression tests. Leave all changes UNCOMMITTED. Do not edit the queue in TASKS.md; the coordinator owns statuses. Return READY_FOR_VALIDATION when implementation is ready for independent checks, WAITING_USER for an actual human gate, BLOCKED for an unresolved implementation/product blocker. READY_FOR_VALIDATION is not completion and does not claim that tests passed.'}
Validation ownership: the coordinator runs make test, make build and the fixed task-specific checks after the worker returns. Gradle local sockets are unavailable in the worker sandbox. Do not invoke Gradle/make or attempt to bypass/reconfigure the sandbox; do not retry a denied command. Run supported lightweight focused checks, add required regressions, and list unexecuted checks truthfully as PENDING_COORDINATOR in tests. A worker-only validation restriction is not an implementation blocker. If a coordinator check fails, inspect its actual log and fix the demonstrated defect; do not claim a pass or weaken its test. Native startup and human approvals require actual evidence.
Allowed changed paths: ${JSON.stringify(cfg.allowedPaths[next.id])}. ${seed ? `Preserved candidate patch is already applied. Reuse it and inspect prior evidence at ${seed.priorRun}; do not restart the completed work.` : ''}
The bounded runner replaces prompt instructions to select a task, create worktrees, review, commit, or continue. Do not launch other agents or tasks. Do not change ${next.id === 'A01' ? '' : 'tools/terra-runner*, '}AGENTS.md, PLAN.md, TASKS.md, docs/pictures, .git, automation/configuration or files outside this worktree. Preserve original MIDI and evidence. All optional V tasks are selected for unpaid implementation, with no authorized paid generation budget or publication. Do not use external connectors or services to send messages, push code, upload or spend money. Do not weaken tests. Use the current documented JDK. No legacy migration.
For video: use independently built companion/ with no media dependency on the MIDI app; stop at actual asset, rights and budget decisions. Never invent approvals. Review logs are in ${runDir}. Run at most this task, within the remaining ${Math.max(1, Math.floor((deadline-Date.now())/60000))} minutes. Report task=${next.id}, base=${head}, commit=${head} (coordinator commits later), tests, artifacts, summary, blocker and status in the required JSON schema.
Include candidate=${candidate} in your result; review approval applies only to that exact Git tree.
Previous concrete feedback: ${feedback || 'None.'}`;
      const args = ['exec', '--ignore-user-config', '--ephemeral', '-m', 'gpt-5.6-terra', '-c', 'model_reasoning_effort="high"', '-c', 'approval_policy="never"',
        '--sandbox', review ? 'read-only' : 'workspace-write', '-C', worktree, '--json', '--output-schema', schemaFile, '-o', resultFile];
      if (!review) args.push('--add-dir', cfg.gradleHome);
      args.push('-');
      await run(cfg.codex, args, label, prompt, true);
      for (const line of read(path.join(runDir, `${label}.log`)).split('\n')) {
        try {
          const event = JSON.parse(line);
          if (event.type === 'turn.completed') {
            const usage = reportedUsage(event.usage);
            for (const key of ['input', 'cached', 'nonCachedInput', 'output', 'reasoning']) state.usage[key] += usage[key];
            state.modelTokens += usage.modelTokens;
            state.cachedInputTokens += usage.cached;
          }
        } catch {}
      }
      atomic(stateFile, state);
      return checkResult(json(resultFile), next.id, head, review, candidate);
    }
    let result, feedback = '', accepted = false, reviewedTree;
    for (let attempt = 0; attempt <= 2; attempt++) {
      result = await agent(false, attempt, feedback);
      if (git(worktree, 'rev-parse', 'HEAD') !== head) throw Error('Worker changed HEAD');
      git(worktree, 'add', '-A'); // Include new files in protection checks and review.
      const changed = git(worktree, 'diff', '--name-only', '--no-renames', '-z', head).split('\0').filter(Boolean);
      if (changed.some(file => !permitted(file, cfg.allowedPaths[next.id]))) throw Error(`Changes outside ${next.id} path scope: ${changed.filter(file => !permitted(file, cfg.allowedPaths[next.id])).join(', ')}`);
      const protectedPaths = ['AGENTS.md', 'PLAN.md', 'TASKS.md', 'docs/pictures', ...(next.id === 'A01' ? [] : ['tools/terra-runner.mjs', 'tools/terra-runner.test.mjs'])];
      if (git(worktree, 'diff', head, '--', ...protectedPaths)) throw Error('Worker changed coordinator-owned files');
      if (result.status === 'BLOCKED') break;
      reviewedTree = git(worktree, 'write-tree');
      if (result.status === 'READY_FOR_VALIDATION' && reviewedTree === git(worktree, 'rev-parse', `${head}^{tree}`)) throw Error('No-change candidate requires coordinator verification');
      try {
        await run('git', ['diff', '--check', head], `whitespace-${attempt}`);
        await run(process.execPath, ['--test', 'tools/terra-runner.test.mjs'], `runner-test-${attempt}`);
        await run('make', ['test'], `test-${attempt}`);
        await run('make', ['build'], `build-${attempt}`);
        // Configuration is coordinator-owned; workers cannot submit executable commands.
        for (const [index, check] of (cfg.taskChecks?.[next.id] || []).entries()) {
          if (!check || !Array.isArray(check.argv) || check.argv.some(v => typeof v !== 'string') ||
              check.argv.length < 2 || !['./gradlew', process.execPath].includes(check.argv[0])) throw Error('Invalid configured task check');
          await run(check.argv[0], check.argv.slice(1), `task-check-${index}-${attempt}`);
        }
        const review = await agent(true, attempt, feedback, reviewedTree);
        git(worktree, 'add', '-A');
        if (git(worktree, 'write-tree') !== reviewedTree) throw Error('Candidate changed after validation/review');
        if (review.status === 'PASS') { accepted = true; break; }
        feedback = review.summary + '\n' + review.blocker;
      } catch (e) { feedback = e.message; }
    }
    if (!accepted) {
      state.active.phase = 'BLOCKED'; state.active.blocker = result?.blocker || feedback || 'Review did not pass';
      atomic(stateFile, state);
      throw Error(`Task retained for coordinator resolution: ${state.active.blocker}`);
    }
    if (state.paused || Date.now() >= deadline || (fs.existsSync(pauseFile) && read(pauseFile).trim())) throw Error('Cancelled or timed out before integration');
    if (git(cfg.repo, 'rev-parse', cfg.branch) !== head) throw Error('Integration base changed; preserve reviewed work');
    verifyBranch();
    git(worktree, 'add', '-A');
    if (git(worktree, 'write-tree') !== reviewedTree) throw Error('Reviewed candidate changed before commit');
    if (git(worktree, 'status', '--porcelain')) {
      git(worktree, 'add', '-A');
      git(worktree, 'commit', '-m', `${next.id}: ${next.title}`);
    }
    const implementation = git(worktree, 'rev-parse', 'HEAD');
    const status = effectiveStatus(next.id, result.status);
    const note = `${implementation.slice(0,12)}; ${result.summary}; ${status === 'WAITING_USER' ? result.blocker || 'Actual human approval/evidence required' : 'test/build + fresh Terra review passed'}; evidence ${runDir}`;
    fs.writeFileSync(path.join(worktree, 'TASKS.md'), mark(md, next.id, status, note));
    git(worktree, 'add', 'TASKS.md'); git(worktree, 'commit', '-m', `queue: record ${next.id} ${status}`);
    const integrated = git(worktree, 'rev-parse', 'HEAD');
    // Compare-and-swap prevents integrating against a changed base.
    git(cfg.repo, 'update-ref', `refs/heads/${cfg.branch}`, integrated, head);
    state.last = { task: next.id, status, implementation, integrated, reviewedTree, dir: runDir, worktree, worktreeCleaned: false, modelTokens: state.modelTokens, cachedInputTokens: state.cachedInputTokens, usage: state.usage };
    delete state.active; atomic(stateFile, state);
    // A completed candidate is committed and reviewable from `implementation`; retain
    // logs but release its dedicated worktree. Interrupted worktrees stay preserved.
    try {
      git(cfg.repo, 'worktree', 'remove', '--force', worktree);
      state.last.worktreeCleaned = true; atomic(stateFile, state);
    } catch (e) {
      state.last.worktreeCleanupError = e.message; atomic(stateFile, state);
    }
    console.log(JSON.stringify(state.last, null, 2));
  } catch (e) {
    state.error = e.message; atomic(stateFile, state); throw e;
  } finally {
    terminate(); release(); process.removeListener('SIGINT', interrupted); process.removeListener('SIGTERM', interrupted);
  }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(e => { console.error(e.message); process.exitCode = 1; });
}
