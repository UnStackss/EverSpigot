package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;

public class EntityHealthFix extends DataFix {
    private static final Set<String> ENTITIES = Sets.newHashSet(
        "ArmorStand",
        "Bat",
        "Blaze",
        "CaveSpider",
        "Chicken",
        "Cow",
        "Creeper",
        "EnderDragon",
        "Enderman",
        "Endermite",
        "EntityHorse",
        "Ghast",
        "Giant",
        "Guardian",
        "LavaSlime",
        "MushroomCow",
        "Ozelot",
        "Pig",
        "PigZombie",
        "Rabbit",
        "Sheep",
        "Shulker",
        "Silverfish",
        "Skeleton",
        "Slime",
        "SnowMan",
        "Spider",
        "Squid",
        "Villager",
        "VillagerGolem",
        "Witch",
        "WitherBoss",
        "Wolf",
        "Zombie"
    );

    public EntityHealthFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public Dynamic<?> fixTag(Dynamic<?> entityDynamic) {
        Optional<Number> optional = entityDynamic.get("HealF").asNumber().result();
        Optional<Number> optional2 = entityDynamic.get("Health").asNumber().result();
        float f;
        if (optional.isPresent()) {
            f = optional.get().floatValue();
            entityDynamic = entityDynamic.remove("HealF");
        } else {
            if (!optional2.isPresent()) {
                return entityDynamic;
            }

            f = optional2.get().floatValue();
        }

        return entityDynamic.set("Health", entityDynamic.createFloat(f));
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityHealthFix", this.getInputSchema().getType(References.ENTITY), entityTyped -> entityTyped.update(DSL.remainderFinder(), this::fixTag)
        );
    }
}
