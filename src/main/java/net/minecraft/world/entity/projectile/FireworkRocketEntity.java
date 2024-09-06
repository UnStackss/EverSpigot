package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class FireworkRocketEntity extends Projectile implements ItemSupplier {

    public static final EntityDataAccessor<ItemStack> DATA_ID_FIREWORKS_ITEM = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<OptionalInt> DATA_ATTACHED_TO_TARGET = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    public static final EntityDataAccessor<Boolean> DATA_SHOT_AT_ANGLE = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.BOOLEAN);
    public int life;
    public int lifetime;
    @Nullable
    public LivingEntity attachedToEntity;

    public FireworkRocketEntity(EntityType<? extends FireworkRocketEntity> type, Level world) {
        super(type, world);
    }

    public FireworkRocketEntity(Level world, double x, double y, double z, ItemStack stack) {
        super(EntityType.FIREWORK_ROCKET, world);
        this.life = 0;
        this.setPos(x, y, z);
        this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, stack.copy());
        int i = 1;
        Fireworks fireworks = (Fireworks) stack.get(DataComponents.FIREWORKS);

        if (fireworks != null) {
            i += fireworks.flightDuration();
        }

        this.setDeltaMovement(this.random.triangle(0.0D, 0.002297D), 0.05D, this.random.triangle(0.0D, 0.002297D));
        this.lifetime = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    public FireworkRocketEntity(Level world, @Nullable Entity entity, double x, double y, double z, ItemStack stack) {
        this(world, x, y, z, stack);
        this.setOwner(entity);
    }

    public FireworkRocketEntity(Level world, ItemStack stack, LivingEntity shooter) {
        this(world, shooter, shooter.getX(), shooter.getY(), shooter.getZ(), stack);
        this.entityData.set(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.of(shooter.getId()));
        this.attachedToEntity = shooter;
    }

    public FireworkRocketEntity(Level world, ItemStack stack, double x, double y, double z, boolean shotAtAngle) {
        this(world, x, y, z, stack);
        this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, shotAtAngle);
    }

    public FireworkRocketEntity(Level world, ItemStack stack, Entity entity, double x, double y, double z, boolean shotAtAngle) {
        this(world, stack, x, y, z, shotAtAngle);
        this.setOwner(entity);
    }

    // Spigot Start - copied from tick
    @Override
    public void inactiveTick() {
        this.life += 1;

        if (!this.level().isClientSide && this.life > this.lifetime) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }
        super.inactiveTick();
    }
    // Spigot End

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, FireworkRocketEntity.getDefaultItem());
        builder.define(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.empty());
        builder.define(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, false);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D && !this.isAttachedToEntity();
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return super.shouldRender(cameraX, cameraY, cameraZ) && !this.isAttachedToEntity();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d;

        if (this.isAttachedToEntity()) {
            if (this.attachedToEntity == null) {
                ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).ifPresent((i) -> {
                    Entity entity = this.level().getEntity(i);

                    if (entity instanceof LivingEntity) {
                        this.attachedToEntity = (LivingEntity) entity;
                    }

                });
            }

            if (this.attachedToEntity != null) {
                if (this.attachedToEntity.isFallFlying()) {
                    Vec3 vec3d1 = this.attachedToEntity.getLookAngle();
                    double d0 = 1.5D;
                    double d1 = 0.1D;
                    Vec3 vec3d2 = this.attachedToEntity.getDeltaMovement();

                    this.attachedToEntity.setDeltaMovement(vec3d2.add(vec3d1.x * 0.1D + (vec3d1.x * 1.5D - vec3d2.x) * 0.5D, vec3d1.y * 0.1D + (vec3d1.y * 1.5D - vec3d2.y) * 0.5D, vec3d1.z * 0.1D + (vec3d1.z * 1.5D - vec3d2.z) * 0.5D));
                    vec3d = this.attachedToEntity.getHandHoldingItemAngle(Items.FIREWORK_ROCKET);
                } else {
                    vec3d = Vec3.ZERO;
                }

                this.setPos(this.attachedToEntity.getX() + vec3d.x, this.attachedToEntity.getY() + vec3d.y, this.attachedToEntity.getZ() + vec3d.z);
                this.setDeltaMovement(this.attachedToEntity.getDeltaMovement());
            }
        } else {
            if (!this.isShotAtAngle()) {
                double d2 = this.horizontalCollision ? 1.0D : 1.15D;

                this.setDeltaMovement(this.getDeltaMovement().multiply(d2, 1.0D, d2).add(0.0D, 0.04D, 0.0D));
            }

            vec3d = this.getDeltaMovement();
            this.move(MoverType.SELF, vec3d);
            this.setDeltaMovement(vec3d);
        }

        HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        if (!this.noPhysics) {
            this.preHitTargetOrDeflectSelf(movingobjectposition); // CraftBukkit - projectile hit event
            this.hasImpulse = true;
        }

        this.updateRotation();
        if (this.life == 0 && !this.isSilent()) {
            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 1.0F);
        }

        ++this.life;
        if (this.level().isClientSide && this.life % 2 < 2) {
            this.level().addParticle(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.05D, -this.getDeltaMovement().y * 0.5D, this.random.nextGaussian() * 0.05D);
        }

        if (!this.level().isClientSide && this.life > this.lifetime) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }

    }

    private void explode() {
        this.level().broadcastEntityEvent(this, (byte) 17);
        this.gameEvent(GameEvent.EXPLODE, this.getOwner());
        this.dealExplosionDamage();
        this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (!this.level().isClientSide) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        BlockPos blockposition = new BlockPos(blockHitResult.getBlockPos());

        this.level().getBlockState(blockposition).entityInside(this.level(), blockposition, this);
        if (!this.level().isClientSide() && this.hasExplosion()) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }

        super.onHitBlock(blockHitResult);
    }

    private boolean hasExplosion() {
        return !this.getExplosions().isEmpty();
    }

    private void dealExplosionDamage() {
        float f = 0.0F;
        List<FireworkExplosion> list = this.getExplosions();

        if (!list.isEmpty()) {
            f = 5.0F + (float) (list.size() * 2);
        }

        if (f > 0.0F) {
            if (this.attachedToEntity != null) {
                this.attachedToEntity.hurt(this.damageSources().fireworks(this, this.getOwner()), 5.0F + (float) (list.size() * 2));
            }

            double d0 = 5.0D;
            Vec3 vec3d = this.position();
            List<LivingEntity> list1 = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(5.0D));
            Iterator iterator = list1.iterator();

            while (iterator.hasNext()) {
                LivingEntity entityliving = (LivingEntity) iterator.next();

                if (entityliving != this.attachedToEntity && this.distanceToSqr((Entity) entityliving) <= 25.0D) {
                    boolean flag = false;

                    for (int i = 0; i < 2; ++i) {
                        Vec3 vec3d1 = new Vec3(entityliving.getX(), entityliving.getY(0.5D * (double) i), entityliving.getZ());
                        BlockHitResult movingobjectpositionblock = this.level().clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

                        if (movingobjectpositionblock.getType() == HitResult.Type.MISS) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        float f1 = f * (float) Math.sqrt((5.0D - (double) this.distanceTo(entityliving)) / 5.0D);

                        entityliving.hurt(this.damageSources().fireworks(this, this.getOwner()), f1);
                    }
                }
            }
        }

    }

    private boolean isAttachedToEntity() {
        return ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).isPresent();
    }

    public boolean isShotAtAngle() {
        return (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE);
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 17 && this.level().isClientSide) {
            Vec3 vec3d = this.getDeltaMovement();

            this.level().createFireworks(this.getX(), this.getY(), this.getZ(), vec3d.x, vec3d.y, vec3d.z, this.getExplosions());
        }

        super.handleEntityEvent(status);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Life", this.life);
        nbt.putInt("LifeTime", this.lifetime);
        nbt.put("FireworksItem", this.getItem().save(this.registryAccess()));
        nbt.putBoolean("ShotAtAngle", (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.life = nbt.getInt("Life");
        this.lifetime = nbt.getInt("LifeTime");
        if (nbt.contains("FireworksItem", 10)) {
            this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, (ItemStack) ItemStack.parse(this.registryAccess(), nbt.getCompound("FireworksItem")).orElseGet(FireworkRocketEntity::getDefaultItem));
        } else {
            this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, FireworkRocketEntity.getDefaultItem());
        }

        if (nbt.contains("ShotAtAngle")) {
            this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, nbt.getBoolean("ShotAtAngle"));
        }

    }

    private List<FireworkExplosion> getExplosions() {
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
        Fireworks fireworks = (Fireworks) itemstack.get(DataComponents.FIREWORKS);

        return fireworks != null ? fireworks.explosions() : List.of();
    }

    @Override
    public ItemStack getItem() {
        return (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    public static ItemStack getDefaultItem() {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity target, DamageSource source) {
        double d0 = target.position().x - this.position().x;
        double d1 = target.position().z - this.position().z;

        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}
