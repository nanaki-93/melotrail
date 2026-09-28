/*
 * V19a controlled motion is deliberately a small compositor, not an image generator.
 * It consumes the V18a prepared-scene descriptor, verifies its immutable image pins,
 * and moves only supplied layer pixels. V19b adds bounded rigid scenery travel,
 * explicit source-section joins, and absolute chunk continuity to that contract.
 */
'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { createCanvas, loadImage } = require('@napi-rs/canvas');
const {
  SceneryInputError, activeSection, sampleOffsets, sectionOffset, stateForFrame, validateScenery,
} = require('./scenery.cjs');

const TOOL_ID = 'melotrail-controlled-motion';
const TOOL_VERSION = '1.1.0';
const REQUEST_SCHEMA = 'melotrail-controlled-motion-request-v1';
const LIMITS = Object.freeze({
  maximumFramesPerInvocation: 300,
  maximumWidth: 3840,
  maximumHeight: 2160,
  maximumPixelsPerFrame: 8294400,
  maximumBlinkAmount: 1,
  maximumBreathPixels: 4,
  maximumHeadDegrees: 3,
  maximumSteamRatePerSecond: 8,
  maximumSteamRisePixelsPerSecond: 28,
});

class MotionInputError extends Error {
  constructor(message) { super(message); this.name = 'MotionInputError'; }
}

const sha256 = (bytes) => crypto.createHash('sha256').update(bytes).digest('hex');
const clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));
const smooth = (value) => { const v = clamp(value, 0, 1); return v * v * (3 - 2 * v); };
const safeId = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;

function stableHash(seed, label, index) {
  const digest = crypto.createHash('sha256').update(`${seed}:${label}:${index}`).digest();
  return digest.readUInt32BE(0) / 0x1_0000_0000;
}

function number(value, label, minimum = -Infinity, maximum = Infinity) {
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new MotionInputError(`${label} must be a finite number in ${minimum}..${maximum}.`);
  }
  return value;
}

function portableArtifactPath(relativePath) {
  if (typeof relativePath !== 'string' || !relativePath || path.isAbsolute(relativePath) ||
      relativePath.split('/').some((segment) => segment === '..' || !segment)) {
    throw new MotionInputError('Prepared-scene artifact paths must be non-empty portable relative paths.');
  }
  return relativePath;
}

function resolveProjectRoot(projectRoot) {
  const resolved = path.resolve(projectRoot);
  let stat;
  try { stat = fs.lstatSync(resolved); } catch { throw new MotionInputError('The supplied video project root is missing.'); }
  if (!stat.isDirectory() || stat.isSymbolicLink()) {
    throw new MotionInputError('The supplied video project root must be a non-symlink directory.');
  }
  let real;
  try { real = fs.realpathSync.native(resolved); } catch { throw new MotionInputError('The supplied video project root cannot be resolved safely.'); }
  if (real !== resolved) {
    throw new MotionInputError('The supplied video project root path may not contain symbolic links.');
  }
  return real;
}

function requireContainedRegularFile(resolvedRoot, resolved, relativePath, label) {
  let current = resolvedRoot;
  for (const segment of relativePath.split('/')) {
    current = path.join(current, segment);
    let stat;
    try { stat = fs.lstatSync(current); } catch { throw new MotionInputError(`${label} artifact is missing: ${relativePath}.`); }
    if (stat.isSymbolicLink()) {
      throw new MotionInputError(`${label} artifact path may not contain symbolic links: ${relativePath}.`);
    }
  }
  let finalStat;
  try { finalStat = fs.lstatSync(resolved); } catch { throw new MotionInputError(`${label} artifact is missing: ${relativePath}.`); }
  if (!finalStat.isFile()) throw new MotionInputError(`${label} artifact is not a regular file: ${relativePath}.`);
  let real;
  try { real = fs.realpathSync.native(resolved); } catch { throw new MotionInputError(`${label} artifact cannot be resolved safely: ${relativePath}.`); }
  if (!real.startsWith(`${resolvedRoot}${path.sep}`)) {
    throw new MotionInputError(`${label} artifact escapes the supplied project root.`);
  }
}

function resolveArtifact(resolvedRoot, artifact, label) {
  if (!artifact || typeof artifact.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(artifact.sha256)) {
    throw new MotionInputError(`${label} needs a lowercase SHA-256 artifact pin.`);
  }
  const relativePath = portableArtifactPath(artifact.relativePath);
  const resolved = path.resolve(resolvedRoot, relativePath);
  if (!resolved.startsWith(`${resolvedRoot}${path.sep}`)) {
    throw new MotionInputError(`${label} artifact escapes the supplied project root.`);
  }
  requireContainedRegularFile(resolvedRoot, resolved, relativePath, label);
  let bytes;
  try { bytes = fs.readFileSync(resolved); } catch { throw new MotionInputError(`${label} artifact is missing: ${relativePath}.`); }
  if (sha256(bytes) !== artifact.sha256) throw new MotionInputError(`${label} source bytes do not match their V18a SHA-256 pin.`);
  return { path: resolved, sha256: artifact.sha256 };
}

function capabilityById(scene, id) {
  const capability = scene.motionCapabilities?.filter((candidate) => candidate.id === id) || [];
  if (capability.length !== 1) throw new MotionInputError(`Control references missing or duplicate V18a capability '${id}'.`);
  if (capability[0].reviewStatus === 'REJECTED') throw new MotionInputError(`Capability '${id}' has a rejected prepared-scene review.`);
  return capability[0];
}

function layerById(scene, id, label = 'layer') {
  const matches = scene.layers?.filter((layer) => layer.id === id) || [];
  if (matches.length !== 1) throw new MotionInputError(`Prepared scene needs exactly one ${label} '${id}'.`);
  if (matches[0].reviewStatus === 'REJECTED') throw new MotionInputError(`Prepared ${label} '${id}' has a rejected review.`);
  return matches[0];
}

function poseById(scene, id) {
  const matches = scene.poses?.filter((pose) => pose.id === id) || [];
  if (matches.length !== 1) throw new MotionInputError(`Prepared scene needs exactly one pose '${id}'.`);
  if (matches[0].reviewStatus === 'REJECTED') throw new MotionInputError(`Prepared pose '${id}' has a rejected review.`);
  return matches[0];
}

