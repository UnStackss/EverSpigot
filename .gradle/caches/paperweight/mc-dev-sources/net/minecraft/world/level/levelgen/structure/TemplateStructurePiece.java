package net.minecraft.world.level.levelgen.structure;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.function.Function;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public abstract class TemplateStructurePiece extends StructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final String templateName;
    protected StructureTemplate template;
    protected StructurePlaceSettings placeSettings;
    protected BlockPos templatePosition;

    public TemplateStructurePiece(
        StructurePieceType type,
        int length,
        StructureTemplateManager structureTemplateManager,
        ResourceLocation id,
        String template,
        StructurePlaceSettings placementData,
        BlockPos pos
    ) {
        super(type, length, structureTemplateManager.getOrCreate(id).getBoundingBox(placementData, pos));
        this.setOrientation(Direction.NORTH);
        this.templateName = template;
        this.templatePosition = pos;
        this.template = structureTemplateManager.getOrCreate(id);
        this.placeSettings = placementData;
    }

    public TemplateStructurePiece(
        StructurePieceType type,
        CompoundTag nbt,
        StructureTemplateManager structureTemplateManager,
        Function<ResourceLocation, StructurePlaceSettings> placementDataGetter
    ) {
        super(type, nbt);
        this.setOrientation(Direction.NORTH);
        this.templateName = nbt.getString("Template");
        this.templatePosition = new BlockPos(nbt.getInt("TPX"), nbt.getInt("TPY"), nbt.getInt("TPZ"));
        ResourceLocation resourceLocation = this.makeTemplateLocation();
        this.template = structureTemplateManager.getOrCreate(resourceLocation);
        this.placeSettings = placementDataGetter.apply(resourceLocation);
        this.boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
    }

    protected ResourceLocation makeTemplateLocation() {
        return ResourceLocation.parse(this.templateName);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
        nbt.putInt("TPX", this.templatePosition.getX());
        nbt.putInt("TPY", this.templatePosition.getY());
        nbt.putInt("TPZ", this.templatePosition.getZ());
        nbt.putString("Template", this.templateName);
    }

    @Override
    public void postProcess(
        WorldGenLevel world,
        StructureManager structureAccessor,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox chunkBox,
        ChunkPos chunkPos,
        BlockPos pivot
    ) {
        this.placeSettings.setBoundingBox(chunkBox);
        this.boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        if (this.template.placeInWorld(world, this.templatePosition, pivot, this.placeSettings, random, 2)) {
            for (StructureTemplate.StructureBlockInfo structureBlockInfo : this.template
                .filterBlocks(this.templatePosition, this.placeSettings, Blocks.STRUCTURE_BLOCK)) {
                if (structureBlockInfo.nbt() != null) {
                    StructureMode structureMode = StructureMode.valueOf(structureBlockInfo.nbt().getString("mode"));
                    if (structureMode == StructureMode.DATA) {
                        this.handleDataMarker(structureBlockInfo.nbt().getString("metadata"), structureBlockInfo.pos(), world, random, chunkBox);
                    }
                }
            }

            for (StructureTemplate.StructureBlockInfo structureBlockInfo2 : this.template
                .filterBlocks(this.templatePosition, this.placeSettings, Blocks.JIGSAW)) {
                if (structureBlockInfo2.nbt() != null) {
                    String string = structureBlockInfo2.nbt().getString("final_state");
                    BlockState blockState = Blocks.AIR.defaultBlockState();

                    try {
                        blockState = BlockStateParser.parseForBlock(world.holderLookup(Registries.BLOCK), string, true).blockState();
                    } catch (CommandSyntaxException var15) {
                        LOGGER.error("Error while parsing blockstate {} in jigsaw block @ {}", string, structureBlockInfo2.pos());
                    }

                    world.setBlock(structureBlockInfo2.pos(), blockState, 3);
                }
            }
        }
    }

    protected abstract void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox);

    @Deprecated
    @Override
    public void move(int x, int y, int z) {
        super.move(x, y, z);
        this.templatePosition = this.templatePosition.offset(x, y, z);
    }

    @Override
    public Rotation getRotation() {
        return this.placeSettings.getRotation();
    }

    public StructureTemplate template() {
        return this.template;
    }

    public BlockPos templatePosition() {
        return this.templatePosition;
    }

    public StructurePlaceSettings placeSettings() {
        return this.placeSettings;
    }
}
