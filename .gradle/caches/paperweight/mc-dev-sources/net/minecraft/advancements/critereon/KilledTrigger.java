package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;

public class KilledTrigger extends SimpleCriterionTrigger<KilledTrigger.TriggerInstance> {
    @Override
    public Codec<KilledTrigger.TriggerInstance> codec() {
        return KilledTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity entity, DamageSource killingDamage) {
        LootContext lootContext = EntityPredicate.createContext(player, entity);
        this.trigger(player, conditions -> conditions.matches(player, lootContext, killingDamage));
    }

    public static record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> entityPredicate, Optional<DamageSourcePredicate> killingBlow
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<KilledTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(KilledTrigger.TriggerInstance::player),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(KilledTrigger.TriggerInstance::entityPredicate),
                        DamageSourcePredicate.CODEC.optionalFieldOf("killing_blow").forGetter(KilledTrigger.TriggerInstance::killingBlow)
                    )
                    .apply(instance, KilledTrigger.TriggerInstance::new)
        );

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(Optional<EntityPredicate> entity) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entity), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(EntityPredicate.Builder killedEntityPredicateBuilder) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(killedEntityPredicateBuilder)), Optional.empty())
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity() {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(Optional<EntityPredicate> entity, Optional<DamageSourcePredicate> killingBlow) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entity), killingBlow));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            EntityPredicate.Builder killedEntityPredicateBuilder, Optional<DamageSourcePredicate> killingBlow
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(killedEntityPredicateBuilder)), killingBlow)
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            Optional<EntityPredicate> entity, DamageSourcePredicate.Builder damageSourcePredicateBuilder
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entity), Optional.of(damageSourcePredicateBuilder.build()))
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            EntityPredicate.Builder killedEntityPredicateBuilder, DamageSourcePredicate.Builder killingBlowBuilder
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(
                    new KilledTrigger.TriggerInstance(
                        Optional.empty(), Optional.of(EntityPredicate.wrap(killedEntityPredicateBuilder)), Optional.of(killingBlowBuilder.build())
                    )
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntityNearSculkCatalyst() {
            return CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(Optional<EntityPredicate> entity) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entity), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(EntityPredicate.Builder killerEntityPredicateBuilder) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(killerEntityPredicateBuilder)), Optional.empty())
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer() {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(Optional<EntityPredicate> entity, Optional<DamageSourcePredicate> killingBlow) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entity), killingBlow));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            EntityPredicate.Builder killerEntityPredicateBuilder, Optional<DamageSourcePredicate> killingBlow
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(killerEntityPredicateBuilder)), killingBlow)
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            Optional<EntityPredicate> entity, DamageSourcePredicate.Builder damageSourcePredicateBuilder
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entity), Optional.of(damageSourcePredicateBuilder.build()))
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            EntityPredicate.Builder killerEntityPredicateBuilder, DamageSourcePredicate.Builder damageSourcePredicateBuilder
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(
                    new KilledTrigger.TriggerInstance(
                        Optional.empty(), Optional.of(EntityPredicate.wrap(killerEntityPredicateBuilder)), Optional.of(damageSourcePredicateBuilder.build())
                    )
                );
        }

        public boolean matches(ServerPlayer player, LootContext entity, DamageSource killingBlow) {
            return (!this.killingBlow.isPresent() || this.killingBlow.get().matches(player, killingBlow))
                && (this.entityPredicate.isEmpty() || this.entityPredicate.get().matches(entity));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entityPredicate, ".entity");
        }
    }
}
