package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

public class ExplorationMapFunction extends LootItemConditionalFunction {
    public static final TagKey<Structure> DEFAULT_DESTINATION = StructureTags.ON_TREASURE_MAPS;
    public static final Holder<MapDecorationType> DEFAULT_DECORATION = MapDecorationTypes.WOODLAND_MANSION;
    public static final byte DEFAULT_ZOOM = 2;
    public static final int DEFAULT_SEARCH_RADIUS = 50;
    public static final boolean DEFAULT_SKIP_EXISTING = true;
    public static final MapCodec<ExplorationMapFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        TagKey.codec(Registries.STRUCTURE).optionalFieldOf("destination", DEFAULT_DESTINATION).forGetter(function -> function.destination),
                        MapDecorationType.CODEC.optionalFieldOf("decoration", DEFAULT_DECORATION).forGetter(function -> function.mapDecoration),
                        Codec.BYTE.optionalFieldOf("zoom", Byte.valueOf((byte)2)).forGetter(function -> function.zoom),
                        Codec.INT.optionalFieldOf("search_radius", Integer.valueOf(50)).forGetter(function -> function.searchRadius),
                        Codec.BOOL.optionalFieldOf("skip_existing_chunks", Boolean.valueOf(true)).forGetter(function -> function.skipKnownStructures)
                    )
                )
                .apply(instance, ExplorationMapFunction::new)
    );
    private final TagKey<Structure> destination;
    private final Holder<MapDecorationType> mapDecoration;
    private final byte zoom;
    private final int searchRadius;
    private final boolean skipKnownStructures;

    ExplorationMapFunction(
        List<LootItemCondition> conditions,
        TagKey<Structure> destination,
        Holder<MapDecorationType> decoration,
        byte zoom,
        int searchRadius,
        boolean skipExistingChunks
    ) {
        super(conditions);
        this.destination = destination;
        this.mapDecoration = decoration;
        this.zoom = zoom;
        this.searchRadius = searchRadius;
        this.skipKnownStructures = skipExistingChunks;
    }

    @Override
    public LootItemFunctionType<ExplorationMapFunction> getType() {
        return LootItemFunctions.EXPLORATION_MAP;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.ORIGIN);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(Items.MAP)) {
            return stack;
        } else {
            Vec3 vec3 = context.getParamOrNull(LootContextParams.ORIGIN);
            if (vec3 != null) {
                ServerLevel serverLevel = context.getLevel();
                BlockPos blockPos = serverLevel.findNearestMapStructure(
                    this.destination, BlockPos.containing(vec3), this.searchRadius, this.skipKnownStructures
                );
                if (blockPos != null) {
                    ItemStack itemStack = MapItem.create(serverLevel, blockPos.getX(), blockPos.getZ(), this.zoom, true, true);
                    MapItem.renderBiomePreviewMap(serverLevel, itemStack);
                    MapItemSavedData.addTargetDecoration(itemStack, blockPos, "+", this.mapDecoration);
                    return itemStack;
                }
            }

            return stack;
        }
    }

    public static ExplorationMapFunction.Builder makeExplorationMap() {
        return new ExplorationMapFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<ExplorationMapFunction.Builder> {
        private TagKey<Structure> destination = ExplorationMapFunction.DEFAULT_DESTINATION;
        private Holder<MapDecorationType> mapDecoration = ExplorationMapFunction.DEFAULT_DECORATION;
        private byte zoom = 2;
        private int searchRadius = 50;
        private boolean skipKnownStructures = true;

        @Override
        protected ExplorationMapFunction.Builder getThis() {
            return this;
        }

        public ExplorationMapFunction.Builder setDestination(TagKey<Structure> destination) {
            this.destination = destination;
            return this;
        }

        public ExplorationMapFunction.Builder setMapDecoration(Holder<MapDecorationType> decoration) {
            this.mapDecoration = decoration;
            return this;
        }

        public ExplorationMapFunction.Builder setZoom(byte zoom) {
            this.zoom = zoom;
            return this;
        }

        public ExplorationMapFunction.Builder setSearchRadius(int searchRadius) {
            this.searchRadius = searchRadius;
            return this;
        }

        public ExplorationMapFunction.Builder setSkipKnownStructures(boolean skipExistingChunks) {
            this.skipKnownStructures = skipExistingChunks;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new ExplorationMapFunction(
                this.getConditions(), this.destination, this.mapDecoration, this.zoom, this.searchRadius, this.skipKnownStructures
            );
        }
    }
}
