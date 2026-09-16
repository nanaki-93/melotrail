/*
 * V19b scenery is a deterministic compositor input, never a source of new
 * pixels. One camera trajectory drives explicitly placed depth planes. A plane
 * may hand off between supplied overlapping source sections at an absolute
 * frame; the renderer verifies those overlaps before it claims any output.
 */
'use strict';

const crypto = require('node:crypto');

const SCENERY_SCHEMA = 'melotrail-rigid-scenery-v1';
const LIMITS = Object.freeze({
  maximumCameraDurationFrames: 3600,
  maximumMotionBlurSamples: 8,
  maximumTravelPixels: 16384,
  maximumPlanes: 16,
  maximumSections: 32,
});

class SceneryInputError extends Error {
  constructor(message) { super(message); this.name = 'SceneryInputError'; }
}

const number = (value, label, minimum = -Infinity, maximum = Infinity) => {
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new SceneryInputError(`${label} must be a finite number in ${minimum}..${maximum}.`);
  }
  return value;
};

const safeId = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
const rejected = (component) => component?.reviewStatus === 'REJECTED';

const rectangle = (value, label) => {
  if (!value || !safeId.test(value.coordinateSpaceId || '')) throw new SceneryInputError(`${label} needs a coordinate space.`);
  return {
    coordinateSpaceId: value.coordinateSpaceId,
    x: number(value.x, `${label}.x`),
    y: number(value.y, `${label}.y`),
    width: number(value.width, `${label}.width`, Number.EPSILON),
    height: number(value.height, `${label}.height`, Number.EPSILON),
  };
};

function sameRectangle(first, second) {
  return first.coordinateSpaceId === second.coordinateSpaceId && first.x === second.x && first.y === second.y &&
    first.width === second.width && first.height === second.height;
}

function contains(outer, inner) {
  return outer.coordinateSpaceId === inner.coordinateSpaceId && inner.x >= outer.x && inner.y >= outer.y &&
    inner.x + inner.width <= outer.x + outer.width && inner.y + inner.height <= outer.y + outer.height;
}

function translated(rect, x, y) { return { ...rect, x: rect.x + x, y: rect.y + y }; }

/* Exact union coverage for axis-aligned declared opaque rectangles. */
function coversViewport(rectangles, viewport) {
  const clipped = rectangles.map((rect) => ({
    x: Math.max(rect.x, viewport.x), y: Math.max(rect.y, viewport.y),
    right: Math.min(rect.x + rect.width, viewport.x + viewport.width),
    bottom: Math.min(rect.y + rect.height, viewport.y + viewport.height),
  })).filter((rect) => rect.x < rect.right && rect.y < rect.bottom);
  const xEdges = [...new Set([viewport.x, viewport.x + viewport.width, ...clipped.flatMap((rect) => [rect.x, rect.right])])].sort((a, b) => a - b);
  for (let index = 0; index < xEdges.length - 1; index += 1) {
    const left = xEdges[index]; const right = xEdges[index + 1];
    if (right <= left) continue;
    const intervals = clipped.filter((rect) => rect.x <= left && rect.right >= right)
      .map((rect) => [rect.y, rect.bottom]).sort((a, b) => a[0] - b[0]);
    let cursor = viewport.y;
    for (const [start, end] of intervals) {
      if (start > cursor) return false;
      cursor = Math.max(cursor, end);
      if (cursor >= viewport.y + viewport.height) break;
    }
    if (cursor < viewport.y + viewport.height) return false;
  }
  return true;
}

function cameraAt(scenery, frame) {
  const relative = frame - scenery.startFrame;
  const denominator = Math.max(1, scenery.durationFrames - 1);
  const progress = scenery.mode === 'static' ? 0 : Math.max(0, Math.min(1, relative / denominator));
  return { x: scenery.travelXPixels * progress, y: scenery.travelYPixels * progress, progress };
}

