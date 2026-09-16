'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { createCanvas, loadImage } = require('@napi-rs/canvas');
const {
  MotionInputError, drawAsset, effectAnchorPoint, maskCoverageCanvas, motionAt, renderBounded, renderFrame, validateRequest,
} = require('./render.cjs');

const copy = (value) => JSON.parse(JSON.stringify(value));
const comparisonMode = require.main === module && process.argv[2] === '--list-production-comparisons';
const selectedFixtureRoot = comparisonMode ? process.argv[3] : process.env.MELOTRAIL_MOTION_FIXTURE_ROOT;
if (!selectedFixtureRoot || !path.isAbsolute(selectedFixtureRoot)) {
  throw new Error('Set MELOTRAIL_MOTION_FIXTURE_ROOT to the absolute build/video-motion-fixtures path emitted by VideoMotionDescriptorFixtureTest.');
}
const fixtureRoot = path.resolve(selectedFixtureRoot);

function fixture(name) {
  const root = path.join(fixtureRoot, name);
  const metadata = JSON.parse(fs.readFileSync(path.join(root, 'fixture-metadata.json'), 'utf8'));
  const request = JSON.parse(fs.readFileSync(path.join(root, metadata.request), 'utf8'));
  const projectRoot = path.join(root, metadata.projectRoot);
  assert.equal(metadata.name, name);
  assert.equal(request.preparedScene.id.id, `controlled-${name}`);
  return { name, root, projectRoot, metadata, request };
}

async function imagesFor(sample) {
  const images = new Map();
  for (const [prefix, items] of [['layer', sample.request.preparedScene.layers], ['pose', sample.request.preparedScene.poses], ['mask', sample.request.preparedScene.masks]]) {
    for (const item of items || []) {
      images.set(`${prefix}:${item.id}`, await loadImage(path.join(sample.projectRoot, item.image.artifact.relativePath)));
    }
  }
  return images;
}

function components(scene) {
  return [
    ...(scene.layers || []).map((item) => [`layer:${item.id}`, item]),
    ...(scene.poses || []).map((item) => [`pose:${item.id}`, item]),
    ...(scene.masks || []).map((item) => [`mask:${item.id}`, item]),
  ];
}

function alphaBounds(canvas) {
  const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
  let minX = canvas.width; let minY = canvas.height; let maxX = -1; let maxY = -1;
  for (let offset = 0; offset < pixels.length; offset += 4) {
    if (!pixels[offset + 3]) continue;
    const index = offset / 4; const x = index % canvas.width; const y = Math.floor(index / canvas.width);
    minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
  }
  return maxX < 0 ? null : { x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1 };
}

function colorBounds(canvas, color) {
  const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
  const selected = createCanvas(canvas.width, canvas.height);
  const selectedContext = selected.getContext('2d');
  const selectedPixels = selectedContext.createImageData(canvas.width, canvas.height);
  for (let offset = 0; offset < pixels.length; offset += 4) {
    if (color.every((value, index) => pixels[offset + index] === value)) selectedPixels.data[offset + 3] = 255;
  }
  selectedContext.putImageData(selectedPixels, 0, 0);
  return alphaBounds(selected);
}

function componentCanvas(sample, images, key, item) {
  const canvas = createCanvas(sample.request.canvas.width, sample.request.canvas.height);
  drawAsset(canvas.getContext('2d'), images.get(key), item);
  return canvas;
}

function pixel(canvas, x, y) {
  return [...canvas.getContext('2d').getImageData(x, y, 1, 1).data];
}

function rgbaAt(pixels, index) {
  const offset = index * 4;
  return [...pixels.slice(offset, offset + 4)];
}

function expectedFilteredAlphaBounds(item, opaqueBounds) {
  if (!item.image.hasTransparentPixels) return opaqueBounds;
  const scaleX = item.transform.scaleX;
  const scaleY = item.transform.scaleY;
  assert.ok(scaleX === 1 || scaleX === 2, 'fixture x scale is explicitly unit or doubled');
  assert.ok(scaleY === 1 || scaleY === 2, 'fixture y scale is explicitly unit or doubled');
  // Canvas smooth scaling gives a doubled transparent-edge cutout one filtered
  // support pixel on each side. This is still wholly inside its V18a bounds.
  const fringeX = scaleX === 2 ? 1 : 0;
  const fringeY = scaleY === 2 ? 1 : 0;
  return {
    x: opaqueBounds.x - fringeX,
    y: opaqueBounds.y - fringeY,
    width: opaqueBounds.width + fringeX * 2,
    height: opaqueBounds.height + fringeY * 2,
  };
}

