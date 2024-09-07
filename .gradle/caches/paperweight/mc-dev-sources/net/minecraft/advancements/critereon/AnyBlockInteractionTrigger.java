package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class AnyBlockInteractionTrigger extends SimpleCriterionTrigger<AnyBlockInteractionTrigger.TriggerInstance> {
    @Override
    public Codec<AnyBlockInteractionTrigger.TriggerInstance> codec() {
        return AnyBlockInteractionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockPos pos, ItemStack stack) {
        ServerLevel serverLevel = player.serverLevel();
        BlockState blockState = serverLevel.getBlockState(pos);
        LootParams lootParams = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, pos.getCenter())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .withParameter(LootContextParams.BLOCK_STATE, blockState)
            .withParameter(LootContextParams.TOOL, stack)
            .create(LootContextParamSets.ADVANCEMENT_LOCATION);
        LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
        this.trigger(player, conditions -> conditions.matches(lootContext));
    }

    public static record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> location)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<AnyBlockInteractionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(AnyBlockInteractionTrigger.TriggerInstance::player),
                        ContextAwarePredicate.CODEC.optionalFieldOf("location").forGetter(AnyBlockInteractionTrigger.TriggerInstance::location)
                    )
                    .apply(instance, AnyBlockInteractionTrigger.TriggerInstance::new)
        );

        public boolean matches(LootContext location) {
            return this.location.isEmpty() || this.location.get().matches(location);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            this.location.ifPresent(location -> validator.validate(location, LootContextParamSets.ADVANCEMENT_LOCATION, ".location"));
        }
    }
}
