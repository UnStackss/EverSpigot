package net.minecraft.world.entity;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.scores.PlayerTeam;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTeleportEvent;
// CraftBukkit end

public abstract class TamableAnimal extends Animal implements OwnableEntity {

    public static final int TELEPORT_WHEN_DISTANCE_IS_SQ = 144;
    private static final int MIN_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 2;
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 3;
    private static final int MAX_VERTICAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 1;
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(TamableAnimal.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Optional<UUID>> DATA_OWNERUUID_ID = SynchedEntityData.defineId(TamableAnimal.class, EntityDataSerializers.OPTIONAL_UUID);
    private boolean orderedToSit;

    protected TamableAnimal(EntityType<? extends TamableAnimal> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TamableAnimal.DATA_FLAGS_ID, (byte) 0);
        builder.define(TamableAnimal.DATA_OWNERUUID_ID, Optional.empty());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.getOwnerUUID() != null) {
            nbt.putUUID("Owner", this.getOwnerUUID());
        }

        nbt.putBoolean("Sitting", this.orderedToSit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        UUID uuid;

        if (nbt.hasUUID("Owner")) {
            uuid = nbt.getUUID("Owner");
        } else {
            String s = nbt.getString("Owner");

            uuid = OldUsersConverter.convertMobOwnerIfNecessary(this.getServer(), s);
        }

        if (uuid != null) {
            try {
                this.setOwnerUUID(uuid);
                this.setTame(true, false);
            } catch (Throwable throwable) {
                this.setTame(false, true);
            }
        }

        this.orderedToSit = nbt.getBoolean("Sitting");
        this.setInSittingPose(this.orderedToSit, false); // Paper - Add EntityToggleSitEvent
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    public boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        if (this.isInSittingPose()) {
            if (distance > (float) this.level().paperConfig().misc.maxLeashDistance.or(Leashable.LEASH_TOO_FAR_DIST)) { // Paper - Configurable max leash distance
                // Paper start - Expand EntityUnleashEvent
                org.bukkit.event.entity.EntityUnleashEvent event = new org.bukkit.event.entity.EntityUnleashEvent(this.getBukkitEntity(), org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.DISTANCE, true);
                if (!event.callEvent()) return false;
                this.dropLeash(true, event.isDropLeash());
                // Paper end - Expand EntityUnleashEvent
            }

            return false;
        } else {
            return super.handleLeashAtDistance(leashHolder, distance);
        }
    }

