package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import java.util.Optional;

public class ZombieVillagerRebuildXpFix extends NamedEntityFix {
    public ZombieVillagerRebuildXpFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "Zombie Villager XP rebuild", References.ENTITY, "minecraft:zombie_villager");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), zombieVillagerDynamic -> {
            Optional<Number> optional = zombieVillagerDynamic.get("Xp").asNumber().result();
            if (optional.isEmpty()) {
                int i = zombieVillagerDynamic.get("VillagerData").get("level").asInt(1);
                return zombieVillagerDynamic.set("Xp", zombieVillagerDynamic.createInt(VillagerRebuildLevelAndXpFix.getMinXpPerLevel(i)));
            } else {
                return zombieVillagerDynamic;
            }
        });
    }
}
