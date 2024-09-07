package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.RandomSource;

public class EntityZombieVillagerTypeFix extends NamedEntityFix {
    private static final int PROFESSION_MAX = 6;

    public EntityZombieVillagerTypeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityZombieVillagerTypeFix", References.ENTITY, "Zombie");
    }

    public Dynamic<?> fixTag(Dynamic<?> zombieDynamic) {
        if (zombieDynamic.get("IsVillager").asBoolean(false)) {
            if (zombieDynamic.get("ZombieType").result().isEmpty()) {
                int i = this.getVillagerProfession(zombieDynamic.get("VillagerProfession").asInt(-1));
                if (i == -1) {
                    i = this.getVillagerProfession(RandomSource.create().nextInt(6));
                }

                zombieDynamic = zombieDynamic.set("ZombieType", zombieDynamic.createInt(i));
            }

            zombieDynamic = zombieDynamic.remove("IsVillager");
        }

        return zombieDynamic;
    }

    private int getVillagerProfession(int type) {
        return type >= 0 && type < 6 ? type : -1;
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
