# AGENTS.md — read this first

No Brakes in Space is a neon anti-gravity **combat** racer in libGDX (Java 17, desktop only:
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
- `Combat.java` — everything on the track that isn't a racer: pickups, missiles (home on
  the racer ahead), mines (dropped behind), shield, turbo, and obstacles (blocks, sliders,
  pylons). All in track space, so a hit is just "close in s and close in lat". AI lane
  choice (`clearLane`) handles one row of hazards at a time and predicts sliders.
- `Alien.java` — the six pilot species as tiny bitmaps; draws them flat (HUD/menu) or as
  3D billboards (on the glider, flying out on a crash, on the podium).
- `PodiumScreen.java` — the award ceremony after a race.
- `NeonRenderer.java` — draws everything as lines with additive blending, twice
  (thick + faint, thin + bright) for a cheap glow. Static track lines are baked once.
- Effects (debris, sparks, shockwave rings) live in `NeonRenderer.update()`. Racers
  just raise flags (`burst`, `scraping`); the renderer spawns the effect and clears them.
- `Hud.java` — speed (km/h), lap, place, weapon, energy and drift bars, minimap.
- `RaceScreen.java` — input, update order, ranking, chase camera (camera "up" follows
  the track so loops roll the world).
- `Engine.java` — dark-matter speed classes (50/100/150/200 DM). `Racer.engine` holds the
  multiplier; use `racer.topSpeed()` rather than `MAX_SPEED` for anything speed-relative.
- `MenuScreen.java` — title + track select with an orbiting preview (reuses NeonRenderer).
- `Sfx.java` — static sound helper. `Sfx.at(name, s, vol)` fades by track distance from
  the player. WAVs come from `tools/make_sfx.py` (edit a recipe, re-run it).
- `BalanceSim.java` — headless AI-vs-AI races on every track; `./gradlew balance` (or
  `-Pengine=DM200`) prints per-track length, tightest corner, AI lap time, missile hit rate,
  mine hits, crashes, pylon hits, knockouts. Run it after touching tracks, weapons, AI or
  speeds. A tightest corner of a few metres means the track has a kink.
- `NoBrakesGame.java` — the track list and screen switching. `Lwjgl3Launcher` — window config.

## Debug keys (in a race)

- **F1** — give yourself a missile. **F2** — fire a missile at yourself from 150 m back.
- **F3** — end the race now and jump to the award ceremony.

## Adding a track

1. Copy `assets/tracks/ringbahn.md`, edit the table. Roll is degrees (+ = right edge
   down). Tunnel column applies until the next point: `-`, `oct`, `box`.
   `scale:` multiplies every point (sketch small, blow it up). `pit: A B` puts the
   recharge strip from A to B metres after the start line. `obstacles: N` scatters N
   obstacle spots (seeded by file name, so the same every race). `tagline:` shows on
   the menu. Music: drop `assets/music/<file name>.ogg` (or add it to make_music.py).
   The spline is centripetal Catmull-Rom, so uneven point spacing is fine.
2. Add the path to `NoBrakesGame.TRACKS`, then run `./gradlew balance` to check it.
3. It shows up in the menu automatically; **T** in a race also cycles tracks.

## Gotchas

- libGDX `Array` reuses its iterator; we set `Collections.allocateIterators = true`
  because loops over racers are nested.
- Screens: sound loops start in `RaceScreen.show()` and stop in `hide()` — not in the
  constructor/dispose, because the new screen is shown before the old one is disposed.
- Pixel aliens and confetti are filled quads through `ImmediateModeRenderer20` (the
  ShapeRenderer can't fill in 3D). Keep each batch under its vertex limit (30000).
- `glLineWidth > 1` needs the default (compatibility) GL context. Don't switch the
  launcher to GL30/core profile without replacing line rendering with quads.
