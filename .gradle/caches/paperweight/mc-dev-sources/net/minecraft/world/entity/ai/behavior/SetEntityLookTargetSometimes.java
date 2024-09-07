package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

@Deprecated
public class SetEntityLookTargetSometimes {
    public static BehaviorControl<LivingEntity> create(float maxDistance, UniformInt interval) {
        return create(maxDistance, interval, entity -> true);
    }

    public static BehaviorControl<LivingEntity> create(EntityType<?> type, float maxDistance, UniformInt interval) {
        return create(maxDistance, interval, entity -> type.equals(entity.getType()));
    }

    private static BehaviorControl<LivingEntity> create(float maxDistance, UniformInt interval, Predicate<LivingEntity> predicate) {
        float f = maxDistance * maxDistance;
        SetEntityLookTargetSometimes.Ticker ticker = new SetEntityLookTargetSometimes.Ticker(interval);
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.LOOK_TARGET), context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES))
                    .apply(
                        context,
                        (lookTarget, visibleMobs) -> (world, entity, time) -> {
                                Optional<LivingEntity> optional = context.<NearestVisibleLivingEntities>get(visibleMobs)
                                    .findClosest(predicate.and(other -> other.distanceToSqr(entity) <= (double)f));
                                if (optional.isEmpty()) {
                                    return false;
                                } else if (!ticker.tickDownAndCheck(world.random)) {
                                    return false;
                                } else {
                                    lookTarget.set(new EntityTracker(optional.get(), true));
                                    return true;
                                }
                            }
                    )
        );
    }

    public static final class Ticker {
        private final UniformInt interval;
        private int ticksUntilNextStart;

        public Ticker(UniformInt interval) {
            if (interval.getMinValue() <= 1) {
                throw new IllegalArgumentException();
            } else {
                this.interval = interval;
            }
        }

        public boolean tickDownAndCheck(RandomSource random) {
            if (this.ticksUntilNextStart == 0) {
                this.ticksUntilNextStart = this.interval.sample(random) - 1;
                return false;
            } else {
                return --this.ticksUntilNextStart == 0;
            }
        }
    }
}
