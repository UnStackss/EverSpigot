package net.minecraft.world.level;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Location;
import org.bukkit.event.block.BlockExplodeEvent;
// CraftBukkit end

public class Explosion {

    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;
    private final RandomSource random;
    private final Level level;
    private final double x;
    private final double y;
    private final double z;
    @Nullable
    public final Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final ParticleOptions smallExplosionParticles;
    private final ParticleOptions largeExplosionParticles;
    private final Holder<SoundEvent> explosionSound;
    private final ObjectArrayList<BlockPos> toBlow;
    private final Map<Player, Vec3> hitPlayers;
    // CraftBukkit - add field
    public boolean wasCanceled = false;
    public float yield;
    // CraftBukkit end

    public static DamageSource getDefaultDamageSource(Level world, @Nullable Entity source) {
        return world.damageSources().explosion(source, Explosion.getIndirectSourceEntityInternal(source));
    }

    public Explosion(Level world, @Nullable Entity entity, double x, double y, double z, float power, List<BlockPos> affectedBlocks, Explosion.BlockInteraction destructionType, ParticleOptions particle, ParticleOptions emitterParticle, Holder<SoundEvent> soundEvent) {
        this(world, entity, Explosion.getDefaultDamageSource(world, entity), (ExplosionDamageCalculator) null, x, y, z, power, false, destructionType, particle, emitterParticle, soundEvent);
        this.toBlow.addAll(affectedBlocks);
    }

    public Explosion(Level world, @Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Explosion.BlockInteraction destructionType, List<BlockPos> affectedBlocks) {
        this(world, entity, x, y, z, power, createFire, destructionType);
        this.toBlow.addAll(affectedBlocks);
    }

    public Explosion(Level world, @Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Explosion.BlockInteraction destructionType) {
        this(world, entity, Explosion.getDefaultDamageSource(world, entity), (ExplosionDamageCalculator) null, x, y, z, power, createFire, destructionType, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
    }

    public Explosion(Level world, @Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Explosion.BlockInteraction destructionType, ParticleOptions particle, ParticleOptions emitterParticle, Holder<SoundEvent> soundEvent) {
        this.random = RandomSource.create();
        this.toBlow = new ObjectArrayList();
        this.hitPlayers = Maps.newHashMap();
        this.level = world;
        this.source = entity;
        this.radius = (float) Math.max(power, 0.0); // CraftBukkit - clamp bad values
        this.x = x;
        this.y = y;
        this.z = z;
        this.fire = createFire;
        this.blockInteraction = destructionType;
        this.damageSource = damageSource == null ? world.damageSources().explosion(this) : damageSource;
        this.damageCalculator = behavior == null ? this.makeDamageCalculator(entity) : behavior;
        this.smallExplosionParticles = particle;
        this.largeExplosionParticles = emitterParticle;
        this.explosionSound = soundEvent;
        this.yield = this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY ? 1.0F / this.radius : 1.0F; // CraftBukkit
    }

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return (ExplosionDamageCalculator) (entity == null ? Explosion.EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(entity));
    }

