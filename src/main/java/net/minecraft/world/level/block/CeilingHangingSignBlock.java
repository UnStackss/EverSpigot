package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CeilingHangingSignBlock extends SignBlock {

    public static final MapCodec<CeilingHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec()).apply(instance, CeilingHangingSignBlock::new);
    });
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    protected static final float AABB_OFFSET = 5.0F;
    protected static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    private static final Map<Integer, VoxelShape> AABBS = Maps.newHashMap(ImmutableMap.of(0, Block.box(1.0D, 0.0D, 7.0D, 15.0D, 10.0D, 9.0D), 4, Block.box(7.0D, 0.0D, 1.0D, 9.0D, 10.0D, 15.0D), 8, Block.box(1.0D, 0.0D, 7.0D, 15.0D, 10.0D, 9.0D), 12, Block.box(7.0D, 0.0D, 1.0D, 9.0D, 10.0D, 15.0D)));

    @Override
    public MapCodec<CeilingHangingSignBlock> codec() {
        return CeilingHangingSignBlock.CODEC;
    }

    public CeilingHangingSignBlock(WoodType type, BlockBehaviour.Properties settings) {
        super(type, settings.sound(type.hangingSignSoundType()));
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CeilingHangingSignBlock.ROTATION, 0)).setValue(CeilingHangingSignBlock.ATTACHED, false)).setValue(CeilingHangingSignBlock.WATERLOGGED, false));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof SignBlockEntity tileentitysign) {
            if (this.shouldTryToChainAnotherHangingSign(player, hit, tileentitysign, stack)) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
        }

        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }

    private boolean shouldTryToChainAnotherHangingSign(Player player, BlockHitResult hitResult, SignBlockEntity sign, ItemStack stack) {
        return !sign.canExecuteClickCommands(sign.isFacingFrontText(player), player) && stack.getItem() instanceof HangingSignItem && hitResult.getDirection().equals(Direction.DOWN);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return world.getBlockState(pos.above()).isFaceSturdy(world, pos.above(), Direction.DOWN, SupportType.CENTER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        FluidState fluid = world.getFluidState(ctx.getClickedPos());
        BlockPos blockposition = ctx.getClickedPos().above();
        BlockState iblockdata = world.getBlockState(blockposition);
        boolean flag = iblockdata.is(BlockTags.ALL_HANGING_SIGNS);
        Direction enumdirection = Direction.fromYRot((double) ctx.getRotation());
        boolean flag1 = !Block.isFaceFull(iblockdata.getCollisionShape(world, blockposition), Direction.DOWN) || ctx.isSecondaryUseActive();

        if (flag && !ctx.isSecondaryUseActive()) {
            if (iblockdata.hasProperty(WallHangingSignBlock.FACING)) {
                Direction enumdirection1 = (Direction) iblockdata.getValue(WallHangingSignBlock.FACING);

                if (enumdirection1.getAxis().test(enumdirection)) {
                    flag1 = false;
                }
            } else if (iblockdata.hasProperty(CeilingHangingSignBlock.ROTATION)) {
                Optional<Direction> optional = RotationSegment.convertToDirection((Integer) iblockdata.getValue(CeilingHangingSignBlock.ROTATION));

                if (optional.isPresent() && ((Direction) optional.get()).getAxis().test(enumdirection)) {
                    flag1 = false;
                }
            }
        }

        int i = !flag1 ? RotationSegment.convertToSegment(enumdirection.getOpposite()) : RotationSegment.convertToSegment(ctx.getRotation() + 180.0F);

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(CeilingHangingSignBlock.ATTACHED, flag1)).setValue(CeilingHangingSignBlock.ROTATION, i)).setValue(CeilingHangingSignBlock.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape = (VoxelShape) CeilingHangingSignBlock.AABBS.get(state.getValue(CeilingHangingSignBlock.ROTATION));

        return voxelshape == null ? CeilingHangingSignBlock.SHAPE : voxelshape;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return this.getShape(state, world, pos, CollisionContext.empty());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.UP && !this.canSurvive(state, world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return RotationSegment.convertToDegrees((Integer) state.getValue(CeilingHangingSignBlock.ROTATION));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CeilingHangingSignBlock.ROTATION, rotation.rotate((Integer) state.getValue(CeilingHangingSignBlock.ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) state.setValue(CeilingHangingSignBlock.ROTATION, mirror.mirror((Integer) state.getValue(CeilingHangingSignBlock.ROTATION), 16));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CeilingHangingSignBlock.ROTATION, CeilingHangingSignBlock.ATTACHED, CeilingHangingSignBlock.WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangingSignBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return null; // Craftbukkit - remove unnecessary sign ticking
    }
}
