package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Turning a build into a form: the wand's region is saved as a structure and then emptied out of
 * the world, so the film holds the build as something it can move and animate instead of blocks
 * standing in the shot.
 *
 * <p>The world is only touched after the file is written, and the caller is only told to make its
 * replay after the server says so — the structure form would otherwise be pointed at a file that
 * does not exist yet, and {@link StructureManager} would remember that id as broken for good.</p>
 */
public class StructureCut
{
    private static String pendingName;
    private static Consumer<Boolean> pendingCallback;

    /**
     * A free id for a film's next cut. Structures made for a film live under it — the picker shows
     * them as a folder, and nothing a film cuts ends up loose among the hand-saved ones.
     */
    public static String nextId(String filmId)
    {
        String prefix = "bbs:" + sanitize(filmId) + "/";
        List<String> ids = StructureManager.getStructureIds();
        int last = 0;

        for (String id : ids)
        {
            if (!id.startsWith(prefix))
            {
                continue;
            }

            try
            {
                last = Math.max(last, Integer.parseInt(id.substring(prefix.length())));
            }
            catch (NumberFormatException ignored)
            {
                /* Someone's own name under the same folder — it just isn't in the running */
            }
        }

        return prefix + (last + 1);
    }

    /** Film ids are free-form, structure paths are not. */
    private static String sanitize(String id)
    {
        String path = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9/._-]", "");

        return path.isEmpty() ? "film" : path;
    }

    /**
     * @param callback told whether the region really went, on the client thread — the replay is
     *                 its to make
     */
    public static void request(String name, BlockPos min, BlockPos max, Consumer<Boolean> callback)
    {
        pendingName = name;
        pendingCallback = callback;

        ClientNetwork.sendCutStructure(name, min, max);
    }

    /** The server's word: the file is written and the world is empty, or nothing happened at all. */
    public static void onCut(boolean ok, String name)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Consumer<Boolean> callback = pendingCallback;

        StructureManager.invalidate();

        if (mc.player != null)
        {
            mc.player.sendMessage(Text.literal((ok ? UIKeys.STRUCTURE_CUT_DONE : UIKeys.STRUCTURE_CUT_FAILED).format(name).get()), true);
        }

        if (ok)
        {
            StructureSelection.clear();
        }

        pendingCallback = null;

        if (callback != null && name.equals(pendingName))
        {
            callback.accept(ok);
        }

        pendingName = null;
    }
}
