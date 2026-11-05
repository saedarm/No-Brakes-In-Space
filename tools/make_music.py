"""Generates the lo-fi techno loops in assets/music (one per track + menu).

Plain Python, no samples. Each song is a list of chords, a tempo and a few knobs;
drums, bass, pads (ducked by the kick for that pumping feel), a sparse pluck melody,
then "lo-fi" treatment: soft saturation, low-pass, vinyl crackle.
Writes WAV, then converts to OGG with ffmpeg (needed for the small files).
    python tools/make_music.py
"""
import math, os, random, struct, subprocess, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "music")

# chord = list of MIDI notes (bass note first)
SONGS = {
    "menu": dict(bpm=84, bars_per_chord=2, seed=1, kick="lofi", hats=0.5,
                 chords=[[40, 59, 62, 66, 71], [36, 55, 59, 62, 64], [43, 59, 62, 66, 69], [38, 57, 60, 64, 66]]),
    "ringbahn": dict(bpm=112, bars_per_chord=2, seed=2, kick="four", hats=1.0,
                     chords=[[45, 57, 60, 64, 67], [41, 57, 60, 64, 65], [36, 55, 59, 64, 67], [43, 55, 59, 62, 64]]),
    "korkenzieher": dict(bpm=124, bars_per_chord=2, seed=3, kick="four", hats=1.0,
                         chords=[[38, 57, 60, 64, 65], [34, 53, 57, 62, 65], [43, 55, 58, 62, 65], [45, 55, 57, 61, 64]]),
}


def hz(m): return 440.0 * 2 ** ((m - 69) / 12)


def render(name, bpm, chords, bars_per_chord, seed, kick, hats):
    random.seed(seed)
    beat = 60.0 / bpm
    bars = len(chords) * bars_per_chord * 2          # progression plays twice
    total = int(bars * 4 * beat * RATE)
    mix = [0.0] * total
    duck = [1.0] * total

    def add(start, samples, vol):
        for i, s in enumerate(samples):
            j = (start + i) % total                    # wrap tails so the loop is seamless
            mix[j] += s * vol

    def kick_hit():
        n, ph, out = int(0.28 * RATE), 0.0, []
        for i in range(n):
            t = i / RATE
            ph += (45 + 75 * math.exp(-t * 30)) / RATE
            out.append(math.sin(2 * math.pi * ph) * math.exp(-t * 9))
        return out

    def noise_hit(sec, decay, lp):
        y, out = 0.0, []
        for i in range(int(sec * RATE)):
            x = random.uniform(-1, 1)
            y += lp * (x - y)
            out.append((x - y if lp < 0.5 else y) * math.exp(-i / RATE * decay))
        return out

    K, H, S = kick_hit(), noise_hit(0.05, 60, 0.3), noise_hit(0.25, 14, 0.6)
    swing = 0.06 * beat

    for bar in range(bars):
        for b in range(4):
            t0 = int((bar * 4 + b) * beat * RATE)
            # kick: four on the floor, or a lazier lo-fi pattern
            if kick == "four" or (b == 0 or (b == 2 and bar % 2 == 1)):
                add(t0, K, 0.9)
                for i in range(int(0.35 * beat * RATE)):   # sidechain duck for pads/bass
                    j = (t0 + i) % total
                    duck[j] = min(duck[j], 0.35 + 0.65 * i / (0.35 * beat * RATE))
            if b in (1, 3):
                add(t0, S, 0.35)
            # hats on offbeats (swung), plus quiet 16ths
            add(t0 + int(0.5 * beat * RATE + swing * RATE), H, 0.35 * hats)
            if hats >= 1 and random.random() < 0.6:
                add(t0 + int(0.25 * beat * RATE), H, 0.12)
                add(t0 + int(0.75 * beat * RATE + swing * RATE), H, 0.12)

    pad = [0.0] * total
    bass = [0.0] * total
    bar_len = 4 * beat
    chord_len = bars_per_chord * bar_len
    for ci in range(bars // bars_per_chord):
        chord = chords[ci % len(chords)]
        start = int(ci * chord_len * RATE)
        n = int(chord_len * RATE)
        # pad: detuned triangles, slow swell
        for note in chord[1:]:
            f1, f2 = hz(note) * 1.003, hz(note) * 0.997
            for i in range(n):
                t = i / RATE
                tri = (2 / math.pi) * (math.asin(math.sin(2 * math.pi * f1 * t)) + math.asin(math.sin(2 * math.pi * f2 * t)))
                swell = min(1, t / 0.4) * min(1, (n - i) / RATE / 0.3)
                pad[(start + i) % total] += tri * swell * 0.07
        # bass: offbeat 8ths on the root, filtered saw
        f = hz(chord[0])
        steps = int(chord_len / (beat / 2))
        for k in range(steps):
            if k % 2 == 0 and random.random() < 0.7:
                continue
            s0 = start + int(k * beat / 2 * RATE)
            m = int(beat / 2 * 0.9 * RATE)
            y = 0.0
            for i in range(m):
                x = 2 * ((f * i / RATE) % 1) - 1
                y += 0.12 * (x - y)
                bass[(s0 + i) % total] += y * math.exp(-i / RATE * 5) * 0.55
        # pluck melody: a few notes from the chord, an octave up, with echo
        for k in range(bars_per_chord * 8):
            if random.random() > 0.28:
                continue
            note = random.choice(chord[1:]) + 12
            p0 = start + int(k * beat / 2 * RATE)
            pl = [math.sin(2 * math.pi * hz(note) * i / RATE) * math.exp(-i / RATE * 7) for i in range(int(0.5 * RATE))]
            add(p0, pl, 0.16)
            add(p0 + int(0.75 * beat * RATE), pl, 0.07)

    # lo-fi: duck pads/bass with the kick, saturate, low-pass, add crackle
    out, y = [], 0.0
    for i in range(total):
        x = mix[i] + (pad[i] + bass[i]) * duck[i]
        x = math.tanh(1.4 * x)
        y += 0.32 * (x - y)
        crackle = (random.uniform(-1, 1) * 0.5 if random.random() < 0.0006 else 0) + random.uniform(-1, 1) * 0.006
        out.append(y + crackle)
    peak = max(abs(v) for v in out)
    return [v / peak * 0.85 for v in out]


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, cfg in SONGS.items():
        samples = render(name, **cfg)
        wav = os.path.join(OUT, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in samples))
        ogg = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", ogg], check=True)
        os.remove(wav)
        print(f"{name}: {len(samples) / RATE:.0f} s -> {ogg}")


if __name__ == "__main__":
    main()
