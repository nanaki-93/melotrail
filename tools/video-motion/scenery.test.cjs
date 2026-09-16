'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { SCENERY_SCHEMA, SceneryInputError, activeSection, sectionOffset, stateForFrame, validateScenery } = require('./scenery.cjs');

const fixtureRoot = process.env.MELOTRAIL_MOTION_FIXTURE_ROOT;
if (!fixtureRoot || !path.isAbsolute(fixtureRoot)) {
  throw new Error('Set MELOTRAIL_MOTION_FIXTURE_ROOT to the absolute build/video-motion-fixtures path emitted by VideoMotionDescriptorFixtureTest.');
}
const copy = (value) => JSON.parse(JSON.stringify(value));

function wideFixture() {
  return JSON.parse(fs.readFileSync(path.join(fixtureRoot, 'wide-scenery', 'request.json'), 'utf8'));
}

function baseScene() {
  const bounds = { coordinateSpaceId: 'scene', x: 0, y: 0, width: 600, height: 100 };
  return {
    layers: [
      { id: 'near', kind: 'SCENERY', bounds, reviewStatus: 'UNREVIEWED' },
      { id: 'far', kind: 'SCENERY', bounds, reviewStatus: 'UNREVIEWED' },
    ],
    masks: [],
    sceneryCoverage: [
      { id: 'near-cover', layerId: 'near', bounds, reviewStatus: 'UNREVIEWED' },
      { id: 'far-cover', layerId: 'far', bounds, reviewStatus: 'UNREVIEWED' },
    ],
    motionCapabilities: [
      { id: 'near-x', targetType: 'SCENERY_COVERAGE', targetId: 'near-cover', control: 'TRANSLATE_X', unit: 'PIXELS', minimum: -600, maximum: 0, reviewStatus: 'UNREVIEWED' },
      { id: 'far-x', targetType: 'SCENERY_COVERAGE', targetId: 'far-cover', control: 'TRANSLATE_X', unit: 'PIXELS', minimum: -600, maximum: 0, reviewStatus: 'UNREVIEWED' },
    ],
    depthRelations: [{ nearerLayerId: 'near', fartherLayerId: 'far', reviewStatus: 'UNREVIEWED' }],
    occlusionRelations: [],
  };
}

function depthRequest(planes = [
  { id: 'near-plane', sections: [{ coverageId: 'near-cover', worldX: 0, worldY: 0, startFrame: 0, endFrameExclusive: 11 }] },
  { id: 'far-plane', sections: [{ coverageId: 'far-cover', worldX: 0, worldY: 0, startFrame: 0, endFrameExclusive: 11 }] },
]) {
  return {
    schema: SCENERY_SCHEMA,
    mode: 'moving',
    viewport: { coordinateSpaceId: 'scene', x: 0, y: 0, width: 100, height: 100 },
    camera: { startFrame: 0, durationFrames: 11, travelXPixels: 100, travelYPixels: 0, motionBlurSamples: 3, shutterFraction: 0.5 },
    planes,
  };
}

test('admits real importer source sections with absolute world placement and deterministic handoff state', () => {
  const request = wideFixture();
  const admitted = validateScenery(request.preparedScene, request.canvas, request.fps, request.frameRange,
    request.scenery, request.seed, request.initialState);
  assert.equal(admitted.planes.length, 1);
  assert.equal(admitted.planes[0].sections.length, 2, 'two distinct imported source assets remain same-depth sections');
  assert.equal(admitted.joins.length, 1);
  assert.equal(admitted.joins[0].boundaryFrame, 450);
  assert.equal(activeSection(admitted.planes[0], 449).coverageId, 'scenery-a-coverage');
  assert.equal(activeSection(admitted.planes[0], 450).coverageId, 'scenery-b-coverage');
  assert.deepEqual(stateForFrame(admitted, request.seed, 449).planes.map((plane) => plane.coverageId), ['scenery-a-coverage']);
  assert.deepEqual(stateForFrame(admitted, request.seed, 450).planes.map((plane) => plane.coverageId), ['scenery-b-coverage']);
});

test('derives canonical far-to-near order and faster near travel independent of request order', () => {
  const scene = baseScene();
  const first = validateScenery(scene, { coordinateSpaceId: 'scene', width: 100, height: 100 }, 30,
    { startFrame: 0, frameCount: 11 }, depthRequest(), 41);
  const reversed = validateScenery(scene, { coordinateSpaceId: 'scene', width: 100, height: 100 }, 30,
    { startFrame: 0, frameCount: 11 }, depthRequest(copy(depthRequest().planes).reverse()), 41);
  assert.deepEqual(first.planes.map((plane) => plane.id), ['far-plane', 'near-plane']);
  assert.deepEqual(reversed.planes.map((plane) => plane.id), ['far-plane', 'near-plane']);
  const far = first.planes[0]; const near = first.planes[1];
  assert.ok(Math.abs(sectionOffset(first, near, near.sections[0], 10).x) > Math.abs(sectionOffset(first, far, far.sections[0], 10).x),
    'nearer plane moves faster on the one camera trajectory');
});

