package net.minecraft.world.entity.monster.piglin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopAdmiringIfTiredOfTryingToReachItem {
    public static BehaviorControl<LivingEntity> create(int cooldown, int timeLimit) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(MemoryModuleType.ADMIRING_ITEM),
                        context.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM),
                        context.registered(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM),
                        context.registered(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)
                    )
                    .apply(
                        context, (admiringItem, nearestVisibleWantedItem, timeTryingToReachAdmireItem, disableWalkToAdmireItem) -> (world, entity, time) -> {
                                if (!entity.getOffhandItem().isEmpty()) {
                                    return false;
                                } else {
                                    Optional<Integer> optional = context.tryGet(timeTryingToReachAdmireItem);
                                    if (optional.isEmpty()) {
                                        timeTryingToReachAdmireItem.set(0);
                                    } else {
                                        int k = optional.get();
                                        if (k > cooldown) {
                                            admiringItem.erase();
                                            timeTryingToReachAdmireItem.erase();
                                            disableWalkToAdmireItem.setWithExpiry(true, (long)timeLimit);
                                        } else {
                                            timeTryingToReachAdmireItem.set(k + 1);
                                        }
                                    }

                                    return true;
                                }
                            }
                    )
        );
    }
}
