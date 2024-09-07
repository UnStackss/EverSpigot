package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public record BlockPredicate(Optional<HolderSet<Block>> blocks, Optional<StatePropertiesPredicate> properties, Optional<NbtPredicate> nbt) {
    public static final Codec<BlockPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("blocks").forGetter(BlockPredicate::blocks),
                    StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(BlockPredicate::properties),
                    NbtPredicate.CODEC.optionalFieldOf("nbt").forGetter(BlockPredicate::nbt)
                )
                .apply(instance, BlockPredicate::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPredicate> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(ByteBufCodecs.holderSet(Registries.BLOCK)),
        BlockPredicate::blocks,
        ByteBufCodecs.optional(StatePropertiesPredicate.STREAM_CODEC),
        BlockPredicate::properties,
        ByteBufCodecs.optional(NbtPredicate.STREAM_CODEC),
        BlockPredicate::nbt,
        BlockPredicate::new
    );

    public boolean matches(ServerLevel world, BlockPos pos) {
        return world.isLoaded(pos)
            && this.matchesState(world.getBlockState(pos))
            && (!this.nbt.isPresent() || matchesBlockEntity(world, world.getBlockEntity(pos), this.nbt.get()));
    }

    public boolean matches(BlockInWorld pos) {
        return this.matchesState(pos.getState()) && (!this.nbt.isPresent() || matchesBlockEntity(pos.getLevel(), pos.getEntity(), this.nbt.get()));
    }

    private boolean matchesState(BlockState state) {
        return (!this.blocks.isPresent() || state.is(this.blocks.get())) && (!this.properties.isPresent() || this.properties.get().matches(state));
    }

    private static boolean matchesBlockEntity(LevelReader world, @Nullable BlockEntity blockEntity, NbtPredicate nbtPredicate) {
        return blockEntity != null && nbtPredicate.matches(blockEntity.saveWithFullMetadata(world.registryAccess()));
    }

    public boolean requiresNbt() {
        return this.nbt.isPresent();
    }

    public static class Builder {
        private Optional<HolderSet<Block>> blocks = Optional.empty();
        private Optional<StatePropertiesPredicate> properties = Optional.empty();
        private Optional<NbtPredicate> nbt = Optional.empty();

        private Builder() {
        }

        public static BlockPredicate.Builder block() {
            return new BlockPredicate.Builder();
        }

        public BlockPredicate.Builder of(Block... blocks) {
            this.blocks = Optional.of(HolderSet.direct(Block::builtInRegistryHolder, blocks));
            return this;
        }

        public BlockPredicate.Builder of(Collection<Block> blocks) {
            this.blocks = Optional.of(HolderSet.direct(Block::builtInRegistryHolder, blocks));
            return this;
        }

        public BlockPredicate.Builder of(TagKey<Block> tag) {
            this.blocks = Optional.of(BuiltInRegistries.BLOCK.getOrCreateTag(tag));
            return this;
        }

        public BlockPredicate.Builder hasNbt(CompoundTag nbt) {
            this.nbt = Optional.of(new NbtPredicate(nbt));
            return this;
        }

        public BlockPredicate.Builder setProperties(StatePropertiesPredicate.Builder state) {
            this.properties = state.build();
            return this;
        }

        public BlockPredicate build() {
            return new BlockPredicate(this.blocks, this.properties, this.nbt);
        }
    }
}
