package net.minecraft.world.entity.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class Skeleton extends AbstractSkeleton {

    private static final int TOTAL_CONVERSION_TIME = 300;
    public static final EntityDataAccessor<Boolean> DATA_STRAY_CONVERSION_ID = SynchedEntityData.defineId(Skeleton.class, EntityDataSerializers.BOOLEAN);
    public static final String CONVERSION_TAG = "StrayConversionTime";
    public int inPowderSnowTime;
    public int conversionTime;

    public Skeleton(EntityType<? extends Skeleton> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Skeleton.DATA_STRAY_CONVERSION_ID, false);
    }

    public boolean isFreezeConverting() {
        return (Boolean) this.getEntityData().get(Skeleton.DATA_STRAY_CONVERSION_ID);
    }

    public void setFreezeConverting(boolean converting) {
        this.entityData.set(Skeleton.DATA_STRAY_CONVERSION_ID, converting);
    }

    @Override
    public boolean isShaking() {
        return this.isFreezeConverting();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isInPowderSnow) {
                if (this.isFreezeConverting()) {
                    --this.conversionTime;
                    if (this.conversionTime < 0) {
                        this.doFreezeConversion();
                    }
                } else {
                    ++this.inPowderSnowTime;
                    if (this.inPowderSnowTime >= 140) {
                        this.startFreezeConversion(300);
                    }
                }
            } else {
                this.inPowderSnowTime = -1;
                this.setFreezeConverting(false);
            }
        }

        super.tick();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("StrayConversionTime", this.isFreezeConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("StrayConversionTime", 99) && nbt.getInt("StrayConversionTime") > -1) {
            this.startFreezeConversion(nbt.getInt("StrayConversionTime"));
        }

    }

    public void startFreezeConversion(int time) {
        this.conversionTime = time;
        this.setFreezeConverting(true);
    }

    protected void doFreezeConversion() {
        Stray stray = this.convertTo(EntityType.STRAY, true, org.bukkit.event.entity.EntityTransformEvent.TransformReason.FROZEN, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.FROZEN); // CraftBukkit - add spawn and transform reasons // Paper - Fix issues with mob conversion
        if (!this.isSilent()) {
            this.level().levelEvent((Player) null, 1048, this.blockPosition(), 0);
        }
        // Paper start - Fix issues with mob conversion; reset conversion time to prevent event spam
        if (stray == null) {
            this.conversionTime = 300;
        }
        // Paper end - Fix issues with mob conversion

    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(world, source, causedByPlayer);
        Entity entity = source.getEntity();

        if (entity instanceof Creeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                entitycreeper.increaseDroppedSkulls();
                this.spawnAtLocation((ItemLike) Items.SKELETON_SKULL);
            }
        }

    }
}
