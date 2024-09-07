package net.minecraft.util.datafix.schemas;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.types.templates.Hook.HookFunction;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;
import org.slf4j.Logger;

public class V99 extends Schema {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Map<String, String> ITEM_TO_BLOCKENTITY = DataFixUtils.make(Maps.newHashMap(), map -> {
        map.put("minecraft:furnace", "Furnace");
        map.put("minecraft:lit_furnace", "Furnace");
        map.put("minecraft:chest", "Chest");
        map.put("minecraft:trapped_chest", "Chest");
        map.put("minecraft:ender_chest", "EnderChest");
        map.put("minecraft:jukebox", "RecordPlayer");
        map.put("minecraft:dispenser", "Trap");
        map.put("minecraft:dropper", "Dropper");
        map.put("minecraft:sign", "Sign");
        map.put("minecraft:mob_spawner", "MobSpawner");
        map.put("minecraft:noteblock", "Music");
        map.put("minecraft:brewing_stand", "Cauldron");
        map.put("minecraft:enhanting_table", "EnchantTable");
        map.put("minecraft:command_block", "CommandBlock");
        map.put("minecraft:beacon", "Beacon");
        map.put("minecraft:skull", "Skull");
        map.put("minecraft:daylight_detector", "DLDetector");
        map.put("minecraft:hopper", "Hopper");
        map.put("minecraft:banner", "Banner");
        map.put("minecraft:flower_pot", "FlowerPot");
        map.put("minecraft:repeating_command_block", "CommandBlock");
        map.put("minecraft:chain_command_block", "CommandBlock");
        map.put("minecraft:standing_sign", "Sign");
        map.put("minecraft:wall_sign", "Sign");
        map.put("minecraft:piston_head", "Piston");
        map.put("minecraft:daylight_detector_inverted", "DLDetector");
        map.put("minecraft:unpowered_comparator", "Comparator");
        map.put("minecraft:powered_comparator", "Comparator");
        map.put("minecraft:wall_banner", "Banner");
        map.put("minecraft:standing_banner", "Banner");
        map.put("minecraft:structure_block", "Structure");
        map.put("minecraft:end_portal", "Airportal");
        map.put("minecraft:end_gateway", "EndGateway");
        map.put("minecraft:shield", "Banner");
    });
    public static final Map<String, String> ITEM_TO_ENTITY = Map.of("minecraft:armor_stand", "ArmorStand", "minecraft:painting", "Painting");
    protected static final HookFunction ADD_NAMES = new HookFunction() {
        public <T> T apply(DynamicOps<T> dynamicOps, T object) {
            return V99.addNames(new Dynamic<>(dynamicOps, object), V99.ITEM_TO_BLOCKENTITY, V99.ITEM_TO_ENTITY);
        }
    };

