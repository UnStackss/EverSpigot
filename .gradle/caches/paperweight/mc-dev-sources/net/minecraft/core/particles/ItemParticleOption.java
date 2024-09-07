package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class ItemParticleOption implements ParticleOptions {
    private static final Codec<ItemStack> ITEM_CODEC = Codec.withAlternative(ItemStack.SINGLE_ITEM_CODEC, ItemStack.ITEM_NON_AIR_CODEC, ItemStack::new);
    private final ParticleType<ItemParticleOption> type;
    private final ItemStack itemStack;

    public static MapCodec<ItemParticleOption> codec(ParticleType<ItemParticleOption> type) {
        return ITEM_CODEC.xmap(stack -> new ItemParticleOption(type, stack), effect -> effect.itemStack).fieldOf("item");
    }

    public static StreamCodec<? super RegistryFriendlyByteBuf, ItemParticleOption> streamCodec(ParticleType<ItemParticleOption> type) {
        return ItemStack.STREAM_CODEC.map(stack -> new ItemParticleOption(type, stack), effect -> effect.itemStack);
    }

    public ItemParticleOption(ParticleType<ItemParticleOption> type, ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Empty stacks are not allowed");
        } else {
            this.type = type;
            this.itemStack = stack;
        }
    }

    @Override
    public ParticleType<ItemParticleOption> getType() {
        return this.type;
    }

    public ItemStack getItem() {
        return this.itemStack;
    }
}
