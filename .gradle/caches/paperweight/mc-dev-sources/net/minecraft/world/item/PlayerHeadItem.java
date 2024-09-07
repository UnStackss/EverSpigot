package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public class PlayerHeadItem extends StandingAndWallBlockItem {
    public PlayerHeadItem(Block block, Block wallBlock, Item.Properties settings) {
        super(block, wallBlock, settings, Direction.DOWN);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResolvableProfile resolvableProfile = stack.get(DataComponents.PROFILE);
        return (Component)(resolvableProfile != null && resolvableProfile.name().isPresent()
            ? Component.translatable(this.getDescriptionId() + ".named", resolvableProfile.name().get())
            : super.getName(stack));
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        ResolvableProfile resolvableProfile = stack.get(DataComponents.PROFILE);
        if (resolvableProfile != null && !resolvableProfile.isResolved()) {
            resolvableProfile.resolve().thenAcceptAsync(profile -> stack.set(DataComponents.PROFILE, profile), SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        }
    }
}
