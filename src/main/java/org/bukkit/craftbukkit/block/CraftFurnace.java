package org.bukkit.craftbukkit.block;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Furnace;
import org.bukkit.craftbukkit.inventory.CraftInventoryFurnace;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Recipe;

public abstract class CraftFurnace<T extends AbstractFurnaceBlockEntity> extends CraftContainer<T> implements Furnace {

    public CraftFurnace(World world, T tileEntity) {
        super(world, tileEntity);
    }

    protected CraftFurnace(CraftFurnace<T> state, Location location) {
        super(state, location);
    }

    @Override
    public FurnaceInventory getSnapshotInventory() {
        return new CraftInventoryFurnace(this.getSnapshot());
    }

    @Override
    public FurnaceInventory getInventory() {
        if (!this.isPlaced()) {
            return this.getSnapshotInventory();
        }

        return new CraftInventoryFurnace(this.getTileEntity());
    }

    @Override
    public short getBurnTime() {
        return (short) this.getSnapshot().litTime;
    }

    @Override
    public void setBurnTime(short burnTime) {
        this.getSnapshot().litTime = burnTime;
        // SPIGOT-844: Allow lighting and relighting using this API
        this.data = this.data.setValue(AbstractFurnaceBlock.LIT, burnTime > 0);
    }

    @Override
    public short getCookTime() {
        return (short) this.getSnapshot().cookingProgress;
    }

    @Override
    public void setCookTime(short cookTime) {
        this.getSnapshot().cookingProgress = cookTime;
    }

    @Override
    public int getCookTimeTotal() {
        return this.getSnapshot().cookingTotalTime;
    }

    @Override
    public void setCookTimeTotal(int cookTimeTotal) {
        this.getSnapshot().cookingTotalTime = cookTimeTotal;
    }

    @Override
    public Map<CookingRecipe<?>, Integer> getRecipesUsed() {
        ImmutableMap.Builder<CookingRecipe<?>, Integer> recipesUsed = ImmutableMap.builder();
        for (Map.Entry<ResourceLocation, Integer> entrySet : this.getSnapshot().getRecipesUsed().object2IntEntrySet()) {
            Recipe recipe = Bukkit.getRecipe(CraftNamespacedKey.fromMinecraft(entrySet.getKey()));
            if (recipe instanceof CookingRecipe<?> cookingRecipe) {
                recipesUsed.put(cookingRecipe, entrySet.getValue());
            }
        }

        return recipesUsed.build();
    }

    @Override
    public abstract CraftFurnace<T> copy();

    @Override
    public abstract CraftFurnace<T> copy(Location location);

    // Paper start - cook speed multiplier API
    @Override
    public double getCookSpeedMultiplier() {
        return this.getSnapshot().cookSpeedMultiplier;
    }

    @Override
    public void setCookSpeedMultiplier(double multiplier) {
        com.google.common.base.Preconditions.checkArgument(multiplier >= 0, "Furnace speed multiplier cannot be negative");
        com.google.common.base.Preconditions.checkArgument(multiplier <= 200, "Furnace speed multiplier cannot more than 200");
        T snapshot = this.getSnapshot();
        snapshot.cookSpeedMultiplier = multiplier;
        snapshot.cookingTotalTime = AbstractFurnaceBlockEntity.getTotalCookTime(this.isPlaced() ? this.world.getHandle() : null, snapshot.recipeType, snapshot, snapshot.cookSpeedMultiplier); // Update the snapshot's current total cook time to scale with the newly set multiplier
    }
    // Paper end
}
