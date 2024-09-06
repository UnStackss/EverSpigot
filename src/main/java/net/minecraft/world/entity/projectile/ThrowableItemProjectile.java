package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ThrowableItemProjectile extends ThrowableProjectile implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(ThrowableItemProjectile.class, EntityDataSerializers.ITEM_STACK);

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, Level world) {
        super(type, world);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, double x, double y, double z, Level world) {
        super(type, x, y, z, world);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, LivingEntity owner, Level world) {
        super(type, owner, world);
    }

    public void setItem(ItemStack stack) {
        this.getEntityData().set(ThrowableItemProjectile.DATA_ITEM_STACK, stack.copyWithCount(1));
    }

    protected abstract Item getDefaultItem();

    // CraftBukkit start
    public Item getDefaultItemPublic() {
        return this.getDefaultItem();
    }
    // CraftBukkit end

    @Override
    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ThrowableItemProjectile.DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ThrowableItemProjectile.DATA_ITEM_STACK, new ItemStack(this.getDefaultItem()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.put("Item", this.getItem().save(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Item", 10)) {
            this.setItem((ItemStack) ItemStack.parse(this.registryAccess(), nbt.getCompound("Item")).orElseGet(() -> {
                return new ItemStack(this.getDefaultItem());
            }));
        } else {
            this.setItem(new ItemStack(this.getDefaultItem()));
        }

    }
}
