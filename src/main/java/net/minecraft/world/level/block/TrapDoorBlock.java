package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class TrapDoorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<TrapDoorBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter((blocktrapdoor) -> {
            return blocktrapdoor.type;
        }), propertiesCodec()).apply(instance, TrapDoorBlock::new);
    });
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final int AABB_THICKNESS = 3;
    protected static final VoxelShape EAST_OPEN_AABB = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_OPEN_AABB = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape SOUTH_OPEN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D);
    protected static final VoxelShape NORTH_OPEN_AABB = Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape BOTTOM_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);
    protected static final VoxelShape TOP_AABB = Block.box(0.0D, 13.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final BlockSetType type;

    @Override
    public MapCodec<? extends TrapDoorBlock> codec() {
        return TrapDoorBlock.CODEC;
    }

    protected TrapDoorBlock(BlockSetType type, BlockBehaviour.Properties settings) {
        super(settings.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TrapDoorBlock.FACING, Direction.NORTH)).setValue(TrapDoorBlock.OPEN, false)).setValue(TrapDoorBlock.HALF, Half.BOTTOM)).setValue(TrapDoorBlock.POWERED, false)).setValue(TrapDoorBlock.WATERLOGGED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(Boolean) state.getValue(TrapDoorBlock.OPEN)) {
            return state.getValue(TrapDoorBlock.HALF) == Half.TOP ? TrapDoorBlock.TOP_AABB : TrapDoorBlock.BOTTOM_AABB;
        } else {
            switch ((Direction) state.getValue(TrapDoorBlock.FACING)) {
                case NORTH:
                default:
                    return TrapDoorBlock.NORTH_OPEN_AABB;
                case SOUTH:
                    return TrapDoorBlock.SOUTH_OPEN_AABB;
                case WEST:
                    return TrapDoorBlock.WEST_OPEN_AABB;
                case EAST:
                    return TrapDoorBlock.EAST_OPEN_AABB;
            }
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        switch (type) {
            case LAND:
                return (Boolean) state.getValue(TrapDoorBlock.OPEN);
            case WATER:
                return (Boolean) state.getValue(TrapDoorBlock.WATERLOGGED);
            case AIR:
                return (Boolean) state.getValue(TrapDoorBlock.OPEN);
            default:
                return false;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!this.type.canOpenByHand()) {
            return InteractionResult.PASS;
        } else {
            this.toggle(state, world, pos, player);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, Level world, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> stackMerger) {
        if (explosion.canTriggerBlocks() && this.type.canOpenByWindCharge() && !(Boolean) state.getValue(TrapDoorBlock.POWERED)) {
            this.toggle(state, world, pos, (Player) null);
        }

        super.onExplosionHit(state, world, pos, explosion, stackMerger);
    }

    private void toggle(BlockState state, Level world, BlockPos pos, @Nullable Player player) {
        BlockState iblockdata1 = (BlockState) state.cycle(TrapDoorBlock.OPEN);

        world.setBlock(pos, iblockdata1, 2);
        if ((Boolean) iblockdata1.getValue(TrapDoorBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        this.playSound(player, world, pos, (Boolean) iblockdata1.getValue(TrapDoorBlock.OPEN));
    }

    protected void playSound(@Nullable Player player, Level world, BlockPos pos, boolean open) {
        world.playSound(player, pos, open ? this.type.trapdoorOpen() : this.type.trapdoorClose(), SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
        world.gameEvent((Entity) player, (Holder) (open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide) {
            boolean flag1 = world.hasNeighborSignal(pos);

            if (flag1 != (Boolean) state.getValue(TrapDoorBlock.POWERED)) {
                // CraftBukkit start
                org.bukkit.World bworld = world.getWorld();
                org.bukkit.block.Block bblock = bworld.getBlockAt(pos.getX(), pos.getY(), pos.getZ());

                int power = bblock.getBlockPower();
                int oldPower = (Boolean) state.getValue(TrapDoorBlock.OPEN) ? 15 : 0;

                if (oldPower == 0 ^ power == 0 || sourceBlock.defaultBlockState().isSignalSource()) {
                    BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bblock, oldPower, power);
                    world.getCraftServer().getPluginManager().callEvent(eventRedstone);
                    flag1 = eventRedstone.getNewCurrent() > 0;
                }
                // CraftBukkit end
                // Paper start - break redstone on trapdoors early
                boolean open = (Boolean) state.getValue(TrapDoorBlock.OPEN) != flag1;
                // note: this must run before any state for this block/its neighborus are written to the world
                // we allow the redstone event to fire so that plugins can block
                if (flag1 && open) { // if we are now powered and it caused the trap door to open
                    // in this case, first check for the redstone on top first
                    BlockPos abovePos = pos.above();
                    BlockState above = world.getBlockState(abovePos);
                    if (above.getBlock() instanceof RedStoneWireBlock) {
                        world.setBlock(abovePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                        Block.popResource(world, abovePos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
                        // now check that this didn't change our state
                        if (world.getBlockState(pos) != state) {
                            // our state was changed, so we cannot propagate this update
                            return;
                        }
                    }
                }
                if (open) {
                // Paper end - break redstone on trapdoors early
                    state = (BlockState) state.setValue(TrapDoorBlock.OPEN, flag1);
                    this.playSound((Player) null, world, pos, flag1);
                }

                world.setBlock(pos, (BlockState) state.setValue(TrapDoorBlock.POWERED, flag1), 2);
                if ((Boolean) state.getValue(TrapDoorBlock.WATERLOGGED)) {
                    world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
                }
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState iblockdata = this.defaultBlockState();
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        Direction enumdirection = ctx.getClickedFace();

        if (!ctx.replacingClickedOnBlock() && enumdirection.getAxis().isHorizontal()) {
            iblockdata = (BlockState) ((BlockState) iblockdata.setValue(TrapDoorBlock.FACING, enumdirection)).setValue(TrapDoorBlock.HALF, ctx.getClickLocation().y - (double) ctx.getClickedPos().getY() > 0.5D ? Half.TOP : Half.BOTTOM);
        } else {
            iblockdata = (BlockState) ((BlockState) iblockdata.setValue(TrapDoorBlock.FACING, ctx.getHorizontalDirection().getOpposite())).setValue(TrapDoorBlock.HALF, enumdirection == Direction.UP ? Half.BOTTOM : Half.TOP);
        }

        if (ctx.getLevel().hasNeighborSignal(ctx.getClickedPos())) {
            iblockdata = (BlockState) ((BlockState) iblockdata.setValue(TrapDoorBlock.OPEN, true)).setValue(TrapDoorBlock.POWERED, true);
        }

        return (BlockState) iblockdata.setValue(TrapDoorBlock.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TrapDoorBlock.FACING, TrapDoorBlock.OPEN, TrapDoorBlock.HALF, TrapDoorBlock.POWERED, TrapDoorBlock.WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(TrapDoorBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(TrapDoorBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    protected BlockSetType getType() {
        return this.type;
    }
}
