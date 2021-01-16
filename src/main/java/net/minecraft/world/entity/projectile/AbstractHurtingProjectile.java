package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public abstract class AbstractHurtingProjectile extends Projectile {

    public static final double INITAL_ACCELERATION_POWER = 0.1D;
    public static final double DEFLECTION_SCALE = 0.5D;
    public double accelerationPower;
    public float bukkitYield = 1; // CraftBukkit
    public boolean isIncendiary = true; // CraftBukkit

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, Level world) {
        super(type, world);
        this.accelerationPower = 0.1D;
    }

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, Level world) {
        this(type, world);
        this.setPos(x, y, z);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, Vec3 velocity, Level world) {
        this(type, world);
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        this.assignDirectionalMovement(velocity, this.accelerationPower);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, LivingEntity owner, Vec3 velocity, Level world) {
        this(type, owner.getX(), owner.getY(), owner.getZ(), velocity, world);
        this.setOwner(owner);
        this.setRot(owner.getYRot(), owner.getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1)) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    protected ClipContext.Block getClipType() {
        return ClipContext.Block.COLLIDER;
    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (!this.level().isClientSide && (entity != null && entity.isRemoved() || !this.level().hasChunkAt(this.blockPosition()))) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
            if (this.shouldBurn()) {
                this.igniteForSeconds(1.0F);
            }

            HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, this.getClipType());

            if (movingobjectposition.getType() != HitResult.Type.MISS) {
                this.preHitTargetOrDeflectSelf(movingobjectposition); // CraftBukkit - projectile hit event

                // CraftBukkit start - Fire ProjectileHitEvent
                if (this.isRemoved()) {
                    // CraftEventFactory.callProjectileHitEvent(this, movingobjectposition); // Paper - this is an undesired duplicate event
                }
                // CraftBukkit end
            }

            this.checkInsideBlocks();
            Vec3 vec3d = this.getDeltaMovement();
            double d0 = this.getX() + vec3d.x;
            double d1 = this.getY() + vec3d.y;
            double d2 = this.getZ() + vec3d.z;

            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            float f;

            if (this.isInWater()) {
                for (int i = 0; i < 4; ++i) {
                    float f1 = 0.25F;

                    this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3d.x * 0.25D, d1 - vec3d.y * 0.25D, d2 - vec3d.z * 0.25D, vec3d.x, vec3d.y, vec3d.z);
                }

                f = this.getLiquidInertia();
            } else {
                f = this.getInertia();
            }

            this.setDeltaMovement(vec3d.add(vec3d.normalize().scale(this.accelerationPower)).scale((double) f));
            ParticleOptions particleparam = this.getTrailParticle();

            if (particleparam != null) {
                this.level().addParticle(particleparam, d0, d1 + 0.5D, d2, 0.0D, 0.0D, 0.0D);
            }

            this.setPos(d0, d1, d2);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source);
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    protected boolean shouldBurn() {
        return true;
    }

    @Nullable
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    protected float getInertia() {
        return 0.95F;
    }

    protected float getLiquidInertia() {
        return 0.8F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putDouble("acceleration_power", this.accelerationPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("acceleration_power", 6)) {
            this.accelerationPower = nbt.getDouble("acceleration_power");
        }

    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        Entity entity = this.getOwner();
        int i = entity == null ? 0 : entity.getId();
        Vec3 vec3d = entityTrackerEntry.getPositionBase();

        return new ClientboundAddEntityPacket(this.getId(), this.getUUID(), vec3d.x(), vec3d.y(), vec3d.z(), entityTrackerEntry.getLastSentXRot(), entityTrackerEntry.getLastSentYRot(), this.getType(), i, entityTrackerEntry.getLastSentMovement(), 0.0D);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Vec3 vec3d = new Vec3(packet.getXa(), packet.getYa(), packet.getZa());

        this.setDeltaMovement(vec3d);
    }

    public void assignDirectionalMovement(Vec3 velocity, double accelerationPower) {
        this.setDeltaMovement(velocity.normalize().scale(accelerationPower));
        this.hasImpulse = true;
    }

    @Override
    protected void onDeflection(@Nullable Entity deflector, boolean fromAttack) {
        super.onDeflection(deflector, fromAttack);
        if (fromAttack) {
            this.accelerationPower = 0.1D;
        } else {
            this.accelerationPower *= 0.5D;
        }

    }
}
