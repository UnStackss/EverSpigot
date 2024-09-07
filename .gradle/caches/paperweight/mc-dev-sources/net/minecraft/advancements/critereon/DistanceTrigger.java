package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class DistanceTrigger extends SimpleCriterionTrigger<DistanceTrigger.TriggerInstance> {
    @Override
    public Codec<DistanceTrigger.TriggerInstance> codec() {
        return DistanceTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Vec3 startPos) {
        Vec3 vec3 = player.position();
        this.trigger(player, conditions -> conditions.matches(player.serverLevel(), startPos, vec3));
    }

    public static record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<LocationPredicate> startPosition, Optional<DistancePredicate> distance
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<DistanceTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(DistanceTrigger.TriggerInstance::player),
                        LocationPredicate.CODEC.optionalFieldOf("start_position").forGetter(DistanceTrigger.TriggerInstance::startPosition),
                        DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(DistanceTrigger.TriggerInstance::distance)
                    )
                    .apply(instance, DistanceTrigger.TriggerInstance::new)
        );

        public static Criterion<DistanceTrigger.TriggerInstance> fallFromHeight(
            EntityPredicate.Builder entity, DistancePredicate distance, LocationPredicate.Builder startPos
        ) {
            return CriteriaTriggers.FALL_FROM_HEIGHT
                .createCriterion(
                    new DistanceTrigger.TriggerInstance(Optional.of(EntityPredicate.wrap(entity)), Optional.of(startPos.build()), Optional.of(distance))
                );
        }

        public static Criterion<DistanceTrigger.TriggerInstance> rideEntityInLava(EntityPredicate.Builder entity, DistancePredicate distance) {
            return CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER
                .createCriterion(new DistanceTrigger.TriggerInstance(Optional.of(EntityPredicate.wrap(entity)), Optional.empty(), Optional.of(distance)));
        }

        public static Criterion<DistanceTrigger.TriggerInstance> travelledThroughNether(DistancePredicate distance) {
            return CriteriaTriggers.NETHER_TRAVEL
                .createCriterion(new DistanceTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(distance)));
        }

        public boolean matches(ServerLevel world, Vec3 pos, Vec3 endPos) {
            return (!this.startPosition.isPresent() || this.startPosition.get().matches(world, pos.x, pos.y, pos.z))
                && (!this.distance.isPresent() || this.distance.get().matches(pos.x, pos.y, pos.z, endPos.x, endPos.y, endPos.z));
        }
    }
}
