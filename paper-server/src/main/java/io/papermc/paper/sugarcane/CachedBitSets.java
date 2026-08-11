package io.papermc.paper.sugarcane;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.BitSet;
import java.util.function.IntFunction;

/**
 * Thread-local, size-keyed {@link BitSet} scratch buffers.
 * <p>
 * Ported from Leaf's C2ME allocation-reduction patch. Ore placement allocates a fresh {@code BitSet} for
 * every vein it tries to place, which is a meaningful share of the garbage produced while generating fresh
 * terrain. Worldgen threads only ever use one of these at a time and always overwrite it before reading, so
 * a per-thread cache keyed on the requested size is enough. Ore vein sizes come from a small fixed set of
 * configured feature values, so the per-thread map stays tiny.
 */
public final class CachedBitSets {

    private static final IntFunction<BitSet> CONSTRUCTOR = BitSet::new;
    private static final ThreadLocal<Int2ObjectOpenHashMap<BitSet>> CACHE = ThreadLocal.withInitial(Int2ObjectOpenHashMap::new);

    private CachedBitSets() {
    }

    /**
     * Returns a cleared {@link BitSet} with at least {@code bits} bits, reusing this thread's previous
     * instance for the same size where possible. The returned set must not outlive the current operation.
     */
    public static BitSet getCachedOrNew(final int bits) {
        final BitSet bitSet = CACHE.get().computeIfAbsent(bits, CONSTRUCTOR);
        bitSet.clear();
        return bitSet;
    }
}
