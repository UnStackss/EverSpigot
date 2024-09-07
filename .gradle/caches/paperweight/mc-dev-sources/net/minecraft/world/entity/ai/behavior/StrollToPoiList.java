package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollToPoiList {
    public static BehaviorControl<Villager> create(
        MemoryModuleType<List<GlobalPos>> secondaryPositions,
        float speed,
        int completionRange,
        int primaryPositionActivationDistance,
        MemoryModuleType<GlobalPos> primaryPosition
    ) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            context -> context.group(context.registered(MemoryModuleType.WALK_TARGET), context.present(secondaryPositions), context.present(primaryPosition))
                    .apply(
                        context,
                        (walkTarget, secondary, primary) -> (world, entity, time) -> {
                                List<GlobalPos> list = context.get(secondary);
                                GlobalPos globalPos = context.get(primary);
                                if (list.isEmpty()) {
                                    return false;
                                } else {
                                    GlobalPos globalPos2 = list.get(world.getRandom().nextInt(list.size()));
                                    if (globalPos2 != null
                                        && world.dimension() == globalPos2.dimension()
                                        && globalPos.pos().closerToCenterThan(entity.position(), (double)primaryPositionActivationDistance)) {
                                        if (time > mutableLong.getValue()) {
                                            walkTarget.set(new WalkTarget(globalPos2.pos(), speed, completionRange));
                                            mutableLong.setValue(time + 100L);
                                        }

                                        return true;
                                    } else {
                                        return false;
                                    }
                                }
                            }
                    )
        );
    }
}
