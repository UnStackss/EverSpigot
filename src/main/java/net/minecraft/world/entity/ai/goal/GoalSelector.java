package net.minecraft.world.entity.ai.goal;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.util.profiling.ProfilerFiller;

public class GoalSelector {
    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override
        public boolean canUse() {
            return false;
        }
    }) {
        @Override
        public boolean isRunning() {
            return false;
        }
    };
    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap<>(Goal.Flag.class);
    private final Set<WrappedGoal> availableGoals = new ObjectLinkedOpenHashSet<>();
    private final Supplier<ProfilerFiller> profiler;
    private static final Goal.Flag[] GOAL_FLAG_VALUES = Goal.Flag.values(); // Paper - remove streams from pathfindergoalselector
    private final ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> goalTypes = new ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<>(Goal.Flag.class); // Paper - remove streams from pathfindergoalselector
    private int curRate;

    public GoalSelector(Supplier<ProfilerFiller> profiler) {
        this.profiler = profiler;
    }

    public void addGoal(int priority, Goal goal) {
        this.availableGoals.add(new WrappedGoal(priority, goal));
    }

    @VisibleForTesting
    public void removeAllGoals(Predicate<Goal> predicate) {
        this.availableGoals.removeIf(goal -> predicate.test(goal.getGoal()));
    }

    // Paper start
    public boolean inactiveTick() {
        this.curRate++;
        return this.curRate % 3 == 0; // TODO newGoalRate was already unused in 1.20.4, check if this is correct
    }
    public boolean hasTasks() {
        for (WrappedGoal task : this.availableGoals) {
            if (task.isRunning()) {
                return true;
            }
        }
        return false;
    }
    // Paper end
    public void removeGoal(Goal goal) {
        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.getGoal() == goal && wrappedGoal.isRunning()) {
                wrappedGoal.stop();
            }
        }

        this.availableGoals.removeIf(wrappedGoalx -> wrappedGoalx.getGoal() == goal);
    }

    // Paper start
    private static boolean goalContainsAnyFlags(WrappedGoal goal, ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<Goal.Flag> controls) {
        return goal.getFlags().hasCommonElements(controls);
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> goalsByControl) {
        long flagIterator = goal.getFlags().getBackingSet();
        int wrappedGoalSize = goal.getFlags().size();
        for (int i = 0; i < wrappedGoalSize; ++i) {
            final Goal.Flag flag = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
            flagIterator ^= ca.spottedleaf.concurrentutil.util.IntegerUtil.getTrailingBit(flagIterator);
            // Paper end
            if (!goalsByControl.getOrDefault(flag, NO_GOAL).canBeReplacedBy(goal)) {
                return false;
            }
        }

        return true;
    }

    public void tick() {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("goalCleanup");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (goalContainsAnyFlags(wrappedGoal, this.goalTypes) || !wrappedGoal.canContinueToUse())) { // Paper - Perf: optimize goal types by removing streams
                wrappedGoal.stop();
            }
        }

        this.lockedFlags.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        profilerFiller.pop();
        profilerFiller.push("goalUpdate");

        for (WrappedGoal wrappedGoal2 : this.availableGoals) {
            // Paper start
            if (!wrappedGoal2.isRunning() && !goalContainsAnyFlags(wrappedGoal2, this.goalTypes) && goalCanBeReplacedForAllFlags(wrappedGoal2, this.lockedFlags) && wrappedGoal2.canUse()) {
                long flagIterator = wrappedGoal2.getFlags().getBackingSet();
                int wrappedGoalSize = wrappedGoal2.getFlags().size();
                for (int i = 0; i < wrappedGoalSize; ++i) {
                    final Goal.Flag flag = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
                    flagIterator ^= ca.spottedleaf.concurrentutil.util.IntegerUtil.getTrailingBit(flagIterator);
                    // Paper end
                    WrappedGoal wrappedGoal3 = this.lockedFlags.getOrDefault(flag, NO_GOAL);
                    wrappedGoal3.stop();
                    this.lockedFlags.put(flag, wrappedGoal2);
                }

                wrappedGoal2.start();
            }
        }

        profilerFiller.pop();
        this.tickRunningGoals(true);
    }

    public void tickRunningGoals(boolean tickAll) {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("goalTick");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (tickAll || wrappedGoal.requiresUpdateEveryTick())) {
                wrappedGoal.tick();
            }
        }

        profilerFiller.pop();
    }

    public Set<WrappedGoal> getAvailableGoals() {
        return this.availableGoals;
    }

    public void disableControlFlag(Goal.Flag control) {
        this.goalTypes.addUnchecked(control); // Paper - remove streams from pathfindergoalselector
    }

    public void enableControlFlag(Goal.Flag control) {
        this.goalTypes.removeUnchecked(control); // Paper - remove streams from pathfindergoalselector
    }

    public void setControlFlag(Goal.Flag control, boolean enabled) {
        if (enabled) {
            this.enableControlFlag(control);
        } else {
            this.disableControlFlag(control);
        }
    }
}
