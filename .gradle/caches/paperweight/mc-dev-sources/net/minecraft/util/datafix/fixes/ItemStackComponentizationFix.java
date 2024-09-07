package net.minecraft.util.datafix.fixes;

import com.google.common.base.Splitter;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.OptionalDynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackComponentizationFix extends DataFix {
    private static final int HIDE_ENCHANTMENTS = 1;
    private static final int HIDE_MODIFIERS = 2;
    private static final int HIDE_UNBREAKABLE = 4;
    private static final int HIDE_CAN_DESTROY = 8;
    private static final int HIDE_CAN_PLACE = 16;
    private static final int HIDE_ADDITIONAL = 32;
    private static final int HIDE_DYE = 64;
    private static final int HIDE_UPGRADES = 128;
    private static final Set<String> POTION_HOLDER_IDS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );
    private static final Set<String> BUCKETED_MOB_IDS = Set.of(
        "minecraft:pufferfish_bucket",
        "minecraft:salmon_bucket",
        "minecraft:cod_bucket",
        "minecraft:tropical_fish_bucket",
        "minecraft:axolotl_bucket",
        "minecraft:tadpole_bucket"
    );
    private static final List<String> BUCKETED_MOB_TAGS = List.of(
        "NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health", "Age", "Variant", "HuntingCooldown", "BucketVariantTag"
    );
    private static final Set<String> BOOLEAN_BLOCK_STATE_PROPERTIES = Set.of(
        "attached",
        "bottom",
        "conditional",
        "disarmed",
        "drag",
        "enabled",
        "extended",
        "eye",
        "falling",
        "hanging",
        "has_bottle_0",
        "has_bottle_1",
        "has_bottle_2",
        "has_record",
        "has_book",
        "inverted",
        "in_wall",
        "lit",
        "locked",
        "occupied",
        "open",
        "persistent",
        "powered",
        "short",
        "signal_fire",
        "snowy",
        "triggered",
        "unstable",
        "waterlogged",
        "berries",
        "bloom",
        "shrieking",
        "can_summon",
        "up",
        "down",
        "north",
        "east",
        "south",
        "west",
        "slot_0_occupied",
        "slot_1_occupied",
        "slot_2_occupied",
        "slot_3_occupied",
        "slot_4_occupied",
        "slot_5_occupied",
        "cracked",
        "crafting"
    );
    private static final Splitter PROPERTY_SPLITTER = Splitter.on(',');

    public ItemStackComponentizationFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private static void fixItemStack(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        int i = data.removeTag("HideFlags").asInt(0);
        data.moveTagToComponent("Damage", "minecraft:damage", dynamic.createInt(0));
        data.moveTagToComponent("RepairCost", "minecraft:repair_cost", dynamic.createInt(0));
        data.moveTagToComponent("CustomModelData", "minecraft:custom_model_data");
        data.removeTag("BlockStateTag")
            .result()
            .ifPresent(blockStateTagDynamic -> data.setComponent("minecraft:block_state", fixBlockStateTag((Dynamic<?>)blockStateTagDynamic)));
        data.moveTagToComponent("EntityTag", "minecraft:entity_data");
        data.fixSubTag("BlockEntityTag", false, blockEntityTagDynamic -> {
            String string = NamespacedSchema.ensureNamespaced(blockEntityTagDynamic.get("id").asString(""));
            blockEntityTagDynamic = fixBlockEntityTag(data, blockEntityTagDynamic, string);
            Dynamic<?> dynamicx = blockEntityTagDynamic.remove("id");
            return dynamicx.equals(blockEntityTagDynamic.emptyMap()) ? dynamicx : blockEntityTagDynamic;
        });
        data.moveTagToComponent("BlockEntityTag", "minecraft:block_entity_data");
        if (data.removeTag("Unbreakable").asBoolean(false)) {
            Dynamic<?> dynamic2 = dynamic.emptyMap();
            if ((i & 4) != 0) {
                dynamic2 = dynamic2.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            data.setComponent("minecraft:unbreakable", dynamic2);
        }

        fixEnchantments(data, dynamic, "Enchantments", "minecraft:enchantments", (i & 1) != 0);
        if (data.is("minecraft:enchanted_book")) {
            fixEnchantments(data, dynamic, "StoredEnchantments", "minecraft:stored_enchantments", (i & 32) != 0);
        }

        data.fixSubTag("display", false, displayDynamic -> fixDisplay(data, displayDynamic, i));
        fixAdventureModeChecks(data, dynamic, i);
        fixAttributeModifiers(data, dynamic, i);
        Optional<? extends Dynamic<?>> optional = data.removeTag("Trim").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic3 = (Dynamic<?>)optional.get();
            if ((i & 128) != 0) {
                dynamic3 = dynamic3.set("show_in_tooltip", dynamic3.createBoolean(false));
            }

            data.setComponent("minecraft:trim", dynamic3);
        }

        if ((i & 32) != 0) {
            data.setComponent("minecraft:hide_additional_tooltip", dynamic.emptyMap());
        }

        if (data.is("minecraft:crossbow")) {
            data.removeTag("Charged");
            data.moveTagToComponent("ChargedProjectiles", "minecraft:charged_projectiles", dynamic.createList(Stream.empty()));
        }

        if (data.is("minecraft:bundle")) {
            data.moveTagToComponent("Items", "minecraft:bundle_contents", dynamic.createList(Stream.empty()));
        }

        if (data.is("minecraft:filled_map")) {
            data.moveTagToComponent("map", "minecraft:map_id");
            Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = data.removeTag("Decorations")
                .asStream()
                .map(ItemStackComponentizationFix::fixMapDecoration)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (dynamicx, dynamic2) -> dynamicx));
            if (!map.isEmpty()) {
                data.setComponent("minecraft:map_decorations", dynamic.createMap(map));
            }
        }

        if (data.is(POTION_HOLDER_IDS)) {
            fixPotionContents(data, dynamic);
        }

        if (data.is("minecraft:writable_book")) {
            fixWritableBook(data, dynamic);
        }

        if (data.is("minecraft:written_book")) {
            fixWrittenBook(data, dynamic);
        }

        if (data.is("minecraft:suspicious_stew")) {
            data.moveTagToComponent("effects", "minecraft:suspicious_stew_effects");
        }

        if (data.is("minecraft:debug_stick")) {
            data.moveTagToComponent("DebugProperty", "minecraft:debug_stick_state");
        }

        if (data.is(BUCKETED_MOB_IDS)) {
            fixBucketedMobData(data, dynamic);
        }

        if (data.is("minecraft:goat_horn")) {
            data.moveTagToComponent("instrument", "minecraft:instrument");
        }

        if (data.is("minecraft:knowledge_book")) {
            data.moveTagToComponent("Recipes", "minecraft:recipes");
        }

        if (data.is("minecraft:compass")) {
            fixLodestoneTracker(data, dynamic);
        }

        if (data.is("minecraft:firework_rocket")) {
            fixFireworkRocket(data);
        }

        if (data.is("minecraft:firework_star")) {
            fixFireworkStar(data);
        }

        if (data.is("minecraft:player_head")) {
            data.removeTag("SkullOwner")
                .result()
                .ifPresent(skullOwnerDynamic -> data.setComponent("minecraft:profile", fixProfile((Dynamic<?>)skullOwnerDynamic)));
        }
    }

    private static Dynamic<?> fixBlockStateTag(Dynamic<?> dynamic) {
        return DataFixUtils.orElse(dynamic.asMapOpt().result().map(stream -> stream.collect(Collectors.toMap(Pair::getFirst, pair -> {
                String string = ((Dynamic)pair.getFirst()).asString("");
                Dynamic<?> dynamicx = (Dynamic<?>)pair.getSecond();
                if (BOOLEAN_BLOCK_STATE_PROPERTIES.contains(string)) {
                    Optional<Boolean> optional = dynamicx.asBoolean().result();
                    if (optional.isPresent()) {
                        return dynamicx.createString(String.valueOf(optional.get()));
                    }
                }

                Optional<Number> optional2 = dynamicx.asNumber().result();
                return optional2.isPresent() ? dynamicx.createString(optional2.get().toString()) : dynamicx;
            }))).map(dynamic::createMap), dynamic);
    }

    private static Dynamic<?> fixDisplay(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic, int hideFlags) {
        data.setComponent("minecraft:custom_name", dynamic.get("Name"));
        data.setComponent("minecraft:lore", dynamic.get("Lore"));
        Optional<Integer> optional = dynamic.get("color").asNumber().result().map(Number::intValue);
        boolean bl = (hideFlags & 64) != 0;
        if (optional.isPresent() || bl) {
            Dynamic<?> dynamic2 = dynamic.emptyMap().set("rgb", dynamic.createInt(optional.orElse(10511680)));
            if (bl) {
                dynamic2 = dynamic2.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            data.setComponent("minecraft:dyed_color", dynamic2);
        }

        Optional<String> optional2 = dynamic.get("LocName").asString().result();
        if (optional2.isPresent()) {
            data.setComponent("minecraft:item_name", ComponentDataFixUtils.createTranslatableComponent(dynamic.getOps(), optional2.get()));
        }

        if (data.is("minecraft:filled_map")) {
            data.setComponent("minecraft:map_color", dynamic.get("MapColor"));
            dynamic = dynamic.remove("MapColor");
        }

        return dynamic.remove("Name").remove("Lore").remove("color").remove("LocName");
    }

    private static <T> Dynamic<T> fixBlockEntityTag(ItemStackComponentizationFix.ItemStackData data, Dynamic<T> dynamic, String blockEntityId) {
        data.setComponent("minecraft:lock", dynamic.get("Lock"));
        dynamic = dynamic.remove("Lock");
        Optional<Dynamic<T>> optional = dynamic.get("LootTable").result();
        if (optional.isPresent()) {
            Dynamic<T> dynamic2 = dynamic.emptyMap().set("loot_table", optional.get());
            long l = dynamic.get("LootTableSeed").asLong(0L);
            if (l != 0L) {
                dynamic2 = dynamic2.set("seed", dynamic.createLong(l));
            }

            data.setComponent("minecraft:container_loot", dynamic2);
            dynamic = dynamic.remove("LootTable").remove("LootTableSeed");
        }
        return switch (blockEntityId) {
            case "minecraft:skull" -> {
                data.setComponent("minecraft:note_block_sound", dynamic.get("note_block_sound"));
                yield dynamic.remove("note_block_sound");
            }
            case "minecraft:decorated_pot" -> {
                data.setComponent("minecraft:pot_decorations", dynamic.get("sherds"));
                Optional<Dynamic<T>> optional2 = dynamic.get("item").result();
                if (optional2.isPresent()) {
                    data.setComponent(
                        "minecraft:container", dynamic.createList(Stream.of(dynamic.emptyMap().set("slot", dynamic.createInt(0)).set("item", optional2.get())))
                    );
                }

                yield dynamic.remove("sherds").remove("item");
            }
            case "minecraft:banner" -> {
                data.setComponent("minecraft:banner_patterns", dynamic.get("patterns"));
                Optional<Number> optional3 = dynamic.get("Base").asNumber().result();
                if (optional3.isPresent()) {
                    data.setComponent("minecraft:base_color", dynamic.createString(BannerPatternFormatFix.fixColor(optional3.get().intValue())));
                }

                yield dynamic.remove("patterns").remove("Base");
            }
            case "minecraft:shulker_box", "minecraft:chest", "minecraft:trapped_chest", "minecraft:furnace", "minecraft:ender_chest", "minecraft:dispenser", "minecraft:dropper", "minecraft:brewing_stand", "minecraft:hopper", "minecraft:barrel", "minecraft:smoker", "minecraft:blast_furnace", "minecraft:campfire", "minecraft:chiseled_bookshelf", "minecraft:crafter" -> {
                List<Dynamic<T>> list = dynamic.get("Items")
                    .asList(
                        itemsDynamic -> itemsDynamic.emptyMap()
                                .set("slot", itemsDynamic.createInt(itemsDynamic.get("Slot").asByte((byte)0) & 255))
                                .set("item", itemsDynamic.remove("Slot"))
                    );
                if (!list.isEmpty()) {
                    data.setComponent("minecraft:container", dynamic.createList(list.stream()));
                }

                yield dynamic.remove("Items");
            }
            case "minecraft:beehive" -> {
                data.setComponent("minecraft:bees", dynamic.get("bees"));
                yield dynamic.remove("bees");
            }
            default -> dynamic;
        };
    }

    private static void fixEnchantments(
        ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic, String nbtKey, String componentId, boolean hideInTooltip
    ) {
        OptionalDynamic<?> optionalDynamic = data.removeTag(nbtKey);
        List<Pair<String, Integer>> list = optionalDynamic.asList(Function.identity())
            .stream()
            .flatMap(enchantmentsDynamic -> parseEnchantment((Dynamic<?>)enchantmentsDynamic).stream())
            .toList();
        if (!list.isEmpty() || hideInTooltip) {
            Dynamic<?> dynamic2 = dynamic.emptyMap();
            Dynamic<?> dynamic3 = dynamic.emptyMap();

            for (Pair<String, Integer> pair : list) {
                dynamic3 = dynamic3.set(pair.getFirst(), dynamic.createInt(pair.getSecond()));
            }

            dynamic2 = dynamic2.set("levels", dynamic3);
            if (hideInTooltip) {
                dynamic2 = dynamic2.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            data.setComponent(componentId, dynamic2);
        }

        if (optionalDynamic.result().isPresent() && list.isEmpty()) {
            data.setComponent("minecraft:enchantment_glint_override", dynamic.createBoolean(true));
        }
    }

    private static Optional<Pair<String, Integer>> parseEnchantment(Dynamic<?> dynamic) {
        return dynamic.get("id")
            .asString()
            .apply2stable((enchantmentId, level) -> Pair.of(enchantmentId, Mth.clamp(level.intValue(), 0, 255)), dynamic.get("lvl").asNumber())
            .result();
    }

    private static void fixAdventureModeChecks(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic, int hideFlags) {
        fixBlockStatePredicates(data, dynamic, "CanDestroy", "minecraft:can_break", (hideFlags & 8) != 0);
        fixBlockStatePredicates(data, dynamic, "CanPlaceOn", "minecraft:can_place_on", (hideFlags & 16) != 0);
    }

    private static void fixBlockStatePredicates(
        ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic, String nbtKey, String componentId, boolean hideInTooltip
    ) {
        Optional<? extends Dynamic<?>> optional = data.removeTag(nbtKey).result();
        if (!optional.isEmpty()) {
            Dynamic<?> dynamic2 = dynamic.emptyMap()
                .set(
                    "predicates",
                    dynamic.createList(
                        optional.get()
                            .asStream()
                            .map(
                                predicatesDynamic -> DataFixUtils.orElse(
                                        predicatesDynamic.asString().map(string -> fixBlockStatePredicate((Dynamic<?>)predicatesDynamic, string)).result(),
                                        predicatesDynamic
                                    )
                            )
                    )
                );
            if (hideInTooltip) {
                dynamic2 = dynamic2.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            data.setComponent(componentId, dynamic2);
        }
    }

    private static Dynamic<?> fixBlockStatePredicate(Dynamic<?> dynamic, String listAsString) {
        int i = listAsString.indexOf(91);
        int j = listAsString.indexOf(123);
        int k = listAsString.length();
        if (i != -1) {
            k = i;
        }

        if (j != -1) {
            k = Math.min(k, j);
        }

        String string = listAsString.substring(0, k);
        Dynamic<?> dynamic2 = dynamic.emptyMap().set("blocks", dynamic.createString(string.trim()));
        int l = listAsString.indexOf(93);
        if (i != -1 && l != -1) {
            Dynamic<?> dynamic3 = dynamic.emptyMap();

            for (String string2 : PROPERTY_SPLITTER.split(listAsString.substring(i + 1, l))) {
                int m = string2.indexOf(61);
                if (m != -1) {
                    String string3 = string2.substring(0, m).trim();
                    String string4 = string2.substring(m + 1).trim();
                    dynamic3 = dynamic3.set(string3, dynamic.createString(string4));
                }
            }

            dynamic2 = dynamic2.set("state", dynamic3);
        }

        int n = listAsString.indexOf(125);
        if (j != -1 && n != -1) {
            dynamic2 = dynamic2.set("nbt", dynamic.createString(listAsString.substring(j, n + 1)));
        }

        return dynamic2;
    }

    private static void fixAttributeModifiers(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic, int hideFlags) {
        OptionalDynamic<?> optionalDynamic = data.removeTag("AttributeModifiers");
        if (!optionalDynamic.result().isEmpty()) {
            boolean bl = (hideFlags & 2) != 0;
            List<? extends Dynamic<?>> list = optionalDynamic.asList(ItemStackComponentizationFix::fixAttributeModifier);
            Dynamic<?> dynamic2 = dynamic.emptyMap().set("modifiers", dynamic.createList(list.stream()));
            if (bl) {
                dynamic2 = dynamic2.set("show_in_tooltip", dynamic.createBoolean(false));
            }

            data.setComponent("minecraft:attribute_modifiers", dynamic2);
        }
    }

    private static Dynamic<?> fixAttributeModifier(Dynamic<?> dynamic) {
        Dynamic<?> dynamic2 = dynamic.emptyMap()
            .set("name", dynamic.createString(""))
            .set("amount", dynamic.createDouble(0.0))
            .set("operation", dynamic.createString("add_value"));
        dynamic2 = Dynamic.copyField(dynamic, "AttributeName", dynamic2, "type");
        dynamic2 = Dynamic.copyField(dynamic, "Slot", dynamic2, "slot");
        dynamic2 = Dynamic.copyField(dynamic, "UUID", dynamic2, "uuid");
        dynamic2 = Dynamic.copyField(dynamic, "Name", dynamic2, "name");
        dynamic2 = Dynamic.copyField(dynamic, "Amount", dynamic2, "amount");
        return Dynamic.copyAndFixField(dynamic, "Operation", dynamic2, "operation", operationDynamic -> {
            return operationDynamic.createString(switch (operationDynamic.asInt(0)) {
                case 1 -> "add_multiplied_base";
                case 2 -> "add_multiplied_total";
                default -> "add_value";
            });
        });
    }

    private static Pair<Dynamic<?>, Dynamic<?>> fixMapDecoration(Dynamic<?> dynamic) {
        Dynamic<?> dynamic2 = DataFixUtils.orElseGet(dynamic.get("id").result(), () -> dynamic.createString(""));
        Dynamic<?> dynamic3 = dynamic.emptyMap()
            .set("type", dynamic.createString(fixMapDecorationType(dynamic.get("type").asInt(0))))
            .set("x", dynamic.createDouble(dynamic.get("x").asDouble(0.0)))
            .set("z", dynamic.createDouble(dynamic.get("z").asDouble(0.0)))
            .set("rotation", dynamic.createFloat((float)dynamic.get("rot").asDouble(0.0)));
        return Pair.of(dynamic2, dynamic3);
    }

    private static String fixMapDecorationType(int index) {
        return switch (index) {
            case 1 -> "frame";
            case 2 -> "red_marker";
            case 3 -> "blue_marker";
            case 4 -> "target_x";
            case 5 -> "target_point";
            case 6 -> "player_off_map";
            case 7 -> "player_off_limits";
            case 8 -> "mansion";
            case 9 -> "monument";
            case 10 -> "banner_white";
            case 11 -> "banner_orange";
            case 12 -> "banner_magenta";
            case 13 -> "banner_light_blue";
            case 14 -> "banner_yellow";
            case 15 -> "banner_lime";
            case 16 -> "banner_pink";
            case 17 -> "banner_gray";
            case 18 -> "banner_light_gray";
            case 19 -> "banner_cyan";
            case 20 -> "banner_purple";
            case 21 -> "banner_blue";
            case 22 -> "banner_brown";
            case 23 -> "banner_green";
            case 24 -> "banner_red";
            case 25 -> "banner_black";
            case 26 -> "red_x";
            case 27 -> "village_desert";
            case 28 -> "village_plains";
            case 29 -> "village_savanna";
            case 30 -> "village_snowy";
            case 31 -> "village_taiga";
            case 32 -> "jungle_temple";
            case 33 -> "swamp_hut";
            default -> "player";
        };
    }

    private static void fixPotionContents(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        Dynamic<?> dynamic2 = dynamic.emptyMap();
        Optional<String> optional = data.removeTag("Potion").asString().result().filter(potionId -> !potionId.equals("minecraft:empty"));
        if (optional.isPresent()) {
            dynamic2 = dynamic2.set("potion", dynamic.createString(optional.get()));
        }

        dynamic2 = data.moveTagInto("CustomPotionColor", dynamic2, "custom_color");
        dynamic2 = data.moveTagInto("custom_potion_effects", dynamic2, "custom_effects");
        if (!dynamic2.equals(dynamic.emptyMap())) {
            data.setComponent("minecraft:potion_contents", dynamic2);
        }
    }

    private static void fixWritableBook(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        Dynamic<?> dynamic2 = fixBookPages(data, dynamic);
        if (dynamic2 != null) {
            data.setComponent("minecraft:writable_book_content", dynamic.emptyMap().set("pages", dynamic2));
        }
    }

    private static void fixWrittenBook(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        Dynamic<?> dynamic2 = fixBookPages(data, dynamic);
        String string = data.removeTag("title").asString("");
        Optional<String> optional = data.removeTag("filtered_title").asString().result();
        Dynamic<?> dynamic3 = dynamic.emptyMap();
        dynamic3 = dynamic3.set("title", createFilteredText(dynamic, string, optional));
        dynamic3 = data.moveTagInto("author", dynamic3, "author");
        dynamic3 = data.moveTagInto("resolved", dynamic3, "resolved");
        dynamic3 = data.moveTagInto("generation", dynamic3, "generation");
        if (dynamic2 != null) {
            dynamic3 = dynamic3.set("pages", dynamic2);
        }

        data.setComponent("minecraft:written_book_content", dynamic3);
    }

    @Nullable
    private static Dynamic<?> fixBookPages(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        List<String> list = data.removeTag("pages").asList(pagesDynamic -> pagesDynamic.asString(""));
        Map<String, String> map = data.removeTag("filtered_pages")
            .asMap(filteredPagesKeyDynamic -> filteredPagesKeyDynamic.asString("0"), filteredPagesValueDynamic -> filteredPagesValueDynamic.asString(""));
        if (list.isEmpty()) {
            return null;
        } else {
            List<Dynamic<?>> list2 = new ArrayList<>(list.size());

            for (int i = 0; i < list.size(); i++) {
                String string = list.get(i);
                String string2 = map.get(String.valueOf(i));
                list2.add(createFilteredText(dynamic, string, Optional.ofNullable(string2)));
            }

            return dynamic.createList(list2.stream());
        }
    }

    private static Dynamic<?> createFilteredText(Dynamic<?> dynamic, String unfiltered, Optional<String> filtered) {
        Dynamic<?> dynamic2 = dynamic.emptyMap().set("raw", dynamic.createString(unfiltered));
        if (filtered.isPresent()) {
            dynamic2 = dynamic2.set("filtered", dynamic.createString(filtered.get()));
        }

        return dynamic2;
    }

    private static void fixBucketedMobData(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        Dynamic<?> dynamic2 = dynamic.emptyMap();

        for (String string : BUCKETED_MOB_TAGS) {
            dynamic2 = data.moveTagInto(string, dynamic2, string);
        }

        if (!dynamic2.equals(dynamic.emptyMap())) {
            data.setComponent("minecraft:bucket_entity_data", dynamic2);
        }
    }

    private static void fixLodestoneTracker(ItemStackComponentizationFix.ItemStackData data, Dynamic<?> dynamic) {
        Optional<? extends Dynamic<?>> optional = data.removeTag("LodestonePos").result();
        Optional<? extends Dynamic<?>> optional2 = data.removeTag("LodestoneDimension").result();
        if (!optional.isEmpty() || !optional2.isEmpty()) {
            boolean bl = data.removeTag("LodestoneTracked").asBoolean(true);
            Dynamic<?> dynamic2 = dynamic.emptyMap();
            if (optional.isPresent() && optional2.isPresent()) {
                dynamic2 = dynamic2.set("target", dynamic.emptyMap().set("pos", (Dynamic<?>)optional.get()).set("dimension", (Dynamic<?>)optional2.get()));
            }

            if (!bl) {
                dynamic2 = dynamic2.set("tracked", dynamic.createBoolean(false));
            }

            data.setComponent("minecraft:lodestone_tracker", dynamic2);
        }
    }

    private static void fixFireworkStar(ItemStackComponentizationFix.ItemStackData data) {
        data.fixSubTag("Explosion", true, explosionDynamic -> {
            data.setComponent("minecraft:firework_explosion", fixFireworkExplosion(explosionDynamic));
            return explosionDynamic.remove("Type").remove("Colors").remove("FadeColors").remove("Trail").remove("Flicker");
        });
    }

    private static void fixFireworkRocket(ItemStackComponentizationFix.ItemStackData data) {
        data.fixSubTag(
            "Fireworks",
            true,
            fireworksDynamic -> {
                Stream<? extends Dynamic<?>> stream = fireworksDynamic.get("Explosions").asStream().map(ItemStackComponentizationFix::fixFireworkExplosion);
                int i = fireworksDynamic.get("Flight").asInt(0);
                data.setComponent(
                    "minecraft:fireworks",
                    fireworksDynamic.emptyMap()
                        .set("explosions", fireworksDynamic.createList(stream))
                        .set("flight_duration", fireworksDynamic.createByte((byte)i))
                );
                return fireworksDynamic.remove("Explosions").remove("Flight");
            }
        );
    }

    private static Dynamic<?> fixFireworkExplosion(Dynamic<?> dynamic) {
        dynamic = dynamic.set("shape", dynamic.createString(switch (dynamic.get("Type").asInt(0)) {
            case 1 -> "large_ball";
            case 2 -> "star";
            case 3 -> "creeper";
            case 4 -> "burst";
            default -> "small_ball";
        })).remove("Type");
        dynamic = dynamic.renameField("Colors", "colors");
        dynamic = dynamic.renameField("FadeColors", "fade_colors");
        dynamic = dynamic.renameField("Trail", "has_trail");
        return dynamic.renameField("Flicker", "has_twinkle");
    }

    public static Dynamic<?> fixProfile(Dynamic<?> dynamic) {
        Optional<String> optional = dynamic.asString().result();
        if (optional.isPresent()) {
            return isValidPlayerName(optional.get()) ? dynamic.emptyMap().set("name", dynamic.createString(optional.get())) : dynamic.emptyMap();
        } else {
            String string = dynamic.get("Name").asString("");
            Optional<? extends Dynamic<?>> optional2 = dynamic.get("Id").result();
            Dynamic<?> dynamic2 = fixProfileProperties(dynamic.get("Properties"));
            Dynamic<?> dynamic3 = dynamic.emptyMap();
            if (isValidPlayerName(string)) {
                dynamic3 = dynamic3.set("name", dynamic.createString(string));
            }

            if (optional2.isPresent()) {
                dynamic3 = dynamic3.set("id", (Dynamic<?>)optional2.get());
            }

            if (dynamic2 != null) {
                dynamic3 = dynamic3.set("properties", dynamic2);
            }

            return dynamic3;
        }
    }

    private static boolean isValidPlayerName(String username) {
        return username.length() <= 16 && username.chars().filter(c -> c <= 32 || c >= 127).findAny().isEmpty();
    }

    @Nullable
    private static Dynamic<?> fixProfileProperties(OptionalDynamic<?> propertiesDynamic) {
        Map<String, List<Pair<String, Optional<String>>>> map = propertiesDynamic.asMap(
            dynamic -> dynamic.asString(""), dynamic -> dynamic.asList(dynamicx -> {
                    String string = dynamicx.get("Value").asString("");
                    Optional<String> optional = dynamicx.get("Signature").asString().result();
                    return Pair.of(string, optional);
                })
        );
        return map.isEmpty()
            ? null
            : propertiesDynamic.createList(
                map.entrySet()
                    .stream()
                    .flatMap(
                        entry -> entry.getValue()
                                .stream()
                                .map(
                                    pair -> {
                                        Dynamic<?> dynamic = propertiesDynamic.emptyMap()
                                            .set("name", propertiesDynamic.createString(entry.getKey()))
                                            .set("value", propertiesDynamic.createString(pair.getFirst()));
                                        Optional<String> optional = pair.getSecond();
                                        return optional.isPresent() ? dynamic.set("signature", propertiesDynamic.createString(optional.get())) : dynamic;
                                    }
                                )
                    )
            );
    }

    protected TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "ItemStack componentization",
            this.getInputSchema().getType(References.ITEM_STACK),
            this.getOutputSchema().getType(References.ITEM_STACK),
            dynamic -> {
                Optional<? extends Dynamic<?>> optional = ItemStackComponentizationFix.ItemStackData.read(dynamic).map(data -> {
                    fixItemStack(data, data.tag);
                    return data.write();
                });
                return DataFixUtils.orElse(optional, dynamic);
            }
        );
    }

    static class ItemStackData {
        private final String item;
        private final int count;
        private Dynamic<?> components;
        private final Dynamic<?> remainder;
        Dynamic<?> tag;

        private ItemStackData(String itemId, int count, Dynamic<?> dynamic) {
            this.item = NamespacedSchema.ensureNamespaced(itemId);
            this.count = count;
            this.components = dynamic.emptyMap();
            this.tag = dynamic.get("tag").orElseEmptyMap();
            this.remainder = dynamic.remove("tag");
        }

        public static Optional<ItemStackComponentizationFix.ItemStackData> read(Dynamic<?> dynamic) {
            return dynamic.get("id")
                .asString()
                .apply2stable(
                    (itemId, count) -> new ItemStackComponentizationFix.ItemStackData(itemId, count.intValue(), dynamic.remove("id").remove("Count")),
                    dynamic.get("Count").asNumber()
                )
                .result();
        }

        public OptionalDynamic<?> removeTag(String key) {
            OptionalDynamic<?> optionalDynamic = this.tag.get(key);
            this.tag = this.tag.remove(key);
            return optionalDynamic;
        }

        public void setComponent(String key, Dynamic<?> value) {
            this.components = this.components.set(key, value);
        }

        public void setComponent(String key, OptionalDynamic<?> optionalValue) {
            optionalValue.result().ifPresent(value -> this.components = this.components.set(key, (Dynamic<?>)value));
        }

        public Dynamic<?> moveTagInto(String nbtKey, Dynamic<?> components, String componentId) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(nbtKey).result();
            return optional.isPresent() ? components.set(componentId, (Dynamic<?>)optional.get()) : components;
        }

        public void moveTagToComponent(String nbtKey, String componentId, Dynamic<?> defaultValue) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(nbtKey).result();
            if (optional.isPresent() && !optional.get().equals(defaultValue)) {
                this.setComponent(componentId, (Dynamic<?>)optional.get());
            }
        }

        public void moveTagToComponent(String nbtKey, String componentId) {
            this.removeTag(nbtKey).result().ifPresent(nbt -> this.setComponent(componentId, (Dynamic<?>)nbt));
        }

        public void fixSubTag(String nbtKey, boolean removeIfEmpty, UnaryOperator<Dynamic<?>> fixer) {
            OptionalDynamic<?> optionalDynamic = this.tag.get(nbtKey);
            if (!removeIfEmpty || !optionalDynamic.result().isEmpty()) {
                Dynamic<?> dynamic = optionalDynamic.orElseEmptyMap();
                dynamic = fixer.apply(dynamic);
                if (dynamic.equals(dynamic.emptyMap())) {
                    this.tag = this.tag.remove(nbtKey);
                } else {
                    this.tag = this.tag.set(nbtKey, dynamic);
                }
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.tag.emptyMap().set("id", this.tag.createString(this.item)).set("count", this.tag.createInt(this.count));
            if (!this.tag.equals(this.tag.emptyMap())) {
                this.components = this.components.set("minecraft:custom_data", this.tag);
            }

            if (!this.components.equals(this.tag.emptyMap())) {
                dynamic = dynamic.set("components", this.components);
            }

            return mergeRemainder(dynamic, this.remainder);
        }

        private static <T> Dynamic<T> mergeRemainder(Dynamic<T> data, Dynamic<?> leftoverNbt) {
            DynamicOps<T> dynamicOps = data.getOps();
            return dynamicOps.getMap(data.getValue())
                .flatMap(mapLike -> dynamicOps.mergeToMap(leftoverNbt.convert(dynamicOps).getValue(), (MapLike<T>)mapLike))
                .map(object -> new Dynamic<>(dynamicOps, (T)object))
                .result()
                .orElse(data);
        }

        public boolean is(String itemId) {
            return this.item.equals(itemId);
        }

        public boolean is(Set<String> itemIds) {
            return itemIds.contains(this.item);
        }

        public boolean hasComponent(String componentId) {
            return this.components.get(componentId).result().isPresent();
        }
    }
}
