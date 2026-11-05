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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;

/** Title + track select. The selected track slowly orbits in 3D behind the menu. */
public class MenuScreen extends ScreenAdapter {
    private static final Color CYAN = new Color(0.3f, 0.85f, 1f, 1f);
    private static final Color DIM = new Color(0.5f, 0.55f, 0.65f, 1f);

    private final NoBrakesGame game;
    private final Track[] tracks = new Track[NoBrakesGame.TRACKS.length];
    private final NeonRenderer[] previews = new NeonRenderer[NoBrakesGame.TRACKS.length];
    private final Array<Racer> noRacers = new Array<>();
    private final PerspectiveCamera cam = new PerspectiveCamera(50, 1280, 720);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 ortho = new Matrix4();
    private int selected;
    private float time, w = 1280, h = 720;

    public MenuScreen(NoBrakesGame game, int selected) {
        this.game = game;
        this.selected = selected;
        font.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
        cam.near = 1f;
        cam.far = 5000f;
    }

    private void ensureLoaded(int i) {
        if (tracks[i] != null) return;
        tracks[i] = Track.load(Gdx.files.internal(NoBrakesGame.TRACKS[i]));
        previews[i] = new NeonRenderer(tracks[i]);
    }

    @Override
    public void render(float delta) {
        time += delta;
        int n = tracks.length;
        if (Gdx.input.isKeyJustPressed(Keys.UP) || Gdx.input.isKeyJustPressed(Keys.W)) { selected = (selected - 1 + n) % n; Sfx.play(Sfx.BEEP, 0.3f); }
        if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.S)) { selected = (selected + 1) % n; Sfx.play(Sfx.BEEP, 0.3f); }
        if (Gdx.input.isKeyJustPressed(Keys.ENTER) || Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            Sfx.play(Sfx.GO, 0.6f);
            game.startRace(selected);
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) { Gdx.app.exit(); return; }
        if (Gdx.input.isKeyJustPressed(Keys.M)) Sfx.toggleMusic();
        int na = Alien.values().length;
        if (Gdx.input.isKeyJustPressed(Keys.LEFT) || Gdx.input.isKeyJustPressed(Keys.A)) {
            game.pilot = Alien.values()[(game.pilot.ordinal() - 1 + na) % na]; Sfx.play(Sfx.PICKUP, 0.4f);
        }
        if (Gdx.input.isKeyJustPressed(Keys.RIGHT) || Gdx.input.isKeyJustPressed(Keys.D)) {
            game.pilot = Alien.values()[(game.pilot.ordinal() + 1) % na]; Sfx.play(Sfx.PICKUP, 0.4f);
        }

        ensureLoaded(selected);
        Track t = tracks[selected];

        // orbit camera around the track's middle
        float cx = (t.minX + t.maxX) / 2f, cz = (t.minZ + t.maxZ) / 2f;
        float span = Math.max(t.maxX - t.minX, t.maxZ - t.minZ);
        float radius = span * 0.95f, ang = time * 0.18f;
        cam.position.set(cx + MathUtils.cos(ang) * radius, span * 0.55f, cz + MathUtils.sin(ang) * radius);
        cam.up.set(0, 1, 0);
        cam.lookAt(cx + span * 0.22f * MathUtils.cos(ang + MathUtils.HALF_PI), 0, cz + span * 0.22f * MathUtils.sin(ang + MathUtils.HALF_PI));   // shift the track right of the menu text
        cam.update();
        previews[selected].fog = radius * 3f;

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        previews[selected].render(cam, noRacers, null, time);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // pilot select: big bouncing alien, bottom right
        Alien al = game.pilot;
        float size = 130f / Math.max(al.w, al.h) * (0.8f + al.px * 1.2f);   // bigger species look bigger
        float px0 = w - 260 - al.w * size / 2f, py0 = 150 + Math.abs(MathUtils.sin(time * 4f)) * 14f;
        shapes.setProjectionMatrix(ortho);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        al.draw2D(shapes, px0, py0, size, 1f);
        shapes.end();

        batch.setProjectionMatrix(ortho);
        batch.begin();
        text("NO BRAKES IN SPACE", 60, h - 60, 4.2f, CYAN);
        text("A MARTIAN DEMOLITION PRIX", 64, h - 128, 1.2f, DIM);

        float y = h / 2f + 60;
        for (int i = 0; i < n; i++) {
            ensureLoaded(i);
            boolean sel = i == selected;
            text((sel ? "> " : "   ") + tracks[i].name.toUpperCase(), 60, y, sel ? 2.2f : 1.6f, sel ? Color.WHITE : DIM);
            y -= sel ? 46 : 36;
        }
        text(String.format("%.1f KM   %d LAPS   4 RACERS", t.length / 1000f, RaceScreen.LAPS), 64, y - 20, 1.2f, CYAN);

        text("PILOT", w - 420, 130, 1.1f, DIM);
        text("<  " + al.name + "  >", w - 420, 100, 2f, al.color);
        text(al.tagline, w - 420, 66, 1.1f, Color.WHITE);

        if ((int) (time * 2) % 2 == 0) text("PRESS ENTER", 60, 110, 1.6f, Color.WHITE);
        text("UP/DOWN  track     LEFT/RIGHT  pilot     ENTER  race     M  music " + (Sfx.musicOn ? "ON" : "OFF") + "     ESC  quit",
                60, 30, 1.1f, DIM);
        batch.end();
    }

    private void text(String s, float x, float y, float scale, Color col) {
        font.getData().setScale(scale);
        font.setColor(col);
        layout.setText(font, s);
        font.draw(batch, layout, x, y);
    }

    @Override
    public void show() {
        Sfx.playMusic("menu");
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
        for (NeonRenderer r : previews) if (r != null) r.dispose();
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
