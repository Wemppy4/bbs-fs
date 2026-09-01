package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The frame as an epoch: one pose evaluation per (form, entity, transition, pose version) per
 * frame, shared by everything that reads bone matrices — the anchor chain, the gizmo, trackers,
 * the motion path, the stencil pass. This is the widening of {@link FormFrameCache}'s scope its
 * own javadoc plans for.
 *
 * <p>The objection that killed an ambient cache before — the pose is mutable global state that
 * several stages write mid-frame — is answered by versioning, not by scope-narrowing: every
 * mutation that can move the pose bumps {@link Form#getPoseVersion()} (value notifications
 * bubble there; the silent animation-state writes bump explicitly), so an entry taken before a
 * mutation simply stops matching. Frame-to-frame reuse never happens: the epoch is part of the
 * key and {@link #nextFrame()} clears the table, which also keeps dead forms from pinning
 * memory.</p>
 *
 * <p>Render thread only. {@link #invalidate()} is for passes that re-pose entities out of band
 * (onion skin renders neighbouring ticks on the live entities).</p>
 */
public class RenderFrame
{
    private static final Map<Form, Entry> ENTRIES = new IdentityHashMap<>();

    private static long epoch;

    public static long getEpoch()
    {
        return epoch;
    }

    public static boolean isEnabled()
    {
        return BBSSettings.framePoseCache == null || BBSSettings.framePoseCache.get();
    }

    /** Frame boundary: everything cached belongs to the finished frame and is dropped. */
    public static void nextFrame()
    {
        epoch += 1;
        ENTRIES.clear();
    }

    /**
     * Drop what is cached mid-frame — for a pass that mutates entity or pose state outside the
     * version accounting (the onion skin re-applying other ticks to live entities).
     */
    public static void invalidate()
    {
        epoch += 1;
        ENTRIES.clear();
    }

    /**
     * Evaluate {@code form}'s matrices for {@code entity}, reusing this frame's result when the
     * form has not moved since. The fallback (disabled or mismatch) is exactly the pre-existing
     * behaviour: a fresh full evaluation.
     */
    public static MatrixCache collect(Form form, IEntity entity, float transition)
    {
        if (!isEnabled())
        {
            return FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);
        }

        Entry entry = ENTRIES.get(form);

        if (entry != null
            && entry.entity == entity
            && Float.compare(entry.transition, transition) == 0
            && entry.poseVersion == form.getPoseVersion())
        {
            BBSProfiler.count(BBSProfiler.Section.FRAME_CACHE_HIT);

            return entry.matrices;
        }

        BBSProfiler.count(BBSProfiler.Section.FRAME_CACHE_MISS);

        MatrixCache matrices = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);

        ENTRIES.put(form, new Entry(entity, transition, form.getPoseVersion(), matrices));

        return matrices;
    }

    private record Entry(IEntity entity, float transition, int poseVersion, MatrixCache matrices)
    {}
}