function maskForHead(scene, subjectLayerId) {
  const masks = (scene.masks || []).filter((mask) => mask.purpose === 'HEAD_REGION' && mask.layerIds?.length === 1 && mask.layerIds[0] === subjectLayerId);
  if (masks.length !== 1) {
    throw new MotionInputError(`Head gesture requires exactly one supplied HEAD_REGION mask for subject '${subjectLayerId}'.`);
  }
  if (masks[0].reviewStatus === 'REJECTED') throw new MotionInputError(`Head mask '${masks[0].id}' has a rejected review.`);
  return masks[0];
}

function cleanBase(scene, width, height) {
  const candidates = (scene.layers || []).filter((layer) => layer.kind === 'ENVIRONMENT' &&
    layer.bounds?.x === 0 && layer.bounds?.y === 0 && layer.bounds?.width === width && layer.bounds?.height === height);
  if (candidates.length !== 1) {
    throw new MotionInputError('Controlled subject motion requires exactly one supplied full-canvas ENVIRONMENT clean background layer.');
  }
  return candidates[0];
}

function assertMatchingSpace(item, spaceId, label) {
  if (!item.bounds || item.bounds.coordinateSpaceId !== spaceId) {
    throw new MotionInputError(`${label} must use the selected output coordinate space.`);
  }
}

function assertAmount(control, capability, maximum, label) {
  const amount = number(control.amount, `${label} amount`, 0, maximum);
  const requested = amount * capability.maximum;
  if (!Number.isFinite(requested) || requested < capability.minimum || requested > capability.maximum) {
    throw new MotionInputError(`${label} exceeds V18a capability '${capability.id}' bounds.`);
  }
  return amount;
}

function hasVersionedId(value) {
  return Boolean(value) && safeId.test(value.id || '') && Number.isSafeInteger(value.version) && value.version > 0;
}

function hasArtifactPin(value) {
  return Boolean(value) && typeof value.relativePath === 'string' &&
    /^[0-9a-f]{64}$/.test(value.sha256 || '');
}

function rectContains(outer, point) {
  return point.x >= outer.x && point.x <= outer.x + outer.width &&
    point.y >= outer.y && point.y <= outer.y + outer.height;
}

function rectsIntersect(first, second) {
  return first.coordinateSpaceId === second.coordinateSpaceId &&
    first.x < second.x + second.width && second.x < first.x + first.width &&
    first.y < second.y + second.height && second.y < first.y + first.height;
}

/*
 * Kotlin owns the prepared-scene schema. The external renderer nevertheless
 * rejects the descriptor shapes it relies on, so a malformed V18a document
 * cannot turn into silently different pixels at this boundary.
 */
function assertV18aDescriptor(scene) {
  if (!hasVersionedId(scene.id)) throw new MotionInputError('Prepared scene needs a V18a versioned id.');
  if (typeof scene.createdAt !== 'string' || !Number.isFinite(Date.parse(scene.createdAt))) {
    throw new MotionInputError('Prepared scene needs an ISO-8601 createdAt timestamp.');
  }
  const source = scene.source;
  if (!source || (!source.look && !Array.isArray(source.references)) ||
      (!source.look && source.references.length === 0)) {
    throw new MotionInputError('Prepared scene needs its V18a source look or reference pins.');
  }
  if (source.look && (!hasVersionedId(source.look.id) || !hasArtifactPin(source.look.artifact))) {
    throw new MotionInputError('Prepared scene source look must retain a versioned id and artifact pin.');
  }
  for (const reference of source.references || []) {
    if (!hasVersionedId(reference.id) || !hasArtifactPin(reference.descriptorArtifact) || !reference.original?.artifact ||
        !hasArtifactPin(reference.original.artifact)) {
      throw new MotionInputError('Prepared scene source references must retain versioned descriptor and original artifact pins.');
    }
  }
  if (!Array.isArray(scene.dependencies) || scene.dependencies.length === 0 ||
      new Set(scene.dependencies.map((dependency) => dependency?.id)).size !== scene.dependencies.length ||
      scene.dependencies.some((dependency) => !safeId.test(dependency?.id || '') ||
        typeof dependency.version !== 'string' || !dependency.version || !/^[0-9a-f]{64}$/.test(dependency.sha256 || ''))) {
    throw new MotionInputError('Prepared scene needs unique V18a dependency pins.');
  }

  const layers = scene.layers || [];
  const byLayerId = new Map(layers.map((layer) => [layer.id, layer]));
  if (byLayerId.size !== layers.length || layers.some((layer) => !safeId.test(layer?.id || '') || !layer.bounds)) {
    throw new MotionInputError('Prepared scene layers need unique ids and bounds.');
  }
  const masks = scene.masks || [];
  const byMaskId = new Map(masks.map((mask) => [mask.id, mask]));
  if (byMaskId.size !== masks.length || masks.some((mask) => !safeId.test(mask?.id || '') || !Array.isArray(mask.layerIds) || !mask.layerIds.length)) {
    throw new MotionInputError('Prepared scene masks need unique ids and target layers.');
  }
  for (const mask of masks) {
    const alpha = mask.alpha;
    const pixelCount = mask.image?.width * mask.image?.height;
    if (!alpha || !Number.isSafeInteger(alpha.opaquePixels) || !Number.isSafeInteger(alpha.translucentPixels) ||
        !Number.isSafeInteger(alpha.transparentPixels) || alpha.opaquePixels < 0 || alpha.translucentPixels < 0 ||
        alpha.transparentPixels < 0 || !Number.isSafeInteger(pixelCount) || pixelCount < 1 ||
        alpha.opaquePixels + alpha.translucentPixels + alpha.transparentPixels !== pixelCount) {
      throw new MotionInputError(`V18a mask '${mask.id}' needs measured source-alpha counts matching its decoded image dimensions.`);
    }
    if (mask.layerIds.some((layerId) => !byLayerId.has(layerId) || !rectsIntersect(mask.bounds, byLayerId.get(layerId).bounds))) {
      throw new MotionInputError(`V18a mask '${mask.id}' must overlap every target layer.`);
    }
    if (mask.purpose === 'HEAD_REGION' && (mask.layerIds.length !== 1 || byLayerId.get(mask.layerIds[0]).kind !== 'SUBJECT')) {
      throw new MotionInputError(`V18a HEAD_REGION mask '${mask.id}' must identify exactly one subject layer.`);
    }
  }
  for (const relation of scene.occlusionRelations || []) {
    const mask = byMaskId.get(relation.maskId);
    if (!byLayerId.has(relation.occluderLayerId) || !byLayerId.has(relation.occludedLayerId) ||
        !mask || !mask.layerIds.includes(relation.occluderLayerId) || !mask.layerIds.includes(relation.occludedLayerId)) {
      throw new MotionInputError('V18a occlusion relation must use a mask covering both related layers.');
    }
  }
  const landmarks = new Map((scene.subjectLandmarks || []).map((landmark) => [landmark.id, landmark]));
  if (landmarks.size !== (scene.subjectLandmarks || []).length) throw new MotionInputError('Prepared scene subject-landmark ids must be unique.');
  for (const landmark of landmarks.values()) {
    const subject = byLayerId.get(landmark.subjectLayerId);
    if (!subject || subject.kind !== 'SUBJECT' || landmark.position?.coordinateSpaceId !== subject.bounds.coordinateSpaceId ||
        !rectContains(subject.bounds, landmark.position.point)) {
      throw new MotionInputError(`V18a landmark '${landmark.id}' must be inside its subject layer.`);
    }
  }
  for (const anchor of scene.effectAnchors || []) {
    const sourceLayer = byLayerId.get(anchor.layerId);
    const point = anchor.position || landmarks.get(anchor.landmarkId)?.position;
    if (!sourceLayer || !point || point.coordinateSpaceId !== sourceLayer.bounds.coordinateSpaceId ||
        !rectContains(sourceLayer.bounds, point.point)) {
      throw new MotionInputError(`V18a effect anchor '${anchor.id}' must resolve inside its source layer.`);
    }
    if (anchor.landmarkId && landmarks.get(anchor.landmarkId).subjectLayerId !== anchor.layerId) {
      throw new MotionInputError(`V18a effect anchor '${anchor.id}' must use a landmark from its source layer.`);
    }
  }
}

