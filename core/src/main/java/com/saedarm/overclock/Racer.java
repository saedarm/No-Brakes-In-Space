package com.saedarm.overclock;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/** One glider. Lives entirely in track space: s (distance along), lat (left/right offset). */
public class Racer {
    // Tuning. Units: metres and seconds. 1 m/s = 3.6 km/h.
    public static final float MAX_SPEED = 125f;      // 450 km/h
    public static final float TURBO_MULT = 1.65f;    // ~740 km/h at full turbo
    public static final float ACCEL = 55f;
    public static final float BRAKE = 90f;
    public static final float COAST = 12f;
    public static final float STEER_SPEED = 22f;     // lateral m/s at full lock (tracks are 20 m wide)
    public static final float CENTRIFUGE = 0.08f;    // how hard corners push you outward
    public static final float HOVER = 0.6f;
    public static final float PIT_RECHARGE = 30f;    // energy per second on the recharge strip
    public static final float CRASH_TIME = 1.6f;     // seconds stopped after hitting an obstacle (alien is airborne)
    public static final float GHOST_TIME = 1.2f;     // can't crash again for this long after getting going
    public static final float OVERCOOK = 0.6f;       // hold a FULL drift longer than this and you spin out
    public static final float SPINOUT_TIME = 0.9f;

    public final String name;
    public final Color color;
    public final boolean isPlayer;

    public float s, lat, speed, latVel;
    public float distance;          // total distance travelled, for ranking
    public int lap;                 // 0 = on the grid, 1 = first lap ...
    public float energy = 100f;
    public float boost;             // drift charge 0..1, stage 1 at 0.5, stage 2 at 1
    public float turbo;             // seconds of turbo left
    public float spin;              // seconds of spin-out left (hit by something)
    public float respawn;           // seconds until back in the race after energy hit 0
    public float shield;            // seconds of shield left
    public boolean drifting;
    public boolean scraping;        // touching a wall this frame (sparks)
    public boolean inPit;           // on the recharge strip this frame
    public float hitFlash;          // seconds of red flash after a hit
    public int burst;               // debris to spawn: 1 = hit, 2 = knocked out (renderer clears it)
    public float crash;             // seconds left stopped after an obstacle crash
    public float ghost;             // seconds of crash immunity (blinks)
    public float ejectLeft, ejectTotal;   // alien is flying out of the cockpit (seconds left / total)
    public float overcook;          // seconds a full drift has been held
    public boolean spunOut;         // set on the frame a spin-out starts (HUD/sfx)
    public Alien alien = Alien.BLIP;
    public Weapon weapon;
    public boolean finished;
    public float finishTime;
    public int place;

    // AI personality
    float skill = 1f, line = 0f, fireDelay;

    public Racer(String name, Color color, boolean isPlayer) {
        this.name = name;
        this.color = color;
        this.isPlayer = isPlayer;
    }

    public Racer(Alien alien, boolean isPlayer) {
        this(isPlayer ? "YOU (" + alien.name + ")" : alien.name, alien.color, isPlayer);
        this.alien = alien;
    }

    /** Fling the alien out of the cockpit; it lands back in after `seconds`. */
    private void eject(float seconds) {
        ejectTotal = ejectLeft = seconds;
        Sfx.at(Sfx.EJECT, s, 0.9f);
    }

    /** Hit an obstacle: full stop, damage, alien goes flying. */
    public boolean crash(float damage) {
        if (shield > 0 || respawn > 0 || crash > 0 || ghost > 0) return false;
        energy -= damage;
        speed = 0; latVel = 0; drifting = false; boost = 0; overcook = 0; spin = 0;
        hitFlash = 0.45f;
        burst = 1;
        Sfx.at(Sfx.CRASH, s, 1f);
        if (energy <= 0) {
            energy = 0;
            respawn = 2.5f;
            burst = 2;
            eject(2.5f);
        } else {
            crash = CRASH_TIME;
            eject(CRASH_TIME);
        }
        return true;
    }

    private void spinOut() {
        spin = SPINOUT_TIME;
        drifting = false;
        boost = 0;
        overcook = 0;
        speed *= 0.8f;
        spunOut = true;
        Sfx.at(Sfx.SPIN, s, 0.9f);
    }

