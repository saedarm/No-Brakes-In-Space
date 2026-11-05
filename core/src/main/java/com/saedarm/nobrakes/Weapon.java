package com.saedarm.nobrakes;

import com.badlogic.gdx.graphics.Color;

public enum Weapon {
    MISSILE("MISSILE", new Color(1f, 0.35f, 0.2f, 1f)),
    MINE("MINES", new Color(1f, 0.15f, 0.4f, 1f)),
    SHIELD("SHIELD", new Color(0.3f, 0.8f, 1f, 1f)),
    TURBO("TURBO", new Color(1f, 0.9f, 0.2f, 1f));

    public final String label;
    public final Color color;

    Weapon(String label, Color color) {
        this.label = label;
        this.color = color;
    }
}
