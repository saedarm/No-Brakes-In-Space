package com.saedarm.overclock;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;

/**
 * Draws everything as glowing lines. The "glow" is cheap: each line is drawn
 * twice with additive blending, once thick and faint, once thin and bright.
 * A real bloom shader is an open item in GAME_DESIGN.md.
 */
public class NeonRenderer implements Disposable {
    public float fog = 650f;                       // lines fade out with distance
    public Racer lockTarget;                       // who the player's missile would hit (bracketed in red)

    private final ShapeRenderer sr = new ShapeRenderer(20000);
    private final ImmediateModeRenderer20 imr = new ImmediateModeRenderer20(30000, false, true, 0);   // filled pixel aliens
    private final Vector3 camRight = new Vector3(), seat = new Vector3();
    private final FloatArray trackLines = new FloatArray();   // x1 y1 z1 x2 y2 z2 r g b a
    private final FloatArray stars = new FloatArray();
    private final Track track;

    private final Vector3 p = new Vector3(), f = new Vector3(), u = new Vector3(), r = new Vector3();
    private final Vector3 a = new Vector3(), b = new Vector3();
    private final Color c = new Color();
    private Camera cam;
    private float alphaScale;

    /** A short glowing line segment flying through space (debris and sparks). */
    private static class Bit {
        final Vector3 p = new Vector3(), v = new Vector3(), d = new Vector3(), axis = new Vector3();
        final Color c = new Color();
        float life, max, spin;
    }
    /** Expanding shockwave ring lying on the track surface. */
    private static class Ring {
        float s, lat, life, max, radius;
        final Color c = new Color();
    }
    private final Array<Bit> bits = new Array<>();
    private final Array<Ring> rings = new Array<>();
    private static final Color PIT = new Color(0.3f, 1f, 0.55f, 1f);
    private static final Color HIT = new Color(1f, 0.15f, 0.2f, 1f);

    public NeonRenderer(Track track) {
        this.track = track;
        buildTrack();
        float cx = (track.minX + track.maxX) / 2, cz = (track.minZ + track.maxZ) / 2;
        for (int i = 0; i < 1500; i++) {
            Vector3 v = new Vector3(MathUtils.random(-1f, 1f), MathUtils.random(-0.4f, 1f), MathUtils.random(-1f, 1f))
                    .nor().scl(MathUtils.random(400f, 900f));
            stars.addAll(cx + v.x, v.y, cz + v.z);
        }
    }

