package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.util.Mth;

public abstract class Goal {
    private final EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class); // Paper unused, but dummy to prevent plugins from crashing as hard. Theyll need to support paper in a special case if this is super important, but really doesn't seem like it would be.
    private final ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> goalTypes = new ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<>(Goal.Flag.class); // Paper - remove streams from pathfindergoalselector

    // Paper start - remove streams from pathfindergoalselector; make sure types are not empty
    public Goal() {
        if (this.goalTypes.size() == 0) {
            this.goalTypes.addUnchecked(Flag.UNKNOWN_BEHAVIOR);
        }
    }
    // Paper end - remove streams from pathfindergoalselector

    public abstract boolean canUse();

    public boolean canContinueToUse() {
        return this.canUse();
    }

    public boolean isInterruptable() {
        return true;
    }

    public void start() {
    }

    public void stop() {
    }

    public boolean requiresUpdateEveryTick() {
        return false;
    }

    public void tick() {
    }

    public void setFlags(EnumSet<Goal.Flag> controls) {
        // Paper start - remove streams from pathfindergoalselector
        this.goalTypes.clear();
        this.goalTypes.addAllUnchecked(controls);
        if (this.goalTypes.size() == 0) {
            this.goalTypes.addUnchecked(Flag.UNKNOWN_BEHAVIOR);
        }
        // Paper end - remove streams from pathfindergoalselector
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    // Paper start - remove streams from pathfindergoalselector
    public ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<Goal.Flag> getFlags() {
        return this.goalTypes;
        // Paper end - remove streams from pathfindergoalselector
    }

    protected int adjustedTickDelay(int ticks) {
        return this.requiresUpdateEveryTick() ? ticks : reducedTickDelay(ticks);
    }

    protected static int reducedTickDelay(int serverTicks) {
        return Mth.positiveCeilDiv(serverTicks, 2);
    }

    // Paper start - Mob goal api
    private com.destroystokyo.paper.entity.ai.PaperVanillaGoal<?> vanillaGoal;
    public <T extends org.bukkit.entity.Mob> com.destroystokyo.paper.entity.ai.Goal<T> asPaperVanillaGoal() {
        if(this.vanillaGoal == null) {
            this.vanillaGoal = new com.destroystokyo.paper.entity.ai.PaperVanillaGoal<>(this);
        }
        //noinspection unchecked
        return (com.destroystokyo.paper.entity.ai.Goal<T>) this.vanillaGoal;
    }
    // Paper end - Mob goal api

    public static enum Flag {
        UNKNOWN_BEHAVIOR, // Paper - add UNKNOWN_BEHAVIOR
        MOVE,
        LOOK,
        JUMP,
        TARGET;
    }
}
