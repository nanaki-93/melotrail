# MC-049 holdout musical-acceptance rubric

Status: awaiting user-approved holdout set and listener results

This gate assesses musical usefulness, not merely MIDI structure. It requires
at least ten MIDI projects that were not used to set generator constants or as
development fixtures. Do not use the obsolete, ignored `data/audio/` material:
it belongs to the audio-era cleanup scope and is not a user-approved or unseen
holdout set.

## Set requirements

Provide ten or more license-safe projects. Each source must contain the complete
song as exactly one note-bearing track on one note-bearing channel, may include
meta-only conductor tracks, and must end on a whole-bar boundary in its confirmed
meter. The set covers different keys, tempos, section orders, melody registers,
rhythmic activity, sustained material, repeated sections, and chromatic harmony.
For each project, state one of:

- `I own this MIDI and authorize its use as Melotrail holdout evidence.`
- `This MIDI is public-domain or license-safe for this evidence; source: …`

Place files at `docs/checks/holdouts/<stable-id>.mid` (or provide their exact
local paths) and do not alter them after their SHA-256 digests are recorded.
The agent will inspect and freeze source hashes before any generation or score
is recorded. These files are evidence only; they must not become new generator
fixtures or be used to tune constants before their initial score.

## Per-project procedure

1. Create a Melotrail project and import the source MIDI; the only melody track
   is protected automatically. Enter authoritative key, tempo, meter, ordered
   section bar counts, and harmony. The bar total must equal the source length.
   Authority entry is excluded from review timing.
2. Start the review timer at the first Arrange generation. Follow the guided
   section/role -> feel -> Generate flow, then use Review's Play -> Accept ->
   Continue path for Chords, Bass, and Drums. Generate another alternative only
   when the current result is not useful; do not edit protected melody or
   authoritative harmony merely to make a result look better.
3. When every part is accepted, use Review's full-arrangement playback, continue
   to Export, and publish its immutable snapshot. Record the snapshot ID,
   elapsed review time, and short reason for every score.
4. Do not manually edit generated MIDI outside Melotrail to conceal a timing or
   harmony defect. Report such a case as a blocker.

## Scores and pass criteria

Score every item from 1 to 5:

- melody preservation;
- chord/keys support and space;
- bass harmonic correctness and motion;
- drum groove completeness and section energy;
- role interaction/collision control;
- section development and transitions;
- usefulness of alternatives; and
- overall readiness for Logic Pro continuation.

The gate passes only if melody preservation is 5 for every project; every core
role and overall score is at least 3; median overall score is at least 4; no
timing/harmony blocker requires external MIDI editing; and median review time
is at most 15 minutes (excluding authority entry and DAW instrument selection).

## Result template

```text
Holdout ownership/license statement:
Reviewer/date:

ID | source path | source hash | snapshot ID | review minutes | melody | chords | bass | drums | interaction | section | alternatives | overall | short reason / blocker
01 |
02 |
03 |
04 |
05 |
06 |
07 |
08 |
09 |
10 |

Median overall:
Median review minutes:
Any external MIDI edit needed for timing/harmony? (must be no):
```

The rubric may record a failed project. Do not remove a project, replace it, or
change generator constants merely to improve the aggregate score. A reproducible
failure requires a targeted fix, a regression test, and a repeat of the affected
project.
