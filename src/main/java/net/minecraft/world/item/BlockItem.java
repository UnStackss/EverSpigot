package net.minecraft.world.item;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockCanBuildEvent;
// CraftBukkit end

public class BlockItem extends Item {

    /** @deprecated */
    @Deprecated
    private final Block block;

    public BlockItem(Block block, Item.Properties settings) {
        super(settings);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult enuminteractionresult = this.place(new BlockPlaceContext(context));

        if (!enuminteractionresult.consumesAction() && context.getItemInHand().has(DataComponents.FOOD)) {
            InteractionResult enuminteractionresult1 = super.use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();

            return enuminteractionresult1 == InteractionResult.CONSUME ? InteractionResult.CONSUME_PARTIAL : enuminteractionresult1;
        } else {
            return enuminteractionresult;
        }
    }

    public InteractionResult place(BlockPlaceContext context) {
        if (!this.getBlock().isEnabled(context.getLevel().enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (!context.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockactioncontext1 = this.updatePlacementContext(context);

            if (blockactioncontext1 == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState iblockdata = this.getPlacementState(blockactioncontext1);
                // CraftBukkit start - special case for handling block placement with water lilies and snow buckets
                org.bukkit.block.BlockState blockstate = null;
                if (this instanceof PlaceOnWaterBlockItem || this instanceof SolidBucketItem) {
                    blockstate = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockactioncontext1.getLevel(), blockactioncontext1.getClickedPos());
                }
                final org.bukkit.block.BlockState oldBlockstate = blockstate != null ? blockstate : org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockactioncontext1.getLevel(), blockactioncontext1.getClickedPos()); // Paper - Reset placed block on exception
                // CraftBukkit end

                if (iblockdata == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockactioncontext1, iblockdata)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos blockposition = blockactioncontext1.getClickedPos();
                    Level world = blockactioncontext1.getLevel();
                    Player entityhuman = blockactioncontext1.getPlayer();
                    ItemStack itemstack = blockactioncontext1.getItemInHand();
                    BlockState iblockdata1 = world.getBlockState(blockposition);

                    if (iblockdata1.is(iblockdata.getBlock())) {
                        iblockdata1 = this.updateBlockStateFromTag(blockposition, world, itemstack, iblockdata1);
                        // Paper start - Reset placed block on exception
                        try {
                        this.updateCustomBlockEntityTag(blockposition, world, entityhuman, itemstack, iblockdata1);
                        BlockItem.updateBlockEntityComponents(world, blockposition, itemstack);
                        } catch (Exception e) {
                            oldBlockstate.update(true, false);
                            if (entityhuman instanceof ServerPlayer player) {
                                org.apache.logging.log4j.LogManager.getLogger().error("Player {} tried placing invalid block", player.getScoreboardName(), e);
                                player.getBukkitEntity().kickPlayer("Packet processing error");
                                return InteractionResult.FAIL;
                            }
                            throw e; // Rethrow exception if not placed by a player
                        }
                        // Paper end - Reset placed block on exception
                        iblockdata1.getBlock().setPlacedBy(world, blockposition, iblockdata1, entityhuman, itemstack);
                        // CraftBukkit start
                        if (blockstate != null) {
                            org.bukkit.event.block.BlockPlaceEvent placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent((ServerLevel) world, entityhuman, blockactioncontext1.getHand(), blockstate, blockposition.getX(), blockposition.getY(), blockposition.getZ());
                            if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                                blockstate.update(true, false);

                                if (this instanceof SolidBucketItem) {
                                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory(); // SPIGOT-4541
                                }
                                return InteractionResult.FAIL;
                            }
                        }
                        // CraftBukkit end
                        if (entityhuman instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) entityhuman, blockposition, itemstack);
                        }
                    }

                    SoundType soundeffecttype = iblockdata1.getSoundType();

