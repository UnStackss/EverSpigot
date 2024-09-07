package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Arrays;
import java.util.function.Function;

public class EntityProjectileOwnerFix extends DataFix {
    public EntityProjectileOwnerFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();
        return this.fixTypeEverywhereTyped("EntityProjectileOwner", schema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> entityTyped) {
        entityTyped = this.updateEntity(entityTyped, "minecraft:egg", this::updateOwnerThrowable);
        entityTyped = this.updateEntity(entityTyped, "minecraft:ender_pearl", this::updateOwnerThrowable);
        entityTyped = this.updateEntity(entityTyped, "minecraft:experience_bottle", this::updateOwnerThrowable);
        entityTyped = this.updateEntity(entityTyped, "minecraft:snowball", this::updateOwnerThrowable);
        entityTyped = this.updateEntity(entityTyped, "minecraft:potion", this::updateOwnerThrowable);
        entityTyped = this.updateEntity(entityTyped, "minecraft:potion", this::updateItemPotion);
        entityTyped = this.updateEntity(entityTyped, "minecraft:llama_spit", this::updateOwnerLlamaSpit);
        entityTyped = this.updateEntity(entityTyped, "minecraft:arrow", this::updateOwnerArrow);
        entityTyped = this.updateEntity(entityTyped, "minecraft:spectral_arrow", this::updateOwnerArrow);
        return this.updateEntity(entityTyped, "minecraft:trident", this::updateOwnerArrow);
    }

    private Dynamic<?> updateOwnerArrow(Dynamic<?> entityDynamic) {
        long l = entityDynamic.get("OwnerUUIDMost").asLong(0L);
        long m = entityDynamic.get("OwnerUUIDLeast").asLong(0L);
        return this.setUUID(entityDynamic, l, m).remove("OwnerUUIDMost").remove("OwnerUUIDLeast");
    }

    private Dynamic<?> updateOwnerLlamaSpit(Dynamic<?> entityDynamic) {
        OptionalDynamic<?> optionalDynamic = entityDynamic.get("Owner");
        long l = optionalDynamic.get("OwnerUUIDMost").asLong(0L);
        long m = optionalDynamic.get("OwnerUUIDLeast").asLong(0L);
        return this.setUUID(entityDynamic, l, m).remove("Owner");
    }

    private Dynamic<?> updateItemPotion(Dynamic<?> entityDynamic) {
        OptionalDynamic<?> optionalDynamic = entityDynamic.get("Potion");
        return entityDynamic.set("Item", optionalDynamic.orElseEmptyMap()).remove("Potion");
    }

    private Dynamic<?> updateOwnerThrowable(Dynamic<?> entityDynamic) {
        String string = "owner";
        OptionalDynamic<?> optionalDynamic = entityDynamic.get("owner");
        long l = optionalDynamic.get("M").asLong(0L);
        long m = optionalDynamic.get("L").asLong(0L);
        return this.setUUID(entityDynamic, l, m).remove("owner");
    }

    private Dynamic<?> setUUID(Dynamic<?> entityDynamic, long most, long least) {
        String string = "OwnerUUID";
        return most != 0L && least != 0L
            ? entityDynamic.set("OwnerUUID", entityDynamic.createIntList(Arrays.stream(createUUIDArray(most, least))))
            : entityDynamic;
    }

    private static int[] createUUIDArray(long most, long least) {
        return new int[]{(int)(most >> 32), (int)most, (int)(least >> 32), (int)least};
    }

    private Typed<?> updateEntity(Typed<?> entityTyped, String matchId, Function<Dynamic<?>, Dynamic<?>> fixer) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, matchId);
        Type<?> type2 = this.getOutputSchema().getChoiceType(References.ENTITY, matchId);
        return entityTyped.updateTyped(DSL.namedChoice(matchId, type), type2, typed -> typed.update(DSL.remainderFinder(), fixer));
    }
}
