package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public interface Equipable {
    EquipmentSlot getEquipmentSlot();

    default Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    default InteractionResultHolder<ItemStack> swapWithEquipmentSlot(Item item, Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        EquipmentSlot equipmentSlot = user.getEquipmentSlotForItem(itemStack);
        if (!user.canUseSlot(equipmentSlot)) {
            return InteractionResultHolder.pass(itemStack);
        } else {
            ItemStack itemStack2 = user.getItemBySlot(equipmentSlot);
            if ((!EnchantmentHelper.has(itemStack2, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || user.isCreative())
                && !ItemStack.matches(itemStack, itemStack2)) {
                if (!world.isClientSide()) {
                    user.awardStat(Stats.ITEM_USED.get(item));
                }

                ItemStack itemStack3 = itemStack2.isEmpty() ? itemStack : itemStack2.copyAndClear();
                ItemStack itemStack4 = user.isCreative() ? itemStack.copy() : itemStack.copyAndClear();
                user.setItemSlot(equipmentSlot, itemStack4);
                return InteractionResultHolder.sidedSuccess(itemStack3, world.isClientSide());
            } else {
                return InteractionResultHolder.fail(itemStack);
            }
        }
    }

    @Nullable
    static Equipable get(ItemStack stack) {
        Item equipable2 = stack.getItem();
        if (equipable2 instanceof Equipable) {
            return (Equipable)equipable2;
        } else {
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block var6 = blockItem.getBlock();
                if (var6 instanceof Equipable) {
                    return (Equipable)var6;
                }
            }

            return null;
        }
    }
}
