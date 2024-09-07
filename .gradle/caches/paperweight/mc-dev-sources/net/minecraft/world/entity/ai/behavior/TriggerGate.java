package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;

public class TriggerGate {
    public static <E extends LivingEntity> OneShot<E> triggerOneShuffled(List<Pair<? extends Trigger<? super E>, Integer>> weightedTasks) {
        return triggerGate(weightedTasks, GateBehavior.OrderPolicy.SHUFFLED, GateBehavior.RunningPolicy.RUN_ONE);
    }

    public static <E extends LivingEntity> OneShot<E> triggerGate(
        List<Pair<? extends Trigger<? super E>, Integer>> weightedTasks, GateBehavior.OrderPolicy order, GateBehavior.RunningPolicy runMode
    ) {
        ShufflingList<Trigger<? super E>> shufflingList = new ShufflingList<>();
        weightedTasks.forEach(task -> shufflingList.add((Trigger<? super E>)task.getFirst(), task.getSecond()));
        return BehaviorBuilder.create(context -> context.point((world, entity, time) -> {
                if (order == GateBehavior.OrderPolicy.SHUFFLED) {
                    shufflingList.shuffle();
                }

                for (Trigger<? super E> trigger : shufflingList) {
                    if (trigger.trigger(world, entity, time) && runMode == GateBehavior.RunningPolicy.RUN_ONE) {
                        break;
                    }
                }

                return true;
            }));
    }
}
