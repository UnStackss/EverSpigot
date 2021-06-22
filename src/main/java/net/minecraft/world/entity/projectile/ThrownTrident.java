package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class ThrownTrident extends AbstractArrow {

    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    public boolean dealtDamage;
    public int clientSideReturnTridentTickCount;

    public ThrownTrident(EntityType<? extends ThrownTrident> type, Level world) {
        super(type, world);
    }

    public ThrownTrident(Level world, LivingEntity owner, ItemStack stack) {
        super(EntityType.TRIDENT, owner, world, stack, (ItemStack) null);
        this.entityData.set(ThrownTrident.ID_LOYALTY, this.getLoyaltyFromItem(stack));
        this.entityData.set(ThrownTrident.ID_FOIL, stack.hasFoil());
    }

    public ThrownTrident(Level world, double x, double y, double z, ItemStack stack) {
        super(EntityType.TRIDENT, x, y, z, world, stack, stack);
        this.entityData.set(ThrownTrident.ID_LOYALTY, this.getLoyaltyFromItem(stack));
        this.entityData.set(ThrownTrident.ID_FOIL, stack.hasFoil());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ThrownTrident.ID_LOYALTY, (byte) 0);
        builder.define(ThrownTrident.ID_FOIL, false);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity entity = this.getOwner();
        byte b0 = (Byte) this.entityData.get(ThrownTrident.ID_LOYALTY);

        if (b0 > 0 && (this.dealtDamage || this.isNoPhysics()) && entity != null) {
            if (!this.isAcceptibleReturnOwner()) {
                if (!this.level().isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }

                this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
            } else {
                this.setNoPhysics(true);
                Vec3 vec3d = entity.getEyePosition().subtract(this.position());

                this.setPosRaw(this.getX(), this.getY() + vec3d.y * 0.015D * (double) b0, this.getZ());
                if (this.level().isClientSide) {
                    this.yOld = this.getY();
                }

                double d0 = 0.05D * (double) b0;

                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(vec3d.normalize().scale(d0)));
                if (this.clientSideReturnTridentTickCount == 0) {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }

                ++this.clientSideReturnTridentTickCount;
            }
        }

        super.tick();
    }

    private boolean isAcceptibleReturnOwner() {
        Entity entity = this.getOwner();

        return entity != null && entity.isAlive() ? !(entity instanceof ServerPlayer) || !entity.isSpectator() : false;
    }

    public boolean isFoil() {
        return (Boolean) this.entityData.get(ThrownTrident.ID_FOIL);
    }

    // Paper start
    public void setFoil(boolean foil) {
        this.entityData.set(ThrownTrident.ID_FOIL, foil);
    }

    public int getLoyalty() {
        return this.entityData.get(ThrownTrident.ID_LOYALTY);
    }

    public void setLoyalty(byte loyalty) {
        this.entityData.set(ThrownTrident.ID_LOYALTY, loyalty);
    }
    // Paper end

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 currentPosition, Vec3 nextPosition) {
        return this.dealtDamage ? null : super.findHitEntity(currentPosition, nextPosition);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        float f = 8.0F;
        Entity entity1 = this.getOwner();
        DamageSource damagesource = this.damageSources().trident(this, (Entity) (entity1 == null ? this : entity1));
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            f = EnchantmentHelper.modifyDamage(worldserver, this.getWeaponItem(), entity, damagesource, f);
        }

        this.dealtDamage = true;
        if (entity.hurt(damagesource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            world = this.level();
            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world; // CraftBukkit - decompile error
                EnchantmentHelper.doPostAttackEffectsWithItemSource(worldserver, entity, damagesource, this.getWeaponItem());
            }

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;

                this.doKnockback(entityliving, damagesource);
                this.doPostHurtEffects(entityliving);
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void hitBlockEnchantmentEffects(ServerLevel world, BlockHitResult blockHitResult, ItemStack weaponStack) {
        Vec3 vec3d = blockHitResult.getBlockPos().clampLocationWithin(blockHitResult.getLocation());
        Entity entity = this.getOwner();
        LivingEntity entityliving;

        if (entity instanceof LivingEntity entityliving1) {
            entityliving = entityliving1;
        } else {
            entityliving = null;
        }

        EnchantmentHelper.onHitBlock(world, weaponStack, entityliving, this, (EquipmentSlot) null, vec3d, world.getBlockState(blockHitResult.getBlockPos()), (item) -> {
            this.kill();
        });
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.TRIDENT);
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(Player player) {
        if (this.ownedBy(player) || this.getOwner() == null) {
            super.playerTouch(player);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.dealtDamage = nbt.getBoolean("DealtDamage");
        this.entityData.set(ThrownTrident.ID_LOYALTY, this.getLoyaltyFromItem(this.getPickupItemStackOrigin()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("DealtDamage", this.dealtDamage);
    }

    private byte getLoyaltyFromItem(ItemStack stack) {
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            return (byte) Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(worldserver, stack, this), 0, 127);
        } else {
            return 0;
        }
    }

    @Override
    public void tickDespawn() {
        byte b0 = (Byte) this.entityData.get(ThrownTrident.ID_LOYALTY);

        if (this.pickup != AbstractArrow.Pickup.ALLOWED || b0 <= 0) {
            super.tickDespawn();
        }

    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return true;
    }
}
