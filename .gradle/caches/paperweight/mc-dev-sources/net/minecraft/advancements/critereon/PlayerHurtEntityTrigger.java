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

public class PlayerHurtEntityTrigger extends SimpleCriterionTrigger<PlayerHurtEntityTrigger.TriggerInstance> {
    @Override
    public Codec<PlayerHurtEntityTrigger.TriggerInstance> codec() {
        return PlayerHurtEntityTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity entity, DamageSource damage, float dealt, float taken, boolean blocked) {
        LootContext lootContext = EntityPredicate.createContext(player, entity);
        this.trigger(player, conditions -> conditions.matches(player, lootContext, damage, dealt, taken, blocked));
    }

    public static record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<DamagePredicate> damage, Optional<ContextAwarePredicate> entity
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<PlayerHurtEntityTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(PlayerHurtEntityTrigger.TriggerInstance::player),
                        DamagePredicate.CODEC.optionalFieldOf("damage").forGetter(PlayerHurtEntityTrigger.TriggerInstance::damage),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(PlayerHurtEntityTrigger.TriggerInstance::entity)
                    )
                    .apply(instance, PlayerHurtEntityTrigger.TriggerInstance::new)
        );

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity() {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntityWithDamage(Optional<DamagePredicate> damage) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY.createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), damage, Optional.empty()));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntityWithDamage(DamagePredicate.Builder damage) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.of(damage.build()), Optional.empty()));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity(Optional<EntityPredicate> entity) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.empty(), EntityPredicate.wrap(entity)));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity(Optional<DamagePredicate> damage, Optional<EntityPredicate> entity) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), damage, EntityPredicate.wrap(entity)));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity(DamagePredicate.Builder damage, Optional<EntityPredicate> entity) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.of(damage.build()), EntityPredicate.wrap(entity)));
        }

        public boolean matches(ServerPlayer player, LootContext entity, DamageSource damageSource, float dealt, float taken, boolean blocked) {
            return (!this.damage.isPresent() || this.damage.get().matches(player, damageSource, dealt, taken, blocked))
                && (!this.entity.isPresent() || this.entity.get().matches(entity));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, ".entity");
        }
    }
}
