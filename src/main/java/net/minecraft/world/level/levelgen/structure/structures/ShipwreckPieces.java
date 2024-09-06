package net.minecraft.world.level.levelgen.structure.structures;

import java.util.Iterator;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class ShipwreckPieces {

    private static final int NUMBER_OF_BLOCKS_ALLOWED_IN_WORLD_GEN_REGION = 32;
    static final BlockPos PIVOT = new BlockPos(4, 0, 15);
    private static final ResourceLocation[] STRUCTURE_LOCATION_BEACHED = new ResourceLocation[]{ResourceLocation.withDefaultNamespace("shipwreck/with_mast"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_full"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf"), ResourceLocation.withDefaultNamespace("shipwreck/with_mast_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")};
    private static final ResourceLocation[] STRUCTURE_LOCATION_OCEAN = new ResourceLocation[]{ResourceLocation.withDefaultNamespace("shipwreck/with_mast"), ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_full"), ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_fronthalf"), ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_backhalf"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_full"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf"), ResourceLocation.withDefaultNamespace("shipwreck/with_mast_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_full_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_fronthalf_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_backhalf_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_full_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"), ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")};
    static final Map<String, ResourceKey<LootTable>> MARKERS_TO_LOOT = Map.of("map_chest", BuiltInLootTables.SHIPWRECK_MAP, "treasure_chest", BuiltInLootTables.SHIPWRECK_TREASURE, "supply_chest", BuiltInLootTables.SHIPWRECK_SUPPLY);

    public ShipwreckPieces() {}

    public static ShipwreckPieces.ShipwreckPiece addRandomPiece(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, StructurePieceAccessor holder, RandomSource random, boolean beached) {
        ResourceLocation minecraftkey = (ResourceLocation) Util.getRandom((Object[]) (beached ? ShipwreckPieces.STRUCTURE_LOCATION_BEACHED : ShipwreckPieces.STRUCTURE_LOCATION_OCEAN), random);
        ShipwreckPieces.ShipwreckPiece shipwreckpieces_a = new ShipwreckPieces.ShipwreckPiece(structureTemplateManager, minecraftkey, pos, rotation, beached);

        holder.addPiece(shipwreckpieces_a);
        return shipwreckpieces_a;
    }

    public static class ShipwreckPiece extends TemplateStructurePiece {

        private final boolean isBeached;

        public ShipwreckPiece(StructureTemplateManager manager, ResourceLocation identifier, BlockPos pos, Rotation rotation, boolean grounded) {
            super(StructurePieceType.SHIPWRECK_PIECE, 0, manager, identifier, identifier.toString(), ShipwreckPiece.makeSettings(rotation), pos);
            this.isBeached = grounded;
        }

        public ShipwreckPiece(StructureTemplateManager manager, CompoundTag nbt) {
            super(StructurePieceType.SHIPWRECK_PIECE, nbt, manager, (minecraftkey) -> {
                return ShipwreckPiece.makeSettings(Rotation.valueOf(nbt.getString("Rot")));
            });
            this.isBeached = nbt.getBoolean("isBeached");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("isBeached", this.isBeached);
            nbt.putString("Rot", this.placeSettings.getRotation().name());
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation) {
            return (new StructurePlaceSettings()).setRotation(rotation).setMirror(Mirror.NONE).setRotationPivot(ShipwreckPieces.PIVOT).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        @Override
        protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox) {
            ResourceKey<LootTable> resourcekey = (ResourceKey) ShipwreckPieces.MARKERS_TO_LOOT.get(metadata);

            if (resourcekey != null) {
                // CraftBukkit start - ensure block transformation
                /*
                RandomizableContainer.setBlockEntityLootTable(worldaccess, randomsource, blockposition.below(), resourcekey);
                */
                this.setCraftLootTable(world, pos.below(), random, resourcekey);
                // CraftBukkit end
            }

        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (this.isTooBigToFitInWorldGenRegion()) {
                super.postProcess(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
            } else {
                int i = world.getMaxBuildHeight();
                int j = 0;
                Vec3i baseblockposition = this.template.getSize();
                Heightmap.Types heightmap_type = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
                int k = baseblockposition.getX() * baseblockposition.getZ();

                if (k == 0) {
                    j = world.getHeight(heightmap_type, this.templatePosition.getX(), this.templatePosition.getZ());
                } else {
                    BlockPos blockposition1 = this.templatePosition.offset(baseblockposition.getX() - 1, 0, baseblockposition.getZ() - 1);

                    int l;

                    for (Iterator iterator = BlockPos.betweenClosed(this.templatePosition, blockposition1).iterator(); iterator.hasNext(); i = Math.min(i, l)) {
                        BlockPos blockposition2 = (BlockPos) iterator.next();

                        l = world.getHeight(heightmap_type, blockposition2.getX(), blockposition2.getZ());
                        j += l;
                    }

                    j /= k;
                }

                this.adjustPositionHeight(this.isBeached ? this.calculateBeachedPosition(i, random) : j);
                super.postProcess(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
            }
        }

        public boolean isTooBigToFitInWorldGenRegion() {
            Vec3i baseblockposition = this.template.getSize();

            return baseblockposition.getX() > 32 || baseblockposition.getY() > 32;
        }

        public int calculateBeachedPosition(int y, RandomSource random) {
            return y - this.template.getSize().getY() / 2 - random.nextInt(3);
        }

        public void adjustPositionHeight(int y) {
            this.templatePosition = new BlockPos(this.templatePosition.getX(), y, this.templatePosition.getZ());
        }
    }
}
