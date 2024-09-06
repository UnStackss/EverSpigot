package net.minecraft.world.effect;

import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

class InfestedMobEffect extends MobEffect {

    private final float chanceToSpawn;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected InfestedMobEffect(MobEffectCategory category, int color, float silverfishChance, ToIntFunction<RandomSource> silverfishCountFunction) {
        super(category, color, ParticleTypes.INFESTED);
        this.chanceToSpawn = silverfishChance;
        this.spawnedCount = silverfishCountFunction;
    }

    @Override
    public void onMobHurt(LivingEntity entity, int amplifier, DamageSource source, float amount) {
        if (entity.getRandom().nextFloat() <= this.chanceToSpawn) {
            int j = this.spawnedCount.applyAsInt(entity.getRandom());

            for (int k = 0; k < j; ++k) {
                this.spawnSilverfish(entity.level(), entity, entity.getX(), entity.getY() + (double) entity.getBbHeight() / 2.0D, entity.getZ());
            }
        }

    }

    private void spawnSilverfish(Level world, LivingEntity entity, double x, double y, double z) {
        Silverfish entitysilverfish = (Silverfish) EntityType.SILVERFISH.create(world);

        if (entitysilverfish != null) {
            RandomSource randomsource = entity.getRandom();
            float f = 1.5707964F;
            float f1 = Mth.randomBetween(randomsource, -1.5707964F, 1.5707964F);
            Vector3f vector3f = entity.getLookAngle().toVector3f().mul(0.3F).mul(1.0F, 1.5F, 1.0F).rotateY(f1);

            entitysilverfish.moveTo(x, y, z, world.getRandom().nextFloat() * 360.0F, 0.0F);
            entitysilverfish.setDeltaMovement(new Vec3(vector3f));
            // CraftBukkit start
            if (!world.addFreshEntity(entitysilverfish, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.POTION_EFFECT)) {
                return;
            }
            // CraftBukkit end
            entitysilverfish.playSound(SoundEvents.SILVERFISH_HURT);
        }
    }
}
