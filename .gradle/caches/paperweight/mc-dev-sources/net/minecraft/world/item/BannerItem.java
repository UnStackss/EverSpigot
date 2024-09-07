package net.minecraft.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.apache.commons.lang3.Validate;

public class BannerItem extends StandingAndWallBlockItem {
    public BannerItem(Block bannerBlock, Block wallBannerBlock, Item.Properties settings) {
        super(bannerBlock, wallBannerBlock, settings, Direction.DOWN);
        Validate.isInstanceOf(AbstractBannerBlock.class, bannerBlock);
        Validate.isInstanceOf(AbstractBannerBlock.class, wallBannerBlock);
    }

    public static void appendHoverTextFromBannerBlockEntityTag(ItemStack stack, List<Component> tooltip) {
        BannerPatternLayers bannerPatternLayers = stack.get(DataComponents.BANNER_PATTERNS);
        if (bannerPatternLayers != null) {
            for (int i = 0; i < Math.min(bannerPatternLayers.layers().size(), 6); i++) {
                BannerPatternLayers.Layer layer = bannerPatternLayers.layers().get(i);
                tooltip.add(layer.description().withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public DyeColor getColor() {
        return ((AbstractBannerBlock)this.getBlock()).getColor();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        appendHoverTextFromBannerBlockEntityTag(stack, tooltip);
    }
}
