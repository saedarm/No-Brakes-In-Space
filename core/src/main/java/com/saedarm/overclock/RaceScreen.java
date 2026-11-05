package com.saedarm.overclock;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

public class RaceScreen extends ScreenAdapter {
    public static final int LAPS = 3;

    private final OverclockGame game;
    private final int trackIndex;
    private final Track track;
    private final Combat combat;
    private final NeonRenderer renderer;
    private final Hud hud = new Hud();
    private final PerspectiveCamera cam = new PerspectiveCamera(70, 1280, 720);
    private final Array<Racer> racers = new Array<>();
    private final Racer player;

    private float countdown = 3f, raceTime, time;
    private boolean finished;

    private final Vector3 p = new Vector3(), f = new Vector3(), u = new Vector3(), r = new Vector3();
    private final Vector3 camUp = new Vector3(0, 1, 0), target = new Vector3();
    private boolean camInit;

    public RaceScreen(OverclockGame game, int trackIndex) {
        this.game = game;
        this.trackIndex = trackIndex;
        this.track = Track.load(Gdx.files.internal(OverclockGame.TRACKS[trackIndex]));
        this.combat = new Combat(track);
        this.renderer = new NeonRenderer(track);

        player = new Racer("YOU", new Color(0.35f, 1f, 0.45f, 1f), true);
        racers.add(player);
        racers.add(ai("VOLT", new Color(1f, 0.3f, 0.9f, 1f), 0.97f));
        racers.add(ai("EMBER", new Color(1f, 0.55f, 0.15f, 1f), 0.94f));
        racers.add(ai("GLINT", new Color(0.3f, 0.8f, 1f, 1f), 0.91f));

        // grid: two columns, just behind the start line (crossing it starts lap 1)
        for (int i = 0; i < racers.size; i++) {
            Racer rc = racers.get(racers.size - 1 - i);
            rc.s = track.wrap(-6f - i * 7f);
            rc.lat = (i % 2 == 0 ? -1 : 1) * track.width * 0.2f;
            rc.distance = -6f - i * 7f;
        }
        cam.near = 0.3f;
        cam.far = 2000f;
    }

    private Racer ai(String name, Color c, float skill) {
        Racer a = new Racer(name, c, false);
        a.baseSkill = skill;
        a.line = MathUtils.random(-1.5f, 1.5f);
        return a;
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 1 / 30f);
        time += dt;
        if (handleKeys()) return;   // screen was replaced; stop using this one

        countdown -= dt;
        if (countdown <= 0) {
            if (!finished) raceTime += dt;
            float steer = (key(Keys.RIGHT, Keys.D) ? 1 : 0) - (key(Keys.LEFT, Keys.A) ? 1 : 0);
            if (finished) player.updateAi(dt, track, player);   // autopilot after the flag
            else player.update(dt, track, steer, key(Keys.UP, Keys.W), key(Keys.DOWN, Keys.S),
                    key(Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT));
            for (Racer rc : racers) {
                if (rc.isPlayer) continue;
                rc.updateAi(dt, track, player);
                combat.aiUse(dt, rc, racers);
            }
            combat.update(dt, racers);
            rank();
        }

        updateCamera(dt);
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        renderer.render(cam, racers, combat, time);
        hud.render(track, player, racers, LAPS, raceTime, countdown, finished);
    }

    private boolean handleKeys() {
        if (Gdx.input.isKeyJustPressed(Keys.SPACE) && countdown <= 0 && !finished) combat.fire(player, racers);
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Keys.R)) { game.startRace(trackIndex); return true; }
        if (Gdx.input.isKeyJustPressed(Keys.T)) { game.startRace((trackIndex + 1) % OverclockGame.TRACKS.length); return true; }
        return false;
    }

    private static boolean key(int a, int b) {
        return Gdx.input.isKeyPressed(a) || Gdx.input.isKeyPressed(b);
    }

    private void rank() {
        for (Racer rc : racers) {
            if (!rc.finished && rc.lap > LAPS) {
                rc.finished = true;
                rc.finishTime = raceTime;
                if (rc.isPlayer) finished = true;
            }
        }
        Array<Racer> order = new Array<>(racers);
        order.sort((a, b) -> {
            if (a.finished && b.finished) return Float.compare(a.finishTime, b.finishTime);
            if (a.finished != b.finished) return a.finished ? -1 : 1;
            return Float.compare(b.distance, a.distance);
        });
        for (int i = 0; i < order.size; i++) order.get(i).place = i + 1;
    }

    /** Chase camera: sits behind and above the ship, and takes its "up" from the track so loops roll the world. */
    private void updateCamera(float dt) {
        track.frame(player.s, p, f, u, r);
        p.mulAdd(r, player.lat).mulAdd(u, Racer.HOVER);
        target.set(p).mulAdd(f, -7.5f).mulAdd(u, 2.6f);

        if (!camInit) { cam.position.set(target); camUp.set(u); camInit = true; }
        cam.position.lerp(target, 1f - (float) Math.exp(-14f * dt));
        camUp.lerp(u, 1f - (float) Math.exp(-8f * dt)).nor();

        Vector3 look = new Vector3(p).mulAdd(f, 12f);
        cam.direction.set(look).sub(cam.position).nor();
        cam.up.set(camUp);
        cam.normalizeUp();
        cam.fieldOfView = 68f + 18f * Math.min(1.4f, player.speed / Racer.MAX_SPEED);
        cam.update();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        hud.resize(width, height);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        hud.dispose();
    }
}
