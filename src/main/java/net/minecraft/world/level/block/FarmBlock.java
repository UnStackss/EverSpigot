package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class FarmBlock extends Block {

    public static final MapCodec<FarmBlock> CODEC = simpleCodec(FarmBlock::new);
    public static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);
    public static final int MAX_MOISTURE = 7;

    @Override
    public MapCodec<FarmBlock> codec() {
        return FarmBlock.CODEC;
    }

    protected FarmBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(FarmBlock.MOISTURE, 0));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && !state.canSurvive(world, pos)) {
            world.scheduleTick(pos, (Block) this, 1);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockState iblockdata1 = world.getBlockState(pos.above());

        return !iblockdata1.isSolid() || iblockdata1.getBlock() instanceof FenceGateBlock || iblockdata1.getBlock() instanceof MovingPistonBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return !this.defaultBlockState().canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? Blocks.DIRT.defaultBlockState() : super.getStateForPlacement(ctx);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FarmBlock.SHAPE;
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(world, pos)) {
            FarmBlock.turnToDirt((Entity) null, state, world, pos);
        }

    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        int i = (Integer) state.getValue(FarmBlock.MOISTURE);

        if (!FarmBlock.isNearWater(world, pos) && !world.isRainingAt(pos.above())) {
            if (i > 0) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(world, pos, (BlockState) state.setValue(FarmBlock.MOISTURE, i - 1), 2); // CraftBukkit
            } else if (!FarmBlock.shouldMaintainFarmland(world, pos)) {
                FarmBlock.turnToDirt((Entity) null, state, world, pos);
            }
        } else if (i < 7) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(world, pos, (BlockState) state.setValue(FarmBlock.MOISTURE, 7), 2); // CraftBukkit
        }

    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(world, state, pos, entity, fallDistance); // CraftBukkit - moved here as game rules / events shouldn't affect fall damage.
        if (!world.isClientSide && world.random.nextFloat() < fallDistance - 0.5F && entity instanceof LivingEntity && (entity instanceof Player || world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) && entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() > 0.512F) {
            // CraftBukkit start - Interact soil
            org.bukkit.event.Cancellable cancellable;
            if (entity instanceof Player) {
                cancellable = CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                world.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            if (cancellable.isCancelled()) {
                return;
            }

            if (!CraftEventFactory.callEntityChangeBlockEvent(entity, pos, Blocks.DIRT.defaultBlockState())) {
                return;
            }
            // CraftBukkit end
            FarmBlock.turnToDirt(entity, state, world, pos);
        }

        // super.fallOn(world, iblockdata, blockposition, entity, f); // CraftBukkit - moved up
    }

    public static void turnToDirt(@Nullable Entity entity, BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        if (CraftEventFactory.callBlockFadeEvent(world, pos, Blocks.DIRT.defaultBlockState()).isCancelled()) {
            return;
        }
        // CraftBukkit end
        BlockState iblockdata1 = pushEntitiesUp(state, Blocks.DIRT.defaultBlockState(), world, pos);

        world.setBlockAndUpdate(pos, iblockdata1);
        world.gameEvent((Holder) GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, iblockdata1));
    }

    private static boolean shouldMaintainFarmland(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos.above()).is(BlockTags.MAINTAINS_FARMLAND);
    }

    private static boolean isNearWater(LevelReader world, BlockPos pos) {
        Iterator iterator = BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4)).iterator();

        BlockPos blockposition1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            blockposition1 = (BlockPos) iterator.next();
        } while (!world.getFluidState(blockposition1).is(FluidTags.WATER));

        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FarmBlock.MOISTURE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}