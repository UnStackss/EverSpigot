package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class PlayTagWithOtherKids {
    private static final int MAX_FLEE_XZ_DIST = 20;
    private static final int MAX_FLEE_Y_DIST = 8;
    private static final float FLEE_SPEED_MODIFIER = 0.6F;
    private static final float CHASE_SPEED_MODIFIER = 0.6F;
    private static final int MAX_CHASERS_PER_TARGET = 5;
    private static final int AVERAGE_WAIT_TIME_BETWEEN_RUNS = 10;

    public static BehaviorControl<PathfinderMob> create() {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(MemoryModuleType.VISIBLE_VILLAGER_BABIES),
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.registered(MemoryModuleType.INTERACTION_TARGET)
                    )
                    .apply(context, (visibleVillagerBabies, walkTarget, lookTarget, interactionTarget) -> (world, entity, time) -> {
                            if (world.getRandom().nextInt(10) != 0) {
                                return false;
                            } else {
                                List<LivingEntity> list = context.get(visibleVillagerBabies);
                                Optional<LivingEntity> optional = list.stream().filter(baby -> isFriendChasingMe(entity, baby)).findAny();
                                if (!optional.isPresent()) {
                                    Optional<LivingEntity> optional2 = findSomeoneBeingChased(list);
                                    if (optional2.isPresent()) {
                                        chaseKid(interactionTarget, lookTarget, walkTarget, optional2.get());
                                        return true;
                                    } else {
                                        list.stream().findAny().ifPresent(baby -> chaseKid(interactionTarget, lookTarget, walkTarget, baby));
                                        return true;
                                    }
                                } else {
                                    for (int i = 0; i < 10; i++) {
                                        Vec3 vec3 = LandRandomPos.getPos(entity, 20, 8);
                                        if (vec3 != null && world.isVillage(BlockPos.containing(vec3))) {
                                            walkTarget.set(new WalkTarget(vec3, 0.6F, 0));
                                            break;
                                        }
                                    }

                                    return true;
                                }
                            }
                        })
        );
    }

    private static void chaseKid(
        MemoryAccessor<?, LivingEntity> interactionTarget,
        MemoryAccessor<?, PositionTracker> lookTarget,
        MemoryAccessor<?, WalkTarget> walkTarget,
        LivingEntity baby
    ) {
        interactionTarget.set(baby);
        lookTarget.set(new EntityTracker(baby, true));
        walkTarget.set(new WalkTarget(new EntityTracker(baby, false), 0.6F, 1));
    }

    private static Optional<LivingEntity> findSomeoneBeingChased(List<LivingEntity> babies) {
        Map<LivingEntity, Integer> map = checkHowManyChasersEachFriendHas(babies);
        return map.entrySet()
            .stream()
            .sorted(Comparator.comparingInt(Entry::getValue))
            .filter(entry -> entry.getValue() > 0 && entry.getValue() <= 5)
            .map(Entry::getKey)
            .findFirst();
    }

    private static Map<LivingEntity, Integer> checkHowManyChasersEachFriendHas(List<LivingEntity> babies) {
        Map<LivingEntity, Integer> map = Maps.newHashMap();
        babies.stream()
            .filter(PlayTagWithOtherKids::isChasingSomeone)
            .forEach(baby -> map.compute(whoAreYouChasing(baby), (target, count) -> count == null ? 1 : count + 1));
        return map;
    }

    private static LivingEntity whoAreYouChasing(LivingEntity baby) {
        return baby.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
    }

    private static boolean isChasingSomeone(LivingEntity baby) {
        return baby.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    private static boolean isFriendChasingMe(LivingEntity entity, LivingEntity baby) {
        return baby.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).filter(target -> target == entity).isPresent();
    }
}
