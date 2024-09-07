package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public interface CrossbowAttackMob extends RangedAttackMob {
    void setChargingCrossbow(boolean charging);

    @Nullable
    LivingEntity getTarget();

    void onCrossbowAttackPerformed();

    default void performCrossbowAttack(LivingEntity entity, float speed) {
        InteractionHand interactionHand = ProjectileUtil.getWeaponHoldingHand(entity, Items.CROSSBOW);
        ItemStack itemStack = entity.getItemInHand(interactionHand);
        if (itemStack.getItem() instanceof CrossbowItem crossbowItem) {
            crossbowItem.performShooting(
                entity.level(), entity, interactionHand, itemStack, speed, (float)(14 - entity.level().getDifficulty().getId() * 4), this.getTarget()
            );
        }

        this.onCrossbowAttackPerformed();
    }
}
