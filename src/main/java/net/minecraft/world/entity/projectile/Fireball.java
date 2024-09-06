package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract class Fireball extends AbstractHurtingProjectile implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(Fireball.class, EntityDataSerializers.ITEM_STACK);

    public Fireball(EntityType<? extends Fireball> type, Level world) {
        super(type, world);
    }

    public Fireball(EntityType<? extends Fireball> type, double x, double y, double z, Vec3 velocity, Level world) {
        super(type, x, y, z, velocity, world);
    }

    public Fireball(EntityType<? extends Fireball> type, LivingEntity owner, Vec3 velocity, Level world) {
        super(type, owner, velocity, world);
    }

    public void setItem(ItemStack stack) {
        if (stack.isEmpty()) {
            this.getEntityData().set(Fireball.DATA_ITEM_STACK, this.getDefaultItem());
        } else {
            this.getEntityData().set(Fireball.DATA_ITEM_STACK, stack.copyWithCount(1));
        }

    }

    @Override
    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(Fireball.DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(Fireball.DATA_ITEM_STACK, this.getDefaultItem());
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
            // CraftBukkit start - SPIGOT-5474 probably came from bugged earlier versions
            ItemStack itemstack = (ItemStack) ItemStack.parse(this.registryAccess(), nbt.getCompound("Item")).orElse(this.getDefaultItem());
            if (!itemstack.isEmpty()) {
                this.setItem(itemstack);
            }
            // CraftBukkit end
        } else {
            this.setItem(this.getDefaultItem());
        }

    }

    private ItemStack getDefaultItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return mappedIndex == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(mappedIndex);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
