package net.minecraft.world.entity.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;

public abstract class WaterAnimal extends PathfinderMob {
    protected WaterAnimal(EntityType<? extends WaterAnimal> type, Level world) {
        super(type, world);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader world) {
        return world.isUnobstructed(this);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 1 + this.level().random.nextInt(3);
    }

    protected void handleAirSupply(int air) {
        if (this.isAlive() && !this.isInWaterOrBubble()) {
            this.setAirSupply(air - 1);
            if (this.getAirSupply() == -20) {
                this.setAirSupply(0);
                this.hurt(this.damageSources().drown(), 2.0F);
            }
        } else {
            this.setAirSupply(300);
        }
    }

    @Override
    public void baseTick() {
        int i = this.getAirSupply();
        super.baseTick();
        this.handleAirSupply(i);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    public static boolean checkSurfaceWaterAnimalSpawnRules(
        EntityType<? extends WaterAnimal> type, LevelAccessor world, MobSpawnType reason, BlockPos pos, RandomSource random
    ) {
        int i = world.getSeaLevel();
        int j = i - 13;
        // Paper start - Make water animal spawn height configurable
        i = world.getMinecraftWorld().paperConfig().entities.spawning.wateranimalSpawnHeight.maximum.or(i);
        j = world.getMinecraftWorld().paperConfig().entities.spawning.wateranimalSpawnHeight.minimum.or(j);
        // Paper end - Make water animal spawn height configurable
        return pos.getY() >= j && pos.getY() <= i && world.getFluidState(pos.below()).is(FluidTags.WATER) && world.getBlockState(pos.above()).is(Blocks.WATER);
    }
}
