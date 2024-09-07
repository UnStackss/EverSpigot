package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetItemCountFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetItemCountFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        NumberProviders.CODEC.fieldOf("count").forGetter(function -> function.value),
                        Codec.BOOL.fieldOf("add").orElse(false).forGetter(function -> function.add)
                    )
                )
                .apply(instance, SetItemCountFunction::new)
    );
    private final NumberProvider value;
    private final boolean add;

    private SetItemCountFunction(List<LootItemCondition> conditions, NumberProvider countRange, boolean add) {
        super(conditions);
        this.value = countRange;
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetItemCountFunction> getType() {
        return LootItemFunctions.SET_COUNT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.value.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = this.add ? stack.getCount() : 0;
        stack.setCount(i + this.value.getInt(context));
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setCount(NumberProvider countRange) {
        return simpleBuilder(list -> new SetItemCountFunction(list, countRange, false));
    }

    public static LootItemConditionalFunction.Builder<?> setCount(NumberProvider countRange, boolean add) {
        return simpleBuilder(list -> new SetItemCountFunction(list, countRange, add));
    }
}
