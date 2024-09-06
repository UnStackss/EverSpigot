package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

public class CaveSpider extends Spider {

    public CaveSpider(EntityType<? extends CaveSpider> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createCaveSpider() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 12.0D);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (super.doHurtTarget(target)) {
            if (target instanceof LivingEntity) {
                byte b0 = 0;

                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    b0 = 7;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    b0 = 15;
                }

                if (b0 > 0) {
                    ((LivingEntity) target).addEffect(new MobEffectInstance(MobEffects.POISON, b0 * 20, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        return entityData;
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        return vehicle.getBbWidth() <= this.getBbWidth() ? new Vec3(0.0D, 0.21875D * (double) this.getScale(), 0.0D) : super.getVehicleAttachmentPoint(vehicle);
    }
}
