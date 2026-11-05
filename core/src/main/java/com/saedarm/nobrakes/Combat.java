package com.saedarm.nobrakes;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Everything on the track that isn't a racer: pickups, missiles, mines and obstacles.
 * Like racers, they all live in track space (s, lat), so collision is just
 * "close in s and close in lat". No 3D physics needed.
 */
public class Combat {
    public static class Missile { Racer owner, target; public float s, lat, speed, life; }
    public static class Mine { Racer owner; public float s, lat, life, arm; }
    public static class Pickup { public float s, lat, cooldown; }

    /** Obstacle shapes. width = across the track, depth = along it (metres). */
    public enum ObstacleType {
        BLOCK(6f, 2.5f, 3f),     // wall chunk: crash
        SLIDER(5f, 2f, 2.5f),    // wall chunk that slides side to side: crash
        PYLON(1.6f, 1.6f, 2.6f); // small post: spin + a little damage
        public final float width, depth, height;
        ObstacleType(float w, float d, float h) { width = w; depth = d; height = h; }
    }
    public static class Obstacle { public ObstacleType type; public float s, lat, phase; }

    public static final float MISSILE_RANGE = 400f;
    /** Pickup odds, same order as Weapon: MISSILE, MINE, SHIELD, TURBO. Missiles kept rare on purpose. */
    public static final float[] WEAPON_ODDS = {0.15f, 0.30f, 0.25f, 0.30f};

    private static Weapon rollWeapon() {
        float roll = MathUtils.random(), sum = 0;
        for (int i = 0; i < WEAPON_ODDS.length; i++) {
            sum += WEAPON_ODDS[i];
            if (roll < sum) return Weapon.values()[i];
        }
        return Weapon.TURBO;
    }
    public static int missileHits, mineHits, missilesFired, crashes, pylonHits;   // stats (used by balance tests)
    public static final float CRASH_DAMAGE = 20f, PYLON_DAMAGE = 10f;
    public static final float SLIDER_PERIOD = 3.5f;

    public final Array<Missile> missiles = new Array<>();
    public final Array<Mine> mines = new Array<>();
    public final Array<Pickup> pickups = new Array<>();
    public final Array<Obstacle> obstacles = new Array<>();
    private final Track track;
    private float time;

    public Combat(Track track) {
        this.track = track;
        float third = track.width / 3f;
        for (float s = 150f; s < track.length - 60f; s += 320f) {
            for (int i = -1; i <= 1; i++) {
                Pickup p = new Pickup();
                p.s = s;
                p.lat = i * third;
                pickups.add(p);
            }
        }
        placeObstacles();
    }

    /**
     * Scatters `obstacles: N` from the track file. Same track = same layout every race
     * (seeded by the file name). Keeps clear of the start straight, the recharge strip,
     * pickup rows and each other, and never blocks the whole width.
     */
    private void placeObstacles() {
        java.util.Random rnd = new java.util.Random(track.id.hashCode());
        float half = track.width / 2f;
        int tries = 0;
        while (obstacles.size < track.obstacleCount && tries++ < 2000) {
            float s = 450f + rnd.nextFloat() * (track.length - 520f);
            if (track.inPit(s) || track.inPit(s + 40f)) continue;
            float row = ((s - 150f) % 320f + 320f) % 320f;          // distance past the last pickup row
            if (row < 30f || row > 290f) continue;
            boolean near = false;
            for (Obstacle o : obstacles) if (Math.abs(track.delta(o.s, s)) < 140f) { near = true; break; }
            if (near) continue;

            float roll = rnd.nextFloat();
            ObstacleType type = roll < 0.45f ? ObstacleType.BLOCK : roll < 0.7f ? ObstacleType.SLIDER : ObstacleType.PYLON;
            if (type == ObstacleType.PYLON) {
                // slalom: three posts 35 m apart, alternating either side of the centre line.
                // Weave through them, or hug a wall and skip the whole thing.
                float side = rnd.nextBoolean() ? 1f : -1f;
                for (int i = 0; i < 3; i++) add(type, s + i * 35f, side * (i % 2 == 0 ? 1f : -1f) * half * 0.3f, 0);
            } else {
                float lat = (rnd.nextFloat() * 2f - 1f) * (half - type.width / 2f - 1f);
                add(type, s, lat, rnd.nextFloat() * MathUtils.PI2);
            }
        }
    }