function expectedFilteredOpaqueCore(item, opaqueBounds) {
  if (!item.image.hasTransparentPixels || (item.transform.scaleX === 1 && item.transform.scaleY === 1)) return opaqueBounds;
  return {
    x: opaqueBounds.x + 1,
    y: opaqueBounds.y + 1,
    width: opaqueBounds.width - 2,
    height: opaqueBounds.height - 2,
  };
}

function componentSolidColor(sample, key) {
  if (key === 'layer:subject') return sample.metadata.colors.subject;
  if (key === 'layer:foreground') return sample.metadata.colors.foreground;
  if (key === 'pose:blink-pose') return sample.metadata.colors.pose;
  if (key.startsWith('mask:')) return sample.metadata.maskColors[key.slice('mask:'.length)];
  return null;
}

function expectedMaskCoverageCanvas(sample, images, mask) {
  const coverage = componentCanvas(sample, images, `mask:${mask.id}`, mask);
  const context = coverage.getContext('2d');
  const pixels = context.getImageData(0, 0, coverage.width, coverage.height);
  const alphaCutout = mask.alpha.transparentPixels > 0 &&
    mask.alpha.opaquePixels + mask.alpha.translucentPixels > 0;
  if (!alphaCutout) {
    for (let offset = 0; offset < pixels.data.length; offset += 4) {
      if (pixels.data[offset + 3] > 0) {
        assert.equal(pixels.data[offset], pixels.data[offset + 1], 'opaque grayscale fixture has equal red and green samples');
        assert.equal(pixels.data[offset], pixels.data[offset + 2], 'opaque grayscale fixture has equal red and blue samples');
      }
      // The fixture contract is grayscale, so its sample value directly is
      // coverage. This reference deliberately does not use production's RGB
      // luminance conversion.
      pixels.data[offset + 3] = Math.round(pixels.data[offset + 3] * pixels.data[offset] / 255);
    }
    context.putImageData(pixels, 0, 0);
  }
  return coverage;
}

function recordedOccluderCanvas(sample, images, relation) {
  const layer = sample.request.preparedScene.layers.find((item) => item.id === relation.occluderLayerId);
  const mask = sample.request.preparedScene.masks.find((item) => item.id === relation.maskId);
  const occluder = componentCanvas(sample, images, `layer:${layer.id}`, layer);
  const maskCanvas = expectedMaskCoverageCanvas(sample, images, mask);
  occluder.getContext('2d').globalCompositeOperation = 'destination-in';
  occluder.getContext('2d').drawImage(maskCanvas, 0, 0);
  return occluder;
}

function expectedHeadGestureFrame(sample, images, validated, state) {
  const subject = sample.request.preparedScene.layers.find((item) => item.id === validated.subjectLayerId);
  const gesture = validated.controls.find((control) => control.kind === 'headGesture');
  const clean = sample.request.preparedScene.layers.find((item) => item.id === 'clean-background');
  const subjectCanvas = componentCanvas(sample, images, `layer:${subject.id}`, subject);
  const silhouette = componentCanvas(sample, images, `layer:${subject.id}`, subject);
  const coverage = expectedMaskCoverageCanvas(sample, images, gesture.mask);
  const head = createCanvas(subjectCanvas.width, subjectCanvas.height);
  const headContext = head.getContext('2d');
  headContext.drawImage(subjectCanvas, 0, 0);
  headContext.globalCompositeOperation = 'destination-in';
  headContext.drawImage(coverage, 0, 0);
  const subjectContext = subjectCanvas.getContext('2d');
  subjectContext.globalCompositeOperation = 'destination-out';
  subjectContext.drawImage(coverage, 0, 0);
  subjectContext.globalCompositeOperation = 'source-over';
  const pivot = gesture.mask.pivot.point;
  subjectContext.save();
  subjectContext.translate(pivot.x, pivot.y);
  subjectContext.rotate(state.headRadians);
  subjectContext.translate(-pivot.x, -pivot.y);
  subjectContext.drawImage(head, 0, 0);
  subjectContext.restore();
  subjectContext.globalCompositeOperation = 'destination-in';
  subjectContext.drawImage(silhouette, 0, 0);
  subjectContext.globalCompositeOperation = 'source-over';
  const expected = componentCanvas(sample, images, `layer:${clean.id}`, clean);
  expected.getContext('2d').drawImage(subjectCanvas, 0, 0);
  return expected;
}

