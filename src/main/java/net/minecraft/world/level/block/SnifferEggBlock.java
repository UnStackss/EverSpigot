package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SnifferEggBlock extends Block {
    public static final MapCodec<SnifferEggBlock> CODEC = simpleCodec(SnifferEggBlock::new);
    public static final int MAX_HATCH_LEVEL = 2;
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    private static final int REGULAR_HATCH_TIME_TICKS = 24000;
    private static final int BOOSTED_HATCH_TIME_TICKS = 12000;
    private static final int RANDOM_HATCH_OFFSET_TICKS = 300;
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 2.0, 15.0, 16.0, 14.0);

    @Override
    public MapCodec<SnifferEggBlock> codec() {
        return CODEC;
    }

    public SnifferEggBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, Integer.valueOf(0)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public int getHatchLevel(BlockState state) {
        return state.getValue(HATCH);
    }

    private boolean isReadyToHatch(BlockState state) {
        return this.getHatchLevel(state) == 2;
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!this.isReadyToHatch(state)) {
            world.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
            world.setBlock(pos, state.setValue(HATCH, Integer.valueOf(this.getHatchLevel(state) + 1)), 2);
        } else {
            world.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
            world.destroyBlock(pos, false);
            Sniffer sniffer = EntityType.SNIFFER.create(world);
            if (sniffer != null) {
                Vec3 vec3 = pos.getCenter();
                sniffer.setBaby(true);
                sniffer.moveTo(vec3.x(), vec3.y(), vec3.z(), Mth.wrapDegrees(world.random.nextFloat() * 360.0F), 0.0F);
                world.addFreshEntity(sniffer);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        boolean bl = hatchBoost(world, pos);
        if (!world.isClientSide() && bl) {
            world.levelEvent(3009, pos, 0);
        }

        int i = bl ? 12000 : 24000;
        int j = i / 3;
        world.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(state));
        world.scheduleTick(pos, this, j + world.random.nextInt(300));
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    public static boolean hatchBoost(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos.below()).is(BlockTags.SNIFFER_EGG_HATCH_BOOST);
    }
}