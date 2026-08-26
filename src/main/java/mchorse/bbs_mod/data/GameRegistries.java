package mchorse.bbs_mod.data;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The game's dynamic registries, for the vanilla codecs BBS serializes data with.
 *
 * <p>Since 1.20.5 an {@link net.minecraft.item.ItemStack} carries components, and some of them
 * point at entries of DATA-DRIVEN registries — enchantments above all. Their codec is a
 * {@code RegistryFixedCodec}, which refuses to encode or decode unless the ops it is handed is a
 * {@link RegistryOps} that can name the registry the entry belongs to. Plain
 * {@link NbtOps#INSTANCE} is not, so an enchanted stack encodes to an ERROR — and BBS, which
 * turned that error into an empty map, silently wrote such items down as air. Everything without
 * a data-driven component (a plain helmet, a stone block) encodes either way, which is why only
 * enchanted items ever went missing.
 *
 * <p>The lookup lives behind suppliers because it belongs to a running game, not to the mod: on a
 * client it comes from the play connection (a remote server's registries, not ours), on a server
 * from the server itself. Both halves register their own source and the first one that can answer
 * wins, so common code can ask for the ops without knowing which side it is on. With no game
 * running — nothing is loaded and nothing is being saved — it falls back to plain NbtOps, exactly
 * what the code did before.
 */
public class GameRegistries
{
    private static final List<Supplier<RegistryWrapper.WrapperLookup>> SOURCES = new ArrayList<>();

    /**
     * Register a way to reach the running game's registries. Called once per side, at mod init;
     * the supplier is expected to return {@code null} while that side has no game.
     */
    public static void addSource(Supplier<RegistryWrapper.WrapperLookup> source)
    {
        if (source != null)
        {
            SOURCES.add(source);
        }
    }

    /** The running game's registries, or {@code null} when no side can answer. */
    public static RegistryWrapper.WrapperLookup lookup()
    {
        for (Supplier<RegistryWrapper.WrapperLookup> source : SOURCES)
        {
            RegistryWrapper.WrapperLookup lookup = source.get();

            if (lookup != null)
            {
                return lookup;
            }
        }

        return null;
    }

    /**
     * NBT ops that can resolve registry entries. Hand these to every vanilla codec BBS calls
     * instead of {@link NbtOps#INSTANCE} — a codec that doesn't need registries is unaffected,
     * since {@link RegistryOps} forwards everything else straight through. That also makes the
     * change backwards compatible: data written the old way still reads back.
     */
    public static DynamicOps<NbtElement> nbtOps()
    {
        RegistryWrapper.WrapperLookup lookup = lookup();

        return lookup == null ? NbtOps.INSTANCE : RegistryOps.of(NbtOps.INSTANCE, lookup);
    }
}
