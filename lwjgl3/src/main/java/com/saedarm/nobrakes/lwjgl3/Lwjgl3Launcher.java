package com.saedarm.nobrakes.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.saedarm.nobrakes.NoBrakesGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("No Brakes in Space");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(144);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);   // 4x MSAA keeps the thin lines clean
        new Lwjgl3Application(new NoBrakesGame(), config);
    }
}
