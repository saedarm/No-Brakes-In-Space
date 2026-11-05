package com.saedarm.overclock;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Pickups, missiles and mines. Like racers, they all live in track space (s, lat),
 * so collision is just "close in s and close in lat". No 3D physics needed.
 */
public class Combat {
    public static class Missile { Racer owner, target; public float s, lat, speed, life; }
    public static class Mine { Racer owner; public float s, lat, life, arm; }
    public static class Pickup { public float s, lat, cooldown; }

    public final Array<Missile> missiles = new Array<>();
    public final Array<Mine> mines = new Array<>();
    public final Array<Pickup> pickups = new Array<>();
    private final Track track;

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
    }

    public void update(float dt, Array<Racer> racers) {
        // pickups
        for (Pickup p : pickups) {
            p.cooldown -= dt;
            if (p.cooldown > 0) continue;
            for (Racer r : racers) {
                if (r.weapon != null || r.respawn > 0) continue;
                if (Math.abs(track.delta(r.s, p.s)) < 2.5f && Math.abs(r.lat - p.lat) < 1.8f) {
                    r.weapon = Weapon.values()[MathUtils.random(Weapon.values().length - 1)];
                    p.cooldown = 5f;
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
                    r.hit(35f);
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
                    r.hit(25f);
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
                m.target = targetAhead(r, racers, 400f);
                m.s = track.wrap(r.s + 3f);
                m.lat = r.lat;
                m.speed = r.speed + 70f;
                m.life = 4f;
                missiles.add(m);
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
                break;
            }
            case SHIELD: r.shield = 4f; break;
            case TURBO: r.turbo = Math.max(r.turbo, 1.6f); break;
        }
        r.weapon = null;
    }

    /** AI decides when to use what it is holding. */
    public void aiUse(float dt, Racer r, Array<Racer> racers) {
        if (r.weapon == null) return;
        r.fireDelay -= dt;
        if (r.fireDelay > 0) return;
        boolean use;
        switch (r.weapon) {
            case MISSILE: use = targetAhead(r, racers, 200f) != null; break;
            case MINE: use = someoneBehind(r, racers, 80f); break;
            case SHIELD: use = r.energy < 70f || someoneBehind(r, racers, 40f); break;
            default: use = Math.abs(track.curvatureAt(r.s + 60f)) < 0.01f; break;
        }
        if (use) {
            fire(r, racers);
            r.fireDelay = MathUtils.random(0.5f, 2f);
        }
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
