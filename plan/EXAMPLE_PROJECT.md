# Example Personal Project

The shared instrument library remains outside the song project and is resolved by logical name:

```text
sounds/
  instruments.json
  LICENSES.json
  piano/piano.sfz
  bass/bass.sfz
  drums/drums.sfz
  pad/pad.sfz
  strings/strings.sfz
```

Do not copy SFZ files or samples into every project. Project/plan JSON uses only `piano`, `bass`, `drums`, `pad`, and `strings`; the registry resolves those names at rendering time.

```text
projects/demo/
  project.json
  parts/
    A.wav
    B.wav
    C.wav
```

Example:

```json
{
  "version": 1,
  "name": "demo",
  "parts": [
    {"id": "A", "file": "parts/A.wav", "role": "verse"},
    {"id": "B", "file": "parts/B.wav", "role": "chorus"},
    {"id": "C", "file": "parts/C.wav", "role": "bridge"}
  ],
  "structure": ["A", "A", "B", "B", "A", "C", "B"]
}
```

First test with only:

```text
piano
bass
drums
pad
```

These instruments already exist in the local `sounds/` starter pack. The first quality gate still renders only piano and bass; drums and pad remain disabled until that gate is accepted.

Expected output:

```text
analysis/A.json
analysis/B.json
analysis/C.json
arrangement.json
stems/
mix/mix.wav
output/master.wav
```

Run the repeatable local workflow:

```bash
make worker
./gradlew cliRun --args="build --project ./projects/demo --no-ai"
```

`--dry-run` validates the project without writing analysis, stems, mixes, or
output files. The generated audio remains WAV/PCM-24 throughout; MP3 export is
a separate final conversion step.
