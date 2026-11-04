package mchorse.bbs_mod.ui.film.controller;

import com.mojang.logging.LogUtils;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * TEMPORARY diagnostics: a flight recorder for the attached film orbit, hunting a single jump of
 * the centre away from the anchor that nobody can reproduce on purpose. Every frame leaves a row
 * (what the anchor did and why, where the subject sits in front of the camera); the rows are
 * written to the game log, prefixed {@code [OrbitTrace]}, when the centre is put back by hand
 * (C) - which is exactly when a jump has just been noticed - or when a frame looks like one.
 * Remove once the jump is found.
 */
class OrbitTrace
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SIZE = 600;
    private static final long AUTO_PAUSE = 3000L;

    private final Row[] rows = new Row[SIZE];
    private int count;
    private long frame;
    private String inputs = "";
    private long lastInputFrame = -1000;
    private long lastDump;
    private final Vector3f lastCamera = new Vector3f(Float.NaN, 0F, 0F);

    private Row current;

    OrbitTrace()
    {
        for (int i = 0; i < SIZE; i++)
        {
            this.rows[i] = new Row();
        }
    }

    /** The user moved the orbit (turn, zoom, pan, the C key...) - not the anchor. */
    void input(String what)
    {
        this.inputs = this.inputs.isEmpty() ? what : this.inputs + "," + what;
        this.lastInputFrame = this.frame;

        if (what.equals("teleport"))
        {
            this.dump("C pressed (centre put back by hand)");
        }
    }

    /** What the anchor did this frame. */
    void anchor(String decision, String why, double dp, float dr, float time, int placed, String key, boolean fallback, float det, float sx, float sy, float sz)
    {
        Row row = this.rows[(int) (this.frame % SIZE)];

        row.frame = this.frame;
        row.millis = System.currentTimeMillis();
        row.decision = decision;
        row.why = why;
        row.dp = dp;
        row.dr = dr;
        row.time = time;
        row.placed = placed;
        row.key = key;
        row.fallback = fallback;
        row.det = det;
        row.sx = sx;
        row.sy = sy;
        row.sz = sz;
        row.camera.set(Float.NaN);
        row.dc = 0F;
        row.inputs = "";
        row.flags = "";
        row.distance = 0F;
        row.smoothing = 0F;

        this.current = row;
    }

    /**
     * Where the subject ended up in front of the camera this frame (camera space), and the
     * frame's flags. Closes the row.
     */
    void camera(Vector3f subjectInCamera, String flags, float distance, float smoothing)
    {
        Row row = this.current;

        if (row == null || row.frame != this.frame)
        {
            row = this.rows[(int) (this.frame % SIZE)];
            row.frame = this.frame;
            row.millis = System.currentTimeMillis();
            row.decision = "?";
            row.why = "";
            row.key = "";
        }

        float dc = 0F;

        if (subjectInCamera != null)
        {
            row.camera.set(subjectInCamera);
            dc = Float.isNaN(this.lastCamera.x) ? 0F : subjectInCamera.distance(this.lastCamera);
            this.lastCamera.set(subjectInCamera);
        }
        else
        {
            row.camera.set(Float.NaN);
            this.lastCamera.set(Float.NaN);
        }

        row.dc = dc;
        row.inputs = this.inputs;
        row.flags = flags;
        row.distance = distance;
        row.smoothing = smoothing;

        boolean quiet = this.frame - this.lastInputFrame > 20 && !flags.contains("F");
        boolean anchorJump = row.decision.equals("FOLLOW") && (row.dp > 0.3D || row.dr > 15F);
        boolean centreJump = quiet && dc > 0.2F;

        this.count = Math.min(this.count + 1, SIZE);
        this.inputs = "";
        this.frame += 1;
        this.current = null;

        if ((anchorJump || centreJump) && System.currentTimeMillis() - this.lastDump > AUTO_PAUSE)
        {
            this.dump(anchorJump ? "anchor jumped while followed" : "centre jumped on screen with no orbit input");
        }
    }

    private void dump(String reason)
    {
        this.lastDump = System.currentTimeMillis();

        int available = (int) Math.min(this.count, this.frame);
        StringBuilder out = new StringBuilder();
        int notable = 0;

        out.append("[OrbitTrace] ").append(reason).append(", last ").append(available).append(" frames, notable ones and the last 10:");

        for (int i = available; i >= 1; i--)
        {
            Row row = this.rows[(int) ((this.frame - i) % SIZE)];
            boolean recent = i <= 10;
            boolean interesting = !row.decision.equals("-") && (row.dp > 0.01D || row.dr > 0.5F || row.decision.equals("KEY"))
                || row.dc > 0.03F || !row.inputs.isEmpty() || row.fallback;

            if (!recent && (!interesting || notable >= 120))
            {
                continue;
            }

            if (!recent)
            {
                notable += 1;
            }

            out.append('\n').append("[OrbitTrace] ").append(row.format(this.lastDump));
        }

        LOGGER.info(out.toString());
    }

    private static class Row
    {
        long frame;
        long millis;
        String decision = "-";
        String why = "";
        double dp;
        float dr;
        float time;
        int placed;
        String key = "";
        boolean fallback;
        float det;
        float sx;
        float sy;
        float sz;
        final Vector3f camera = new Vector3f(Float.NaN);
        float dc;
        String inputs = "";
        String flags = "";
        float distance;
        float smoothing;

        String format(long now)
        {
            return String.format(java.util.Locale.ROOT,
                "f%d %+.2fs t=%.2f placed=%d %s(%s) dp=%.3f dr=%.1f key=%s%s det=%.3f s=(%.2f %.2f %.2f) subj=(%.2f %.2f %.2f) dc=%.3f dist=%.2f smooth=%.3f flags=%s in=%s",
                this.frame, (this.millis - now) / 1000D, this.time, this.placed, this.decision, this.why, this.dp, this.dr, this.key,
                this.fallback ? " FALLBACK" : "", this.det, this.sx, this.sy, this.sz,
                this.camera.x, this.camera.y, this.camera.z, this.dc, this.distance, this.smoothing, this.flags, this.inputs);
        }
    }
}
