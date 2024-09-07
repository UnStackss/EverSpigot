package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

public class PickedUpItemTrigger extends SimpleCriterionTrigger<PickedUpItemTrigger.TriggerInstance> {
    @Override
    public Codec<PickedUpItemTrigger.TriggerInstance> codec() {
        return PickedUpItemTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack stack, @Nullable Entity entity) {
        LootContext lootContext = EntityPredicate.createContext(player, entity);
        this.trigger(player, conditions -> conditions.matches(player, stack, lootContext));
    }

    public static record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item, Optional<ContextAwarePredicate> entity)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<PickedUpItemTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(PickedUpItemTrigger.TriggerInstance::player),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(PickedUpItemTrigger.TriggerInstance::item),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(PickedUpItemTrigger.TriggerInstance::entity)
                    )
                    .apply(instance, PickedUpItemTrigger.TriggerInstance::new)
        );

        public static Criterion<PickedUpItemTrigger.TriggerInstance> thrownItemPickedUpByEntity(
            ContextAwarePredicate player, Optional<ItemPredicate> item, Optional<ContextAwarePredicate> entity
        ) {
            return CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.createCriterion(new PickedUpItemTrigger.TriggerInstance(Optional.of(player), item, entity));
        }

        public static Criterion<PickedUpItemTrigger.TriggerInstance> thrownItemPickedUpByPlayer(
            Optional<ContextAwarePredicate> playerPredicate, Optional<ItemPredicate> item, Optional<ContextAwarePredicate> entity
        ) {
            return CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.createCriterion(new PickedUpItemTrigger.TriggerInstance(playerPredicate, item, entity));
        }

        public boolean matches(ServerPlayer player, ItemStack stack, LootContext entity) {
            return (!this.item.isPresent() || this.item.get().test(stack)) && (!this.entity.isPresent() || this.entity.get().matches(entity));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, ".entity");
        }
    }
}
