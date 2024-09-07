package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

public class LocateHidingPlace {
    public static OneShot<LivingEntity> create(int maxDistance, float walkSpeed, int preferredDistance) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.HOME),
                        context.registered(MemoryModuleType.HIDING_PLACE),
                        context.registered(MemoryModuleType.PATH),
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.registered(MemoryModuleType.BREED_TARGET),
                        context.registered(MemoryModuleType.INTERACTION_TARGET)
                    )
                    .apply(
                        context,
                        (walkTarget, home, hidingPlace, path, lookTarget, breedTarget, interactionTarget) -> (world, entity, time) -> {
                                world.getPoiManager()
                                    .find(
                                        poiType -> poiType.is(PoiTypes.HOME),
                                        pos -> true,
                                        entity.blockPosition(),
                                        preferredDistance + 1,
                                        PoiManager.Occupancy.ANY
                                    )
                                    .filter(pos -> pos.closerToCenterThan(entity.position(), (double)preferredDistance))
                                    .or(
                                        () -> world.getPoiManager()
                                                .getRandom(
                                                    poiType -> poiType.is(PoiTypes.HOME),
                                                    pos -> true,
                                                    PoiManager.Occupancy.ANY,
                                                    entity.blockPosition(),
                                                    maxDistance,
                                                    entity.getRandom()
                                                )
                                    )
                                    .or(() -> context.<GlobalPos>tryGet(home).map(GlobalPos::pos))
                                    .ifPresent(pos -> {
                                        path.erase();
                                        lookTarget.erase();
                                        breedTarget.erase();
                                        interactionTarget.erase();
                                        hidingPlace.set(GlobalPos.of(world.dimension(), pos));
                                        if (!pos.closerToCenterThan(entity.position(), (double)preferredDistance)) {
                                            walkTarget.set(new WalkTarget(pos, walkSpeed, preferredDistance));
                                        }
                                    });
                                return true;
                            }
                    )
        );
    }
}
