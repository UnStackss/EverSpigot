package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FillPlayerHead extends LootItemConditionalFunction {
    public static final MapCodec<FillPlayerHead> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(LootContext.EntityTarget.CODEC.fieldOf("entity").forGetter(function -> function.entityTarget))
                .apply(instance, FillPlayerHead::new)
    );
    private final LootContext.EntityTarget entityTarget;

    public FillPlayerHead(List<LootItemCondition> conditions, LootContext.EntityTarget entity) {
        super(conditions);
        this.entityTarget = entity;
    }

    @Override
    public LootItemFunctionType<FillPlayerHead> getType() {
        return LootItemFunctions.FILL_PLAYER_HEAD;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(this.entityTarget.getParam());
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.is(Items.PLAYER_HEAD) && context.getParamOrNull(this.entityTarget.getParam()) instanceof Player player) {
            stack.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> fillPlayerHead(LootContext.EntityTarget target) {
        return simpleBuilder(conditions -> new FillPlayerHead(conditions, target));
    }
}
