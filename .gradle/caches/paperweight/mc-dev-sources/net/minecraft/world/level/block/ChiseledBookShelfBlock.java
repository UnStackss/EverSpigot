package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class ChiseledBookShelfBlock extends BaseEntityBlock {
    public static final MapCodec<ChiseledBookShelfBlock> CODEC = simpleCodec(ChiseledBookShelfBlock::new);
    private static final int MAX_BOOKS_IN_STORAGE = 6;
    public static final int BOOKS_PER_ROW = 3;
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES = List.of(
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED
    );

    @Override
    public MapCodec<ChiseledBookShelfBlock> codec() {
        return CODEC;
    }

    public ChiseledBookShelfBlock(BlockBehaviour.Properties settings) {
        super(settings);
        BlockState blockState = this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);

        for (BooleanProperty booleanProperty : SLOT_OCCUPIED_PROPERTIES) {
            blockState = blockState.setValue(booleanProperty, Boolean.valueOf(false));
        }

        this.registerDefaultState(blockState);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            if (!stack.is(ItemTags.BOOKSHELF_BOOKS)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            } else {
                OptionalInt optionalInt = this.getHitSlot(hit, state);
                if (optionalInt.isEmpty()) {
                    return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
                } else if (state.getValue(SLOT_OCCUPIED_PROPERTIES.get(optionalInt.getAsInt()))) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                } else {
                    addBook(world, pos, player, chiseledBookShelfBlockEntity, stack, optionalInt.getAsInt());
                    return ItemInteractionResult.sidedSuccess(world.isClientSide);
                }
            }
        } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            OptionalInt optionalInt = this.getHitSlot(hit, state);
            if (optionalInt.isEmpty()) {
                return InteractionResult.PASS;
            } else if (!state.getValue(SLOT_OCCUPIED_PROPERTIES.get(optionalInt.getAsInt()))) {
                return InteractionResult.CONSUME;
            } else {
                removeBook(world, pos, player, chiseledBookShelfBlockEntity, optionalInt.getAsInt());
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    public OptionalInt getHitSlot(BlockHitResult hit, BlockState state) {
        return getRelativeHitCoordinatesForBlockFace(hit, state.getValue(HorizontalDirectionalBlock.FACING)).map(hitPos -> {
            int i = hitPos.y >= 0.5F ? 0 : 1;
            int j = getSection(hitPos.x);
            return OptionalInt.of(j + i * 3);
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult hit, Direction facing) {
        Direction direction = hit.getDirection();
        if (facing != direction) {
            return Optional.empty();
        } else {
            BlockPos blockPos = hit.getBlockPos().relative(direction);
            Vec3 vec3 = hit.getLocation().subtract((double)blockPos.getX(), (double)blockPos.getY(), (double)blockPos.getZ());
            double d = vec3.x();
            double e = vec3.y();
            double f = vec3.z();

            return switch (direction) {
                case NORTH -> Optional.of(new Vec2((float)(1.0 - d), (float)e));
                case SOUTH -> Optional.of(new Vec2((float)d, (float)e));
                case WEST -> Optional.of(new Vec2((float)f, (float)e));
                case EAST -> Optional.of(new Vec2((float)(1.0 - f), (float)e));
                case DOWN, UP -> Optional.empty();
            };
        }
    }

    public static int getSection(float x) {
        float f = 0.0625F;
        float g = 0.375F;
        if (x < 0.375F) {
            return 0;
        } else {
            float h = 0.6875F;
            return x < 0.6875F ? 1 : 2;
        }
    }

    private static void addBook(Level world, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, ItemStack stack, int slot) {
        if (!world.isClientSide) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            SoundEvent soundEvent = stack.is(Items.ENCHANTED_BOOK) ? SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED : SoundEvents.CHISELED_BOOKSHELF_INSERT;
            blockEntity.setItem(slot, stack.consumeAndReturn(1, player));
            world.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void removeBook(Level world, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, int slot) {
        if (!world.isClientSide) {
            ItemStack itemStack = blockEntity.removeItem(slot, 1);
            SoundEvent soundEvent = itemStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_PICKUP;
            world.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getInventory().add(itemStack)) {
                player.drop(itemStack, false);
            }

            world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiseledBookShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
        SLOT_OCCUPIED_PROPERTIES.forEach(property -> builder.add(property));
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            boolean bl;
            label32: {
                if (world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity && !chiseledBookShelfBlockEntity.isEmpty()) {
                    for (int i = 0; i < 6; i++) {
                        ItemStack itemStack = chiseledBookShelfBlockEntity.getItem(i);
                        if (!itemStack.isEmpty()) {
                            Containers.dropItemStack(world, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), itemStack);
                        }
                    }

                    chiseledBookShelfBlockEntity.clearContent();
                    bl = true;
                    break label32;
                }

                bl = false;
            }

            super.onRemove(state, world, pos, newState, moved);
            if (bl) {
                world.updateNeighbourForOutputSignal(pos, this);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        if (world.isClientSide()) {
            return 0;
        } else {
            return world.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity
                ? chiseledBookShelfBlockEntity.getLastInteractedSlot() + 1
                : 0;
        }
    }
}
