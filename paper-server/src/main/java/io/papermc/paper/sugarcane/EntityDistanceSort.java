package io.papermc.paper.sugarcane;

import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.List;
import net.minecraft.world.entity.Entity;

/**
 * Sorts entities by squared distance from another entity, with exactly the result of
 * {@code entities.sort(Comparator.comparingDouble(from::distanceToSqr))}.
 * <p>
 * The comparator form works out both distances again for every comparison. A villager's nearest-entity sensor in
 * a trading hall sorts a hundred or more neighbours, which comes to well over a thousand distance calculations per
 * run, for every villager. This works each distance out once and sorts the precomputed keys instead.
 * <p>
 * Both sorts are stable and compare the same keys with {@link Double#compare}, so entities at exactly the same
 * distance keep the order they were collected in and the output order is identical.
 */
public final class EntityDistanceSort {

    private EntityDistanceSort() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> void sortByDistanceSqr(final List<T> entities, final Entity from) {
        final int size = entities.size();
        if (size < 2) {
            return;
        }

        final Object[] values = entities.toArray();
        final double[] keys = new double[size];
        final int[] order = new int[size];
        for (int i = 0; i < size; ++i) {
            keys[i] = from.distanceToSqr((Entity) values[i]);
            order[i] = i;
        }

        IntArrays.mergeSort(order, (a, b) -> Double.compare(keys[a], keys[b])); // stable

        for (int i = 0; i < size; ++i) {
            entities.set(i, (T) values[order[i]]);
        }
    }
}
