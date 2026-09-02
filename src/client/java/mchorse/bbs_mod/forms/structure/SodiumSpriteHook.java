package mchorse.bbs_mod.forms.structure;

import net.minecraft.client.texture.Sprite;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Sodium animates only sprites it saw being rendered this frame ("Animate Only Visible
 * Textures"); baked structure geometry bypasses its pipeline, so water/lava/portal sprites
 * inside structures would freeze. This hook marks sprites active through Sodium's
 * {@code SpriteUtil} — resolved reflectively (0.5 jellysquid statics or 0.6 caffeinemc API
 * instance), and turns into a no-op when Sodium is absent or the API moved.
 */
public class SodiumSpriteHook
{
    private static MethodHandle markSpriteActive;
    private static Object instance;
    private static boolean resolved;

    private static void resolve()
    {
        resolved = true;

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        /* Sodium 0.5.x: me.jellysquid...SpriteUtil.markSpriteActive(Sprite) static */
        try
        {
            Class<?> util = Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil");

            markSpriteActive = lookup.findStatic(util, "markSpriteActive", MethodType.methodType(void.class, Sprite.class));

            return;
        }
        catch (Throwable ignored)
        {}

        /* Sodium 0.6+: net.caffeinemc...api.texture.SpriteUtil.INSTANCE.markSpriteActive(Sprite) */
        try
        {
            Class<?> util = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");

            instance = util.getField("INSTANCE").get(null);
            markSpriteActive = lookup.findVirtual(util, "markSpriteActive", MethodType.methodType(void.class, Sprite.class));
        }
        catch (Throwable ignored)
        {}
    }

    public static void markActive(Iterable<Sprite> sprites)
    {
        if (!resolved)
        {
            resolve();
        }

        if (markSpriteActive == null)
        {
            return;
        }

        try
        {
            if (instance == null)
            {
                for (Sprite sprite : sprites)
                {
                    markSpriteActive.invoke(sprite);
                }
            }
            else
            {
                for (Sprite sprite : sprites)
                {
                    markSpriteActive.invoke(instance, sprite);
                }
            }
        }
        catch (Throwable e)
        {
            /* API misbehaved — disable the hook instead of spamming */
            markSpriteActive = null;
        }
    }
}