/** Pure request/descriptor admission. It does no image IO and has no side effects. */
function validateRequest(request) {
  if (!request || request.schema !== REQUEST_SCHEMA) throw new MotionInputError(`Request schema must be '${REQUEST_SCHEMA}'.`);
  if (!request.preparedScene || request.preparedScene.schemaVersion !== 2) throw new MotionInputError('Request must embed a V18a schemaVersion 2 prepared scene.');
  if (!Number.isSafeInteger(request.seed) || request.seed < 0) throw new MotionInputError('Seed must be a non-negative safe integer.');
  const scene = request.preparedScene;
  assertV18aDescriptor(scene);
  const canvas = request.canvas || {};
  const width = number(canvas.width, 'Canvas width', 1, LIMITS.maximumWidth);
  const height = number(canvas.height, 'Canvas height', 1, LIMITS.maximumHeight);
  if (!Number.isInteger(width) || !Number.isInteger(height) || width * height > LIMITS.maximumPixelsPerFrame) {
    throw new MotionInputError('Canvas dimensions exceed the bounded controlled-motion frame capacity.');
  }
  if (!safeId.test(canvas.coordinateSpaceId || '')) throw new MotionInputError('Canvas coordinateSpaceId must be a safe V18a coordinate-space ID.');
  const space = (scene.coordinateSpaces || []).filter((candidate) => candidate.id === canvas.coordinateSpaceId);
  if (space.length !== 1 || space[0].width < width || space[0].height < height) {
    throw new MotionInputError('Canvas dimensions must fit inside one V18a coordinate space.');
  }
  const finishedViewport = (scene.layers || []).filter((layer) => layer.kind === 'FINISHED_SCENE' &&
    layer.bounds?.coordinateSpaceId === canvas.coordinateSpaceId && layer.bounds.x === 0 && layer.bounds.y === 0 &&
    layer.bounds.width === width && layer.bounds.height === height);
  if (finishedViewport.length !== 1) {
    throw new MotionInputError('Canvas must exactly match the prepared FINISHED_SCENE viewport; wider scenery is not stretched into the output.');
  }
  const fps = number(request.fps, 'Frames per second', 1, 120);
  const frameRange = request.frameRange || {};
  const startFrame = number(frameRange.startFrame, 'frameRange.startFrame', 0, Number.MAX_SAFE_INTEGER);
  const frameCount = number(frameRange.frameCount, 'frameRange.frameCount', 1, LIMITS.maximumFramesPerInvocation);
  const endFrameExclusive = startFrame + frameCount;
  if (!Number.isSafeInteger(startFrame) || !Number.isSafeInteger(frameCount) || !Number.isSafeInteger(endFrameExclusive)) {
    throw new MotionInputError('Frame range start, count, and end must use safe integer arithmetic.');
  }
  if (!Array.isArray(request.controls) || request.controls.length > 16) {
    throw new MotionInputError('Request controls must be an array with at most sixteen entries.');
  }
  const ids = new Set();
  const subjectControlKinds = new Set();
  const controls = [];
  let subjectLayerId = null;
  for (const control of request.controls) {
    if (!control || !safeId.test(control.id || '') || ids.has(control.id)) throw new MotionInputError('Control IDs must be unique safe identifiers.');
    ids.add(control.id);
    const capability = capabilityById(scene, control.capabilityId);
    if (control.kind === 'blink') {
      requireUniqueSubjectControl(subjectControlKinds, control.kind);
      if (capability.targetType !== 'POSE' || capability.control !== 'POSE_BLEND') throw new MotionInputError(`Blink '${control.id}' needs a POSE_BLEND capability.`);
      const pose = poseById(scene, capability.targetId);
      const subject = layerById(scene, pose.subjectLayerId, 'subject layer');
      if (subject.kind !== 'SUBJECT') throw new MotionInputError(`Blink pose '${pose.id}' must identify a SUBJECT layer.`);
      assertMatchingSpace(subject, canvas.coordinateSpaceId, `Subject '${subject.id}'`);
      assertMatchingSpace(pose, canvas.coordinateSpaceId, `Blink pose '${pose.id}'`);
      if (subject.bounds.width !== pose.bounds.width || subject.bounds.height !== pose.bounds.height ||
          subject.bounds.x !== pose.bounds.x || subject.bounds.y !== pose.bounds.y) {
        throw new MotionInputError(`Blink pose '${pose.id}' must align exactly with subject '${subject.id}' to preserve its silhouette.`);
      }
      subjectLayerId = requireSameSubject(subjectLayerId, subject.id, control.kind);
      controls.push({ ...control, capability, pose, subject, amount: assertAmount(control, capability, LIMITS.maximumBlinkAmount, 'Blink') });
    } else if (control.kind === 'breathing') {
      requireUniqueSubjectControl(subjectControlKinds, control.kind);
      if (capability.targetType !== 'LAYER' || capability.control !== 'TRANSLATE_Y') throw new MotionInputError(`Breathing '${control.id}' needs a subject TRANSLATE_Y capability.`);
      const subject = layerById(scene, capability.targetId, 'subject layer');
      if (subject.kind !== 'SUBJECT') throw new MotionInputError(`Breathing '${control.id}' targets a non-subject layer.`);
      assertMatchingSpace(subject, canvas.coordinateSpaceId, `Subject '${subject.id}'`);
      subjectLayerId = requireSameSubject(subjectLayerId, subject.id, control.kind);
      const amplitude = number(control.amplitudePixels, 'Breathing amplitudePixels', 0, LIMITS.maximumBreathPixels);
      if (0 < capability.minimum || 0 > capability.maximum || amplitude < capability.minimum || amplitude > capability.maximum) {
        throw new MotionInputError(`Breathing amplitude exceeds V18a capability '${capability.id}' bounds.`);
      }
      controls.push({ ...control, capability, subject, amplitude });
    } else if (control.kind === 'headGesture') {
      requireUniqueSubjectControl(subjectControlKinds, control.kind);
      if (capability.targetType !== 'LAYER' || capability.control !== 'ROTATE') throw new MotionInputError(`Head gesture '${control.id}' needs a subject ROTATE capability.`);
      const subject = layerById(scene, capability.targetId, 'subject layer');
      if (subject.kind !== 'SUBJECT') throw new MotionInputError(`Head gesture '${control.id}' targets a non-subject layer.`);
      assertMatchingSpace(subject, canvas.coordinateSpaceId, `Subject '${subject.id}'`);
      subjectLayerId = requireSameSubject(subjectLayerId, subject.id, control.kind);
      const amplitude = number(control.amplitudeDegrees, 'Head gesture amplitudeDegrees', 0, LIMITS.maximumHeadDegrees);
      if (amplitude < capability.minimum || amplitude > capability.maximum || -amplitude < capability.minimum || -amplitude > capability.maximum) {
        throw new MotionInputError(`Head gesture amplitude exceeds V18a capability '${capability.id}' bounds.`);
      }
      controls.push({ ...control, capability, subject, mask: maskForHead(scene, subject.id), amplitude });
    } else if (control.kind === 'steam') {
      if (capability.targetType !== 'EFFECT_ANCHOR' || capability.control !== 'EFFECT_RATE') throw new MotionInputError(`Steam '${control.id}' needs an anchored EFFECT_RATE capability.`);
      const anchors = (scene.effectAnchors || []).filter((anchor) => anchor.id === capability.targetId);
      if (anchors.length !== 1 || anchors[0].reviewStatus === 'REJECTED') throw new MotionInputError(`Steam '${control.id}' needs one non-rejected V18a effect anchor.`);
      const anchor = anchors[0];
      const source = layerById(scene, anchor.layerId, 'effect anchor source layer');
      const anchorPosition = anchor.position || (scene.subjectLandmarks || []).find((landmark) => landmark.id === anchor.landmarkId)?.position;
      if (!anchorPosition || anchorPosition.coordinateSpaceId !== canvas.coordinateSpaceId) {
        throw new MotionInputError(`Steam '${control.id}' anchor must resolve to the selected V18a coordinate space.`);
      }
      const rate = number(control.ratePerSecond, 'Steam ratePerSecond', 0.01, LIMITS.maximumSteamRatePerSecond);
      if (rate < capability.minimum || rate > capability.maximum) throw new MotionInputError(`Steam rate exceeds V18a capability '${capability.id}' bounds.`);
      const rise = number(control.risePixelsPerSecond ?? 18, 'Steam risePixelsPerSecond', 0.01, LIMITS.maximumSteamRisePixelsPerSecond);
      controls.push({ ...control, capability, anchor, source, rate, rise });
    } else {
      throw new MotionInputError(`Unsupported controlled-motion kind '${control.kind}'.`);
    }
  }
  if (subjectLayerId) cleanBase(scene, width, height);
  let scenery;
  try {
    scenery = validateScenery(scene, { coordinateSpaceId: canvas.coordinateSpaceId, width, height }, fps,
      { startFrame, frameCount }, request.scenery, request.seed, request.initialState);
  } catch (error) {
    if (error instanceof SceneryInputError) throw new MotionInputError(error.message);
    throw error;
  }
  if (controls.length === 0 && !scenery) {
    throw new MotionInputError('Request needs controlled subject/effect motion, validated scenery, or both.');
  }
  return Object.freeze({ request, scene, width, height, fps, startFrame, frameCount, controls, subjectLayerId, scenery, spaceId: canvas.coordinateSpaceId });
}

