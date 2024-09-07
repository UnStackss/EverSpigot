package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BlockPosFormatAndRenamesFix extends DataFix {
    private static final List<String> PATROLLING_MOBS = List.of(
        "minecraft:witch", "minecraft:ravager", "minecraft:pillager", "minecraft:illusioner", "minecraft:evoker", "minecraft:vindicator"
    );

    public BlockPosFormatAndRenamesFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private Typed<?> fixFields(Typed<?> typed, Map<String, String> oldToNewKey) {
        return typed.update(DSL.remainderFinder(), dynamic -> {
            for (Entry<String, String> entry : oldToNewKey.entrySet()) {
                dynamic = dynamic.renameAndFixField(entry.getKey(), entry.getValue(), ExtraDataFixUtils::fixBlockPos);
            }

            return dynamic;
        });
    }

    private <T> Dynamic<T> fixMapSavedData(Dynamic<T> dynamic) {
        return dynamic.update("frames", frames -> frames.createList(frames.asStream().map(frame -> {
                frame = frame.renameAndFixField("Pos", "pos", ExtraDataFixUtils::fixBlockPos);
                frame = frame.renameField("Rotation", "rotation");
                return frame.renameField("EntityId", "entity_id");
            }))).update("banners", banners -> banners.createList(banners.asStream().map(banner -> {
                banner = banner.renameField("Pos", "pos");
                banner = banner.renameField("Color", "color");
                return banner.renameField("Name", "name");
            })));
    }

    public TypeRewriteRule makeRule() {
        List<TypeRewriteRule> list = new ArrayList<>();
        this.addEntityRules(list);
        this.addBlockEntityRules(list);
        list.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format for map frames",
                this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA),
                typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("data", this::fixMapSavedData))
            )
        );
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        list.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format for compass target",
                type,
                ItemStackTagFix.createFixer(type, "minecraft:compass"::equals, tagDynamic -> tagDynamic.update("LodestonePos", ExtraDataFixUtils::fixBlockPos))
            )
        );
        return TypeRewriteRule.seq(list);
    }

    private void addEntityRules(List<TypeRewriteRule> rules) {
        rules.add(this.createEntityFixer(References.ENTITY, "minecraft:bee", Map.of("HivePos", "hive_pos", "FlowerPos", "flower_pos")));
        rules.add(this.createEntityFixer(References.ENTITY, "minecraft:end_crystal", Map.of("BeamTarget", "beam_target")));
        rules.add(this.createEntityFixer(References.ENTITY, "minecraft:wandering_trader", Map.of("WanderTarget", "wander_target")));

        for (String string : PATROLLING_MOBS) {
            rules.add(this.createEntityFixer(References.ENTITY, string, Map.of("PatrolTarget", "patrol_target")));
        }

        rules.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format in Leash for mobs",
                this.getInputSchema().getType(References.ENTITY),
                typed -> typed.update(DSL.remainderFinder(), entityDynamic -> entityDynamic.renameAndFixField("Leash", "leash", ExtraDataFixUtils::fixBlockPos))
            )
        );
    }

    private void addBlockEntityRules(List<TypeRewriteRule> rules) {
        rules.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:beehive", Map.of("FlowerPos", "flower_pos")));
        rules.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:end_gateway", Map.of("ExitPortal", "exit_portal")));
    }

    private TypeRewriteRule createEntityFixer(TypeReference typeReference, String id, Map<String, String> oldToNewKey) {
        String string = "BlockPos format in " + oldToNewKey.keySet() + " for " + id + " (" + typeReference.typeName() + ")";
        OpticFinder<?> opticFinder = DSL.namedChoice(id, this.getInputSchema().getChoiceType(typeReference, id));
        return this.fixTypeEverywhereTyped(
            string, this.getInputSchema().getType(typeReference), typed -> typed.updateTyped(opticFinder, typedx -> this.fixFields(typedx, oldToNewKey))
        );
    }
}
