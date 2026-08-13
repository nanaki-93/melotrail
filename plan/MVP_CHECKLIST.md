# MVP Checklist

## Existing pipeline
- [ ] MP3 -> WAV
- [ ] analysis
- [ ] repair
- [ ] LoFi on/off
- [ ] master writes valid WAV
- [ ] MP3 export is separate

## Parts
- [ ] create project
- [ ] add A.wav
- [ ] add B.wav
- [ ] project JSON loads
- [ ] sources never overwritten

## Structure
- [ ] `A A B B A C` works
- [ ] invalid IDs fail
- [ ] repetitions work
- [ ] timeline deterministic

## Analysis
- [ ] duration
- [ ] sample rate
- [ ] channels
- [ ] peak
- [ ] RMS
- [ ] silence
- [ ] optional BPM/key

## Arrangement
- [ ] schema
- [ ] deterministic planner
- [ ] source instruments
- [ ] generated instrument placeholders
- [ ] transition schema

## Qwen
- [ ] local endpoint
- [ ] JSON-only response
- [ ] schema validation
- [ ] invalid response rejected
- [ ] deterministic mode works without Qwen

## Generation
- [x] local SFZ definitions exist for piano, bass, drums, pad, and strings
- [x] local starter WAV samples exist for all five instruments
- [x] current sample metadata audited: 44.1 kHz, mono, PCM 16-bit
- [ ] registry entries reference validated license IDs
- [ ] drum note map/channel convention validated
- [ ] every SFZ sample reference and WAV format validated in code
- [ ] local sample installation/Git portability policy documented
- [ ] compatible SFZ renderer installed/configured
- [ ] piano MIDI renders through `sounds/piano/piano.sfz`
- [ ] bass MIDI renders through `sounds/bass/bass.sfz`
- [ ] one bass stem
- [ ] correct duration
- [ ] correct sample rate
- [ ] no clipping

## Mixing
- [ ] piano only
- [ ] piano + bass
- [ ] mono/stereo
- [ ] gain/pan
- [ ] safe output

## End-to-end
- [ ] mix.wav
- [ ] repair
- [ ] LoFi
- [ ] master
- [ ] master.wav
- [ ] dry mode
- [ ] deterministic mode
- [ ] AI mode
