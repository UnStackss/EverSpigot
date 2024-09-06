package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallHangingSignBlock extends SignBlock {

    public static final MapCodec<WallHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec()).apply(instance, WallHangingSignBlock::new);
    });
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final VoxelShape PLANK_NORTHSOUTH = Block.box(0.0D, 14.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    public static final VoxelShape PLANK_EASTWEST = Block.box(6.0D, 14.0D, 0.0D, 10.0D, 16.0D, 16.0D);
    public static final VoxelShape SHAPE_NORTHSOUTH = Shapes.or(WallHangingSignBlock.PLANK_NORTHSOUTH, Block.box(1.0D, 0.0D, 7.0D, 15.0D, 10.0D, 9.0D));
    public static final VoxelShape SHAPE_EASTWEST = Shapes.or(WallHangingSignBlock.PLANK_EASTWEST, Block.box(7.0D, 0.0D, 1.0D, 9.0D, 10.0D, 15.0D));
    private static final Map<Direction, VoxelShape> AABBS = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, WallHangingSignBlock.SHAPE_NORTHSOUTH, Direction.SOUTH, WallHangingSignBlock.SHAPE_NORTHSOUTH, Direction.EAST, WallHangingSignBlock.SHAPE_EASTWEST, Direction.WEST, WallHangingSignBlock.SHAPE_EASTWEST));

    @Override
    public MapCodec<WallHangingSignBlock> codec() {
        return WallHangingSignBlock.CODEC;
    }

    public WallHangingSignBlock(WoodType type, BlockBehaviour.Properties settings) {
        super(type, settings.sound(type.hangingSignSoundType()));
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(WallHangingSignBlock.FACING, Direction.NORTH)).setValue(WallHangingSignBlock.WATERLOGGED, false));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof SignBlockEntity tileentitysign) {
            if (this.shouldTryToChainAnotherHangingSign(state, player, hit, tileentitysign, stack)) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
        }

        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }

    private boolean shouldTryToChainAnotherHangingSign(BlockState state, Player player, BlockHitResult hitResult, SignBlockEntity sign, ItemStack stack) {
        return !sign.canExecuteClickCommands(sign.isFacingFrontText(player), player) && stack.getItem() instanceof HangingSignItem && !this.isHittingEditableSide(hitResult, state);
    }

    private boolean isHittingEditableSide(BlockHitResult hitResult, BlockState state) {
        return hitResult.getDirection().getAxis() == ((Direction) state.getValue(WallHangingSignBlock.FACING)).getAxis();
    }

    @Override
    public String getDescriptionId() {
        return this.asItem().getDescriptionId();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (VoxelShape) WallHangingSignBlock.AABBS.get(state.getValue(WallHangingSignBlock.FACING));
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return this.getShape(state, world, pos, CollisionContext.empty());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        switch ((Direction) state.getValue(WallHangingSignBlock.FACING)) {
            case EAST:
            case WEST:
                return WallHangingSignBlock.PLANK_EASTWEST;
            default:
                return WallHangingSignBlock.PLANK_NORTHSOUTH;
        }
    }

    public boolean canPlace(BlockState state, LevelReader world, BlockPos pos) {
        Direction enumdirection = ((Direction) state.getValue(WallHangingSignBlock.FACING)).getClockWise();
        Direction enumdirection1 = ((Direction) state.getValue(WallHangingSignBlock.FACING)).getCounterClockWise();

        return this.canAttachTo(world, state, pos.relative(enumdirection), enumdirection1) || this.canAttachTo(world, state, pos.relative(enumdirection1), enumdirection);
    }

    public boolean canAttachTo(LevelReader world, BlockState state, BlockPos toPos, Direction direction) {
        BlockState iblockdata1 = world.getBlockState(toPos);

        return iblockdata1.is(BlockTags.WALL_HANGING_SIGNS) ? ((Direction) iblockdata1.getValue(WallHangingSignBlock.FACING)).getAxis().test((Direction) state.getValue(WallHangingSignBlock.FACING)) : iblockdata1.isFaceSturdy(world, toPos, direction, SupportType.FULL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState iblockdata = this.defaultBlockState();
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        Direction[] aenumdirection = ctx.getNearestLookingDirections();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (enumdirection.getAxis().isHorizontal() && !enumdirection.getAxis().test(ctx.getClickedFace())) {
                Direction enumdirection1 = enumdirection.getOpposite();

                iblockdata = (BlockState) iblockdata.setValue(WallHangingSignBlock.FACING, enumdirection1);
                if (iblockdata.canSurvive(world, blockposition) && this.canPlace(iblockdata, world, blockposition)) {
                    return (BlockState) iblockdata.setValue(WallHangingSignBlock.WATERLOGGED, fluid.getType() == Fluids.WATER);
                }
            }
        }

        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis() == ((Direction) state.getValue(WallHangingSignBlock.FACING)).getClockWise().getAxis() && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return ((Direction) state.getValue(WallHangingSignBlock.FACING)).toYRot();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(WallHangingSignBlock.FACING, rotation.rotate((Direction) state.getValue(WallHangingSignBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(WallHangingSignBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WallHangingSignBlock.FACING, WallHangingSignBlock.WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangingSignBlockEntity(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return null; // Craftbukkit - remove unnecessary sign ticking
    }
}
