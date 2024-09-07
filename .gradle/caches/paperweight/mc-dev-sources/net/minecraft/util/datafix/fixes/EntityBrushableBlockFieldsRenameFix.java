package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityBrushableBlockFieldsRenameFix extends NamedEntityFix {
    public EntityBrushableBlockFieldsRenameFix(Schema outputSchema) {
        super(outputSchema, false, "EntityBrushableBlockFieldsRenameFix", References.BLOCK_ENTITY, "minecraft:brushable_block");
    }

    public Dynamic<?> fixTag(Dynamic<?> dynamic) {
        return dynamic.renameField("loot_table", "LootTable").renameField("loot_table_seed", "LootTableSeed");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
