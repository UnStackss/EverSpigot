package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LimitCount extends LootItemConditionalFunction {
    public static final MapCodec<LimitCount> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance).and(IntRange.CODEC.fieldOf("limit").forGetter(function -> function.limiter)).apply(instance, LimitCount::new)
    );
    private final IntRange limiter;

    private LimitCount(List<LootItemCondition> conditions, IntRange limit) {
        super(conditions);
        this.limiter = limit;
    }

    @Override
    public LootItemFunctionType<LimitCount> getType() {
        return LootItemFunctions.LIMIT_COUNT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.limiter.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = this.limiter.clamp(context, stack.getCount());
        stack.setCount(i);
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> limitCount(IntRange limit) {
        return simpleBuilder(conditions -> new LimitCount(conditions, limit));
    }
}
