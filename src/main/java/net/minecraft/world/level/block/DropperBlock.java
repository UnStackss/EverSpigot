package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
// CraftBukkit end

public class DropperBlock extends DispenserBlock {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<DropperBlock> CODEC = simpleCodec(DropperBlock::new);
    private static final DispenseItemBehavior DISPENSE_BEHAVIOUR = new DefaultDispenseItemBehavior(true); // CraftBukkit

    @Override
    public MapCodec<DropperBlock> codec() {
        return DropperBlock.CODEC;
    }

    public DropperBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected DispenseItemBehavior getDispenseMethod(Level world, ItemStack stack) {
        return DropperBlock.DISPENSE_BEHAVIOUR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DropperBlockEntity(pos, state);
    }

    @Override
    public void dispenseFrom(ServerLevel world, BlockState state, BlockPos pos) {
        DispenserBlockEntity tileentitydispenser = (DispenserBlockEntity) world.getBlockEntity(pos, BlockEntityType.DROPPER).orElse(null); // CraftBukkit - decompile error

        if (tileentitydispenser == null) {
            DropperBlock.LOGGER.warn("Ignoring dispensing attempt for Dropper without matching block entity at {}", pos);
        } else {
            BlockSource sourceblock = new BlockSource(world, pos, state, tileentitydispenser);
            int i = tileentitydispenser.getRandomSlot(world.random);

            if (i < 0) {
                world.levelEvent(1001, pos, 0);
            } else {
                ItemStack itemstack = tileentitydispenser.getItem(i);

                if (!itemstack.isEmpty()) {
                    Direction enumdirection = (Direction) world.getBlockState(pos).getValue(DropperBlock.FACING);
                    Container iinventory = HopperBlockEntity.getContainerAt(world, pos.relative(enumdirection));
                    ItemStack itemstack1;

                    if (iinventory == null) {
                        itemstack1 = DropperBlock.DISPENSE_BEHAVIOUR.dispense(sourceblock, itemstack);
                    } else {
                        // CraftBukkit start - Fire event when pushing items into other inventories
                        CraftItemStack oitemstack = CraftItemStack.asCraftMirror(itemstack.copyWithCount(1));

                        org.bukkit.inventory.Inventory destinationInventory;
                        // Have to special case large chests as they work oddly
                        if (iinventory instanceof CompoundContainer) {
                            destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
                        } else {
                            destinationInventory = iinventory.getOwner().getInventory();
                        }

                        InventoryMoveItemEvent event = new InventoryMoveItemEvent(tileentitydispenser.getOwner().getInventory(), oitemstack, destinationInventory, true);
                        world.getCraftServer().getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            return;
                        }
                        itemstack1 = HopperBlockEntity.addItem(tileentitydispenser, iinventory, CraftItemStack.asNMSCopy(event.getItem()), enumdirection.getOpposite());
                        if (event.getItem().equals(oitemstack) && itemstack1.isEmpty()) {
                            // CraftBukkit end
                            itemstack1 = itemstack.copy();
                            itemstack1.shrink(1);
                        } else {
                            itemstack1 = itemstack.copy();
                        }
                    }

                    tileentitydispenser.setItem(i, itemstack1);
                }
            }
        }
    }
}
