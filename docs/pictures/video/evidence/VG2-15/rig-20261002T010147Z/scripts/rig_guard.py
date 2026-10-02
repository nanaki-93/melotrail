"""Exact data/process helper excerpts from the retained direct supervisor.
Source SHA-256: db18f49ee1166aa8c8da2ac79fc47e1da899c8df559020986f6b9c6f4dd60b59
No historical packet, socket, launcher or media admission is reused.
"""
import hashlib
import json
import os
import stat
import subprocess
from pathlib import Path

def need(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def fresh_json(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as f:
        json.dump(value, f, indent=2)
        f.write('\n')
        f.flush()
        os.fsync(f.fileno())


def bytes_under(root):
    if not os.path.lexists(root):
        return 0
    need(not Path(root).is_symlink(), 'Symlink storage root')
    if Path(root).is_file():
        return Path(root).stat().st_size
    total = 0
    for parent, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            f = Path(parent) / name
            need(not f.is_symlink(), 'Symlink in owned storage')
            if f.is_file():
                need(stat.S_ISREG(f.lstat().st_mode), 'Nonregular owned artifact')
                total += f.stat().st_size
    return total


def process_rows():
    text = subprocess.check_output(['/bin/ps', '-axo', 'pid=,ppid=,pgid=,rss=,lstart='], text=True, timeout=3)
    rows = {}
    for line in text.splitlines():
        x = line.split(maxsplit=4)
        if len(x) == 5:
            rows[int(x[0])] = {'parent': int(x[1]), 'group': int(x[2]), 'rss': int(x[3]) * 1024, 'birth': x[4]}
    return rows


def owned_ids(rows, pid):
    found = {i for i, row in rows.items() if i == pid or row['group'] == pid}
    while True:
        children = {i for i, row in rows.items() if row['parent'] in found}
        if children <= found:
            return found
        found |= children
