package net.minecraft.data.models.model;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class TextureMapping {
    private final Map<TextureSlot, ResourceLocation> slots = Maps.newHashMap();
    private final Set<TextureSlot> forcedSlots = Sets.newHashSet();

    public TextureMapping put(TextureSlot key, ResourceLocation id) {
        this.slots.put(key, id);
        return this;
    }

    public TextureMapping putForced(TextureSlot key, ResourceLocation id) {
        this.slots.put(key, id);
        this.forcedSlots.add(key);
        return this;
    }

    public Stream<TextureSlot> getForced() {
        return this.forcedSlots.stream();
    }

    public TextureMapping copySlot(TextureSlot parent, TextureSlot child) {
        this.slots.put(child, this.slots.get(parent));
        return this;
    }

    public TextureMapping copyForced(TextureSlot parent, TextureSlot child) {
        this.slots.put(child, this.slots.get(parent));
        this.forcedSlots.add(child);
        return this;
    }

    public ResourceLocation get(TextureSlot key) {
        for (TextureSlot textureSlot = key; textureSlot != null; textureSlot = textureSlot.getParent()) {
            ResourceLocation resourceLocation = this.slots.get(textureSlot);
            if (resourceLocation != null) {
                return resourceLocation;
            }
        }

        throw new IllegalStateException("Can't find texture for slot " + key);
    }

    public TextureMapping copyAndUpdate(TextureSlot key, ResourceLocation id) {
        TextureMapping textureMapping = new TextureMapping();
        textureMapping.slots.putAll(this.slots);
        textureMapping.forcedSlots.addAll(this.forcedSlots);
        textureMapping.put(key, id);
        return textureMapping;
    }

    public static TextureMapping cube(Block block) {
        ResourceLocation resourceLocation = getBlockTexture(block);
        return cube(resourceLocation);
    }

    public static TextureMapping defaultTexture(Block block) {
        ResourceLocation resourceLocation = getBlockTexture(block);
        return defaultTexture(resourceLocation);
    }

    public static TextureMapping defaultTexture(ResourceLocation id) {
        return new TextureMapping().put(TextureSlot.TEXTURE, id);
    }

    public static TextureMapping cube(ResourceLocation id) {
        return new TextureMapping().put(TextureSlot.ALL, id);
    }

    public static TextureMapping cross(Block block) {
        return singleSlot(TextureSlot.CROSS, getBlockTexture(block));
    }

    public static TextureMapping cross(ResourceLocation id) {
        return singleSlot(TextureSlot.CROSS, id);
    }

    public static TextureMapping plant(Block block) {
        return singleSlot(TextureSlot.PLANT, getBlockTexture(block));
    }

    public static TextureMapping plant(ResourceLocation id) {
        return singleSlot(TextureSlot.PLANT, id);
    }

    public static TextureMapping rail(Block block) {
        return singleSlot(TextureSlot.RAIL, getBlockTexture(block));
    }

    public static TextureMapping rail(ResourceLocation id) {
        return singleSlot(TextureSlot.RAIL, id);
    }

    public static TextureMapping wool(Block block) {
        return singleSlot(TextureSlot.WOOL, getBlockTexture(block));
    }

    public static TextureMapping flowerbed(Block block) {
        return new TextureMapping().put(TextureSlot.FLOWERBED, getBlockTexture(block)).put(TextureSlot.STEM, getBlockTexture(block, "_stem"));
    }

    public static TextureMapping wool(ResourceLocation id) {
        return singleSlot(TextureSlot.WOOL, id);
    }

    public static TextureMapping stem(Block block) {
        return singleSlot(TextureSlot.STEM, getBlockTexture(block));
    }

    public static TextureMapping attachedStem(Block stem, Block upper) {
        return new TextureMapping().put(TextureSlot.STEM, getBlockTexture(stem)).put(TextureSlot.UPPER_STEM, getBlockTexture(upper));
    }

    public static TextureMapping pattern(Block block) {
        return singleSlot(TextureSlot.PATTERN, getBlockTexture(block));
    }

    public static TextureMapping fan(Block block) {
        return singleSlot(TextureSlot.FAN, getBlockTexture(block));
    }

    public static TextureMapping crop(ResourceLocation id) {
        return singleSlot(TextureSlot.CROP, id);
    }

    public static TextureMapping pane(Block block, Block top) {
        return new TextureMapping().put(TextureSlot.PANE, getBlockTexture(block)).put(TextureSlot.EDGE, getBlockTexture(top, "_top"));
    }

    public static TextureMapping singleSlot(TextureSlot key, ResourceLocation id) {
        return new TextureMapping().put(key, id);
    }

    public static TextureMapping column(Block block) {
        return new TextureMapping().put(TextureSlot.SIDE, getBlockTexture(block, "_side")).put(TextureSlot.END, getBlockTexture(block, "_top"));
    }

    public static TextureMapping cubeTop(Block block) {
        return new TextureMapping().put(TextureSlot.SIDE, getBlockTexture(block, "_side")).put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping pottedAzalea(Block block) {
        return new TextureMapping()
            .put(TextureSlot.PLANT, getBlockTexture(block, "_plant"))
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping logColumn(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block))
            .put(TextureSlot.END, getBlockTexture(block, "_top"))
            .put(TextureSlot.PARTICLE, getBlockTexture(block));
    }

    public static TextureMapping column(ResourceLocation side, ResourceLocation end) {
        return new TextureMapping().put(TextureSlot.SIDE, side).put(TextureSlot.END, end);
    }

    public static TextureMapping fence(Block block) {
        return new TextureMapping()
            .put(TextureSlot.TEXTURE, getBlockTexture(block))
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping customParticle(Block block) {
        return new TextureMapping().put(TextureSlot.TEXTURE, getBlockTexture(block)).put(TextureSlot.PARTICLE, getBlockTexture(block, "_particle"));
    }

    public static TextureMapping cubeBottomTop(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping cubeBottomTopWithWall(Block block) {
        ResourceLocation resourceLocation = getBlockTexture(block);
        return new TextureMapping()
            .put(TextureSlot.WALL, resourceLocation)
            .put(TextureSlot.SIDE, resourceLocation)
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping columnWithWall(Block block) {
        ResourceLocation resourceLocation = getBlockTexture(block);
        return new TextureMapping()
            .put(TextureSlot.TEXTURE, resourceLocation)
            .put(TextureSlot.WALL, resourceLocation)
            .put(TextureSlot.SIDE, resourceLocation)
            .put(TextureSlot.END, getBlockTexture(block, "_top"));
    }

    public static TextureMapping door(ResourceLocation top, ResourceLocation bottom) {
        return new TextureMapping().put(TextureSlot.TOP, top).put(TextureSlot.BOTTOM, bottom);
    }

    public static TextureMapping door(Block block) {
        return new TextureMapping().put(TextureSlot.TOP, getBlockTexture(block, "_top")).put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping particle(Block block) {
        return new TextureMapping().put(TextureSlot.PARTICLE, getBlockTexture(block));
    }

    public static TextureMapping particle(ResourceLocation id) {
        return new TextureMapping().put(TextureSlot.PARTICLE, id);
    }

    public static TextureMapping fire0(Block block) {
        return new TextureMapping().put(TextureSlot.FIRE, getBlockTexture(block, "_0"));
    }

    public static TextureMapping fire1(Block block) {
        return new TextureMapping().put(TextureSlot.FIRE, getBlockTexture(block, "_1"));
    }

    public static TextureMapping lantern(Block block) {
        return new TextureMapping().put(TextureSlot.LANTERN, getBlockTexture(block));
    }

    public static TextureMapping torch(Block block) {
        return new TextureMapping().put(TextureSlot.TORCH, getBlockTexture(block));
    }

    public static TextureMapping torch(ResourceLocation id) {
        return new TextureMapping().put(TextureSlot.TORCH, id);
    }

    public static TextureMapping trialSpawner(Block block, String side, String top) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, side))
            .put(TextureSlot.TOP, getBlockTexture(block, top))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping vault(Block block, String front, String side, String top, String bottom) {
        return new TextureMapping()
            .put(TextureSlot.FRONT, getBlockTexture(block, front))
            .put(TextureSlot.SIDE, getBlockTexture(block, side))
            .put(TextureSlot.TOP, getBlockTexture(block, top))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, bottom));
    }

    public static TextureMapping particleFromItem(Item item) {
        return new TextureMapping().put(TextureSlot.PARTICLE, getItemTexture(item));
    }

    public static TextureMapping commandBlock(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.BACK, getBlockTexture(block, "_back"));
    }

    public static TextureMapping orientableCube(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping orientableCubeOnlyTop(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping orientableCubeSameEnds(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.END, getBlockTexture(block, "_end"));
    }

    public static TextureMapping top(Block top) {
        return new TextureMapping().put(TextureSlot.TOP, getBlockTexture(top, "_top"));
    }

    public static TextureMapping craftingTable(Block block, Block bottom) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(block, "_front"))
            .put(TextureSlot.DOWN, getBlockTexture(bottom))
            .put(TextureSlot.UP, getBlockTexture(block, "_top"))
            .put(TextureSlot.NORTH, getBlockTexture(block, "_front"))
            .put(TextureSlot.EAST, getBlockTexture(block, "_side"))
            .put(TextureSlot.SOUTH, getBlockTexture(block, "_side"))
            .put(TextureSlot.WEST, getBlockTexture(block, "_front"));
    }

    public static TextureMapping fletchingTable(Block frontTopSideBlock, Block downBlock) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(frontTopSideBlock, "_front"))
            .put(TextureSlot.DOWN, getBlockTexture(downBlock))
            .put(TextureSlot.UP, getBlockTexture(frontTopSideBlock, "_top"))
            .put(TextureSlot.NORTH, getBlockTexture(frontTopSideBlock, "_front"))
            .put(TextureSlot.SOUTH, getBlockTexture(frontTopSideBlock, "_front"))
            .put(TextureSlot.EAST, getBlockTexture(frontTopSideBlock, "_side"))
            .put(TextureSlot.WEST, getBlockTexture(frontTopSideBlock, "_side"));
    }

    public static TextureMapping snifferEgg(String age) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.SNIFFER_EGG, age + "_north"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.SNIFFER_EGG, age + "_bottom"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.SNIFFER_EGG, age + "_top"))
            .put(TextureSlot.NORTH, getBlockTexture(Blocks.SNIFFER_EGG, age + "_north"))
            .put(TextureSlot.SOUTH, getBlockTexture(Blocks.SNIFFER_EGG, age + "_south"))
            .put(TextureSlot.EAST, getBlockTexture(Blocks.SNIFFER_EGG, age + "_east"))
            .put(TextureSlot.WEST, getBlockTexture(Blocks.SNIFFER_EGG, age + "_west"));
    }

    public static TextureMapping campfire(Block block) {
        return new TextureMapping().put(TextureSlot.LIT_LOG, getBlockTexture(block, "_log_lit")).put(TextureSlot.FIRE, getBlockTexture(block, "_fire"));
    }

    public static TextureMapping candleCake(Block block, boolean lit) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.CAKE, "_side"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.CAKE, "_bottom"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.CAKE, "_top"))
            .put(TextureSlot.SIDE, getBlockTexture(Blocks.CAKE, "_side"))
            .put(TextureSlot.CANDLE, getBlockTexture(block, lit ? "_lit" : ""));
    }

    public static TextureMapping cauldron(ResourceLocation content) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.CAULDRON, "_side"))
            .put(TextureSlot.SIDE, getBlockTexture(Blocks.CAULDRON, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.CAULDRON, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.CAULDRON, "_bottom"))
            .put(TextureSlot.INSIDE, getBlockTexture(Blocks.CAULDRON, "_inner"))
            .put(TextureSlot.CONTENT, content);
    }

    public static TextureMapping sculkShrieker(boolean canSummon) {
        String string = canSummon ? "_can_summon" : "";
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.SCULK_SHRIEKER, "_bottom"))
            .put(TextureSlot.SIDE, getBlockTexture(Blocks.SCULK_SHRIEKER, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.SCULK_SHRIEKER, "_top"))
            .put(TextureSlot.INNER_TOP, getBlockTexture(Blocks.SCULK_SHRIEKER, string + "_inner_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.SCULK_SHRIEKER, "_bottom"));
    }

    public static TextureMapping layer0(Item item) {
        return new TextureMapping().put(TextureSlot.LAYER0, getItemTexture(item));
    }

    public static TextureMapping layer0(Block block) {
        return new TextureMapping().put(TextureSlot.LAYER0, getBlockTexture(block));
    }

    public static TextureMapping layer0(ResourceLocation id) {
        return new TextureMapping().put(TextureSlot.LAYER0, id);
    }

    public static TextureMapping layered(ResourceLocation layer0, ResourceLocation layer1) {
        return new TextureMapping().put(TextureSlot.LAYER0, layer0).put(TextureSlot.LAYER1, layer1);
    }

    public static TextureMapping layered(ResourceLocation layer0, ResourceLocation layer1, ResourceLocation layer2) {
        return new TextureMapping().put(TextureSlot.LAYER0, layer0).put(TextureSlot.LAYER1, layer1).put(TextureSlot.LAYER2, layer2);
    }

    public static ResourceLocation getBlockTexture(Block block) {
        ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourceLocation.withPrefix("block/");
    }

    public static ResourceLocation getBlockTexture(Block block, String suffix) {
        ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourceLocation.withPath(path -> "block/" + path + suffix);
    }

    public static ResourceLocation getItemTexture(Item item) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocation.withPrefix("item/");
    }

    public static ResourceLocation getItemTexture(Item item, String suffix) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocation.withPath(path -> "item/" + path + suffix);
    }
}