test('requires and verifies complete receipt state at a source-section boundary', () => {
  const request = wideFixture();
  const firstRequest = copy(request); firstRequest.frameRange = { startFrame: 0, frameCount: 450 };
  const first = validateScenery(firstRequest.preparedScene, firstRequest.canvas, firstRequest.fps, firstRequest.frameRange,
    firstRequest.scenery, firstRequest.seed);
  const finalState = stateForFrame(first, request.seed, 449);
  const resumedRequest = copy(request); resumedRequest.frameRange = { startFrame: 450, frameCount: 300 };
  const resumed = validateScenery(resumedRequest.preparedScene, resumedRequest.canvas, resumedRequest.fps, resumedRequest.frameRange,
    resumedRequest.scenery, resumedRequest.seed, finalState);
  assert.equal(resumed.initialState.stateSha256, finalState.stateSha256);
  assert.equal(stateForFrame(resumed, request.seed, 450).planes[0].coverageId, 'scenery-b-coverage');
  assert.throws(() => validateScenery(resumedRequest.preparedScene, resumedRequest.canvas, resumedRequest.fps, resumedRequest.frameRange,
    resumedRequest.scenery, resumedRequest.seed), /prior receipt finalState/);
});

test('admits scenery-only static chunks but rejects uncovered moving ranges', () => {
  const request = wideFixture();
  const staticScenery = copy(request.scenery);
  staticScenery.mode = 'static';
  staticScenery.camera = { startFrame: 0, durationFrames: 1, travelXPixels: 0, travelYPixels: 0, motionBlurSamples: 1 };
  staticScenery.planes[0].sections = [{ coverageId: 'scenery-a-coverage', worldX: 0, worldY: 0, startFrame: 900, endFrameExclusive: 1200 }];
  const admitted = validateScenery(request.preparedScene, request.canvas, request.fps,
    { startFrame: 900, frameCount: 300 }, staticScenery, request.seed);
  assert.equal(admitted.mode, 'static');
  const hole = copy(request.scenery);
  hole.planes[0].sections[1].worldX = 400;
  assert.throws(() => validateScenery(request.preparedScene, request.canvas, request.fps,
    request.frameRange, hole, request.seed), /join|hole|capability/);
});

test('rejected scenery dependencies fail pure admission for every authoritative component kind', () => {
  const singlePlane = depthRequest([{ id: 'far-plane', sections: [{ coverageId: 'far-cover', worldX: 0, worldY: 0, startFrame: 0, endFrameExclusive: 11 }] }]);
  const validate = (scene, raw = singlePlane) => validateScenery(scene, { coordinateSpaceId: 'scene', width: 100, height: 100 }, 30,
    { startFrame: 0, frameCount: 11 }, raw, 41);

  const rejectedLayer = baseScene(); rejectedLayer.layers.find((layer) => layer.id === 'far').reviewStatus = 'REJECTED';
  assert.throws(() => validate(rejectedLayer), /layer 'far'.*rejected/);
  const rejectedCoverage = baseScene(); rejectedCoverage.sceneryCoverage.find((coverage) => coverage.id === 'far-cover').reviewStatus = 'REJECTED';
  assert.throws(() => validate(rejectedCoverage), /coverage 'far-cover'.*rejected/);
  const rejectedCapability = baseScene(); rejectedCapability.motionCapabilities.find((capability) => capability.id === 'far-x').reviewStatus = 'REJECTED';
  assert.throws(() => validate(rejectedCapability), /capability 'far-x'.*rejected/);
  const rejectedDepth = baseScene(); rejectedDepth.depthRelations[0].reviewStatus = 'REJECTED';
  assert.throws(() => validate(rejectedDepth, depthRequest()), /depth relation.*rejected/);

  const rejectedOcclusion = baseScene();
  rejectedOcclusion.layers.push({ id: 'frame', kind: 'FOREGROUND', bounds: rejectedOcclusion.layers[0].bounds, reviewStatus: 'UNREVIEWED' });
  rejectedOcclusion.masks.push({ id: 'window-mask', layerIds: ['frame', 'far'], reviewStatus: 'UNREVIEWED' });
  rejectedOcclusion.occlusionRelations.push({ occluderLayerId: 'frame', occludedLayerId: 'far', maskId: 'window-mask', reviewStatus: 'REJECTED' });
  assert.throws(() => validate(rejectedOcclusion), /occlusion relation.*rejected/);
});
