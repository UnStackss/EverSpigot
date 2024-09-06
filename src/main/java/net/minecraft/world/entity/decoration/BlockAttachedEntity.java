package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
// CraftBukkit start
import net.minecraft.tags.DamageTypeTags;
import org.bukkit.entity.Hanging;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
// CraftBukkit end

public abstract class BlockAttachedEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private int checkInterval; { this.checkInterval = this.getId() % this.level().spigotConfig.hangingTickFrequency; } // Paper - Perf: offset item frame ticking
    protected BlockPos pos;

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> type, Level world) {
        super(type, world);
    }

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> type, Level world, BlockPos attachedBlockPos) {
        this(type, world);
        this.pos = attachedBlockPos;
    }

    protected abstract void recalculateBoundingBox();

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            this.checkBelowWorld();
            if (this.checkInterval++ == this.level().spigotConfig.hangingTickFrequency) { // Spigot
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    // CraftBukkit start - fire break events
                    BlockState material = this.level().getBlockState(this.blockPosition());
                    HangingBreakEvent.RemoveCause cause;

                    if (!material.isAir()) {
                        // TODO: This feels insufficient to catch 100% of suffocation cases
                        cause = HangingBreakEvent.RemoveCause.OBSTRUCTION;
                    } else {
                        cause = HangingBreakEvent.RemoveCause.PHYSICS;
                    }

                    HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), cause);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    if (this.isRemoved() || event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    this.dropItem((Entity) null);
                }
            }
        }

    }

    public abstract boolean survives();

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player entityhuman) {
            return !this.level().mayInteract(entityhuman, this.pos) ? true : this.hurt(this.damageSources().playerAttack(entityhuman), 0.0F);
        } else {
            return false;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            if (!this.isRemoved() && !this.level().isClientSide) {
                // CraftBukkit start - fire break events
                Entity damager = (source.isDirect()) ? source.getDirectEntity() : source.getEntity();
                HangingBreakEvent event;
                if (damager != null) {
                    event = new HangingBreakByEntityEvent((Hanging) this.getBukkitEntity(), damager.getBukkitEntity(), source.is(DamageTypeTags.IS_EXPLOSION) ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.ENTITY);
                } else {
                    event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), source.is(DamageTypeTags.IS_EXPLOSION) ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.DEFAULT);
                }

                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (this.isRemoved() || event.isCancelled()) {
                    return true;
                }
                // CraftBukkit end

                this.kill();
                this.markHurt();
                this.dropItem(source.getEntity());
            }

            return true;
        }
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        if (!this.level().isClientSide && !this.isRemoved() && movement.lengthSqr() > 0.0D) {
            if (this.isRemoved()) return; // CraftBukkit

            // CraftBukkit start - fire break events
            // TODO - Does this need its own cause? Seems to only be triggered by pistons
            HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.PHYSICS);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (this.isRemoved() || event.isCancelled()) {
                return;
            }
            // CraftBukkit end

            this.kill();
            this.dropItem((Entity) null);
        }

    }

    @Override
    public void push(double deltaX, double deltaY, double deltaZ, @Nullable Entity pushingEntity) { // Paper - override correct overload
        if (false && !this.level().isClientSide && !this.isRemoved() && deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > 0.0D) { // CraftBukkit - not needed
            this.kill();
            this.dropItem((Entity) null);
        }

    }

    // CraftBukkit start - selectively save tile position
    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound, boolean includeAll) {
        if (includeAll) {
            this.addAdditionalSaveData(nbttagcompound);
        }
    }
    // CraftBukkit end

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        BlockPos blockposition = this.getPos();

        nbt.putInt("TileX", blockposition.getX());
        nbt.putInt("TileY", blockposition.getY());
        nbt.putInt("TileZ", blockposition.getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        BlockPos blockposition = new BlockPos(nbt.getInt("TileX"), nbt.getInt("TileY"), nbt.getInt("TileZ"));

        if (!blockposition.closerThan(this.blockPosition(), 16.0D)) {
            BlockAttachedEntity.LOGGER.error("Block-attached entity at invalid position: {}", blockposition);
        } else {
            this.pos = blockposition;
        }
    }

    public abstract void dropItem(@Nullable Entity breaker);

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.pos = BlockPos.containing(x, y, z);
        this.recalculateBoundingBox();
        this.hasImpulse = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {}

    @Override
    public void refreshDimensions() {}
}
