"""Generates every sound effect in assets/sfx as a small 16-bit mono WAV.

No samples, no dependencies: plain Python. Tweak a recipe, re-run:
    python tools/make_sfx.py
Loops (engine, recharge) use whole-number cycles per second so they loop cleanly.
"""
import math, os, random, struct, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sfx")
random.seed(7)


def save(name, samples):
    os.makedirs(OUT, exist_ok=True)
    peak = max(1e-6, max(abs(s) for s in samples))
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.9 * 32767)) for s in samples))


def n(sec): return int(sec * RATE)
def saw(ph): return 2 * (ph % 1.0) - 1
def sq(ph): return 1.0 if (ph % 1.0) < 0.5 else -1.0
def env(i, total, attack=0.005, release=None):
    t = i / RATE; T = total / RATE
    a = min(1, t / attack) if attack > 0 else 1
    r = 1 - t / T if release is None else min(1, (T - t) / release)
    return max(0, a * r)


def lowpass(xs, k):
    y, out = 0.0, []
    for x in xs:
        y += k * (x - y); out.append(y)
    return out


def sweep(f0, f1, sec, wavef=math.sin, curve=1.0):
    total, ph, out = n(sec), 0.0, []
    for i in range(total):
        t = i / total
        f = f0 + (f1 - f0) * t ** curve
        ph += f / RATE
        out.append(wavef(ph * (2 * math.pi if wavef is math.sin else 1)))
    return out


def noise(sec): return [random.uniform(-1, 1) for _ in range(n(sec))]

# --- engine: 1 s loop, 55 Hz family, pitch is shifted in game by speed
total = n(1.0)
eng = lowpass([0.5 * saw(55 * i / RATE) + 0.3 * sq(110 * i / RATE) + 0.2 * saw(165 * i / RATE)
               + 0.15 * random.uniform(-1, 1) for i in range(total)], 0.25)
save("engine", eng)

# --- recharge hum: 1 s loop with 8 Hz tremolo
save("recharge", [(math.sin(2 * math.pi * 440 * i / RATE) + 0.5 * math.sin(2 * math.pi * 660 * i / RATE))
                  * (0.6 + 0.4 * math.sin(2 * math.pi * 8 * i / RATE)) for i in range(n(1.0))])

# --- countdown
b = n(0.15); save("beep", [sq(660 * i / RATE) * env(i, b) for i in range(b)])
g = n(0.45); save("go", [sq(1320 * i / RATE) * env(i, g) for i in range(g)])

# --- pickup: quick rising arpeggio
pk = []
for f in (660, 880, 1320):
    m = n(0.06); pk += [sq(f * i / RATE) * env(i, m) * 0.7 for i in range(m)]
save("pickup", pk)

# --- missile launch: noise hiss + falling tone
mt = n(0.45)
hiss = lowpass(noise(0.45), 0.5)
tone = sweep(900, 250, 0.45, saw)
save("missile", [(0.6 * hiss[i] + 0.5 * tone[i]) * env(i, mt) for i in range(mt)])

# --- mine drop: low thunk
md = n(0.18); save("mine", [s * env(i, md) for i, s in enumerate(sweep(140, 50, 0.18))])

# --- hit: crunchy burst + thump
ht = n(0.4)
hn = lowpass(noise(0.4), 0.35)
save("hit", [(hn[i] + 0.8 * math.sin(2 * math.pi * 80 * i / RATE)) * (1 - i / ht) ** 2 for i in range(ht)])

# --- knockout: long rumbling explosion
ko = n(1.1)
kn = lowpass(noise(1.1), 0.08)
save("ko", [(kn[i] * 3 + 0.4 * math.sin(2 * math.pi * 50 * i / RATE)) * (1 - i / ko) ** 1.5 for i in range(ko)])

# --- turbo: rising whoosh
tb = n(0.7)
tn = lowpass(noise(0.7), 0.2)
tt = sweep(200, 1200, 0.7, saw, 0.7)
save("turbo", [(0.7 * tn[i] + 0.4 * tt[i]) * env(i, tb, 0.05) for i in range(tb)])

# --- shield up: shimmering rise
sh = n(0.45)
st = sweep(500, 1500, 0.45)
save("shield", [(st[i] * (0.6 + 0.4 * math.sin(2 * math.pi * 30 * i / RATE))) * env(i, sh, 0.02) for i in range(sh)])

# --- lock-on warning blip
lk = n(0.07); save("lock", [sq(1760 * i / RATE) * env(i, lk, 0.002) for i in range(lk)])

# --- wall scrape: short grinding burst (played repeatedly while scraping)
sc = n(0.12)
sn = noise(0.12)
save("scrape", [sn[i] * sq(300 * i / RATE) * env(i, sc) for i in range(sc)])

print("wrote", sorted(os.listdir(OUT)))
