package com.saedarm.nobrakes;

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

    private final NoBrakesGame game;
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
    private float afterFinish;   // seconds since the player crossed the line (podium after a few)
    private int lastCount = 4;
    private float lockBeep;

    private final Vector3 p = new Vector3(), f = new Vector3(), u = new Vector3(), r = new Vector3();
    private final Vector3 camUp = new Vector3(0, 1, 0), target = new Vector3();
    private boolean camInit;
    private final Vector3 camBase = new Vector3();
    private float shake;

    public RaceScreen(NoBrakesGame game, int trackIndex) {
        this.game = game;
        this.trackIndex = trackIndex;
        this.track = Track.load(Gdx.files.internal(NoBrakesGame.TRACKS[trackIndex]));
        this.combat = new Combat(track);
        this.renderer = new NeonRenderer(track);

        player = new Racer(game.pilot, true);
        racers.add(player);
        // three rivals: random other species, from sharpest to sloppiest
        Array<Alien> others = new Array<>(Alien.values());
        others.removeValue(game.pilot, true);
        others.shuffle();
        racers.add(ai(others.get(0), 0.97f, 0.9f));
        racers.add(ai(others.get(1), 0.94f, 0.7f));
        racers.add(ai(others.get(2), 0.91f, 0.5f));

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

    /** skill = top-speed factor, awareness = how early it spots mines and missiles (0..1). */
    private Racer ai(Alien alien, float skill, float awareness) {
        Racer a = new Racer(alien, false);
        a.baseSkill = skill;
        a.awareness = awareness;
        a.line = MathUtils.random(-1.5f, 1.5f);
        return a;
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 1 / 30f);
        time += dt;
        if (handleKeys()) return;   // screen was replaced; stop using this one
        if (finished) {
            afterFinish += dt;
            if (afterFinish > 4f || (afterFinish > 0.5f && Gdx.input.isKeyJustPressed(Keys.ENTER))) {
                rank();
                game.showPodium(racers, trackIndex);
                return;
            }
        }

        countdown -= dt;
        int count = (int) Math.ceil(countdown);
        if (count != lastCount) {
            if (count >= 1 && count <= 3) Sfx.play(Sfx.BEEP, 0.6f);
            else if (count <= 0 && lastCount >= 1) Sfx.play(Sfx.GO, 0.7f);
            lastCount = count;
        }
        if (countdown <= 0) {
            if (!finished) raceTime += dt;
            float steer = (key(Keys.RIGHT, Keys.D) ? 1 : 0) - (key(Keys.LEFT, Keys.A) ? 1 : 0);
            if (finished) player.updateAi(dt, track, player, combat);   // autopilot after the flag
            else player.update(dt, track, steer, key(Keys.UP, Keys.W), key(Keys.DOWN, Keys.S),
                    key(Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT));
            for (Racer rc : racers) {
                if (rc.isPlayer) continue;
                rc.updateAi(dt, track, player, combat);
                combat.aiUse(dt, rc, racers);
            }
            combat.update(dt, racers);
            rank();
        }

        if (player.burst > 0) shake = Math.max(shake, player.burst == 2 ? 1f : 0.55f);
        if (player.scraping && player.speed > 30f) shake = Math.max(shake, 0.12f);
        renderer.update(dt, racers);   // spawns debris and clears burst flags
        Sfx.update(dt, player);

        // lock-on: warning for missiles chasing us, brackets on whoever our missile would hit
        Combat.Missile threat = combat.incoming(player, Combat.MISSILE_RANGE);
        hud.incoming = threat == null ? -1 : track.delta(player.s, threat.s);
        if (threat != null && !finished) {
            lockBeep -= dt;
            if (lockBeep <= 0) {
                Sfx.play(Sfx.LOCK, 0.6f);
                lockBeep = MathUtils.clamp(hud.incoming / 250f, 0.07f, 0.45f);
            }
        } else lockBeep = 0;
        Racer mine = player.weapon == Weapon.MISSILE && !finished ? combat.lockTarget(player, racers) : null;
        hud.myLock = mine;
        renderer.lockTarget = mine;
        updateCamera(dt);
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        renderer.render(cam, racers, combat, time);
        hud.render(track, player, racers, LAPS, raceTime, countdown, finished);
    }

    private boolean handleKeys() {
        if (Gdx.input.isKeyJustPressed(Keys.SPACE) && countdown <= 0 && !finished) combat.fire(player, racers);
        if (Gdx.input.isKeyJustPressed(Keys.M)) Sfx.toggleMusic();
        // debug keys for play-testing combat
        if (Gdx.input.isKeyJustPressed(Keys.F1)) player.weapon = Weapon.MISSILE;
        if (Gdx.input.isKeyJustPressed(Keys.F3)) { rank(); game.showPodium(racers, trackIndex); return true; }
        if (Gdx.input.isKeyJustPressed(Keys.F2) && countdown <= 0) combat.debugMissileAt(player, racers.get(1), 150f);
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)
                ) { game.showMenu(trackIndex); return true; }
        if (Gdx.input.isKeyJustPressed(Keys.R)) { game.startRace(trackIndex); return true; }
        if (Gdx.input.isKeyJustPressed(Keys.T)) { game.startRace((trackIndex + 1) % NoBrakesGame.TRACKS.length); return true; }
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

        if (!camInit) { camBase.set(target); camUp.set(u); camInit = true; }
        camBase.lerp(target, 1f - (float) Math.exp(-14f * dt));
        shake = Math.max(0, shake - 1.8f * dt);
        float amp = shake * shake * 0.9f;
        cam.position.set(camBase)
                .mulAdd(r, MathUtils.random(-amp, amp))
                .mulAdd(u, MathUtils.random(-amp, amp));
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
    public void show() {
        Sfx.startRace(track, player);
        Sfx.playMusic(track.id);
    }

    @Override
    public void hide() {
        Sfx.stopLoops();
    }

    @Override
    public void dispose() {
        renderer.dispose();
        hud.dispose();
    }
}
