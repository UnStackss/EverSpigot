package net.minecraft.world.level.block.entity;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.inventory.CampfireRecipe;
// CraftBukkit end

public class CampfireBlockEntity extends BlockEntity implements Clearable {

    private static final int BURN_COOL_SPEED = 2;
    private static final int NUM_SLOTS = 4;
    private final NonNullList<ItemStack> items;
    public final int[] cookingProgress;
    public final int[] cookingTime;
    private final RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> quickCheck;

    public CampfireBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CAMPFIRE, pos, state);
        this.items = NonNullList.withSize(4, ItemStack.EMPTY);
        this.cookingProgress = new int[4];
        this.cookingTime = new int[4];
        this.quickCheck = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
    }

    public static void cookTick(Level world, BlockPos pos, BlockState state, CampfireBlockEntity campfire) {
        boolean flag = false;

        for (int i = 0; i < campfire.items.size(); ++i) {
            ItemStack itemstack = (ItemStack) campfire.items.get(i);

            if (!itemstack.isEmpty()) {
                flag = true;
                int j = campfire.cookingProgress[i]++;

                if (campfire.cookingProgress[i] >= campfire.cookingTime[i]) {
                    SingleRecipeInput singlerecipeinput = new SingleRecipeInput(itemstack);
                    ItemStack itemstack1 = (ItemStack) campfire.quickCheck.getRecipeFor(singlerecipeinput, world).map((recipeholder) -> {
                        return ((CampfireCookingRecipe) recipeholder.value()).assemble(singlerecipeinput, world.registryAccess());
                    }).orElse(itemstack);

                    if (itemstack1.isItemEnabled(world.enabledFeatures())) {
                        // CraftBukkit start - fire BlockCookEvent
                        CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
                        org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);

                        BlockCookEvent blockCookEvent = new BlockCookEvent(CraftBlock.at(world, pos), source, result);
                        world.getCraftServer().getPluginManager().callEvent(blockCookEvent);

                        if (blockCookEvent.isCancelled()) {
                            return;
                        }

                        result = blockCookEvent.getResult();
                        itemstack1 = CraftItemStack.asNMSCopy(result);
                        // CraftBukkit end
                        // Paper start - Fix item locations dropped from campfires
                        double deviation = 0.05F * RandomSource.GAUSSIAN_SPREAD_FACTOR;
                        while (!itemstack1.isEmpty()) {
                            net.minecraft.world.entity.item.ItemEntity droppedItem = new net.minecraft.world.entity.item.ItemEntity(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, itemstack1.split(world.random.nextInt(21) + 10));
                            droppedItem.setDeltaMovement(world.random.triangle(0.0D, deviation), world.random.triangle(0.2D, deviation), world.random.triangle(0.0D, deviation));
                            world.addFreshEntity(droppedItem);
                        }
                        // Paper end - Fix item locations dropped from campfires
                        campfire.items.set(i, ItemStack.EMPTY);
                        world.sendBlockUpdated(pos, state, state, 3);
                        world.gameEvent((Holder) GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
                    }
                }
            }
        }

        if (flag) {
            setChanged(world, pos, state);
        }

    }

    public static void cooldownTick(Level world, BlockPos pos, BlockState state, CampfireBlockEntity campfire) {
        boolean flag = false;

        for (int i = 0; i < campfire.items.size(); ++i) {
            if (campfire.cookingProgress[i] > 0) {
                flag = true;
                campfire.cookingProgress[i] = Mth.clamp(campfire.cookingProgress[i] - 2, 0, campfire.cookingTime[i]);
            }
        }

        if (flag) {
            setChanged(world, pos, state);
        }

    }

    public static void particleTick(Level world, BlockPos pos, BlockState state, CampfireBlockEntity campfire) {
        RandomSource randomsource = world.random;
        int i;

        if (randomsource.nextFloat() < 0.11F) {
            for (i = 0; i < randomsource.nextInt(2) + 2; ++i) {
                CampfireBlock.makeParticles(world, pos, (Boolean) state.getValue(CampfireBlock.SIGNAL_FIRE), false);
            }
        }

        i = ((Direction) state.getValue(CampfireBlock.FACING)).get2DDataValue();

        for (int j = 0; j < campfire.items.size(); ++j) {
            if (!((ItemStack) campfire.items.get(j)).isEmpty() && randomsource.nextFloat() < 0.2F) {
                Direction enumdirection = Direction.from2DDataValue(Math.floorMod(j + i, 4));
                float f = 0.3125F;
                double d0 = (double) pos.getX() + 0.5D - (double) ((float) enumdirection.getStepX() * 0.3125F) + (double) ((float) enumdirection.getClockWise().getStepX() * 0.3125F);
                double d1 = (double) pos.getY() + 0.5D;
                double d2 = (double) pos.getZ() + 0.5D - (double) ((float) enumdirection.getStepZ() * 0.3125F) + (double) ((float) enumdirection.getClockWise().getStepZ() * 0.3125F);

                for (int k = 0; k < 4; ++k) {
                    world.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 5.0E-4D, 0.0D);
                }
            }
        }

    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.items.clear();
        ContainerHelper.loadAllItems(nbt, this.items, registryLookup);
        int[] aint;

        if (nbt.contains("CookingTimes", 11)) {
            aint = nbt.getIntArray("CookingTimes");
            System.arraycopy(aint, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, aint.length));
        }

        if (nbt.contains("CookingTotalTimes", 11)) {
            aint = nbt.getIntArray("CookingTotalTimes");
            System.arraycopy(aint, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, aint.length));
        }

    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        ContainerHelper.saveAllItems(nbt, this.items, true, registryLookup);
        nbt.putIntArray("CookingTimes", this.cookingProgress);
        nbt.putIntArray("CookingTotalTimes", this.cookingTime);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        CompoundTag nbttagcompound = new CompoundTag();

        ContainerHelper.saveAllItems(nbttagcompound, this.items, true, registryLookup);
        return nbttagcompound;
    }

    public Optional<RecipeHolder<CampfireCookingRecipe>> getCookableRecipe(ItemStack stack) {
        return this.items.stream().noneMatch(ItemStack::isEmpty) ? Optional.empty() : this.quickCheck.getRecipeFor(new SingleRecipeInput(stack), this.level);
    }

    public boolean placeFood(@Nullable LivingEntity user, ItemStack stack, int cookTime) {
        for (int j = 0; j < this.items.size(); ++j) {
            ItemStack itemstack1 = (ItemStack) this.items.get(j);

            if (itemstack1.isEmpty()) {
                // CraftBukkit start
                CampfireStartEvent event = new CampfireStartEvent(CraftBlock.at(this.level,this.worldPosition), CraftItemStack.asCraftMirror(stack), (CampfireRecipe) this.getCookableRecipe(stack).get().toBukkitRecipe());
                this.level.getCraftServer().getPluginManager().callEvent(event);
                this.cookingTime[j] = event.getTotalCookTime(); // i -> event.getTotalCookTime()
                // CraftBukkit end
                this.cookingProgress[j] = 0;
                this.items.set(j, stack.consumeAndReturn(1, user));
                this.level.gameEvent((Holder) GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(user, this.getBlockState()));
                this.markUpdated();
                return true;
            }
        }

        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    public void dowse() {
        if (this.level != null) {
            this.markUpdated();
        }

    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        ((ItemContainerContents) components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        componentMapBuilder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag nbt) {
        nbt.remove("Items");
    }
}