    private void add(ObstacleType type, float s, float lat, float phase) {
        Obstacle o = new Obstacle();
        o.type = type; o.s = track.wrap(s); o.lat = lat; o.phase = phase;
        obstacles.add(o);
    }

    public void update(float dt, Array<Racer> racers) {
        time += dt;
        // obstacles: sliders move, anyone touching one crashes (or spins, for pylons)
        float half = track.width / 2f;
        for (Obstacle o : obstacles) {
            if (o.type == ObstacleType.SLIDER)
                o.lat = MathUtils.sin(time * MathUtils.PI2 / SLIDER_PERIOD + o.phase) * (half - o.type.width / 2f - 0.5f);
            for (Racer r : racers) {
                if (r.respawn > 0 || r.crash > 0 || r.ghost > 0) continue;
                float ds = track.delta(r.s, o.s);
                if (Math.abs(ds) > o.type.depth / 2f + 1.6f || Math.abs(r.lat - o.lat) > o.type.width / 2f + 1.3f) continue;
                if (o.type == ObstacleType.PYLON) {
                    if (r.hit(PYLON_DAMAGE)) {
                        // knock the ship off the post so it can't get hit again while spinning on top of it
                        float side = r.lat >= o.lat ? 1f : -1f;
                        if (Math.abs(o.lat + side * (o.type.width / 2f + 1.8f)) > half - 0.9f) side = -side;
                        r.lat = o.lat + side * (o.type.width / 2f + 1.8f);
                        r.latVel = side * 8f;
                        r.ghost = 0.8f;
                        pylonHits++;
                    }
                } else if (r.crash(CRASH_DAMAGE)) {
                    crashes++;
                    float back = o.type.depth / 2f + 3f + ds;   // put the ship just in front of the block
                    r.s = track.wrap(r.s - back);
                    r.distance -= back;
                }
            }
        }

        // pickups
        for (Pickup p : pickups) {
            p.cooldown -= dt;
            if (p.cooldown > 0) continue;
            for (Racer r : racers) {
                if (r.weapon != null || r.respawn > 0) continue;
                if (Math.abs(track.delta(r.s, p.s)) < 2.5f && Math.abs(r.lat - p.lat) < 1.8f) {
                    r.weapon = rollWeapon();
                    p.cooldown = 5f;
                    if (r.isPlayer) Sfx.play(Sfx.PICKUP, 0.7f);
                    break;
                }
            }
        }

        // missiles: fly forward along the track, home in on target's lane
        for (int i = missiles.size - 1; i >= 0; i--) {
            Missile m = missiles.get(i);
            m.life -= dt;
            m.s = track.wrap(m.s + m.speed * dt);
            if (m.target != null) m.lat = MathUtils.lerp(m.lat, m.target.lat, Math.min(1f, 3f * dt));
            boolean dead = m.life <= 0;
            for (Racer r : racers) {
                if (dead || r == m.owner || r.respawn > 0) continue;
                if (Math.abs(track.delta(r.s, m.s)) < 2.5f && Math.abs(r.lat - m.lat) < 1.6f) {
                    if (r.hit(35f)) missileHits++;
                    dead = true;
                }
            }
            if (dead) missiles.removeIndex(i);
        }

        // mines: sit still, arm after a moment (so you don't hit your own)
        for (int i = mines.size - 1; i >= 0; i--) {
            Mine m = mines.get(i);
            m.life -= dt;
            m.arm -= dt;
            boolean dead = m.life <= 0;
            for (Racer r : racers) {
                if (dead || r.respawn > 0 || (r == m.owner && m.arm > 0)) continue;
                if (Math.abs(track.delta(r.s, m.s)) < 1.8f && Math.abs(r.lat - m.lat) < 1.5f) {
                    if (r.hit(25f)) mineHits++;
                    dead = true;
                }
            }
            if (dead) mines.removeIndex(i);
        }
    }

