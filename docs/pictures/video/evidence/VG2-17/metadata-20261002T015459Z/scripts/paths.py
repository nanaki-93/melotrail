"""Small adaptation of the proved descriptor-relative destination boundary; no job owner."""
import contextlib
import hashlib
import os
import stat
import unicodedata
from pathlib import Path


def need(ok, message):
    if not ok:
        raise RuntimeError(message)


def identity(path, info):
    return {'path': str(path), 'device': info.st_dev, 'inode': info.st_ino}


def directory_info(path):
    info = path.lstat()
    need(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode), 'Unsafe directory: ' + str(path))
    return info


def capture(paths):
    folded=[unicodedata.normalize('NFC',str(v)).casefold() for v in paths.values()]
    need(len(folded)==len(set(folded)), 'Destination alias/collision')
    anchors = {}
    for value in paths.values():
        p = Path(value)
        need(p.is_absolute() and str(p) == os.path.normpath(str(p)), 'Noncanonical destination')
        need(not os.path.lexists(p), 'Destination already exists: ' + str(p))
        for a in reversed(p.parents):
            need(a.exists(), 'Missing destination parent: ' + str(a))
            anchors[str(a)] = identity(a, directory_info(a))
    return {'paths': dict(paths), 'ancestors': list(anchors.values())}


def validate(contract, paths, fresh=False):
    need(contract['paths'] == paths, 'Destination contract drift')
    anchors = {a['path']: a for a in contract['ancestors']}
    need(len(anchors) == len(contract['ancestors']), 'Duplicate destination anchors')
    for value in paths.values():
        for parent in Path(value).parents:
            need(str(parent) in anchors, 'Incomplete ancestor contract')
    for path, expected in anchors.items():
        need(identity(Path(path), directory_info(Path(path))) == expected, 'Changed ancestor: ' + path)
    for name, value in paths.items():
        p = Path(value)
        if os.path.lexists(p):
            need(not p.is_symlink(), 'Symlink destination')
            need(not fresh, 'Fresh destination exists: ' + str(p))
            need(stat.S_ISDIR(p.lstat().st_mode) if name == 'work' else stat.S_ISREG(p.lstat().st_mode), 'Wrong destination type')
    return anchors


@contextlib.contextmanager
def directory_fd(path, anchors):
    # Walk from / with O_NOFOLLOW and compare the descriptor, not a resolved spelling.
    path = Path(path)
    need(path.is_absolute() and str(path) == os.path.normpath(str(path)), 'Noncanonical directory')
    fd, current = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW), Path('/')
    try:
        def check():
            if str(current) in anchors:
                need(identity(current, os.fstat(fd)) == anchors[str(current)], 'Changed opened ancestor')
        check()
        for part in path.parts[1:]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd, current = child, current / part
            check()
        yield fd
    finally:
        os.close(fd)


def fresh_directory(path, anchors):
    path = Path(path)
    with directory_fd(path.parent, anchors) as fd:
        os.mkdir(path.name, mode=0o700, dir_fd=fd)
    return identity(path, directory_info(path))


def publish(source, contract, digest, check):
    # A fresh hidden stage and exclusive hard-link publication make the final name atomic.
    destinations = contract['paths']
    review, staged = Path(destinations['review']), Path(destinations['stagedReview'])
    need(review.parent == staged.parent, 'Publication parents differ')
    anchors = validate(contract, destinations)
    need(not os.path.lexists(review) and not os.path.lexists(staged), 'Review destination already present')
    check()
    with directory_fd(Path(source).parent, anchors) as src_parent, directory_fd(review.parent, anchors) as parent:
        src_fd = os.open(Path(source).name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=src_parent)
        try:
            need(stat.S_ISREG(os.fstat(src_fd).st_mode), 'Unsafe review source')
            dst_fd = os.open(staged.name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=parent)
            with os.fdopen(dst_fd, 'wb') as dst:
                h = hashlib.sha256()
                while True:
                    chunk = os.read(src_fd, 1024 * 1024)
                    if not chunk:
                        break
                    validate(contract, destinations)
                    check()
                    dst.write(chunk)
                    h.update(chunk)
                need(h.hexdigest() == digest, 'Verified source changed during copy')
                dst.flush()
                os.fsync(dst.fileno())
            validate(contract, destinations)
            check()
            os.link(staged.name, review.name, src_dir_fd=parent, dst_dir_fd=parent, follow_symlinks=False)
            os.fsync(parent)
            validate(contract, destinations)
            # Remove only this newly created stage after successful atomic publication.
            os.unlink(staged.name, dir_fd=parent)
            os.fsync(parent)
        finally:
            os.close(src_fd)
    return review
