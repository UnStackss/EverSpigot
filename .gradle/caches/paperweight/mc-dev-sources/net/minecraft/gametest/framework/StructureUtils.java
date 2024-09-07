package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class StructureUtils {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DEFAULT_Y_SEARCH_RADIUS = 10;
    public static final String DEFAULT_TEST_STRUCTURES_DIR = "gameteststructures";
    public static String testStructuresDir = "gameteststructures";

    public static Rotation getRotationForRotationSteps(int steps) {
        switch (steps) {
            case 0:
                return Rotation.NONE;
            case 1:
                return Rotation.CLOCKWISE_90;
            case 2:
                return Rotation.CLOCKWISE_180;
            case 3:
                return Rotation.COUNTERCLOCKWISE_90;
            default:
                throw new IllegalArgumentException("rotationSteps must be a value from 0-3. Got value " + steps);
        }
    }

    public static int getRotationStepsForRotation(Rotation rotation) {
        switch (rotation) {
            case NONE:
                return 0;
            case CLOCKWISE_90:
                return 1;
            case CLOCKWISE_180:
                return 2;
            case COUNTERCLOCKWISE_90:
                return 3;
            default:
                throw new IllegalArgumentException("Unknown rotation value, don't know how many steps it represents: " + rotation);
        }
    }

    public static AABB getStructureBounds(StructureBlockEntity structureBlockEntity) {
        return AABB.of(getStructureBoundingBox(structureBlockEntity));
    }

    public static BoundingBox getStructureBoundingBox(StructureBlockEntity structureBlockEntity) {
        BlockPos blockPos = getStructureOrigin(structureBlockEntity);
        BlockPos blockPos2 = getTransformedFarCorner(blockPos, structureBlockEntity.getStructureSize(), structureBlockEntity.getRotation());
        return BoundingBox.fromCorners(blockPos, blockPos2);
    }

    public static BlockPos getStructureOrigin(StructureBlockEntity structureBlockEntity) {
        return structureBlockEntity.getBlockPos().offset(structureBlockEntity.getStructurePos());
    }

    public static void addCommandBlockAndButtonToStartTest(BlockPos pos, BlockPos relativePos, Rotation rotation, ServerLevel world) {
        BlockPos blockPos = StructureTemplate.transform(pos.offset(relativePos), Mirror.NONE, rotation, pos);
        world.setBlockAndUpdate(blockPos, Blocks.COMMAND_BLOCK.defaultBlockState());
        CommandBlockEntity commandBlockEntity = (CommandBlockEntity)world.getBlockEntity(blockPos);
        commandBlockEntity.getCommandBlock().setCommand("test runclosest");
        BlockPos blockPos2 = StructureTemplate.transform(blockPos.offset(0, 0, -1), Mirror.NONE, rotation, blockPos);
        world.setBlockAndUpdate(blockPos2, Blocks.STONE_BUTTON.defaultBlockState().rotate(rotation));
    }

    public static void createNewEmptyStructureBlock(String testName, BlockPos pos, Vec3i relativePos, Rotation rotation, ServerLevel world) {
        BoundingBox boundingBox = getStructureBoundingBox(pos.above(), relativePos, rotation);
        clearSpaceForStructure(boundingBox, world);
        world.setBlockAndUpdate(pos, Blocks.STRUCTURE_BLOCK.defaultBlockState());
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)world.getBlockEntity(pos);
        structureBlockEntity.setIgnoreEntities(false);
        structureBlockEntity.setStructureName(ResourceLocation.parse(testName));
        structureBlockEntity.setStructureSize(relativePos);
        structureBlockEntity.setMode(StructureMode.SAVE);
        structureBlockEntity.setShowBoundingBox(true);
    }

    public static StructureBlockEntity prepareTestStructure(GameTestInfo state, BlockPos pos, Rotation rotation, ServerLevel world) {
        Vec3i vec3i = world.getStructureManager()
            .get(ResourceLocation.parse(state.getStructureName()))
            .orElseThrow(() -> new IllegalStateException("Missing test structure: " + state.getStructureName()))
            .getSize();
        BoundingBox boundingBox = getStructureBoundingBox(pos, vec3i, rotation);
        BlockPos blockPos;
        if (rotation == Rotation.NONE) {
            blockPos = pos;
        } else if (rotation == Rotation.CLOCKWISE_90) {
            blockPos = pos.offset(vec3i.getZ() - 1, 0, 0);
        } else if (rotation == Rotation.CLOCKWISE_180) {
            blockPos = pos.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);
        } else {
            if (rotation != Rotation.COUNTERCLOCKWISE_90) {
                throw new IllegalArgumentException("Invalid rotation: " + rotation);
            }

            blockPos = pos.offset(0, 0, vec3i.getX() - 1);
        }

        forceLoadChunks(boundingBox, world);
        clearSpaceForStructure(boundingBox, world);
        return createStructureBlock(state, blockPos.below(), rotation, world);
    }

    public static void encaseStructure(AABB box, ServerLevel world, boolean noSkyAccess) {
        BlockPos blockPos = BlockPos.containing(box.minX, box.minY, box.minZ).offset(-1, 0, -1);
        BlockPos blockPos2 = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        BlockPos.betweenClosedStream(blockPos, blockPos2).forEach(pos -> {
            boolean bl2 = pos.getX() == blockPos.getX() || pos.getX() == blockPos2.getX() || pos.getZ() == blockPos.getZ() || pos.getZ() == blockPos2.getZ();
            boolean bl3 = pos.getY() == blockPos2.getY();
            if (bl2 || bl3 && noSkyAccess) {
                world.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
            }
        });
    }

    public static void removeBarriers(AABB box, ServerLevel world) {
        BlockPos blockPos = BlockPos.containing(box.minX, box.minY, box.minZ).offset(-1, 0, -1);
        BlockPos blockPos2 = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        BlockPos.betweenClosedStream(blockPos, blockPos2).forEach(pos -> {
            boolean bl = pos.getX() == blockPos.getX() || pos.getX() == blockPos2.getX() || pos.getZ() == blockPos.getZ() || pos.getZ() == blockPos2.getZ();
            boolean bl2 = pos.getY() == blockPos2.getY();
            if (world.getBlockState(pos).is(Blocks.BARRIER) && (bl || bl2)) {
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        });
    }

    private static void forceLoadChunks(BoundingBox box, ServerLevel world) {
        box.intersectingChunks().forEach(chunkPos -> world.setChunkForced(chunkPos.x, chunkPos.z, true));
    }

    public static void clearSpaceForStructure(BoundingBox area, ServerLevel world) {
        int i = area.minY() - 1;
        BoundingBox boundingBox = new BoundingBox(area.minX() - 2, area.minY() - 3, area.minZ() - 3, area.maxX() + 3, area.maxY() + 20, area.maxZ() + 3);
        BlockPos.betweenClosedStream(boundingBox).forEach(pos -> clearBlock(i, pos, world));
        world.getBlockTicks().clearArea(boundingBox);
        world.clearBlockEvents(boundingBox);
        AABB aABB = AABB.of(boundingBox);
        List<Entity> list = world.getEntitiesOfClass(Entity.class, aABB, entity -> !(entity instanceof Player));
        list.forEach(Entity::discard);
    }

    public static BlockPos getTransformedFarCorner(BlockPos pos, Vec3i size, Rotation rotation) {
        BlockPos blockPos = pos.offset(size).offset(-1, -1, -1);
        return StructureTemplate.transform(blockPos, Mirror.NONE, rotation, pos);
    }

    public static BoundingBox getStructureBoundingBox(BlockPos pos, Vec3i relativePos, Rotation rotation) {
        BlockPos blockPos = getTransformedFarCorner(pos, relativePos, rotation);
        BoundingBox boundingBox = BoundingBox.fromCorners(pos, blockPos);
        int i = Math.min(boundingBox.minX(), boundingBox.maxX());
        int j = Math.min(boundingBox.minZ(), boundingBox.maxZ());
        return boundingBox.move(pos.getX() - i, 0, pos.getZ() - j);
    }

    public static Optional<BlockPos> findStructureBlockContainingPos(BlockPos pos, int radius, ServerLevel world) {
        return findStructureBlocks(pos, radius, world).filter(structureBlockPos -> doesStructureContain(structureBlockPos, pos, world)).findFirst();
    }

    public static Optional<BlockPos> findNearestStructureBlock(BlockPos pos, int radius, ServerLevel world) {
        Comparator<BlockPos> comparator = Comparator.comparingInt(posx -> posx.distManhattan(pos));
        return findStructureBlocks(pos, radius, world).min(comparator);
    }

    public static Stream<BlockPos> findStructureByTestFunction(BlockPos pos, int radius, ServerLevel world, String templateName) {
        return findStructureBlocks(pos, radius, world)
            .map(posx -> (StructureBlockEntity)world.getBlockEntity(posx))
            .filter(Objects::nonNull)
            .filter(blockEntity -> Objects.equals(blockEntity.getStructureName(), templateName))
            .map(BlockEntity::getBlockPos)
            .map(BlockPos::immutable);
    }

    public static Stream<BlockPos> findStructureBlocks(BlockPos pos, int radius, ServerLevel world) {
        BoundingBox boundingBox = getBoundingBoxAtGround(pos, radius, world);
        return BlockPos.betweenClosedStream(boundingBox).filter(p -> world.getBlockState(p).is(Blocks.STRUCTURE_BLOCK)).map(BlockPos::immutable);
    }

    private static StructureBlockEntity createStructureBlock(GameTestInfo state, BlockPos pos, Rotation rotation, ServerLevel world) {
        world.setBlockAndUpdate(pos, Blocks.STRUCTURE_BLOCK.defaultBlockState());
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)world.getBlockEntity(pos);
        structureBlockEntity.setMode(StructureMode.LOAD);
        structureBlockEntity.setRotation(rotation);
        structureBlockEntity.setIgnoreEntities(false);
        structureBlockEntity.setStructureName(ResourceLocation.parse(state.getStructureName()));
        structureBlockEntity.setMetaData(state.getTestName());
        if (!structureBlockEntity.loadStructureInfo(world)) {
            throw new RuntimeException("Failed to load structure info for test: " + state.getTestName() + ". Structure name: " + state.getStructureName());
        } else {
            return structureBlockEntity;
        }
    }

    private static BoundingBox getBoundingBoxAtGround(BlockPos pos, int radius, ServerLevel world) {
        BlockPos blockPos = BlockPos.containing(
            (double)pos.getX(), (double)world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY(), (double)pos.getZ()
        );
        return new BoundingBox(blockPos).inflatedBy(radius, 10, radius);
    }

    public static Stream<BlockPos> lookedAtStructureBlockPos(BlockPos pos, Entity entity, ServerLevel world) {
        int i = 200;
        Vec3 vec3 = entity.getEyePosition();
        Vec3 vec32 = vec3.add(entity.getLookAngle().scale(200.0));
        return findStructureBlocks(pos, 200, world)
            .map(p -> world.getBlockEntity(p, BlockEntityType.STRUCTURE_BLOCK))
            .flatMap(Optional::stream)
            .filter(blockEntity -> getStructureBounds(blockEntity).clip(vec3, vec32).isPresent())
            .map(BlockEntity::getBlockPos)
            .sorted(Comparator.comparing(pos::distSqr))
            .limit(1L);
    }

    private static void clearBlock(int altitude, BlockPos pos, ServerLevel world) {
        BlockState blockState;
        if (pos.getY() < altitude) {
            blockState = Blocks.STONE.defaultBlockState();
        } else {
            blockState = Blocks.AIR.defaultBlockState();
        }

        BlockInput blockInput = new BlockInput(blockState, Collections.emptySet(), null);
        blockInput.place(world, pos, 2);
        world.blockUpdated(pos, blockState.getBlock());
    }

    private static boolean doesStructureContain(BlockPos structureBlockPos, BlockPos pos, ServerLevel world) {
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)world.getBlockEntity(structureBlockPos);
        return getStructureBoundingBox(structureBlockEntity).isInside(pos);
    }
}
