package net.minecraft.world.entity;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityUnleashEvent;
// CraftBukkit end

public abstract class PathfinderMob extends Mob {

    protected static final float DEFAULT_WALK_TARGET_VALUE = 0.0F;

    protected PathfinderMob(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    public BlockPos movingTarget; public BlockPos getMovingTarget() { return movingTarget; } // Paper

    public float getWalkTargetValue(BlockPos pos) {
        return this.getWalkTargetValue(pos, this.level());
    }

    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return 0.0F;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor world, MobSpawnType spawnReason) {
        return this.getWalkTargetValue(this.blockPosition(), world) >= 0.0F;
    }

    public boolean isPathFinding() {
        return !this.getNavigation().isDone();
    }

    public boolean isPanicking() {
        if (this.brain.hasMemoryValue(MemoryModuleType.IS_PANICKING)) {
            return this.brain.getMemory(MemoryModuleType.IS_PANICKING).isPresent();
        } else {
            Iterator iterator = this.goalSelector.getAvailableGoals().iterator();

            WrappedGoal pathfindergoalwrapped;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                pathfindergoalwrapped = (WrappedGoal) iterator.next();
            } while (!pathfindergoalwrapped.isRunning() || !(pathfindergoalwrapped.getGoal() instanceof PanicGoal));

            return true;
        }
    }

    protected boolean shouldStayCloseToLeashHolder() {
        return true;
    }

    @Override
    public void closeRangeLeashBehaviour(Entity entity) {
        super.closeRangeLeashBehaviour(entity);
        if (this.shouldStayCloseToLeashHolder() && !this.isPanicking()) {
            this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            float f = 2.0F;
            float f1 = this.distanceTo(entity);
            Vec3 vec3d = (new Vec3(entity.getX() - this.getX(), entity.getY() - this.getY(), entity.getZ() - this.getZ())).normalize().scale((double) Math.max(f1 - 2.0F, 0.0F));

            this.getNavigation().moveTo(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z, this.followLeashSpeed());
        }

    }

    @Override
    public boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        this.restrictTo(leashHolder.blockPosition(), 5);
        return true;
    }

    protected double followLeashSpeed() {
        return 1.0D;
    }
}
