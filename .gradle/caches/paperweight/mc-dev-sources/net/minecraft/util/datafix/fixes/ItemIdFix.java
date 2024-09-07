package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemIdFix extends DataFix {
    public static final Int2ObjectMap<String> ITEM_NAMES = DataFixUtils.make(new Int2ObjectOpenHashMap<>(), map -> {
        map.put(1, "minecraft:stone");
        map.put(2, "minecraft:grass");
        map.put(3, "minecraft:dirt");
        map.put(4, "minecraft:cobblestone");
        map.put(5, "minecraft:planks");
        map.put(6, "minecraft:sapling");
        map.put(7, "minecraft:bedrock");
        map.put(8, "minecraft:flowing_water");
        map.put(9, "minecraft:water");
        map.put(10, "minecraft:flowing_lava");
        map.put(11, "minecraft:lava");
        map.put(12, "minecraft:sand");
        map.put(13, "minecraft:gravel");
        map.put(14, "minecraft:gold_ore");
        map.put(15, "minecraft:iron_ore");
        map.put(16, "minecraft:coal_ore");
        map.put(17, "minecraft:log");
        map.put(18, "minecraft:leaves");
        map.put(19, "minecraft:sponge");
        map.put(20, "minecraft:glass");
        map.put(21, "minecraft:lapis_ore");
        map.put(22, "minecraft:lapis_block");
        map.put(23, "minecraft:dispenser");
        map.put(24, "minecraft:sandstone");
        map.put(25, "minecraft:noteblock");
        map.put(27, "minecraft:golden_rail");
        map.put(28, "minecraft:detector_rail");
        map.put(29, "minecraft:sticky_piston");
        map.put(30, "minecraft:web");
        map.put(31, "minecraft:tallgrass");
        map.put(32, "minecraft:deadbush");
        map.put(33, "minecraft:piston");
        map.put(35, "minecraft:wool");
        map.put(37, "minecraft:yellow_flower");
        map.put(38, "minecraft:red_flower");
        map.put(39, "minecraft:brown_mushroom");
        map.put(40, "minecraft:red_mushroom");
        map.put(41, "minecraft:gold_block");
        map.put(42, "minecraft:iron_block");
        map.put(43, "minecraft:double_stone_slab");
        map.put(44, "minecraft:stone_slab");
        map.put(45, "minecraft:brick_block");
        map.put(46, "minecraft:tnt");
        map.put(47, "minecraft:bookshelf");
        map.put(48, "minecraft:mossy_cobblestone");
        map.put(49, "minecraft:obsidian");
        map.put(50, "minecraft:torch");
        map.put(51, "minecraft:fire");
        map.put(52, "minecraft:mob_spawner");
        map.put(53, "minecraft:oak_stairs");
        map.put(54, "minecraft:chest");
        map.put(56, "minecraft:diamond_ore");
        map.put(57, "minecraft:diamond_block");
        map.put(58, "minecraft:crafting_table");
        map.put(60, "minecraft:farmland");
        map.put(61, "minecraft:furnace");
        map.put(62, "minecraft:lit_furnace");
        map.put(65, "minecraft:ladder");
        map.put(66, "minecraft:rail");
        map.put(67, "minecraft:stone_stairs");
        map.put(69, "minecraft:lever");
        map.put(70, "minecraft:stone_pressure_plate");
        map.put(72, "minecraft:wooden_pressure_plate");
        map.put(73, "minecraft:redstone_ore");
        map.put(76, "minecraft:redstone_torch");
        map.put(77, "minecraft:stone_button");
        map.put(78, "minecraft:snow_layer");
        map.put(79, "minecraft:ice");
        map.put(80, "minecraft:snow");
        map.put(81, "minecraft:cactus");
        map.put(82, "minecraft:clay");
        map.put(84, "minecraft:jukebox");
        map.put(85, "minecraft:fence");
        map.put(86, "minecraft:pumpkin");
        map.put(87, "minecraft:netherrack");
        map.put(88, "minecraft:soul_sand");
        map.put(89, "minecraft:glowstone");
        map.put(90, "minecraft:portal");
        map.put(91, "minecraft:lit_pumpkin");
        map.put(95, "minecraft:stained_glass");
        map.put(96, "minecraft:trapdoor");
        map.put(97, "minecraft:monster_egg");
        map.put(98, "minecraft:stonebrick");
        map.put(99, "minecraft:brown_mushroom_block");
        map.put(100, "minecraft:red_mushroom_block");
        map.put(101, "minecraft:iron_bars");
        map.put(102, "minecraft:glass_pane");
        map.put(103, "minecraft:melon_block");
        map.put(106, "minecraft:vine");
        map.put(107, "minecraft:fence_gate");
        map.put(108, "minecraft:brick_stairs");
        map.put(109, "minecraft:stone_brick_stairs");
        map.put(110, "minecraft:mycelium");
        map.put(111, "minecraft:waterlily");
        map.put(112, "minecraft:nether_brick");
        map.put(113, "minecraft:nether_brick_fence");
        map.put(114, "minecraft:nether_brick_stairs");
        map.put(116, "minecraft:enchanting_table");
        map.put(119, "minecraft:end_portal");
        map.put(120, "minecraft:end_portal_frame");
        map.put(121, "minecraft:end_stone");
        map.put(122, "minecraft:dragon_egg");
        map.put(123, "minecraft:redstone_lamp");
        map.put(125, "minecraft:double_wooden_slab");
        map.put(126, "minecraft:wooden_slab");
        map.put(127, "minecraft:cocoa");
        map.put(128, "minecraft:sandstone_stairs");
        map.put(129, "minecraft:emerald_ore");
        map.put(130, "minecraft:ender_chest");
        map.put(131, "minecraft:tripwire_hook");
        map.put(133, "minecraft:emerald_block");
        map.put(134, "minecraft:spruce_stairs");
        map.put(135, "minecraft:birch_stairs");
        map.put(136, "minecraft:jungle_stairs");
        map.put(137, "minecraft:command_block");
        map.put(138, "minecraft:beacon");
        map.put(139, "minecraft:cobblestone_wall");
        map.put(141, "minecraft:carrots");
        map.put(142, "minecraft:potatoes");
        map.put(143, "minecraft:wooden_button");
        map.put(145, "minecraft:anvil");
        map.put(146, "minecraft:trapped_chest");
        map.put(147, "minecraft:light_weighted_pressure_plate");
        map.put(148, "minecraft:heavy_weighted_pressure_plate");
        map.put(151, "minecraft:daylight_detector");
        map.put(152, "minecraft:redstone_block");
        map.put(153, "minecraft:quartz_ore");
        map.put(154, "minecraft:hopper");
        map.put(155, "minecraft:quartz_block");
        map.put(156, "minecraft:quartz_stairs");
        map.put(157, "minecraft:activator_rail");
        map.put(158, "minecraft:dropper");
        map.put(159, "minecraft:stained_hardened_clay");
        map.put(160, "minecraft:stained_glass_pane");
        map.put(161, "minecraft:leaves2");
        map.put(162, "minecraft:log2");
        map.put(163, "minecraft:acacia_stairs");
        map.put(164, "minecraft:dark_oak_stairs");
        map.put(170, "minecraft:hay_block");
        map.put(171, "minecraft:carpet");
        map.put(172, "minecraft:hardened_clay");
        map.put(173, "minecraft:coal_block");
        map.put(174, "minecraft:packed_ice");
        map.put(175, "minecraft:double_plant");
        map.put(256, "minecraft:iron_shovel");
        map.put(257, "minecraft:iron_pickaxe");
        map.put(258, "minecraft:iron_axe");
        map.put(259, "minecraft:flint_and_steel");
        map.put(260, "minecraft:apple");
        map.put(261, "minecraft:bow");
        map.put(262, "minecraft:arrow");
        map.put(263, "minecraft:coal");
        map.put(264, "minecraft:diamond");
        map.put(265, "minecraft:iron_ingot");
        map.put(266, "minecraft:gold_ingot");
        map.put(267, "minecraft:iron_sword");
        map.put(268, "minecraft:wooden_sword");
        map.put(269, "minecraft:wooden_shovel");
        map.put(270, "minecraft:wooden_pickaxe");
        map.put(271, "minecraft:wooden_axe");
        map.put(272, "minecraft:stone_sword");
        map.put(273, "minecraft:stone_shovel");
        map.put(274, "minecraft:stone_pickaxe");
        map.put(275, "minecraft:stone_axe");
        map.put(276, "minecraft:diamond_sword");
        map.put(277, "minecraft:diamond_shovel");
        map.put(278, "minecraft:diamond_pickaxe");
        map.put(279, "minecraft:diamond_axe");
        map.put(280, "minecraft:stick");
        map.put(281, "minecraft:bowl");
        map.put(282, "minecraft:mushroom_stew");
        map.put(283, "minecraft:golden_sword");
        map.put(284, "minecraft:golden_shovel");
        map.put(285, "minecraft:golden_pickaxe");
        map.put(286, "minecraft:golden_axe");
        map.put(287, "minecraft:string");
        map.put(288, "minecraft:feather");
        map.put(289, "minecraft:gunpowder");
        map.put(290, "minecraft:wooden_hoe");
        map.put(291, "minecraft:stone_hoe");
        map.put(292, "minecraft:iron_hoe");
        map.put(293, "minecraft:diamond_hoe");
        map.put(294, "minecraft:golden_hoe");
        map.put(295, "minecraft:wheat_seeds");
        map.put(296, "minecraft:wheat");
        map.put(297, "minecraft:bread");
        map.put(298, "minecraft:leather_helmet");
        map.put(299, "minecraft:leather_chestplate");
        map.put(300, "minecraft:leather_leggings");
        map.put(301, "minecraft:leather_boots");
        map.put(302, "minecraft:chainmail_helmet");
        map.put(303, "minecraft:chainmail_chestplate");
        map.put(304, "minecraft:chainmail_leggings");
        map.put(305, "minecraft:chainmail_boots");
        map.put(306, "minecraft:iron_helmet");
        map.put(307, "minecraft:iron_chestplate");
        map.put(308, "minecraft:iron_leggings");
        map.put(309, "minecraft:iron_boots");
        map.put(310, "minecraft:diamond_helmet");
        map.put(311, "minecraft:diamond_chestplate");
        map.put(312, "minecraft:diamond_leggings");
        map.put(313, "minecraft:diamond_boots");
        map.put(314, "minecraft:golden_helmet");
        map.put(315, "minecraft:golden_chestplate");
        map.put(316, "minecraft:golden_leggings");
        map.put(317, "minecraft:golden_boots");
        map.put(318, "minecraft:flint");
        map.put(319, "minecraft:porkchop");
        map.put(320, "minecraft:cooked_porkchop");
        map.put(321, "minecraft:painting");
        map.put(322, "minecraft:golden_apple");
        map.put(323, "minecraft:sign");
        map.put(324, "minecraft:wooden_door");
        map.put(325, "minecraft:bucket");
        map.put(326, "minecraft:water_bucket");
        map.put(327, "minecraft:lava_bucket");
        map.put(328, "minecraft:minecart");
        map.put(329, "minecraft:saddle");
        map.put(330, "minecraft:iron_door");
        map.put(331, "minecraft:redstone");
        map.put(332, "minecraft:snowball");
        map.put(333, "minecraft:boat");
        map.put(334, "minecraft:leather");
        map.put(335, "minecraft:milk_bucket");
        map.put(336, "minecraft:brick");
        map.put(337, "minecraft:clay_ball");
        map.put(338, "minecraft:reeds");
        map.put(339, "minecraft:paper");
        map.put(340, "minecraft:book");
        map.put(341, "minecraft:slime_ball");
        map.put(342, "minecraft:chest_minecart");
        map.put(343, "minecraft:furnace_minecart");
        map.put(344, "minecraft:egg");
        map.put(345, "minecraft:compass");
        map.put(346, "minecraft:fishing_rod");
        map.put(347, "minecraft:clock");
        map.put(348, "minecraft:glowstone_dust");
        map.put(349, "minecraft:fish");
        map.put(350, "minecraft:cooked_fished");
        map.put(351, "minecraft:dye");
        map.put(352, "minecraft:bone");
        map.put(353, "minecraft:sugar");
        map.put(354, "minecraft:cake");
        map.put(355, "minecraft:bed");
        map.put(356, "minecraft:repeater");
        map.put(357, "minecraft:cookie");
        map.put(358, "minecraft:filled_map");
        map.put(359, "minecraft:shears");
        map.put(360, "minecraft:melon");
        map.put(361, "minecraft:pumpkin_seeds");
        map.put(362, "minecraft:melon_seeds");
        map.put(363, "minecraft:beef");
        map.put(364, "minecraft:cooked_beef");
        map.put(365, "minecraft:chicken");
        map.put(366, "minecraft:cooked_chicken");
        map.put(367, "minecraft:rotten_flesh");
        map.put(368, "minecraft:ender_pearl");
        map.put(369, "minecraft:blaze_rod");
        map.put(370, "minecraft:ghast_tear");
        map.put(371, "minecraft:gold_nugget");
        map.put(372, "minecraft:nether_wart");
        map.put(373, "minecraft:potion");
        map.put(374, "minecraft:glass_bottle");
        map.put(375, "minecraft:spider_eye");
        map.put(376, "minecraft:fermented_spider_eye");
        map.put(377, "minecraft:blaze_powder");
        map.put(378, "minecraft:magma_cream");
        map.put(379, "minecraft:brewing_stand");
        map.put(380, "minecraft:cauldron");
        map.put(381, "minecraft:ender_eye");
        map.put(382, "minecraft:speckled_melon");
        map.put(383, "minecraft:spawn_egg");
        map.put(384, "minecraft:experience_bottle");
        map.put(385, "minecraft:fire_charge");
        map.put(386, "minecraft:writable_book");
        map.put(387, "minecraft:written_book");
        map.put(388, "minecraft:emerald");
        map.put(389, "minecraft:item_frame");
        map.put(390, "minecraft:flower_pot");
        map.put(391, "minecraft:carrot");
        map.put(392, "minecraft:potato");
        map.put(393, "minecraft:baked_potato");
        map.put(394, "minecraft:poisonous_potato");
        map.put(395, "minecraft:map");
        map.put(396, "minecraft:golden_carrot");
        map.put(397, "minecraft:skull");
        map.put(398, "minecraft:carrot_on_a_stick");
        map.put(399, "minecraft:nether_star");
        map.put(400, "minecraft:pumpkin_pie");
        map.put(401, "minecraft:fireworks");
        map.put(402, "minecraft:firework_charge");
        map.put(403, "minecraft:enchanted_book");
        map.put(404, "minecraft:comparator");
        map.put(405, "minecraft:netherbrick");
        map.put(406, "minecraft:quartz");
        map.put(407, "minecraft:tnt_minecart");
        map.put(408, "minecraft:hopper_minecart");
        map.put(417, "minecraft:iron_horse_armor");
        map.put(418, "minecraft:golden_horse_armor");
        map.put(419, "minecraft:diamond_horse_armor");
        map.put(420, "minecraft:lead");
        map.put(421, "minecraft:name_tag");
        map.put(422, "minecraft:command_block_minecart");
        map.put(2256, "minecraft:record_13");
        map.put(2257, "minecraft:record_cat");
        map.put(2258, "minecraft:record_blocks");
        map.put(2259, "minecraft:record_chirp");
        map.put(2260, "minecraft:record_far");
        map.put(2261, "minecraft:record_mall");
        map.put(2262, "minecraft:record_mellohi");
        map.put(2263, "minecraft:record_stal");
        map.put(2264, "minecraft:record_strad");
        map.put(2265, "minecraft:record_ward");
        map.put(2266, "minecraft:record_11");
        map.put(2267, "minecraft:record_wait");
        map.defaultReturnValue("minecraft:air");
    });

    public ItemIdFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public static String getItem(int id) {
        return ITEM_NAMES.get(id);
    }

    public TypeRewriteRule makeRule() {
        Type<Either<Integer, Pair<String, String>>> type = DSL.or(
            DSL.intType(), DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString())
        );
        Type<Pair<String, String>> type2 = DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString());
        OpticFinder<Either<Integer, Pair<String, String>>> opticFinder = DSL.fieldFinder("id", type);
        return this.fixTypeEverywhereTyped(
            "ItemIdFix",
            this.getInputSchema().getType(References.ITEM_STACK),
            this.getOutputSchema().getType(References.ITEM_STACK),
            itemStackTyped -> itemStackTyped.update(
                    opticFinder, type2, id -> id.map(ordinal -> Pair.of(References.ITEM_NAME.typeName(), getItem(ordinal)), named -> named)
                )
        );
    }
}
