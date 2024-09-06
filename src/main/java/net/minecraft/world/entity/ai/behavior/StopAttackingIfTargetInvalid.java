package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class StopAttackingIfTargetInvalid {

    private static final int TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE = 200;

    public StopAttackingIfTargetInvalid() {}

    public static <E extends Mob> BehaviorControl<E> create(BiConsumer<E, LivingEntity> forgetCallback) {
        return StopAttackingIfTargetInvalid.create((entityliving) -> {
            return false;
        }, forgetCallback, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(Predicate<LivingEntity> alternativeCondition) {
        return StopAttackingIfTargetInvalid.create(alternativeCondition, (entityinsentient, entityliving) -> {
        }, true);
    }

    public static <E extends Mob> BehaviorControl<E> create() {
        return StopAttackingIfTargetInvalid.create((entityliving) -> {
            return false;
        }, (entityinsentient, entityliving) -> {
        }, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(Predicate<LivingEntity> alternativeCondition, BiConsumer<E, LivingEntity> forgetCallback, boolean shouldForgetIfTargetUnreachable) {
        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            return behaviorbuilder_b.group(behaviorbuilder_b.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_b.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)).apply(behaviorbuilder_b, (memoryaccessor, memoryaccessor1) -> {
                return (worldserver, entityinsentient, i) -> {
                    LivingEntity entityliving = (LivingEntity) behaviorbuilder_b.get(memoryaccessor);

                    if (entityinsentient.canAttack(entityliving) && (!shouldForgetIfTargetUnreachable || !StopAttackingIfTargetInvalid.isTiredOfTryingToReachTarget(entityinsentient, behaviorbuilder_b.tryGet(memoryaccessor1))) && entityliving.isAlive() && entityliving.level() == entityinsentient.level() && !alternativeCondition.test(entityliving)) {
                        return true;
                    } else {
                        // CraftBukkit start
                        LivingEntity old = entityinsentient.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                        EntityTargetEvent event = CraftEventFactory.callEntityTargetLivingEvent(entityinsentient, null, (old != null && !old.isAlive()) ? EntityTargetEvent.TargetReason.TARGET_DIED : EntityTargetEvent.TargetReason.FORGOT_TARGET);
                        if (event.isCancelled()) {
                            return false;
                        }
                        if (event.getTarget() != null) {
                            entityinsentient.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, ((CraftLivingEntity) event.getTarget()).getHandle());
                            return true;
                        }
                        // CraftBukkit end
                        forgetCallback.accept(entityinsentient, entityliving);
                        memoryaccessor.erase();
                        return true;
                    }
                };
            });
        });
    }

    private static boolean isTiredOfTryingToReachTarget(LivingEntity entityliving, Optional<Long> optional) {
        return optional.isPresent() && entityliving.level().getGameTime() - (Long) optional.get() > 200L;
    }
}
