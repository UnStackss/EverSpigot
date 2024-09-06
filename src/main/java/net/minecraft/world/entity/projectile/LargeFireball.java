package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class LargeFireball extends Fireball {

    public int explosionPower = 1;

    public LargeFireball(EntityType<? extends LargeFireball> type, Level world) {
        super(type, world);
        this.isIncendiary = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit
    }

    public LargeFireball(Level world, LivingEntity owner, Vec3 velocity, int explosionPower) {
        super(EntityType.FIREBALL, owner, velocity, world);
        this.explosionPower = explosionPower;
        this.isIncendiary = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

            // CraftBukkit start - fire ExplosionPrimeEvent
            ExplosionPrimeEvent event = new ExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                // give 'this' instead of (Entity) null so we know what causes the damage
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.MOB);
            }
            // CraftBukkit end
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            Entity entity = entityHitResult.getEntity();
            Entity entity1 = this.getOwner();
            DamageSource damagesource = this.damageSources().fireball(this, entity1);

            entity.hurt(damagesource, 6.0F);
            EnchantmentHelper.doPostAttackEffects(worldserver, entity, damagesource);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putByte("ExplosionPower", (byte) this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("ExplosionPower", 99)) {
            // CraftBukkit - set bukkitYield when setting explosionpower
            this.bukkitYield = this.explosionPower = nbt.getByte("ExplosionPower");
        }

    }
}
