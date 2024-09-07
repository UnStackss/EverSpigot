package net.minecraft.world.entity.decoration;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class PaintingVariants {
    public static final ResourceKey<PaintingVariant> KEBAB = create("kebab");
    public static final ResourceKey<PaintingVariant> AZTEC = create("aztec");
    public static final ResourceKey<PaintingVariant> ALBAN = create("alban");
    public static final ResourceKey<PaintingVariant> AZTEC2 = create("aztec2");
    public static final ResourceKey<PaintingVariant> BOMB = create("bomb");
    public static final ResourceKey<PaintingVariant> PLANT = create("plant");
    public static final ResourceKey<PaintingVariant> WASTELAND = create("wasteland");
    public static final ResourceKey<PaintingVariant> POOL = create("pool");
    public static final ResourceKey<PaintingVariant> COURBET = create("courbet");
    public static final ResourceKey<PaintingVariant> SEA = create("sea");
    public static final ResourceKey<PaintingVariant> SUNSET = create("sunset");
    public static final ResourceKey<PaintingVariant> CREEBET = create("creebet");
    public static final ResourceKey<PaintingVariant> WANDERER = create("wanderer");
    public static final ResourceKey<PaintingVariant> GRAHAM = create("graham");
    public static final ResourceKey<PaintingVariant> MATCH = create("match");
    public static final ResourceKey<PaintingVariant> BUST = create("bust");
    public static final ResourceKey<PaintingVariant> STAGE = create("stage");
    public static final ResourceKey<PaintingVariant> VOID = create("void");
    public static final ResourceKey<PaintingVariant> SKULL_AND_ROSES = create("skull_and_roses");
    public static final ResourceKey<PaintingVariant> WITHER = create("wither");
    public static final ResourceKey<PaintingVariant> FIGHTERS = create("fighters");
    public static final ResourceKey<PaintingVariant> POINTER = create("pointer");
    public static final ResourceKey<PaintingVariant> PIGSCENE = create("pigscene");
    public static final ResourceKey<PaintingVariant> BURNING_SKULL = create("burning_skull");
    public static final ResourceKey<PaintingVariant> SKELETON = create("skeleton");
    public static final ResourceKey<PaintingVariant> DONKEY_KONG = create("donkey_kong");
    public static final ResourceKey<PaintingVariant> EARTH = create("earth");
    public static final ResourceKey<PaintingVariant> WIND = create("wind");
    public static final ResourceKey<PaintingVariant> WATER = create("water");
    public static final ResourceKey<PaintingVariant> FIRE = create("fire");
    public static final ResourceKey<PaintingVariant> BAROQUE = create("baroque");
    public static final ResourceKey<PaintingVariant> HUMBLE = create("humble");
    public static final ResourceKey<PaintingVariant> MEDITATIVE = create("meditative");
    public static final ResourceKey<PaintingVariant> PRAIRIE_RIDE = create("prairie_ride");
    public static final ResourceKey<PaintingVariant> UNPACKED = create("unpacked");
    public static final ResourceKey<PaintingVariant> BACKYARD = create("backyard");
    public static final ResourceKey<PaintingVariant> BOUQUET = create("bouquet");
    public static final ResourceKey<PaintingVariant> CAVEBIRD = create("cavebird");
    public static final ResourceKey<PaintingVariant> CHANGING = create("changing");
    public static final ResourceKey<PaintingVariant> COTAN = create("cotan");
    public static final ResourceKey<PaintingVariant> ENDBOSS = create("endboss");
    public static final ResourceKey<PaintingVariant> FERN = create("fern");
    public static final ResourceKey<PaintingVariant> FINDING = create("finding");
    public static final ResourceKey<PaintingVariant> LOWMIST = create("lowmist");
    public static final ResourceKey<PaintingVariant> ORB = create("orb");
    public static final ResourceKey<PaintingVariant> OWLEMONS = create("owlemons");
    public static final ResourceKey<PaintingVariant> PASSAGE = create("passage");
    public static final ResourceKey<PaintingVariant> POND = create("pond");
    public static final ResourceKey<PaintingVariant> SUNFLOWERS = create("sunflowers");
    public static final ResourceKey<PaintingVariant> TIDES = create("tides");

    public static void bootstrap(BootstrapContext<PaintingVariant> registry) {
        register(registry, KEBAB, 1, 1);
        register(registry, AZTEC, 1, 1);
        register(registry, ALBAN, 1, 1);
        register(registry, AZTEC2, 1, 1);
        register(registry, BOMB, 1, 1);
        register(registry, PLANT, 1, 1);
        register(registry, WASTELAND, 1, 1);
        register(registry, POOL, 2, 1);
        register(registry, COURBET, 2, 1);
        register(registry, SEA, 2, 1);
        register(registry, SUNSET, 2, 1);
        register(registry, CREEBET, 2, 1);
        register(registry, WANDERER, 1, 2);
        register(registry, GRAHAM, 1, 2);
        register(registry, MATCH, 2, 2);
        register(registry, BUST, 2, 2);
        register(registry, STAGE, 2, 2);
        register(registry, VOID, 2, 2);
        register(registry, SKULL_AND_ROSES, 2, 2);
        register(registry, WITHER, 2, 2);
        register(registry, FIGHTERS, 4, 2);
        register(registry, POINTER, 4, 4);
        register(registry, PIGSCENE, 4, 4);
        register(registry, BURNING_SKULL, 4, 4);
        register(registry, SKELETON, 4, 3);
        register(registry, EARTH, 2, 2);
        register(registry, WIND, 2, 2);
        register(registry, WATER, 2, 2);
        register(registry, FIRE, 2, 2);
        register(registry, DONKEY_KONG, 4, 3);
        register(registry, BAROQUE, 2, 2);
        register(registry, HUMBLE, 2, 2);
        register(registry, MEDITATIVE, 1, 1);
        register(registry, PRAIRIE_RIDE, 1, 2);
        register(registry, UNPACKED, 4, 4);
        register(registry, BACKYARD, 3, 4);
        register(registry, BOUQUET, 3, 3);
        register(registry, CAVEBIRD, 3, 3);
        register(registry, CHANGING, 4, 2);
        register(registry, COTAN, 3, 3);
        register(registry, ENDBOSS, 3, 3);
        register(registry, FERN, 3, 3);
        register(registry, FINDING, 4, 2);
        register(registry, LOWMIST, 4, 2);
        register(registry, ORB, 4, 4);
        register(registry, OWLEMONS, 3, 3);
        register(registry, PASSAGE, 4, 2);
        register(registry, POND, 3, 4);
        register(registry, SUNFLOWERS, 3, 3);
        register(registry, TIDES, 3, 3);
    }

    private static void register(BootstrapContext<PaintingVariant> registry, ResourceKey<PaintingVariant> key, int width, int height) {
        registry.register(key, new PaintingVariant(width, height, key.location()));
    }

    private static ResourceKey<PaintingVariant> create(String id) {
        return ResourceKey.create(Registries.PAINTING_VARIANT, ResourceLocation.withDefaultNamespace(id));
    }
}
