package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public class SequenceFunction implements LootItemFunction {
    public static final MapCodec<SequenceFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LootItemFunctions.TYPED_CODEC.listOf().fieldOf("functions").forGetter(function -> function.functions))
                .apply(instance, SequenceFunction::new)
    );
    public static final Codec<SequenceFunction> INLINE_CODEC = LootItemFunctions.TYPED_CODEC
        .listOf()
        .xmap(SequenceFunction::new, function -> function.functions);
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

    private SequenceFunction(List<LootItemFunction> terms) {
        this.functions = terms;
        this.compositeFunction = LootItemFunctions.compose(terms);
    }

    public static SequenceFunction of(List<LootItemFunction> terms) {
        return new SequenceFunction(List.copyOf(terms));
    }

    @Override
    public ItemStack apply(ItemStack itemStack, LootContext lootContext) {
        return this.compositeFunction.apply(itemStack, lootContext);
    }

    @Override
    public void validate(ValidationContext reporter) {
        LootItemFunction.super.validate(reporter);

        for (int i = 0; i < this.functions.size(); i++) {
            this.functions.get(i).validate(reporter.forChild(".function[" + i + "]"));
        }
    }

    @Override
    public LootItemFunctionType<SequenceFunction> getType() {
        return LootItemFunctions.SEQUENCE;
    }
}
