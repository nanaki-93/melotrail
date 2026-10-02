"""Known installed filters only; PNG cICP provides per-frame colour metadata."""
import hashlib
import json
import re
from pathlib import Path

FILTER = 'scale=iw:ih:in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p'


def require_frame_tags(argv):
    # Numeric conversion and PNG cICP checks are required by the source stage.
    if argv.count('-vf') != 1 or argv[argv.index('-vf') + 1] != FILTER:
        raise RuntimeError('Missing exact converted-pixel frame colour contract')
    for name, value in [('-pix_fmt', 'yuv420p'), ('-colorspace', 'bt709'), ('-color_trc', 'bt709'), ('-color_primaries', 'bt709'), ('-color_range', 'tv')]:
        if argv.count(name) != 1 or argv[argv.index(name) + 1] != value:
            raise RuntimeError('Frame and output colour contract differ')


def declared_filters(options):
    values=[x.split('=',1)[1] for x in options if x.startswith('--enable-filter=')]
    if '--disable-everything' not in options or len(values)!=1:
        raise RuntimeError('Unrecognized installed filter configuration')
    return set(values[0].split(','))


def require_available(argv,options):
    if '-vf' not in argv:return
    if argv.count('-vf')!=1:raise RuntimeError('Ambiguous filter graph')
    graph=argv[argv.index('-vf')+1]
    # Accept only the simple, unquoted graphs in this proof.
    if not re.fullmatch(r'[A-Za-z0-9_=,:.]+',graph):raise RuntimeError('Unsupported filter syntax')
    names={part.split('=',1)[0] for part in graph.split(',')}
    missing=names-declared_filters(options)
    if missing:raise RuntimeError('Filters absent from installed build: '+','.join(sorted(missing)))


def check_packet_filters(p):
    pin=p['installedManifest'];path=Path(pin['path'])
    if hashlib.sha256(path.read_bytes()).hexdigest()!=pin['sha256']:raise RuntimeError('Installed manifest drift')
    m=json.loads(path.read_text())
    if m['ffmpegSha256']!=pin['ffmpegSha256'] or m['sourceRevision']!=pin['sourceRevision']:raise RuntimeError('Installed tool identity drift')
    if hashlib.sha256(Path(p['tools']['ffmpeg']).read_bytes()).hexdigest()!=m['ffmpegSha256']:raise RuntimeError('Installed executable drift')
    decoders=set(next(x.split('=',1)[1] for x in m['buildOptions'] if x.startswith('--enable-decoder=')).split(','))
    if 'png' not in decoders:raise RuntimeError('PNG decoder unavailable')
    for op in p['operations']:require_available(op['argv'],m['buildOptions'])