function requireSameSubject(existing, candidate, controlKind) {
  if (existing && existing !== candidate) throw new MotionInputError(`${controlKind} targets '${candidate}', but this bounded invocation supports one independently controlled subject.`);
  return candidate;
}

function requireUniqueSubjectControl(kinds, kind) {
  if (kinds.has(kind)) {
    throw new MotionInputError(`This bounded invocation supports at most one '${kind}' control for its subject.`);
  }
  kinds.add(kind);
}

function frameTime(frame, fps) { return frame / fps; }

function blinkClosedAmount(time, seed, id, amount) {
  // A deterministic absolute-time schedule: revisiting a frame produces the same blink.
  const epoch = Math.floor(time / 7);
  let closed = 0;
  for (let slot = epoch - 1; slot <= epoch + 1; slot += 1) {
    const start = slot * 7 + 1 + stableHash(seed, `${id}:start`, slot) * 4.5;
    const close = 0.08 + stableHash(seed, `${id}:close`, slot) * 0.05;
    const open = 0.11 + stableHash(seed, `${id}:open`, slot) * 0.06;
    if (time >= start && time < start + close) closed = Math.max(closed, smooth((time - start) / close));
    if (time >= start + close && time < start + close + open) closed = Math.max(closed, 1 - smooth((time - start - close) / open));
  }
  return closed * amount;
}

