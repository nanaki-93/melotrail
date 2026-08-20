#!/usr/bin/env python3
"""Normalize the imported local production packs and generate registry v3.

Run with no arguments for a dry run.  --apply is intentionally local-only: sound assets are
ignored by Git and no network/download operation is performed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


PACKS = {
    "Emilyguitar": "karoryfer-emily-guitar",
    "Fashionbass": "karoryfer-fashionbass",
    "Gogodze_Phu_vol_II": "karoryfer-gogodze-phu-vol-ii",
    "Karoryfer_Bigcat_cello": "karoryfer-bigcat-cello",
    "Pastabass": "karoryfer-pastabass",
    "Shinyguitar": "karoryfer-shinyguitar",
    "Sneakybass": "karoryfer-sneakybass",
    "VCSL-1.2.2-RC": "versilian-vcsl-1.2.2-rc",
    "VCSL_Keys": "versilian-vcsl-keys",
    "Virtuosity_Drums_v0.925": "versilian-virtuosity-drums-0.925",
}

LEGACY_STARTER_DIRECTORIES = ("bass", "drums", "pad", "piano", "strings")


def entrypoints(pack: str, directory: Path) -> list[Path]:
    sfz = lambda path: sorted(path.glob("*.sfz"))
    if pack == "Gogodze_Phu_vol_II":
        return sfz(directory / "Programs")
    if pack == "Karoryfer_Bigcat_cello":
        return [directory / "Programs" / name for name in (
            "01- Bowed (velocity layer).sfz", "02- Bowed (mod wheel).sfz", "03- Plucked.sfz")]
    if pack == "Shinyguitar":
        return [directory / "Programs" / "melotrail-main.sfz"]
    if pack == "Sneakybass":
        return sfz(directory / "Programs")
    if pack == "Virtuosity_Drums_v0.925":
        return sfz(directory / "Programs")
    if pack == "VCSL-1.2.2-RC":
        return sorted(directory.rglob("*.sfz"))
    return sfz(directory)


def stable_id(pack_id: str, path: Path) -> str:
    stem = path.with_suffix("").as_posix().lower()
    text = "".join(ch if ch.isalnum() else "-" for ch in stem)
    value = pack_id + "-" + "-".join(part for part in text.split("-") if part)
    return value if len(value) <= 48 else value[:41].rstrip("-") + "-" + hashlib.sha1(value.encode()).hexdigest()[:6]


def metadata(pack: str, path: Path) -> tuple[str, list[str], str]:
    name = path.stem.lower()
    if pack in {"Fashionbass", "Pastabass", "Sneakybass"}:
        return "bass", ["bass"], "automatic" if any(x in name for x in ("clean", "fetuccine", "pluck")) else "manual-only"
    if pack in {"Gogodze_Phu_vol_II", "Virtuosity_Drums_v0.925"}:
        return "drum-kit", ["drums"], "automatic" if "kit" in name else "manual-only"
    if pack in {"Emilyguitar", "Shinyguitar"}:
        return "guitar", ["melody", "harmony", "texture"], "automatic" if name in {"emily-clean", "main", "melotrail-main"} else "manual-only"
    if pack == "Karoryfer_Bigcat_cello":
        return "strings", ["counter-melody", "texture"], "automatic" if name.startswith("01-") else "manual-only"
    if pack == "VCSL_Keys":
        return "keys", ["melody", "harmony", "counter-melody"], "automatic" if "grand-piano-k" in name else "manual-only"
    if pack == "VCSL-1.2.2-RC":
        top = path.parts[0] if path.parts else ""
        if top == "Aerophones": return "winds", ["melody", "counter-melody"], "manual-only"
        if top == "Chordophones": return "keys-and-strings", ["melody", "harmony", "counter-melody", "texture"], "automatic" if "grand piano, kawai" in name else "manual-only"
        if top == "Electrophones": return "synth", ["harmony", "texture", "ambience"], "automatic" if "clavisynth" in name else "manual-only"
        if top == "Idiophones": return "mallets-and-percussion", ["counter-melody", "texture"], "manual-only"
        return "percussion", ["texture", "ambience"], "manual-only"
    return "instrument", ["texture"], "manual-only"


def catalog(root: Path) -> dict:
    instruments = []
    for source_name, pack_id in PACKS.items():
        directory = root / "libraries" / pack_id
        library_id = pack_id.replace(".", "-")
        for program in entrypoints(source_name, directory):
            if not program.is_file():
                raise FileNotFoundError(f"Missing catalog program: {program}")
            relative = program.relative_to(root).as_posix()
            category, roles, selection = metadata(source_name, program.relative_to(directory))
            instruments.append({
                "id": stable_id(library_id, program.relative_to(directory)),
                "name": program.stem,
                "category": category,
                "selectionMode": selection,
                "roles": roles,
                "engine": {"type": "sfz", "path": relative},
                "license": {
                    "id": "CC0-1.0", "commercialUse": True, "attributionRequired": False,
                    "sourceName": "Imported CC0 production library",
                    "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/"
                },
                "library": {"id": library_id, "name": source_name, "version": "imported", "source": "local production import"},
                "capabilities": {},
                "midiChannel": 10 if "drums" in roles else None,
            })
    ids = [entry["id"] for entry in instruments]
    if len(ids) != len(set(ids)):
        raise ValueError("Stable-ID collision while generating catalog")
    return {"version": 3, "supportedSampleRates": [44100, 48000], "midiChannelConvention": "one-based", "instruments": sorted(instruments, key=lambda item: item["id"])}


def ensure_compatibility_wrappers(libraries: Path) -> None:
    """Supply a local definition expected by Shinyguitar's Sforzando bank."""
    wrapper = libraries / PACKS["Shinyguitar"] / "Programs" / "melotrail-main.sfz"
    wrapper.write_text('#define $sample_dir ../Samples\n#include "main.sfz"\n', encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("sounds"))
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    source = root / "production"
    libraries = root / "libraries"
    if source.is_dir() and not args.apply:
        print(f"Would move {len(PACKS)} packs from {source} to {libraries}; rerun with --apply")
        return
    if source.is_dir():
        libraries.mkdir(parents=True, exist_ok=True)
        for old, new in PACKS.items():
            origin, target = source / old, libraries / new
            if not origin.is_dir():
                raise FileNotFoundError(f"Missing imported pack: {origin}")
            if target.exists():
                raise FileExistsError(f"Refusing to overwrite existing pack: {target}")
            shutil.move(str(origin), str(target))
        source.rmdir()
    if not libraries.is_dir():
        raise FileNotFoundError(f"No normalized library directory: {libraries}")
    for dot_store in root.rglob(".DS_Store"):
        dot_store.unlink()
    if args.apply:
        for legacy in LEGACY_STARTER_DIRECTORIES:
            legacy_path = root / legacy
            if legacy_path.is_dir():
                shutil.rmtree(legacy_path)
        legacy_licenses = root / "LICENSES.json"
        if legacy_licenses.is_file():
            legacy_licenses.unlink()
    ensure_compatibility_wrappers(libraries)
    document = catalog(root)
    output = root / "instruments.json"
    output.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(document['instruments'])} catalog entries to {output}")


if __name__ == "__main__":
    main()