    private void buildTrack() {
        int n = track.count;
        float hw = track.width / 2f;
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            c.fromHsv(200f + 140f * MathUtils.sin(k / (float) n * MathUtils.PI2), 0.85f, 1f);
            // edges
            edge(k, k2, -hw, c, 1f);
            edge(k, k2, hw, c, 1f);
            // floor rungs
            if (k % 6 == 0) {
                a.set(track.pos[k]).mulAdd(track.right[k], -hw);
                b.set(track.pos[k]).mulAdd(track.right[k], hw);
                add(a, b, c, 0.22f);
            }
            // tunnel rings
            if (track.tunnel[k] != 0 && k % 7 == 0) ring(k, track.tunnel[k], c);
            // recharge strip: inner border + diagonal hatching on the left third
            if (track.inPit(k * track.step)) {
                float inner = -hw / 3f;
                if (track.inPit(k2 * track.step)) edge(k, k2, inner, PIT, 0.9f);
                if (k % 4 == 0) {
                    int k3 = (k + 3) % n;
                    a.set(track.pos[k]).mulAdd(track.right[k], -hw).mulAdd(track.up[k], 0.05f);
                    b.set(track.pos[k3]).mulAdd(track.right[k3], inner).mulAdd(track.up[k3], 0.05f);
                    add(a, b, PIT, 0.55f);
                }
            }
        }
    }

    private void edge(int k, int k2, float off, Color col, float alpha) {
        a.set(track.pos[k]).mulAdd(track.right[k], off);
        b.set(track.pos[k2]).mulAdd(track.right[k2], off);
        add(a, b, col, alpha);
    }

    private void ring(int k, int type, Color col) {
        Vector3 o = track.pos[k], R = track.right[k], U = track.up[k];
        float hw = track.width / 2f * 1.15f;
        if (type == 2) {
            float h = track.width * 0.8f;
            float[][] q = {{-hw, 0}, {-hw, h}, {hw, h}, {hw, 0}};
            for (int i = 0; i < 3; i++) {
                a.set(o).mulAdd(R, q[i][0]).mulAdd(U, q[i][1]);
                b.set(o).mulAdd(R, q[i + 1][0]).mulAdd(U, q[i + 1][1]);
                add(a, b, col, 0.8f);
            }
        } else {
            float rad = hw * 1.08f;
            for (int i = 0; i < 8; i++) {
                float a1 = MathUtils.PI2 * (i + 0.5f) / 8f, a2 = MathUtils.PI2 * (i + 1.5f) / 8f;
                a.set(o).mulAdd(R, MathUtils.cos(a1) * rad).mulAdd(U, MathUtils.sin(a1) * rad + rad * 0.38f);
                b.set(o).mulAdd(R, MathUtils.cos(a2) * rad).mulAdd(U, MathUtils.sin(a2) * rad + rad * 0.38f);
                add(a, b, col, 0.8f);
            }
        }
    }

    private void add(Vector3 v1, Vector3 v2, Color col, float alpha) {
        trackLines.addAll(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, col.r, col.g, col.b, alpha);
    }

    // ---------------------------------------------------------------- effects

    /** Spawns debris for hits/knockouts and sparks for wall scrapes, then moves everything. */
    public void update(float dt, Array<Racer> racers) {
        for (Racer rc : racers) {
            if (rc.burst > 0) {
                shipPos(rc);
                boolean ko = rc.burst == 2;
                int n = ko ? 60 : 22;
                for (int i = 0; i < n; i++) {
                    Bit bt = new Bit();
                    bt.p.set(p);
                    bt.v.setToRandomDirection().scl(MathUtils.random(8f, ko ? 45f : 25f)).mulAdd(f, rc.speed * 0.55f);
                    bt.d.setToRandomDirection().scl(MathUtils.random(0.3f, ko ? 1.6f : 0.9f));
                    bt.axis.setToRandomDirection();
                    bt.spin = MathUtils.random(-720f, 720f);
                    bt.c.set(i % 3 == 0 ? Color.WHITE : (i % 3 == 1 ? HIT : rc.color));
                    bt.life = bt.max = MathUtils.random(0.5f, ko ? 1.6f : 0.9f);
                    bits.add(bt);
                }
                Ring rg = new Ring();
                rg.s = rc.s; rg.lat = rc.lat;
                rg.life = rg.max = ko ? 0.7f : 0.4f;
                rg.radius = ko ? 16f : 7f;
                rg.c.set(ko ? Color.WHITE : HIT);
                rings.add(rg);
                rc.burst = 0;
            }
            if (rc.scraping && rc.speed > 30f) {
                shipPos(rc);
                for (int i = 0; i < 2; i++) {
                    Bit bt = new Bit();
                    bt.p.set(p).mulAdd(r, Math.signum(rc.lat) * 1.5f);
                    bt.v.set(f).scl(rc.speed * 0.7f).add(MathUtils.random(-6f, 6f), MathUtils.random(2f, 8f), MathUtils.random(-6f, 6f));
                    bt.d.set(f).scl(-MathUtils.random(0.4f, 1.2f));
                    bt.axis.set(u);
                    bt.c.set(1f, 0.85f, 0.4f, 1f);
                    bt.life = bt.max = MathUtils.random(0.15f, 0.35f);
                    bits.add(bt);
                }
            }
        }
        for (int i = bits.size - 1; i >= 0; i--) {
            Bit bt = bits.get(i);
            bt.life -= dt;
            if (bt.life <= 0) { bits.removeIndex(i); continue; }
            bt.p.mulAdd(bt.v, dt);
            bt.v.scl(1f - 1.5f * dt);
            if (bt.spin != 0) bt.d.rotate(bt.axis, bt.spin * dt);
        }
        for (int i = rings.size - 1; i >= 0; i--) {
            Ring rg = rings.get(i);
            rg.life -= dt;
            if (rg.life <= 0) rings.removeIndex(i);
        }
    }

    /** Sets p/f/u/r to a racer's world position and track frame. */
    private void shipPos(Racer rc) {
        track.frame(rc.s, p, f, u, r);
        p.mulAdd(r, rc.lat).mulAdd(u, Racer.HOVER);
    }

    // ---------------------------------------------------------------- frame

    public void render(Camera cam, Array<Racer> racers, Combat combat, float time) {
        this.cam = cam;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);   // additive = neon
        sr.setProjectionMatrix(cam.combined);

        sr.begin(ShapeType.Point);
        for (int i = 0; i < stars.size; i += 3) {
            sr.setColor(0.6f, 0.65f, 0.8f, 0.6f);
            sr.point(stars.get(i), stars.get(i + 1), stars.get(i + 2));
        }
        sr.end();

        pass(5f, 0.16f, racers, combat, time);   // wide, faint halo
        pass(1.6f, 1f, racers, combat, time);    // sharp core

        Gdx.gl.glLineWidth(1f);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        drawAliens(racers, time);
    }

    /**
     * Pilots as solid pixel billboards sitting on their gliders. After a crash or knockout
     * the alien is launched: up and sideways in an arc, tumbling, and lands back in the seat.
     */
    private void drawAliens(Array<Racer> racers, float time) {
        if (racers.size == 0) return;
        camRight.set(cam.direction).crs(cam.up).nor();
        imr.begin(cam.combined, GL20.GL_TRIANGLES);
        for (Racer rc : racers) {
            pose(rc, time);
            seat.set(p).mulAdd(u, 0.35f).mulAdd(f, -0.2f);
            float rot = 0, scale = 1f;
            if (rc.ejectLeft > 0 && rc.ejectTotal > 0) {
                float t = 1f - rc.ejectLeft / rc.ejectTotal;              // 0 -> 1 over the flight
                float side = (rc.distance % 2f) > 1f ? 1f : -1f;
                float height = rc.ejectTotal > 2f ? 7f : 4.5f;   // low enough to stay in the chase camera's view
                seat.mulAdd(u, 4f * height * t * (1f - t))
                    .mulAdd(r, MathUtils.sin(t * MathUtils.PI) * 4f * side)
                    .mulAdd(f, MathUtils.sin(t * MathUtils.PI) * 2f);
                rot = t * 720f * side;
                scale = 1.3f;
            } else {
                rot = MathUtils.clamp(rc.latVel * 1.5f, -20f, 20f) + MathUtils.sin(time * 6f + rc.distance) * 4f;  // bobbing
            }
            float alpha = fog(seat.x, seat.y, seat.z);
            if (alpha <= 0) continue;
            if (rc.ghost > 0 && (int) (time * 12) % 2 == 0) alpha *= 0.3f;
            rc.alien.draw3D(imr, seat, camRight, cam.up, rot, scale, alpha, rc.hitFlash > 0 && (int) (time * 20) % 2 == 0);
            if (imr.getNumVertices() > 26000) { imr.end(); imr.begin(cam.combined, GL20.GL_TRIANGLES); }
        }
        imr.end();
    }

    /** Sets p/f/u/r to the ship's world pose: hover, lean into steering, spin, drift yaw. */
    private void pose(Racer rc, float time) {
        track.frame(rc.s, p, f, u, r);
        p.mulAdd(r, rc.lat).mulAdd(u, Racer.HOVER + 0.08f * MathUtils.sin(time * 5f + rc.distance * 0.01f));
        float lean = MathUtils.clamp(rc.latVel * 1.8f, -30f, 30f);
        u.rotate(f, lean);
        r.set(f).crs(u).nor();
        float yaw = rc.spin > 0 ? rc.spin * 900f : (rc.drifting ? -Math.signum(rc.latVel) * 14f : 0f);
        if (yaw != 0) { f.rotate(u, yaw); r.set(f).crs(u).nor(); }
    }

    private void pass(float width, float alpha, Array<Racer> racers, Combat combat, float time) {
        Gdx.gl.glLineWidth(width);
        alphaScale = alpha;
        sr.begin(ShapeType.Line);

        float[] t = trackLines.items;
        for (int i = 0; i < trackLines.size; i += 10) {
            float fade = fog(t[i], t[i + 1], t[i + 2]);
            if (fade <= 0) continue;
            sr.setColor(t[i + 6], t[i + 7], t[i + 8], t[i + 9] * fade * alpha);
            sr.line(t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5]);
        }

        if (combat != null) drawCombat(combat, time);
        for (Racer rc : racers) ship(rc, time);
        if (lockTarget != null && lockTarget.respawn <= 0) lockBrackets(lockTarget, time);
        drawEffects();
        sr.end();
    }

    private static final Color BLOCK_C = new Color(1f, 0.3f, 0.2f, 1f), SLIDER_C = new Color(1f, 0.8f, 0.15f, 1f),
            PYLON_C = new Color(1f, 0.55f, 0.2f, 1f);
    private final Vector3[] box = new Vector3[8];
    { for (int i = 0; i < 8; i++) box[i] = new Vector3(); }

    /** Wireframe obstacle: a box sitting on the track surface, plus a warning mark. */
    private void obstacle(Combat.Obstacle o, float time) {
        Combat.ObstacleType t = o.type;
        float hw = t.width / 2f, hd = t.depth / 2f, ht = t.height;
        Color col = t == Combat.ObstacleType.BLOCK ? BLOCK_C : t == Combat.ObstacleType.SLIDER ? SLIDER_C : PYLON_C;
        for (int i = 0; i < 8; i++) {
            float ds = (i & 1) == 0 ? -hd : hd, dl = (i & 2) == 0 ? -hw : hw, dh = (i & 4) == 0 ? 0.05f : ht;
            track.point(o.s + ds, o.lat + dl, dh, box[i]);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) line(box[e[0]], box[e[1]], col);
        if (t == Combat.ObstacleType.BLOCK) {              // big X on the face you drive at
            line(box[0], box[6], col); line(box[2], box[4], col);
        } else if (t == Combat.ObstacleType.SLIDER) {      // hazard stripes
            for (int k = 1; k < 4; k++) {
                float q = k / 4f;
                a.set(box[0]).lerp(box[2], q);
                b.set(box[4]).lerp(box[6], Math.min(1f, q + 0.25f));
                line(a, b, col);
            }
        } else {                                           // pylon: blinking top light
            if ((int) (time * 4) % 2 == 0) { a.set(box[4]).lerp(box[7], 0.5f); b.set(a).mulAdd(track.up[track.index(o.s)], 0.8f); line(a, b, Color.WHITE); }
        }
    }

    private void drawCombat(Combat combat, float time) {
        for (Combat.Obstacle o : combat.obstacles) {
            Vector3 at = track.pos[track.index(o.s)];
            if (fog(at.x, at.y, at.z) > 0) obstacle(o, time);
        }
        for (Combat.Pickup pk : combat.pickups) {
            if (pk.cooldown > 0) continue;
            box(pk.s, pk.lat, 1.2f, 0.7f, time * 3f, c.set(1f, 0.3f, 1f, 1f));
        }
        for (Combat.Mine m : combat.mines) {
            float pulse = 0.6f + 0.4f * MathUtils.sin(time * 12f);
            box(m.s, m.lat, 0.5f, 0.5f, time * 6f, c.set(1f, 0.1f, 0.3f, pulse));
        }
        for (Combat.Missile m : combat.missiles) {
            box(m.s, m.lat, 0.9f, 0.35f, time * 20f, c.set(Weapon.MISSILE.color));
            trail(m.s, m.lat, 0.9f, 10f, Weapon.MISSILE.color);
        }
    }

    /** Four red corner brackets that tighten around the locked target. */
    private void lockBrackets(Racer t, float time) {
        shipPos(t);
        float size = 3.2f + 0.6f * MathUtils.sin(time * 10f);
        Color col = c.set(1f, 0.2f, 0.25f, 1f);
        for (int i = 0; i < 4; i++) {
            float sx = (i % 2 == 0) ? 1 : -1, sy = (i < 2) ? 1 : -1;
            Vector3 corner = new Vector3(p).mulAdd(r, sx * size).mulAdd(u, 0.8f + sy * size * 0.6f);
            a.set(corner).mulAdd(r, -sx * 1.2f);
            line(corner, a, col);
            a.set(corner).mulAdd(u, -sy * 1.0f);
            line(corner, a, col);
        }
    }

    private void drawEffects() {
        for (Bit bt : bits) {
            float k = bt.life / bt.max;
            a.set(bt.p);
            b.set(bt.p).add(bt.d);
            line(a, b, c.set(bt.c.r, bt.c.g, bt.c.b, k));
        }
        for (Ring rg : rings) {
            float k = 1f - rg.life / rg.max;                 // 0 -> 1 as it expands
            float rad = rg.radius * (0.2f + 0.8f * k);
            track.frame(rg.s, p, f, u, r);
            p.mulAdd(r, rg.lat).mulAdd(u, 0.3f);
            Vector3 prev = new Vector3(), cur = new Vector3();
            for (int i = 0; i <= 12; i++) {
                float ang = i / 12f * MathUtils.PI2;
                cur.set(p).mulAdd(r, MathUtils.cos(ang) * rad).mulAdd(f, MathUtils.sin(ang) * rad);
                if (i > 0) line(prev, cur, c.set(rg.c.r, rg.c.g, rg.c.b, 1f - k));
                prev.set(cur);
            }
        }
    }

    private float fog(float x, float y, float z) {
        float d = cam.position.dst(x, y, z);
        return MathUtils.clamp(1f - d / fog, 0f, 1f);
    }

    private void line(Vector3 v1, Vector3 v2, Color col) {
        float fade = fog(v1.x, v1.y, v1.z);
        if (fade <= 0) return;
        sr.setColor(col.r, col.g, col.b, col.a * fade * alphaScale);
        sr.line(v1, v2);
    }

    /** Spinning diamond at track position. */
    private void box(float s, float lat, float size, float h, float angle, Color col) {
        track.frame(s, p, f, u, r);
        p.mulAdd(r, lat).mulAdd(u, h + size);
        Vector3 prev = new Vector3(), cur = new Vector3(), top = new Vector3(p).mulAdd(u, size), bot = new Vector3(p).mulAdd(u, -size);
        for (int i = 0; i <= 4; i++) {
            float ang = angle + i * MathUtils.HALF_PI;
            cur.set(p).mulAdd(r, MathUtils.cos(ang) * size).mulAdd(f, MathUtils.sin(ang) * size);
            if (i > 0) { line(prev, cur, col); }
            line(cur, top, col);
            line(cur, bot, col);
            prev.set(cur);
        }
    }

    private void trail(float s, float lat, float h, float len, Color col) {
        a.set(track.point(s, lat, h, a));
        b.set(track.point(s - len, lat, h, b));
        line(a, b, c.set(col.r, col.g, col.b, 0.5f));
    }

    // Ship model in local space: x = right, y = up, z = forward.
    private static final float[][] SHIP = {
            {0, 0.15f, 2.4f}, {-1.7f, 0, -1.1f}, {1.7f, 0, -1.1f}, {0, 0.55f, -0.8f}, {0, 0, -0.6f}
    };
    private static final int[][] SHIP_EDGES = {{0, 1}, {0, 2}, {1, 4}, {2, 4}, {0, 3}, {1, 3}, {2, 3}, {3, 4}};
    private final Vector3[] shipPts = {new Vector3(), new Vector3(), new Vector3(), new Vector3(), new Vector3()};

    private void ship(Racer rc, float time) {
        if ((rc.respawn > 0 || rc.ghost > 0) && (int) (time * 10) % 2 == 0) return;   // blink while respawning
        pose(rc, time);

        for (int i = 0; i < SHIP.length; i++) {
            shipPts[i].set(p).mulAdd(r, SHIP[i][0]).mulAdd(u, SHIP[i][1]).mulAdd(f, SHIP[i][2]);
        }
        Color col = rc.color;
        if (rc.hitFlash > 0 && (int) (time * 20) % 2 == 0) col = HIT;
        else if (rc.inPit && rc.energy < 100f) col = c.set(rc.color).lerp(PIT, 0.5f + 0.5f * MathUtils.sin(time * 14f));
        for (int[] e : SHIP_EDGES) line(shipPts[e[0]], shipPts[e[1]], col);

        // engine flame, longer with turbo
        float flame = rc.turbo > 0 ? 4f : 1f + rc.speed / Racer.MAX_SPEED;
        a.set(shipPts[4]);
        b.set(shipPts[4]).mulAdd(f, -flame);
        line(a, b, rc.turbo > 0 ? Weapon.TURBO.color : c.set(col.r, col.g, col.b, 0.6f));

        if (rc.shield > 0) {
            Vector3 prev = new Vector3(), cur = new Vector3();
            for (int i = 0; i <= 10; i++) {
                float ang = i / 10f * MathUtils.PI2 + time * 4f;
                cur.set(p).mulAdd(r, MathUtils.cos(ang) * 2.6f).mulAdd(f, MathUtils.sin(ang) * 2.6f);
                if (i > 0) line(prev, cur, Weapon.SHIELD.color);
                prev.set(cur);
            }
        }
    }

    @Override
    public void dispose() {
        sr.dispose();
        imr.dispose();
    }
}
