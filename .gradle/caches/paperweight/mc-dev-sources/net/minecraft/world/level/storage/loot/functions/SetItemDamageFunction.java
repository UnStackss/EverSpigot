package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.slf4j.Logger;

public class SetItemDamageFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetItemDamageFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        NumberProviders.CODEC.fieldOf("damage").forGetter(function -> function.damage),
                        Codec.BOOL.fieldOf("add").orElse(false).forGetter(function -> function.add)
                    )
                )
                .apply(instance, SetItemDamageFunction::new)
    );
    private final NumberProvider damage;
    private final boolean add;

    private SetItemDamageFunction(List<LootItemCondition> conditions, NumberProvider durabilityRange, boolean add) {
        super(conditions);
        this.damage = durabilityRange;
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetItemDamageFunction> getType() {
        return LootItemFunctions.SET_DAMAGE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.damage.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isDamageableItem()) {
            int i = stack.getMaxDamage();
            float f = this.add ? 1.0F - (float)stack.getDamageValue() / (float)i : 0.0F;
            float g = 1.0F - Mth.clamp(this.damage.getFloat(context) + f, 0.0F, 1.0F);
            stack.setDamageValue(Mth.floor(g * (float)i));
        } else {
            LOGGER.warn("Couldn't set damage of loot item {}", stack);
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider durabilityRange) {
        return simpleBuilder(conditions -> new SetItemDamageFunction(conditions, durabilityRange, false));
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider durabilityRange, boolean add) {
        return simpleBuilder(conditions -> new SetItemDamageFunction(conditions, durabilityRange, add));
    }
}
