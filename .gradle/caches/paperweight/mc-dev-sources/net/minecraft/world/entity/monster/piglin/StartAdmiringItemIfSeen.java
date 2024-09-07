package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;

public class StartAdmiringItemIfSeen {
    public static BehaviorControl<LivingEntity> create(int duration) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM),
                        context.absent(MemoryModuleType.ADMIRING_ITEM),
                        context.absent(MemoryModuleType.ADMIRING_DISABLED),
                        context.absent(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)
                    )
                    .apply(context, (nearestVisibleWantedItem, admiringItem, admiringDisabled, disableWalkToAdmireItem) -> (world, entity, time) -> {
                            ItemEntity itemEntity = context.get(nearestVisibleWantedItem);
                            if (!PiglinAi.isLovedItem(itemEntity.getItem())) {
                                return false;
                            } else {
                                admiringItem.setWithExpiry(true, (long)duration);
                                return true;
                            }
                        })
        );
    }
}
