package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;

public class ItemUsedOnLocationTrigger extends SimpleCriterionTrigger<ItemUsedOnLocationTrigger.TriggerInstance> {
    @Override
    public Codec<ItemUsedOnLocationTrigger.TriggerInstance> codec() {
        return ItemUsedOnLocationTrigger.TriggerInstance.CODEC;
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
        public static final Codec<ItemUsedOnLocationTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ItemUsedOnLocationTrigger.TriggerInstance::player),
                        ContextAwarePredicate.CODEC.optionalFieldOf("location").forGetter(ItemUsedOnLocationTrigger.TriggerInstance::location)
                    )
                    .apply(instance, ItemUsedOnLocationTrigger.TriggerInstance::new)
        );

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlock(Block block) {
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).build()
            );
            return CriteriaTriggers.PLACED_BLOCK
                .createCriterion(new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate)));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlock(LootItemCondition.Builder... locationConditions) {
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                Arrays.stream(locationConditions).map(LootItemCondition.Builder::build).toArray(LootItemCondition[]::new)
            );
            return CriteriaTriggers.PLACED_BLOCK
                .createCriterion(new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate)));
        }

        private static ItemUsedOnLocationTrigger.TriggerInstance itemUsedOnLocation(LocationPredicate.Builder location, ItemPredicate.Builder item) {
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                LocationCheck.checkLocation(location).build(), MatchTool.toolMatches(item).build()
            );
            return new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> itemUsedOnBlock(LocationPredicate.Builder location, ItemPredicate.Builder item) {
            return CriteriaTriggers.ITEM_USED_ON_BLOCK.createCriterion(itemUsedOnLocation(location, item));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> allayDropItemOnBlock(LocationPredicate.Builder location, ItemPredicate.Builder item) {
            return CriteriaTriggers.ALLAY_DROP_ITEM_ON_BLOCK.createCriterion(itemUsedOnLocation(location, item));
        }

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
