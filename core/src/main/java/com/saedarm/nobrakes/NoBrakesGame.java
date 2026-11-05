package com.saedarm.nobrakes;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;

public class NoBrakesGame extends Game {
    /** Add a track: drop a .md table in assets/tracks and list it here. */
    public static final String[] TRACKS = {
            "tracks/ringbahn.md",
            "tracks/korkenzieher.md",
            "tracks/pretzel.md",
            "tracks/spin-cycle.md",
            "tracks/toilet-bowl.md",
            "tracks/i-75.md",
    };

    @Override
    public void create() {
        // libGDX Arrays reuse one iterator by default; we nest loops over racers, so allow fresh ones.
        com.badlogic.gdx.utils.Collections.allocateIterators = true;
        Sfx.load();
        showMenu(0);
    }

    public void showMenu(int selected) {
        swap(new MenuScreen(this, selected));
    }

    /** The player's alien, picked on the menu. */
    public Alien pilot = Alien.BLIP;
    /** Speed class, picked on the menu. 150 DM is the original tuning. */
    public Engine engine = Engine.DM150;

    public void showPodium(com.badlogic.gdx.utils.Array<Racer> racers, int trackIndex) {
        swap(new PodiumScreen(this, racers, trackIndex));
    }

    public void startRace(int trackIndex) {
        swap(new RaceScreen(this, trackIndex));
    }

    private void swap(Screen next) {
        Screen old = getScreen();
        setScreen(next);
        if (old != null) old.dispose();
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        Sfx.dispose();
    }
}
