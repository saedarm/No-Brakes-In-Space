package com.saedarm.overclock;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;

public class OverclockGame extends Game {
    /** Add a track: drop a .md table in assets/tracks and list it here. */
    public static final String[] TRACKS = {
            "tracks/ringbahn.md",
            "tracks/korkenzieher.md",
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
