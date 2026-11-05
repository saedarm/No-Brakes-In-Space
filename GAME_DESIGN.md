# GAME_DESIGN.md

Tags: **[E]** decided · **[V]** suggestion · **[?]** open.
Only Sam moves things to [E].

## What the game is

- [E] Neon wireframe anti-gravity racer, inspired by Schnell. Tracks float in space
  with loops, corkscrews and tunnels; no gravity, you stick to the track surface.
- [E] My variant: **combat racer** — pickups, weapons, energy, getting knocked out.
- [E] libGDX, desktop only (core + lwjgl3). Keyboard controls.
- [V] Working title "Overclock".

## Driving

- [E] Drift + two-stage turbo (hold SHIFT while steering; release at half bar = short
  turbo, full bar = long turbo).
- [V] Top speed 450 km/h, ~740 km/h on turbo.
- [V] Corners push you to the outside, harder at speed ("you have to steer more").
- [V] Scraping walls drains energy but can never knock you out by itself.
- [?] Air brakes (Q/E) like Wipeout, or keep drift as the only advanced input?
- [?] Gamepad support.

## Combat

- [V] Pickup rows every ~320 m, three across. One item held at a time.
- [V] Items: MISSILE (homes on the racer ahead), MINES (three dropped behind),
  SHIELD (4 s immunity), TURBO.
- [V] Energy 100. Missile 35, mine 25. At 0 you're down for 2.5 s, back at 60.
- [?] Energy recharge — pit lane strip like Wipeout, or absorb pickups for energy?
- [?] More weapons: EMP (drains boost of everyone near), rear-firing missile, plasma.
- [?] Elimination mode (last ship running wins) as a second mode.

## Racers

- [V] 4 racers: you + 3 AI (VOLT, EMBER, GLINT) with different skill.
- [V] Light rubber-banding on AI so races stay close.
- [?] Pilot characters with portraits/reactions (Schnell has 8 pixel buddies).
- [?] Ship stats (speed / handling / armor) per pilot.

## Tracks

- [E] Tracks are Markdown tables in `assets/tracks/` (x, y, z, roll, tunnel).
- [V] Ringbahn (loop + hairpin + tunnels), Korkenzieher (corkscrew + helix).
- [?] Tracks are short (~10 s laps). Longer tracks, or more laps?
- [?] Track editor tool.

## Look and sound

- [V] Additive double-drawn lines for glow; distance fade instead of fog.
- [?] Real bloom pass (FBO + blur shader).
- [?] Pixel font for the HUD instead of the default libGDX font.
- [?] Music and SFX. Schnell built its own 303/808/909 machine — maybe a similar tool.

## Menus

- [?] Title screen, pilot select, track select. Right now the game boots straight into
  a race; R restarts, T cycles track.
