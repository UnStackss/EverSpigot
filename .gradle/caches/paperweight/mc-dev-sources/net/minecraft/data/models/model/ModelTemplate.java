package net.minecraft.data.models.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class ModelTemplate {
    private final Optional<ResourceLocation> model;
    private final Set<TextureSlot> requiredSlots;
    private final Optional<String> suffix;

    public ModelTemplate(Optional<ResourceLocation> parent, Optional<String> variant, TextureSlot... requiredTextureKeys) {
        this.model = parent;
        this.suffix = variant;
        this.requiredSlots = ImmutableSet.copyOf(requiredTextureKeys);
    }

    public ResourceLocation getDefaultModelLocation(Block block) {
        return ModelLocationUtils.getModelLocation(block, this.suffix.orElse(""));
    }

    public ResourceLocation create(Block block, TextureMapping textures, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelCollector) {
        return this.create(ModelLocationUtils.getModelLocation(block, this.suffix.orElse("")), textures, modelCollector);
    }

    public ResourceLocation createWithSuffix(
        Block block, String suffix, TextureMapping textures, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelCollector
    ) {
        return this.create(ModelLocationUtils.getModelLocation(block, suffix + this.suffix.orElse("")), textures, modelCollector);
    }

    public ResourceLocation createWithOverride(
        Block block, String suffix, TextureMapping textures, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelCollector
    ) {
        return this.create(ModelLocationUtils.getModelLocation(block, suffix), textures, modelCollector);
    }

    public ResourceLocation create(ResourceLocation id, TextureMapping textures, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelCollector) {
        return this.create(id, textures, modelCollector, this::createBaseTemplate);
    }

    public ResourceLocation create(
        ResourceLocation id, TextureMapping textures, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelCollector, ModelTemplate.JsonFactory jsonFactory
    ) {
        Map<TextureSlot, ResourceLocation> map = this.createMap(textures);
        modelCollector.accept(id, () -> jsonFactory.create(id, map));
        return id;
    }

    public JsonObject createBaseTemplate(ResourceLocation id, Map<TextureSlot, ResourceLocation> textures) {
        JsonObject jsonObject = new JsonObject();
        this.model.ifPresent(parent -> jsonObject.addProperty("parent", parent.toString()));
        if (!textures.isEmpty()) {
            JsonObject jsonObject2 = new JsonObject();
            textures.forEach((textureKey, texture) -> jsonObject2.addProperty(textureKey.getId(), texture.toString()));
            jsonObject.add("textures", jsonObject2);
        }

        return jsonObject;
    }

    private Map<TextureSlot, ResourceLocation> createMap(TextureMapping textures) {
        return Streams.concat(this.requiredSlots.stream(), textures.getForced()).collect(ImmutableMap.toImmutableMap(Function.identity(), textures::get));
    }

    public interface JsonFactory {
        JsonObject create(ResourceLocation id, Map<TextureSlot, ResourceLocation> textures);
    }
}
