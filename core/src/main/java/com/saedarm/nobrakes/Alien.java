package com.saedarm.nobrakes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * The pilots: little pixel aliens of different shapes and sizes.
 * Each one is a tiny bitmap drawn as squares, either flat in the HUD/menu or as a
 * billboard sitting on (or flying out of) its glider in 3D.
 *
 * Bitmap key: X body, # darker body, o white, p pupil (black), * yellow accent, . empty
 */
public enum Alien {
    BLIP("BLIP", "Small. Loud. Fast.", new Color(0.4f, 1f, 0.45f, 1f), 0.17f,
            "..X..X..",
            "...XX...",
            "..XXXX..",
            ".XoXXoX.",
            "XXXXXXXX",
            "X.X##X.X",
            "X.X..X.X",
            "...XX..."),
    GLORB("GLORB", "One eye. Zero brakes.", new Color(1f, 0.35f, 0.9f, 1f), 0.22f,
            "...XXXX...",
            ".XXXXXXXX.",
            "XXXooooXXX",
            "XXooppooXX",
            "XXXooooXXX",
            "XXXXXXXXXX",
            ".XXX##XXX.",
            "X.X.XX.X.X"),
    ZIP("ZIP", "Mostly antenna.", new Color(0.3f, 0.85f, 1f, 1f), 0.15f,
            "*.....*",
            ".X...X.",
            "..XXX..",
            ".XoXoX.",
            ".XXXXX.",
            "..X#X..",
            "..XXX..",
            "..XXX..",
            ".X...X.",
            ".X...X."),
    KRONK("KRONK", "Pinches first. Asks never.", new Color(1f, 0.55f, 0.15f, 1f), 0.19f,
            "XX........XX",
            "X.X......X.X",
            "XX..XXXX..XX",
            ".X.XoXXoX.X.",
            "..XXXXXXXX..",
            "..XX####XX..",
            ".X.X....X.X.",
            "X..X....X..X"),
    MOOP("MOOP", "Technically a soup.", new Color(0.65f, 0.45f, 1f, 1f), 0.18f,
            "..XXXXX..",
            ".XXXXXXX.",
            "XXoXXXoXX",
            "XXXX#XXXX",
            "XXXXXXXXX",
            "X.X.X.X.X",
            "X.X.X.X.X",
            ".X.X.X.X.",
            "X...X...X"),
    SPROING("SPROING", "Three eyes, no license.", new Color(1f, 0.9f, 0.25f, 1f), 0.13f,
            "*..*..*",
            "X..X..X",
            ".XXXXX.",
            "XoXoXoX",
            "XXXXXXX",
            ".X###X.",
            ".X...X.",
            "XX...XX");

    public final String name, tagline;
    public final Color color;
    public final float px;            // size of one pixel in metres: bigger = bigger alien
    public final String[] rows;
    public final int w, h;

    Alien(String name, String tagline, Color color, float px, String... rows) {
        this.name = name;
        this.tagline = tagline;
        this.color = color;
        this.px = px;
        this.rows = rows;
        this.h = rows.length;
        int max = 0;
        for (String r : rows) max = Math.max(max, r.length());
        this.w = max;
    }

    /** Height of the sprite in metres. */
    public float height() { return h * px; }

    private static final Color tmp = new Color();

    private Color pixel(char ch, boolean flash) {
        if (flash) return tmp.set(1f, 0.2f, 0.25f, 1f);
        switch (ch) {
            case 'X': return tmp.set(color);
            case '#': return tmp.set(color.r * 0.55f, color.g * 0.55f, color.b * 0.55f, 1f);
            case 'o': return tmp.set(1f, 1f, 1f, 1f);
            case 'p': return tmp.set(0.05f, 0.05f, 0.1f, 1f);
            case '*': return tmp.set(1f, 0.95f, 0.4f, 1f);
            default: return null;
        }
    }

    /** Flat, for HUD and menus. ShapeRenderer must be in Filled mode. (x, y) is bottom-left. */
    public void draw2D(ShapeRenderer sr, float x, float y, float size, float alpha) {
        for (int r = 0; r < h; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                Color col = pixel(row.charAt(c), false);
                if (col == null) continue;
                sr.setColor(col.r, col.g, col.b, alpha);
                sr.rect(x + c * size, y + (h - 1 - r) * size, size * 0.92f, size * 0.92f);
            }
        }
    }

    /**
     * As a billboard in 3D. base = bottom-centre of the sprite in world space; right/up =
     * camera axes so it always faces you. rot spins it (degrees) around its own middle.
     * Call between imr.begin(..., GL_TRIANGLES) and imr.end().
     */
    public void draw3D(ImmediateModeRenderer20 imr, Vector3 base, Vector3 right, Vector3 up,
                       float rot, float scale, float alpha, boolean flash) {
        float p = px * scale;
        float cos = MathUtils.cosDeg(rot), sin = MathUtils.sinDeg(rot);
        float cy = h * p / 2f, half = p * 0.46f;
        for (int r = 0; r < h; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                Color col = pixel(row.charAt(c), flash);
                if (col == null) continue;
                float lx = (c - w / 2f + 0.5f) * p, ly = (h - 1 - r + 0.5f) * p - cy;
                quad(imr, base, right, up, cos, sin, cy, lx, ly, half, col, alpha);
            }
        }
    }

    private static void quad(ImmediateModeRenderer20 imr, Vector3 base, Vector3 right, Vector3 up,
                             float cos, float sin, float cy, float lx, float ly, float half, Color col, float alpha) {
        float[][] k = {{-half, -half}, {half, -half}, {half, half}, {-half, -half}, {half, half}, {-half, half}};
        for (float[] o : k) {
            float x = lx + o[0], y = ly + o[1];
            float rx = x * cos - y * sin, ry = x * sin + y * cos + cy;
            imr.color(col.r, col.g, col.b, alpha);
            imr.vertex(base.x + right.x * rx + up.x * ry,
                    base.y + right.y * rx + up.y * ry,
                    base.z + right.z * rx + up.z * ry);
        }
    }
}