                    if (entityhuman == null) world.playSound(entityhuman, blockposition, this.getPlaceSound(iblockdata1), net.minecraft.sounds.SoundSource.BLOCKS, (soundeffecttype.getVolume() + 1.0F) / 2.0F, soundeffecttype.getPitch() * 0.8F); // Paper - Fix block place logic; reintroduce this for the dispenser (i.e the shulker)
                    world.gameEvent((Holder) GameEvent.BLOCK_PLACE, blockposition, GameEvent.Context.of(entityhuman, iblockdata1));
                    itemstack.consume(1, entityhuman);
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            }
        }
    }

    protected SoundEvent getPlaceSound(BlockState state) {
        return state.getSoundType().getPlaceSound();
    }

    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return context;
    }

    private static void updateBlockEntityComponents(Level world, BlockPos pos, ItemStack stack) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity != null) {
            tileentity.applyComponentsFromItemStack(stack);
            tileentity.setChanged();
        }

    }

    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, @Nullable Player player, ItemStack stack, BlockState state) {
        return BlockItem.updateCustomBlockEntityTag(world, player, pos, stack);
    }

    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState iblockdata = this.getBlock().getStateForPlacement(context);

        return iblockdata != null && this.canPlace(context, iblockdata) ? iblockdata : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos pos, Level world, ItemStack stack, BlockState state) {
        BlockItemStateProperties blockitemstateproperties = (BlockItemStateProperties) stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);

        if (blockitemstateproperties.isEmpty()) {
            return state;
        } else {
            BlockState iblockdata1 = blockitemstateproperties.apply(state);

            if (iblockdata1 != state) {
                world.setBlock(pos, iblockdata1, 2);
            }

            return iblockdata1;
        }
    }

    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        Player entityhuman = context.getPlayer();
        CollisionContext voxelshapecollision = entityhuman == null ? CollisionContext.empty() : CollisionContext.of(entityhuman);
        // CraftBukkit start - store default return
        Level world = context.getLevel(); // Paper - Cancel hit for vanished players
        boolean defaultReturn = (!this.mustSurvive() || state.canSurvive(context.getLevel(), context.getClickedPos())) && world.checkEntityCollision(state, entityhuman, voxelshapecollision, context.getClickedPos(), true); // Paper - Cancel hit for vanished players
        org.bukkit.entity.Player player = (context.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) context.getPlayer().getBukkitEntity() : null;

        BlockCanBuildEvent event = new BlockCanBuildEvent(CraftBlock.at(context.getLevel(), context.getClickedPos()), player, CraftBlockData.fromData(state), defaultReturn);
        context.getLevel().getCraftServer().getPluginManager().callEvent(event);

        return event.isBuildable();
        // CraftBukkit end
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return context.getLevel().setBlock(context.getClickedPos(), state, 11);
    }

    public static boolean updateCustomBlockEntityTag(Level world, @Nullable Player player, BlockPos pos, ItemStack stack) {
        MinecraftServer minecraftserver = world.getServer();

        if (minecraftserver == null) {
            return false;
        } else {
            CustomData customdata = (CustomData) stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);

            if (!customdata.isEmpty()) {
                BlockEntity tileentity = world.getBlockEntity(pos);

                if (tileentity != null) {
                    if (!world.isClientSide && tileentity.onlyOpCanSetNbt() && (player == null || !(player.canUseGameMasterBlocks() || (player.getAbilities().instabuild && player.getBukkitEntity().hasPermission("minecraft.nbt.place"))))) { // Spigot - add permission
                        return false;
                    }

                    return customdata.loadInto(tileentity, world.registryAccess());
                }
            }

            return false;
        }
    }

    @Override
    public String getDescriptionId() {
        return this.getBlock().getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        super.appendHoverText(stack, context, tooltip, type);
        this.getBlock().appendHoverText(stack, context, tooltip, type);
    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> map, Item item) {
        map.put(this.getBlock(), item);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return !(this.getBlock() instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        ItemContainerContents itemcontainercontents = (ItemContainerContents) entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        if (itemcontainercontents != null) {
            ItemUtils.onContainerDestroyed(entity, itemcontainercontents.nonEmptyItemsCopy());
        }

    }

    public static void setBlockEntityData(ItemStack stack, BlockEntityType<?> type, CompoundTag nbt) {
        nbt.remove("id");
        if (nbt.isEmpty()) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        } else {
            BlockEntity.addEntityType(nbt, type);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        }

    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.getBlock().requiredFeatures();
    }
}
