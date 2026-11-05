package com.saedarm.nobrakes;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import java.io.File;

/**
 * Headless balance check: runs AI-only races (no window, no GPU) on every track and
 * prints track stats and combat numbers. Use it after changing tracks, weapons, AI or
 * speed numbers to see if something got broken, too easy, or too punishing.
 *
 *   ./gradlew balance                     (150 DM)
 *   ./gradlew balance -Pengine=DM200      (any Engine name: DM50, DM100, DM150, DM200)
 */
public class BalanceSim {
    public static void main(String[] args) {
        com.badlogic.gdx.utils.Collections.allocateIterators = true;
        Engine engine = args.length > 0 ? Engine.valueOf(args[0]) : Engine.DM150;
        int races = 20;
        float seconds = 120f, dt = 1 / 60f;
        System.out.printf("%s, %d AI-only races x %.0f s per track, rivals' awareness 0.5-0.9%n", engine.label, races, seconds);
        System.out.printf("%-16s %6s %8s %8s | %8s %8s %8s %8s %6s%n",
                "track", "km", "tightest", "AI lap", "missile%", "mines", "crashes", "pylons", "KOs");
        for (String path : NoBrakesGame.TRACKS) {
            Track t = Track.load(new FileHandle(new File("assets/" + path)));
            float maxCurve = 0;
            for (float c : t.curve) maxCurve = Math.max(maxCurve, Math.abs(c));
            Combat.missileHits = Combat.mineHits = Combat.missilesFired = Combat.crashes = Combat.pylonHits = 0;
            int kos = 0;
            double dist = 0;
            for (int race = 0; race < races; race++) {
                MathUtils.random.setSeed(race);
                Combat c = new Combat(t);
                Array<Racer> rs = new Array<>();
                float[] aw = {0.9f, 0.7f, 0.5f, 0.7f};
                for (int i = 0; i < 4; i++) {
                    Racer r = new Racer("R" + i, Color.WHITE, false);
                    r.baseSkill = 0.97f - i * 0.02f;
                    r.awareness = aw[i];
                    r.engine = engine.mult;
                    r.s = t.wrap(-6 - i * 7);
                    r.distance = -6 - i * 7;
                    rs.add(r);
                }
                for (float tt = 0; tt < seconds; tt += dt) {
                    for (Racer r : rs) { r.updateAi(dt, t, rs.get(0), c); c.aiUse(dt, r, rs); }
                    c.update(dt, rs);
                    for (Racer r : rs) {
                        if (r.burst == 2) kos++;
                        r.burst = 0;
                    }
                }
                for (Racer r : rs) dist += r.distance;
            }
            double avgSpeed = dist / (races * 4.0 * seconds);
            System.out.printf("%-16s %6.1f %7.0fm %7.1fs | %7.0f%% %8d %8d %8d %6d%n",
                    t.name, t.length / 1000f, 1f / Math.max(1e-4f, maxCurve), t.length / avgSpeed,
                    100f * Combat.missileHits / Math.max(1, Combat.missilesFired), Combat.mineHits, Combat.crashes, Combat.pylonHits, kos);
        }
    }
}
