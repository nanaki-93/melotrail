# Task 110 artifact inventory

This inventory records the canonical workflow meanings established by Task 110.
It does not add a processor or a second project format.

| Boundary | Reused canonical artifact/reference | Task 110 meaning |
| --- | --- | --- |
| Import | `Part.file` under `source/` | Immutable imported evidence. |
| Conversion | `MidiReferences.raw` under `midi/raw/` | The one raw-MIDI boundary for direct MIDI or eligible audio transcription. |
| Clean MIDI | Legacy-compatible `MidiReferences.clean`, cleanup options, and quality report under `midi/clean/` and `midi/quality/` | The mandatory deterministic cleaned artifact. The persisted field name remains `clean` for compatible reads; product/workflow code uses **Clean MIDI**, not the overlapping “repair” stage meaning. |
| Optional AI fix | `MidiReferences.aiFixSelection` plus `MidiAiFixReferences` | `SKIP` selects cleaned MIDI. `APPROVED` selects only fingerprint-matched `midi/ai-fix/<part>/approved.mid`; `draft.mid` is review evidence and is never selected. |
| Optional MIDI Feel | Legacy-compatible `MidiReferences.analysisInput` and `MidiFeelReferences` | The persisted selector remains readable. `REPAIRED` means current base without a Feel transform (cleaned or approved AI fix); `LOFI_FEEL` selects a fingerprint-matched derived MIDI. This is distinct from post-mix Lo-fi audio texture. |
| Analysis | `Part.analysis` under `analysis/` | Analysis consumes only `SelectedMidiArtifactResolver`; file existence cannot override stale workflow evidence. |
| Structure | `Project.structure` | User-owned ordering remains canonical and is not rewritten by selection or invalidation. |
| Cohesion | Existing aggregate/per-occurrence references plus `CohesionBoundaryReference` | Existing per-occurrence files remain readable. New adjacent-boundary metadata uses stable outgoing/incoming IDs and canonical fingerprinted paths under `cohesion/boundaries/`. |
| Arrangement and build | Existing arrangement, generated MIDI, stems, mix, master, and release artifacts | Retained downstream files are inspection evidence whenever the workflow graph marks them stale. |

The only current per-part MIDI precedence is:

```text
raw MIDI
  -> cleaned MIDI
  -> cleaned MIDI OR approved AI-fixed MIDI
  -> current base OR Lo-fi Feel MIDI
  -> analysis
```

`SelectedMidiArtifactResolver` owns that precedence and validates the selected
file, project confinement, MIDI format, and fingerprints. Compatibility helpers
delegate to it; downstream services must not select by file existence.

Invalidation retains references and files. A source/raw change invalidates Clean
MIDI and every descendant; a cleaned-MIDI change additionally invalidates AI-fix
selection; an AI-fix selection or MIDI-Feel change invalidates analysis and every
Structure-dependent descendant; analysis or Structure changes invalidate
Cohesion onward. Skipped optional branches ignore missing or stale unselected
artifacts and therefore do not block progression.