function withoutRecordedForeground(request, relation) {
  const result = copy(request);
  result.preparedScene.layers = result.preparedScene.layers.filter((item) => item.id !== relation.occluderLayerId);
  result.preparedScene.masks = result.preparedScene.masks.filter((item) => item.id !== relation.maskId);
  result.preparedScene.occlusionRelations = result.preparedScene.occlusionRelations.filter((item) =>
    item.occluderLayerId !== relation.occluderLayerId && item.maskId !== relation.maskId);
  result.preparedScene.depthRelations = result.preparedScene.depthRelations.filter((item) =>
    item.nearerLayerId !== relation.occluderLayerId && item.fartherLayerId !== relation.occluderLayerId);
  return result;
}

function sourceOverPixel(backdrop, overlay) {
  const canvas = createCanvas(1, 1);
  const ctx = canvas.getContext('2d');
  const base = ctx.createImageData(1, 1);
  base.data.set(backdrop);
  ctx.putImageData(base, 0, 0);
  const top = createCanvas(1, 1);
  const topContext = top.getContext('2d');
  const topPixel = topContext.createImageData(1, 1);
  topPixel.data.set(overlay);
  topContext.putImageData(topPixel, 0, 0);
  ctx.drawImage(top, 0, 0);
  return [...ctx.getImageData(0, 0, 1, 1).data];
}

function steamOnlyCentroid(withSteam, withoutSteam) {
  const source = withSteam.getContext('2d').getImageData(0, 0, withSteam.width, withSteam.height).data;
  const base = withoutSteam.getContext('2d').getImageData(0, 0, withoutSteam.width, withoutSteam.height).data;
  let weight = 0; let sumX = 0; let sumY = 0;
  for (let offset = 0; offset < source.length; offset += 4) {
    const difference = Math.abs(source[offset] - base[offset]) + Math.abs(source[offset + 1] - base[offset + 1]) +
      Math.abs(source[offset + 2] - base[offset + 2]) + Math.abs(source[offset + 3] - base[offset + 3]);
    if (!difference) continue;
    const index = offset / 4; const x = index % withSteam.width; const y = Math.floor(index / withSteam.width);
    weight += difference; sumX += x * difference; sumY += y * difference;
  }
  assert.ok(weight > 0, 'steam must own visible pixels relative to the same frame without steam');
  return { x: sumX / weight, y: sumY / weight };
}

function matchingMotionFrame(validated) {
  for (let frame = validated.startFrame; frame < validated.startFrame + validated.frameCount; frame += 1) {
    const state = motionAt(validated, frame);
    if (Math.abs(state.breathPixels) > 0.25 && Math.abs(state.headRadians) > 0.002) return frame;
  }
  throw new Error('Production fixture did not produce a measurable bounded subject motion frame.');
}

function matchingHeadFrame(validated) {
  for (let frame = validated.startFrame; frame < validated.startFrame + validated.frameCount; frame += 1) {
    if (Math.abs(motionAt(validated, frame).headRadians) > 0.01) return frame;
  }
  throw new Error('Production fixture did not produce a measurable head-gesture frame.');
}

