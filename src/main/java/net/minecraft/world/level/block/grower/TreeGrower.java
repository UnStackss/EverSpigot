package net.minecraft.world.level.block.grower;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
// CraftBukkit start
import net.minecraft.data.worldgen.features.TreeFeatures;
import org.bukkit.TreeType;
// CraftBukkit end

public final class TreeGrower {

    private static final Map<String, TreeGrower> GROWERS = new Object2ObjectArrayMap();
    public static final Codec<TreeGrower> CODEC;
    public static final TreeGrower OAK;
    public static final TreeGrower SPRUCE;
    public static final TreeGrower MANGROVE;
    public static final TreeGrower AZALEA;
    public static final TreeGrower BIRCH;
    public static final TreeGrower JUNGLE;
    public static final TreeGrower ACACIA;
    public static final TreeGrower CHERRY;
    public static final TreeGrower DARK_OAK;
    private final String name;
    private final float secondaryChance;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers;

    public TreeGrower(String id, Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> regularVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> beesVariant) {
        this(id, 0.0F, megaVariant, Optional.empty(), regularVariant, Optional.empty(), beesVariant, Optional.empty());
    }

    public TreeGrower(String id, float rareChance, Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> rareMegaVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> regularVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> rareRegularVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> beesVariant, Optional<ResourceKey<ConfiguredFeature<?, ?>>> rareBeesVariant) {
        this.name = id;
        this.secondaryChance = rareChance;
        this.megaTree = megaVariant;
        this.secondaryMegaTree = rareMegaVariant;
        this.tree = regularVariant;
        this.secondaryTree = rareRegularVariant;
        this.flowers = beesVariant;
        this.secondaryFlowers = rareBeesVariant;
        TreeGrower.GROWERS.put(id, this);
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowersNearby) {
        if (random.nextFloat() < this.secondaryChance) {
            if (flowersNearby && this.secondaryFlowers.isPresent()) {
                return (ResourceKey) this.secondaryFlowers.get();
            }

            if (this.secondaryTree.isPresent()) {
                return (ResourceKey) this.secondaryTree.get();
            }
        }

        return flowersNearby && this.flowers.isPresent() ? (ResourceKey) this.flowers.get() : (ResourceKey) this.tree.orElse(null); // CraftBukkit - decompile error
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random) {
        return this.secondaryMegaTree.isPresent() && random.nextFloat() < this.secondaryChance ? (ResourceKey) this.secondaryMegaTree.get() : (ResourceKey) this.megaTree.orElse(null); // CraftBukkit - decompile error
    }

    public boolean growTree(ServerLevel world, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> resourcekey = this.getConfiguredMegaFeature(random);

        if (resourcekey != null) {
            Holder<ConfiguredFeature<?, ?>> holder = (Holder) world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(resourcekey).orElse(null); // CraftBukkit - decompile error

            if (holder != null) {
                this.setTreeType(holder); // CraftBukkit
                for (int i = 0; i >= -1; --i) {
                    for (int j = 0; j >= -1; --j) {
                        if (TreeGrower.isTwoByTwoSapling(state, world, pos, i, j)) {
                            ConfiguredFeature<?, ?> worldgenfeatureconfigured = (ConfiguredFeature) holder.value();
                            BlockState iblockdata1 = Blocks.AIR.defaultBlockState();

                            world.setBlock(pos.offset(i, 0, j), iblockdata1, 4);
                            world.setBlock(pos.offset(i + 1, 0, j), iblockdata1, 4);
                            world.setBlock(pos.offset(i, 0, j + 1), iblockdata1, 4);
                            world.setBlock(pos.offset(i + 1, 0, j + 1), iblockdata1, 4);
                            if (worldgenfeatureconfigured.place(world, chunkGenerator, random, pos.offset(i, 0, j))) {
                                return true;
                            }

                            world.setBlock(pos.offset(i, 0, j), state, 4);
                            world.setBlock(pos.offset(i + 1, 0, j), state, 4);
                            world.setBlock(pos.offset(i, 0, j + 1), state, 4);
                            world.setBlock(pos.offset(i + 1, 0, j + 1), state, 4);
                            return false;
                        }
                    }
                }
            }
        }

        ResourceKey<ConfiguredFeature<?, ?>> resourcekey1 = this.getConfiguredFeature(random, this.hasFlowers(world, pos));

        if (resourcekey1 == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder1 = (Holder) world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(resourcekey1).orElse(null); // CraftBukkit - decompile error

            if (holder1 == null) {
                return false;
            } else {
                this.setTreeType(holder1); // CraftBukkit
                ConfiguredFeature<?, ?> worldgenfeatureconfigured1 = (ConfiguredFeature) holder1.value();
                BlockState iblockdata2 = world.getFluidState(pos).createLegacyBlock();

                world.setBlock(pos, iblockdata2, 4);
                if (worldgenfeatureconfigured1.place(world, chunkGenerator, random, pos)) {
                    if (world.getBlockState(pos) == iblockdata2) {
                        world.sendBlockUpdated(pos, state, iblockdata2, 2);
                    }

                    return true;
                } else {
                    world.setBlock(pos, state, 4);
                    return false;
                }
            }
        }
    }

