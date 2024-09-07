package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollAroundPoi {
    private static final int MIN_TIME_BETWEEN_STROLLS = 180;
    private static final int STROLL_MAX_XZ_DIST = 8;
    private static final int STROLL_MAX_Y_DIST = 6;

    public static OneShot<PathfinderMob> create(MemoryModuleType<GlobalPos> posModule, float walkSpeed, int maxDistance) {
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
                                Optional<Vec3> optional = Optional.ofNullable(LandRandomPos.getPos(entity, 8, 6));
                                walkTarget.setOrErase(optional.map(targetPos -> new WalkTarget(targetPos, walkSpeed, 1)));
                                mutableLong.setValue(time + 180L);
                                return true;
                            }
                        })
        );
    }
}