function motionAt(validated, frame) {
  const time = frameTime(frame, validated.fps);
  const state = { time, blink: 0, breathPixels: 0, headRadians: 0, steam: [] };
  for (const control of validated.controls) {
    if (control.kind === 'blink') state.blink = Math.max(state.blink, blinkClosedAmount(time, validated.request.seed, control.id, control.amount));
    if (control.kind === 'breathing') {
      const period = number(control.periodSeconds ?? 3.6, 'Breathing periodSeconds', 1, 12);
      const phase = stableHash(validated.request.seed, `${control.id}:phase`, 0) * Math.PI * 2;
      state.breathPixels += control.amplitude * (1 - Math.cos((time / period) * Math.PI * 2 + phase)) / 2;
    }
    if (control.kind === 'headGesture') {
      const period = number(control.periodSeconds ?? 5.8, 'Head gesture periodSeconds', 2, 16);
      const phase = stableHash(validated.request.seed, `${control.id}:phase`, 0) * Math.PI * 2;
      state.headRadians += control.amplitude * Math.sin((time / period) * Math.PI * 2 + phase) * Math.PI / 180;
    }
    if (control.kind === 'steam') state.steam.push(control);
  }
  return state;
}

function drawAsset(ctx, image, item) {
  const { bounds, transform, pivot } = item;
  ctx.save();
  if (transform) {
    const tolerance = 1e-7;
    const canonical = Math.abs(transform.translateX - bounds.x) <= tolerance &&
      Math.abs(transform.translateY - bounds.y) <= tolerance &&
      Math.abs(transform.scaleX * image.width - bounds.width) <= tolerance &&
      Math.abs(transform.scaleY * image.height - bounds.height) <= tolerance;
    if (!canonical) {
      ctx.restore();
      throw new MotionInputError(`Prepared component '${item.id}' has a transform inconsistent with its V18a bounds and decoded source size.`);
    }
    const origin = pivot?.point || { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 };
    ctx.translate(origin.x, origin.y);
    ctx.rotate((transform.rotationDegrees || 0) * Math.PI / 180);
    ctx.translate(-origin.x, -origin.y);
    ctx.translate(transform.translateX, transform.translateY);
    ctx.scale(transform.scaleX, transform.scaleY);
    ctx.drawImage(image, 0, 0);
  } else {
    ctx.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height);
  }
  ctx.restore();
}

function fullCanvasMask(width, height, mask, image) {
  const canvas = createCanvas(width, height);
  drawAsset(canvas.getContext('2d'), image, mask);
  return canvas;
}

function usesSourceAlphaCoverage(mask) {
  return mask.alpha.opaquePixels + mask.alpha.translucentPixels > 0 && mask.alpha.transparentPixels > 0;
}

/*
 * V18a admits either a source-alpha cutout or an opaque contrasting grayscale
 * mask. Decide from the importer's measurements of the original decoded image,
 * before placement adds transparent canvas pixels. Alpha cutouts use alpha
 * alone, so black selected pixels remain selected. Only the grayscale form
 * converts source luminance to coverage.
 */
function maskCoverageCanvas(width, height, mask, image) {
  const canvas = fullCanvasMask(width, height, mask, image);
  if (usesSourceAlphaCoverage(mask)) return canvas;

  const source = createCanvas(image.width, image.height);
  source.getContext('2d').drawImage(image, 0, 0);
  const sourcePixels = source.getContext('2d').getImageData(0, 0, image.width, image.height).data;
  let minimumLuminance = 255;
  let maximumLuminance = 0;
  for (let offset = 0; offset < sourcePixels.length; offset += 4) {
    if (sourcePixels[offset + 3] === 0) continue;
    const luminance = Math.round((sourcePixels[offset] * 2126 + sourcePixels[offset + 1] * 7152 +
      sourcePixels[offset + 2] * 722) / 10_000);
    minimumLuminance = Math.min(minimumLuminance, luminance);
    maximumLuminance = Math.max(maximumLuminance, luminance);
  }
  if (maximumLuminance <= minimumLuminance) {
    throw new MotionInputError(`V18a mask '${mask.id}' has neither usable source-alpha separation nor visible grayscale contrast.`);
  }

  const context = canvas.getContext('2d');
  const pixels = context.getImageData(0, 0, width, height);
  for (let offset = 0; offset < pixels.data.length; offset += 4) {
    const luminance = (pixels.data[offset] * 2126 + pixels.data[offset + 1] * 7152 +
      pixels.data[offset + 2] * 722) / 10_000 / 255;
    pixels.data[offset + 3] = Math.round(pixels.data[offset + 3] * luminance);
  }
  context.putImageData(pixels, 0, 0);
  return canvas;
}

function preparedMaskCoverage(validated, images, mask) {
  const key = `coverage:${mask.id}`;
  if (!images.has(key)) {
    images.set(key, maskCoverageCanvas(
      validated.width, validated.height, mask, images.get(`mask:${mask.id}`),
    ));
  }
  return images.get(key);
}

async function loadPreparedImages(validated, projectRoot) {
  const resolvedRoot = resolveProjectRoot(projectRoot);
  const required = new Map();
  for (const layer of validated.scene.layers || []) required.set(`layer:${layer.id}`, layer);
  for (const pose of validated.scene.poses || []) required.set(`pose:${pose.id}`, pose);
  for (const mask of validated.scene.masks || []) required.set(`mask:${mask.id}`, mask);
  const loaded = new Map();
  for (const [key, item] of required) {
    const artifact = resolveArtifact(resolvedRoot, item.image.artifact, key);
    loaded.set(key, await loadImage(artifact.path));
  }
  return loaded;
}

