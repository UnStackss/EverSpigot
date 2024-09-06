package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
// CraftBukkit start
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class ThrownPotion extends ThrowableItemProjectile implements ItemSupplier {

    public static final double SPLASH_RANGE = 4.0D;
    private static final double SPLASH_RANGE_SQ = 16.0D;
    public static final Predicate<net.minecraft.world.entity.LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = (entityliving) -> {
        return entityliving.isSensitiveToWater() || entityliving.isOnFire();
    };

    public ThrownPotion(EntityType<? extends ThrownPotion> type, Level world) {
        super(type, world);
    }

    public ThrownPotion(Level world, net.minecraft.world.entity.LivingEntity owner) {
        super(EntityType.POTION, owner, world);
    }

    public ThrownPotion(Level world, double x, double y, double z) {
        super(EntityType.POTION, x, y, z, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Direction enumdirection = blockHitResult.getDirection();
            BlockPos blockposition = blockHitResult.getBlockPos();
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            PotionContents potioncontents = (PotionContents) itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

            if (potioncontents.is(Potions.WATER)) {
                this.dowseFire(blockposition1);
                this.dowseFire(blockposition1.relative(enumdirection.getOpposite()));
                Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext()) {
                    Direction enumdirection1 = (Direction) iterator.next();

                    this.dowseFire(blockposition1.relative(enumdirection1));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        // Paper start - More projectile API
        this.splash(hitResult);
    }
    public void splash(@Nullable HitResult hitResult) {
        // Paper end - More projectile API
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            PotionContents potioncontents = (PotionContents) itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

            boolean showParticles = true; // Paper - Fix potions splash events
            if (potioncontents.is(Potions.WATER)) {
                showParticles = this.applyWater(hitResult); // Paper - Fix potions splash events
            } else if (true || potioncontents.hasEffects()) { // CraftBukkit - Call event even if no effects to apply
                if (this.isLingering()) {
                    showParticles = this.makeAreaOfEffectCloud(potioncontents, hitResult); // CraftBukkit - Pass MovingObjectPosition // Paper
                } else {
                    showParticles = this.applySplash(potioncontents.getAllEffects(), hitResult != null && hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) hitResult).getEntity() : null, hitResult); // CraftBukkit - Pass MovingObjectPosition // Paper - More projectile API
                }
            }

            if (showParticles) { // Paper - Fix potions splash events
            int i = potioncontents.potion().isPresent() && ((Potion) ((Holder) potioncontents.potion().get()).value()).hasInstantEffects() ? 2007 : 2002;

            this.level().levelEvent(i, this.blockPosition(), potioncontents.getColor());
            } // Paper - Fix potions splash events
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }

    private static final Predicate<net.minecraft.world.entity.LivingEntity> APPLY_WATER_GET_ENTITIES_PREDICATE = ThrownPotion.WATER_SENSITIVE_OR_ON_FIRE.or(Axolotl.class::isInstance); // Paper - Fix potions splash events
    private boolean applyWater(@Nullable HitResult hitResult) { // Paper - Fix potions splash events
        AABB axisalignedbb = this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        // Paper start - Fix potions splash events
        List<net.minecraft.world.entity.LivingEntity> list = this.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, axisalignedbb, ThrownPotion.APPLY_WATER_GET_ENTITIES_PREDICATE);
        Map<LivingEntity, Double> affected = new HashMap<>();
        java.util.Set<LivingEntity> rehydrate = new java.util.HashSet<>();
        java.util.Set<LivingEntity> extinguish = new java.util.HashSet<>();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            net.minecraft.world.entity.LivingEntity entityliving = (net.minecraft.world.entity.LivingEntity) iterator.next();
            if (entityliving instanceof Axolotl axolotl) {
                rehydrate.add(((org.bukkit.entity.Axolotl) axolotl.getBukkitEntity()));
            }
            double d0 = this.distanceToSqr((Entity) entityliving);

            if (d0 < 16.0D) {
                if (entityliving.isSensitiveToWater()) {
                    affected.put(entityliving.getBukkitLivingEntity(), 1.0);
                }

                if (entityliving.isOnFire() && entityliving.isAlive()) {
                    extinguish.add(entityliving.getBukkitLivingEntity());
                }
            }
        }

        io.papermc.paper.event.entity.WaterBottleSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callWaterBottleSplashEvent(
            this, hitResult, affected, rehydrate, extinguish
        );
        if (!event.isCancelled()) {
            for (LivingEntity affectedEntity : event.getToDamage()) {
                ((CraftLivingEntity) affectedEntity).getHandle().hurt(this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
            }
            for (LivingEntity toExtinguish : event.getToExtinguish()) {
                ((CraftLivingEntity) toExtinguish).getHandle().extinguishFire();
            }
            for (LivingEntity toRehydrate : event.getToRehydrate()) {
                if (((CraftLivingEntity) toRehydrate).getHandle() instanceof Axolotl axolotl) {
                    axolotl.rehydrate();
                }
            }
            // Paper end - Fix potions splash events
        }
        return !event.isCancelled(); // Paper - Fix potions splash events

    }

    private boolean applySplash(Iterable<MobEffectInstance> iterable, @Nullable Entity entity, @Nullable HitResult position) { // CraftBukkit - Pass MovingObjectPosition // Paper - Fix potions splash events & More projectile API
        AABB axisalignedbb = this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<net.minecraft.world.entity.LivingEntity> list = this.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, axisalignedbb);
        Map<LivingEntity, Double> affected = new HashMap<LivingEntity, Double>(); // CraftBukkit

        if (!list.isEmpty()) {
            Entity entity1 = this.getEffectSource();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                net.minecraft.world.entity.LivingEntity entityliving = (net.minecraft.world.entity.LivingEntity) iterator.next();

                if (entityliving.isAffectedByPotions()) {
                    double d0 = this.distanceToSqr((Entity) entityliving);

                    if (d0 < 16.0D) {
                        double d1;

                        // Paper - diff on change, used when calling the splash event for water splash potions
                        if (entityliving == entity) {
                            d1 = 1.0D;
                        } else {
                            d1 = 1.0D - Math.sqrt(d0) / 4.0D;
                        }

                        // CraftBukkit start
                        affected.put((LivingEntity) entityliving.getBukkitEntity(), d1);
                    }
                }
            }
        }

        org.bukkit.event.entity.PotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPotionSplashEvent(this, position, affected);
        if (!event.isCancelled() && list != null && !list.isEmpty()) { // do not process effects if there are no effects to process
            Entity entity1 = this.getEffectSource();
            for (LivingEntity victim : event.getAffectedEntities()) {
                if (!(victim instanceof CraftLivingEntity)) {
                    continue;
                }

                net.minecraft.world.entity.LivingEntity entityliving = ((CraftLivingEntity) victim).getHandle();
                double d1 = event.getIntensity(victim);
                // CraftBukkit end

                Iterator iterator1 = iterable.iterator();

                while (iterator1.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator1.next();
                    Holder<MobEffect> holder = mobeffect.getEffect();
                    // CraftBukkit start - Abide by PVP settings - for players only!
                    if (!this.level().pvpMode && this.getOwner() instanceof ServerPlayer && entityliving instanceof ServerPlayer && entityliving != this.getOwner()) {
                        MobEffect mobeffectlist = (MobEffect) holder.value();
                        if (mobeffectlist == MobEffects.MOVEMENT_SLOWDOWN || mobeffectlist == MobEffects.DIG_SLOWDOWN || mobeffectlist == MobEffects.HARM || mobeffectlist == MobEffects.BLINDNESS
                                || mobeffectlist == MobEffects.HUNGER || mobeffectlist == MobEffects.WEAKNESS || mobeffectlist == MobEffects.POISON) {
                            continue;
                        }
                    }
                    // CraftBukkit end

                    if (((MobEffect) holder.value()).isInstantenous()) {
                        ((MobEffect) holder.value()).applyInstantenousEffect(this, this.getOwner(), entityliving, mobeffect.getAmplifier(), d1);
                    } else {
                        int i = mobeffect.mapDuration((j) -> {
                            return (int) (d1 * (double) j + 0.5D);
                        });
                        MobEffectInstance mobeffect1 = new MobEffectInstance(holder, i, mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible());

                        if (!mobeffect1.endsWithin(20)) {
                            entityliving.addEffect(mobeffect1, entity1, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_SPLASH); // CraftBukkit
                        }
                    }
                }
            }
        }
        return !event.isCancelled(); // Paper - Fix potions splash events

    }

    private boolean makeAreaOfEffectCloud(PotionContents potioncontents, @Nullable HitResult position) { // CraftBukkit - Pass MovingObjectPosition // Paper - More projectile API
        AreaEffectCloud entityareaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        Entity entity = this.getOwner();

        if (entity instanceof net.minecraft.world.entity.LivingEntity entityliving) {
            entityareaeffectcloud.setOwner(entityliving);
        }

        entityareaeffectcloud.setRadius(3.0F);
        entityareaeffectcloud.setRadiusOnUse(-0.5F);
        entityareaeffectcloud.setWaitTime(10);
        entityareaeffectcloud.setRadiusPerTick(-entityareaeffectcloud.getRadius() / (float) entityareaeffectcloud.getDuration());
        entityareaeffectcloud.setPotionContents(potioncontents);
        boolean noEffects = potioncontents.hasEffects(); // Paper - Fix potions splash events
        // CraftBukkit start
        org.bukkit.event.entity.LingeringPotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLingeringPotionSplashEvent(this, position, entityareaeffectcloud);
        if (!(event.isCancelled() || entityareaeffectcloud.isRemoved() || (!event.allowsEmptyCreation() && (noEffects && !entityareaeffectcloud.potionContents.hasEffects())))) { // Paper - don't spawn area effect cloud if the effects were empty and not changed during the event handling
            this.level().addFreshEntity(entityareaeffectcloud);
        } else {
            entityareaeffectcloud.discard(null); // CraftBukkit - add Bukkit remove cause
        }
        // CraftBukkit end
        return !event.isCancelled(); // Paper - Fix potions splash events
    }

    public boolean isLingering() {
        return this.getItem().is(Items.LINGERING_POTION);
    }

    private void dowseFire(BlockPos pos) {
        BlockState iblockdata = this.level().getBlockState(pos);

        if (iblockdata.is(BlockTags.FIRE)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, pos, iblockdata.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                this.level().destroyBlock(pos, false, this);
            }
            // CraftBukkit end
        } else if (AbstractCandleBlock.isLit(iblockdata)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, pos, iblockdata.setValue(AbstractCandleBlock.LIT, false))) {
                AbstractCandleBlock.extinguish((Player) null, iblockdata, this.level(), pos);
            }
            // CraftBukkit end
        } else if (CampfireBlock.isLitCampfire(iblockdata)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, pos, iblockdata.setValue(CampfireBlock.LIT, false))) {
                this.level().levelEvent((Player) null, 1009, pos, 0);
                CampfireBlock.dowse(this.getOwner(), this.level(), pos, iblockdata);
                this.level().setBlockAndUpdate(pos, (BlockState) iblockdata.setValue(CampfireBlock.LIT, false));
            }
            // CraftBukkit end
        }

    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(net.minecraft.world.entity.LivingEntity target, DamageSource source) {
        double d0 = target.position().x - this.position().x;
        double d1 = target.position().z - this.position().z;

        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}
