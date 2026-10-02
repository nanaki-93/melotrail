"""Read-only inspection of this bounded export's sample tables; no media retry."""
from pathlib import Path
from collections import Counter
import hashlib, json, struct

run = Path(__file__).resolve().parents[1]
movie = run / 'review/tabi-window-watch-1080p.mp4'
data = movie.read_bytes()
tables, top = {}, []

def walk(start, end, parent=''):
    while start < end:
        size, kind = struct.unpack('>I4s', data[start:start + 8])
        kind = kind.decode('ascii')
        payload = start + 8
        if size == 1:
            size = struct.unpack('>Q', data[payload:payload + 8])[0]
            payload += 8
        assert size >= payload - start and start + size <= end
        if not parent:
            top.append({'type': kind, 'offset': start, 'bytes': size})
        if kind in ('moov', 'trak', 'mdia', 'minf', 'stbl', 'edts'):
            walk(payload, start + size, parent + '/' + kind)
        elif kind in ('stts', 'ctts'):
            assert data[payload] == 0
            count = struct.unpack('>I', data[payload + 4:payload + 8])[0]
            tables[kind] = [struct.unpack('>II', data[payload + 8 + i * 8:payload + 16 + i * 8]) for i in range(count)]
        elif kind == 'mdhd':
            assert data[payload] == 0
            tables['mediaTimescale'], tables['mediaDuration'] = struct.unpack('>II', data[payload + 12:payload + 20])
        elif kind == 'elst':
            tables['hasEditList'] = True
        start += size
    assert start == end

walk(0, len(data))
durations = [value for count, value in tables['stts'] for _ in range(count)]
offsets = [value for count, value in tables['ctts'] for _ in range(count)]
assert len(durations) == len(offsets) == 129
dts, presentation = 0, []
for delta, offset in zip(durations, offsets):
    presentation.append(dts + offset)
    dts += delta
ordered = sorted(presentation)
intervals = Counter(b - a for a, b in zip(ordered, ordered[1:]))
facts = json.JSONDecoder().raw_decode((run / 'checks/probe.log').read_text())[0]
assert len(facts['streams']) == 1
stream = facts['streams'][0]
assert (stream['codec_name'], stream['profile'], stream['pix_fmt'], stream['width'], stream['height']) == ('h264', 'High', 'yuv420p', 1920, 1080)
assert all(stream[k] == 'bt709' for k in ('color_space', 'color_transfer', 'color_primaries'))
assert int(stream['nb_read_frames']) == 129
assert stream['r_frame_rate'] == '25/1'
result = {
    'file': str(movie), 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data),
    'width': 1920, 'height': 1080, 'codec': 'h264', 'profile': 'High', 'pixelFormat': 'yuv420p',
    'colour': 'BT.709 limited range', 'audioStreams': 0, 'fullyCountedFrames': 129,
    'nominalFrameRate': stream['r_frame_rate'], 'averageFrameRate': stream['avg_frame_rate'],
    'streamDurationSeconds': stream['duration'], 'startSeconds': stream['start_time'],
    'videoBitrate': int(stream['bit_rate']), 'pixelAspect': stream.get('sample_aspect_ratio', 'not signalled'),
    'fastStart': next(x['offset'] for x in top if x['type'] == 'moov') < next(x['offset'] for x in top if x['type'] == 'mdat'),
    'hasEditList': tables.get('hasEditList', False), 'sampleTables': tables,
    'presentationTicks': ordered, 'presentationIntervalHistogram': dict(intervals),
    'expectedTimescale': 12800, 'expectedIntervalTicks': 512,
    'exact25FpsCadencePass': set(intervals) == {512},
    'disposition': 'LOCAL_1080P_QUALITY_PREVIEW_ONLY',
    'limitation': 'Encoder completed and all frames decode, but sample timing and declared duration differ from the admitted 5.16s uniform-25fps target. Preserve this sole encode; no automatic re-encode or loop/upload-ready claim.'
}
(run / 'checks/container.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({k: v for k, v in result.items() if k not in ('sampleTables', 'presentationTicks')}))
