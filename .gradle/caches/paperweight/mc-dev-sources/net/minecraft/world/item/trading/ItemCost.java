package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemCost(Holder<Item> item, int count, DataComponentPredicate components, ItemStack itemStack) {
    public static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemCost::item),
                    ExtraCodecs.POSITIVE_INT.fieldOf("count").orElse(1).forGetter(ItemCost::count),
                    DataComponentPredicate.CODEC.optionalFieldOf("components", DataComponentPredicate.EMPTY).forGetter(ItemCost::components)
                )
                .apply(instance, ItemCost::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemCost> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.holderRegistry(Registries.ITEM),
        ItemCost::item,
        ByteBufCodecs.VAR_INT,
        ItemCost::count,
        DataComponentPredicate.STREAM_CODEC,
        ItemCost::components,
        ItemCost::new
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemCost>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);

    public ItemCost(ItemLike item) {
        this(item, 1);
    }

    public ItemCost(ItemLike item, int count) {
        this(item.asItem().builtInRegistryHolder(), count, DataComponentPredicate.EMPTY);
    }

    public ItemCost(Holder<Item> item, int count, DataComponentPredicate components) {
        this(item, count, components, createStack(item, count, components));
    }

    public ItemCost withComponents(UnaryOperator<DataComponentPredicate.Builder> builderCallback) {
        return new ItemCost(this.item, this.count, builderCallback.apply(DataComponentPredicate.builder()).build());
    }

    private static ItemStack createStack(Holder<Item> item, int count, DataComponentPredicate components) {
        return new ItemStack(item, count, components.asPatch());
    }

    public boolean test(ItemStack stack) {
        return stack.is(this.item) && this.components.test((DataComponentHolder)stack);
    }
}