    public void fire(Racer r, Array<Racer> racers) {
        if (r.weapon == null || r.respawn > 0) return;
        switch (r.weapon) {
            case MISSILE: {
                Missile m = new Missile();
                m.owner = r;
                m.target = targetAhead(r, racers, MISSILE_RANGE);
                m.s = track.wrap(r.s + 3f);
                m.lat = r.lat;
                m.speed = r.speed + 70f;
                m.life = 4f;
                missiles.add(m);
                missilesFired++;
                Sfx.at(Sfx.MISSILE, r.s, 0.9f);
                break;
            }
            case MINE: {
                for (int i = -1; i <= 1; i++) {
                    Mine m = new Mine();
                    m.owner = r;
                    m.s = track.wrap(r.s - 4f - Math.abs(i) * 3f);
                    m.lat = MathUtils.clamp(r.lat + i * 2.2f, -track.width / 2 + 1, track.width / 2 - 1);
                    m.arm = 0.6f;
                    m.life = 25f;
                    mines.add(m);
                }
                Sfx.at(Sfx.MINE, r.s, 0.9f);
                break;
            }
            case SHIELD: r.shield = 4f; Sfx.at(Sfx.SHIELD, r.s, 0.8f); break;
            case TURBO: r.turbo = Math.max(r.turbo, 1.6f); Sfx.at(Sfx.TURBO, r.s, 0.8f); break;
        }
        r.weapon = null;
    }

    /** AI decides when to use what it is holding. */
    public void aiUse(float dt, Racer r, Array<Racer> racers) {
        if (r.weapon == null) return;
        // a missile is coming and we hold a shield: raise it now, no hesitation
        if (r.weapon == Weapon.SHIELD && incoming(r, 60f + 60f * r.awareness) != null) {
            fire(r, racers);
            return;
        }
        r.fireDelay -= dt;
        if (r.fireDelay > 0) return;
        boolean use;
        switch (r.weapon) {
            case MISSILE: use = targetAhead(r, racers, 200f) != null; break;
            case MINE: use = someoneBehind(r, racers, 80f); break;
            case SHIELD: use = r.energy < 30f; break;   // otherwise save it for missiles
            default: use = Math.abs(track.curvatureAt(r.s + 60f)) < 0.01f; break;
        }
        if (use) {
            fire(r, racers);
            r.fireDelay = MathUtils.random(0.5f, 2f);
        }
    }

    /** Debug: launch a missile at target from `behind` metres back, owned by `owner`. */
    public void debugMissileAt(Racer target, Racer owner, float behind) {
        Missile m = new Missile();
        m.owner = owner;
        m.target = target;
        m.s = track.wrap(target.s - behind);
        m.lat = target.lat;
        m.speed = target.speed + 70f;
        m.life = 6f;
        missiles.add(m);
        Sfx.at(Sfx.MISSILE, m.s, 0.9f);
    }

    /** Nearest mine in r's path within range metres ahead (ignores r's own unarmed mines). */
    public Mine mineAhead(Racer r, float range) {
        Mine best = null;
        float bestD = range;
        for (Mine m : mines) {
            if (m.owner == r && m.arm > 0) continue;
            float d = track.delta(m.s, r.s);
            if (d > 0 && d < bestD && Math.abs(m.lat - r.lat) < 3.2f) { bestD = d; best = m; }
        }
        return best;
    }

    /** Lane (lat) closest to `want` that passes every mine within `range` ahead with room to spare. */
    /** Where an obstacle will be `seconds` from now (sliders move; everything else stays put). */
    public float latAt(Obstacle o, float seconds) {
        if (o.type != ObstacleType.SLIDER) return o.lat;
        float half = track.width / 2f;
        return MathUtils.sin((time + seconds) * MathUtils.PI2 / SLIDER_PERIOD + o.phase) * (half - o.type.width / 2f - 0.5f);
    }

