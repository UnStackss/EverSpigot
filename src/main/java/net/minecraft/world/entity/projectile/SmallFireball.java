package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class SmallFireball extends Fireball {

    public SmallFireball(EntityType<? extends SmallFireball> type, Level world) {
        super(type, world);
    }

    public SmallFireball(Level world, LivingEntity owner, Vec3 velocity) {
        super(EntityType.SMALL_FIREBALL, owner, velocity, world);
        // CraftBukkit start
        if (this.getOwner() != null && this.getOwner() instanceof Mob) {
            this.isIncendiary = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        }
        // CraftBukkit end
    }

    public SmallFireball(Level world, double x, double y, double z, Vec3 velocity) {
        super(EntityType.SMALL_FIREBALL, x, y, z, velocity, world);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            Entity entity = entityHitResult.getEntity();
            Entity entity1 = this.getOwner();
            int i = entity.getRemainingFireTicks();

            // CraftBukkit start - Entity damage by entity event + combust event
            EntityCombustByEntityEvent event = new EntityCombustByEntityEvent((org.bukkit.entity.Projectile) this.getBukkitEntity(), entity.getBukkitEntity(), 5.0F);
            entity.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                entity.igniteForSeconds(event.getDuration(), false);
            }
            // CraftBukkit end
            DamageSource damagesource = this.damageSources().fireball(this, entity1);

            if (!entity.hurt(damagesource, 5.0F)) {
                entity.setRemainingFireTicks(i);
            } else {
                EnchantmentHelper.doPostAttackEffects(worldserver, entity, damagesource);
            }

        }
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            Entity entity = this.getOwner();

            if (this.isIncendiary) { // CraftBukkit
                BlockPos blockposition = blockHitResult.getBlockPos().relative(blockHitResult.getDirection());

                if (this.level().isEmptyBlock(blockposition) && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition, this).isCancelled()) { // CraftBukkit
                    this.level().setBlockAndUpdate(blockposition, BaseFireBlock.getState(this.level(), blockposition));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
