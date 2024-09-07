package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;

public class MeleeAttack {
    public static OneShot<Mob> create(int cooldown) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.present(MemoryModuleType.ATTACK_TARGET),
                        context.absent(MemoryModuleType.ATTACK_COOLING_DOWN),
                        context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(
                        context,
                        (lookTarget, attackTarget, attackCoolingDown, visibleMobs) -> (world, entity, time) -> {
                                LivingEntity livingEntity = context.get(attackTarget);
                                if (!isHoldingUsableProjectileWeapon(entity)
                                    && entity.isWithinMeleeAttackRange(livingEntity)
                                    && context.<NearestVisibleLivingEntities>get(visibleMobs).contains(livingEntity)) {
                                    lookTarget.set(new EntityTracker(livingEntity, true));
                                    entity.swing(InteractionHand.MAIN_HAND);
                                    entity.doHurtTarget(livingEntity);
                                    attackCoolingDown.setWithExpiry(true, (long)cooldown);
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                    )
        );
    }

    private static boolean isHoldingUsableProjectileWeapon(Mob mob) {
        return mob.isHolding(stack -> {
            Item item = stack.getItem();
            return item instanceof ProjectileWeaponItem && mob.canFireProjectileWeapon((ProjectileWeaponItem)item);
        });
    }
}
