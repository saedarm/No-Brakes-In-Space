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
- [V] Energy recharge: a green recharge strip on the left third of the start straight,
  +30 energy/s while you're on it. AI heads for it when below 55 energy.
- [?] Also let you spend a held pickup on energy instead of firing it?
- [V] Hit feedback: debris burst (bigger on a knockout), shockwave ring on the track,
  ship flashes red, red screen flash, camera shake. Wall scrapes throw sparks and a
  small shake.
- [?] More weapons: EMP (drains boost of everyone near), rear-firing missile, plasma.
- [?] Elimination mode (last ship running wins) as a second mode.

## Racers

- [V] 4 racers: you + 3 AI (VOLT, EMBER, GLINT) with different skill.
- [V] Light rubber-banding on AI so races stay close.
- [?] Pilot characters with portraits/reactions (Schnell has 8 pixel buddies).
- [?] Ship stats (speed / handling / armor) per pilot.

## Tracks

- [E] Tracks are Markdown tables in `assets/tracks/` (x, y, z, roll, tunnel), with
  `width:`, `scale:` (multiplies the points) and `pit: A B` (recharge strip, metres).
- [V] Ringbahn (loop, hairpin, S-bend tunnel run; scale 2.5, ~35 s laps) and
  Korkenzieher (corkscrew, helix; scale 2).
- [?] Lap length feels right? 3 laps x ~35 s is about a 2-minute race.
- [?] Track editor tool.

## Look and sound

- [V] Additive double-drawn lines for glow; distance fade instead of fog.
- [?] Real bloom pass (FBO + blur shader).
- [?] Pixel font for the HUD instead of the default libGDX font.
- [?] Music and SFX. Schnell built its own 303/808/909 machine — maybe a similar tool.

## Menus

- [?] Title screen, pilot select, track select. Right now the game boots straight into
  a race; R restarts, T cycles track.
