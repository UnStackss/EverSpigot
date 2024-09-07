package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

public class TradeTrigger extends SimpleCriterionTrigger<TradeTrigger.TriggerInstance> {
    @Override
    public Codec<TradeTrigger.TriggerInstance> codec() {
        return TradeTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, AbstractVillager merchant, ItemStack stack) {
        LootContext lootContext = EntityPredicate.createContext(player, merchant);
        this.trigger(player, conditions -> conditions.matches(lootContext, stack));
    }

    public static record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> villager, Optional<ItemPredicate> item
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TradeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TradeTrigger.TriggerInstance::player),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("villager").forGetter(TradeTrigger.TriggerInstance::villager),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TradeTrigger.TriggerInstance::item)
                    )
                    .apply(instance, TradeTrigger.TriggerInstance::new)
        );

        public static Criterion<TradeTrigger.TriggerInstance> tradedWithVillager() {
            return CriteriaTriggers.TRADE.createCriterion(new TradeTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<TradeTrigger.TriggerInstance> tradedWithVillager(EntityPredicate.Builder playerPredicate) {
            return CriteriaTriggers.TRADE
                .createCriterion(new TradeTrigger.TriggerInstance(Optional.of(EntityPredicate.wrap(playerPredicate)), Optional.empty(), Optional.empty()));
        }

        public boolean matches(LootContext villager, ItemStack stack) {
            return (!this.villager.isPresent() || this.villager.get().matches(villager)) && (!this.item.isPresent() || this.item.get().test(stack));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.villager, ".villager");
        }
    }
}