    public V99(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    protected static TypeTemplate equipment(Schema schema) {
        return DSL.optionalFields("Equipment", DSL.list(References.ITEM_STACK.in(schema)));
    }

    protected static void registerMob(Schema schema, Map<String, Supplier<TypeTemplate>> map, String entityId) {
        schema.register(map, entityId, () -> equipment(schema));
    }

    protected static void registerThrowableProjectile(Schema schema, Map<String, Supplier<TypeTemplate>> map, String entityId) {
        schema.register(map, entityId, () -> DSL.optionalFields("inTile", References.BLOCK_NAME.in(schema)));
    }

    protected static void registerMinecart(Schema schema, Map<String, Supplier<TypeTemplate>> map, String entityId) {
        schema.register(map, entityId, () -> DSL.optionalFields("DisplayTile", References.BLOCK_NAME.in(schema)));
    }

    protected static void registerInventory(Schema schema, Map<String, Supplier<TypeTemplate>> map, String entityId) {
        schema.register(map, entityId, () -> DSL.optionalFields("Items", DSL.list(References.ITEM_STACK.in(schema))));
    }

    public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
        Map<String, Supplier<TypeTemplate>> map = Maps.newHashMap();
        schema.register(map, "Item", name -> DSL.optionalFields("Item", References.ITEM_STACK.in(schema)));
        schema.registerSimple(map, "XPOrb");
        registerThrowableProjectile(schema, map, "ThrownEgg");
        schema.registerSimple(map, "LeashKnot");
        schema.registerSimple(map, "Painting");
        schema.register(map, "Arrow", name -> DSL.optionalFields("inTile", References.BLOCK_NAME.in(schema)));
        schema.register(map, "TippedArrow", name -> DSL.optionalFields("inTile", References.BLOCK_NAME.in(schema)));
        schema.register(map, "SpectralArrow", name -> DSL.optionalFields("inTile", References.BLOCK_NAME.in(schema)));
        registerThrowableProjectile(schema, map, "Snowball");
        registerThrowableProjectile(schema, map, "Fireball");
        registerThrowableProjectile(schema, map, "SmallFireball");
        registerThrowableProjectile(schema, map, "ThrownEnderpearl");
        schema.registerSimple(map, "EyeOfEnderSignal");
        schema.register(map, "ThrownPotion", name -> DSL.optionalFields("inTile", References.BLOCK_NAME.in(schema), "Potion", References.ITEM_STACK.in(schema)));
        registerThrowableProjectile(schema, map, "ThrownExpBottle");
        schema.register(map, "ItemFrame", name -> DSL.optionalFields("Item", References.ITEM_STACK.in(schema)));
        registerThrowableProjectile(schema, map, "WitherSkull");
        schema.registerSimple(map, "PrimedTnt");
        schema.register(
            map, "FallingSand", name -> DSL.optionalFields("Block", References.BLOCK_NAME.in(schema), "TileEntityData", References.BLOCK_ENTITY.in(schema))
        );
        schema.register(map, "FireworksRocketEntity", name -> DSL.optionalFields("FireworksItem", References.ITEM_STACK.in(schema)));
        schema.registerSimple(map, "Boat");
        schema.register(
            map, "Minecart", () -> DSL.optionalFields("DisplayTile", References.BLOCK_NAME.in(schema), "Items", DSL.list(References.ITEM_STACK.in(schema)))
        );
        registerMinecart(schema, map, "MinecartRideable");
        schema.register(
            map,
            "MinecartChest",
            name -> DSL.optionalFields("DisplayTile", References.BLOCK_NAME.in(schema), "Items", DSL.list(References.ITEM_STACK.in(schema)))
        );
        registerMinecart(schema, map, "MinecartFurnace");
        registerMinecart(schema, map, "MinecartTNT");
        schema.register(
            map, "MinecartSpawner", () -> DSL.optionalFields("DisplayTile", References.BLOCK_NAME.in(schema), References.UNTAGGED_SPAWNER.in(schema))
        );
        schema.register(
            map,
            "MinecartHopper",
            name -> DSL.optionalFields("DisplayTile", References.BLOCK_NAME.in(schema), "Items", DSL.list(References.ITEM_STACK.in(schema)))
        );
        registerMinecart(schema, map, "MinecartCommandBlock");
        registerMob(schema, map, "ArmorStand");
        registerMob(schema, map, "Creeper");
        registerMob(schema, map, "Skeleton");
        registerMob(schema, map, "Spider");
        registerMob(schema, map, "Giant");
        registerMob(schema, map, "Zombie");
        registerMob(schema, map, "Slime");
        registerMob(schema, map, "Ghast");
        registerMob(schema, map, "PigZombie");
        schema.register(map, "Enderman", name -> DSL.optionalFields("carried", References.BLOCK_NAME.in(schema), equipment(schema)));
        registerMob(schema, map, "CaveSpider");
        registerMob(schema, map, "Silverfish");
        registerMob(schema, map, "Blaze");
        registerMob(schema, map, "LavaSlime");
        registerMob(schema, map, "EnderDragon");
        registerMob(schema, map, "WitherBoss");
        registerMob(schema, map, "Bat");
        registerMob(schema, map, "Witch");
        registerMob(schema, map, "Endermite");
        registerMob(schema, map, "Guardian");
        registerMob(schema, map, "Pig");
        registerMob(schema, map, "Sheep");
        registerMob(schema, map, "Cow");
        registerMob(schema, map, "Chicken");
        registerMob(schema, map, "Squid");
        registerMob(schema, map, "Wolf");
        registerMob(schema, map, "MushroomCow");
        registerMob(schema, map, "SnowMan");
        registerMob(schema, map, "Ozelot");
        registerMob(schema, map, "VillagerGolem");
        schema.register(
            map,
            "EntityHorse",
            name -> DSL.optionalFields(
                    "Items",
                    DSL.list(References.ITEM_STACK.in(schema)),
                    "ArmorItem",
                    References.ITEM_STACK.in(schema),
                    "SaddleItem",
                    References.ITEM_STACK.in(schema),
                    equipment(schema)
                )
        );
        registerMob(schema, map, "Rabbit");
        schema.register(
            map,
            "Villager",
            name -> DSL.optionalFields(
                    "Inventory",
                    DSL.list(References.ITEM_STACK.in(schema)),
                    "Offers",
                    DSL.optionalFields("Recipes", DSL.list(References.VILLAGER_TRADE.in(schema))),
                    equipment(schema)
                )
        );
        schema.registerSimple(map, "EnderCrystal");
        schema.register(map, "AreaEffectCloud", string -> DSL.optionalFields("Particle", References.PARTICLE.in(schema)));
        schema.registerSimple(map, "ShulkerBullet");
        schema.registerSimple(map, "DragonFireball");
        registerMob(schema, map, "Shulker");
        return map;
    }

