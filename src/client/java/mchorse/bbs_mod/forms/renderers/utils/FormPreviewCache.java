package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.render.special.BbsFormGuiElementRenderer;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * How often the little 3D picture of a form in a list, palette or the HUD is drawn again.
 *
 * <p>Each visible row used to be a full model render every frame — channels, IK, physics, one draw
 * call per bone and material — fifteen rows, fifteen models. Now a form's picture lives in its own
 * off-screen texture and is re-drawn when the form changed (its pose version or the box size) and
 * otherwise in rotation, a settings-bound number of pictures per frame, so the pictures still turn
 * with the mouse and play their idle animations — at a fraction of the rate, for a fraction of the
 * cost.</p>
 *
 * <p>1.21.11: the picture cannot be captured here, because a form's UI preview does not draw when
 * it is asked to — it records a special GUI element, and vanilla renders it later, in the GUI
 * prepare phase, into a texture {@link BbsFormGuiElementRenderer} keeps for exactly this form and
 * size. So this class holds no framebuffers of its own: it decides which pictures are due, and the
 * decision rides along with the element. Everything a form does to draw itself stays untouched,
 * which is what keeps every form type (models, mobs, billboards, blocks) on the same path.</p>
 */
public class FormPreviewCache
{
    private static final long SWEEP_EVERY = 120L;
    private static final long KEEP_EPOCHS = 600L;

    /* A form keeps one renderer for its lifetime (FormUtilsClient#getRenderer), so the renderer is
     * the identity of the picture; entries are swept by last use. */
    private static final Map<FormRenderer<?>, Entry> ENTRIES = new IdentityHashMap<>();

    private static long epoch = -1L;
    private static long lastSweep;
    private static int rendersThisFrame;
    private static int requestedThisFrame;
    private static int requestedLastFrame;

    private static class Entry
    {
        int width;
        int height;
        int poseVersion;
        long renderedEpoch = -1L;
        long usedEpoch;

        /** The picture was taken while the form's model was still loading — retake as soon as it lands. */
        boolean modelPending;
    }

    /**
     * Whether this form's picture is drawn again on this frame, or the one it already has is
     * composited into the cell once more.
     */
    public static boolean claimRefresh(FormRenderer<?> renderer, int w, int h)
    {
        int budget = BBSSettings.previewRefreshBudget == null ? 0 : BBSSettings.previewRefreshBudget.get();
        Form form = renderer.getForm();

        if (budget <= 0 || w <= 0 || h <= 0 || form == null || !RenderFrame.isEnabled())
        {
            return true;
        }

        long now = RenderFrame.getEpoch();

        if (now != epoch)
        {
            epoch = now;
            requestedLastFrame = requestedThisFrame;
            requestedThisFrame = 0;
            rendersThisFrame = 0;

            if (now - lastSweep >= SWEEP_EVERY)
            {
                lastSweep = now;
                sweep(now);
            }
        }

        requestedThisFrame += 1;

        Entry entry = ENTRIES.computeIfAbsent(renderer, (key) -> new Entry());

        entry.usedEpoch = now;

        /* A changed form (or box) draws again at once, budget or not — a stale picture of an edit is
         * wrong, an unrefreshed idle animation is merely late. Rotation: with N pictures on screen
         * and K renders a frame, each one comes round every N/K frames. */
        boolean fresh = entry.renderedEpoch < 0 || entry.width != w || entry.height != h || entry.poseVersion != form.getPoseVersion();

        /* A cell drawn while its model was still in the loader's queue holds an empty picture; the
         * moment the model lands, it redraws ahead of the rotation — an empty cell that waits its
         * turn reads as a much longer load than it was. */
        if (!fresh && entry.modelPending && !isModelPending(renderer))
        {
            fresh = true;
        }

        int interval = Math.max(1, (requestedLastFrame + budget - 1) / budget);
        boolean due = rendersThisFrame < budget && now - entry.renderedEpoch >= interval;

        if (!fresh && !due)
        {
            BBSProfiler.count(BBSProfiler.Section.UI_PREVIEWS_CACHED);

            return false;
        }

        entry.width = w;
        entry.height = h;
        entry.poseVersion = form.getPoseVersion();
        entry.renderedEpoch = now;
        entry.modelPending = isModelPending(renderer);
        rendersThisFrame += 1;

        return true;
    }

    private static boolean isModelPending(FormRenderer<?> renderer)
    {
        return renderer instanceof ModelFormRenderer modelRenderer && modelRenderer.getModel() == null;
    }

    /** Forget the forms nobody asked about for a while (a closed film, a scrolled-away palette). */
    private static void sweep(long now)
    {
        Iterator<Map.Entry<FormRenderer<?>, Entry>> it = ENTRIES.entrySet().iterator();

        while (it.hasNext())
        {
            if (now - it.next().getValue().usedEpoch > KEEP_EPOCHS)
            {
                it.remove();
            }
        }
    }
}
