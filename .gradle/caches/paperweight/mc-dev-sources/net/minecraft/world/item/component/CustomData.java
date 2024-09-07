package net.minecraft.world.item.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class CustomData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    public static final Codec<CustomData> CODEC = Codec.withAlternative(CompoundTag.CODEC, TagParser.AS_CODEC)
        .xmap(CustomData::new, component -> component.tag);
    public static final Codec<CustomData> CODEC_WITH_ID = CODEC.validate(
        component -> component.getUnsafe().contains("id", 8) ? DataResult.success(component) : DataResult.error(() -> "Missing id for entity in: " + component)
    );
    @Deprecated
    public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, component -> component.tag);
    private final CompoundTag tag;

    private CustomData(CompoundTag nbt) {
        this.tag = nbt;
    }

    public static CustomData of(CompoundTag nbt) {
        return new CustomData(nbt.copy());
    }

    public static Predicate<ItemStack> itemMatcher(DataComponentType<CustomData> type, CompoundTag nbt) {
        return stack -> {
            CustomData customData = stack.getOrDefault(type, EMPTY);
            return customData.matchedBy(nbt);
        };
    }

    public boolean matchedBy(CompoundTag nbt) {
        return NbtUtils.compareNbt(nbt, this.tag, true);
    }

    public static void update(DataComponentType<CustomData> type, ItemStack stack, Consumer<CompoundTag> nbtSetter) {
        CustomData customData = stack.getOrDefault(type, EMPTY).update(nbtSetter);
        if (customData.tag.isEmpty()) {
            stack.remove(type);
        } else {
            stack.set(type, customData);
        }
    }

    public static void set(DataComponentType<CustomData> type, ItemStack stack, CompoundTag nbt) {
        if (!nbt.isEmpty()) {
            stack.set(type, of(nbt));
        } else {
            stack.remove(type);
        }
    }

    public CustomData update(Consumer<CompoundTag> nbtConsumer) {
        CompoundTag compoundTag = this.tag.copy();
        nbtConsumer.accept(compoundTag);
        return new CustomData(compoundTag);
    }

    public void loadInto(Entity entity) {
        CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
        UUID uUID = entity.getUUID();
        compoundTag.merge(this.tag);
        entity.load(compoundTag);
        entity.setUUID(uUID);
    }

    public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider registryLookup) {
        CompoundTag compoundTag = blockEntity.saveCustomOnly(registryLookup);
        CompoundTag compoundTag2 = compoundTag.copy();
        compoundTag.merge(this.tag);
        if (!compoundTag.equals(compoundTag2)) {
            try {
                blockEntity.loadCustomOnly(compoundTag, registryLookup);
                blockEntity.setChanged();
                return true;
            } catch (Exception var8) {
                LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), var8);

                try {
                    blockEntity.loadCustomOnly(compoundTag2, registryLookup);
                } catch (Exception var7) {
                    LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), var7);
                }
            }
        }

        return false;
    }

    public <T> DataResult<CustomData> update(DynamicOps<Tag> ops, MapEncoder<T> encoder, T value) {
        return encoder.encode(value, ops, ops.mapBuilder()).build(this.tag).map(nbt -> new CustomData((CompoundTag)nbt));
    }

    public <T> DataResult<T> read(MapDecoder<T> decoder) {
        return this.read(NbtOps.INSTANCE, decoder);
    }

    public <T> DataResult<T> read(DynamicOps<Tag> ops, MapDecoder<T> decoder) {
        MapLike<Tag> mapLike = ops.getMap(this.tag).getOrThrow();
        return decoder.decode(ops, mapLike);
    }

    public int size() {
        return this.tag.size();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    @Override
    public boolean equals(Object object) {
        return object == this || object instanceof CustomData customData && this.tag.equals(customData.tag);
    }

    @Override
    public int hashCode() {
        return this.tag.hashCode();
    }

    @Override
    public String toString() {
        return this.tag.toString();
    }

    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }
}
