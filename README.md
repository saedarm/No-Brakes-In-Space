# Overclock

A neon anti-gravity combat racer in libGDX. Glowing wireframe tracks float in space,
with loops, corkscrews and tunnels. Grab pickups and knock out your rivals with
missiles and mines.

## Run

    ./gradlew lwjgl3:run

Runnable jar: `./gradlew lwjgl3:jar` → `lwjgl3/build/libs/Overclock.jar`

## Controls

| Key | Action |
|-----|--------|
| Up / W | Accelerate |
| Down / S | Brake |
| Left/Right, A/D | Steer |
| Shift + steer | Drift (charges turbo; release to fire it) |
| Space | Use item |
| R | Restart |
| T | Next track |
| Esc | Back to menu |
| F1 / F2 | Debug: get a missile / fire one at yourself |

Balance check (headless AI races): `./gradlew balance`
Regenerate sound effects: `python tools/make_sfx.py`

Read `AGENTS.md` for how the code works and `GAME_DESIGN.md` for what's decided.
