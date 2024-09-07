package net.minecraft.data.models.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModelLocationUtils {
    @Deprecated
    public static ResourceLocation decorateBlockModelLocation(String name) {
        return ResourceLocation.withDefaultNamespace("block/" + name);
    }

    public static ResourceLocation decorateItemModelLocation(String name) {
        return ResourceLocation.withDefaultNamespace("item/" + name);
    }

    public static ResourceLocation getModelLocation(Block block, String suffix) {
        ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourceLocation.withPath(path -> "block/" + path + suffix);
    }

    public static ResourceLocation getModelLocation(Block block) {
        ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourceLocation.withPrefix("block/");
    }

    public static ResourceLocation getModelLocation(Item item) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocation.withPrefix("item/");
    }

    public static ResourceLocation getModelLocation(Item item, String suffix) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocation.withPath(path -> "item/" + path + suffix);
    }
}
