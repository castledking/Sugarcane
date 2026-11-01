package io.papermc.paper.sugarcane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.world.entity.Entity;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Checks that {@link EntityDistanceSort} puts entities in exactly the order vanilla's
 * {@code list.sort(Comparator.comparingDouble(body::distanceToSqr))} does, including entities at equal distances.
 */
@Normal
public class EntityDistanceSortTest {

    @Test
    public void sameOrderAsComparatorSort() {
        final List<Entity> pool = new ArrayList<>();
        for (int i = 0; i < 300; ++i) {
            pool.add(Mockito.mock(Entity.class, Mockito.withSettings().stubOnly()));
        }
        final Map<Entity, Double> distances = new IdentityHashMap<>();
        final Entity from = Mockito.mock(Entity.class, Mockito.withSettings().stubOnly());
        Mockito.when(from.distanceToSqr(ArgumentMatchers.any(Entity.class))).thenAnswer(invocation -> distances.get(invocation.<Entity>getArgument(0)));

        final SplittableRandom random = new SplittableRandom(0x5A6A2CA9EL);
        for (int trial = 0; trial < 400; ++trial) {
            final List<Entity> shuffled = new ArrayList<>(pool);
            for (int i = shuffled.size() - 1; i > 0; --i) {
                final int j = random.nextInt(i + 1);
                final Entity swap = shuffled.get(i);
                shuffled.set(i, shuffled.get(j));
                shuffled.set(j, swap);
            }
            final int size = random.nextInt(pool.size() + 1);
            final List<Entity> entities = new ArrayList<>(shuffled.subList(0, size));

            distances.clear();
            // grid layouts such as trading halls put many entities at exactly the same distance
            final int distinct = trial % 3 == 0 ? 1 + random.nextInt(6) : Integer.MAX_VALUE;
            for (final Entity entity : entities) {
                final double distance = distinct == Integer.MAX_VALUE ? random.nextDouble() * 2048.0 : random.nextInt(distinct) * 9.0;
                distances.put(entity, distance);
            }

            final List<Entity> expected = new ArrayList<>(entities);
            expected.sort(Comparator.comparingDouble(from::distanceToSqr));
            final List<Entity> actual = new ArrayList<>(entities);
            EntityDistanceSort.sortByDistanceSqr(actual, from);

            Assertions.assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); ++i) {
                Assertions.assertSame(expected.get(i), actual.get(i), "trial " + trial + " index " + i);
            }
        }
    }
}
