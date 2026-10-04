package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.iris.OptiFineShaderOptions;
import mchorse.bbs_mod.utils.iris.ShaderMenu;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Entry point of the refreshed shader-curve editor. Decides whether the "add curve" action opens the
 * BBS-styled {@link UIShaderOptionPicker} (a mirror of the active pack's options menu) or falls back to
 * BBS's stock flat list.
 *
 * <p>OptiFine is optional: its menu provider uses reflection, and an absent or
 * disabled shader pack leaves the original vanilla-curve list available.</p>
 */
public final class ShaderCurvePicker
{
    private ShaderCurvePicker()
    {}

    /**
     * @return {@code true} if the refreshed picker was shown (caller should not also open the flat list);
     *         {@code false} to let BBS's stock {@code offerCurveKeys} flat list run.
     */
    public static boolean open(UIContext context, List<String> existing, Consumer<String> callback)
    {
        if (!OptiFineShaderOptions.available())
        {
            return false;
        }

        ShaderMenu menu;
        Map<String, String> languageMap;

        try
        {
            menu = OptiFineShaderOptions.menu();

            if (menu == null)
            {
                return false;
            }

            languageMap = OptiFineShaderOptions.language(BBSModClient.getLanguageKey());
        }
        catch (LinkageError e)
        {
            /* Optional provider cannot be linked: retain the original flat list. */
            return false;
        }

        List<String> addedChannels = new ArrayList<>(existing);
        Consumer<String> onAddOptionId = (id) ->
        {
            String channel = CurveClip.SHADER_CURVES_PREFIX + id;

            callback.accept(channel);
            addedChannels.add(channel);
        };
        Runnable openLegacy = () ->
        {
            UICurveClip.offerCurveKeyList(context, addedChannels, callback);
        };

        UIShaderOptionPicker picker = new UIShaderOptionPicker(menu, languageMap, collectAddedOptionIds(existing), onAddOptionId, openLegacy);

        UIOverlay.addOverlay(context, picker, picker.preferredWidth(), picker.preferredHeight());

        return true;
    }

    /**
     * Bare option ids (prefix stripped) of the curve channels already present, so the picker can mark
     * those cells as animated (a highlighted outline).
     */
    private static Set<String> collectAddedOptionIds(List<String> existing)
    {
        Set<String> added = new HashSet<>();

        for (String id : existing)
        {
            if (id.startsWith(CurveClip.SHADER_CURVES_PREFIX))
            {
                added.add(id.substring(CurveClip.SHADER_CURVES_PREFIX.length()));
            }
        }

        return added;
    }
}
