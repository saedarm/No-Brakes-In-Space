package com.saedarm.nobrakes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** 2D overlay: speedometer, lap/place, weapon, energy and boost bars, minimap. */
public class Hud implements Disposable {
    private static final Color CYAN = new Color(0.3f, 0.85f, 1f, 1f);
    private static final Color DIM = new Color(0.5f, 0.55f, 0.65f, 1f);

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer sr = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 ortho = new Matrix4();
    private float w, h;
    /** Set by RaceScreen each frame: distance of a missile locked on the player (-1 = none). */
    public float incoming = -1;
    /** Set by RaceScreen each frame: who the player's missile would lock on to. */
    public Racer myLock;

    public Hud() {
        font.getRegion().getTexture().setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
                com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
    }

    public void resize(int width, int height) {
        w = width; h = height;
        ortho.setToOrtho2D(0, 0, w, h);
    }

    public void render(Track track, Racer player, Array<Racer> racers, int laps, float raceTime,
                       float countdown, boolean finished) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(ortho);
        batch.setProjectionMatrix(ortho);

        float cx = w / 2f;
        // --- shapes
        sr.begin(ShapeType.Line);
        sr.setColor(CYAN);
        bracket(cx - 120, 22, 240, 92);
        minimap(track, racers);
        sr.end();

        sr.begin(ShapeType.Filled);
        if (incoming >= 0 && player.respawn <= 0) {          // red edge glow while locked
            float k = 0.25f + 0.25f * (float) Math.abs(Math.sin(raceTime * 10f));
            sr.setColor(1f, 0.1f, 0.15f, k);
            sr.rect(0, 0, w, 6); sr.rect(0, h - 6, w, 6); sr.rect(0, 0, 6, h); sr.rect(w - 6, 0, 6, h);
        }
        if (player.hitFlash > 0) {
            sr.setColor(1f, 0.1f, 0.15f, player.hitFlash * 0.45f);
            sr.rect(0, 0, w, h);
        }
        boolean low = player.energy < 35f && player.respawn <= 0;
        bar(30, 30, 220, 10, player.energy / 100f, !low ? CYAN : ((int) (raceTime * 6) % 2 == 0 ? Weapon.MINE.color : Color.WHITE));
        boolean cooking = player.drifting && player.overcook > 0;
        Color driftCol = cooking ? ((int) (raceTime * 16) % 2 == 0 ? Weapon.MINE.color : Color.WHITE)
                : player.boost >= 1f ? Weapon.TURBO.color : player.boost >= 0.5f ? Color.ORANGE : DIM;
        bar(30, 52, 220, 6, player.boost, driftCol);

        // pilot portrait: shakes and flashes while your alien is airborne
        float shake = player.ejectLeft > 0 ? MathUtils.random(-4f, 4f) : 0f;
        float ppx = 72f / Math.max(player.alien.w, player.alien.h);
        player.alien.draw2D(sr, 30 + shake, h - 175 + shake, ppx,
                player.hitFlash > 0 && (int) (raceTime * 20) % 2 == 0 ? 0.3f : 1f);
        sr.setColor(Color.WHITE);
        sr.rect(30 + 110 - 1, 50, 2, 10);   // stage-1 marker
        sr.end();

