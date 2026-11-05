# AGENTS.md — read this first

Overclock is a neon anti-gravity **combat** racer in libGDX (Java 17, desktop only:
`core` + `lwjgl3`). Inspired by Schnell (deaddreamer99), with weapons.

Every session: read this file, then `GAME_DESIGN.md`, then work.
Write decisions into `GAME_DESIGN.md`, not into chat.

## Rules for agents

- `GAME_DESIGN.md` tags every point: **[E]** decided, **[V]** suggestion, **[?]** open.
  Only [E] is binding. Never treat a [V] as decided. Don't promote anything to [E]
  yourself — Sam does that.
- Keep it lean. No new Gradle modules, no frameworks, no Scene2D/Ashley unless an
  [E] item says so. Add a file only when it earns its keep.
- After a change: `./gradlew lwjgl3:run` must still start and a race must finish.
- Tuning numbers live as constants at the top of `Racer.java` and `Combat.java`.
  Change those, don't scatter magic numbers.

## How it works (one paragraph per file)

- `Track.java` — loads `assets/tracks/*.md` (a Markdown table of control points with
  roll and tunnel type), runs a closed Catmull-Rom spline through them, and resamples
  it into frames ~1 m apart. Each frame has `pos`, `fwd`, `up`, `right`. "Up" is
  parallel-transported so it never flips in loops, then rotated by the designer's roll.
- `Racer.java` — a glider in **track space**: `s` (distance along) and `lat` (left/right).
  No gravity anywhere; world position = `track.point(s, lat, hover)`. Holds speed,
  drift → two-stage turbo, energy, spin-outs, respawn, and a simple AI driver.
- `Combat.java` — pickups, missiles (home on the racer ahead), mines (dropped behind),
  shield, turbo. Also in track space, so a hit is just "close in s and close in lat".
- `NeonRenderer.java` — draws everything as lines with additive blending, twice
  (thick + faint, thin + bright) for a cheap glow. Static track lines are baked once.
- Effects (debris, sparks, shockwave rings) live in `NeonRenderer.update()`. Racers
  just raise flags (`burst`, `scraping`); the renderer spawns the effect and clears them.
- `Hud.java` — speed (km/h), lap, place, weapon, energy and drift bars, minimap.
- `RaceScreen.java` — input, update order, ranking, chase camera (camera "up" follows
  the track so loops roll the world).
- `OverclockGame.java` — the track list. `Lwjgl3Launcher` — window config.

## Adding a track

1. Copy `assets/tracks/ringbahn.md`, edit the table. Roll is degrees (+ = right edge
   down). Tunnel column applies until the next point: `-`, `oct`, `box`.
   `scale:` multiplies every point (sketch small, blow it up). `pit: A B` puts the
   recharge strip from A to B metres after the start line.
2. Add the path to `OverclockGame.TRACKS`.
3. Press **T** in game to cycle tracks.

## Gotchas

- libGDX `Array` reuses its iterator; we set `Collections.allocateIterators = true`
  because loops over racers are nested.
- `glLineWidth > 1` needs the default (compatibility) GL context. Don't switch the
  launcher to GL30/core profile without replacing line rendering with quads.