if (!comparisonMode) {
test('renders actual importer descriptors at exact unit and nonunit placements for every component', async (t) => {
  for (const name of ['unit-scale', 'nonunit-scale']) {
    const sample = fixture(name);
    const temporary = fs.mkdtempSync(path.join(os.tmpdir(), `melotrail-${name}-`));
    t.after(() => fs.rmSync(temporary, { recursive: true, force: true }));
    const images = await imagesFor(sample);
    for (const [key, item] of components(sample.request.preparedScene)) {
      assert.ok(item.transform, `${key} must retain its production importer transform`);
      const image = images.get(key);
      assert.equal(item.transform.translateX, item.bounds.x, `${key} x placement`);
      assert.equal(item.transform.translateY, item.bounds.y, `${key} y placement`);
      assert.equal(item.transform.scaleX * image.width, item.bounds.width, `${key} width scale`);
      assert.equal(item.transform.scaleY * image.height, item.bounds.height, `${key} height scale`);
      const component = componentCanvas(sample, images, key, item);
      const opaqueBounds = sample.metadata.componentPixelBounds[key];
      assert.deepEqual(alphaBounds(component), expectedFilteredAlphaBounds(item, opaqueBounds), `${key} must be placed once with exact smooth-filter support`);
      const solidColor = componentSolidColor(sample, key);
      if (solidColor) {
        const centerX = opaqueBounds.x + Math.floor(opaqueBounds.width / 2);
        const centerY = opaqueBounds.y + Math.floor(opaqueBounds.height / 2);
        assert.deepEqual(pixel(component, centerX, centerY), solidColor, `${key} retains an exact opaque interior at its importer placement`);
      }
    }

    const placementRequest = copy(sample.request);
    placementRequest.frameRange.frameCount = 3;
    placementRequest.controls = [copy(sample.request.controls.find((control) => control.kind === 'blink'))];
    placementRequest.controls[0].amount = 0;
    const validated = validateRequest(placementRequest);
    const frame = renderFrame(validated, images, validated.startFrame).canvas;
    const subjectLayer = sample.request.preparedScene.layers.find((layer) => layer.id === 'subject');
    assert.deepEqual(colorBounds(frame, sample.metadata.colors.subject),
      expectedFilteredOpaqueCore(subjectLayer, sample.metadata.componentPixelBounds['layer:subject']),
      'subject opaque core uses the importer placement once with smooth scaling');
    const foregroundLayer = sample.request.preparedScene.layers.find((layer) => layer.id === 'foreground');
    const foregroundCore = expectedFilteredOpaqueCore(foregroundLayer, sample.metadata.componentPixelBounds['layer:foreground']);
    assert.deepEqual(pixel(frame, foregroundCore.x, foregroundCore.y), sample.metadata.colors.foreground, 'recorded opaque foreground core occludes the placed subject');
    assert.equal(frame.toBuffer('image/png').equals(renderFrame(validated, images, validated.startFrame).canvas.toBuffer('image/png')), true, 'absolute frame and seed repeat exactly');

    const receipt = await renderBounded(placementRequest, sample.projectRoot, path.join(temporary, 'render'));
    assert.equal(receipt.frames.length, 3);
    assert.equal(receipt.frames[0].frame, 90);
    assert.equal(fs.existsSync(path.join(temporary, 'render', 'render-receipt.json')), true);
  }
});

test('uses actual importer mask representations for exact head selection and alpha occlusion coverage', async () => {
  const sample = fixture('opaque-head-black-alpha');
  const images = await imagesFor(sample);
  const headMask = sample.request.preparedScene.masks.find((mask) => mask.id === 'head-mask');
  const occlusionMask = sample.request.preparedScene.masks.find((mask) => mask.id === 'foreground-occlusion');
  assert.equal(headMask.alpha.transparentPixels, 0, 'opaque grayscale classification comes from original importer alpha');
  assert.ok(occlusionMask.alpha.transparentPixels > 0, 'black alpha cutout retains original importer separation');

  for (const mask of [headMask, occlusionMask]) {
    const actual = maskCoverageCanvas(
      sample.request.canvas.width, sample.request.canvas.height, mask, images.get(`mask:${mask.id}`),
    );
    const expected = expectedMaskCoverageCanvas(sample, images, mask);
    assert.equal(actual.toBuffer('image/png').equals(expected.toBuffer('image/png')), true,
      `${mask.id} coverage follows its independently interpreted V18a representation`);
    const selected = sample.metadata.maskCoverageBounds[mask.id];
    assert.equal(pixel(actual, selected.x + Math.floor(selected.width / 2), selected.y + Math.floor(selected.height / 2))[3], 255,
      `${mask.id} selected interior has exact opaque coverage`);
  }
  const rawHead = componentCanvas(sample, images, 'mask:head-mask', headMask);
  const headBounds = headMask.bounds;
  assert.deepEqual(pixel(rawHead, headBounds.x, headBounds.y), [0, 0, 0, 255], 'opaque head-mask background is real black image content');
  const normalizedHead = maskCoverageCanvas(sample.request.canvas.width, sample.request.canvas.height, headMask, images.get('mask:head-mask'));
  assert.equal(pixel(normalizedHead, headBounds.x, headBounds.y)[3], 0, 'black opaque head-mask content is unselected');
  const rawOcclusion = componentCanvas(sample, images, 'mask:foreground-occlusion', occlusionMask);
  const occlusionSelection = sample.metadata.maskCoverageBounds['foreground-occlusion'];
  const occlusionX = occlusionSelection.x + Math.floor(occlusionSelection.width / 2);
  const occlusionY = occlusionSelection.y + Math.floor(occlusionSelection.height / 2);
  assert.deepEqual(pixel(rawOcclusion, occlusionX, occlusionY), [0, 0, 0, 255], 'alpha-selected occlusion pixels are actually black');

  const relation = sample.request.preparedScene.occlusionRelations[0];
  const headRequest = withoutRecordedForeground(sample.request, relation);
  headRequest.controls = [copy(sample.request.controls.find((control) => control.kind === 'headGesture'))];
  const validated = validateRequest(headRequest);
  const frame = matchingHeadFrame(validated);
  const state = motionAt(validated, frame);
  const actual = renderFrame(validated, images, frame).canvas;
  const expected = expectedHeadGestureFrame({ ...sample, request: headRequest }, images, validated, state);
  assert.equal(actual.toBuffer('image/png').equals(expected.toBuffer('image/png')), true,
    'head extraction and removal consume the independently interpreted grayscale coverage exactly');

  const staticRequest = copy(headRequest);
  staticRequest.controls[0].amplitudeDegrees = 0;
  const staticFrame = renderFrame(validateRequest(staticRequest), images, frame).canvas;
  const expectedPixels = expected.getContext('2d').getImageData(0, 0, expected.width, expected.height).data;
  const actualPixels = actual.getContext('2d').getImageData(0, 0, actual.width, actual.height).data;
  const staticPixels = staticFrame.getContext('2d').getImageData(0, 0, staticFrame.width, staticFrame.height).data;
  const coveragePixels = expectedMaskCoverageCanvas(sample, images, headMask)
    .getContext('2d').getImageData(0, 0, actual.width, actual.height).data;
  let selectedChanged = -1;
  let unselectedPreserved = -1;
  for (let index = 0; index < actualPixels.length / 4; index += 1) {
    const selected = coveragePixels[index * 4 + 3] === 255;
    const expectedPixel = rgbaAt(expectedPixels, index);
    const staticPixel = rgbaAt(staticPixels, index);
    if (selected && selectedChanged < 0 && !expectedPixel.every((value, channel) => value === staticPixel[channel])) {
      selectedChanged = index;
    }
    if (!selected && unselectedPreserved < 0 && staticPixel.every((value, channel) => value === sample.metadata.colors.subject[channel]) &&
        expectedPixel.every((value, channel) => value === staticPixel[channel])) {
      unselectedPreserved = index;
    }
  }
  assert.ok(selectedChanged >= 0, 'fixture contains a selected head pixel changed by the gesture');
  assert.deepEqual(rgbaAt(actualPixels, selectedChanged), rgbaAt(expectedPixels, selectedChanged), 'selected head pixel moves exactly');
  assert.ok(unselectedPreserved >= 0, 'fixture contains an unselected visible head-area pixel');
  assert.deepEqual(rgbaAt(actualPixels, unselectedPreserved), rgbaAt(staticPixels, unselectedPreserved), 'unselected head pixel stays exact');

  const steam = validated.controls.find((control) => control.kind === 'steam');
  assert.equal(steam, undefined, 'head-only comparison has no effect control');
  const fullValidated = validateRequest(sample.request);
  const fullState = motionAt(fullValidated, frame);
  const fullSteam = fullValidated.controls.find((control) => control.kind === 'steam');
  const selectedAnchor = effectAnchorPoint(fullValidated, fullState, fullSteam, images);
  assert.notEqual(selectedAnchor.x, sample.metadata.anchor.x, 'selected anchor follows head rotation');
  const unselectedRequest = copy(sample.request);
  const unselected = sample.metadata.maskCoverageBounds['head-mask'];
  const unselectedPoint = { x: unselected.x - 2, y: unselected.y + Math.floor(unselected.height / 2) };
  unselectedRequest.preparedScene.subjectLandmarks[0].position.point = unselectedPoint;
  const unselectedValidated = validateRequest(unselectedRequest);
  const unselectedSteam = unselectedValidated.controls.find((control) => control.kind === 'steam');
  assert.deepEqual(
    effectAnchorPoint(unselectedValidated, fullState, unselectedSteam, images),
    { x: unselectedPoint.x, y: unselectedPoint.y + fullState.breathPixels },
    'anchor in black grayscale content receives breathing but no head rotation',
  );
});

test('effect-only static source preserves the finished composition and recorded foreground occlusion', async () => {
  const sample = fixture('effect-only-static');
  const images = await imagesFor(sample);
  const validated = validateRequest(sample.request);
  assert.equal(validated.subjectLayerId, null, 'effect-only admission must not invent dynamic subject motion');
  const frameNumber = 100;
  const rendered = renderFrame(validated, images, frameNumber).canvas;
  const finished = sample.request.preparedScene.layers.find((layer) => layer.kind === 'FINISHED_SCENE');
  const baseline = componentCanvas(sample, images, `layer:${finished.id}`, finished);
  const source = rendered.getContext('2d').getImageData(0, 0, rendered.width, rendered.height).data;
  const base = baseline.getContext('2d').getImageData(0, 0, baseline.width, baseline.height).data;
  let differences = 0;
  for (let offset = 0; offset < source.length; offset += 4) {
    if (source[offset] === base[offset] && source[offset + 1] === base[offset + 1] &&
        source[offset + 2] === base[offset + 2] && source[offset + 3] === base[offset + 3]) continue;
    differences += 1;
    const index = offset / 4; const x = index % rendered.width; const y = Math.floor(index / rendered.width);
    assert.ok(x >= sample.metadata.anchor.x - 14 && x <= sample.metadata.anchor.x + 14 && y <= sample.metadata.anchor.y + 6,
      `effect-only rendering changed a non-steam pixel at ${x},${y}`);
  }
  assert.ok(differences > 0, 'requested static-source steam is visible');
  const subject = sample.metadata.subjectBounds;
  assert.deepEqual(pixel(rendered, subject.x + 3, subject.y + 3), sample.metadata.colors.subject, 'finished composition retains its subject');
  const foreground = sample.metadata.componentPixelBounds['layer:foreground'];
  for (let y = foreground.y; y < foreground.y + foreground.height; y += 1) {
    for (let x = foreground.x; x < foreground.x + foreground.width; x += 1) {
      assert.deepEqual(pixel(rendered, x, y), pixel(baseline, x, y), 'foreground pixels remain identical over steam');
    }
  }
});

test('steam follows its transformed subject source while the production foreground remains on top', async () => {
  for (const fixtureName of ['nonunit-scale', 'opaque-head-black-alpha']) {
    const sample = fixture(fixtureName);
    const images = await imagesFor(sample);
    const validated = validateRequest(sample.request);
    const frame = matchingMotionFrame(validated);
    const state = motionAt(validated, frame);
    const steam = validated.controls.find((control) => control.kind === 'steam');
    const anchored = effectAnchorPoint(validated, state, steam, images);
    const original = sample.request.preparedScene.subjectLandmarks[0].position.point;
    assert.notDeepEqual(anchored, original, 'head-region source receives head and breathing transforms');

    const withoutSteam = copy(sample.request);
    withoutSteam.controls = withoutSteam.controls.filter((control) => control.kind !== 'steam');
    const staticRequest = copy(sample.request);
    staticRequest.controls = [
      { ...copy(sample.request.controls.find((control) => control.kind === 'blink')), amount: 0 },
      copy(sample.request.controls.find((control) => control.kind === 'steam')),
    ];
    const staticWithoutSteam = copy(staticRequest);
    staticWithoutSteam.controls = staticWithoutSteam.controls.filter((control) => control.kind !== 'steam');
    const movingWithSteam = renderFrame(validated, images, frame).canvas;
    const movingWithoutSteam = renderFrame(validateRequest(withoutSteam), images, frame).canvas;
    const staticWithSteam = renderFrame(validateRequest(staticRequest), images, frame).canvas;
    const staticWithout = renderFrame(validateRequest(staticWithoutSteam), images, frame).canvas;
    const movingCentroid = steamOnlyCentroid(movingWithSteam, movingWithoutSteam);
    const staticCentroid = steamOnlyCentroid(staticWithSteam, staticWithout);
    assert.ok(Math.abs((movingCentroid.x - staticCentroid.x) - (anchored.x - original.x)) < 1.5, 'steam follows moved source x');
    assert.ok(Math.abs((movingCentroid.y - staticCentroid.y) - (anchored.y - original.y)) < 1.5, 'steam follows moved source y');
    const relation = sample.request.preparedScene.occlusionRelations[0];
    const occluder = recordedOccluderCanvas(sample, images, relation);
    const underWithSteamRequest = withoutRecordedForeground(sample.request, relation);
    const underWithoutSteamRequest = withoutRecordedForeground(withoutSteam, relation);
    const underWithSteam = renderFrame(validateRequest(underWithSteamRequest), images, frame).canvas;
    const underWithoutSteam = renderFrame(validateRequest(underWithoutSteamRequest), images, frame).canvas;
    const withPixels = movingWithSteam.getContext('2d').getImageData(0, 0, movingWithSteam.width, movingWithSteam.height).data;
    const withoutPixels = movingWithoutSteam.getContext('2d').getImageData(0, 0, movingWithoutSteam.width, movingWithoutSteam.height).data;
    const overPixels = occluder.getContext('2d').getImageData(0, 0, occluder.width, occluder.height).data;
    const underWithPixels = underWithSteam.getContext('2d').getImageData(0, 0, underWithSteam.width, underWithSteam.height).data;
    const underWithoutPixels = underWithoutSteam.getContext('2d').getImageData(0, 0, underWithoutSteam.width, underWithoutSteam.height).data;
    let opaquePixels = 0;
    let blendedEdge = null;
    for (let index = 0; index < overPixels.length / 4; index += 1) {
      const overlay = rgbaAt(overPixels, index);
      if (overlay[3] === 255) {
        opaquePixels += 1;
        assert.deepEqual(rgbaAt(withPixels, index), overlay, 'fully opaque foreground exactly hides steam');
        assert.deepEqual(rgbaAt(withoutPixels, index), overlay, 'fully opaque foreground is independent of steam');
      } else if (overlay[3] > 0 && overlay[3] < 255 &&
                 !rgbaAt(underWithPixels, index).every((value, channel) => value === rgbaAt(underWithoutPixels, index)[channel])) {
        blendedEdge = { index, overlay };
      }
    }
    assert.ok(opaquePixels > 0, 'fixture supplies a fully opaque recorded foreground interior');
    assert.ok(blendedEdge, 'fixture supplies a filtered translucent foreground edge over visible steam');
    const edgeWithUnder = rgbaAt(underWithPixels, blendedEdge.index);
    const edgeWithoutUnder = rgbaAt(underWithoutPixels, blendedEdge.index);
    const expectedWith = sourceOverPixel(edgeWithUnder, blendedEdge.overlay);
    const expectedWithout = sourceOverPixel(edgeWithoutUnder, blendedEdge.overlay);
    assert.deepEqual(rgbaAt(withPixels, blendedEdge.index), expectedWith, 'filtered edge composites the recorded foreground over steam');
    assert.deepEqual(rgbaAt(withoutPixels, blendedEdge.index), expectedWithout, 'filtered edge composites the same foreground without steam');
    assert.notDeepEqual(expectedWith, expectedWithout, 'translucent antialiasing retains the attenuated backdrop instead of pretending to be opaque');
  }
});

test('rejects duplicate subject controls and keeps every admitted state inside declared bounds', () => {
  const sample = fixture('unit-scale');
  for (const kind of ['blink', 'breathing', 'headGesture']) {
    const request = copy(sample.request);
    const duplicate = copy(request.controls.find((control) => control.kind === kind));
    duplicate.id = `${kind}-duplicate`;
    request.controls.push(duplicate);
    assert.throws(() => validateRequest(request), /at most one/);
  }
  const validated = validateRequest(sample.request);
  assert.equal(validated.controls.filter((control) => control.kind === 'blink').length, 1);
  assert.equal(validated.controls.filter((control) => control.kind === 'breathing').length, 1);
  assert.equal(validated.controls.filter((control) => control.kind === 'headGesture').length, 1);
  for (let frame = validated.startFrame; frame < validated.startFrame + validated.frameCount; frame += 1) {
    const state = motionAt(validated, frame);
    assert.ok(state.blink >= 0 && state.blink <= 1);
    assert.ok(state.breathPixels >= 0 && state.breathPixels <= 2);
    assert.ok(Math.abs(state.headRadians * 180 / Math.PI) <= 2 + 1e-9);
  }
});

test('rejects malformed production descriptors and unsupported face controls', () => {
  const sample = fixture('unit-scale');
  for (const field of ['id', 'source', 'dependencies', 'createdAt']) {
    const request = copy(sample.request); delete request.preparedScene[field];
    assert.throws(() => validateRequest(request), MotionInputError, `missing V18a ${field} must be rejected`);
  }
  const semanticMaskRequest = copy(sample.request);
  semanticMaskRequest.preparedScene.occlusionRelations[0].maskId = 'head-mask';
  assert.throws(() => validateRequest(semanticMaskRequest), /occlusion relation/);
  const outOfBoundsLandmarkRequest = copy(sample.request);
  outOfBoundsLandmarkRequest.preparedScene.subjectLandmarks[0].position.point.x = sample.metadata.subjectBounds.x + sample.metadata.subjectBounds.width + 1;
  assert.throws(() => validateRequest(outOfBoundsLandmarkRequest), /landmark/);
  const withoutPose = copy(sample.request); withoutPose.preparedScene.poses = [];
  assert.throws(() => validateRequest(withoutPose), /pose 'blink-pose'/);
  const withoutHead = copy(sample.request); withoutHead.preparedScene.masks = withoutHead.preparedScene.masks.filter((mask) => mask.id !== 'head-mask');
  assert.throws(() => validateRequest(withoutHead), /HEAD_REGION mask/);
});

test('rejects matching-digest artifact links and linked roots before creating output', async (t) => {
  const sample = fixture('unit-scale');
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'melotrail-motion-links-'));
  t.after(() => fs.rmSync(temporary, { recursive: true, force: true }));
  const subject = sample.request.preparedScene.layers.find((layer) => layer.id === 'subject');
  const source = path.join(sample.projectRoot, subject.image.artifact.relativePath);
  const external = path.join(temporary, 'matching-subject.png');
  fs.copyFileSync(source, external);

  const finalLink = path.join(sample.projectRoot, 'linked-subject.png');
  fs.symlinkSync(external, finalLink);
  const finalRequest = copy(sample.request);
  finalRequest.frameRange.frameCount = 1;
  finalRequest.preparedScene.layers.find((layer) => layer.id === 'subject').image.artifact.relativePath = 'linked-subject.png';
  const finalOutput = path.join(temporary, 'final-link-output');
  await assert.rejects(renderBounded(finalRequest, sample.projectRoot, finalOutput), /symbolic links/);
  assert.equal(fs.existsSync(finalOutput), false, 'final-link rejection writes no owner marker or frame');
  fs.unlinkSync(finalLink);

  const outsideParent = path.join(temporary, 'outside-parent');
  fs.mkdirSync(outsideParent);
  fs.copyFileSync(source, path.join(outsideParent, 'subject.png'));
  const parentLink = path.join(sample.projectRoot, 'linked-parent');
  fs.symlinkSync(outsideParent, parentLink);
  const parentRequest = copy(sample.request);
  parentRequest.frameRange.frameCount = 1;
  parentRequest.preparedScene.layers.find((layer) => layer.id === 'subject').image.artifact.relativePath = 'linked-parent/subject.png';
  const parentOutput = path.join(temporary, 'parent-link-output');
  await assert.rejects(renderBounded(parentRequest, sample.projectRoot, parentOutput), /symbolic links/);
  assert.equal(fs.existsSync(parentOutput), false, 'parent-link rejection writes no owner marker or frame');
  fs.unlinkSync(parentLink);

  const rootLink = path.join(temporary, 'project-link');
  fs.symlinkSync(sample.projectRoot, rootLink);
  const rootOutput = path.join(temporary, 'root-link-output');
  await assert.rejects(renderBounded(parentRequest, rootLink, rootOutput), /project root/);
  assert.equal(fs.existsSync(rootOutput), false, 'linked-root rejection writes no owner marker or frame');
});
}

if (comparisonMode) {
  const set = JSON.parse(fs.readFileSync(path.join(fixtureRoot, 'fixture-set.json'), 'utf8'));
  process.stdout.write(`${JSON.stringify({
    fixtures: fixtureRoot,
    renderCommands: set.fixtures.map((name) => {
      const root = path.join(fixtureRoot, name);
      return `node tools/video-motion/render.cjs --request ${path.join(root, 'request.json')} --project-root ${path.join(root, 'project')} --output ${path.join(root, 'comparison-render')}`;
    }),
  }, null, 2)}\n`);
}
