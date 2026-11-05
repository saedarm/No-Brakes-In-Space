package com.saedarm.overclock;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
    private static final float FOG = 650f;          // lines fade out with distance

    private final ShapeRenderer sr = new ShapeRenderer(20000);
    private final FloatArray trackLines = new FloatArray();   // x1 y1 z1 x2 y2 z2 r g b a
    private final FloatArray stars = new FloatArray();
    private final Track track;

    private final Vector3 p = new Vector3(), f = new Vector3(), u = new Vector3(), r = new Vector3();
    private final Vector3 a = new Vector3(), b = new Vector3();
    private final Color c = new Color();
    private Camera cam;
    private float alphaScale;

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
        for (Racer rc : racers) ship(rc, time);

        sr.end();
    }

    private float fog(float x, float y, float z) {
        float d = cam.position.dst(x, y, z);
        return MathUtils.clamp(1f - d / FOG, 0f, 1f);
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
        if (rc.respawn > 0 && (int) (time * 10) % 2 == 0) return;   // blink while respawning
        track.frame(rc.s, p, f, u, r);
        p.mulAdd(r, rc.lat).mulAdd(u, Racer.HOVER + 0.08f * MathUtils.sin(time * 5f + rc.distance * 0.01f));

        // lean into steering, spin when hit, yaw when drifting
        float lean = MathUtils.clamp(rc.latVel * 1.8f, -30f, 30f);
        u.rotate(f, lean);
        r.set(f).crs(u).nor();
        float yaw = rc.spin > 0 ? rc.spin * 900f : (rc.drifting ? -Math.signum(rc.latVel) * 14f : 0f);
        if (yaw != 0) { f.rotate(u, yaw); r.set(f).crs(u).nor(); }

        for (int i = 0; i < SHIP.length; i++) {
            shipPts[i].set(p).mulAdd(r, SHIP[i][0]).mulAdd(u, SHIP[i][1]).mulAdd(f, SHIP[i][2]);
        }
        Color col = rc.color;
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
    }
}