function activeSection(plane, frame) {
  const exact = plane.sections.find((section) => section.startFrame <= frame && frame < section.endFrameExclusive);
  if (exact) return exact;
  if (frame < plane.sections[0].startFrame) return plane.sections[0];
  if (frame >= plane.sections.at(-1).endFrameExclusive) return plane.sections.at(-1);
  return null;
}

function sectionOffset(scenery, plane, section, frame) {
  const camera = cameraAt(scenery, frame);
  const canonical = (value) => value === 0 ? 0 : value;
  return {
    x: canonical(section.worldX - section.coverage.x - camera.x * plane.depthFactor),
    y: canonical(section.worldY - section.coverage.y - camera.y * plane.depthFactor),
  };
}

function sectionRectangle(scenery, plane, section, frame) {
  const offset = sectionOffset(scenery, plane, section, frame);
  return translated(section.coverage, offset.x, offset.y);
}

function sampleOffsets(scenery) {
  if (scenery.motionBlurSamples === 1) return [0];
  return Array.from({ length: scenery.motionBlurSamples }, (_, sample) =>
    ((sample + 0.5) / scenery.motionBlurSamples - 0.5) * scenery.shutterFraction);
}

function stateForFrame(scenery, seed, frame) {
  const camera = cameraAt(scenery, frame);
  const planes = scenery.planes.map((plane) => {
    const section = activeSection(plane, frame);
    return {
      id: plane.id,
      depthFactor: plane.depthFactor,
      coverageId: section?.coverageId || null,
      worldX: section?.worldX ?? null,
      worldY: section?.worldY ?? null,
      offset: section ? sectionOffset(scenery, plane, section, frame) : null,
    };
  });
  const trajectory = {
    mode: scenery.mode,
    startFrame: scenery.startFrame,
    durationFrames: scenery.durationFrames,
    travelXPixels: scenery.travelXPixels,
    travelYPixels: scenery.travelYPixels,
    motionBlurSamples: scenery.motionBlurSamples,
    shutterFraction: scenery.shutterFraction,
    planes: scenery.planes.map((plane) => ({
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
  };
  const trajectorySha256 = crypto.createHash('sha256').update(JSON.stringify(trajectory)).digest('hex');
  const payload = JSON.stringify({ seed, frame, camera, trajectorySha256, planes });
  return { frame, seed, camera, trajectorySha256, planes, stateSha256: crypto.createHash('sha256').update(payload).digest('hex') };
}

function validateInitialState(initialState, scenery, seed, startFrame) {
  if (!initialState) {
    if (scenery.mode === 'moving' && startFrame > scenery.startFrame) {
      throw new SceneryInputError('A resumed moving-scenery range must pass the prior receipt finalState as initialState.');
    }
    return null;
  }
  if (!Number.isSafeInteger(initialState.frame) || initialState.frame !== startFrame - 1 || initialState.seed !== seed ||
      typeof initialState.stateSha256 !== 'string' || !/^[0-9a-f]{64}$/.test(initialState.stateSha256)) {
    throw new SceneryInputError('initialState must be the immediately previous V19b finalState with the same seed.');
  }
  const expected = stateForFrame(scenery, seed, initialState.frame);
  if (expected.stateSha256 !== initialState.stateSha256) throw new SceneryInputError('initialState does not match the declared camera trajectory, plane activation, and seed.');
  return expected;
}

function depthFactors(scene, planes) {
  if (planes.length === 1) return new Map([[planes[0].id, 1]]);
  const planeByLayer = new Map();
  for (const plane of planes) for (const section of plane.sections) planeByLayer.set(section.layerId, plane.id);
  const related = (scene.depthRelations || []).filter((relation) =>
    planeByLayer.has(relation.nearerLayerId) && planeByLayer.has(relation.fartherLayerId));
  for (const relation of related) {
    if (rejected(relation)) throw new SceneryInputError(`Selected depth relation '${relation.nearerLayerId}>${relation.fartherLayerId}' has a rejected prepared-scene review.`);
    if (planeByLayer.get(relation.nearerLayerId) === planeByLayer.get(relation.fartherLayerId)) {
      throw new SceneryInputError('Source sections in one scenery plane must have the same depth; remove their conflicting V18a depth relation.');
    }
  }
  if (!related.length) throw new SceneryInputError('Moving scenery with multiple depth planes needs non-rejected V18a depthRelations between those planes.');
  const adjacent = new Map(planes.map((plane) => [plane.id, new Set()]));
  const outgoing = new Map(planes.map((plane) => [plane.id, new Set()]));
  for (const relation of related) {
    const nearer = planeByLayer.get(relation.nearerLayerId);
    const farther = planeByLayer.get(relation.fartherLayerId);
    adjacent.get(nearer).add(farther); adjacent.get(farther).add(nearer); outgoing.get(nearer).add(farther);
  }
  const connected = new Set([planes[0].id]);
  for (const current of connected) for (const next of adjacent.get(current)) connected.add(next);
  if (connected.size !== planes.length) throw new SceneryInputError('Every moving scenery depth plane must be connected by non-rejected V18a depthRelations.');
  const visiting = new Set(); const ranks = new Map();
  function rank(id) {
    if (ranks.has(id)) return ranks.get(id);
    if (visiting.has(id)) throw new SceneryInputError('Moving scenery cannot use cyclic V18a depthRelations.');
    visiting.add(id);
    const value = Math.max(0, ...[...outgoing.get(id)].map((next) => rank(next) + 1));
    visiting.delete(id); ranks.set(id, value); return value;
  }
  for (const plane of planes) rank(plane.id);
  const minimum = Math.min(...ranks.values());
  return new Map(planes.map((plane) => [plane.id, 1 + ranks.get(plane.id) - minimum]));
}

function validatePlaneSections(rawPlanes, coverageById, layerById, evaluationStart, evaluationEnd) {
  if (!Array.isArray(rawPlanes) || rawPlanes.length < 1 || rawPlanes.length > LIMITS.maximumPlanes) {
    throw new SceneryInputError(`Scenery needs 1..${LIMITS.maximumPlanes} explicit depth planes.`);
  }
  if (new Set(rawPlanes.map((plane) => plane?.id)).size !== rawPlanes.length || rawPlanes.some((plane) => !safeId.test(plane?.id || ''))) {
    throw new SceneryInputError('Scenery plane IDs must be unique safe identifiers.');
  }
  const seenCoverage = new Set(); const seenLayers = new Set(); let sectionCount = 0;
  return rawPlanes.map((rawPlane) => {
    if (!Array.isArray(rawPlane.sections) || rawPlane.sections.length < 1) throw new SceneryInputError(`Scenery plane '${rawPlane.id}' needs supplied source sections.`);
    const sections = rawPlane.sections.map((rawSection, index) => {
      sectionCount += 1;
      if (sectionCount > LIMITS.maximumSections) throw new SceneryInputError(`Scenery supports at most ${LIMITS.maximumSections} supplied source sections.`);
      const coverage = coverageById.get(rawSection?.coverageId);
      if (!coverage || !safeId.test(rawSection?.coverageId || '') || seenCoverage.has(rawSection.coverageId)) {
        throw new SceneryInputError('Every scenery source section must name one unique declared V18a coverage ID.');
      }
      seenCoverage.add(rawSection.coverageId);
      if (rejected(coverage)) throw new SceneryInputError(`Scenery coverage '${coverage.id}' has a rejected prepared-scene review.`);
      const layer = layerById.get(coverage.layerId);
      if (!layer || seenLayers.has(coverage.layerId)) throw new SceneryInputError('Every scenery source section must identify one distinct prepared layer.');
      seenLayers.add(coverage.layerId);
      if (rejected(layer)) throw new SceneryInputError(`Scenery layer '${layer.id}' has a rejected prepared-scene review.`);
      const bounds = rectangle(coverage.bounds, `Scenery coverage '${coverage.id}'`);
      if (!contains(layer.bounds, bounds)) throw new SceneryInputError(`Scenery coverage '${coverage.id}' must remain inside its prepared layer placement.`);
      const startFrame = rawSection.startFrame;
      const endFrameExclusive = rawSection.endFrameExclusive;
      if (!Number.isSafeInteger(startFrame) || !Number.isSafeInteger(endFrameExclusive) || startFrame < 0 || endFrameExclusive <= startFrame) {
        throw new SceneryInputError(`Scenery section '${coverage.id}' needs a non-negative absolute startFrame before endFrameExclusive.`);
      }
      return {
        index, coverageId: coverage.id, layerId: coverage.layerId, coverage: bounds,
        worldX: number(rawSection.worldX, `Scenery section '${coverage.id}'.worldX`, -LIMITS.maximumTravelPixels, LIMITS.maximumTravelPixels),
        worldY: number(rawSection.worldY, `Scenery section '${coverage.id}'.worldY`, -LIMITS.maximumTravelPixels, LIMITS.maximumTravelPixels),
        startFrame, endFrameExclusive,
      };
    }).sort((first, second) => first.startFrame - second.startFrame || first.coverageId.localeCompare(second.coverageId));
    if (sections[0].startFrame > evaluationStart || sections.at(-1).endFrameExclusive < evaluationEnd ||
        sections.some((section, index) => index > 0 && sections[index - 1].endFrameExclusive !== section.startFrame)) {
      throw new SceneryInputError(`Scenery plane '${rawPlane.id}' source activations must cover the complete absolute trajectory with exact consecutive boundaries.`);
    }
    return { id: rawPlane.id, sections };
  });
}

function compatibleOcclusions(scene, selectedLayerIds) {
  const selected = new Set(selectedLayerIds);
  const layers = new Map((scene.layers || []).map((layer) => [layer.id, layer]));
  const masks = new Map((scene.masks || []).map((mask) => [mask.id, mask]));
  const result = [];
  for (const relation of scene.depthRelations || []) {
    if ((selected.has(relation.nearerLayerId) || selected.has(relation.fartherLayerId)) && rejected(relation)) {
      throw new SceneryInputError(`Selected depth relation '${relation.nearerLayerId}>${relation.fartherLayerId}' has a rejected prepared-scene review.`);
    }
  }
  for (const relation of scene.occlusionRelations || []) {
    const touches = selected.has(relation.occludedLayerId) || selected.has(relation.occluderLayerId);
    if (!touches) continue;
    if (rejected(relation)) throw new SceneryInputError(`Selected scenery occlusion relation '${relation.occluderLayerId}>${relation.occludedLayerId}' has a rejected prepared-scene review.`);
    if (selected.has(relation.occluderLayerId)) {
      throw new SceneryInputError(`Selected scenery layer '${relation.occluderLayerId}' cannot project its supplied occlusion mask rigidly; use depth-plane paint order or a separately prepared static foreground.`);
    }
    if (!selected.has(relation.occludedLayerId)) continue;
    const occluder = layers.get(relation.occluderLayerId); const mask = masks.get(relation.maskId);
    if (!occluder || rejected(occluder)) throw new SceneryInputError(`Scenery occluder layer '${relation.occluderLayerId}' is missing or rejected.`);
    if (!mask || rejected(mask)) throw new SceneryInputError(`Scenery occlusion mask '${relation.maskId}' is missing or rejected.`);
    if (!mask.layerIds?.includes(relation.occluderLayerId) || !mask.layerIds?.includes(relation.occludedLayerId)) {
      throw new SceneryInputError(`Scenery occlusion mask '${relation.maskId}' must cover its static foreground and selected scenery layer.`);
    }
    result.push(relation);
  }
  return result.sort((first, second) => `${first.occluderLayerId}:${first.occludedLayerId}:${first.maskId}`.localeCompare(`${second.occluderLayerId}:${second.occludedLayerId}:${second.maskId}`));
}

function validateScenery(scene, canvas, fps, frameRange, raw, seed, initialState) {
  if (raw === undefined) return null;
  if (!raw || raw.schema !== SCENERY_SCHEMA) throw new SceneryInputError(`Scenery schema must be '${SCENERY_SCHEMA}'.`);
  const viewport = rectangle(raw.viewport, 'Scenery viewport');
  const output = { coordinateSpaceId: canvas.coordinateSpaceId, x: 0, y: 0, width: canvas.width, height: canvas.height };
  if (!sameRectangle(viewport, output)) throw new SceneryInputError('Scenery viewport must exactly name the explicit output canvas aperture.');
  const mode = raw.mode;
  if (!['static', 'moving'].includes(mode)) throw new SceneryInputError("Scenery mode must be 'static' or 'moving'.");
  const camera = raw.camera || {};
  const startFrame = Number.isSafeInteger(camera.startFrame) ? camera.startFrame : 0;
  const durationFrames = Number.isSafeInteger(camera.durationFrames) ? camera.durationFrames : 1;
  if (startFrame < 0 || durationFrames < 1 || durationFrames > LIMITS.maximumCameraDurationFrames) {
    throw new SceneryInputError(`Camera durationFrames must be 1..${LIMITS.maximumCameraDurationFrames} with a non-negative startFrame.`);
  }
  const travelXPixels = number(camera.travelXPixels ?? 0, 'Camera travelXPixels', -LIMITS.maximumTravelPixels, LIMITS.maximumTravelPixels);
  const travelYPixels = number(camera.travelYPixels ?? 0, 'Camera travelYPixels', -LIMITS.maximumTravelPixels, LIMITS.maximumTravelPixels);
  if (mode === 'static' && (travelXPixels !== 0 || travelYPixels !== 0 || durationFrames !== 1)) throw new SceneryInputError('Static scenery needs zero camera travel and durationFrames 1.');
  if (mode === 'moving' && durationFrames < 2) throw new SceneryInputError('Moving scenery needs at least two camera frames.');
  if (mode === 'moving' && travelXPixels === 0 && travelYPixels === 0) throw new SceneryInputError('Moving scenery needs non-zero explicit camera travel; a subject control is not camera motion.');
  if (mode === 'moving' && (frameRange.startFrame < startFrame || frameRange.startFrame + frameRange.frameCount > startFrame + durationFrames)) {
    throw new SceneryInputError('Requested frame range exceeds the declared camera trajectory; V19b never freezes the last frame.');
  }
  const motionBlurSamples = Number.isSafeInteger(camera.motionBlurSamples) ? camera.motionBlurSamples : (mode === 'moving' ? 3 : 1);
  if (motionBlurSamples < 1 || motionBlurSamples > LIMITS.maximumMotionBlurSamples) throw new SceneryInputError(`Camera motionBlurSamples must be 1..${LIMITS.maximumMotionBlurSamples}.`);
  const shutterFraction = number(camera.shutterFraction ?? (mode === 'moving' ? 0.5 : 0), 'Camera shutterFraction', 0, 1);
  const coverageById = new Map((scene.sceneryCoverage || []).map((coverage) => [coverage.id, coverage]));
  const layerById = new Map((scene.layers || []).map((layer) => [layer.id, layer]));
  const evaluationStart = mode === 'moving' ? startFrame : frameRange.startFrame;
  const evaluationEnd = mode === 'moving' ? startFrame + durationFrames : frameRange.startFrame + frameRange.frameCount;
  const planes = validatePlaneSections(raw.planes, coverageById, layerById, evaluationStart, evaluationEnd);
  const factors = mode === 'moving' ? depthFactors(scene, planes) : new Map(planes.map((plane) => [plane.id, 1]));
  for (const plane of planes) plane.depthFactor = factors.get(plane.id);
  planes.sort((first, second) => first.depthFactor - second.depthFactor || first.id.localeCompare(second.id));
  const selectedLayerIds = planes.flatMap((plane) => plane.sections.map((section) => section.layerId));
  const occlusions = compatibleOcclusions(scene, selectedLayerIds);
  const scenery = { mode, viewport, startFrame, durationFrames, travelXPixels, travelYPixels, motionBlurSamples, shutterFraction, planes, occlusions, fps };

  for (const plane of planes) for (const section of plane.sections) {
    if (mode === 'static' && (section.worldX !== section.coverage.x || section.worldY !== section.coverage.y)) {
      throw new SceneryInputError(`Static scenery section '${section.coverageId}' must retain its prepared V18a placement.`);
    }
    if (mode === 'moving') for (const [axis, travel, world, prepared] of [
      ['X', travelXPixels, section.worldX, section.coverage.x],
      ['Y', travelYPixels, section.worldY, section.coverage.y],
    ]) {
      if (travel === 0) {
        if (world !== prepared) throw new SceneryInputError(`Scenery section '${section.coverageId}' cannot change ${axis} placement without a camera trajectory and V18a capability on that axis.`);
        continue;
      }
      const capabilities = (scene.motionCapabilities || []).filter((capability) => capability.targetType === 'SCENERY_COVERAGE' &&
        capability.targetId === section.coverageId && capability.control === `TRANSLATE_${axis}` && capability.unit === 'PIXELS');
      if (capabilities.length !== 1) throw new SceneryInputError(`Moving scenery '${section.coverageId}' needs one declared V18a TRANSLATE_${axis} capability.`);
      if (rejected(capabilities[0])) throw new SceneryInputError(`Scenery capability '${capabilities[0].id}' has a rejected prepared-scene review.`);
      const sampleFrames = [section.startFrame, Math.max(section.startFrame, section.endFrameExclusive - 1e-6)];
      for (const frame of sampleFrames) {
        const value = sectionOffset(scenery, plane, section, frame)[axis.toLowerCase()];
        if (value < capabilities[0].minimum || value > capabilities[0].maximum) throw new SceneryInputError(`Section placement or camera travel exceeds declared V18a scenery capability '${capabilities[0].id}'.`);
      }
    }
  }

  const joins = [];
  for (const plane of planes) for (let index = 1; index < plane.sections.length; index += 1) {
    const outgoing = plane.sections[index - 1]; const incoming = plane.sections[index]; const boundaryFrame = incoming.startFrame;
    const joinSamples = [...new Set([boundaryFrame, boundaryFrame - 1e-6,
      ...sampleOffsets(scenery).flatMap((offset) => [boundaryFrame - 1 + offset, boundaryFrame + offset])])].sort((a, b) => a - b);
    for (const sampleFrame of joinSamples) {
      if (!contains(sectionRectangle(scenery, plane, outgoing, sampleFrame), viewport) ||
          !contains(sectionRectangle(scenery, plane, incoming, sampleFrame), viewport)) {
        throw new SceneryInputError(`Scenery join at absolute frame ${boundaryFrame} is not wholly offscreen; both supplied section overlaps must contain every shutter sample.`);
      }
    }
    joins.push({ planeId: plane.id, boundaryFrame, outgoingCoverageId: outgoing.coverageId, incomingCoverageId: incoming.coverageId, sampleFrames: joinSamples });
  }

  for (let frame = evaluationStart; frame < evaluationEnd; frame += 1) for (const shutter of sampleOffsets(scenery)) {
    const sampleFrame = frame + shutter;
    const visible = [];
    for (const plane of planes) {
      const section = activeSection(plane, sampleFrame);
      if (!section) throw new SceneryInputError(`Scenery plane '${plane.id}' has no active supplied section at absolute frame ${frame}.`);
      visible.push(sectionRectangle(scenery, plane, section, sampleFrame));
    }
    if (!coversViewport(visible, viewport)) throw new SceneryInputError(`Declared scenery coverage leaves a visible hole at absolute frame ${frame}; supply wider coherent source pixels or reduce camera travel.`);
  }
  scenery.joins = joins;
  const resumed = validateInitialState(initialState, scenery, seed, frameRange.startFrame);
  return Object.freeze({ ...scenery, initialState: resumed });
}

module.exports = {
  LIMITS, SCENERY_SCHEMA, SceneryInputError, activeSection, cameraAt, coversViewport,
  sampleOffsets, sectionOffset, stateForFrame, validateScenery,
};
