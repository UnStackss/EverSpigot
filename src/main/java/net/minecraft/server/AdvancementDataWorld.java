package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.resources.IResourceManager;
import net.minecraft.server.packs.resources.ResourceDataJson;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.GameProfilerFiller;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.HashMap;
// CraftBukkit end

public class AdvancementDataWorld extends ResourceDataJson {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Gson GSON = (new GsonBuilder()).create();
    public Map<MinecraftKey, AdvancementHolder> advancements = new HashMap<>(); // CraftBukkit - SPIGOT-7734: mutable
    private AdvancementTree tree = new AdvancementTree();
    private final HolderLookup.a registries;

    public AdvancementDataWorld(HolderLookup.a holderlookup_a) {
        super(AdvancementDataWorld.GSON, Registries.elementsDirPath(Registries.ADVANCEMENT));
        this.registries = holderlookup_a;
    }

    protected void apply(Map<MinecraftKey, JsonElement> map, IResourceManager iresourcemanager, GameProfilerFiller gameprofilerfiller) {
        RegistryOps<JsonElement> registryops = this.registries.createSerializationContext(JsonOps.INSTANCE);
        Builder<MinecraftKey, AdvancementHolder> builder = ImmutableMap.builder();

        map.forEach((minecraftkey, jsonelement) -> {
            // Spigot start
            if (org.spigotmc.SpigotConfig.disabledAdvancements != null && (org.spigotmc.SpigotConfig.disabledAdvancements.contains("*") || org.spigotmc.SpigotConfig.disabledAdvancements.contains(minecraftkey.toString()) || org.spigotmc.SpigotConfig.disabledAdvancements.contains(minecraftkey.getNamespace()))) {
                return;
            }
            // Spigot end

            try {
                Advancement advancement = (Advancement) Advancement.CODEC.parse(registryops, jsonelement).getOrThrow(JsonParseException::new);

                this.validate(minecraftkey, advancement);
                builder.put(minecraftkey, new AdvancementHolder(minecraftkey, advancement));
            } catch (Exception exception) {
                AdvancementDataWorld.LOGGER.error("Parsing error loading custom advancement {}: {}", minecraftkey, exception.getMessage());
            }

        });
        this.advancements = new HashMap<>(builder.buildOrThrow()); // CraftBukkit - SPIGOT-7734: mutable
        AdvancementTree advancementtree = new AdvancementTree();

        advancementtree.addAll(this.advancements.values());
        Iterator iterator = advancementtree.roots().iterator();

        while (iterator.hasNext()) {
            AdvancementNode advancementnode = (AdvancementNode) iterator.next();

            if (advancementnode.holder().value().display().isPresent()) {
                TreeNodePosition.run(advancementnode);
            }
        }

        this.tree = advancementtree;
    }

    private void validate(MinecraftKey minecraftkey, Advancement advancement) {
        ProblemReporter.a problemreporter_a = new ProblemReporter.a();

        advancement.validate(problemreporter_a, this.registries.asGetterLookup());
        problemreporter_a.getReport().ifPresent((s) -> {
            AdvancementDataWorld.LOGGER.warn("Found validation problems in advancement {}: \n{}", minecraftkey, s);
        });
    }

    @Nullable
    public AdvancementHolder get(MinecraftKey minecraftkey) {
        return (AdvancementHolder) this.advancements.get(minecraftkey);
    }

    public AdvancementTree tree() {
        return this.tree;
    }

    public Collection<AdvancementHolder> getAllAdvancements() {
        return this.advancements.values();
    }
}
