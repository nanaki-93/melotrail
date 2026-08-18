"""Focused tests for the repository documentation-inventory validator."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("documentation_coverage", REPOSITORY / "tools" / "check_documentation_coverage.py")
assert SPEC and SPEC.loader
coverage = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = coverage
SPEC.loader.exec_module(coverage)


class DocumentationCoverageTest(unittest.TestCase):
    """Exercise classification and callable-change failures using isolated fixtures."""

    def test_accepts_documented_and_exempt_sources(self) -> None:
        with fixture_repository({
            "src/main/kotlin/Documented.kt": "/** Explains the fixture's documented callable. */\nfun documented() = Unit\n",
            "worker/exempt.py": "VALUE = 1\n",
        }, {
            "src/main/kotlin/Documented.kt": "documented",
            "worker/exempt.py": "trivial/generated",
        }) as repository:
            coverage.validate_inventory(repository)

    def test_rejects_missing_source_classification(self) -> None:
        with fixture_repository({"worker/missing.py": "def undocumented():\n    return None\n"}, {}) as repository:
            with self.assertRaisesRegex(coverage.DocumentationCoverageError, "Missing inventory classification: worker/missing.py"):
                coverage.validate_inventory(repository)

    def test_rejects_changed_callable_declaration_until_reviewed(self) -> None:
        path = "src/main/kotlin/Changed.kt"
        with fixture_repository({path: "fun before() = Unit\n"}, {path: "deferred-with-reason"}) as repository:
            (repository / path).write_text("fun after() = Unit\n", encoding="utf-8")
            with self.assertRaisesRegex(coverage.DocumentationCoverageError, "Callable declarations changed"):
                coverage.validate_inventory(repository)

    def test_rejects_callable_source_misclassified_as_trivial(self) -> None:
        path = "worker/not_trivial.py"
        with fixture_repository({path: "def meaningful():\n    return None\n"}, {path: "trivial/generated"}) as repository:
            with self.assertRaisesRegex(coverage.DocumentationCoverageError, "Trivial/generated classification has callable declarations"):
                coverage.validate_inventory(repository)

    def test_rejects_undocumented_callable_marked_documented(self) -> None:
        path = "worker/not_documented.py"
        with fixture_repository({path: "def meaningful():\n    return None\n"}, {path: "documented"}) as repository:
            with self.assertRaisesRegex(coverage.DocumentationCoverageError, "Documented classification is missing direct KDoc/docstrings"):
                coverage.validate_inventory(repository)


class fixture_repository:
    """Create a disposable repository and matching inventory for one validator test."""

    def __init__(self, files: dict[str, str], classifications: dict[str, str]) -> None:
        self.files = files
        self.classifications = classifications
        self.temporary: tempfile.TemporaryDirectory | None = None

    def __enter__(self) -> Path:
        self.temporary = tempfile.TemporaryDirectory()
        root = Path(self.temporary.name)
        rows = []
        for relative, content in self.files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            if relative in self.classifications:
                declarations = coverage.callable_declarations(content, path.suffix)
                rows.append({
                    "path": relative,
                    "classification": self.classifications[relative],
                    "reason": "Focused fixture classification.",
                    "functionCount": len(declarations),
                    "documentedFunctionCount": coverage.documented_callable_count(content, path.suffix),
                    "declarationDigest": hashlib.sha256("\n".join(declarations).encode("utf-8")).hexdigest(),
                })
        inventory = root / "docs" / "FUNCTION_DOCUMENTATION_INVENTORY.json"
        inventory.parent.mkdir(parents=True, exist_ok=True)
        inventory.write_text(json.dumps({"schemaVersion": 1, "sources": rows}), encoding="utf-8")
        return root

    def __exit__(self, exc_type, exc, traceback) -> None:
        assert self.temporary is not None
        self.temporary.cleanup()


if __name__ == "__main__":
    unittest.main()
