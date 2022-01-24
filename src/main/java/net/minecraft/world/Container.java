package net.minecraft.world;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
// CraftBukkit start
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
// CraftBukkit end

public interface Container extends Clearable {

    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int amount);

    ItemStack removeItemNoUpdate(int slot);

    void setItem(int slot, ItemStack stack);

    int getMaxStackSize(); // CraftBukkit

    default int getMaxStackSize(ItemStack stack) {
        return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
    }

    void setChanged();

    boolean stillValid(Player player);

    default void startOpen(Player player) {}

    default void stopOpen(Player player) {}

    default boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    default boolean canTakeItem(Container hopperInventory, int slot, ItemStack stack) {
        return true;
    }

    default int countItem(Item item) {
        int i = 0;

        for (int j = 0; j < this.getContainerSize(); ++j) {
            ItemStack itemstack = this.getItem(j);

            if (itemstack.getItem().equals(item)) {
                i += itemstack.getCount();
            }
        }

        return i;
    }

    default boolean hasAnyOf(Set<Item> items) {
        return this.hasAnyMatching((itemstack) -> {
            return !itemstack.isEmpty() && items.contains(itemstack.getItem());
        });
    }

    default boolean hasAnyMatching(Predicate<ItemStack> predicate) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (predicate.test(itemstack)) {
                return true;
            }
        }

        return false;
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player) {
        return Container.stillValidBlockEntity(blockEntity, player, 4.0F);
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player, float range) {
        Level world = blockEntity.getLevel();
        BlockPos blockposition = blockEntity.getBlockPos();

        return world == null ? false : (world.getBlockEntity(blockposition) != blockEntity ? false : player.canInteractWithBlock(blockposition, (double) range));
    }

    // CraftBukkit start
    java.util.List<ItemStack> getContents();

    void onOpen(CraftHumanEntity who);

    void onClose(CraftHumanEntity who);

    java.util.List<org.bukkit.entity.HumanEntity> getViewers();

    org.bukkit.inventory.@org.jetbrains.annotations.Nullable InventoryHolder getOwner(); // Paper - annotation

    void setMaxStackSize(int size);

    org.bukkit.Location getLocation();

    int MAX_STACK = 99;
    // CraftBukkit end
}
