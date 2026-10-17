package io.papermc.paper.sugarcane;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.SplittableRandom;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that {@link PoiSearchQueue} visits sections in exactly the order of the hash set search it replaced in
 * {@code PoiAccess}, including when the search prunes sections part way through.
 */
@Normal
public class PoiSearchQueueTest {

    // a pruned section is not expanded. Real searches prune on distance against a bound that tightens as POIs are
    // found, so make the decision depend on how far the search has got as well as on the section itself.
    private static boolean prune(final long key, final int visited, final long seed, final int pruneChance) {
        long hash = key * 0x9E3779B97F4A7C15L + visited * 0xC2B2AE3D27D4EB4FL + seed;
        hash ^= hash >>> 31;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 29;
        return Math.floorMod(hash, 100) < pruneChance;
    }

    // the loop from PoiAccess before Sugarcane replaced it
    private static LongList searchWithHashSet(final int lowerX, final int lowerY, final int lowerZ,
                                              final int upperX, final int upperY, final int upperZ,
                                              final int centerX, final int centerY, final int centerZ,
                                              final long seed, final int pruneChance) {
        final LongList visited = new LongArrayList();
        final long centerKey = CoordinateUtils.getChunkSectionKey(centerX, centerY, centerZ);
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(centerKey);
        queue.enqueue(centerKey);

        while (!queue.isEmpty()) {
            final long key = queue.dequeueLong();
            final int sectionX = CoordinateUtils.getChunkSectionX(key);
            final int sectionY = CoordinateUtils.getChunkSectionY(key);
            final int sectionZ = CoordinateUtils.getChunkSectionZ(key);

            if (sectionX < lowerX || sectionX > upperX || sectionY < lowerY || sectionY > upperY || sectionZ < lowerZ || sectionZ > upperZ) {
                continue;
            }
            if (prune(key, visited.size(), seed, pruneChance)) {
                continue;
            }
            visited.add(key);

            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        if ((dx & 1) + (dy & 1) + (dz & 1) != 1) {
                            continue;
                        }
                        final long neighbourKey = CoordinateUtils.getChunkSectionKey(sectionX + dx, sectionY + dy, sectionZ + dz);
                        if (seen.add(neighbourKey)) {
                            queue.enqueue(neighbourKey);
                        }
                    }
                }
            }
        }

        return visited;
    }

    private static LongList searchWithQueue(final int lowerX, final int lowerY, final int lowerZ,
                                            final int upperX, final int upperY, final int upperZ,
                                            final int centerX, final int centerY, final int centerZ,
                                            final long seed, final int pruneChance) {
        final LongList visited = new LongArrayList();
        final PoiSearchQueue queue = new PoiSearchQueue(lowerX, lowerY, lowerZ, upperX, upperY, upperZ);
        queue.enqueue(centerX, centerY, centerZ);

        while (!queue.isEmpty()) {
            final long key = queue.dequeue();
            final int sectionX = CoordinateUtils.getChunkSectionX(key);
            final int sectionY = CoordinateUtils.getChunkSectionY(key);
            final int sectionZ = CoordinateUtils.getChunkSectionZ(key);

            if (prune(key, visited.size(), seed, pruneChance)) {
                continue;
            }
            visited.add(key);

            queue.enqueueNeighbours(sectionX, sectionY, sectionZ);
        }

        return visited;
    }

    private static void assertSameOrder(final int lowerX, final int lowerY, final int lowerZ,
                                        final int upperX, final int upperY, final int upperZ,
                                        final int centerX, final int centerY, final int centerZ,
                                        final long seed, final int pruneChance) {
        final LongList expected = searchWithHashSet(lowerX, lowerY, lowerZ, upperX, upperY, upperZ, centerX, centerY, centerZ, seed, pruneChance);
        final LongList actual = searchWithQueue(lowerX, lowerY, lowerZ, upperX, upperY, upperZ, centerX, centerY, centerZ, seed, pruneChance);
        Assertions.assertEquals(expected, actual, () -> "box " + lowerX + "," + lowerY + "," + lowerZ + " to " + upperX + "," + upperY + "," + upperZ
            + " centre " + centerX + "," + centerY + "," + centerZ + " seed " + seed + " prune " + pruneChance);
    }

    @Test
    public void villagerSearchVisitsEverySectionInOrder() {
        // 48 block search around x=100, z=-37 in a -64..319 world: 7x7 columns, 24 sections tall
        final int range = 48;
        final int x = 100;
        final int z = -37;
        assertSameOrder((x - range) >> 4, -4, (z - range) >> 4, (x + range) >> 4, 19, (z + range) >> 4, x >> 4, 4, z >> 4, 0L, 0);
        final LongList visited = searchWithQueue((x - range) >> 4, -4, (z - range) >> 4, (x + range) >> 4, 19, (z + range) >> 4, x >> 4, 4, z >> 4, 0L, 0);
        Assertions.assertEquals(7 * 24 * 7, visited.size());
    }

    @Test
    public void randomBoxesAndPruningMatch() {
        final SplittableRandom random = new SplittableRandom(0x5A6A2CA9EL);
        for (int i = 0; i < 2_000; ++i) {
            final int lowerX = random.nextInt(-1_000_000, 1_000_000);
            final int lowerY = random.nextInt(-8, 4);
            final int lowerZ = random.nextInt(-1_000_000, 1_000_000);
            final int upperX = lowerX + random.nextInt(0, 12);
            final int upperY = lowerY + random.nextInt(0, 30);
            final int upperZ = lowerZ + random.nextInt(0, 12);
            final int centerX = random.nextInt(lowerX, upperX + 1);
            final int centerY = random.nextInt(lowerY, upperY + 1);
            final int centerZ = random.nextInt(lowerZ, upperZ + 1);
            assertSameOrder(lowerX, lowerY, lowerZ, upperX, upperY, upperZ, centerX, centerY, centerZ, random.nextLong(), random.nextInt(0, 60));
        }
    }

    @Test
    public void emptyBoxVisitsNothing() {
        // a negative search range makes the lower bound pass the upper bound
        Assertions.assertTrue(searchWithQueue(5, 0, 5, 4, 10, 4, 4, 5, 4, 0L, 0).isEmpty());
        Assertions.assertTrue(searchWithHashSet(5, 0, 5, 4, 10, 4, 4, 5, 4, 0L, 0).isEmpty());
    }
}
