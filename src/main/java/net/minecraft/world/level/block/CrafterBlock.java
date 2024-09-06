package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Iterator;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
// CraftBukkit end

public class CrafterBlock extends BaseEntityBlock {

    public static final MapCodec<CrafterBlock> CODEC = simpleCodec(CrafterBlock::new);
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
    private static final int MAX_CRAFTING_TICKS = 6;
    private static final int CRAFTING_TICK_DELAY = 4;
    private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);
    private static final int CRAFTER_ADVANCEMENT_DIAMETER = 17;

    public CrafterBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CrafterBlock.ORIENTATION, FrontAndTop.NORTH_UP)).setValue(CrafterBlock.TRIGGERED, false)).setValue(CrafterBlock.CRAFTING, false));
    }

    @Override
    protected MapCodec<CrafterBlock> codec() {
        return CrafterBlock.CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CrafterBlockEntity crafterblockentity) {
            return crafterblockentity.getRedstoneSignal();
        } else {
            return 0;
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        boolean flag1 = world.hasNeighborSignal(pos);
        boolean flag2 = (Boolean) state.getValue(CrafterBlock.TRIGGERED);
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (flag1 && !flag2) {
            world.scheduleTick(pos, (Block) this, 4);
            world.setBlock(pos, (BlockState) state.setValue(CrafterBlock.TRIGGERED, true), 2);
            this.setBlockEntityTriggered(tileentity, true);
        } else if (!flag1 && flag2) {
            world.setBlock(pos, (BlockState) ((BlockState) state.setValue(CrafterBlock.TRIGGERED, false)).setValue(CrafterBlock.CRAFTING, false), 2);
            this.setBlockEntityTriggered(tileentity, false);
        }

    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.dispenseFrom(state, world, pos);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? null : createTickerHelper(type, BlockEntityType.CRAFTER, CrafterBlockEntity::serverTick);
    }

    private void setBlockEntityTriggered(@Nullable BlockEntity blockEntity, boolean triggered) {
        if (blockEntity instanceof CrafterBlockEntity crafterblockentity) {
            crafterblockentity.setTriggered(triggered);
        }

    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CrafterBlockEntity crafterblockentity = new CrafterBlockEntity(pos, state);

        crafterblockentity.setTriggered(state.hasProperty(CrafterBlock.TRIGGERED) && (Boolean) state.getValue(CrafterBlock.TRIGGERED));
        return crafterblockentity;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction enumdirection = ctx.getNearestLookingDirection().getOpposite();
        Direction enumdirection1;

        switch (enumdirection) {
            case DOWN:
                enumdirection1 = ctx.getHorizontalDirection().getOpposite();
                break;
            case UP:
                enumdirection1 = ctx.getHorizontalDirection();
                break;
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                enumdirection1 = Direction.UP;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Direction enumdirection2 = enumdirection1;

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(CrafterBlock.ORIENTATION, FrontAndTop.fromFrontAndTop(enumdirection, enumdirection2))).setValue(CrafterBlock.TRIGGERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if ((Boolean) state.getValue(CrafterBlock.TRIGGERED)) {
            world.scheduleTick(pos, (Block) this, 4);
        }

    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        Containers.dropContentsOnDestroy(state, newState, world, pos);
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof CrafterBlockEntity) {
                player.openMenu((CrafterBlockEntity) tileentity);
            }

            return InteractionResult.CONSUME;
        }
    }

    protected void dispenseFrom(BlockState state, ServerLevel world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CrafterBlockEntity crafterblockentity) {
            CraftingInput craftinginput = crafterblockentity.asCraftInput();
            Optional optional = CrafterBlock.getPotentialResults(world, craftinginput);

            if (optional.isEmpty()) {
                world.levelEvent(1050, pos, 0);
            } else {
                RecipeHolder<CraftingRecipe> recipeholder = (RecipeHolder) optional.get();
                ItemStack itemstack = ((CraftingRecipe) recipeholder.value()).assemble(craftinginput, world.registryAccess());

                // CraftBukkit start
                CrafterCraftEvent event = CraftEventFactory.callCrafterCraftEvent(pos, world, crafterblockentity, itemstack, recipeholder);
                if (event.isCancelled()) {
                    return;
                }
                itemstack = CraftItemStack.asNMSCopy(event.getResult());
                // CraftBukkit end
                if (itemstack.isEmpty()) {
                    world.levelEvent(1050, pos, 0);
                } else {
                    crafterblockentity.setCraftingTicksRemaining(6);
                    world.setBlock(pos, (BlockState) state.setValue(CrafterBlock.CRAFTING, true), 2);
                    itemstack.onCraftedBySystem(world);
                    this.dispenseItem(world, pos, crafterblockentity, itemstack, state, recipeholder);
                    Iterator iterator = ((CraftingRecipe) recipeholder.value()).getRemainingItems(craftinginput).iterator();

                    while (iterator.hasNext()) {
                        ItemStack itemstack1 = (ItemStack) iterator.next();

                        if (!itemstack1.isEmpty()) {
                            this.dispenseItem(world, pos, crafterblockentity, itemstack1, state, recipeholder);
                        }
                    }

                    crafterblockentity.getItems().forEach((itemstack2) -> {
                        if (!itemstack2.isEmpty()) {
                            itemstack2.shrink(1);
                        }
                    });
                    crafterblockentity.setChanged();
                }
            }
        }
    }

    public static Optional<RecipeHolder<CraftingRecipe>> getPotentialResults(Level world, CraftingInput input) {
        return CrafterBlock.RECIPE_CACHE.get(world, input);
    }

    private void dispenseItem(ServerLevel world, BlockPos pos, CrafterBlockEntity blockEntity, ItemStack stack, BlockState state, RecipeHolder<CraftingRecipe> recipe) {
        Direction enumdirection = ((FrontAndTop) state.getValue(CrafterBlock.ORIENTATION)).front();
        Container iinventory = HopperBlockEntity.getContainerAt(world, pos.relative(enumdirection));
        ItemStack itemstack1 = stack.copy();

        if (iinventory != null && (iinventory instanceof CrafterBlockEntity || stack.getCount() > iinventory.getMaxStackSize(stack))) {
            // CraftBukkit start - InventoryMoveItemEvent
            CraftItemStack oitemstack = CraftItemStack.asCraftMirror(itemstack1);

            Inventory destinationInventory;
            // Have to special case large chests as they work oddly
            if (iinventory instanceof CompoundContainer) {
                destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
            } else {
                destinationInventory = iinventory.getOwner().getInventory();
            }

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(blockEntity.getOwner().getInventory(), oitemstack, destinationInventory, true);
            world.getCraftServer().getPluginManager().callEvent(event);
            itemstack1 = CraftItemStack.asNMSCopy(event.getItem());
            while (!itemstack1.isEmpty()) {
                if (event.isCancelled()) {
                    break;
                }
                // CraftBukkit end
                ItemStack itemstack2 = itemstack1.copyWithCount(1);
                ItemStack itemstack3 = HopperBlockEntity.addItem(blockEntity, iinventory, itemstack2, enumdirection.getOpposite());

                if (!itemstack3.isEmpty()) {
                    break;
                }

                itemstack1.shrink(1);
            }
        } else if (iinventory != null) {
            // CraftBukkit start - InventoryMoveItemEvent
            CraftItemStack oitemstack = CraftItemStack.asCraftMirror(itemstack1);

            Inventory destinationInventory;
            // Have to special case large chests as they work oddly
            if (iinventory instanceof CompoundContainer) {
                destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
            } else {
                destinationInventory = iinventory.getOwner().getInventory();
            }

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(blockEntity.getOwner().getInventory(), oitemstack, destinationInventory, true);
            world.getCraftServer().getPluginManager().callEvent(event);
            itemstack1 = CraftItemStack.asNMSCopy(event.getItem());
            while (!itemstack1.isEmpty()) {
                if (event.isCancelled()) {
                    break;
                }
                // CraftBukkit end
                int i = itemstack1.getCount();

                itemstack1 = HopperBlockEntity.addItem(blockEntity, iinventory, itemstack1, enumdirection.getOpposite());
                if (i == itemstack1.getCount()) {
                    break;
                }
            }
        }

        if (!itemstack1.isEmpty()) {
            Vec3 vec3d = Vec3.atCenterOf(pos);
            Vec3 vec3d1 = vec3d.relative(enumdirection, 0.7D);

            DefaultDispenseItemBehavior.spawnItem(world, itemstack1, 6, enumdirection, vec3d1);
            Iterator iterator = world.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(vec3d, 17.0D, 17.0D, 17.0D)).iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(entityplayer, recipe.id(), blockEntity.getItems());
            }

            world.levelEvent(1049, pos, 0);
            world.levelEvent(2010, pos, enumdirection.get3DDataValue());
        }

    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CrafterBlock.ORIENTATION, rotation.rotation().rotate((FrontAndTop) state.getValue(CrafterBlock.ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) state.setValue(CrafterBlock.ORIENTATION, mirror.rotation().rotate((FrontAndTop) state.getValue(CrafterBlock.ORIENTATION)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CrafterBlock.ORIENTATION, CrafterBlock.TRIGGERED, CrafterBlock.CRAFTING);
    }
}
