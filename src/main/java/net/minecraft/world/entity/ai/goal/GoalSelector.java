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
    private final EnumSet<Goal.Flag> disabledFlags = EnumSet.noneOf(Goal.Flag.class);
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

    private static boolean goalContainsAnyFlags(WrappedGoal goal, EnumSet<Goal.Flag> controls) {
        for (Goal.Flag flag : goal.getFlags()) {
            if (controls.contains(flag)) {
                return true;
            }
        }

        return false;
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> goalsByControl) {
        for (Goal.Flag flag : goal.getFlags()) {
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
            if (wrappedGoal.isRunning() && (goalContainsAnyFlags(wrappedGoal, this.disabledFlags) || !wrappedGoal.canContinueToUse())) {
                wrappedGoal.stop();
            }
        }

        this.lockedFlags.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        profilerFiller.pop();
        profilerFiller.push("goalUpdate");

        for (WrappedGoal wrappedGoal2 : this.availableGoals) {
            if (!wrappedGoal2.isRunning()
                && !goalContainsAnyFlags(wrappedGoal2, this.disabledFlags)
                && goalCanBeReplacedForAllFlags(wrappedGoal2, this.lockedFlags)
                && wrappedGoal2.canUse()) {
                for (Goal.Flag flag : wrappedGoal2.getFlags()) {
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
        this.disabledFlags.add(control);
    }

    public void enableControlFlag(Goal.Flag control) {
        this.disabledFlags.remove(control);
    }

    public void setControlFlag(Goal.Flag control, boolean enabled) {
        if (enabled) {
            this.enableControlFlag(control);
        } else {
            this.disableControlFlag(control);
        }
    }
}
