package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;

public class Swim extends Behavior<Mob> {
    private final float chance;

    public Swim(float chance) {
        super(ImmutableMap.of());
        this.chance = chance;
    }

    public static boolean shouldSwim(Mob entity) {
        return entity.isInWater() && entity.getFluidHeight(FluidTags.WATER) > entity.getFluidJumpThreshold() || entity.isInLava();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Mob entity) {
        return shouldSwim(entity);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Mob entity, long time) {
        return this.checkExtraStartConditions(world, entity);
    }

    @Override
    protected void tick(ServerLevel serverLevel, Mob mob, long l) {
        if (mob.getRandom().nextFloat() < this.chance) {
            mob.getJumpControl().jump();
        }
    }
}