    /** steer -1..1 (right +), throttle/brake/drift as booleans. */
    public void update(float dt, Track track, float steer, boolean throttle, boolean brake, boolean drift) {
        shield = Math.max(0, shield - dt);
        hitFlash = Math.max(0, hitFlash - dt);
        ejectLeft = Math.max(0, ejectLeft - dt);
        scraping = false;
        spunOut = false;
        if (respawn > 0) {
            respawn -= dt;
            speed = 0; latVel = 0;
            if (respawn <= 0) { energy = 60f; lat = 0; spin = 0; ghost = GHOST_TIME; }
            return;
        }
        if (crash > 0) {
            crash -= dt;
            speed = 0; latVel = 0;
            if (crash <= 0) ghost = GHOST_TIME;
            return;
        }
        ghost = Math.max(0, ghost - dt);
        if (spin > 0) {
            spin -= dt;
            steer = 0; throttle = false; drift = false;
            speed *= 1f - 0.5f * dt;
        }

        // --- drift + two-stage turbo
        boolean wantDrift = drift && Math.abs(steer) > 0.2f && speed > 40f;
        if (wantDrift) {
            drifting = true;
            boost = Math.min(1f, boost + dt * 0.55f * Math.abs(steer));
            steer *= 1.5f;
            // hold a full drift too long and you lose it
            if (boost >= 1f) overcook += dt;
            if (overcook > OVERCOOK) { spinOut(); steer = 0; }
        } else if (drifting) {
            overcook = 0;
            drifting = false;
            if (boost >= 0.5f) {
                turbo = Math.max(turbo, boost >= 1f ? 2.2f : 1.1f);
                if (isPlayer) Sfx.play(Sfx.TURBO, boost >= 1f ? 1f : 0.6f);
            }
            boost = 0;
        }
        turbo = Math.max(0, turbo - dt);

        // --- speed
        float top = MAX_SPEED * skill * (turbo > 0 ? TURBO_MULT : 1f);
        if (turbo > 0) speed += ACCEL * 2.2f * dt;
        else if (throttle) speed += ACCEL * (1f - 0.6f * speed / top) * dt;
        else speed -= COAST * dt;
        if (brake) speed -= BRAKE * dt;
        if (drifting) speed -= 6f * dt;
        if (speed > top) speed = MathUtils.lerp(speed, top, 2.5f * dt);
        speed = Math.max(0, speed);

        // --- steering, plus corners pushing you to the outside
        float speedFactor = 0.55f + 0.45f * Math.min(1f, speed / MAX_SPEED);
        latVel = MathUtils.lerp(latVel, steer * STEER_SPEED * speedFactor, Math.min(1f, 9f * dt));
        latVel -= track.curvatureAt(s) * speed * speed * CENTRIFUGE * dt;
        lat += latVel * dt;

        // --- walls
        float half = track.width / 2f - 0.9f;
        if (Math.abs(lat) > half) {
            lat = Math.signum(lat) * half;
            latVel *= -0.35f;
            speed *= 1f - 1.4f * dt;
            // scraping hurts but never kills, and never on the recharge strip
            if (shield <= 0 && !track.inPit(s)) energy = Math.max(1f, energy - 8f * dt);
            scraping = true;
            if (drifting) spinOut();          // drifting into a wall = spin
        }

        // --- recharge strip: drive over the left third of the track inside the pit zone
        inPit = track.inPit(s) && lat < -track.width / 6f;
        if (inPit) energy = Math.min(100f, energy + PIT_RECHARGE * dt);

        // --- move along
        float ds = speed * dt;
        distance += ds;
        float before = s;
        s = track.wrap(s + ds);
        if (s < before) lap++;
    }

    /** Hit by a missile or mine. Returns true if it landed. */
    public boolean hit(float damage) {
        if (shield > 0 || respawn > 0 || crash > 0) return false;
        energy -= damage;
        spin = 0.9f;
        hitFlash = 0.45f;
        burst = 1;
        if (energy <= 0) {
            energy = 0;
            respawn = 2.5f;
            burst = 2;
            eject(2.5f);
        }
        Sfx.at(burst == 2 ? Sfx.KO : Sfx.HIT, s, 1f);
        return true;
    }

    /**
     * AI driver: follows an inside line, brakes for tight bends, heads for the recharge
     * strip when hurt, steers around mines it sees, and jukes side to side when a
     * missile is locked on. awareness (0..1) scales how early it notices threats.
     */
    public void updateAi(float dt, Track track, Racer player, Combat combat) {
        float half = track.width / 2f - 1.2f;
        float c = track.curvatureAt(s + speed * 0.5f + 12f);
        float target = MathUtils.clamp(c * 160f, -1f, 1f) * half * 0.7f + line;
        if (energy < 55f && track.inPit(s + 40f)) target = -half * 0.75f;   // go recharge

        if (combat != null) {
            // mines: find the clear lane closest to where we wanted to go
            float look = (speed * 0.9f + 15f) * awareness;
            if (combat.hazardAhead(this, look)) target = combat.clearLane(this, look, target, half);

            // missile on our tail and no shield: hold the line, then one hard sidestep at the last moment
            // (the missile follows our lane with a lag, so a late sharp move makes it miss)
            Combat.Missile in = combat.incoming(this, 120f);
            if (in != null && weapon != Weapon.SHIELD) {
                float d = track.delta(s, in.s);
                if (dodgeDir == 0) dodgeDir = lat > 0 ? -1f : 1f;           // toward the roomier side
                if (d < 12f + 30f * awareness) target = MathUtils.clamp(lat + dodgeDir * 6f, -half, half);
            } else dodgeDir = 0;
        }
        target = MathUtils.clamp(target, -half, half);
        float steer = MathUtils.clamp((target - lat) * 0.35f, -1f, 1f);
        boolean brake = Math.abs(c) > 0.045f && speed > MAX_SPEED * 0.7f;

        // rubber band [V]
        float gap = player.distance - distance;
        skill = MathUtils.clamp(baseSkill + gap * 0.00015f, 0.8f, 1.08f);

        update(dt, track, steer, true, brake, false);
    }

    float baseSkill = 1f;
    float awareness = 1f;
    private float dodgeDir;
}
