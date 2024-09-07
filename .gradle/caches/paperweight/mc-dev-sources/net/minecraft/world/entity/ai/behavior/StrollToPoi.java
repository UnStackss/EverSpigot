package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollToPoi {
    public static BehaviorControl<PathfinderMob> create(MemoryModuleType<GlobalPos> posModule, float walkSpeed, int completionRange, int maxDistance) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            context -> context.group(context.registered(MemoryModuleType.WALK_TARGET), context.present(posModule))
                    .apply(context, (walkTarget, pos) -> (world, entity, time) -> {
                            GlobalPos globalPos = context.get(pos);
                            if (world.dimension() != globalPos.dimension() || !globalPos.pos().closerToCenterThan(entity.position(), (double)maxDistance)) {
                                return false;
                            } else if (time <= mutableLong.getValue()) {
                                return true;
                            } else {
                                walkTarget.set(new WalkTarget(globalPos.pos(), walkSpeed, completionRange));
                                mutableLong.setValue(time + 80L);
                                return true;
                            }
                        })
        );
    }
}
