package com.saedarm.overclock;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;

/**
 * A closed track built from a Markdown table of control points.
 *
 * The spline is resampled into "frames" spaced ~1 m apart. Each frame has a
 * position plus three directions: fwd (along the track), up (away from the
 * surface) and right. Everything that moves on the track lives in track space
 * (distance s along the track, lateral offset lat), and is converted to world
 * space with these frames. That is what makes loops and upside-down sections
 * work without any gravity.
 */
public class Track {
    public final String name;
    public final String id;          // file name without extension, e.g. "ringbahn" (picks the music)
    public final float width;
    public float length;
    public float step;
    public int count;
    public Vector3[] pos, fwd, up, right;
    public float[] curve;   // signed curvature, + = turning right
    public byte[] tunnel;   // 0 none, 1 octagon, 2 box
    public float minX, maxX, minZ, maxZ;
    public float pitStart = -1, pitEnd = -1;   // recharge strip, metres from the start line

    private final Vector3 t1 = new Vector3(), t2 = new Vector3();

    private Track(String id, String name, float width) {
        this.id = id;
        this.name = name;
        this.width = width;
    }

    // ---------------------------------------------------------------- loading

    public static Track load(FileHandle file) {
        String name = file.nameWithoutExtension();
        float width = 12f;
        float scale = 1f;
        float[] pit = null;
        Array<Vector3> cps = new Array<>();
        FloatArray rolls = new FloatArray();
        Array<String> tunnels = new Array<>();

        for (String raw : file.readString("UTF-8").split("\n")) {
            String line = raw.trim();
            if (line.startsWith("# ")) name = line.substring(2).trim();
            else if (line.startsWith("width:")) width = Float.parseFloat(line.substring(6).trim());
            else if (line.startsWith("scale:")) scale = Float.parseFloat(line.substring(6).trim());
            else if (line.startsWith("pit:")) {
                String[] p = line.substring(4).trim().split("\\s+");
                pit = new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1])};
            }
            else if (line.startsWith("|")) {
                String[] c = line.substring(1).split("\\|");
                if (c.length < 5) continue;
                String first = c[0].trim();
                if (first.isEmpty() || first.startsWith("-") && first.contains("--") || Character.isLetter(first.charAt(0))) continue;
                cps.add(new Vector3(f(c[0]), f(c[1]), f(c[2])).scl(scale));
                rolls.add(f(c[3]));
                tunnels.add(c[4].trim());
            }
        }
        if (cps.size < 4) throw new IllegalArgumentException(file + ": need at least 4 control points");
        Track t = new Track(file.nameWithoutExtension(), name, width);
        t.build(cps, rolls, tunnels);
        if (pit != null) { t.pitStart = pit[0]; t.pitEnd = pit[1]; }
        return t;
    }

    private static float f(String s) { return Float.parseFloat(s.trim()); }

    private void build(Array<Vector3> cps, FloatArray rollIn, Array<String> tunIn) {
        int n = cps.size;
        CatmullRomSpline<Vector3> spline = new CatmullRomSpline<>(cps.toArray(Vector3.class), true);

        // Unwrap rolls so 270 -> 0 continues to 360 instead of spinning back.
        float[] roll = new float[n + 1];
        roll[0] = rollIn.get(0);
        for (int i = 1; i <= n; i++) {
            float prev = rollIn.get(i - 1), next = rollIn.get(i % n);
            roll[i] = roll[i - 1] + wrapDeg(next - prev);
        }

        // 1. Sample the spline finely and measure arc length.
        int fine = n * 200;
        Vector3[] fp = new Vector3[fine + 1];
        float[] fl = new float[fine + 1];
        float[] fr = new float[fine + 1];
        byte[] ft = new byte[fine + 1];
        for (int i = 0; i <= fine; i++) {
            float u = i / (float) fine;
            fp[i] = spline.valueAt(new Vector3(), u == 1f ? 0f : u);
            int seg = Math.min((int) (u * n), n - 1);
            float local = u * n - seg;
            fr[i] = MathUtils.lerp(roll[seg], roll[seg + 1], local);
            String tun = tunIn.get(seg);
            ft[i] = (byte) (tun.equals("oct") ? 1 : tun.equals("box") ? 2 : 0);
            if (i > 0) fl[i] = fl[i - 1] + fp[i].dst(fp[i - 1]);
        }
        length = fl[fine];
        count = Math.max(16, (int) length);
        step = length / count;

        // 2. Resample at even spacing.
        pos = new Vector3[count]; fwd = new Vector3[count]; up = new Vector3[count]; right = new Vector3[count];
        curve = new float[count]; tunnel = new byte[count];
        float[] frameRoll = new float[count];
        int j = 0;
        for (int k = 0; k < count; k++) {
            float d = k * step;
            while (j < fine - 1 && fl[j + 1] < d) j++;
            float a = (d - fl[j]) / Math.max(1e-5f, fl[j + 1] - fl[j]);
            pos[k] = new Vector3(fp[j]).lerp(fp[j + 1], a);
            frameRoll[k] = MathUtils.lerp(fr[j], fr[j + 1], a);
            tunnel[k] = ft[j];
        }
        for (int k = 0; k < count; k++) {
            fwd[k] = new Vector3(pos[(k + 1) % count]).sub(pos[(k - 1 + count) % count]).nor();
        }

        // 3. Parallel-transport an "up" vector along the track so it never flips in loops.
        up[0] = new Vector3(0, 1, 0);
        project(up[0], fwd[0]);
        if (up[0].len2() < 1e-4f) up[0].set(1, 0, 0);
        project(up[0], fwd[0]).nor();
        for (int k = 1; k < count; k++) up[k] = project(new Vector3(up[k - 1]), fwd[k]).nor();

        // Closing the loop: spread the leftover twist evenly so frame N meets frame 0.
        Vector3 end = project(new Vector3(up[count - 1]), fwd[0]).nor();
        float twist = MathUtils.atan2(new Vector3(end).crs(up[0]).dot(fwd[0]), end.dot(up[0]));
        for (int k = 0; k < count; k++) {
            up[k].rotateRad(fwd[k], twist * k / count);
            up[k].rotate(fwd[k], frameRoll[k]);          // designer bank / corkscrew
            right[k] = new Vector3(fwd[k]).crs(up[k]).nor();
        }

        // 4. Curvature (how hard the track turns right/left) and minimap bounds.
        minX = minZ = Float.MAX_VALUE; maxX = maxZ = -Float.MAX_VALUE;
        for (int k = 0; k < count; k++) {
            Vector3 dT = new Vector3(fwd[(k + 1) % count]).sub(fwd[(k - 1 + count) % count]).scl(1f / (2 * step));
            curve[k] = dT.dot(right[k]);
            minX = Math.min(minX, pos[k].x); maxX = Math.max(maxX, pos[k].x);
            minZ = Math.min(minZ, pos[k].z); maxZ = Math.max(maxZ, pos[k].z);
        }
    }

    private static Vector3 project(Vector3 v, Vector3 axis) {
        return v.mulAdd(axis, -v.dot(axis));
    }

    private static float wrapDeg(float d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    // ---------------------------------------------------------------- queries

    public float wrap(float s) {
        s %= length;
        return s < 0 ? s + length : s;
    }

    /** Shortest signed distance from b to a along the track (a - b). */
    public float delta(float a, float b) {
        float d = wrap(a - b);
        return d > length / 2 ? d - length : d;
    }

    public int index(float s) {
        return Math.min(count - 1, (int) (wrap(s) / step));
    }

    public float curvatureAt(float s) { return curve[index(s)]; }

    public boolean inPit(float s) {
        if (pitStart < 0) return false;
        s = wrap(s);
        return s >= pitStart && s <= pitEnd;
    }

    public int tunnelAt(float s) { return tunnel[index(s)]; }

    /** Interpolated frame at distance s. Any out-vector may be null. */
    public void frame(float s, Vector3 outPos, Vector3 outFwd, Vector3 outUp, Vector3 outRight) {
        float f = wrap(s) / step;
        int i = Math.min(count - 1, (int) f), k = (i + 1) % count;
        float a = f - (int) f;
        if (outPos != null) outPos.set(pos[i]).lerp(pos[k], a);
        t1.set(fwd[i]).lerp(fwd[k], a).nor();
        t2.set(up[i]).lerp(up[k], a).nor();
        if (outFwd != null) outFwd.set(t1);
        if (outUp != null) outUp.set(t2);
        if (outRight != null) outRight.set(t1).crs(t2).nor();
    }

    /** World position of a point at track distance s, lateral offset lat, height h above the surface. */
    public Vector3 point(float s, float lat, float h, Vector3 out) {
        Vector3 p = new Vector3(), u = new Vector3(), r = new Vector3();
        frame(s, p, null, u, r);
        return out.set(p).mulAdd(r, lat).mulAdd(u, h);
    }
}
