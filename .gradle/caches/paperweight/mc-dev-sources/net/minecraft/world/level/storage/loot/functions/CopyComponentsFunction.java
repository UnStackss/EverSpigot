package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyComponentsFunction extends LootItemConditionalFunction {
    public static final MapCodec<CopyComponentsFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        CopyComponentsFunction.Source.CODEC.fieldOf("source").forGetter(function -> function.source),
                        DataComponentType.CODEC.listOf().optionalFieldOf("include").forGetter(function -> function.include),
                        DataComponentType.CODEC.listOf().optionalFieldOf("exclude").forGetter(function -> function.exclude)
                    )
                )
                .apply(instance, CopyComponentsFunction::new)
    );
    private final CopyComponentsFunction.Source source;
    private final Optional<List<DataComponentType<?>>> include;
    private final Optional<List<DataComponentType<?>>> exclude;
    private final Predicate<DataComponentType<?>> bakedPredicate;

    CopyComponentsFunction(
        List<LootItemCondition> conditions,
        CopyComponentsFunction.Source source,
        Optional<List<DataComponentType<?>>> include,
        Optional<List<DataComponentType<?>>> exclude
    ) {
        super(conditions);
        this.source = source;
        this.include = include.map(List::copyOf);
        this.exclude = exclude.map(List::copyOf);
        List<Predicate<DataComponentType<?>>> list = new ArrayList<>(2);
        exclude.ifPresent(excludedTypes -> list.add(type -> !excludedTypes.contains(type)));
        include.ifPresent(includedTypes -> list.add(includedTypes::contains));
        this.bakedPredicate = Util.allOf(list);
    }

    @Override
    public LootItemFunctionType<CopyComponentsFunction> getType() {
        return LootItemFunctions.COPY_COMPONENTS;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.source.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        DataComponentMap dataComponentMap = this.source.get(context);
        stack.applyComponents(dataComponentMap.filter(this.bakedPredicate));
        return stack;
    }

    public static CopyComponentsFunction.Builder copyComponents(CopyComponentsFunction.Source source) {
        return new CopyComponentsFunction.Builder(source);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyComponentsFunction.Builder> {
        private final CopyComponentsFunction.Source source;
        private Optional<ImmutableList.Builder<DataComponentType<?>>> include = Optional.empty();
        private Optional<ImmutableList.Builder<DataComponentType<?>>> exclude = Optional.empty();

        Builder(CopyComponentsFunction.Source source) {
            this.source = source;
        }

        public CopyComponentsFunction.Builder include(DataComponentType<?> type) {
            if (this.include.isEmpty()) {
                this.include = Optional.of(ImmutableList.builder());
            }

            this.include.get().add(type);
            return this;
        }

        public CopyComponentsFunction.Builder exclude(DataComponentType<?> type) {
            if (this.exclude.isEmpty()) {
                this.exclude = Optional.of(ImmutableList.builder());
            }

            this.exclude.get().add(type);
            return this;
        }

        @Override
        protected CopyComponentsFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyComponentsFunction(
                this.getConditions(), this.source, this.include.map(ImmutableList.Builder::build), this.exclude.map(ImmutableList.Builder::build)
            );
        }
    }

    public static enum Source implements StringRepresentable {
        BLOCK_ENTITY("block_entity");

        public static final Codec<CopyComponentsFunction.Source> CODEC = StringRepresentable.fromValues(CopyComponentsFunction.Source::values);
        private final String name;

        private Source(final String id) {
            this.name = id;
        }

        public DataComponentMap get(LootContext context) {
            switch (this) {
                case BLOCK_ENTITY:
                    BlockEntity blockEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
                    return blockEntity != null ? blockEntity.collectComponents() : DataComponentMap.EMPTY;
                default:
                    throw new MatchException(null, null);
            }
        }

        public Set<LootContextParam<?>> getReferencedContextParams() {
            switch (this) {
                case BLOCK_ENTITY:
                    return Set.of(LootContextParams.BLOCK_ENTITY);
                default:
                    throw new MatchException(null, null);
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
