package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;

public class JigsawBlock extends Block implements EntityBlock, GameMasterBlock {
    public static final MapCodec<JigsawBlock> CODEC = simpleCodec(JigsawBlock::new);
    public static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;

    @Override
    public MapCodec<JigsawBlock> codec() {
        return CODEC;
    }

    protected JigsawBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(ORIENTATION, FrontAndTop.NORTH_UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ORIENTATION, rotation.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ORIENTATION, mirror.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction direction = ctx.getClickedFace();
        Direction direction2;
        if (direction.getAxis() == Direction.Axis.Y) {
            direction2 = ctx.getHorizontalDirection().getOpposite();
        } else {
            direction2 = Direction.UP;
        }

        return this.defaultBlockState().setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(direction, direction2));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JigsawBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof JigsawBlockEntity && player.canUseGameMasterBlocks()) {
            player.openJigsawBlock((JigsawBlockEntity)blockEntity);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static boolean canAttach(StructureTemplate.StructureBlockInfo info1, StructureTemplate.StructureBlockInfo info2) {
        Direction direction = getFrontFacing(info1.state());
        Direction direction2 = getFrontFacing(info2.state());
        Direction direction3 = getTopFacing(info1.state());
        Direction direction4 = getTopFacing(info2.state());
        JigsawBlockEntity.JointType jointType = JigsawBlockEntity.JointType.byName(info1.nbt().getString("joint"))
            .orElseGet(() -> direction.getAxis().isHorizontal() ? JigsawBlockEntity.JointType.ALIGNED : JigsawBlockEntity.JointType.ROLLABLE);
        boolean bl = jointType == JigsawBlockEntity.JointType.ROLLABLE;
        return direction == direction2.getOpposite()
            && (bl || direction3 == direction4)
            && info1.nbt().getString("target").equals(info2.nbt().getString("name"));
    }

    public static Direction getFrontFacing(BlockState state) {
        return state.getValue(ORIENTATION).front();
    }

    public static Direction getTopFacing(BlockState state) {
        return state.getValue(ORIENTATION).top();
    }
}
