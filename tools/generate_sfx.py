#!/usr/bin/env python3
"""Generate game sound effects (WAV) using Python wave + math synthesis."""

import math
import os
import struct
import random
import wave

AUDIO = os.path.join(os.path.dirname(__file__), "..", "client", "src", "main", "resources", "audio")
SR = 44100  # sample rate

def wav_write(path, samples):
    """Write mono 16-bit PCM WAV from list of floats in [-1, 1]."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "w") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        # Convert to int16
        data = [max(-32767, min(32767, int(s * 32767))) for s in samples]
        f.writeframes(struct.pack(f"<{len(data)}h", *data))
    return os.path.getsize(path)

def envelope(n, attack_pct=0.05, decay_pct=0.3, sustain_level=0.3):
    """ADSR-like: linear attack, exponential-ish decay, sustain, then fade."""
    attack = max(1, int(n * attack_pct))
    decay = max(1, int(n * decay_pct))
    release = max(1, int(n * 0.15))
    env = []
    for i in range(n):
        if i < attack:
            env.append(i / attack)
        elif i < attack + decay:
            t = (i - attack) / decay
            env.append(1.0 - (1.0 - sustain_level) * t)
        elif i < n - release:
            env.append(sustain_level)
        else:
            t = (i - (n - release)) / release
            env.append(sustain_level * (1.0 - t))
    return env

def sine(freq, duration, amp=0.8):
    """Generate sine wave samples."""
    n = int(SR * duration)
    return [amp * math.sin(2 * math.pi * freq * i / SR) for i in range(n)]

def noise(duration, amp=0.5):
    """White noise."""
    n = int(SR * duration)
    return [amp * (random.random() * 2 - 1) for _ in range(n)]

def mix(*signals):
    """Mix multiple signals (element-wise sum, clamped)."""
    if not signals:
        return []
    n = len(signals[0])
    result = []
    for i in range(n):
        s = sum(sig[i] for sig in signals if i < len(sig))
        result.append(max(-1.0, min(1.0, s)))
    return result

def apply_env(samples, env):
    n = min(len(samples), len(env))
    return [samples[i] * env[i] for i in range(n)]

# ── SFX generators ──────────────────────────────────

def gen_shoot_pistol():
    """Short crisp pop: noise burst + mid freq."""
    dur = 0.15
    n = int(SR * dur)
    env = envelope(n, 0.01, 0.08, 0.05)
    sig = noise(dur, 0.4)
    # Add a 800Hz tone for "pop"
    tone = sine(800, dur, 0.3)
    sig = [sig[i] + tone[i] for i in range(n)]
    sig = [sig[i] * env[i] for i in range(n)]
    return sig

def gen_shoot_rifle():
    """Deeper boom: low freq + noise."""
    dur = 0.3
    n = int(SR * dur)
    env = envelope(n, 0.02, 0.1, 0.1)
    noise_sig = noise(dur, 0.5)
    low = sine(120, dur, 0.6)
    mid = sine(400, dur, 0.2)
    sig = mix(noise_sig, low, mid)
    sig = apply_env(sig, env)
    return sig

def gen_explosion():
    """Rumble: long noise with low freq sweep."""
    dur = 0.8
    n = int(SR * dur)
    env = envelope(n, 0.02, 0.15, 0.1)
    noise_sig = noise(dur, 0.7)
    # frequency sweep 150→30 Hz
    sweep = []
    for i in range(n):
        t = i / n
        f = 150 - 120 * t
        sweep.append(0.5 * math.sin(2 * math.pi * f * i / SR))
    sig = mix(noise_sig, sweep)
    sig = apply_env(sig, env)
    return sig

def gen_hit_marker():
    """Ping: short high tone."""
    dur = 0.08
    n = int(SR * dur)
    env = envelope(n, 0.01, 0.5, 0.0)
    # Two tones
    hi = sine(2200, dur, 0.4)
    mid = sine(1500, dur, 0.3)
    sig = mix(hi, mid)
    sig = apply_env(sig, env)
    return sig

def gen_footsteps():
    """Soft tap: very short noise burst."""
    dur = 0.04
    n = int(SR * dur)
    env = envelope(n, 0.01, 0.5, 0.0)
    sig = noise(dur, 0.3)
    # Add low thud
    low = sine(180, dur, 0.2)
    sig = mix(sig, low)
    sig = apply_env(sig, env)
    return sig

def gen_reload():
    """Mechanical click: two short transients."""
    dur = 0.25
    n = int(SR * dur)
    sig = [0.0] * n
    # Click 1 at 0ms
    c1_start = 0
    c1_len = int(SR * 0.03)
    for i in range(c1_len):
        sig[c1_start + i] += (random.random() * 2 - 1) * 0.5 * (1 - i / c1_len)
    # Click 2 at 150ms
    c2_start = int(SR * 0.15)
    c2_len = int(SR * 0.04)
    for i in range(c2_len):
        sig[c2_start + i] += (random.random() * 2 - 1) * 0.4 * (1 - i / c2_len)
    return sig

def gen_error():
    """Error buzz: low discordant tone."""
    dur = 0.3
    n = int(SR * dur)
    env = envelope(n, 0.02, 0.3, 0.1)
    # Two clashing tones
    a = sine(200, dur, 0.5)
    b = sine(250, dur, 0.4)
    noise_sig = noise(dur, 0.15)
    sig = mix(a, b, noise_sig)
    sig = apply_env(sig, env)
    return sig

def gen_hover():
    """UI hover: soft tick."""
    dur = 0.05
    n = int(SR * dur)
    env = envelope(n, 0.01, 0.4, 0.0)
    tone = sine(1200, dur, 0.3)
    sig = apply_env(tone, env)
    return sig

# ── Main ─────────────────────────────────────────────

SFX = {
    "sfx/shoot_pistol.wav":  gen_shoot_pistol,
    "sfx/shoot_rifle.wav":   gen_shoot_rifle,
    "sfx/explosion.wav":     gen_explosion,
    "sfx/hit_marker.wav":    gen_hit_marker,
    "sfx/footsteps.wav":     gen_footsteps,
    "sfx/reload.wav":        gen_reload,
    "ui/error.wav":          gen_error,
    "ui/hover.wav":          gen_hover,
}

if __name__ == "__main__":
    for path, gen_fn in SFX.items():
        full = os.path.join(AUDIO, path)
        sig = gen_fn()
        size = wav_write(full, sig)
        dur = len(sig) / SR
        print(f"  ✓ {path} ({dur:.2f}s, {size} bytes)")
    print(f"\n{len(SFX)} SFX generated → {AUDIO}")
