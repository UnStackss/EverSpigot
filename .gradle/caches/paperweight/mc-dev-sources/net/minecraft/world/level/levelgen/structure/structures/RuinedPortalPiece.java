package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlackstoneReplaceProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.LavaSubmergedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class RuinedPortalPiece extends TemplateStructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float PROBABILITY_OF_GOLD_GONE = 0.3F;
    private static final float PROBABILITY_OF_MAGMA_INSTEAD_OF_NETHERRACK = 0.07F;
    private static final float PROBABILITY_OF_MAGMA_INSTEAD_OF_LAVA = 0.2F;
    private final RuinedPortalPiece.VerticalPlacement verticalPlacement;
    private final RuinedPortalPiece.Properties properties;

    public RuinedPortalPiece(
        StructureTemplateManager manager,
        BlockPos pos,
        RuinedPortalPiece.VerticalPlacement verticalPlacement,
        RuinedPortalPiece.Properties properties,
        ResourceLocation id,
        StructureTemplate template,
        Rotation rotation,
        Mirror mirror,
        BlockPos blockPos
    ) {
        super(StructurePieceType.RUINED_PORTAL, 0, manager, id, id.toString(), makeSettings(mirror, rotation, verticalPlacement, blockPos, properties), pos);
        this.verticalPlacement = verticalPlacement;
        this.properties = properties;
    }

    public RuinedPortalPiece(StructureTemplateManager manager, CompoundTag nbt) {
        super(StructurePieceType.RUINED_PORTAL, nbt, manager, id -> makeSettings(manager, nbt, id));
        this.verticalPlacement = RuinedPortalPiece.VerticalPlacement.byName(nbt.getString("VerticalPlacement"));
        this.properties = RuinedPortalPiece.Properties.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, nbt.get("Properties"))).getPartialOrThrow();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
        super.addAdditionalSaveData(context, nbt);
        nbt.putString("Rotation", this.placeSettings.getRotation().name());
        nbt.putString("Mirror", this.placeSettings.getMirror().name());
        nbt.putString("VerticalPlacement", this.verticalPlacement.getName());
        RuinedPortalPiece.Properties.CODEC
            .encodeStart(NbtOps.INSTANCE, this.properties)
            .resultOrPartial(LOGGER::error)
            .ifPresent(tag -> nbt.put("Properties", tag));
    }

    private static StructurePlaceSettings makeSettings(StructureTemplateManager manager, CompoundTag nbt, ResourceLocation id) {
        StructureTemplate structureTemplate = manager.getOrCreate(id);
        BlockPos blockPos = new BlockPos(structureTemplate.getSize().getX() / 2, 0, structureTemplate.getSize().getZ() / 2);
        return makeSettings(
            Mirror.valueOf(nbt.getString("Mirror")),
            Rotation.valueOf(nbt.getString("Rotation")),
            RuinedPortalPiece.VerticalPlacement.byName(nbt.getString("VerticalPlacement")),
            blockPos,
            RuinedPortalPiece.Properties.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, nbt.get("Properties"))).getPartialOrThrow()
        );
    }

    private static StructurePlaceSettings makeSettings(
        Mirror mirror, Rotation rotation, RuinedPortalPiece.VerticalPlacement verticalPlacement, BlockPos pos, RuinedPortalPiece.Properties properties
    ) {
        BlockIgnoreProcessor blockIgnoreProcessor = properties.airPocket ? BlockIgnoreProcessor.STRUCTURE_BLOCK : BlockIgnoreProcessor.STRUCTURE_AND_AIR;
        List<ProcessorRule> list = Lists.newArrayList();
        list.add(getBlockReplaceRule(Blocks.GOLD_BLOCK, 0.3F, Blocks.AIR));
        list.add(getLavaProcessorRule(verticalPlacement, properties));
        if (!properties.cold) {
            list.add(getBlockReplaceRule(Blocks.NETHERRACK, 0.07F, Blocks.MAGMA_BLOCK));
        }

        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(mirror)
            .setRotationPivot(pos)
            .addProcessor(blockIgnoreProcessor)
            .addProcessor(new RuleProcessor(list))
            .addProcessor(new BlockAgeProcessor(properties.mossiness))
            .addProcessor(new ProtectedBlockProcessor(BlockTags.FEATURES_CANNOT_REPLACE))
            .addProcessor(new LavaSubmergedBlockProcessor());
        if (properties.replaceWithBlackstone) {
            structurePlaceSettings.addProcessor(BlackstoneReplaceProcessor.INSTANCE);
        }

        return structurePlaceSettings;
    }

    private static ProcessorRule getLavaProcessorRule(RuinedPortalPiece.VerticalPlacement verticalPlacement, RuinedPortalPiece.Properties properties) {
        if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR) {
            return getBlockReplaceRule(Blocks.LAVA, Blocks.MAGMA_BLOCK);
        } else {
            return properties.cold ? getBlockReplaceRule(Blocks.LAVA, Blocks.NETHERRACK) : getBlockReplaceRule(Blocks.LAVA, 0.2F, Blocks.MAGMA_BLOCK);
        }
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
        BoundingBox boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        if (chunkBox.isInside(boundingBox.getCenter())) {
            chunkBox.encapsulate(boundingBox);
            super.postProcess(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
            this.spreadNetherrack(random, world);
            this.addNetherrackDripColumnsBelowPortal(random, world);
            if (this.properties.vines || this.properties.overgrown) {
                BlockPos.betweenClosedStream(this.getBoundingBox()).forEach(pos -> {
                    if (this.properties.vines) {
                        this.maybeAddVines(random, world, pos);
                    }

                    if (this.properties.overgrown) {
                        this.maybeAddLeavesAbove(random, world, pos);
                    }
                });
            }
        }
    }

    @Override
    protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox) {
    }

    private void maybeAddVines(RandomSource random, LevelAccessor world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        if (!blockState.isAir() && !blockState.is(Blocks.VINE)) {
            Direction direction = getRandomHorizontalDirection(random);
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState2 = world.getBlockState(blockPos);
            if (blockState2.isAir()) {
                if (Block.isFaceFull(blockState.getCollisionShape(world, pos), direction)) {
                    BooleanProperty booleanProperty = VineBlock.getPropertyForFace(direction.getOpposite());
                    world.setBlock(blockPos, Blocks.VINE.defaultBlockState().setValue(booleanProperty, Boolean.valueOf(true)), 3);
                }
            }
        }
    }

    private void maybeAddLeavesAbove(RandomSource random, LevelAccessor world, BlockPos pos) {
        if (random.nextFloat() < 0.5F && world.getBlockState(pos).is(Blocks.NETHERRACK) && world.getBlockState(pos.above()).isAir()) {
            world.setBlock(pos.above(), Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true)), 3);
        }
    }

    private void addNetherrackDripColumnsBelowPortal(RandomSource random, LevelAccessor world) {
        for (int i = this.boundingBox.minX() + 1; i < this.boundingBox.maxX(); i++) {
            for (int j = this.boundingBox.minZ() + 1; j < this.boundingBox.maxZ(); j++) {
                BlockPos blockPos = new BlockPos(i, this.boundingBox.minY(), j);
                if (world.getBlockState(blockPos).is(Blocks.NETHERRACK)) {
                    this.addNetherrackDripColumn(random, world, blockPos.below());
                }
            }
        }
    }

    private void addNetherrackDripColumn(RandomSource random, LevelAccessor world, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        this.placeNetherrackOrMagma(random, world, mutableBlockPos);
        int i = 8;

        while (i > 0 && random.nextFloat() < 0.5F) {
            mutableBlockPos.move(Direction.DOWN);
            i--;
            this.placeNetherrackOrMagma(random, world, mutableBlockPos);
        }
    }

    private void spreadNetherrack(RandomSource random, LevelAccessor world) {
        boolean bl = this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_LAND_SURFACE
            || this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR;
        BlockPos blockPos = this.boundingBox.getCenter();
        int i = blockPos.getX();
        int j = blockPos.getZ();
        float[] fs = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};
        int k = fs.length;
        int l = (this.boundingBox.getXSpan() + this.boundingBox.getZSpan()) / 2;
        int m = random.nextInt(Math.max(1, 8 - l / 2));
        int n = 3;
        BlockPos.MutableBlockPos mutableBlockPos = BlockPos.ZERO.mutable();

        for (int o = i - k; o <= i + k; o++) {
            for (int p = j - k; p <= j + k; p++) {
                int q = Math.abs(o - i) + Math.abs(p - j);
                int r = Math.max(0, q + m);
                if (r < k) {
                    float f = fs[r];
                    if (random.nextDouble() < (double)f) {
                        int s = getSurfaceY(world, o, p, this.verticalPlacement);
                        int t = bl ? s : Math.min(this.boundingBox.minY(), s);
                        mutableBlockPos.set(o, t, p);
                        if (Math.abs(t - this.boundingBox.minY()) <= 3 && this.canBlockBeReplacedByNetherrackOrMagma(world, mutableBlockPos)) {
                            this.placeNetherrackOrMagma(random, world, mutableBlockPos);
                            if (this.properties.overgrown) {
                                this.maybeAddLeavesAbove(random, world, mutableBlockPos);
                            }

                            this.addNetherrackDripColumn(random, world, mutableBlockPos.below());
                        }
                    }
                }
            }
        }
    }

    private boolean canBlockBeReplacedByNetherrackOrMagma(LevelAccessor world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return !blockState.is(Blocks.AIR)
            && !blockState.is(Blocks.OBSIDIAN)
            && !blockState.is(BlockTags.FEATURES_CANNOT_REPLACE)
            && (this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_NETHER || !blockState.is(Blocks.LAVA));
    }

    private void placeNetherrackOrMagma(RandomSource random, LevelAccessor world, BlockPos pos) {
        if (!this.properties.cold && random.nextFloat() < 0.07F) {
            world.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        } else {
            world.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), 3);
        }
    }

    private static int getSurfaceY(LevelAccessor world, int x, int y, RuinedPortalPiece.VerticalPlacement verticalPlacement) {
        return world.getHeight(getHeightMapType(verticalPlacement), x, y) - 1;
    }

    public static Heightmap.Types getHeightMapType(RuinedPortalPiece.VerticalPlacement verticalPlacement) {
        return verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
    }

    private static ProcessorRule getBlockReplaceRule(Block old, float chance, Block updated) {
        return new ProcessorRule(new RandomBlockMatchTest(old, chance), AlwaysTrueTest.INSTANCE, updated.defaultBlockState());
    }

    private static ProcessorRule getBlockReplaceRule(Block old, Block updated) {
        return new ProcessorRule(new BlockMatchTest(old), AlwaysTrueTest.INSTANCE, updated.defaultBlockState());
    }

    public static class Properties {
        public static final Codec<RuinedPortalPiece.Properties> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        Codec.BOOL.fieldOf("cold").forGetter(properties -> properties.cold),
                        Codec.FLOAT.fieldOf("mossiness").forGetter(properties -> properties.mossiness),
                        Codec.BOOL.fieldOf("air_pocket").forGetter(properties -> properties.airPocket),
                        Codec.BOOL.fieldOf("overgrown").forGetter(properties -> properties.overgrown),
                        Codec.BOOL.fieldOf("vines").forGetter(properties -> properties.vines),
                        Codec.BOOL.fieldOf("replace_with_blackstone").forGetter(properties -> properties.replaceWithBlackstone)
                    )
                    .apply(instance, RuinedPortalPiece.Properties::new)
        );
        public boolean cold;
        public float mossiness;
        public boolean airPocket;
        public boolean overgrown;
        public boolean vines;
        public boolean replaceWithBlackstone;

        public Properties() {
        }

        public Properties(boolean cold, float mossiness, boolean airPocket, boolean overgrown, boolean vines, boolean replaceWithBlackstone) {
            this.cold = cold;
            this.mossiness = mossiness;
            this.airPocket = airPocket;
            this.overgrown = overgrown;
            this.vines = vines;
            this.replaceWithBlackstone = replaceWithBlackstone;
        }
    }

    public static enum VerticalPlacement implements StringRepresentable {
        ON_LAND_SURFACE("on_land_surface"),
        PARTLY_BURIED("partly_buried"),
        ON_OCEAN_FLOOR("on_ocean_floor"),
        IN_MOUNTAIN("in_mountain"),
        UNDERGROUND("underground"),
        IN_NETHER("in_nether");

        public static final StringRepresentable.EnumCodec<RuinedPortalPiece.VerticalPlacement> CODEC = StringRepresentable.fromEnum(
            RuinedPortalPiece.VerticalPlacement::values
        );
        private final String name;

        private VerticalPlacement(final String id) {
            this.name = id;
        }

        public String getName() {
            return this.name;
        }

        public static RuinedPortalPiece.VerticalPlacement byName(String id) {
            return CODEC.byName(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