    /** Distance to the nearest mine or obstacle ahead of r within range (anywhere across the track), or -1. */
    private float nearestHazard(Racer r, float range) {
        float best = -1;
        for (Mine m : mines) {
            if (m.owner == r && m.arm > 0) continue;
            float d = track.delta(m.s, r.s);
            if (d > 0 && d < range && (best < 0 || d < best)) best = d;
        }
        for (Obstacle o : obstacles) {
            float d = track.delta(o.s, r.s);
            if (d > 0 && d < range && (best < 0 || d < best)) best = d;
        }
        return best;
    }

    /** Is there a mine or obstacle in r's path within range metres? */
    public boolean hazardAhead(Racer r, float range) {
        if (mineAhead(r, range) != null) return true;
        for (Obstacle o : obstacles) {
            float d = track.delta(o.s, r.s);
            float lat = latAt(o, d / Math.max(20f, r.speed));
            if (d > 0 && d < range && Math.abs(lat - r.lat) < o.type.width / 2f + 3f) return true;
        }
        return false;
    }

    /**
     * Lane (lat) closest to `want` that clears the next group of hazards: everything within
     * 75 m of the nearest one (a whole pylon slalom, or a fan of mines). Sliders are judged
     * by where they'll be when we arrive.
     */
    public float clearLane(Racer r, float range, float want, float half) {
        float near = nearestHazard(r, range);
        if (near < 0) return want;
        float lo = near - 1f, hi = near + 75f;
        float best = want, bestCost = Float.MAX_VALUE;
        for (float lane = -half; lane <= half + 0.01f; lane += 0.5f) {
            boolean clear = true;
            for (Mine m : mines) {
                if (m.owner == r && m.arm > 0) continue;
                float d = track.delta(m.s, r.s);
                if (d > lo && d < hi && (Math.abs(m.lat - lane) < 2.4f || mustCross(r, m.lat, 2.4f, lane, d))) { clear = false; break; }
            }
            for (Obstacle o : obstacles) {
                if (!clear) break;
                float d = track.delta(o.s, r.s);
                if (d <= lo || d >= hi) continue;
                float lat = latAt(o, d / Math.max(20f, r.speed));
                float margin = o.type.width / 2f + 2.2f;
                if (Math.abs(lat - lane) < margin || mustCross(r, lat, margin, lane, d)) clear = false;
            }
            if (!clear) continue;
            float cost = Math.abs(lane - want) + 0.5f * Math.abs(lane - r.lat);
            if (cost < bestCost) { bestCost = cost; best = lane; }
        }
        return best;
    }

    /**
     * True if getting to `lane` means crossing in front of a hazard at `hazLat` that's
     * `d` metres ahead, and there isn't enough time to get across before reaching it.
     */
    private static boolean mustCross(Racer r, float hazLat, float margin, float lane, float d) {
        if (Math.signum(lane - hazLat) == Math.signum(r.lat - hazLat)) return false;   // same side: no crossing
        float need = Math.abs(r.lat - hazLat) + margin;
        float can = Racer.STEER_SPEED * 0.8f * d / Math.max(20f, r.speed);
        return need > can;
    }

    /** Closest missile locked on r and within range metres behind it. */
    public Missile incoming(Racer r, float range) {
        Missile best = null;
        float bestD = range;
        for (Missile m : missiles) {
            if (m.target != r) continue;
            float d = track.delta(r.s, m.s);
            if (d > 0 && d < bestD) { bestD = d; best = m; }
        }
        return best;
    }

    /** What a missile fired by r right now would lock on to (null if nobody in range). */
    public Racer lockTarget(Racer r, Array<Racer> racers) {
        return targetAhead(r, racers, MISSILE_RANGE);
    }

    private Racer targetAhead(Racer r, Array<Racer> racers, float range) {
        Racer best = null;
        float bestD = range;
        for (Racer o : racers) {
            if (o == r || o.respawn > 0) continue;
            float d = o.distance - r.distance;
            if (d > 0 && d < bestD) { bestD = d; best = o; }
        }
        return best;
    }

    private boolean someoneBehind(Racer r, Array<Racer> racers, float range) {
        for (Racer o : racers) {
            if (o == r) continue;
            float d = r.distance - o.distance;
            if (d > 0 && d < range) return true;
        }
        return false;
    }
}
