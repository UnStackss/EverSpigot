package net.minecraft.data.advancements;

import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

public interface AdvancementSubProvider {
    void generate(HolderLookup.Provider lookup, Consumer<AdvancementHolder> exporter);

    static AdvancementHolder createPlaceholder(String id) {
        return Advancement.Builder.advancement().build(ResourceLocation.parse(id));
    }
}
