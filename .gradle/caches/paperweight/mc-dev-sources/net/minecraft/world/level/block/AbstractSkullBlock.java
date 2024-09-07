package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;

public abstract class AbstractSkullBlock extends BaseEntityBlock implements Equipable {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final SkullBlock.Type type;

    public AbstractSkullBlock(SkullBlock.Type type, BlockBehaviour.Properties settings) {
        super(settings);
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, Boolean.valueOf(false)));
    }

    @Override
    protected abstract MapCodec<? extends AbstractSkullBlock> codec();

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkullBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide) {
            boolean bl = state.is(Blocks.DRAGON_HEAD) || state.is(Blocks.DRAGON_WALL_HEAD) || state.is(Blocks.PIGLIN_HEAD) || state.is(Blocks.PIGLIN_WALL_HEAD);
            if (bl) {
                return createTickerHelper(type, BlockEntityType.SKULL, SkullBlockEntity::animation);
            }
        }

        return null;
    }

    public SkullBlock.Type getType() {
        return this.type;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(POWERED, Boolean.valueOf(ctx.getLevel().hasNeighborSignal(ctx.getClickedPos())));
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide) {
            boolean bl = world.hasNeighborSignal(pos);
            if (bl != state.getValue(POWERED)) {
                world.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(bl)), 2);
            }
        }
    }
}
