package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;

public class MemoryExpiryDataFix extends NamedEntityFix {
    public MemoryExpiryDataFix(Schema outputSchema, String choiceName) {
        super(outputSchema, false, "Memory expiry data fix (" + choiceName + ")", References.ENTITY, choiceName);
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }

    public Dynamic<?> fixTag(Dynamic<?> entityDynamic) {
        return entityDynamic.update("Brain", this::updateBrain);
    }

    private Dynamic<?> updateBrain(Dynamic<?> brainDynamic) {
        return brainDynamic.update("memories", this::updateMemories);
    }

    private Dynamic<?> updateMemories(Dynamic<?> memoriesDynamic) {
        return memoriesDynamic.updateMapValues(this::updateMemoryEntry);
    }

    private Pair<Dynamic<?>, Dynamic<?>> updateMemoryEntry(Pair<Dynamic<?>, Dynamic<?>> memoryKv) {
        return memoryKv.mapSecond(this::wrapMemoryValue);
    }

    private Dynamic<?> wrapMemoryValue(Dynamic<?> memoryValue) {
        return memoryValue.createMap(ImmutableMap.of(memoryValue.createString("value"), memoryValue));
    }
}
