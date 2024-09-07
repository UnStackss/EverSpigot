package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetCustomModelDataFunction extends LootItemConditionalFunction {
    static final MapCodec<SetCustomModelDataFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(NumberProviders.CODEC.fieldOf("value").forGetter(lootFunction -> lootFunction.valueProvider))
                .apply(instance, SetCustomModelDataFunction::new)
    );
    private final NumberProvider valueProvider;

    private SetCustomModelDataFunction(List<LootItemCondition> conditions, NumberProvider value) {
        super(conditions);
        this.valueProvider = value;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.valueProvider.getReferencedContextParams();
    }

    @Override
    public LootItemFunctionType<SetCustomModelDataFunction> getType() {
        return LootItemFunctions.SET_CUSTOM_MODEL_DATA;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(this.valueProvider.getInt(context)));
        return stack;
    }
}
