package com.saedarm.overclock;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import java.io.File;

/**
 * Headless balance check: runs many AI-only races (no window, no GPU) and prints
 * how often missiles land, how many mine hits and knockouts happen, with AI threat
 * awareness off vs on. Run with:  ./gradlew balance
 * Use it after changing weapon or AI numbers to see if combat got too easy or too punishing.
 */
public class BalanceSim {
    public static void main(String[] args) {
        com.badlogic.gdx.utils.Collections.allocateIterators = true;
        int races = 40;
        float seconds = 120f, dt = 1 / 60f;
        for (String path : OverclockGame.TRACKS) {
            Track t = Track.load(new FileHandle(new File("assets/" + path)));
            for (float aw : new float[]{0f, 1f}) {
                Combat.missileHits = Combat.mineHits = Combat.missilesFired = 0;
                int kos = 0;
                for (int race = 0; race < races; race++) {
                    MathUtils.random.setSeed(race);
                    Combat c = new Combat(t);
                    Array<Racer> rs = new Array<>();
                    for (int i = 0; i < 4; i++) {
                        Racer r = new Racer("R" + i, Color.WHITE, false);
                        r.baseSkill = 0.95f + i * 0.01f;
                        r.awareness = aw;
                        r.s = t.wrap(-6 - i * 7);
                        r.distance = -6 - i * 7;
                        rs.add(r);
                    }
                    for (float tt = 0; tt < seconds; tt += dt) {
                        for (Racer r : rs) { r.updateAi(dt, t, rs.get(0), c); c.aiUse(dt, r, rs); }
                        c.update(dt, rs);
                        for (Racer r : rs) { if (r.burst == 2) kos++; r.burst = 0; }
                    }
                }
                System.out.printf("%-13s awareness %.0f | missiles %4d fired, %3.0f%% hit | mine hits %4d | knockouts %3d%n",
                        t.name, aw, Combat.missilesFired, 100f * Combat.missileHits / Math.max(1, Combat.missilesFired),
                        Combat.mineHits, kos);
            }
        }
        System.out.printf("(%d races x %.0f s per track, 4 AI racers)%n", races, seconds);
    }
}