    public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
        Map<String, Supplier<TypeTemplate>> map = Maps.newHashMap();
        registerInventory(schema, map, "Furnace");
        registerInventory(schema, map, "Chest");
        schema.registerSimple(map, "EnderChest");
        schema.register(map, "RecordPlayer", name -> DSL.optionalFields("RecordItem", References.ITEM_STACK.in(schema)));
        registerInventory(schema, map, "Trap");
        registerInventory(schema, map, "Dropper");
        schema.registerSimple(map, "Sign");
        schema.register(map, "MobSpawner", name -> References.UNTAGGED_SPAWNER.in(schema));
        schema.registerSimple(map, "Music");
        schema.registerSimple(map, "Piston");
        registerInventory(schema, map, "Cauldron");
        schema.registerSimple(map, "EnchantTable");
        schema.registerSimple(map, "Airportal");
        schema.registerSimple(map, "Control");
        schema.registerSimple(map, "Beacon");
        schema.registerSimple(map, "Skull");
        schema.registerSimple(map, "DLDetector");
        registerInventory(schema, map, "Hopper");
        schema.registerSimple(map, "Comparator");
        schema.register(map, "FlowerPot", name -> DSL.optionalFields("Item", DSL.or(DSL.constType(DSL.intType()), References.ITEM_NAME.in(schema))));
        schema.registerSimple(map, "Banner");
        schema.registerSimple(map, "Structure");
        schema.registerSimple(map, "EndGateway");
        return map;
    }

    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> map, Map<String, Supplier<TypeTemplate>> map2) {
        schema.registerType(false, References.LEVEL, DSL::remainder);
        schema.registerType(
            false,
            References.PLAYER,
            () -> DSL.optionalFields("Inventory", DSL.list(References.ITEM_STACK.in(schema)), "EnderItems", DSL.list(References.ITEM_STACK.in(schema)))
        );
        schema.registerType(
            false,
            References.CHUNK,
            () -> DSL.fields(
                    "Level",
                    DSL.optionalFields(
                        "Entities",
                        DSL.list(References.ENTITY_TREE.in(schema)),
                        "TileEntities",
                        DSL.list(DSL.or(References.BLOCK_ENTITY.in(schema), DSL.remainder())),
                        "TileTicks",
                        DSL.list(DSL.fields("i", References.BLOCK_NAME.in(schema)))
                    )
                )
        );
        schema.registerType(
            true,
            References.BLOCK_ENTITY,
            () -> DSL.optionalFields("components", References.DATA_COMPONENTS.in(schema), DSL.taggedChoiceLazy("id", DSL.string(), map2))
        );
        schema.registerType(true, References.ENTITY_TREE, () -> DSL.optionalFields("Riding", References.ENTITY_TREE.in(schema), References.ENTITY.in(schema)));
        schema.registerType(false, References.ENTITY_NAME, () -> DSL.constType(NamespacedSchema.namespacedString()));
        schema.registerType(true, References.ENTITY, () -> DSL.taggedChoiceLazy("id", DSL.string(), map));
        schema.registerType(
            true,
            References.ITEM_STACK,
            () -> DSL.hook(
                    DSL.optionalFields(
                        "id",
                        DSL.or(DSL.constType(DSL.intType()), References.ITEM_NAME.in(schema)),
                        "tag",
                        DSL.optionalFields(
                            Pair.of("EntityTag", References.ENTITY_TREE.in(schema)),
                            Pair.of("BlockEntityTag", References.BLOCK_ENTITY.in(schema)),
                            Pair.of("CanDestroy", DSL.list(References.BLOCK_NAME.in(schema))),
                            Pair.of("CanPlaceOn", DSL.list(References.BLOCK_NAME.in(schema))),
                            Pair.of("Items", DSL.list(References.ITEM_STACK.in(schema))),
                            Pair.of("ChargedProjectiles", DSL.list(References.ITEM_STACK.in(schema)))
                        )
                    ),
                    ADD_NAMES,
                    HookFunction.IDENTITY
                )
        );
        schema.registerType(false, References.OPTIONS, DSL::remainder);
        schema.registerType(false, References.BLOCK_NAME, () -> DSL.or(DSL.constType(DSL.intType()), DSL.constType(NamespacedSchema.namespacedString())));
        schema.registerType(false, References.ITEM_NAME, () -> DSL.constType(NamespacedSchema.namespacedString()));
        schema.registerType(false, References.STATS, DSL::remainder);
        schema.registerType(false, References.SAVED_DATA_COMMAND_STORAGE, DSL::remainder);
        schema.registerType(false, References.SAVED_DATA_FORCED_CHUNKS, DSL::remainder);
        schema.registerType(false, References.SAVED_DATA_MAP_DATA, DSL::remainder);
        schema.registerType(false, References.SAVED_DATA_MAP_INDEX, DSL::remainder);
        schema.registerType(false, References.SAVED_DATA_RAIDS, DSL::remainder);
        schema.registerType(false, References.SAVED_DATA_RANDOM_SEQUENCES, DSL::remainder);
        schema.registerType(
            false,
            References.SAVED_DATA_SCOREBOARD,
            () -> DSL.optionalFields(
                    "data", DSL.optionalFields("Objectives", DSL.list(References.OBJECTIVE.in(schema)), "Teams", DSL.list(References.TEAM.in(schema)))
                )
        );
        schema.registerType(
            false,
            References.SAVED_DATA_STRUCTURE_FEATURE_INDICES,
            () -> DSL.optionalFields("data", DSL.optionalFields("Features", DSL.compoundList(References.STRUCTURE_FEATURE.in(schema))))
        );
        schema.registerType(false, References.STRUCTURE_FEATURE, DSL::remainder);
        schema.registerType(false, References.OBJECTIVE, DSL::remainder);
        schema.registerType(false, References.TEAM, DSL::remainder);
        schema.registerType(true, References.UNTAGGED_SPAWNER, DSL::remainder);
        schema.registerType(false, References.POI_CHUNK, DSL::remainder);
        schema.registerType(false, References.WORLD_GEN_SETTINGS, DSL::remainder);
        schema.registerType(false, References.ENTITY_CHUNK, () -> DSL.optionalFields("Entities", DSL.list(References.ENTITY_TREE.in(schema))));
        schema.registerType(true, References.DATA_COMPONENTS, DSL::remainder);
        schema.registerType(
            true,
            References.VILLAGER_TRADE,
            () -> DSL.optionalFields(
                    "buy", References.ITEM_STACK.in(schema), "buyB", References.ITEM_STACK.in(schema), "sell", References.ITEM_STACK.in(schema)
                )
        );
        schema.registerType(true, References.PARTICLE, () -> DSL.constType(DSL.string()));
    }

    protected static <T> T addNames(Dynamic<T> stack, Map<String, String> renames, Map<String, String> map) {
        return stack.update("tag", tag -> tag.update("BlockEntityTag", blockEntityTag -> {
                String string = stack.get("id").asString().result().map(NamespacedSchema::ensureNamespaced).orElse("minecraft:air");
                if (!"minecraft:air".equals(string)) {
                    String string2 = renames.get(string);
                    if (string2 != null) {
                        return blockEntityTag.set("id", stack.createString(string2));
                    }

                    LOGGER.warn("Unable to resolve BlockEntity for ItemStack: {}", string);
                }

                return blockEntityTag;
            }).update("EntityTag", entityTag -> {
                if (entityTag.get("id").result().isPresent()) {
                    return entityTag;
                } else {
                    String string = NamespacedSchema.ensureNamespaced(stack.get("id").asString(""));
                    String string2 = map.get(string);
                    return string2 != null ? entityTag.set("id", stack.createString(string2)) : entityTag;
                }
            })).getValue();
    }
}
