package net.minecraft.world.entity.ai.behavior.warden;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class SetWardenLookTarget {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.registered(MemoryModuleType.DISTURBANCE_LOCATION),
                        context.registered(MemoryModuleType.ROAR_TARGET),
                        context.absent(MemoryModuleType.ATTACK_TARGET)
                    )
                    .apply(
                        context,
                        (lookTarget, disturbanceLocation, roarTarget, attackTarget) -> (world, entity, time) -> {
                                Optional<BlockPos> optional = context.<LivingEntity>tryGet(roarTarget)
                                    .map(Entity::blockPosition)
                                    .or(() -> context.tryGet(disturbanceLocation));
                                if (optional.isEmpty()) {
                                    return false;
                                } else {
                                    lookTarget.set(new BlockPosTracker(optional.get()));
                                    return true;
                                }
                            }
                    )
        );
    }
}
