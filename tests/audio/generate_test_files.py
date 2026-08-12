#!/usr/bin/env python3
"""
Generate test audio files for the AI Music Workstation regression test suite.
All files are 44.1 kHz, 16-bit WAV format.
"""

import os
import struct
import math
import numpy as np
from scipy.io import wavfile

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), 'audio')

def write_wav(filepath: str, sample_rate: int, data: np.ndarray):
    """Write a 16-bit WAV file."""
    wavfile.write(filepath, sample_rate, (data * 32767).astype(np.int16))

def generate_clean_melody():
    """44.1 kHz, 16-bit, mono, 10 seconds - clean sine wave melody."""
    sample_rate = 44100
    duration = 10  # seconds
    t = np.linspace(0, duration, sample_rate * duration)
    # Simple melody: A4, C5, E5, A5
    freqs = [440, 523.25, 659.25, 880]
    note_duration = duration / len(freqs)
    samples = np.zeros_like(t)
    for i, freq in enumerate(freqs):
        start = int(i * note_duration * sample_rate)
        end = int((i + 1) * note_duration * sample_rate)
        samples[start:end] = 0.5 * np.sin(2 * np.pi * freq * t[start:end])
    write_wav(os.path.join(OUTPUT_DIR, 'clean_melody.wav'), sample_rate, samples)

def generate_piano_melody():
    """44.1 kHz, 16-bit, stereo, 10 seconds."""
    sample_rate = 44100
    duration = 10
    t = np.linspace(0, duration, sample_rate * duration)
    freqs = [261.63, 329.63, 392.00, 523.25]  # C4, E4, G4, C5
    note_duration = duration / len(freqs)
    left = np.zeros_like(t)
    right = np.zeros_like(t)
    for i, freq in enumerate(freqs):
        start = int(i * note_duration * sample_rate)
        end = int((i + 1) * note_duration * sample_rate)
        signal = 0.4 * np.sin(2 * np.pi * freq * t[start:end])
        left[start:end] = signal
        right[start:end] = signal * 0.8  # Slightly quieter on right
    stereo = np.column_stack([left, right])
    write_wav(os.path.join(OUTPUT_DIR, 'piano_melody.wav'), sample_rate, stereo)

def generate_noisy_recording():
    """44.1 kHz, 16-bit, mono, 10 seconds with noise."""
    sample_rate = 44100
    duration = 10
    t = np.linspace(0, duration, sample_rate * duration)
    signal = 0.5 * np.sin(2 * np.pi * 440 * t)
    noise = 0.1 * np.random.randn(len(t))
    write_wav(os.path.join(OUTPUT_DIR, 'noisy_recording.wav'), sample_rate, signal + noise)

def generate_off_pitch():
    """44.1 kHz, 16-bit, mono, 5 seconds - slightly detuned."""
    sample_rate = 44100
    duration = 5
    t = np.linspace(0, duration, sample_rate * duration)
    # Intentionally off-pitch: 445 Hz instead of 440 Hz
    write_wav(os.path.join(OUTPUT_DIR, 'off_pitch.wav'), sample_rate,
              0.5 * np.sin(2 * np.pi * 445 * t))

def generate_full_song():
    """44.1 kHz, 16-bit, stereo, 60 seconds."""
    sample_rate = 44100
    duration = 60
    t = np.linspace(0, duration, sample_rate * duration)
    # Simple chord progression
    chords = [
        [261.63, 329.63, 392.00],  # C major
        [293.66, 369.99, 440.00],  # D major
        [329.63, 415.30, 493.88],  # E major
        [261.63, 329.63, 392.00],  # C major
    ]
    chord_duration = duration / len(chords)
    left = np.zeros_like(t)
    right = np.zeros_like(t)
    for i, chord in enumerate(chords):
        start = int(i * chord_duration * sample_rate)
        end = int((i + 1) * chord_duration * sample_rate)
        for freq in chord:
            phase = 2 * np.pi * freq * t[start:end]
            left[start:end] += 0.15 * np.sin(phase)
            right[start:end] += 0.12 * np.sin(phase + 0.1)
    write_wav(os.path.join(OUTPUT_DIR, 'full_song.wav'), sample_rate,
              np.column_stack([left, right]))

def generate_clipped():
    """44.1 kHz, 16-bit, stereo, 5 seconds - with clipping."""
    sample_rate = 44100
    duration = 5
    t = np.linspace(0, duration, sample_rate * duration)
    # Amplitude > 1.0 to cause clipping
    signal = 1.5 * np.sin(2 * np.pi * 440 * t)
    write_wav(os.path.join(OUTPUT_DIR, 'clipped.wav'), sample_rate, signal)

def generate_silence():
    """44.1 kHz, 16-bit, mono, 3 seconds - silence."""
    sample_rate = 44100
    duration = 3
    samples = np.zeros(sample_rate * duration)
    write_wav(os.path.join(OUTPUT_DIR, 'silence.wav'), sample_rate, samples)

def generate_dc_offset():
    """44.1 kHz, 16-bit, mono, 5 seconds - with DC offset."""
    sample_rate = 44100
    duration = 5
    t = np.linspace(0, duration, sample_rate * duration)
    signal = 0.3 * np.sin(2 * np.pi * 440 * t) + 0.3  # DC offset of 0.3
    write_wav(os.path.join(OUTPUT_DIR, 'dc_offset.wav'), sample_rate, signal)

def generate_click_tracks():
    """Generate click tracks at various BPM."""
    sample_rate = 44100
    durations = {
        '60_bpm': 5,
        '90_bpm': 5,
        '120_bpm': 5,
        '140_bpm': 5,
    }
    for bpm, duration in durations.items():
        beat_interval = 60.0 / int(bpm.split('_')[0])  # seconds per beat
        num_beats = int(duration / beat_interval)
        t = np.linspace(0, duration, sample_rate * duration)
        samples = np.zeros_like(t)
        for i in range(num_beats):
            beat_time = i * beat_interval
            # Short click at each beat
            click_start = int(beat_time * sample_rate)
            click_end = min(click_start + int(0.001 * sample_rate), len(samples))
            samples[click_start:click_end] = 1.0
        write_wav(os.path.join(OUTPUT_DIR, 'various_bpm', f'{bpm}.wav'), sample_rate, samples)

if __name__ == '__main__':
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(os.path.join(OUTPUT_DIR, 'various_bpm'), exist_ok=True)
    
    print("Generating test audio files...")
    generate_clean_melody()
    print("  ✓ clean_melody.wav")
    generate_piano_melody()
    print("  ✓ piano_melody.wav")
    generate_noisy_recording()
    print("  ✓ noisy_recording.wav")
    generate_off_pitch()
    print("  ✓ off_pitch.wav")
    generate_full_song()
    print("  ✓ full_song.wav")
    generate_clipped()
    print("  ✓ clipped.wav")
    generate_silence()
    print("  ✓ silence.wav")
    generate_dc_offset()
    print("  ✓ dc_offset.wav")
    generate_click_tracks()
    print("  ✓ various_bpm/*.wav")
    print("\nAll test files generated successfully!")
