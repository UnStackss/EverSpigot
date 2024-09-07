package net.minecraft.commands.arguments.blocks;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockPredicateArgument implements ArgumentType<BlockPredicateArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stone", "minecraft:stone", "stone[foo=bar]", "#stone", "#stone[foo=bar]{baz=nbt}");
    private final HolderLookup<Block> blocks;

    public BlockPredicateArgument(CommandBuildContext commandRegistryAccess) {
        this.blocks = commandRegistryAccess.lookupOrThrow(Registries.BLOCK);
    }

    public static BlockPredicateArgument blockPredicate(CommandBuildContext commandRegistryAccess) {
        return new BlockPredicateArgument(commandRegistryAccess);
    }

    public BlockPredicateArgument.Result parse(StringReader stringReader) throws CommandSyntaxException {
        return parse(this.blocks, stringReader);
    }

    public static BlockPredicateArgument.Result parse(HolderLookup<Block> registryWrapper, StringReader reader) throws CommandSyntaxException {
        return BlockStateParser.parseForTesting(registryWrapper, reader, true)
            .map(
                result -> new BlockPredicateArgument.BlockPredicate(result.blockState(), result.properties().keySet(), result.nbt()),
                result -> new BlockPredicateArgument.TagPredicate(result.tag(), result.vagueProperties(), result.nbt())
            );
    }

    public static Predicate<BlockInWorld> getBlockPredicate(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, BlockPredicateArgument.Result.class);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return BlockStateParser.fillSuggestions(this.blocks, suggestionsBuilder, true, true);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    static class BlockPredicate implements BlockPredicateArgument.Result {
        private final BlockState state;
        private final Set<Property<?>> properties;
        @Nullable
        private final CompoundTag nbt;

        public BlockPredicate(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag nbt) {
            this.state = state;
            this.properties = properties;
            this.nbt = nbt;
        }

        @Override
        public boolean test(BlockInWorld blockInWorld) {
            BlockState blockState = blockInWorld.getState();
            if (!blockState.is(this.state.getBlock())) {
                return false;
            } else {
                for (Property<?> property : this.properties) {
                    if (blockState.getValue(property) != this.state.getValue(property)) {
                        return false;
                    }
                }

                if (this.nbt == null) {
                    return true;
                } else {
                    BlockEntity blockEntity = blockInWorld.getEntity();
                    return blockEntity != null
                        && NbtUtils.compareNbt(this.nbt, blockEntity.saveWithFullMetadata(blockInWorld.getLevel().registryAccess()), true);
                }
            }
        }

        @Override
        public boolean requiresNbt() {
            return this.nbt != null;
        }
    }

    public interface Result extends Predicate<BlockInWorld> {
        boolean requiresNbt();
    }

    static class TagPredicate implements BlockPredicateArgument.Result {
        private final HolderSet<Block> tag;
        @Nullable
        private final CompoundTag nbt;
        private final Map<String, String> vagueProperties;

        TagPredicate(HolderSet<Block> tag, Map<String, String> properties, @Nullable CompoundTag nbt) {
            this.tag = tag;
            this.vagueProperties = properties;
            this.nbt = nbt;
        }

        @Override
        public boolean test(BlockInWorld blockInWorld) {
            BlockState blockState = blockInWorld.getState();
            if (!blockState.is(this.tag)) {
                return false;
            } else {
                for (Entry<String, String> entry : this.vagueProperties.entrySet()) {
                    Property<?> property = blockState.getBlock().getStateDefinition().getProperty(entry.getKey());
                    if (property == null) {
                        return false;
                    }

                    Comparable<?> comparable = (Comparable<?>)property.getValue(entry.getValue()).orElse(null);
                    if (comparable == null) {
                        return false;
                    }

                    if (blockState.getValue(property) != comparable) {
                        return false;
                    }
                }

                if (this.nbt == null) {
                    return true;
                } else {
                    BlockEntity blockEntity = blockInWorld.getEntity();
                    return blockEntity != null
                        && NbtUtils.compareNbt(this.nbt, blockEntity.saveWithFullMetadata(blockInWorld.getLevel().registryAccess()), true);
                }
            }
        }

        @Override
        public boolean requiresNbt() {
            return this.nbt != null;
        }
    }
}
