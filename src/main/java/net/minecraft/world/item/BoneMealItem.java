package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class BoneMealItem extends Item {

    public static final int GRASS_SPREAD_WIDTH = 3;
    public static final int GRASS_SPREAD_HEIGHT = 1;
    public static final int GRASS_COUNT_MULTIPLIER = 3;

    public BoneMealItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // CraftBukkit start - extract bonemeal application logic to separate, static method
        return BoneMealItem.applyBonemeal(context);
    }

    public static InteractionResult applyBonemeal(UseOnContext itemactioncontext) {
        // CraftBukkit end
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockPos blockposition1 = blockposition.relative(itemactioncontext.getClickedFace());

        if (BoneMealItem.growCrop(itemactioncontext.getItemInHand(), world, blockposition)) {
            if (!world.isClientSide) {
                if (itemactioncontext.getPlayer() != null) itemactioncontext.getPlayer().gameEvent(GameEvent.ITEM_INTERACT_FINISH); // CraftBukkit - SPIGOT-7518
                world.levelEvent(1505, blockposition, 15);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            BlockState iblockdata = world.getBlockState(blockposition);
            boolean flag = iblockdata.isFaceSturdy(world, blockposition, itemactioncontext.getClickedFace());

            if (flag && BoneMealItem.growWaterPlant(itemactioncontext.getItemInHand(), world, blockposition1, itemactioncontext.getClickedFace())) {
                if (!world.isClientSide) {
                    if (itemactioncontext.getPlayer() != null) itemactioncontext.getPlayer().gameEvent(GameEvent.ITEM_INTERACT_FINISH); // CraftBukkit - SPIGOT-7518
                    world.levelEvent(1505, blockposition1, 15);
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public static boolean growCrop(ItemStack stack, Level world, BlockPos pos) {
        BlockState iblockdata = world.getBlockState(pos);
        Block block = iblockdata.getBlock();

        if (block instanceof BonemealableBlock iblockfragileplantelement) {
            if (iblockfragileplantelement.isValidBonemealTarget(world, pos, iblockdata)) {
                if (world instanceof ServerLevel) {
                    if (iblockfragileplantelement.isBonemealSuccess(world, world.random, pos, iblockdata)) {
                        iblockfragileplantelement.performBonemeal((ServerLevel) world, world.random, pos, iblockdata);
                    }

                    stack.shrink(1);
                }

                return true;
            }
        }

        return false;
    }

    public static boolean growWaterPlant(ItemStack stack, Level world, BlockPos blockPos, @Nullable Direction facing) {
        if (world.getBlockState(blockPos).is(Blocks.WATER) && world.getFluidState(blockPos).getAmount() == 8) {
            if (!(world instanceof ServerLevel)) {
                return true;
            } else {
                RandomSource randomsource = world.getRandom();
                int i = 0;

                while (i < 128) {
                    BlockPos blockposition1 = blockPos;
                    BlockState iblockdata = Blocks.SEAGRASS.defaultBlockState();
                    int j = 0;

                    while (true) {
                        if (j < i / 16) {
                            blockposition1 = blockposition1.offset(randomsource.nextInt(3) - 1, (randomsource.nextInt(3) - 1) * randomsource.nextInt(3) / 2, randomsource.nextInt(3) - 1);
                            if (!world.getBlockState(blockposition1).isCollisionShapeFullBlock(world, blockposition1)) {
                                ++j;
                                continue;
                            }
                        } else {
                            Holder<Biome> holder = world.getBiome(blockposition1);

                            if (holder.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
                                if (i == 0 && facing != null && facing.getAxis().isHorizontal()) {
                                    iblockdata = (BlockState) BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.WALL_CORALS, world.random).map((holder1) -> {
                                        return ((Block) holder1.value()).defaultBlockState();
                                    }).orElse(iblockdata);
                                    if (iblockdata.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                        iblockdata = (BlockState) iblockdata.setValue(BaseCoralWallFanBlock.FACING, facing);
                                    }
                                } else if (randomsource.nextInt(4) == 0) {
                                    iblockdata = (BlockState) BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.UNDERWATER_BONEMEALS, world.random).map((holder1) -> {
                                        return ((Block) holder1.value()).defaultBlockState();
                                    }).orElse(iblockdata);
                                }
                            }

                            if (iblockdata.is(BlockTags.WALL_CORALS, (blockbase_blockdata) -> {
                                return blockbase_blockdata.hasProperty(BaseCoralWallFanBlock.FACING);
                            })) {
                                for (int k = 0; !iblockdata.canSurvive(world, blockposition1) && k < 4; ++k) {
                                    iblockdata = (BlockState) iblockdata.setValue(BaseCoralWallFanBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(randomsource));
                                }
                            }

                            if (iblockdata.canSurvive(world, blockposition1)) {
                                BlockState iblockdata1 = world.getBlockState(blockposition1);

                                if (iblockdata1.is(Blocks.WATER) && world.getFluidState(blockposition1).getAmount() == 8) {
                                    world.setBlock(blockposition1, iblockdata, 3);
                                } else if (iblockdata1.is(Blocks.SEAGRASS) && randomsource.nextInt(10) == 0) {
                                    ((BonemealableBlock) Blocks.SEAGRASS).performBonemeal((ServerLevel) world, randomsource, blockposition1, iblockdata1);
                                }
                            }
                        }

                        ++i;
                        break;
                    }
                }

                stack.shrink(1);
                return true;
            }
        } else {
            return false;
        }
    }

    public static void addGrowthParticles(LevelAccessor world, BlockPos pos, int count) {
        BlockState iblockdata = world.getBlockState(pos);
        Block block = iblockdata.getBlock();

        if (block instanceof BonemealableBlock) {
            BonemealableBlock iblockfragileplantelement = (BonemealableBlock) block;
            BlockPos blockposition1 = iblockfragileplantelement.getParticlePos(pos);

            switch (iblockfragileplantelement.getType()) {
                case NEIGHBOR_SPREADER:
                    ParticleUtils.spawnParticles(world, blockposition1, count * 3, 3.0D, 1.0D, false, ParticleTypes.HAPPY_VILLAGER);
                    break;
                case GROWER:
                    ParticleUtils.spawnParticleInBlock(world, blockposition1, count, ParticleTypes.HAPPY_VILLAGER);
            }
        } else if (iblockdata.is(Blocks.WATER)) {
            ParticleUtils.spawnParticles(world, pos, count * 3, 3.0D, 1.0D, false, ParticleTypes.HAPPY_VILLAGER);
        }

    }
}