function subjectCanvas(validated, images, state) {
  const subject = layerById(validated.scene, validated.subjectLayerId, 'subject layer');
  const canvas = createCanvas(validated.width, validated.height);
  const ctx = canvas.getContext('2d');
  drawAsset(ctx, images.get(`layer:${subject.id}`), subject);
  const silhouette = createCanvas(validated.width, validated.height);
  drawAsset(silhouette.getContext('2d'), images.get(`layer:${subject.id}`), subject);
  const blink = validated.controls.find((control) => control.kind === 'blink');
  if (blink && state.blink > 0) {
    const pose = createCanvas(validated.width, validated.height);
    drawAsset(pose.getContext('2d'), images.get(`pose:${blink.pose.id}`), blink.pose);
    pose.getContext('2d').globalCompositeOperation = 'destination-in';
    pose.getContext('2d').drawImage(canvas, 0, 0);
    ctx.globalAlpha = state.blink;
    ctx.drawImage(pose, 0, 0);
    ctx.globalAlpha = 1;
  }
  const gesture = validated.controls.find((control) => control.kind === 'headGesture');
  if (gesture && state.headRadians !== 0) {
    const mask = preparedMaskCoverage(validated, images, gesture.mask);
    const head = createCanvas(validated.width, validated.height);
    const headContext = head.getContext('2d');
    headContext.drawImage(canvas, 0, 0);
    headContext.globalCompositeOperation = 'destination-in';
    headContext.drawImage(mask, 0, 0);
    ctx.globalCompositeOperation = 'destination-out';
    ctx.drawImage(mask, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
    const pivot = gesture.mask.pivot?.point || subject.pivot?.point || { x: subject.bounds.x + subject.bounds.width / 2, y: subject.bounds.y + subject.bounds.height / 2 };
    ctx.save();
    ctx.translate(pivot.x, pivot.y);
    ctx.rotate(state.headRadians);
    ctx.translate(-pivot.x, -pivot.y);
    ctx.drawImage(head, 0, 0);
    ctx.restore();
  }
  // Neither a supplied blink pose nor a rotated supplied head region may grow
  // beyond the authoritative separated-subject silhouette.
  ctx.globalCompositeOperation = 'destination-in';
  ctx.drawImage(silhouette, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
  return canvas;
}

function anchorPoint(scene, anchor) {
  if (anchor.position) return anchor.position.point;
  const landmark = (scene.subjectLandmarks || []).filter((candidate) => candidate.id === anchor.landmarkId);
  if (landmark.length !== 1) throw new MotionInputError(`Effect anchor '${anchor.id}' lacks its supplied landmark source.`);
  return landmark[0].position.point;
}

function rotatePoint(point, pivot, radians) {
  const x = point.x - pivot.x;
  const y = point.y - pivot.y;
  return {
    x: pivot.x + x * Math.cos(radians) - y * Math.sin(radians),
    y: pivot.y + x * Math.sin(radians) + y * Math.cos(radians),
  };
}

/** Returns an effect source point after the same supported subject transforms as its pixels. */
function effectAnchorPoint(validated, state, control, images) {
  let point = anchorPoint(validated.scene, control.anchor);
  if (control.source.id !== validated.subjectLayerId) return point;
  const gesture = validated.controls.find((candidate) => candidate.kind === 'headGesture' && candidate.subject.id === control.source.id);
  const coverage = gesture && preparedMaskCoverage(validated, images, gesture.mask);
  const selected = coverage && point.x >= 0 && point.y >= 0 && point.x < coverage.width && point.y < coverage.height &&
    coverage.getContext('2d').getImageData(Math.floor(point.x), Math.floor(point.y), 1, 1).data[3] > 0;
  if (gesture && selected) {
    const subject = gesture.subject;
    const pivot = gesture.mask.pivot?.point || subject.pivot?.point || {
      x: subject.bounds.x + subject.bounds.width / 2,
      y: subject.bounds.y + subject.bounds.height / 2,
    };
    point = rotatePoint(point, pivot, state.headRadians);
  }
  return { x: point.x, y: point.y + state.breathPixels };
}

function drawSteam(ctx, state, validated, images) {
  for (const control of state.steam) {
    const anchor = effectAnchorPoint(validated, state, control, images);
    const interval = 1 / control.rate;
    const lifetime = 2.6;
    const first = Math.ceil((state.time - lifetime) / interval);
    const last = Math.floor(state.time / interval);
    for (let birthIndex = first; birthIndex <= last; birthIndex += 1) {
      const birth = birthIndex * interval;
      const age = state.time - birth;
      const progress = age / lifetime;
      if (progress < 0 || progress >= 1) continue;
      const fade = smooth(age / 0.18) * (1 - smooth((progress - 0.30) / 0.70));
      const drift = (stableHash(validated.request.seed, `${control.id}:drift`, birthIndex) - 0.5) * 4 +
        4 * Math.sin(age * 3.3 + birth * 1.2);
      const x = anchor.x + drift * progress;
      const y = anchor.y - control.rise * age - 1.8 * age * age;
      const radius = 1.3 + 3.8 * progress;
      const gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
      gradient.addColorStop(0, `rgba(252,247,239,${0.21 * fade})`);
      gradient.addColorStop(0.45, `rgba(252,247,239,${0.12 * fade})`);
      gradient.addColorStop(1, 'rgba(252,247,239,0)');
      ctx.fillStyle = gradient;
      ctx.fillRect(x - radius, y - radius, radius * 2, radius * 2);
    }
  }
}

function finishedSceneBase(scene, width, height) {
  const candidates = (scene.layers || []).filter((layer) => layer.kind === 'FINISHED_SCENE' &&
    layer.bounds?.x === 0 && layer.bounds?.y === 0 && layer.bounds?.width === width && layer.bounds?.height === height);
  if (candidates.length !== 1) {
    throw new MotionInputError('Rendering without a separated subject requires exactly one supplied full-canvas FINISHED_SCENE layer.');
  }
  return candidates[0];
}

function activeOcclusionRelations(validated) {
  const sourceIds = new Set(validated.controls.filter((control) => control.kind === 'steam').map((control) => control.source.id));
  if (validated.subjectLayerId) sourceIds.add(validated.subjectLayerId);
  const seen = new Set();
  return [...(validated.scene.occlusionRelations || []), ...(validated.scenery?.occlusions || [])].filter((relation) => {
    const sceneryOcclusion = validated.scenery?.occlusions.includes(relation);
    if (!sourceIds.has(relation.occludedLayerId) && !sceneryOcclusion) return false;
    const key = `${relation.occluderLayerId}:${relation.maskId}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function drawRecordedOccluder(ctx, validated, images, relation) {
  const layer = layerById(validated.scene, relation.occluderLayerId, 'occluder layer');
  const masks = (validated.scene.masks || []).filter((mask) => mask.id === relation.maskId);
  if (masks.length !== 1) throw new MotionInputError(`Occlusion relation needs exactly one supplied mask '${relation.maskId}'.`);
  const mask = preparedMaskCoverage(validated, images, masks[0]);
  const occluder = createCanvas(validated.width, validated.height);
  const occluderContext = occluder.getContext('2d');
  drawAsset(occluderContext, images.get(`layer:${layer.id}`), layer);
  occluderContext.globalCompositeOperation = 'destination-in';
  occluderContext.drawImage(mask, 0, 0);
  ctx.drawImage(occluder, 0, 0);
}

function sectionViewport(validated, images, plane, section, frame) {
  const canvas = createCanvas(validated.width, validated.height);
  const context = canvas.getContext('2d');
  const layer = layerById(validated.scene, section.layerId, 'scenery layer');
  const offset = sectionOffset(validated.scenery, plane, section, frame);
  context.save();
  context.translate(offset.x, offset.y);
  drawAsset(context, images.get(`layer:${layer.id}`), layer);
  context.restore();
  return canvas;
}

/** Decoded source-section pixels must agree throughout every admitted offscreen handoff. */
function validateSceneryJoins(validated, images) {
  if (!validated.scenery) return;
  for (const join of validated.scenery.joins) {
    const plane = validated.scenery.planes.find((candidate) => candidate.id === join.planeId);
    const outgoing = plane.sections.find((section) => section.coverageId === join.outgoingCoverageId);
    const incoming = plane.sections.find((section) => section.coverageId === join.incomingCoverageId);
    for (const sampleFrame of join.sampleFrames) {
      const outgoingPixels = sectionViewport(validated, images, plane, outgoing, sampleFrame)
        .getContext('2d').getImageData(0, 0, validated.width, validated.height).data;
      const incomingPixels = sectionViewport(validated, images, plane, incoming, sampleFrame)
        .getContext('2d').getImageData(0, 0, validated.width, validated.height).data;
      if (!Buffer.from(outgoingPixels).equals(Buffer.from(incomingPixels))) {
        throw new MotionInputError(`Supplied scenery sections '${outgoing.coverageId}' and '${incoming.coverageId}' do not match exactly across the offscreen join at absolute frame ${join.boundaryFrame}.`);
      }
    }
  }
}

function drawScenery(ctx, validated, images, frame) {
  const scenery = validated.scenery;
  if (!scenery) return;
  const shutters = sampleOffsets(scenery);
  const total = new Uint32Array(validated.width * validated.height * 4);
  for (const shutter of shutters) {
    const layerFrame = frame + shutter;
    const sampleCanvas = createCanvas(validated.width, validated.height);
    const sampleContext = sampleCanvas.getContext('2d');
    for (const plane of scenery.planes) {
      const section = activeSection(plane, layerFrame);
      if (!section) throw new MotionInputError(`Scenery plane '${plane.id}' has no active source section at frame ${layerFrame}.`);
      const layer = layerById(validated.scene, section.layerId, 'scenery layer');
      const offset = sectionOffset(scenery, plane, section, layerFrame);
      sampleContext.save();
      sampleContext.translate(offset.x, offset.y);
      drawAsset(sampleContext, images.get(`layer:${layer.id}`), layer);
      sampleContext.restore();
    }
    const pixels = sampleContext.getImageData(0, 0, validated.width, validated.height).data;
    for (let index = 0; index < pixels.length; index += 1) total[index] += pixels[index];
  }
  const result = ctx.createImageData(validated.width, validated.height);
  for (let index = 0; index < result.data.length; index += 1) result.data[index] = Math.round(total[index] / shutters.length);
  ctx.putImageData(result, 0, 0);
}

/** Renders one frame from absolute time. The caller owns persistence of its bytes. */
function renderFrame(validated, images, frame) {
  if (!Number.isSafeInteger(frame) || frame < validated.startFrame || frame >= validated.startFrame + validated.frameCount) {
    throw new MotionInputError('Frame is outside this bounded invocation range.');
  }
  const canvas = createCanvas(validated.width, validated.height);
  const ctx = canvas.getContext('2d');
  const dynamic = validated.subjectLayerId;
  const occlusions = activeOcclusionRelations(validated);
  const foreground = new Set(occlusions.map((relation) => relation.occluderLayerId));
  const sceneryLayerIds = new Set(validated.scenery?.planes.flatMap((plane) => plane.sections.map((section) => section.layerId)));
  const base = dynamic ? cleanBase(validated.scene, validated.width, validated.height) : null;
  if (base) {
    drawAsset(ctx, images.get(`layer:${base.id}`), base);
    for (const layer of validated.scene.layers || []) {
      if (layer.id === base.id || layer.id === dynamic || foreground.has(layer.id) || sceneryLayerIds.has(layer.id) || layer.kind === 'FINISHED_SCENE') continue;
      drawAsset(ctx, images.get(`layer:${layer.id}`), layer);
    }
  } else {
    const finished = finishedSceneBase(validated.scene, validated.width, validated.height);
    drawAsset(ctx, images.get(`layer:${finished.id}`), finished);
  }
  drawScenery(ctx, validated, images, frame);
  const state = motionAt(validated, frame);
  if (dynamic) {
    const subject = subjectCanvas(validated, images, state);
    ctx.drawImage(subject, 0, state.breathPixels);
  }
  // A source-attached effect belongs behind recorded foreground occluders.
  drawSteam(ctx, state, validated, images);
  for (const relation of occlusions) drawRecordedOccluder(ctx, validated, images, relation);
  return { canvas, state };
}

function canonicalRequest(validated) {
  const request = validated.request;
  return JSON.stringify({ schema: request.schema, preparedScene: request.preparedScene, seed: request.seed, fps: request.fps, canvas: request.canvas, frameRange: request.frameRange, controls: request.controls, scenery: request.scenery, initialState: request.initialState });
}

function canonicalSeriesRequest(validated) {
  const request = validated.request;
  return JSON.stringify({ schema: request.schema, preparedScene: request.preparedScene, seed: request.seed, fps: request.fps, canvas: request.canvas, controls: request.controls, scenery: request.scenery });
}

function claimOutputDirectory(outputDirectory, requestHash) {
  const absolute = path.resolve(outputDirectory);
  const marker = path.join(absolute, '.melotrail-controlled-motion-owner.json');
  if (fs.existsSync(absolute)) {
    if (!fs.statSync(absolute).isDirectory() || !fs.existsSync(marker)) {
      throw new MotionInputError('Output directory already exists but is not an owned controlled-motion output directory.');
    }
    const existing = JSON.parse(fs.readFileSync(marker, 'utf8'));
    if (existing.tool !== TOOL_ID || existing.requestSha256 !== requestHash) {
      throw new MotionInputError('Output directory belongs to a different controlled-motion request.');
    }
  } else {
    fs.mkdirSync(absolute, { recursive: true, mode: 0o700 });
    fs.writeFileSync(marker, `${JSON.stringify({ tool: TOOL_ID, requestSha256: requestHash }, null, 2)}\n`, { mode: 0o600 });
  }
  return absolute;
}

/** Writes at most the admitted range, one PNG at a time, then a receipt. */
async function renderBounded(request, projectRoot, outputDirectory, options = {}) {
  const validated = validateRequest(request);
  const requestSha256 = sha256(Buffer.from(canonicalRequest(validated)));
  const seriesSha256 = validated.scenery ? sha256(Buffer.from(canonicalSeriesRequest(validated))) : requestSha256;
  const images = await loadPreparedImages(validated, projectRoot);
  validateSceneryJoins(validated, images);
  // Source confinement and every immutable pin are verified before any output
  // directory, owner marker, frame, or receipt is written.
  const outputAlreadyExisted = fs.existsSync(path.resolve(outputDirectory));
  const output = claimOutputDirectory(outputDirectory, seriesSha256);
  const frames = [];
  const written = [];
  try {
    for (let offset = 0; offset < validated.frameCount; offset += 1) {
      if (options.isCancelled?.()) throw new MotionInputError('Controlled-motion render was cancelled before the next frame.');
      const frame = validated.startFrame + offset;
      const fileName = `frame-${String(frame).padStart(8, '0')}.png`;
      const target = path.join(output, fileName);
      if (fs.existsSync(target)) throw new MotionInputError(`Refusing to overwrite existing output frame '${fileName}'.`);
      const rendered = renderFrame(validated, images, frame);
      const bytes = rendered.canvas.toBuffer('image/png');
      fs.writeFileSync(target, bytes, { flag: 'wx', mode: 0o600 });
      written.push(target);
      frames.push({ frame, timeSeconds: rendered.state.time, file: fileName, sha256: sha256(bytes) });
      // Canvas/native finalizers and asynchronous cancellation need an event-loop
      // turn. A synchronous whole-chunk loop retains native frame allocations
      // until it returns, even though only one PNG is intentionally buffered.
      // A resolved Promise/microtask is insufficient; drawing and PNG bytes stay
      // unchanged, and the native supervisor still enforces the same RSS limit.
      await new Promise((resolve) => setImmediate(resolve));
    }
    // The final yield is also cancellable; do not publish a success receipt
    // after an asynchronous cancellation on the last frame.
    if (options.isCancelled?.()) throw new MotionInputError('Controlled-motion render was cancelled before receipt publication.');
  } catch (error) {
    if (options.isCancelled?.()) {
      // Only files this invocation created are reaped. Existing chunks, inputs,
      // and unrelated output-directory entries are never touched.
      for (const target of written) fs.rmSync(target, { force: true });
      if (!outputAlreadyExisted) {
        fs.rmSync(path.join(output, '.melotrail-controlled-motion-owner.json'), { force: true });
        try { fs.rmdirSync(output); } catch { /* an unrelated concurrent entry remains */ }
      }
    }
    throw error;
  }
  const finalState = validated.scenery ? stateForFrame(validated.scenery, validated.request.seed,
    validated.startFrame + validated.frameCount - 1) : null;
  const receipt = {
    schema: 'melotrail-controlled-motion-receipt-v1', tool: { id: TOOL_ID, version: TOOL_VERSION },
    requestSha256,
    sourcePins: [...new Set([
      ...(validated.scene.layers || []),
      ...(validated.scene.poses || []),
      ...(validated.scene.masks || []),
    ].map((component) => component.image.artifact.sha256))].sort(),
    frameRange: { startFrame: validated.startFrame, frameCount: validated.frameCount, fps: validated.fps },
    ...(validated.scenery ? { scenery: {
      mode: validated.scenery.mode,
      viewport: validated.scenery.viewport,
      planes: validated.scenery.planes.map((plane) => ({
        id: plane.id,
        depthFactor: plane.depthFactor,
        sections: plane.sections.map((section) => ({
          coverageId: section.coverageId,
          worldX: section.worldX,
          worldY: section.worldY,
          startFrame: section.startFrame,
          endFrameExclusive: section.endFrameExclusive,
        })),
      })),
      joins: validated.scenery.joins,
      initialState: validated.scenery.initialState,
      finalState,
    } } : {}),
    frames, limitations: [
      'This bounded compositor moves only supplied prepared-layer pixels; it does not regenerate surrounding scene pixels.',
      'Blinking uses supplied aligned pose pixels; head gesture uses only a supplied HEAD_REGION mask.',
      'Steam is a source-anchored soft rise/fade effect. Scenery uses only supplied declared-coverage pixels, exact supplied section overlaps, and a bounded camera track.',
      'Drinking and page-turning are not supported by this motion set.',
    ],
  };
  const receiptName = validated.scenery
    ? `render-receipt-${String(validated.startFrame).padStart(8, '0')}-${String(validated.frameCount).padStart(8, '0')}.json`
    : 'render-receipt.json';
  fs.writeFileSync(path.join(output, receiptName), `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
  return receipt;
}

function parseCli(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (!['--request', '--project-root', '--output', '--cancel-file'].includes(key)) throw new MotionInputError(`Unknown argument '${key}'.`);
    const value = argv[index + 1];
    if (!value || value.startsWith('--') || values.has(key)) throw new MotionInputError(`Argument '${key}' needs one value.`);
    values.set(key, value); index += 1;
  }
  for (const required of ['--request', '--project-root', '--output']) if (!values.has(required)) throw new MotionInputError(`Missing ${required}.`);
  return values;
}

async function main(argv) {
  const values = parseCli(argv);
  const request = JSON.parse(fs.readFileSync(values.get('--request'), 'utf8'));
  const cancelFile = values.get('--cancel-file');
  const receipt = await renderBounded(request, values.get('--project-root'), values.get('--output'), {
    isCancelled: () => Boolean(cancelFile && fs.existsSync(cancelFile)),
  });
  const receiptName = receipt.scenery
    ? `render-receipt-${String(receipt.frameRange.startFrame).padStart(8, '0')}-${String(receipt.frameRange.frameCount).padStart(8, '0')}.json`
    : 'render-receipt.json';
  process.stdout.write(`${JSON.stringify({ receipt: receiptName, frames: receipt.frames.length })}\n`);
}

module.exports = { LIMITS, MotionInputError, blinkClosedAmount, drawAsset, drawScenery, effectAnchorPoint, frameTime, maskCoverageCanvas, motionAt, renderBounded, renderFrame, validateRequest, validateSceneryJoins };

if (require.main === module) main(process.argv.slice(2)).catch((error) => {
  process.stderr.write(`${error.name || 'Error'}: ${error.message}\n`);
  process.exitCode = 1;
});
