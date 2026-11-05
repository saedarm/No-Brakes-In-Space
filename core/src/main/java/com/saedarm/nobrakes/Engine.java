package com.saedarm.nobrakes;

/**
 * Speed classes, like 50/100/150cc, except the engines run on dark matter.
 * mult scales top speed and acceleration for everyone in the race.
 */
public enum Engine {
    DM50("50 DM", "Dark matter, decaf.", 0.62f),
    DM100("100 DM", "Standard issue.", 0.8f),
    DM150("150 DM", "Probably fine.", 1.0f),
    DM200("200 DM", "Containment not guaranteed.", 1.25f);

    public final String label, tagline;
    public final float mult;

    Engine(String label, String tagline, float mult) {
        this.label = label;
        this.tagline = tagline;
        this.mult = mult;
    }

    public Engine next() { return values()[(ordinal() + 1) % values().length]; }
    public Engine prev() { return values()[(ordinal() - 1 + values().length) % values().length]; }
}
