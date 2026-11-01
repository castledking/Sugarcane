package io.papermc.paper.sugarcane;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Sugarcane replaces the map walks in {@link Brain#tick} with arrays built from those maps. This drives two identical
 * brains, one through {@link Brain#tick} and one through a copy of the vanilla loops, and checks that every behaviour
 * is offered a start, ticked and stopped in the same order, including when a behaviour switches the brain to another
 * activity part way through the start pass.
 */
@Normal
public class BrainBehaviourOrderTest {

    private static final Activity[] ACTIVITIES = {Activity.CORE, Activity.IDLE, Activity.WORK, Activity.PANIC, Activity.REST, Activity.MEET};

    private static final class Recorder implements BehaviorControl<LivingEntity> {
        private final String name;
        private final List<String> log;
        private final long seed;
        private final Brain<LivingEntity>[] brain;
        private final Activity switchTo;
        private Behavior.Status status = Behavior.Status.STOPPED;
        private int remaining;

        private Recorder(final String name, final List<String> log, final long seed, final Brain<LivingEntity>[] brain, final Activity switchTo) {
            this.name = name;
            this.log = log;
            this.seed = seed;
            this.brain = brain;
            this.switchTo = switchTo;
        }

        private int roll(final long timestamp, final int salt) {
            long hash = this.seed * 0x9E3779B97F4A7C15L + timestamp * 0xC2B2AE3D27D4EB4FL + salt;
            hash ^= hash >>> 31;
            hash *= 0xBF58476D1CE4E5B9L;
            hash ^= hash >>> 29;
            return (int) Math.floorMod(hash, 100L);
        }

        @Override
        public Behavior.Status getStatus() {
            return this.status;
        }

        @Override
        public Set<MemoryModuleType<?>> getRequiredMemories() {
            return Set.of();
        }

        @Override
        public boolean tryStart(final ServerLevel level, final LivingEntity body, final long timestamp) {
            this.log.add("try " + this.name);
            if (this.roll(timestamp, 1) >= 35) {
                return false;
            }
            this.status = Behavior.Status.RUNNING;
            this.remaining = 1 + this.roll(timestamp, 2) % 4;
            if (this.switchTo != null) {
                this.log.add("switch " + this.name + " -> " + this.switchTo.getName());
                this.brain[0].setActiveActivityIfPossible(this.switchTo);
            }
            return true;
        }

        @Override
        public void tickOrStop(final ServerLevel level, final LivingEntity body, final long timestamp) {
            this.log.add("tick " + this.name);
            if (--this.remaining <= 0) {
                this.doStop(level, body, timestamp);
            }
        }

        @Override
        public void doStop(final ServerLevel level, final LivingEntity body, final long timestamp) {
            this.log.add("stop " + this.name);
            this.status = Behavior.Status.STOPPED;
        }

        @Override
        public String debugString() {
            return this.name;
        }
    }

    @SuppressWarnings("unchecked")
    private static Brain<LivingEntity> build(final long layoutSeed, final List<String> log) {
        final Brain<LivingEntity>[] holder = new Brain[1];
        final Brain<LivingEntity> brain = new Brain<>();
        holder[0] = brain;
        final SplittableRandom random = new SplittableRandom(layoutSeed);
        int id = 0;
        for (final Activity activity : ACTIVITIES) {
            final ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super LivingEntity>>> behaviours = ImmutableList.builder();
            final int count = 2 + random.nextInt(9);
            for (int i = 0; i < count; ++i) {
                final int priority = random.nextInt(12);
                // a few behaviours switch the brain to another activity when they start
                final Activity switchTo = random.nextInt(8) == 0 ? ACTIVITIES[1 + random.nextInt(ACTIVITIES.length - 1)] : null;
                behaviours.add(Pair.of(priority, new Recorder(activity.getName() + "#" + id, log, layoutSeed * 31 + id, holder, switchTo)));
                ++id;
            }
            brain.addActivity(activity, behaviours.build(), ImmutableSet.of(), ImmutableSet.of());
        }
        return brain;
    }

    private static Object field(final Brain<?> brain, final String name) throws ReflectiveOperationException {
        final Field field = Brain.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(brain);
    }

    // Brain#startEachNonRunningBehavior and Brain#tickEachRunningBehavior as vanilla wrote them
    @SuppressWarnings("unchecked")
    private static void vanillaTick(final Brain<LivingEntity> brain, final ServerLevel level, final LivingEntity body, final long time) throws ReflectiveOperationException {
        final Map<Integer, Map<Activity, Set<BehaviorControl<? super LivingEntity>>>> byPriority =
            (Map<Integer, Map<Activity, Set<BehaviorControl<? super LivingEntity>>>>) field(brain, "availableBehaviorsByPriority");
        final Set<Activity> active = (Set<Activity>) field(brain, "activeActivities");
        for (final Map<Activity, Set<BehaviorControl<? super LivingEntity>>> byActivity : byPriority.values()) {
            for (final Map.Entry<Activity, Set<BehaviorControl<? super LivingEntity>>> entry : byActivity.entrySet()) {
                if (active.contains(entry.getKey())) {
                    for (final BehaviorControl<? super LivingEntity> behaviour : entry.getValue()) {
                        if (behaviour.getStatus() == Behavior.Status.STOPPED) {
                            behaviour.tryStart(level, body, time);
                        }
                    }
                }
            }
        }
        for (final BehaviorControl<? super LivingEntity> behaviour : vanillaRunning(brain)) {
            behaviour.tickOrStop(level, body, time);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BehaviorControl<? super LivingEntity>> vanillaRunning(final Brain<LivingEntity> brain) throws ReflectiveOperationException {
        final Map<Integer, Map<Activity, Set<BehaviorControl<? super LivingEntity>>>> byPriority =
            (Map<Integer, Map<Activity, Set<BehaviorControl<? super LivingEntity>>>>) field(brain, "availableBehaviorsByPriority");
        final List<BehaviorControl<? super LivingEntity>> running = new ArrayList<>();
        for (final Map<Activity, Set<BehaviorControl<? super LivingEntity>>> byActivity : byPriority.values()) {
            for (final Set<BehaviorControl<? super LivingEntity>> behaviours : byActivity.values()) {
                for (final BehaviorControl<? super LivingEntity> behaviour : behaviours) {
                    if (behaviour.getStatus() == Behavior.Status.RUNNING) {
                        running.add(behaviour);
                    }
                }
            }
        }
        return running;
    }

    private static List<String> names(final List<? extends BehaviorControl<?>> behaviours) {
        final List<String> names = new ArrayList<>();
        for (final BehaviorControl<?> behaviour : behaviours) {
            names.add(behaviour.debugString());
        }
        return names;
    }

    @Test
    public void sameOrderAsVanilla() throws ReflectiveOperationException {
        final long[] time = new long[1];
        final ServerLevel level = Mockito.mock(ServerLevel.class);
        Mockito.when(level.getGameTime()).thenAnswer(invocation -> time[0]);
        final LivingEntity body = Mockito.mock(LivingEntity.class);
        Mockito.when(body.level()).thenReturn(level);

        for (long layout = 1; layout <= 60; ++layout) {
            final List<String> optimisedLog = new ArrayList<>();
            final List<String> vanillaLog = new ArrayList<>();
            final Brain<LivingEntity> optimised = build(layout, optimisedLog);
            final Brain<LivingEntity> vanilla = build(layout, vanillaLog);

            for (time[0] = 0; time[0] < 200; ++time[0]) {
                if (time[0] % 37 == 0) {
                    // move both brains between activities from outside the tick, like a schedule update would
                    final Activity activity = ACTIVITIES[1 + (int) Math.floorMod(time[0] * 7 + layout, ACTIVITIES.length - 1)];
                    optimised.setActiveActivityIfPossible(activity);
                    vanilla.setActiveActivityIfPossible(activity);
                }
                optimised.tick(level, body);
                vanillaTick(vanilla, level, body, time[0]);
                Assertions.assertEquals(names(vanillaRunning(vanilla)), names(optimised.getRunningBehaviors()), "running behaviours, layout " + layout + " tick " + time[0]);
                Assertions.assertEquals(vanillaLog, optimisedLog, "layout " + layout + " tick " + time[0]);
            }

            if (layout % 10 == 0) {
                optimised.stopAll(level, body);
                final long now = time[0];
                for (final BehaviorControl<? super LivingEntity> behaviour : vanillaRunning(vanilla)) {
                    behaviour.doStop(level, body, now);
                }
                Assertions.assertEquals(vanillaLog, optimisedLog, "stopAll, layout " + layout);
            }
        }
    }
}
