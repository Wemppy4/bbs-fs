package mchorse.bbs_mod.data;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The game's dynamic registries, for the vanilla codecs BBS serializes data with.
 *
 * <p>Since 1.20.5 an {@link net.minecraft.item.ItemStack} carries components, and some of them
 * point at entries of DATA-DRIVEN registries — enchantments above all. Their codec is a
 * {@code RegistryFixedCodec}, and it refuses the write twice over. First it demands a
 * {@link RegistryOps}: plain {@link NbtOps#INSTANCE} gets "Can't access registry". Then it
 * demands that the ops carry the registry set the entry ACTUALLY BELONGS TO — the check is
 * {@code RegistryEntry.ownerEquals}, object identity on the registry — and anything else gets
 * "Element ... is not valid in current registry set". BBS turned either error into an empty map,
 * so an enchanted item was silently written down as air. Everything without a data-driven
 * component encodes under any ops at all, which is why only enchanted items ever went missing.
 *
 * <p>The second demand is why this is not one global lookup. A client and its integrated server
 * each build their own copy of the dynamic registries, so a stack held by the client player is
 * owned by the CLIENT's set and a stack held by the server by the SERVER's, and handing one to
 * the other fails exactly as if there were no registries at all. Each side therefore registers a
 * {@link Source} that knows both its registries and its own thread, and a caller gets the set
 * belonging to the thread it is serializing on. With no game running — nothing is loaded and
 * nothing is being saved — it falls back to plain NbtOps, exactly what the code did before.
 */
public class GameRegistries
{
    /** One side's registries, and the thread whose data they own. */
    public interface Source
    {
        /** This side's registries, or {@code null} while this side has no game. */
        RegistryWrapper.WrapperLookup lookup();

        /** Whether the calling thread is this side's own. */
        boolean isOwnThread();
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    /* RegistryOps carries a per-instance cache of resolved registries, so it is worth keeping one
     * per side rather than building a fresh one for every keyframe of a film save. Weak keys: a
     * registry set dies with its world, and holding it here would keep every world ever loaded. */
    private static final Map<RegistryWrapper.WrapperLookup, DynamicOps<NbtElement>> OPS =
        Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Register a side's registries. Called once per side at mod init; the source is expected to
     * report {@code null} while that side has no game.
     */
    public static void addSource(Source source)
    {
        if (source != null)
        {
            SOURCES.add(source);
        }
    }

    /**
     * Register a side's registries ahead of every other, for the side that owns most of what BBS
     * serializes. Only decides the exotic case — a thread neither side claims; when the calling
     * thread belongs to a side, that side answers regardless of order. The client uses this: a
     * physical client's mod init runs after the common one, and almost everything BBS writes
     * there (the film editor, a take, the item picker) is client-owned.
     */
    public static void addPreferredSource(Source source)
    {
        if (source != null)
        {
            SOURCES.add(0, source);
        }
    }

    /**
     * The registries owned by the thread this is called on, or the first side that has any when
     * no side claims the thread, or {@code null} when no side has a game.
     */
    public static RegistryWrapper.WrapperLookup lookup()
    {
        RegistryWrapper.WrapperLookup fallback = null;

        for (Source source : SOURCES)
        {
            RegistryWrapper.WrapperLookup lookup = source.lookup();

            if (lookup == null)
            {
                continue;
            }

            if (source.isOwnThread())
            {
                return lookup;
            }

            if (fallback == null)
            {
                fallback = lookup;
            }
        }

        return fallback;
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

        if (lookup == null)
        {
            return NbtOps.INSTANCE;
        }

        return OPS.computeIfAbsent(lookup, (key) -> RegistryOps.of(NbtOps.INSTANCE, key));
    }
}
