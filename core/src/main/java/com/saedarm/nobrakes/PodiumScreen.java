package com.saedarm.nobrakes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * The award ceremony. Top three aliens on a wireframe podium, the winner bouncing under a
 * spinning trophy, confetti, fireworks, a fanfare, a dumb caption, and last place face-down
 * on the floor next to the podium.
 */
public class PodiumScreen extends ScreenAdapter {
    private static final Color GOLD = new Color(1f, 0.85f, 0.25f, 1f), DIM = new Color(0.5f, 0.55f, 0.65f, 1f);
    private static final float[] PODIUM_X = {0f, -4.6f, 4.6f}, PODIUM_H = {3f, 2f, 1.3f};
    private static final String[] WIN_LINES = {
            "%s has been informed it is the best.",
            "%s will not stop talking about this.",
            "%s thanks its mom, who is a cloud.",
            "%s is now legally a race car.",
            "Congratulations %s. Please return the trophy by Tuesday.",
    };
    private static final String[] YOU_WIN_LINES = {
            "YOU WON. The aliens are furious.",
            "YOU WON. %s's family has been notified.",
            "YOU WON. Nobody saw that coming, least of all %s.",
    };
    private static final String[] LAST_LINES = {
            "%s is fine. %s is lying down on purpose.",
            "%s would like everyone to stop looking.",
            "%s has asked for a recount.",
    };

    private final NoBrakesGame game;
    private final int trackIndex;
    private final Array<Racer> order = new Array<>();
    private final String caption, lastCaption, trackName;

    private final PerspectiveCamera cam = new PerspectiveCamera(55, 1280, 720);
    private final ShapeRenderer sr = new ShapeRenderer(8000);
    private final ImmediateModeRenderer20 imr = new ImmediateModeRenderer20(30000, false, true, 0);
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 ortho = new Matrix4();
    private float w = 1280, h = 720, time, nextFirework = 0.4f, confettiTimer;
    private static final float ALIEN_SCALE = 1.5f;

    private static class Bit { final Vector3 p = new Vector3(), v = new Vector3(); final Color c = new Color(); float life, max, spin; }
    private final Array<Bit> confetti = new Array<>(), sparks = new Array<>();
    private final Vector3 a = new Vector3(), b = new Vector3(), right = new Vector3(), tmp = new Vector3();

    public PodiumScreen(NoBrakesGame game, Array<Racer> racers, int trackIndex) {
        this.game = game;
        this.trackIndex = trackIndex;
        order.addAll(racers);
        order.sort((x, y) -> x.place - y.place);
        Racer win = order.first(), last = order.peek();
        caption = win.isPlayer ? String.format(pick(YOU_WIN_LINES), order.get(1).alien.name)
                : String.format(pick(WIN_LINES), win.alien.name);
        lastCaption = String.format(pick(LAST_LINES), last.alien.name, last.alien.name);
        trackName = Track.load(Gdx.files.internal(NoBrakesGame.TRACKS[trackIndex])).name.toUpperCase();
        font.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
        cam.near = 0.5f;
        cam.far = 200f;
    }

    private static String pick(String[] lines) { return lines[MathUtils.random(lines.length - 1)]; }

    @Override
    public void show() {
        Sfx.play(Sfx.FANFARE, 0.9f);
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 1 / 30f);
        time += dt;
        if (Gdx.input.isKeyJustPressed(Keys.ENTER) || Gdx.input.isKeyJustPressed(Keys.ESCAPE)) { game.showMenu(trackIndex); return; }
        if (Gdx.input.isKeyJustPressed(Keys.R)) { game.startRace(trackIndex); return; }
        if (Gdx.input.isKeyJustPressed(Keys.M)) Sfx.toggleMusic();
        update(dt);

