package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ChargedProjectiles {
    public static final ChargedProjectiles EMPTY = new ChargedProjectiles(List.of());
    public static final Codec<ChargedProjectiles> CODEC = ItemStack.CODEC
        .listOf()
        .xmap(ChargedProjectiles::new, chargedProjectiles -> chargedProjectiles.items);
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargedProjectiles> STREAM_CODEC = ItemStack.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(ChargedProjectiles::new, chargedProjectiles -> chargedProjectiles.items);
    private final List<ItemStack> items;

    private ChargedProjectiles(List<ItemStack> projectiles) {
        this.items = projectiles;
    }

    public static ChargedProjectiles of(ItemStack projectile) {
        return new ChargedProjectiles(List.of(projectile.copy()));
    }

    public static ChargedProjectiles of(List<ItemStack> projectiles) {
        return new ChargedProjectiles(List.copyOf(Lists.transform(projectiles, ItemStack::copy)));
    }

    public boolean contains(Item item) {
        for (ItemStack itemStack : this.items) {
            if (itemStack.is(item)) {
                return true;
            }
        }

        return false;
    }

    public List<ItemStack> getItems() {
        return Lists.transform(this.items, ItemStack::copy);
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof ChargedProjectiles chargedProjectiles && ItemStack.listMatches(this.items, chargedProjectiles.items)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return ItemStack.hashStackList(this.items);
    }

    @Override
    public String toString() {
        return "ChargedProjectiles[items=" + this.items + "]";
    }
}