    protected void spawnTamingParticles(boolean positive) {
        SimpleParticleType particletype = ParticleTypes.HEART;

        if (!positive) {
            particletype = ParticleTypes.SMOKE;
        }

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(particletype, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 7) {
            this.spawnTamingParticles(true);
        } else if (status == 6) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(status);
        }

    }

    public boolean isTame() {
        return ((Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID) & 4) != 0;
    }

    public void setTame(boolean tamed, boolean updateAttributes) {
        byte b0 = (Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID);

        if (tamed) {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 | 4));
        } else {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 & -5));
        }

        if (updateAttributes) {
            this.applyTamingSideEffects();
        }

    }

    protected void applyTamingSideEffects() {}

    public boolean isInSittingPose() {
        return ((Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID) & 1) != 0;
    }

    public void setInSittingPose(boolean inSittingPose) {
        // Paper start - Add EntityToggleSitEvent
        this.setInSittingPose(inSittingPose, true);
    }
    public void setInSittingPose(boolean inSittingPose, boolean callEvent) {
        if (callEvent && !new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), inSittingPose).callEvent()) return;
        // Paper end - Add EntityToggleSitEvent
        byte b0 = (Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID);

        if (inSittingPose) {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 | 1));
        } else {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 & -2));
        }

    }

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return (UUID) ((Optional) this.entityData.get(TamableAnimal.DATA_OWNERUUID_ID)).orElse((Object) null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(TamableAnimal.DATA_OWNERUUID_ID, Optional.ofNullable(uuid));
    }

    public void tame(Player player) {
        this.setTame(true, true);
        this.setOwnerUUID(player.getUUID());
        if (player instanceof ServerPlayer entityplayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger(entityplayer, (Animal) this);
        }

    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return this.isOwnedBy(target) ? false : super.canAttack(target);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return true;
    }

    @Override
    public PlayerTeam getTeam() {
        if (this.isTame()) {
            LivingEntity entityliving = this.getOwner();

            if (entityliving != null) {
                return entityliving.getTeam();
            }
        }

        return super.getTeam();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (this.isTame()) {
            LivingEntity entityliving = this.getOwner();

            if (other == entityliving) {
                return true;
            }

            if (entityliving != null) {
                return entityliving.isAlliedTo(other);
            }
        }

        return super.isAlliedTo(other);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide && this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES) && this.getOwner() instanceof ServerPlayer) {
            // Paper start - Add TameableDeathMessageEvent
            io.papermc.paper.event.entity.TameableDeathMessageEvent event = new io.papermc.paper.event.entity.TameableDeathMessageEvent((org.bukkit.entity.Tameable) getBukkitEntity(), io.papermc.paper.adventure.PaperAdventure.asAdventure(this.getCombatTracker().getDeathMessage()));
            if (event.callEvent()) {
                this.getOwner().sendSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.deathMessage()));
            }
            // Paper end - Add TameableDeathMessageEvent
        }

        super.die(damageSource);
    }

    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    public void setOrderedToSit(boolean sitting) {
        this.orderedToSit = sitting;
    }

    public void tryToTeleportToOwner() {
        LivingEntity entityliving = this.getOwner();

        if (entityliving != null) {
            this.teleportToAroundBlockPos(entityliving.blockPosition());
        }

    }

    public boolean shouldTryTeleportToOwner() {
        LivingEntity entityliving = this.getOwner();

        return entityliving != null && this.distanceToSqr((Entity) this.getOwner()) >= 144.0D;
    }

    private void teleportToAroundBlockPos(BlockPos pos) {
        for (int i = 0; i < 10; ++i) {
            int j = this.random.nextIntBetweenInclusive(-3, 3);
            int k = this.random.nextIntBetweenInclusive(-3, 3);

            if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                int l = this.random.nextIntBetweenInclusive(-1, 1);

                if (this.maybeTeleportTo(pos.getX() + j, pos.getY() + l, pos.getZ() + k)) {
                    return;
                }
            }
        }

    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            // CraftBukkit start
            EntityTeleportEvent event = CraftEventFactory.callEntityTeleportEvent(this, (double) x + 0.5D, (double) y, (double) z + 0.5D);
            if (event.isCancelled() || event.getTo() == null) { // Paper - prevent NP on null event to location
                return false;
            }
            Location to = event.getTo();
            this.moveTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
            // CraftBukkit end
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathType pathtype = WalkNodeEvaluator.getPathTypeStatic((Mob) this, pos);

        if (pathtype != PathType.WALKABLE) {
            return false;
        } else {
            BlockState iblockdata = this.level().getBlockState(pos.below());

            if (!this.canFlyToOwner() && iblockdata.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockposition1 = pos.subtract(this.blockPosition());

                return this.level().noCollision(this, this.getBoundingBox().move(blockposition1));
            }
        }
    }

    public final boolean unableToMoveToOwner() {
        return this.isOrderedToSit() || this.isPassenger() || this.mayBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
    }

    protected boolean canFlyToOwner() {
        return false;
    }

    public class TamableAnimalPanicGoal extends PanicGoal {

        public TamableAnimalPanicGoal(final double d0, final TagKey tagkey) {
            super(TamableAnimal.this, d0, tagkey);
        }

        public TamableAnimalPanicGoal(final double d0) {
            super(TamableAnimal.this, d0);
        }

        @Override
        public void tick() {
            if (!TamableAnimal.this.unableToMoveToOwner() && TamableAnimal.this.shouldTryTeleportToOwner()) {
                TamableAnimal.this.tryToTeleportToOwner();
            }

            super.tick();
        }
    }
}
