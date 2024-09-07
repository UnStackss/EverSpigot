package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Items;

public class StopHoldingItemIfNoLongerAdmiring {
    public static BehaviorControl<Piglin> create() {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.ADMIRING_ITEM)).apply(context, admiringItem -> (world, entity, time) -> {
                        if (!entity.getOffhandItem().isEmpty() && !entity.getOffhandItem().is(Items.SHIELD)) {
                            PiglinAi.stopHoldingOffHandItem(entity, true);
                            return true;
                        } else {
                            return false;
                        }
                    })
        );
    }
}
