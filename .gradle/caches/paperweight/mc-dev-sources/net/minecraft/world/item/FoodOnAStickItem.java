package net.minecraft.world.item;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class FoodOnAStickItem<T extends Entity & ItemSteerable> extends Item {
    private final EntityType<T> canInteractWith;
    private final int consumeItemDamage;

    public FoodOnAStickItem(Item.Properties settings, EntityType<T> target, int damagePerUse) {
        super(settings);
        this.canInteractWith = target;
        this.consumeItemDamage = damagePerUse;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (world.isClientSide) {
            return InteractionResultHolder.pass(itemStack);
        } else {
            Entity entity = user.getControlledVehicle();
            if (user.isPassenger() && entity instanceof ItemSteerable itemSteerable && entity.getType() == this.canInteractWith && itemSteerable.boost()) {
                EquipmentSlot equipmentSlot = LivingEntity.getSlotForHand(hand);
                ItemStack itemStack2 = itemStack.hurtAndConvertOnBreak(this.consumeItemDamage, Items.FISHING_ROD, user, equipmentSlot);
                return InteractionResultHolder.success(itemStack2);
            }

            user.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.pass(itemStack);
        }
    }
}
