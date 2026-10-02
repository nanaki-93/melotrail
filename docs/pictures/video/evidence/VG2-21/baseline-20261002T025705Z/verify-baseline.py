"""Read-only recovery check; never starts a renderer, model or media decoder.

Run from the repository root. Media and historical receipts stay at their
original local paths. This check proves retained bytes, not runtime readiness.
"""
import hashlib
import json
from pathlib import Path


def sha256(path):
    with path.open("rb") as stream:
        digest = hashlib.sha256()
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
        return digest.hexdigest()


def main():
    owner = Path(__file__).resolve().parent
    repo = owner.parents[5]
    baseline = json.loads((owner / "checks/baseline.json").read_text())
    failures = []
    for row in baseline["files"]:
        path = repo / row["path"]
        if not path.is_file():
            failures.append({"path": row["path"], "reason": "missing file"})
        elif path.stat().st_size != row["bytes"] or sha256(path) != row["sha256"]:
            failures.append({"path": row["path"], "reason": "changed bytes"})
    print(json.dumps({
        "status": "PASS" if not failures else "FAIL",
        "checkedFiles": len(baseline["files"]),
        "failures": failures,
        "mediaModelOrDecoderLaunches": 0,
        "scope": "Retained source/recipe identity only; no current runtime or moving pass.",
    }, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
