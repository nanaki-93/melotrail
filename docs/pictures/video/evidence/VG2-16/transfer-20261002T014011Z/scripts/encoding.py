"""Match AVFrame colour tags to already converted Rec.709 transport pixels."""
FILTER = 'scale=iw:ih:in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p,setparams=range=limited:color_primaries=bt709:color_trc=bt709:colorspace=bt709'


def require_frame_tags(argv):
    if argv.count('-vf') != 1 or argv[argv.index('-vf') + 1] != FILTER:
        raise RuntimeError('Missing exact converted-pixel frame colour contract')
    for name, value in [('-pix_fmt', 'yuv420p'), ('-colorspace', 'bt709'), ('-color_trc', 'bt709'), ('-color_primaries', 'bt709'), ('-color_range', 'tv')]:
        if argv.count(name) != 1 or argv[argv.index(name) + 1] != value:
            raise RuntimeError('Frame and output colour contract differ')
