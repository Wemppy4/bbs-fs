package mchorse.bbs_mod.utils.profiler;

/**
 * Per-frame call counters for the film editor's hot paths.
 *
 * <p>The main metric is the NUMBER of calls, not milliseconds: "collectMatrices went from 47
 * to 6 per frame" is a deterministic, driver-noise-proof proof that a cache works. Counters
 * are longs in a flat array indexed by {@link Section#ordinal()} — no string keys, no maps,
 * no allocation on the hot path. When {@link #enabled} is false the count call is a single
 * branch the JIT strips.</p>
 *
 * <p>{@link #frame()} is called once per frame at the existing frame boundary
 * ({@code BBSRendering.onWorldRenderBegin}); it snapshots the finished frame for the overlay
 * (so numbers don't flicker mid-frame) and zeroes the live row. Render thread only.</p>
 */
public class BBSProfiler
{
    public enum Section
    {
        /** Full channel evaluation of a model form (reset + actions + pose copy + apply). */
        EVALUATE_CHANNELS,
        /** {@code ModelInstance.captureMatrices} runs (~4 matrices per bone each). */
        CAPTURE_MATRICES,
        /** Full pose-pipeline reads: {@code FormRenderer.collectMatrices} root calls. */
        COLLECT_MATRICES,
        /** Deep pose copies in {@code ModelFormRenderer.getPose}. */
        POSE_COPY,
        /** Form renders (any form renderer's {@code render}). */
        FORM_RENDER,
        /** Stencil picking passes (full scene re-render + pixel read). */
        STENCIL_PASS,
        /** Keyframe channel segment lookups. */
        KEYFRAME_FIND_SEGMENT,
        /** Full model renders into UI boxes (lists, palettes, HUD thumbnails). */
        UI_PREVIEW_RENDERS,
        /** 2D UI draw calls issued by Batcher2D (each is a shader bind + draw + flush). */
        UI_DRAW_CALLS,
        /** Per-frame pose cache: reads served without re-evaluating the pipeline. */
        FRAME_CACHE_HIT,
        /** Per-frame pose cache: reads that had to evaluate (first read, or the pose moved). */
        FRAME_CACHE_MISS,
        /** Channel evaluations skipped because the model still holds the same evaluation. */
        CHANNELS_SKIPPED,
        /** BOBJ meshes skinned on the CPU and re-uploaded (the armature moved since the last upload). */
        BOBJ_SKINS,
        /** BOBJ mesh uploads skipped — the VAO already holds this armature pose. */
        BOBJ_SKINS_SKIPPED;

        /** Values are cached because {@code values()} clones the array on every call. */
        public static final Section[] VALUES = values();
    }

    /**
     * Timed subsystems — wall-clock milliseconds per frame. These are what rank the elephants:
     * a subsystem's share of the frame, not how often it was called. Coarse on purpose (a
     * handful of begin/end pairs per frame), so the timing itself costs nothing measurable.
     */
    public enum Timer
    {
        /** The film's forms in the world pass (BaseFilmController.render). */
        WORLD_FORMS,
        /** The stencil picking pass: scene re-render into the pick buffer + pixel read. */
        STENCIL_PASS,
        /** The gizmo's own drawing (rings, handles, pie). */
        GIZMO,
        /** The pick preview: hover highlight, sphere mask, readout (includes STENCIL_PASS). */
        PICK_PREVIEW,
        /** Onion skin frames. */
        ONION,
        /** Motion path (signature check, cache rebuilds, drawing). */
        MOTION_PATH,
        /** Timeline widgets: clips strip and keyframe editors. */
        UI_TIMELINE,
        /** Model renders into UI boxes (lists, palettes, thumbnails). */
        UI_PREVIEWS,
        /** Welded models' CPU side: seam capture, tessellation, upload (cache misses only). */
        WELDS,
        /** The whole UI tree render. */
        UI_TOTAL;

        public static final Timer[] VALUES = values();
    }

    /** Master switch, mirrored from the settings row by the overlay owner. */
    public static boolean enabled;

    private static final long[] LIVE = new long[Section.VALUES.length];
    private static final long[] SNAPSHOT = new long[Section.VALUES.length];

    private static final long[] TIMER_LIVE = new long[Timer.VALUES.length];
    private static final long[] TIMER_SNAPSHOT = new long[Timer.VALUES.length];
    private static final long[] TIMER_START = new long[Timer.VALUES.length];

    public static void begin(Timer timer)
    {
        if (enabled)
        {
            TIMER_START[timer.ordinal()] = System.nanoTime();
        }
    }

    public static void end(Timer timer)
    {
        if (enabled)
        {
            TIMER_LIVE[timer.ordinal()] += System.nanoTime() - TIMER_START[timer.ordinal()];
        }
    }

    /** The finished frame's time for a subsystem, in milliseconds. */
    public static double getMs(Timer timer)
    {
        return TIMER_SNAPSHOT[timer.ordinal()] / 1_000_000D;
    }

    public static void count(Section section)
    {
        if (enabled)
        {
            LIVE[section.ordinal()] += 1;
        }
    }

    public static void count(Section section, long n)
    {
        if (enabled)
        {
            LIVE[section.ordinal()] += n;
        }
    }

    /** The finished frame's value for a section — what the overlay shows. */
    public static long get(Section section)
    {
        return SNAPSHOT[section.ordinal()];
    }

    /** Frame boundary: publish the finished frame and start counting the next one. */
    public static void frame()
    {
        System.arraycopy(LIVE, 0, SNAPSHOT, 0, LIVE.length);
        System.arraycopy(TIMER_LIVE, 0, TIMER_SNAPSHOT, 0, TIMER_LIVE.length);

        for (int i = 0; i < LIVE.length; i++)
        {
            LIVE[i] = 0L;
        }

        for (int i = 0; i < TIMER_LIVE.length; i++)
        {
            TIMER_LIVE[i] = 0L;
        }
    }
}
