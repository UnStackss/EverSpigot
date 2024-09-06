package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public class Arrow extends AbstractArrow {

    private static final int EXPOSED_POTION_DECAY_TIME = 600;
    public static final int NO_EFFECT_COLOR = -1;
    private static final EntityDataAccessor<Integer> ID_EFFECT_COLOR = SynchedEntityData.defineId(Arrow.class, EntityDataSerializers.INT);
    private static final byte EVENT_POTION_PUFF = 0;

    public Arrow(EntityType<? extends Arrow> type, Level world) {
        super(type, world);
    }

    public Arrow(Level world, double x, double y, double z, ItemStack stack, @Nullable ItemStack shotFrom) {
        super(EntityType.ARROW, x, y, z, world, stack, shotFrom);
        this.updateColor();
    }

    public Arrow(Level world, LivingEntity owner, ItemStack stack, @Nullable ItemStack shotFrom) {
        super(EntityType.ARROW, owner, world, stack, shotFrom);
        this.updateColor();
    }

    public PotionContents getPotionContents() {
        return (PotionContents) this.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    public void setPotionContents(PotionContents potionContentsComponent) {
        this.getPickupItemStackOrigin().set(DataComponents.POTION_CONTENTS, potionContentsComponent);
        this.updateColor();
    }

    @Override
    public void setPickupItemStack(ItemStack stack) {
        super.setPickupItemStack(stack);
        this.updateColor();
    }

    public void updateColor() {
        PotionContents potioncontents = this.getPotionContents();

        this.entityData.set(Arrow.ID_EFFECT_COLOR, potioncontents.equals(PotionContents.EMPTY) ? -1 : potioncontents.getColor());
    }

    public void addEffect(MobEffectInstance effect) {
        this.setPotionContents(this.getPotionContents().withEffectAdded(effect));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Arrow.ID_EFFECT_COLOR, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.inGround) {
                if (this.inGroundTime % 5 == 0) {
                    this.makeParticle(1);
                }
            } else {
                this.makeParticle(2);
            }
        } else if (this.inGround && this.inGroundTime != 0 && !this.getPotionContents().equals(PotionContents.EMPTY) && this.inGroundTime >= 600) {
            this.level().broadcastEntityEvent(this, (byte) 0);
            this.setPickupItemStack(new ItemStack(Items.ARROW));
        }

    }

    private void makeParticle(int amount) {
        int j = this.getColor();

        if (j != -1 && amount > 0) {
            for (int k = 0; k < amount; ++k) {
                this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, j), this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
            }

        }
    }

    public int getColor() {
        return (Integer) this.entityData.get(Arrow.ID_EFFECT_COLOR);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        Entity entity = this.getEffectSource();
        PotionContents potioncontents = this.getPotionContents();
        Iterator iterator;
        MobEffectInstance mobeffect;

        if (potioncontents.potion().isPresent()) {
            iterator = ((Potion) ((Holder) potioncontents.potion().get()).value()).getEffects().iterator();

            while (iterator.hasNext()) {
                mobeffect = (MobEffectInstance) iterator.next();
                target.addEffect(new MobEffectInstance(mobeffect.getEffect(), Math.max(mobeffect.mapDuration((i) -> {
                    return i / 8;
                }), 1), mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible()), entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
            }
        }

        iterator = potioncontents.customEffects().iterator();

        while (iterator.hasNext()) {
            mobeffect = (MobEffectInstance) iterator.next();
            target.addEffect(mobeffect, entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
        }

    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 0) {
            int i = this.getColor();

            if (i != -1) {
                float f = (float) (i >> 16 & 255) / 255.0F;
                float f1 = (float) (i >> 8 & 255) / 255.0F;
                float f2 = (float) (i >> 0 & 255) / 255.0F;

                for (int j = 0; j < 20; ++j) {
                    this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, f, f1, f2), this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
                }
            }
        } else {
            super.handleEntityEvent(status);
        }

    }
}
