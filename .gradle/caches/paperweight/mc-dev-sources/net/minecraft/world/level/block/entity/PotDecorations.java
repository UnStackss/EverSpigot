package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record PotDecorations(Optional<Item> back, Optional<Item> left, Optional<Item> right, Optional<Item> front) {
    public static final PotDecorations EMPTY = new PotDecorations(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    public static final Codec<PotDecorations> CODEC = BuiltInRegistries.ITEM
        .byNameCodec()
        .sizeLimitedListOf(4)
        .xmap(PotDecorations::new, PotDecorations::ordered);
    public static final StreamCodec<RegistryFriendlyByteBuf, PotDecorations> STREAM_CODEC = ByteBufCodecs.registry(Registries.ITEM)
        .apply(ByteBufCodecs.list(4))
        .map(PotDecorations::new, PotDecorations::ordered);

    private PotDecorations(List<Item> sherds) {
        this(getItem(sherds, 0), getItem(sherds, 1), getItem(sherds, 2), getItem(sherds, 3));
    }

    public PotDecorations(Item back, Item left, Item right, Item front) {
        this(List.of(back, left, right, front));
    }

    private static Optional<Item> getItem(List<Item> sherds, int index) {
        if (index >= sherds.size()) {
            return Optional.empty();
        } else {
            Item item = sherds.get(index);
            return item == Items.BRICK ? Optional.empty() : Optional.of(item);
        }
    }

    public CompoundTag save(CompoundTag nbt) {
        if (this.equals(EMPTY)) {
            return nbt;
        } else {
            nbt.put("sherds", CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow());
            return nbt;
        }
    }

    public List<Item> ordered() {
        return Stream.of(this.back, this.left, this.right, this.front).map(item -> item.orElse(Items.BRICK)).toList();
    }

    public static PotDecorations load(@Nullable CompoundTag nbt) {
        return nbt != null && nbt.contains("sherds") ? CODEC.parse(NbtOps.INSTANCE, nbt.get("sherds")).result().orElse(EMPTY) : EMPTY;
    }
}
