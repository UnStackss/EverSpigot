package net.minecraft.world.inventory;

import com.mojang.datafixers.util.Pair;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

class ArmorSlot extends Slot {
    private final LivingEntity owner;
    private final EquipmentSlot slot;
    @Nullable
    private final ResourceLocation emptyIcon;

    public ArmorSlot(
        Container inventory, LivingEntity entity, EquipmentSlot equipmentSlot, int index, int x, int y, @Nullable ResourceLocation backgroundSprite
    ) {
        super(inventory, index, x, y);
        this.owner = entity;
        this.slot = equipmentSlot;
        this.emptyIcon = backgroundSprite;
    }

    @Override
    public void setByPlayer(ItemStack stack, ItemStack previousStack) {
        this.owner.onEquipItem(this.slot, previousStack, stack);
        super.setByPlayer(stack, previousStack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.slot == this.owner.getEquipmentSlotForItem(stack);
    }

    @Override
    public boolean mayPickup(Player playerEntity) {
        ItemStack itemStack = this.getItem();
        return (itemStack.isEmpty() || playerEntity.isCreative() || !EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
            && super.mayPickup(playerEntity);
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return this.emptyIcon != null ? Pair.of(InventoryMenu.BLOCK_ATLAS, this.emptyIcon) : super.getNoItemIcon();
    }
}
