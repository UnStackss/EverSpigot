package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class VillageBoundRandomStroll {
    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;

    public static OneShot<PathfinderMob> create(float walkSpeed) {
        return create(walkSpeed, 10, 7);
    }

    public static OneShot<PathfinderMob> create(float walkSpeed, int horizontalRange, int verticalRange) {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.WALK_TARGET))
                    .apply(
                        context,
                        walkTarget -> (world, entity, time) -> {
                                BlockPos blockPos = entity.blockPosition();
                                Vec3 vec3;
                                if (world.isVillage(blockPos)) {
                                    vec3 = LandRandomPos.getPos(entity, horizontalRange, verticalRange);
                                } else {
                                    SectionPos sectionPos = SectionPos.of(blockPos);
                                    SectionPos sectionPos2 = BehaviorUtils.findSectionClosestToVillage(world, sectionPos, 2);
                                    if (sectionPos2 != sectionPos) {
                                        vec3 = DefaultRandomPos.getPosTowards(
                                            entity, horizontalRange, verticalRange, Vec3.atBottomCenterOf(sectionPos2.center()), (float) (Math.PI / 2)
                                        );
                                    } else {
                                        vec3 = LandRandomPos.getPos(entity, horizontalRange, verticalRange);
                                    }
                                }

                                walkTarget.setOrErase(Optional.ofNullable(vec3).map(pos -> new WalkTarget(pos, walkSpeed, 0)));
                                return true;
                            }
                    )
        );
    }
}
