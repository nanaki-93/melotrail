# Screen Specification

## 1. Project

Purpose:
Define the musical authority before processing starts.

Fields:
- Project name
- Style profile
- Tempo
- Time signature
- Key / mode
- Verse chord progression
- Chorus chord progression
- Bridge chord progression
- Production profile
- Working sample rate

Important:
Clearly label key/chords as **Project Musical Authority**.

Actions:
- Save Project
- Duplicate Project
- Open Project

---

## 2. Source

Purpose:
Import, prepare and approve musical source parts.

Each part card shows:
- Part ID
- Role: Verse / Chorus / Bridge / Other
- Source type: MIDI / WAV / MP3
- transcription status
- cleanup status
- AI Fix status
- AI Enhance status
- duration / bars
- key compatibility
- chord compatibility
- recognizability / protected motif status

Actions:
- Import
- Transcribe
- Clean
- AI Fix
- AI Enhance
- Play current version
- Compare versions

Version selector:

```text
SOURCE | RAW MIDI | CLEAN | AI FIX | AI ENHANCE
```

The user should be able to A/B these versions.

---

## 3. Structure

Purpose:
Build the actual song from prepared parts.

Timeline:

```text
[A1 Verse] [A2 Verse] [B1 Chorus] [C1 Bridge] [B2 Chorus]
```

Features:
- drag/drop reorder
- repeat section
- delete occurrence
- occurrence metadata
- bars
- section role
- chord progression preview
- target energy

Important:
Repeated occurrences are independent arrangement instances.

---

## 4. Melody Connection

This should live inside Structure, not as a completely separate navigation area.

For every boundary:

```text
A2 → B1
```

show:

- source ending chord
- next starting chord
- proposed strategy
- notes modified
- change budget
- critic result

Strategies:
- NONE
- HOLD_LAST_NOTE
- EXTEND_CHORD
- REST
- PICKUP
- STEPWISE_PICKUP
- SIMPLIFY_ENDING

Actions:
- Generate Connections
- Regenerate Selected Boundary
- Manual Strategy Override
- Preview Boundary
- Compare Before / After
- Approve Source Song

The user should be able to listen to the connected solo source before arrangement.

---

## 5. Arrange

This becomes the most important screen.

Layout:

```text
┌──────────────────────────────────────────────────────────────┐
│ Global Song Plan                                            │
│ Energy curve + section purposes                             │
├──────────────────────────────────────────────────────────────┤
│ Song timeline                                               │
│ A1      A2      B1      C1      B2                          │
├──────────────────────────────────────────────────────────────┤
│ Piano   █████████████████████████████████████████████       │
│ Bass            ███████████████████████████████████         │
│ Drums                   ███████████      ███████████        │
│ Pad         ███████████████████████████████████████         │
│ Strings                                      ███████        │
├──────────────────────────────────────────────────────────────┤
│ Selected section inspector                                  │
└──────────────────────────────────────────────────────────────┘
```

### Global planning panel

Show:
- section purpose
- energy curve
- density curve
- instrumentation plan
- Qwen producer decisions

Actions:
- Generate Plan
- Re-plan
- Lock Plan
- Switch to deterministic baseline

### Incremental arrangement panel

Show track states:

```text
Piano    ACCEPTED
Bass     ACCEPTED
Drums    REVIEW
Pad      REVIEW
Strings  LOCKED
```

Each role shows:
- pattern
- density
- register
- validation score
- issues
- generation seed
- current MIDI artifact

Actions:
- Generate
- Validate
- Regenerate Issue Only
- Accept
- Reject
- Pattern Override

Do not render all instruments with one generic "Arrange" button once the user enters advanced mode.

---

## 6. Core Arrangement Approval

Within Arrange, show a dedicated review state after:

- Piano
- Bass
- Drums
- Pad

are ready.

Actions:

```text
[Play Core Arrangement]
[Compare Piano Only]
[Solo Bass]
[Solo Drums]
[Solo Pad]
[Approve Core Arrangement]
[Return to Editing]
```

Optional layers remain locked until approval.

---

## 7. Optional Layers / Transitions

After core approval:

- Strings
- Countermelody
- transition plan
- ensemble cohesion

Expose DensityBudget.

Example:

```text
B2
Target density: 0.70
Current core:   0.61
Remaining:      0.09

Strings recommendation: OFF
```

This is important so the user sees why MeloTrail chooses silence.

---

## 8. Whole-Song Review

Still inside Arrange.

Show deterministic critic report:

- melody preservation
- harmony
- bass quality
- groove
- masking
- density
- section contrast
- transition quality
- recognizability

Issues should be clickable and navigate to the exact section/bar.

Actions:
- Apply Targeted Fix
- Ignore Warning
- Preview Before/After
- Accept Final MIDI

No generic "AI improve everything" button.

---

## 9. Mix

Purpose:
Turn accepted MIDI arrangement into production audio.

Panels:
- instrument selection
- rendered stems
- mixer
- buses
- audio critic
- LoFi A/B

Mixer controls:
- volume
- pan
- mute
- solo
- EQ
- compression
- reverb send
- optional automation

Top buttons:

```text
[Render Stems]
[Build Dry Mix]
[Build LoFi Mix]
[A/B Dry ↔ LoFi]
```

Audio critic:
- clipping
- headroom
- masking
- low-end conflict
- stereo correlation
- melody audibility

---

## 10. Release

Purpose:
Commercial-ready validation and export.

Show checklist:

- Source provenance
- Instrument licenses
- Source approved
- Core arrangement approved
- Final MIDI approved
- Recognizability passed
- Melody audibility passed
- Mix approved
- Loudness passed
- True peak passed
- AI usage metadata
- Release similarity report

Master card:

```text
Integrated: -13.9 LUFS
True Peak:  -1.1 dBTP
LRA:         6.3 LU
Status:      PASS
```

Actions:
- Build Master
- Export WAV
- Export MP3
- Generate Provenance
- Generate YouTube Release Metadata

Commercial-ready status must be visually distinct from ordinary build success.

---

## 11. Progressive disclosure, recovery, and safe edits

The default workspace presents one next action per stage. Advanced evidence,
planner controls, runtime details, and technical metrics are available through
contextual **More options**, **Show details**, or **Inspect** disclosures; they
do not become a second workflow or change pipeline state by themselves.

Long-running work shows an explicit local/worker/model/renderer status, known
step progress when the service provides it, actionable failure text, and a
safe retry. Cancellation is offered only at an artifact-safe boundary. UI
progress comes from `WorkflowReadModel`, durable stage runs, and typed operation
feedback—not filesystem inspection.

Changes that remove a melody part, structure occurrence, or the entire song
structure require confirmation. The confirmation explains that source and
validated historical artifacts remain recoverable evidence, while downstream
results become stale and must be regenerated. Harmony, source-rights, cleanup,
and other approval gates stay explicit; navigation and page visits never bypass
them.