    private static boolean isTwoByTwoSapling(BlockState state, BlockGetter world, BlockPos pos, int x, int z) {
        Block block = state.getBlock();

        return world.getBlockState(pos.offset(x, 0, z)).is(block) && world.getBlockState(pos.offset(x + 1, 0, z)).is(block) && world.getBlockState(pos.offset(x, 0, z + 1)).is(block) && world.getBlockState(pos.offset(x + 1, 0, z + 1)).is(block);
    }

    private boolean hasFlowers(LevelAccessor world, BlockPos pos) {
        Iterator iterator = BlockPos.MutableBlockPos.betweenClosed(pos.below().north(2).west(2), pos.above().south(2).east(2)).iterator();

        BlockPos blockposition1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            blockposition1 = (BlockPos) iterator.next();
        } while (!world.getBlockState(blockposition1).is(BlockTags.FLOWERS));

        return true;
    }

    // CraftBukkit start
    private void setTreeType(Holder<ConfiguredFeature<?, ?>> holder) {
        ResourceKey<ConfiguredFeature<?, ?>> worldgentreeabstract = holder.unwrapKey().get();
        if (worldgentreeabstract == TreeFeatures.OAK || worldgentreeabstract == TreeFeatures.OAK_BEES_005) {
            SaplingBlock.treeType = TreeType.TREE;
        } else if (worldgentreeabstract == TreeFeatures.HUGE_RED_MUSHROOM) {
            SaplingBlock.treeType = TreeType.RED_MUSHROOM;
        } else if (worldgentreeabstract == TreeFeatures.HUGE_BROWN_MUSHROOM) {
            SaplingBlock.treeType = TreeType.BROWN_MUSHROOM;
        } else if (worldgentreeabstract == TreeFeatures.JUNGLE_TREE) {
            SaplingBlock.treeType = TreeType.COCOA_TREE;
        } else if (worldgentreeabstract == TreeFeatures.JUNGLE_TREE_NO_VINE) {
            SaplingBlock.treeType = TreeType.SMALL_JUNGLE;
        } else if (worldgentreeabstract == TreeFeatures.PINE) {
            SaplingBlock.treeType = TreeType.TALL_REDWOOD;
        } else if (worldgentreeabstract == TreeFeatures.SPRUCE) {
            SaplingBlock.treeType = TreeType.REDWOOD;
        } else if (worldgentreeabstract == TreeFeatures.ACACIA) {
            SaplingBlock.treeType = TreeType.ACACIA;
        } else if (worldgentreeabstract == TreeFeatures.BIRCH || worldgentreeabstract == TreeFeatures.BIRCH_BEES_005) {
            SaplingBlock.treeType = TreeType.BIRCH;
        } else if (worldgentreeabstract == TreeFeatures.SUPER_BIRCH_BEES_0002) {
            SaplingBlock.treeType = TreeType.TALL_BIRCH;
        } else if (worldgentreeabstract == TreeFeatures.SWAMP_OAK) {
            SaplingBlock.treeType = TreeType.SWAMP;
        } else if (worldgentreeabstract == TreeFeatures.FANCY_OAK || worldgentreeabstract == TreeFeatures.FANCY_OAK_BEES_005) {
            SaplingBlock.treeType = TreeType.BIG_TREE;
        } else if (worldgentreeabstract == TreeFeatures.JUNGLE_BUSH) {
            SaplingBlock.treeType = TreeType.JUNGLE_BUSH;
        } else if (worldgentreeabstract == TreeFeatures.DARK_OAK) {
            SaplingBlock.treeType = TreeType.DARK_OAK;
        } else if (worldgentreeabstract == TreeFeatures.MEGA_SPRUCE) {
            SaplingBlock.treeType = TreeType.MEGA_REDWOOD;
        } else if (worldgentreeabstract == TreeFeatures.MEGA_PINE) {
            SaplingBlock.treeType = TreeType.MEGA_PINE;
        } else if (worldgentreeabstract == TreeFeatures.MEGA_JUNGLE_TREE) {
            SaplingBlock.treeType = TreeType.JUNGLE;
        } else if (worldgentreeabstract == TreeFeatures.AZALEA_TREE) {
            SaplingBlock.treeType = TreeType.AZALEA;
        } else if (worldgentreeabstract == TreeFeatures.MANGROVE) {
            SaplingBlock.treeType = TreeType.MANGROVE;
        } else if (worldgentreeabstract == TreeFeatures.TALL_MANGROVE) {
            SaplingBlock.treeType = TreeType.TALL_MANGROVE;
        } else if (worldgentreeabstract == TreeFeatures.CHERRY || worldgentreeabstract == TreeFeatures.CHERRY_BEES_005) {
            SaplingBlock.treeType = TreeType.CHERRY;
        } else {
            throw new IllegalArgumentException("Unknown tree generator " + worldgentreeabstract);
        }
    }
    // CraftBukkit end

    static {
        Function<TreeGrower, String> function = (worldgentreeprovider) -> { // CraftBukkit - decompile error
            return worldgentreeprovider.name;
        };
        Map<String, TreeGrower> map = TreeGrower.GROWERS; // CraftBukkit - decompile error

        Objects.requireNonNull(map);
        CODEC = Codec.stringResolver(function, map::get);
        OAK = new TreeGrower("oak", 0.1F, Optional.empty(), Optional.empty(), Optional.of(TreeFeatures.OAK), Optional.of(TreeFeatures.FANCY_OAK), Optional.of(TreeFeatures.OAK_BEES_005), Optional.of(TreeFeatures.FANCY_OAK_BEES_005));
        SPRUCE = new TreeGrower("spruce", 0.5F, Optional.of(TreeFeatures.MEGA_SPRUCE), Optional.of(TreeFeatures.MEGA_PINE), Optional.of(TreeFeatures.SPRUCE), Optional.empty(), Optional.empty(), Optional.empty());
        MANGROVE = new TreeGrower("mangrove", 0.85F, Optional.empty(), Optional.empty(), Optional.of(TreeFeatures.MANGROVE), Optional.of(TreeFeatures.TALL_MANGROVE), Optional.empty(), Optional.empty());
        AZALEA = new TreeGrower("azalea", Optional.empty(), Optional.of(TreeFeatures.AZALEA_TREE), Optional.empty());
        BIRCH = new TreeGrower("birch", Optional.empty(), Optional.of(TreeFeatures.BIRCH), Optional.of(TreeFeatures.BIRCH_BEES_005));
        JUNGLE = new TreeGrower("jungle", Optional.of(TreeFeatures.MEGA_JUNGLE_TREE), Optional.of(TreeFeatures.JUNGLE_TREE_NO_VINE), Optional.empty());
        ACACIA = new TreeGrower("acacia", Optional.empty(), Optional.of(TreeFeatures.ACACIA), Optional.empty());
        CHERRY = new TreeGrower("cherry", Optional.empty(), Optional.of(TreeFeatures.CHERRY), Optional.of(TreeFeatures.CHERRY_BEES_005));
        DARK_OAK = new TreeGrower("dark_oak", Optional.of(TreeFeatures.DARK_OAK), Optional.empty(), Optional.empty());
    }
}
