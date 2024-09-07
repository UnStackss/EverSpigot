package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;

public class FallAfterExplosionTrigger extends SimpleCriterionTrigger<FallAfterExplosionTrigger.TriggerInstance> {
    @Override
    public Codec<FallAfterExplosionTrigger.TriggerInstance> codec() {
        return FallAfterExplosionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Vec3 startPosition, @Nullable Entity cause) {
        Vec3 vec3 = player.position();
        LootContext lootContext = cause != null ? EntityPredicate.createContext(player, cause) : null;
        this.trigger(player, conditions -> conditions.matches(player.serverLevel(), startPosition, vec3, lootContext));
    }

    public static record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player,
        Optional<LocationPredicate> startPosition,
        Optional<DistancePredicate> distance,
        Optional<ContextAwarePredicate> cause
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<FallAfterExplosionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FallAfterExplosionTrigger.TriggerInstance::player),
                        LocationPredicate.CODEC.optionalFieldOf("start_position").forGetter(FallAfterExplosionTrigger.TriggerInstance::startPosition),
                        DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(FallAfterExplosionTrigger.TriggerInstance::distance),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("cause").forGetter(FallAfterExplosionTrigger.TriggerInstance::cause)
                    )
                    .apply(instance, FallAfterExplosionTrigger.TriggerInstance::new)
        );

        public static Criterion<FallAfterExplosionTrigger.TriggerInstance> fallAfterExplosion(DistancePredicate distance, EntityPredicate.Builder cause) {
            return CriteriaTriggers.FALL_AFTER_EXPLOSION
                .createCriterion(
                    new FallAfterExplosionTrigger.TriggerInstance(
                        Optional.empty(), Optional.empty(), Optional.of(distance), Optional.of(EntityPredicate.wrap(cause))
                    )
                );
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.cause(), ".cause");
        }

        public boolean matches(ServerLevel world, Vec3 startPosition, Vec3 endPosition, @Nullable LootContext cause) {
            return (!this.startPosition.isPresent() || this.startPosition.get().matches(world, startPosition.x, startPosition.y, startPosition.z))
                && (
                    !this.distance.isPresent()
                        || this.distance.get().matches(startPosition.x, startPosition.y, startPosition.z, endPosition.x, endPosition.y, endPosition.z)
                )
                && (!this.cause.isPresent() || cause != null && this.cause.get().matches(cause));
        }
    }
}
