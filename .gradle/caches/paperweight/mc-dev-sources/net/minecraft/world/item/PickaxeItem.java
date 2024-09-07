package net.minecraft.world.item;

import net.minecraft.tags.BlockTags;

public class PickaxeItem extends DiggerItem {
    public PickaxeItem(Tier material, Item.Properties properties) {
        super(material, BlockTags.MINEABLE_WITH_PICKAXE, properties);
    }
}
