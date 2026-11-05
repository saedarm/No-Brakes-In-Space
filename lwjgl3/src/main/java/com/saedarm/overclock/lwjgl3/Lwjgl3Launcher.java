package com.saedarm.overclock.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.saedarm.overclock.OverclockGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Overclock");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(144);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);   // 4x MSAA keeps the thin lines clean
        new Lwjgl3Application(new OverclockGame(), config);
    }
}