        // --- text
        batch.begin();
        text(String.valueOf(Math.round(player.speed * 3.6f)), cx, 100, 3.6f, Color.WHITE, true);
        text(time(raceTime), cx, 46, 1.2f, DIM, true);
        text("LAP " + Math.max(1, Math.min(laps, player.lap)) + "/" + laps, 30, h - 24, 1.6f, Color.WHITE, false);
        text(place(player.place) + " / " + racers.size, 30, h - 54, 2.2f, CYAN, false);
        text("ENERGY", 260, 42, 1f, DIM, false);
        text("DRIFT", 260, 60, 1f, DIM, false);
        if (player.weapon != null) text("[ " + player.weapon.label + " ]  SPACE", 30, 100, 1.4f, player.weapon.color, false);
        if (player.weapon == Weapon.MISSILE)
            text(myLock != null ? "LOCKED: " + myLock.name : "NO TARGET IN RANGE", 30, 124, 1.1f,
                    myLock != null ? Weapon.MISSILE.color : DIM, false);
        if (incoming >= 0 && player.respawn <= 0) {
            boolean on = (int) (raceTime * (incoming < 80 ? 14 : 6)) % 2 == 0;
            String hint = player.weapon == Weapon.SHIELD ? "SPACE = SHIELD" : "JUKE LEFT / RIGHT";
            text("!! MISSILE LOCK  " + Math.round(incoming) + " m !!", cx, h / 2f - 120, 2f,
                    on ? Weapon.MINE.color : Color.WHITE, true);
            text(hint, cx, h / 2f - 150, 1.2f, Weapon.MISSILE.color, true);
        }
        if (player.inPit && player.energy < 100f) text("RECHARGING", cx, 150, 1.6f, new Color(0.3f, 1f, 0.55f, 1f), true);
        else if (low) text("LOW ENERGY  -  RECHARGE STRIP ON THE START STRAIGHT", cx, 150, 1.1f, Weapon.MINE.color, true);
        if (player.respawn > 0) text("SYSTEMS DOWN", cx, h / 2f + 80, 2.4f, Weapon.MINE.color, true);
        else if (player.crash > 0) text("CRASHED!", cx, h / 2f + 80, 2.6f, Weapon.MINE.color, true);
        else if (player.spin > 0 && player.hitFlash <= 0) text("SPUN OUT", cx, h / 2f + 80, 2.2f, Color.ORANGE, true);
        if (player.ejectLeft > 0) text("WHEEEE!", 30 + 80, h - 110, 1.4f, player.alien.color, false);
        if (cooking) text("RELEASE!", 260, 76, 1.2f, Weapon.MINE.color, false);
        else if (player.drifting && player.boost >= 1f) text("FULL", 260, 76, 1f, Weapon.TURBO.color, false);
        if (countdown > 0) text(String.valueOf((int) Math.ceil(countdown)), cx, h / 2f + 120, 6f, Color.WHITE, true);
        else if (countdown > -1f) text("GO", cx, h / 2f + 120, 6f, Weapon.TURBO.color, true);
        if (finished) {
            text("FINISHED  " + place(player.place), cx, h / 2f + 140, 3f, Color.WHITE, true);
            float y = h / 2f + 70;
            for (Racer r : sorted(racers)) {
                String t = r.finished ? time(r.finishTime) : "--:--.--";
                text(place(r.place) + "   " + r.name + "   " + t, cx, y, 1.4f, r.color, true);
                y -= 28;
            }
            text("ENTER  award ceremony", cx, y - 20, 1.2f, DIM, true);
        }
        text(track.name.toUpperCase(), w - 30 - 200, h - 230, 1.1f, DIM, false);
        text("ARROWS steer/throttle   SHIFT drift   SPACE fire   M music " + (Sfx.musicOn ? "on" : "off") + "   R restart   ESC menu",
                cx, h - 12, 0.9f, DIM, true);
        batch.end();
    }

    private void bracket(float x, float y, float bw, float bh) {
        float t = 14;
        sr.line(x, y, x, y + bh); sr.line(x, y, x + t, y); sr.line(x, y + bh, x + t, y + bh);
        sr.line(x + bw, y, x + bw, y + bh); sr.line(x + bw, y, x + bw - t, y); sr.line(x + bw, y + bh, x + bw - t, y + bh);
    }

    private void bar(float x, float y, float bw, float bh, float fill, Color col) {
        sr.setColor(0.15f, 0.17f, 0.22f, 1f);
        sr.rect(x, y, bw, bh);
        sr.setColor(col);
        sr.rect(x, y, bw * Math.max(0, Math.min(1, fill)), bh);
    }

    private void minimap(Track track, Array<Racer> racers) {
        float size = 200, ox = w - size - 30, oy = h - size - 30;
        float spanX = track.maxX - track.minX, spanZ = track.maxZ - track.minZ;
        float scale = size / Math.max(spanX, spanZ);
        float px = ox + (size - spanX * scale) / 2, py = oy + (size - spanZ * scale) / 2;
        sr.setColor(0.4f, 0.45f, 0.6f, 0.8f);
        for (int k = 0; k < track.count; k += 4) {
            int k2 = (k + 4) % track.count;
            sr.line(px + (track.pos[k].x - track.minX) * scale, py + (track.maxZ - track.pos[k].z) * scale,
                    px + (track.pos[k2].x - track.minX) * scale, py + (track.maxZ - track.pos[k2].z) * scale);
        }
        sr.setColor(0.3f, 1f, 0.55f, 1f);
        for (int k = 0; k < track.count; k += 4) {
            int k2 = (k + 4) % track.count;
            if (!track.inPit(k * track.step) || !track.inPit(k2 * track.step)) continue;
            sr.line(px + (track.pos[k].x - track.minX) * scale, py + (track.maxZ - track.pos[k].z) * scale,
                    px + (track.pos[k2].x - track.minX) * scale, py + (track.maxZ - track.pos[k2].z) * scale);
        }
        for (Racer r : racers) {
            int k = track.index(r.s);
            float x = px + (track.pos[k].x - track.minX) * scale, y = py + (track.maxZ - track.pos[k].z) * scale;
            sr.setColor(r.color);
            float d = r.isPlayer ? 5 : 3;
            sr.rect(x - d, y - d, d * 2, d * 2);
        }
    }

    private void text(String s, float x, float y, float scale, Color col, boolean center) {
        font.getData().setScale(scale);
        font.setColor(col);
        layout.setText(font, s);
        font.draw(batch, layout, center ? x - layout.width / 2 : x, y);
    }

    private static Array<Racer> sorted(Array<Racer> racers) {
        Array<Racer> out = new Array<>(racers);
        out.sort((a, b) -> a.place - b.place);
        return out;
    }

    private static String place(int p) {
        return p + (p == 1 ? "ST" : p == 2 ? "ND" : p == 3 ? "RD" : "TH");
    }

    private static String time(float t) {
        int m = (int) (t / 60);
        float s = t - m * 60;
        return String.format("%d:%05.2f", m, s);
    }

    @Override
    public void dispose() {
        batch.dispose();
        sr.dispose();
        font.dispose();
    }
}
