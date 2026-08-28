# MC-047 bounded property evidence

Status: automated property gate passed on 2026-08-28.

The target suite is `MidiCoreBoundedPropertyTest`. Every corpus is deterministic,
offline, and bounded by a JUnit 20-second class timeout. It has no network input,
unseeded randomness, or retained generated corpus.

| Boundary | Seed | Cases | Bound | Required result |
| --- | ---: | ---: | --- | --- |
| Malformed SMF chunks | 47001 | 64 | 128 bytes/input | `INVALID_MIDI`; no source artifact or project mutation |
| Unpaired note events | 47006 | 48 | one compact format-0 track/input | typed `UNCLOSED_NOTE_ON` or `ORPHAN_NOTE_OFF`, then `IMPORT_REJECTED` with no source publication |
| Rational tick conversion | 47002 | 128 | PPQ 1–2047; reduced denominator <1024 | deterministic nearest-ties-up rounding and exact representable round trips |
| Authority timelines | 47003 | 64 | 1–4 contiguous occurrences | valid coverage passes; a one-tick gap returns `CHORD_WINDOW_GAP` |
| Project JSON/path/state | 47004 | 48 | serialized project <8192 chars | deterministic schema round trip; traversal/absolute/Windows paths invalid; rejected candidates cannot remain accepted |
| Writer/reader semantic round trip | 47005 | 48 | 4 core role tracks; bounded event count | SMF1 semantic facts, role channels, markers, end tick, expression events, and no program/unsupported event |

Total deterministic cases: 400. No new minimized regression fixture was needed:
all seeded cases passed on two consecutive runs. Existing owned fixture files
continue to carry the smallest hand-authored regressions for parser-specific
conditions.

Run command:

```bash
./gradlew :test --tests app.melotrail.midi.MidiCoreBoundedPropertyTest --rerun-tasks --console=plain
```
