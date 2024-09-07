package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetInstrumentFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetInstrumentFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(TagKey.hashedCodec(Registries.INSTRUMENT).fieldOf("options").forGetter(function -> function.options))
                .apply(instance, SetInstrumentFunction::new)
    );
    private final TagKey<Instrument> options;

    private SetInstrumentFunction(List<LootItemCondition> conditions, TagKey<Instrument> options) {
        super(conditions);
        this.options = options;
    }

    @Override
    public LootItemFunctionType<SetInstrumentFunction> getType() {
        return LootItemFunctions.SET_INSTRUMENT;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        InstrumentItem.setRandom(stack, this.options, context.getRandom());
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setInstrumentOptions(TagKey<Instrument> options) {
        return simpleBuilder(conditions -> new SetInstrumentFunction(conditions, options));
    }
}
