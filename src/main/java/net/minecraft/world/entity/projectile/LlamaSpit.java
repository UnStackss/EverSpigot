package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class LlamaSpit extends Projectile {

    public LlamaSpit(EntityType<? extends LlamaSpit> type, Level world) {
        super(type, world);
    }

    public LlamaSpit(Level world, Llama owner) {
        this(EntityType.LLAMA_SPIT, world);
        this.setOwner(owner);
        this.setPos(owner.getX() - (double) (owner.getBbWidth() + 1.0F) * 0.5D * (double) Mth.sin(owner.yBodyRot * 0.017453292F), owner.getEyeY() - 0.10000000149011612D, owner.getZ() + (double) (owner.getBbWidth() + 1.0F) * 0.5D * (double) Mth.cos(owner.yBodyRot * 0.017453292F));
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06D;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d = this.getDeltaMovement();
        HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        this.preHitTargetOrDeflectSelf(movingobjectposition); // CraftBukkit - projectile hit event
        double d0 = this.getX() + vec3d.x;
        double d1 = this.getY() + vec3d.y;
        double d2 = this.getZ() + vec3d.z;

        this.updateRotation();
        float f = 0.99F;

        if (this.level().getBlockStates(this.getBoundingBox()).noneMatch(BlockBehaviour.BlockStateBase::isAir)) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else if (this.isInWaterOrBubble()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            this.setDeltaMovement(vec3d.scale(0.9900000095367432D));
            this.applyGravity();
            this.setPos(d0, d1, d2);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Entity entity = this.getOwner();

        if (entity instanceof LivingEntity entityliving) {
            entity = entityHitResult.getEntity();
            DamageSource damagesource = this.damageSources().spit(this, entityliving);

            if (entity.hurt(damagesource, 1.0F)) {
                Level world = this.level();

                if (world instanceof ServerLevel) {
                    ServerLevel worldserver = (ServerLevel) world;

                    EnchantmentHelper.doPostAttackEffects(worldserver, entity, damagesource);
                }
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        double d0 = packet.getXa();
        double d1 = packet.getYa();
        double d2 = packet.getZa();

        for (int i = 0; i < 7; ++i) {
            double d3 = 0.4D + 0.1D * (double) i;

            this.level().addParticle(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), d0 * d3, d1, d2 * d3);
        }

        this.setDeltaMovement(d0, d1, d2);
    }
}