        cam.position.set(MathUtils.sin(time * 0.3f) * 3f, 4.8f, 15f);
        cam.up.set(0, 1, 0);
        cam.lookAt(0, 3.4f, 0);
        cam.update();
        right.set(cam.direction).crs(cam.up).nor();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        lines(4f, 0.18f);
        lines(1.6f, 1f);
        Gdx.gl.glLineWidth(1f);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sprites();
        overlay();
    }

    // ---------------------------------------------------------------- simulation

    private void update(float dt) {
        // confetti rains forever
        confettiTimer -= dt;
        while (confettiTimer <= 0) {
            confettiTimer += 1f / 45f;                     // ~45 pieces a second
            Bit c = new Bit();
            c.p.set(MathUtils.random(-12f, 12f), 13f, MathUtils.random(-4f, 4f));
            c.v.set(MathUtils.random(-0.5f, 0.5f), MathUtils.random(-3f, -1.5f), 0);
            Alien col = Alien.values()[MathUtils.random(Alien.values().length - 1)];
            c.c.set(MathUtils.randomBoolean(0.25f) ? Color.WHITE : col.color);
            c.life = c.max = 6f;
            c.spin = MathUtils.random(360f);
            confetti.add(c);
        }
        for (int i = confetti.size - 1; i >= 0; i--) {
            Bit c = confetti.get(i);
            c.life -= dt;
            c.p.mulAdd(c.v, dt);
            c.p.x += MathUtils.sin(time * 3f + c.spin) * dt * 0.8f;
            c.spin += 300f * dt;
            if (c.life <= 0 || c.p.y < 0) confetti.removeIndex(i);
        }
        // fireworks
        nextFirework -= dt;
        if (nextFirework <= 0) {
            nextFirework = MathUtils.random(0.5f, 1.1f);
            tmp.set(MathUtils.random(-13f, 13f), MathUtils.random(8f, 13f), MathUtils.random(-10f, -5f));
            Color col = Alien.values()[MathUtils.random(Alien.values().length - 1)].color;
            for (int i = 0; i < 46; i++) {
                Bit s = new Bit();
                s.p.set(tmp);
                s.v.setToRandomDirection().scl(MathUtils.random(5f, 9f));
                s.c.set(MathUtils.randomBoolean(0.2f) ? Color.WHITE : col);
                s.life = s.max = MathUtils.random(1f, 1.6f);
                sparks.add(s);
            }
            Sfx.play(Sfx.POP, 0.5f);
        }
        for (int i = sparks.size - 1; i >= 0; i--) {
            Bit s = sparks.get(i);
            s.life -= dt;
            s.v.y -= 6f * dt;
            s.v.scl(1f - 0.8f * dt);
            s.p.mulAdd(s.v, dt);
            if (s.life <= 0) sparks.removeIndex(i);
        }
    }

    // ---------------------------------------------------------------- drawing

    private void lines(float width, float alpha) {
        Gdx.gl.glLineWidth(width);
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeType.Line);
        // floor grid
        for (int i = -14; i <= 14; i += 2) {
            sr.setColor(0.25f, 0.3f, 0.55f, 0.35f * alpha);
            sr.line(i, 0, -12, i, 0, 8);
            sr.line(-14, 0, i * 0.7f - 2, 14, 0, i * 0.7f - 2);
        }
        // podium blocks
        for (int k = 0; k < 3 && k < order.size; k++) {
            Color c = k == 0 ? GOLD : order.get(k).alien.color;
            box(PODIUM_X[k], PODIUM_H[k], 3.8f, 3f, c, alpha);
        }
        // sweeping spotlights
        for (int k = -1; k <= 1; k += 2) {
            float sweep = MathUtils.sin(time * 0.9f + k) * 6f;
            sr.setColor(1f, 1f, 0.8f, 0.25f * alpha);
            sr.line(k * 14f, 0, -6, sweep, 6f, 0);
            sr.line(k * 14f, 0, -6, sweep + 1.5f, 6f, 0);
        }
        // trophy over the winner
        trophy(0f, PODIUM_H[0] + order.first().alien.height() * ALIEN_SCALE + 1.0f, alpha);
        // fireworks
        for (Bit s : sparks) {
            float k = s.life / s.max;
            sr.setColor(s.c.r, s.c.g, s.c.b, k * alpha);
            b.set(s.p).mulAdd(s.v, -0.06f);
            sr.line(s.p, b);
        }
        sr.end();
    }

    private void box(float x, float hgt, float bw, float bd, Color c, float alpha) {
        float x0 = x - bw / 2, x1 = x + bw / 2, z0 = -bd / 2, z1 = bd / 2;
        sr.setColor(c.r, c.g, c.b, alpha);
        float[][] e = {{x0, 0, z1, x1, 0, z1}, {x0, hgt, z1, x1, hgt, z1}, {x0, 0, z1, x0, hgt, z1}, {x1, 0, z1, x1, hgt, z1},
                {x0, hgt, z0, x1, hgt, z0}, {x0, hgt, z0, x0, hgt, z1}, {x1, hgt, z0, x1, hgt, z1},
                {x0, 0, z0, x0, hgt, z0}, {x1, 0, z0, x1, hgt, z0}};
        for (float[] l : e) sr.line(l[0], l[1], l[2], l[3], l[4], l[5]);
    }

    /** Wireframe cup: rings stacked along a profile, spinning, with two handles. */
    private void trophy(float x, float y, float alpha) {
        float[][] profile = {{0f, 0.55f}, {0.15f, 0.5f}, {0.6f, 0.12f}, {0.75f, 0.12f}, {1.0f, 0.35f},
                {1.4f, 0.65f}, {1.9f, 0.8f}, {2.2f, 0.85f}};
        float spin = time * 90f;
        int seg = 10;
        sr.setColor(GOLD.r, GOLD.g, GOLD.b, alpha);
        for (int i = 0; i < profile.length; i++) {
            float py = y + profile[i][0], rad = profile[i][1];
            for (int k = 0; k < seg; k++) {
                float a1 = spin + k * 360f / seg, a2 = spin + (k + 1) * 360f / seg;
                sr.line(x + MathUtils.cosDeg(a1) * rad, py, MathUtils.sinDeg(a1) * rad,
                        x + MathUtils.cosDeg(a2) * rad, py, MathUtils.sinDeg(a2) * rad);
                if (i > 0 && k % 2 == 0) {
                    float pr = profile[i - 1][1], ppy = y + profile[i - 1][0];
                    sr.line(x + MathUtils.cosDeg(a1) * pr, ppy, MathUtils.sinDeg(a1) * pr,
                            x + MathUtils.cosDeg(a1) * rad, py, MathUtils.sinDeg(a1) * rad);
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            float hx = MathUtils.cosDeg(spin) * side, hz = MathUtils.sinDeg(spin) * side;
            for (int k = 0; k < 6; k++) {
                float t1 = k / 6f * MathUtils.PI, t2 = (k + 1) / 6f * MathUtils.PI;
                float r1 = 0.8f + MathUtils.sin(t1) * 0.4f, r2 = 0.8f + MathUtils.sin(t2) * 0.4f;
                sr.line(x + hx * r1, y + 1.3f + MathUtils.cos(t1) * 0.4f, hz * r1,
                        x + hx * r2, y + 1.3f + MathUtils.cos(t2) * 0.4f, hz * r2);
            }
        }
    }

    private void sprites() {
        imr.begin(cam.combined, GL20.GL_TRIANGLES);
        // podium aliens
        for (int k = 0; k < 3 && k < order.size; k++) {
            Racer r = order.get(k);
            float bounce, rot;
            if (k == 0) {                                   // winner: big bounces, a flip every few seconds
                bounce = Math.abs(MathUtils.sin(time * 4.5f)) * 1.4f;
                float flip = (time % 3f) > 2.4f ? ((time % 3f) - 2.4f) / 0.6f * 360f : 0f;
                rot = flip;
            } else if (k == 1) {
                bounce = Math.abs(MathUtils.sin(time * 3f)) * 0.4f;
                rot = MathUtils.sin(time * 3f) * 8f;
            } else {
                bounce = 0f;
                rot = MathUtils.sin(time * 1.5f) * 12f;    // polite sway
            }
            a.set(PODIUM_X[k], PODIUM_H[k] + bounce, 0.3f);
            r.alien.draw3D(imr, a, right, cam.up, rot, ALIEN_SCALE, 1f, false);
        }
        // last place: face-down on the floor next to the podium
        if (order.size > 3) {
            Racer last = order.get(3);
            a.set(9f, 0.25f, 1.5f);
            last.alien.draw3D(imr, a, right, cam.up, 90f + MathUtils.sin(time * 0.7f) * 3f, ALIEN_SCALE, 1f, false);
        }
        // confetti
        for (Bit c : confetti) {
            float cs = MathUtils.cosDeg(c.spin) * 0.09f, sn = MathUtils.sinDeg(c.spin) * 0.09f;
            float[][] q = {{-1, -1}, {1, -1}, {1, 1}, {-1, -1}, {1, 1}, {-1, 1}};
            for (float[] o : q) {
                float x = o[0] * cs - o[1] * sn, y = o[0] * sn + o[1] * cs;
                imr.color(c.c.r, c.c.g, c.c.b, Math.min(1f, c.life));
                imr.vertex(c.p.x + right.x * x, c.p.y + y, c.p.z + right.z * x);
            }
            if (imr.getNumVertices() > 29000) break;
        }
        imr.end();
    }

    private void overlay() {
        batch.setProjectionMatrix(ortho);
        batch.begin();
        text("AWARD CEREMONY", w / 2f, h - 30, 2.6f, GOLD, true);
        text(trackName + "  CUP   /   " + game.engine.label, w / 2f, h - 78, 1.2f, DIM, true);
        text(caption, w / 2f, h - 110, 1.4f, Color.WHITE, true);

        // place numbers + names under each podium (projected from 3D)
        for (int k = 0; k < 3 && k < order.size; k++) {
            Racer r = order.get(k);
            tmp.set(PODIUM_X[k], PODIUM_H[k] * 0.45f, 1.5f);
            cam.project(tmp, 0, 0, w, h);
            text(String.valueOf(k + 1), tmp.x, tmp.y + 20, 3f, k == 0 ? GOLD : r.alien.color, true);
            text(r.isPlayer ? "YOU" : r.alien.name, tmp.x, tmp.y - 22, 1.3f, Color.WHITE, true);
        }
        if (order.size > 3) {
            tmp.set(9f, 0.2f, 1.5f);
            cam.project(tmp, 0, 0, w, h);
            text(order.get(3).isPlayer ? "YOU" : order.get(3).alien.name, tmp.x, tmp.y - 30, 1.1f, DIM, true);
            text(lastCaption, w / 2f, 118, 1.1f, DIM, true);
        }

        float y = 92;
        StringBuilder sb = new StringBuilder();
        for (Racer r : order) {
            if (sb.length() > 0) sb.append("     ");
            sb.append(r.place).append(". ").append(r.isPlayer ? "YOU" : r.alien.name).append("  ")
                    .append(r.finished ? time(r.finishTime) : "DNF");
        }
        text(sb.toString(), w / 2f, y, 1.15f, Color.WHITE, true);
        text("ENTER  menu      R  race again      M  music", w / 2f, 40, 1.1f, DIM, true);
        batch.end();
    }

    private void text(String s, float x, float y, float scale, Color col, boolean center) {
        font.getData().setScale(scale);
        font.setColor(col);
        layout.setText(font, s);
        font.draw(batch, layout, center ? x - layout.width / 2 : x, y);
    }

    private static String time(float t) {
        int m = (int) (t / 60);
        return String.format("%d:%05.2f", m, t - m * 60);
    }

    @Override
    public void resize(int width, int height) {
        w = width; h = height;
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        ortho.setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void dispose() {
        sr.dispose();
        imr.dispose();
        batch.dispose();
        font.dispose();
    }
}
