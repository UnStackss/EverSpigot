package net.minecraft.world.entity.ai.behavior.warden;

import net.minecraft.util.Unit;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class TryToSniff {
    private static final IntProvider SNIFF_COOLDOWN = UniformInt.of(100, 200);

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.IS_SNIFFING),
                        context.registered(MemoryModuleType.WALK_TARGET),
                        context.absent(MemoryModuleType.SNIFF_COOLDOWN),
                        context.present(MemoryModuleType.NEAREST_ATTACKABLE),
                        context.absent(MemoryModuleType.DISTURBANCE_LOCATION)
                    )
                    .apply(context, (isSniffing, walkTarget, sniffCooldown, nearestAttackable, disturbanceLocation) -> (world, entity, time) -> {
                            isSniffing.set(Unit.INSTANCE);
                            sniffCooldown.setWithExpiry(Unit.INSTANCE, (long)SNIFF_COOLDOWN.sample(world.getRandom()));
                            walkTarget.erase();
                            entity.setPose(Pose.SNIFFING);
                            return true;
                        })
        );
    }
}
