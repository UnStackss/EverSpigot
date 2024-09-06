package net.minecraft.world.entity.vehicle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
// CraftBukkit end

public abstract class VehicleEntity extends Entity {

    protected static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    public VehicleEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (this.isInvulnerableTo(source)) {
                return false;
            } else {
                // CraftBukkit start
                Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                org.bukkit.entity.Entity attacker = (source.getEntity() == null) ? null : source.getEntity().getBukkitEntity();

                VehicleDamageEvent event = new VehicleDamageEvent(vehicle, attacker, (double) amount);
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return false;
                }
                amount = (float) event.getDamage();
                // CraftBukkit end
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.markHurt();
                this.setDamage(this.getDamage() + amount * 10.0F);
                this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
                boolean flag = source.getEntity() instanceof Player && ((Player) source.getEntity()).getAbilities().instabuild;

                if ((flag || this.getDamage() <= 40.0F) && !this.shouldSourceDestroy(source)) {
                    if (flag) {
                        // CraftBukkit start
                        VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, attacker);
                        this.level().getCraftServer().getPluginManager().callEvent(destroyEvent);

                        if (destroyEvent.isCancelled()) {
                            this.setDamage(40.0F); // Maximize damage so this doesn't get triggered again right away
                            return true;
                        }
                        // CraftBukkit end
                        this.discard(EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                    }
                } else {
                    // CraftBukkit start
                    VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, attacker);
                    this.level().getCraftServer().getPluginManager().callEvent(destroyEvent);

                    if (destroyEvent.isCancelled()) {
                        this.setDamage(40.0F); // Maximize damage so this doesn't get triggered again right away
                        return true;
                    }
                    // CraftBukkit end
                    this.destroy(source);
                }

                return true;
            }
        } else {
            return true;
        }
    }

    boolean shouldSourceDestroy(DamageSource source) {
        return false;
    }

    public void destroy(Item selfAsItem) {
        this.kill();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack itemstack = new ItemStack(selfAsItem);

            itemstack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            this.spawnAtLocation(itemstack);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VehicleEntity.DATA_ID_HURT, 0);
        builder.define(VehicleEntity.DATA_ID_HURTDIR, 1);
        builder.define(VehicleEntity.DATA_ID_DAMAGE, 0.0F);
    }

    public void setHurtTime(int damageWobbleTicks) {
        this.entityData.set(VehicleEntity.DATA_ID_HURT, damageWobbleTicks);
    }

    public void setHurtDir(int damageWobbleSide) {
        this.entityData.set(VehicleEntity.DATA_ID_HURTDIR, damageWobbleSide);
    }

    public void setDamage(float damageWobbleStrength) {
        this.entityData.set(VehicleEntity.DATA_ID_DAMAGE, damageWobbleStrength);
    }

    public float getDamage() {
        return (Float) this.entityData.get(VehicleEntity.DATA_ID_DAMAGE);
    }

    public int getHurtTime() {
        return (Integer) this.entityData.get(VehicleEntity.DATA_ID_HURT);
    }

    public int getHurtDir() {
        return (Integer) this.entityData.get(VehicleEntity.DATA_ID_HURTDIR);
    }

    protected void destroy(DamageSource source) {
        this.destroy(this.getDropItem());
    }

    abstract Item getDropItem();
}