    public static float getSeenPercent(Vec3 source, Entity entity) {
        AABB axisalignedbb = entity.getBoundingBox();
        double d0 = 1.0D / ((axisalignedbb.maxX - axisalignedbb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((axisalignedbb.maxY - axisalignedbb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((axisalignedbb.maxZ - axisalignedbb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D) {
            int i = 0;
            int j = 0;

            for (double d5 = 0.0D; d5 <= 1.0D; d5 += d0) {
                for (double d6 = 0.0D; d6 <= 1.0D; d6 += d1) {
                    for (double d7 = 0.0D; d7 <= 1.0D; d7 += d2) {
                        double d8 = Mth.lerp(d5, axisalignedbb.minX, axisalignedbb.maxX);
                        double d9 = Mth.lerp(d6, axisalignedbb.minY, axisalignedbb.maxY);
                        double d10 = Mth.lerp(d7, axisalignedbb.minZ, axisalignedbb.maxZ);
                        Vec3 vec3d1 = new Vec3(d8 + d3, d9, d10 + d4);

                        if (entity.level().clip(new ClipContext(vec3d1, source, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) {
                            ++i;
                        }

                        ++j;
                    }
                }
            }

            return (float) i / (float) j;
        } else {
            return 0.0F;
        }
    }

    public float radius() {
        return this.radius;
    }

    public Vec3 center() {
        return new Vec3(this.x, this.y, this.z);
    }

    public void explode() {
        // CraftBukkit start
        if (this.radius < 0.1F) {
            return;
        }
        // CraftBukkit end
        this.level.gameEvent(this.source, (Holder) GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));
        Set<BlockPos> set = Sets.newHashSet();
        boolean flag = true;

        int i;
        int j;

        for (int k = 0; k < 16; ++k) {
            for (i = 0; i < 16; ++i) {
                for (j = 0; j < 16; ++j) {
                    if (k == 0 || k == 15 || i == 0 || i == 15 || j == 0 || j == 15) {
                        double d0 = (double) ((float) k / 15.0F * 2.0F - 1.0F);
                        double d1 = (double) ((float) i / 15.0F * 2.0F - 1.0F);
                        double d2 = (double) ((float) j / 15.0F * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                        double d4 = this.x;
                        double d5 = this.y;
                        double d6 = this.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            BlockPos blockposition = BlockPos.containing(d4, d5, d6);
                            BlockState iblockdata = this.level.getBlockState(blockposition);
                            FluidState fluid = this.level.getFluidState(blockposition);

                            if (!this.level.isInWorldBounds(blockposition)) {
                                break;
                            }

                            Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, blockposition, iblockdata, fluid);

                            if (optional.isPresent()) {
                                f -= ((Float) optional.get() + 0.3F) * 0.3F;
                            }

                            if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockposition, iblockdata, f)) {
                                set.add(blockposition);
                            }

                            d4 += d0 * 0.30000001192092896D;
                            d5 += d1 * 0.30000001192092896D;
                            d6 += d2 * 0.30000001192092896D;
                        }
                    }
                }
            }
        }

        this.toBlow.addAll(set);
        float f2 = this.radius * 2.0F;

        i = Mth.floor(this.x - (double) f2 - 1.0D);
        j = Mth.floor(this.x + (double) f2 + 1.0D);
        int l = Mth.floor(this.y - (double) f2 - 1.0D);
        int i1 = Mth.floor(this.y + (double) f2 + 1.0D);
        int j1 = Mth.floor(this.z - (double) f2 - 1.0D);
        int k1 = Mth.floor(this.z + (double) f2 + 1.0D);
        List<Entity> list = this.level.getEntities(this.source, new AABB((double) i, (double) l, (double) j1, (double) j, (double) i1, (double) k1), (com.google.common.base.Predicate<Entity>) entity -> entity.isAlive() && !entity.isSpectator()); // Paper - Fix lag from explosions processing dead entities
        Vec3 vec3d = new Vec3(this.x, this.y, this.z);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (!entity.ignoreExplosion(this)) {
                double d7 = Math.sqrt(entity.distanceToSqr(vec3d)) / (double) f2;

                if (d7 <= 1.0D) {
                    double d8 = entity.getX() - this.x;
                    double d9 = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                    double d10 = entity.getZ() - this.z;
                    double d11 = Math.sqrt(d8 * d8 + d9 * d9 + d10 * d10);

                    if (d11 != 0.0D) {
                        d8 /= d11;
                        d9 /= d11;
                        d10 /= d11;
                        if (this.damageCalculator.shouldDamageEntity(this, entity)) {
                            // CraftBukkit start

                            // Special case ender dragon only give knockback if no damage is cancelled
                            // Thinks to note:
                            // - Setting a velocity to a ComplexEntityPart is ignored (and therefore not needed)
                            // - Damaging ComplexEntityPart while forward the damage to EntityEnderDragon
                            // - Damaging EntityEnderDragon does nothing
                            // - EntityEnderDragon hitbock always covers the other parts and is therefore always present
                            if (entity instanceof EnderDragonPart) {
                                continue;
                            }

                            entity.lastDamageCancelled = false;

                            if (entity instanceof EnderDragon) {
                                for (EnderDragonPart entityComplexPart : ((EnderDragon) entity).subEntities) {
                                    // Calculate damage separately for each EntityComplexPart
                                    if (list.contains(entityComplexPart)) {
                                        entityComplexPart.hurt(this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity));
                                    }
                                }
                            } else {
                                entity.hurt(this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity));
                            }

                            if (entity.lastDamageCancelled) { // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Skip entity if damage event was cancelled
                                continue;
                            }
                            // CraftBukkit end
                        }

                        double d12 = (1.0D - d7) * this.getBlockDensity(vec3d, entity) * (double) this.damageCalculator.getKnockbackMultiplier(entity); // Paper - Optimize explosions
                        double d13;

                        if (entity instanceof LivingEntity) {
                            LivingEntity entityliving = (LivingEntity) entity;

                            d13 = entity instanceof Player && this.level.paperConfig().environment.disableExplosionKnockback ? 0 : d12 * (1.0D - entityliving.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
                        } else {
                            d13 = d12;
                        }

                        d8 *= d13;
                        d9 *= d13;
                        d10 *= d13;
                        Vec3 vec3d1 = new Vec3(d8, d9, d10);

                        // CraftBukkit start - Call EntityKnockbackEvent
                        if (entity instanceof LivingEntity) {
                            // Paper start - knockback events
                            io.papermc.paper.event.entity.EntityKnockbackEvent event = CraftEventFactory.callEntityKnockbackEvent((org.bukkit.craftbukkit.entity.CraftLivingEntity) entity.getBukkitEntity(), this.source, this.damageSource.getEntity() != null ? this.damageSource.getEntity() : this.source, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.EXPLOSION, d13, vec3d1);
                            vec3d1 = event.isCancelled() ? Vec3.ZERO : org.bukkit.craftbukkit.util.CraftVector.toNMS(event.getKnockback());
                            // Paper end - knockback events
                        }
                        // CraftBukkit end
                        entity.setDeltaMovement(entity.getDeltaMovement().add(vec3d1));
                        if (entity instanceof Player) {
                            Player entityhuman = (Player) entity;

                            if (!entityhuman.isSpectator() && (!entityhuman.isCreative() || !entityhuman.getAbilities().flying) && !level.paperConfig().environment.disableExplosionKnockback) { // Paper - Option to disable explosion knockback
                                this.hitPlayers.put(entityhuman, vec3d1);
                            }
                        }

                        entity.onExplosionHit(this.source);
                    }
                }
            }
        }

    }

    public void finalizeExplosion(boolean particles) {
        if (this.level.isClientSide) {
            this.level.playLocalSound(this.x, this.y, this.z, (SoundEvent) this.explosionSound.value(), SoundSource.BLOCKS, 4.0F, (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
        }

        boolean flag1 = this.interactsWithBlocks();

        if (particles) {
            ParticleOptions particleparam;

            if (this.radius >= 2.0F && flag1) {
                particleparam = this.largeExplosionParticles;
            } else {
                particleparam = this.smallExplosionParticles;
            }

            this.level.addParticle(particleparam, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
        }

        if (flag1) {
            this.level.getProfiler().push("explosion_blocks");
            List<Pair<ItemStack, BlockPos>> list = new ArrayList();

            Util.shuffle(this.toBlow, this.level.random);
            ObjectListIterator objectlistiterator = this.toBlow.iterator();
            // CraftBukkit start
            org.bukkit.World bworld = this.level.getWorld();
            Location location = new Location(bworld, this.x, this.y, this.z);

            List<org.bukkit.block.Block> blockList = new ObjectArrayList<>();
            for (int i1 = this.toBlow.size() - 1; i1 >= 0; i1--) {
                BlockPos cpos = this.toBlow.get(i1);
                org.bukkit.block.Block bblock = bworld.getBlockAt(cpos.getX(), cpos.getY(), cpos.getZ());
                if (!bblock.getType().isAir()) {
                    blockList.add(bblock);
                }
            }

            List<org.bukkit.block.Block> bukkitBlocks;

            if (this.source != null) {
                EntityExplodeEvent event = CraftEventFactory.callEntityExplodeEvent(this.source, blockList, this.yield, this.getBlockInteraction());
                this.wasCanceled = event.isCancelled();
                bukkitBlocks = event.blockList();
                this.yield = event.getYield();
            } else {
                org.bukkit.block.Block block = location.getBlock();
                org.bukkit.block.BlockState blockState = (this.damageSource.getDirectBlockState() != null) ? this.damageSource.getDirectBlockState() : block.getState();
                BlockExplodeEvent event = CraftEventFactory.callBlockExplodeEvent(block, blockState, blockList, this.yield, this.getBlockInteraction());
                this.wasCanceled = event.isCancelled();
                bukkitBlocks = event.blockList();
                this.yield = event.getYield();
            }

            this.toBlow.clear();

            for (org.bukkit.block.Block bblock : bukkitBlocks) {
                BlockPos coords = new BlockPos(bblock.getX(), bblock.getY(), bblock.getZ());
                this.toBlow.add(coords);
            }

            if (this.wasCanceled) {
                return;
            }
            // CraftBukkit end
            objectlistiterator = this.toBlow.iterator();

            while (objectlistiterator.hasNext()) {
                BlockPos blockposition = (BlockPos) objectlistiterator.next();
                // CraftBukkit start - TNTPrimeEvent
                BlockState iblockdata = this.level.getBlockState(blockposition);
                Block block = iblockdata.getBlock();
                if (block instanceof net.minecraft.world.level.block.TntBlock) {
                    Entity sourceEntity = this.source == null ? null : this.source;
                    BlockPos sourceBlock = sourceEntity == null ? BlockPos.containing(this.x, this.y, this.z) : null;
                    if (!CraftEventFactory.callTNTPrimeEvent(this.level, blockposition, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.EXPLOSION, sourceEntity, sourceBlock)) {
                        this.level.sendBlockUpdated(blockposition, Blocks.AIR.defaultBlockState(), iblockdata, 3); // Update the block on the client
                        continue;
                    }
                }
                // CraftBukkit end

                this.level.getBlockState(blockposition).onExplosionHit(this.level, blockposition, this, (itemstack, blockposition1) -> {
                    Explosion.addOrAppendStack(list, itemstack, blockposition1);
                });
            }

            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Pair<ItemStack, BlockPos> pair = (Pair) iterator.next();

                Block.popResource(this.level, (BlockPos) pair.getSecond(), (ItemStack) pair.getFirst());
            }

            this.level.getProfiler().pop();
        }

        if (this.fire) {
            ObjectListIterator objectlistiterator1 = this.toBlow.iterator();

            while (objectlistiterator1.hasNext()) {
                BlockPos blockposition1 = (BlockPos) objectlistiterator1.next();

                if (this.random.nextInt(3) == 0 && this.level.getBlockState(blockposition1).isAir() && this.level.getBlockState(blockposition1.below()).isSolidRender(this.level, blockposition1.below())) {
                    // CraftBukkit start - Ignition by explosion
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level, blockposition1, this).isCancelled()) {
                        this.level.setBlockAndUpdate(blockposition1, BaseFireBlock.getState(this.level, blockposition1));
                    }
                    // CraftBukkit end
                }
            }
        }

    }

    private static void addOrAppendStack(List<Pair<ItemStack, BlockPos>> stacks, ItemStack stack, BlockPos pos) {
        if (stack.isEmpty()) return; // CraftBukkit - SPIGOT-5425
        for (int i = 0; i < stacks.size(); ++i) {
            Pair<ItemStack, BlockPos> pair = (Pair) stacks.get(i);
            ItemStack itemstack1 = (ItemStack) pair.getFirst();

            if (ItemEntity.areMergable(itemstack1, stack)) {
                stacks.set(i, Pair.of(ItemEntity.merge(itemstack1, stack, 16), (BlockPos) pair.getSecond()));
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        stacks.add(Pair.of(stack, pos));
    }

    public boolean interactsWithBlocks() {
        return this.blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    public Map<Player, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Nullable
    private static LivingEntity getIndirectSourceEntityInternal(@Nullable Entity from) {
        if (from == null) {
            return null;
        } else if (from instanceof PrimedTnt) {
            PrimedTnt entitytntprimed = (PrimedTnt) from;

            return entitytntprimed.getOwner();
        } else if (from instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) from;

            return entityliving;
        } else {
            if (from instanceof Projectile) {
                Projectile iprojectile = (Projectile) from;
                Entity entity1 = iprojectile.getOwner();

                if (entity1 instanceof LivingEntity) {
                    LivingEntity entityliving1 = (LivingEntity) entity1;

                    return entityliving1;
                }
            }

            return null;
        }
    }

    @Nullable
    public LivingEntity getIndirectSourceEntity() {
        return Explosion.getIndirectSourceEntityInternal(this.source);
    }

    @Nullable
    public Entity getDirectSourceEntity() {
        return this.source;
    }

    public void clearToBlow() {
        this.toBlow.clear();
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public Explosion.BlockInteraction getBlockInteraction() {
        return this.blockInteraction;
    }

    public ParticleOptions getSmallExplosionParticles() {
        return this.smallExplosionParticles;
    }

    public ParticleOptions getLargeExplosionParticles() {
        return this.largeExplosionParticles;
    }

    public Holder<SoundEvent> getExplosionSound() {
        return this.explosionSound;
    }

    public boolean canTriggerBlocks() {
        return this.blockInteraction == Explosion.BlockInteraction.TRIGGER_BLOCK && !this.level.isClientSide() ? (this.source != null && this.source.getType() == EntityType.BREEZE_WIND_CHARGE ? this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) : true) : false;
    }

    public static enum BlockInteraction {

        KEEP, DESTROY, DESTROY_WITH_DECAY, TRIGGER_BLOCK;

        private BlockInteraction() {}
    }
    // Paper start - Optimize explosions
    private float getBlockDensity(Vec3 vec3d, Entity entity) {
        if (!this.level.paperConfig().environment.optimizeExplosions) {
            return getSeenPercent(vec3d, entity);
        }
        CacheKey key = new CacheKey(this, entity.getBoundingBox());
        Float blockDensity = this.level.explosionDensityCache.get(key);
        if (blockDensity == null) {
            blockDensity = getSeenPercent(vec3d, entity);
            this.level.explosionDensityCache.put(key, blockDensity);
        }

        return blockDensity;
    }

    static class CacheKey {
        private final Level world;
        private final double posX, posY, posZ;
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;

        public CacheKey(Explosion explosion, AABB aabb) {
            this.world = explosion.level;
            this.posX = explosion.x;
            this.posY = explosion.y;
            this.posZ = explosion.z;
            this.minX = aabb.minX;
            this.minY = aabb.minY;
            this.minZ = aabb.minZ;
            this.maxX = aabb.maxX;
            this.maxY = aabb.maxY;
            this.maxZ = aabb.maxZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (Double.compare(cacheKey.posX, posX) != 0) return false;
            if (Double.compare(cacheKey.posY, posY) != 0) return false;
            if (Double.compare(cacheKey.posZ, posZ) != 0) return false;
            if (Double.compare(cacheKey.minX, minX) != 0) return false;
            if (Double.compare(cacheKey.minY, minY) != 0) return false;
            if (Double.compare(cacheKey.minZ, minZ) != 0) return false;
            if (Double.compare(cacheKey.maxX, maxX) != 0) return false;
            if (Double.compare(cacheKey.maxY, maxY) != 0) return false;
            if (Double.compare(cacheKey.maxZ, maxZ) != 0) return false;
            return world.equals(cacheKey.world);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = world.hashCode();
            temp = Double.doubleToLongBits(posX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
    // Paper end
}
