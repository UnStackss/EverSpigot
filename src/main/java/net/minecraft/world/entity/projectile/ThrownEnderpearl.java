package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class ThrownEnderpearl extends ThrowableItemProjectile {

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> type, Level world) {
        super(type, world);
    }

    public ThrownEnderpearl(Level world, LivingEntity owner) {
        super(EntityType.ENDER_PEARL, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        entityHitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        for (int i = 0; i < 32; ++i) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(), this.random.nextGaussian(), 0.0D, this.random.nextGaussian());
        }

        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            if (!this.isRemoved()) {
                Entity entity = this.getOwner();

                if (entity != null && ThrownEnderpearl.isAllowedToTeleportOwner(entity, worldserver)) {
                    if (entity.isPassenger()) {
                        entity.unRide();
                    }

                    if (entity instanceof ServerPlayer) {
                        ServerPlayer entityplayer = (ServerPlayer) entity;

                        if (entityplayer.connection.isAcceptingMessages()) {
                            // CraftBukkit start
                            Entity tp = entity.changeDimension(new DimensionTransition(worldserver, this.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING, PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
                            if (tp == null) {
                                this.discard(EntityRemoveEvent.Cause.HIT);
                                return;
                            }
                            // CraftBukkit end
                            if (this.random.nextFloat() < 0.05F && worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                                Endermite entityendermite = (Endermite) EntityType.ENDERMITE.create(worldserver);

                                if (entityendermite != null) {
                                    entityendermite.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                    worldserver.addFreshEntity(entityendermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                                }
                            }

                            // entity.changeDimension(new DimensionTransition(worldserver, this.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING)); // CraftBukkit - moved up
                            entity.resetFallDistance();
                            entityplayer.resetCurrentImpulseContext();
                            entity.hurt(this.damageSources().fall().customEventDamager(this), 5.0F); // CraftBukkit // Paper - fix DamageSource API
                            this.playSound(worldserver, this.position());
                        }
                    } else {
                        entity.changeDimension(new DimensionTransition(worldserver, this.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING));
                        entity.resetFallDistance();
                        this.playSound(worldserver, this.position());
                    }

                    this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
                    return;
                }

                this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
                return;
            }
        }

    }

    private static boolean isAllowedToTeleportOwner(Entity entity, Level world) {
        if (entity.level().dimension() == world.dimension()) {
            if (!(entity instanceof LivingEntity)) {
                return entity.isAlive();
            } else {
                LivingEntity entityliving = (LivingEntity) entity;

                return entityliving.isAlive() && !entityliving.isSleeping();
            }
        } else {
            return entity.canUsePortal(true);
        }
    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (entity instanceof ServerPlayer && !entity.isAlive() && this.level().getGameRules().getBoolean(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH)) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
        }

    }

    private void playSound(Level world, Vec3 pos) {
        world.playSound((Player) null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        if (from.getTypeKey() == LevelStem.END) { // CraftBukkit
            Entity entity = this.getOwner();

            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                return super.canChangeDimensions(from, to) && entityplayer.seenCredits;
            }
        }

        return super.canChangeDimensions(from, to);
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        if (state.is(Blocks.END_GATEWAY)) {
            Entity entity = this.getOwner();

            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                entityplayer.onInsideBlock(state);
            }
        }

    }
}
