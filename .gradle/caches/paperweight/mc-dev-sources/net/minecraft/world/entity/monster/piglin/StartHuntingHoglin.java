package net.minecraft.world.entity.monster.piglin;

import java.util.List;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;

public class StartHuntingHoglin {
    public static OneShot<Piglin> create() {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN),
                        context.absent(MemoryModuleType.ANGRY_AT),
                        context.absent(MemoryModuleType.HUNTED_RECENTLY),
                        context.registered(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS)
                    )
                    .apply(
                        context,
                        (nearestVisibleHuntableHoglin, angryAt, huntedRecently, nearestVisibleAdultPiglins) -> (world, entity, time) -> {
                                if (!entity.isBaby()
                                    && !context.<List>tryGet(nearestVisibleAdultPiglins)
                                        .map(piglin -> piglin.stream().anyMatch(StartHuntingHoglin::hasHuntedRecently))
                                        .isPresent()) {
                                    Hoglin hoglin = context.get(nearestVisibleHuntableHoglin);
                                    PiglinAi.setAngerTarget(entity, hoglin);
                                    PiglinAi.dontKillAnyMoreHoglinsForAWhile(entity);
                                    PiglinAi.broadcastAngerTarget(entity, hoglin);
                                    context.<List>tryGet(nearestVisibleAdultPiglins)
                                        .ifPresent(piglin -> piglin.forEach(PiglinAi::dontKillAnyMoreHoglinsForAWhile));
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                    )
        );
    }

    private static boolean hasHuntedRecently(AbstractPiglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.HUNTED_RECENTLY);
    }
}
