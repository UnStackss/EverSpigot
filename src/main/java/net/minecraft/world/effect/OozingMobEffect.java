package net.minecraft.world.effect;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

class OozingMobEffect extends MobEffect {

    private static final int RADIUS_TO_CHECK_SLIMES = 2;
    public static final int SLIME_SIZE = 2;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected OozingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> slimeCountFunction) {
        super(category, color, ParticleTypes.ITEM_SLIME);
        this.spawnedCount = slimeCountFunction;
    }

    @VisibleForTesting
    protected static int numberOfSlimesToSpawn(int maxEntityCramming, OozingMobEffect.NearbySlimes slimeCounter, int potentialSlimes) {
        return maxEntityCramming < 1 ? potentialSlimes : Mth.clamp(0, maxEntityCramming - slimeCounter.count(maxEntityCramming), potentialSlimes);
    }

    @Override
    public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED) {
            int j = this.spawnedCount.applyAsInt(entity.getRandom());
            Level world = entity.level();
            int k = world.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            int l = OozingMobEffect.numberOfSlimesToSpawn(k, OozingMobEffect.NearbySlimes.closeTo(entity), j);

            for (int i1 = 0; i1 < l; ++i1) {
                this.spawnSlimeOffspring(entity.level(), entity.getX(), entity.getY() + 0.5D, entity.getZ());
            }

        }
    }

    private void spawnSlimeOffspring(Level world, double x, double y, double z) {
        Slime entityslime = (Slime) EntityType.SLIME.create(world);

        if (entityslime != null) {
            entityslime.setSize(2, true);
            entityslime.moveTo(x, y, z, world.getRandom().nextFloat() * 360.0F, 0.0F);
            world.addFreshEntity(entityslime, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.POTION_EFFECT); // CraftBukkit
        }
    }

    @FunctionalInterface
    protected interface NearbySlimes {

        int count(int limit);

        static OozingMobEffect.NearbySlimes closeTo(LivingEntity entity) {
            return (i) -> {
                List<Slime> list = new ArrayList();

                entity.level().getEntities(EntityType.SLIME, entity.getBoundingBox().inflate(2.0D), (entityslime) -> {
                    return entityslime != entity;
                }, list, i);
                return list.size();
            };
        }
    }
}
