package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class RuinedPortalStructure extends Structure {
    private static final String[] STRUCTURE_LOCATION_PORTALS = new String[]{
        "ruined_portal/portal_1",
        "ruined_portal/portal_2",
        "ruined_portal/portal_3",
        "ruined_portal/portal_4",
        "ruined_portal/portal_5",
        "ruined_portal/portal_6",
        "ruined_portal/portal_7",
        "ruined_portal/portal_8",
        "ruined_portal/portal_9",
        "ruined_portal/portal_10"
    };
    private static final String[] STRUCTURE_LOCATION_GIANT_PORTALS = new String[]{
        "ruined_portal/giant_portal_1", "ruined_portal/giant_portal_2", "ruined_portal/giant_portal_3"
    };
    private static final float PROBABILITY_OF_GIANT_PORTAL = 0.05F;
    private static final int MIN_Y_INDEX = 15;
    private final List<RuinedPortalStructure.Setup> setups;
    public static final MapCodec<RuinedPortalStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    settingsCodec(instance),
                    ExtraCodecs.nonEmptyList(RuinedPortalStructure.Setup.CODEC.listOf()).fieldOf("setups").forGetter(structure -> structure.setups)
                )
                .apply(instance, RuinedPortalStructure::new)
    );

    public RuinedPortalStructure(Structure.StructureSettings config, List<RuinedPortalStructure.Setup> setups) {
        super(config);
        this.setups = setups;
    }

    public RuinedPortalStructure(Structure.StructureSettings config, RuinedPortalStructure.Setup setup) {
        this(config, List.of(setup));
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        RuinedPortalPiece.Properties properties = new RuinedPortalPiece.Properties();
        WorldgenRandom worldgenRandom = context.random();
        RuinedPortalStructure.Setup setup = null;
        if (this.setups.size() > 1) {
            float f = 0.0F;

            for (RuinedPortalStructure.Setup setup2 : this.setups) {
                f += setup2.weight();
            }

            float g = worldgenRandom.nextFloat();

            for (RuinedPortalStructure.Setup setup3 : this.setups) {
                g -= setup3.weight() / f;
                if (g < 0.0F) {
                    setup = setup3;
                    break;
                }
            }
        } else {
            setup = this.setups.get(0);
        }

        if (setup == null) {
            throw new IllegalStateException();
        } else {
            RuinedPortalStructure.Setup setup4 = setup;
            properties.airPocket = sample(worldgenRandom, setup4.airPocketProbability());
            properties.mossiness = setup4.mossiness();
            properties.overgrown = setup4.overgrown();
            properties.vines = setup4.vines();
            properties.replaceWithBlackstone = setup4.replaceWithBlackstone();
            ResourceLocation resourceLocation;
            if (worldgenRandom.nextFloat() < 0.05F) {
                resourceLocation = ResourceLocation.withDefaultNamespace(
                    STRUCTURE_LOCATION_GIANT_PORTALS[worldgenRandom.nextInt(STRUCTURE_LOCATION_GIANT_PORTALS.length)]
                );
            } else {
                resourceLocation = ResourceLocation.withDefaultNamespace(STRUCTURE_LOCATION_PORTALS[worldgenRandom.nextInt(STRUCTURE_LOCATION_PORTALS.length)]);
            }

            StructureTemplate structureTemplate = context.structureTemplateManager().getOrCreate(resourceLocation);
            Rotation rotation = Util.getRandom(Rotation.values(), worldgenRandom);
            Mirror mirror = worldgenRandom.nextFloat() < 0.5F ? Mirror.NONE : Mirror.FRONT_BACK;
            BlockPos blockPos = new BlockPos(structureTemplate.getSize().getX() / 2, 0, structureTemplate.getSize().getZ() / 2);
            ChunkGenerator chunkGenerator = context.chunkGenerator();
            LevelHeightAccessor levelHeightAccessor = context.heightAccessor();
            RandomState randomState = context.randomState();
            BlockPos blockPos2 = context.chunkPos().getWorldPosition();
            BoundingBox boundingBox = structureTemplate.getBoundingBox(blockPos2, rotation, blockPos, mirror);
            BlockPos blockPos3 = boundingBox.getCenter();
            int i = chunkGenerator.getBaseHeight(
                    blockPos3.getX(), blockPos3.getZ(), RuinedPortalPiece.getHeightMapType(setup4.placement()), levelHeightAccessor, randomState
                )
                - 1;
            int j = findSuitableY(
                worldgenRandom,
                chunkGenerator,
                setup4.placement(),
                properties.airPocket,
                i,
                boundingBox.getYSpan(),
                boundingBox,
                levelHeightAccessor,
                randomState
            );
            BlockPos blockPos4 = new BlockPos(blockPos2.getX(), j, blockPos2.getZ());
            return Optional.of(
                new Structure.GenerationStub(
                    blockPos4,
                    collector -> {
                        if (setup4.canBeCold()) {
                            properties.cold = isCold(
                                blockPos4,
                                context.chunkGenerator()
                                    .getBiomeSource()
                                    .getNoiseBiome(
                                        QuartPos.fromBlock(blockPos4.getX()),
                                        QuartPos.fromBlock(blockPos4.getY()),
                                        QuartPos.fromBlock(blockPos4.getZ()),
                                        randomState.sampler()
                                    )
                            );
                        }

                        collector.addPiece(
                            new RuinedPortalPiece(
                                context.structureTemplateManager(),
                                blockPos4,
                                setup4.placement(),
                                properties,
                                resourceLocation,
                                structureTemplate,
                                rotation,
                                mirror,
                                blockPos
                            )
                        );
                    }
                )
            );
        }
    }

    private static boolean sample(WorldgenRandom random, float probability) {
        return probability != 0.0F && (probability == 1.0F || random.nextFloat() < probability);
    }

    private static boolean isCold(BlockPos pos, Holder<Biome> biome) {
        return biome.value().coldEnoughToSnow(pos);
    }

    private static int findSuitableY(
        RandomSource random,
        ChunkGenerator chunkGenerator,
        RuinedPortalPiece.VerticalPlacement verticalPlacement,
        boolean airPocket,
        int height,
        int blockCountY,
        BoundingBox box,
        LevelHeightAccessor world,
        RandomState noiseConfig
    ) {
        int i = world.getMinBuildHeight() + 15;
        int j;
        if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_NETHER) {
            if (airPocket) {
                j = Mth.randomBetweenInclusive(random, 32, 100);
            } else if (random.nextFloat() < 0.5F) {
                j = Mth.randomBetweenInclusive(random, 27, 29);
            } else {
                j = Mth.randomBetweenInclusive(random, 29, 100);
            }
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_MOUNTAIN) {
            int m = height - blockCountY;
            j = getRandomWithinInterval(random, 70, m);
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.UNDERGROUND) {
            int o = height - blockCountY;
            j = getRandomWithinInterval(random, i, o);
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.PARTLY_BURIED) {
            j = height - blockCountY + Mth.randomBetweenInclusive(random, 2, 8);
        } else {
            j = height;
        }

        List<BlockPos> list = ImmutableList.of(
            new BlockPos(box.minX(), 0, box.minZ()),
            new BlockPos(box.maxX(), 0, box.minZ()),
            new BlockPos(box.minX(), 0, box.maxZ()),
            new BlockPos(box.maxX(), 0, box.maxZ())
        );
        List<NoiseColumn> list2 = list.stream()
            .map(pos -> chunkGenerator.getBaseColumn(pos.getX(), pos.getZ(), world, noiseConfig))
            .collect(Collectors.toList());
        Heightmap.Types types = verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR
            ? Heightmap.Types.OCEAN_FLOOR_WG
            : Heightmap.Types.WORLD_SURFACE_WG;

        int s;
        for (s = j; s > i; s--) {
            int t = 0;

            for (NoiseColumn noiseColumn : list2) {
                BlockState blockState = noiseColumn.getBlock(s);
                if (types.isOpaque().test(blockState)) {
                    if (++t == 3) {
                        return s;
                    }
                }
            }
        }

        return s;
    }

    private static int getRandomWithinInterval(RandomSource random, int min, int max) {
        return min < max ? Mth.randomBetweenInclusive(random, min, max) : max;
    }

    @Override
    public StructureType<?> type() {
        return StructureType.RUINED_PORTAL;
    }

    public static record Setup(
        RuinedPortalPiece.VerticalPlacement placement,
        float airPocketProbability,
        float mossiness,
        boolean overgrown,
        boolean vines,
        boolean canBeCold,
        boolean replaceWithBlackstone,
        float weight
    ) {
        public static final Codec<RuinedPortalStructure.Setup> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        RuinedPortalPiece.VerticalPlacement.CODEC.fieldOf("placement").forGetter(RuinedPortalStructure.Setup::placement),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("air_pocket_probability").forGetter(RuinedPortalStructure.Setup::airPocketProbability),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("mossiness").forGetter(RuinedPortalStructure.Setup::mossiness),
                        Codec.BOOL.fieldOf("overgrown").forGetter(RuinedPortalStructure.Setup::overgrown),
                        Codec.BOOL.fieldOf("vines").forGetter(RuinedPortalStructure.Setup::vines),
                        Codec.BOOL.fieldOf("can_be_cold").forGetter(RuinedPortalStructure.Setup::canBeCold),
                        Codec.BOOL.fieldOf("replace_with_blackstone").forGetter(RuinedPortalStructure.Setup::replaceWithBlackstone),
                        ExtraCodecs.POSITIVE_FLOAT.fieldOf("weight").forGetter(RuinedPortalStructure.Setup::weight)
                    )
                    .apply(instance, RuinedPortalStructure.Setup::new)
        );
    }
}
