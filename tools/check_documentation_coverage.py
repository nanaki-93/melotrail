#!/usr/bin/env python3
"""Validate Melotrail's checked-in production-source documentation inventory.

The inventory deliberately tracks callable declaration digests as well as paths.
That makes a new function, or a signature change in an existing source file, a
review event: maintainers must either add focused KDoc/docstrings or record a
specific, local exemption before the check can pass.  The checker is offline
and reads no build output, test source, or generated source.
"""

from __future__ import annotations

import argparse
import ast
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SOURCE_ROOTS = (
    Path("src/main/kotlin"),
    Path("desktopApp/src/main/kotlin"),
    Path("worker"),
)
INVENTORY_PATH = Path("docs/FUNCTION_DOCUMENTATION_INVENTORY.json")
CLASSIFICATIONS = {
    "documented",
    "inherited-contract",
    "trivial/generated",
    "deferred-with-reason",
}
EXCLUDED_PARTS = {"tests", "__pycache__", ".venv", ".venv-worker", "build"}
KOTLIN_FUNCTION = re.compile(r"^\s*(?:(?:public|private|internal|protected|override|suspend|inline|tailrec|operator|open|abstract)\s+)*fun\s+[^({=]+", re.MULTILINE)
PYTHON_FUNCTION = re.compile(r"^(?:async\s+)?def\s+[^(:]+", re.MULTILINE)
KOTLIN_DOCUMENTED_FUNCTION = re.compile(
    r"/\*\*.*?\*/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:(?:public|private|internal|protected|override|suspend|inline|tailrec|operator|open|abstract)\s+)*fun\s+[^({=]+",
    re.DOTALL,
)


@dataclass(frozen=True)
class SourceFacts:
    """The stable callable declaration facts used to guard one source file."""

    path: str
    function_count: int
    documented_function_count: int
    declaration_digest: str


class DocumentationCoverageError(ValueError):
    """Raised when production sources and their reviewed inventory disagree."""


def discover_sources(repository: Path) -> list[Path]:
    """Return production Kotlin/Python files in the contract's three roots.

    Tests, build output, virtual environments, and Python bytecode caches are
    excluded so the result is independent of local tooling state.
    """
    sources: list[Path] = []
    for relative_root in SOURCE_ROOTS:
        root = repository / relative_root
        if not root.is_dir():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix not in {".kt", ".py"}:
                continue
            relative = path.relative_to(repository)
            if any(part in EXCLUDED_PARTS for part in relative.parts):
                continue
            sources.append(relative)
    return sorted(sources)


def callable_declarations(source: str, suffix: str) -> list[str]:
    """Extract normalized callable declaration lines without parsing function bodies.

    This lightweight guard intentionally detects every Kotlin ``fun`` and
    Python ``def`` declaration, including private helpers.  It is not a source
    parser; formatting that changes a declaration is still a review event and
    therefore safely requires an inventory refresh.
    """
    pattern = KOTLIN_FUNCTION if suffix == ".kt" else PYTHON_FUNCTION
    return [" ".join(match.group(0).split()) for match in pattern.finditer(source)]


def documented_callable_count(source: str, suffix: str) -> int:
    """Count callable declarations with directly attached KDoc or docstrings.

    Kotlin KDoc must immediately precede its declaration (annotations are
    allowed). Python uses the language's own function-docstring parser, so a
    module docstring never counts as documentation for a handler or helper.
    """
    if suffix == ".kt":
        return len(KOTLIN_DOCUMENTED_FUNCTION.findall(source))
    module = ast.parse(source)
    return sum(
        ast.get_docstring(node) is not None
        for node in ast.walk(module)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
    )


def source_facts(repository: Path, relative_path: Path) -> SourceFacts:
    """Calculate the inventory facts for one UTF-8 source file."""
    source = (repository / relative_path).read_text(encoding="utf-8")
    declarations = callable_declarations(source, relative_path.suffix)
    encoded = "\n".join(declarations).encode("utf-8")
    return SourceFacts(
        path=relative_path.as_posix(),
        function_count=len(declarations),
        documented_function_count=documented_callable_count(source, relative_path.suffix),
        declaration_digest=hashlib.sha256(encoded).hexdigest(),
    )


def load_inventory(path: Path) -> list[dict]:
    """Load the versioned JSON inventory and reject malformed top-level data."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise DocumentationCoverageError(f"Documentation inventory is missing: {path}") from exc
    except json.JSONDecodeError as exc:
        raise DocumentationCoverageError(f"Documentation inventory is invalid JSON: {exc}") from exc
    if document.get("schemaVersion") != 1 or not isinstance(document.get("sources"), list):
        raise DocumentationCoverageError("Documentation inventory must contain schemaVersion 1 and a sources array")
    return document["sources"]


def validate_inventory(repository: Path, inventory_path: Path | None = None) -> None:
    """Fail when a production source lacks a current, reviewable classification.

    Each row must name exactly one discovered source and preserve its callable
    declaration count/digest.  Deferred classifications are permitted only
    with a concrete reason, keeping intentional legacy exemptions visible.
    """
    inventory_path = inventory_path or repository / INVENTORY_PATH
    rows = load_inventory(inventory_path)
    errors: list[str] = []
    by_path: dict[str, dict] = {}
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("path"), str):
            errors.append("Inventory rows must be objects with a string path")
            continue
        path = row["path"]
        if path in by_path:
            errors.append(f"Duplicate inventory path: {path}")
        by_path[path] = row
        classification = row.get("classification")
        if classification not in CLASSIFICATIONS:
            errors.append(f"Unsupported classification for {path}: {classification!r}")
        reason = row.get("reason")
        if not isinstance(reason, str) or not reason.strip():
            errors.append(f"Missing reviewable reason for {path}")

    sources = {path.as_posix(): source_facts(repository, path) for path in discover_sources(repository)}
    missing = sorted(set(sources) - set(by_path))
    extra = sorted(set(by_path) - set(sources))
    errors.extend(f"Missing inventory classification: {path}" for path in missing)
    errors.extend(f"Inventory references non-production source: {path}" for path in extra)

    for path, facts in sources.items():
        row = by_path.get(path)
        if row is None:
            continue
        if row.get("classification") == "trivial/generated" and facts.function_count:
            errors.append(f"Trivial/generated classification has callable declarations: {path}")
        if row.get("classification") == "documented" and facts.documented_function_count != facts.function_count:
            errors.append(f"Documented classification is missing direct KDoc/docstrings: {path}")
        if row.get("functionCount") != facts.function_count:
            errors.append(f"Callable count changed for {path}; review its documentation classification")
        if row.get("documentedFunctionCount") != facts.documented_function_count:
            errors.append(f"Direct documentation coverage changed for {path}; review its documentation classification")
        if row.get("declarationDigest") != facts.declaration_digest:
            errors.append(f"Callable declarations changed for {path}; review its documentation classification")
    if errors:
        raise DocumentationCoverageError("\n".join(errors))


def main(arguments: Iterable[str] | None = None) -> int:
    """Run validation for a repository root supplied by the command line."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path.cwd(), help="repository root (default: current directory)")
    parser.add_argument("--inventory", type=Path, help="inventory path relative to the repository unless absolute")
    options = parser.parse_args(arguments)
    repository = options.repository.resolve()
    inventory = options.inventory
    if inventory is not None and not inventory.is_absolute():
        inventory = repository / inventory
    try:
        validate_inventory(repository, inventory)
    except DocumentationCoverageError as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
