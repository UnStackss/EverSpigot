package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

// CraftBukkit start
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
// CraftBukkit end

public class RecipeManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HolderLookup.Provider registries;
    public Multimap<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.of();
    public Map<ResourceLocation, RecipeHolder<?>> byName = ImmutableMap.of();
    private boolean hasErrors;

    public RecipeManager(HolderLookup.Provider registryLookup) {
        super(RecipeManager.GSON, Registries.elementsDirPath(Registries.RECIPE));
        this.registries = registryLookup;
    }

    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        this.hasErrors = false;
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> com_google_common_collect_immutablemap_builder = ImmutableMap.builder();
        RegistryOps<JsonElement> registryops = this.registries.createSerializationContext(JsonOps.INSTANCE);
        Iterator iterator = prepared.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<ResourceLocation, JsonElement> entry = (Entry) iterator.next();
            ResourceLocation minecraftkey = (ResourceLocation) entry.getKey();

            try {
                Recipe<?> irecipe = (Recipe) Recipe.CODEC.parse(registryops, (JsonElement) entry.getValue()).getOrThrow(JsonParseException::new);
                RecipeHolder<?> recipeholder = new RecipeHolder<>(minecraftkey, irecipe);

                builder.put(irecipe.getType(), recipeholder);
                com_google_common_collect_immutablemap_builder.put(minecraftkey, recipeholder);
            } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
                RecipeManager.LOGGER.error("Parsing error loading recipe {}", minecraftkey, jsonparseexception);
            }
        }

        // CraftBukkit start - mutable
        this.byType = LinkedHashMultimap.create(builder.build());
        this.byName = Maps.newHashMap(com_google_common_collect_immutablemap_builder.build());
        // CraftBukkit end
        RecipeManager.LOGGER.info("Loaded {} recipes", this.byName.size()); // Paper - Improve logging and errors; log correct number of recipes
    }

    // CraftBukkit start
    public void addRecipe(RecipeHolder<?> irecipe) {
        org.spigotmc.AsyncCatcher.catchOp("Recipe Add"); // Spigot
        Collection<RecipeHolder<?>> map = this.byType.get(irecipe.value().getType()); // CraftBukkit

        if (this.byName.containsKey(irecipe.id())) {
            throw new IllegalStateException("Duplicate recipe ignored with ID " + irecipe.id());
        } else {
            map.add(irecipe);
            this.byName.put(irecipe.id(), irecipe);
        }
    }
    // CraftBukkit end

    public boolean hadErrorsLoading() {
        return this.hasErrors;
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level world) {
        return this.getRecipeFor(type, input, world, (RecipeHolder) null);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level world, @Nullable ResourceLocation id) {
        RecipeHolder<T> recipeholder = id != null ? this.byKeyTyped(type, id) : null;

        return this.getRecipeFor(type, input, world, recipeholder);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level world, @Nullable RecipeHolder<T> recipe) {
        // CraftBukkit start
        List<RecipeHolder<T>> list = this.byType(type).stream().filter((recipeholder1) -> {
            return recipeholder1.value().matches(input, world);
        }).toList();
        Optional<RecipeHolder<T>> recipe1 = (list.isEmpty() || input.isEmpty()) ? Optional.empty() : (recipe != null && recipe.value().matches(input, world) ? Optional.of(recipe) : Optional.of(list.getLast())); // CraftBukkit - SPIGOT-4638: last recipe gets priority
        return recipe1;
        // CraftBukkit end
    }

    public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T> type) {
        return List.copyOf(this.byType(type));
    }

    public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getRecipesFor(RecipeType<T> type, I input, Level world) {
        return (List) this.byType(type).stream().filter((recipeholder) -> {
            return recipeholder.value().matches(input, world);
        }).sorted(Comparator.comparing((recipeholder) -> {
            return recipeholder.value().getResultItem(world.registryAccess()).getDescriptionId();
        })).collect(Collectors.toList());
    }

    private <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type) {
        return (Collection) this.byType.get(type); // CraftBukkit - decompile error
    }

    public <I extends RecipeInput, T extends Recipe<I>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> type, I input, Level world) {
        Optional<RecipeHolder<T>> optional = this.getRecipeFor(type, input, world);

        if (optional.isPresent()) {
            return ((RecipeHolder) optional.get()).value().getRemainingItems(input);
        } else {
            NonNullList<ItemStack> nonnulllist = NonNullList.withSize(input.size(), ItemStack.EMPTY);

            for (int i = 0; i < nonnulllist.size(); ++i) {
                nonnulllist.set(i, input.getItem(i));
            }

            return nonnulllist;
        }
    }

    public Optional<RecipeHolder<?>> byKey(ResourceLocation id) {
        return Optional.ofNullable((RecipeHolder) this.byName.get(id));
    }

    @Nullable
    private <T extends Recipe<?>> RecipeHolder<T> byKeyTyped(RecipeType<T> type, ResourceLocation id) {
        RecipeHolder<?> recipeholder = (RecipeHolder) this.byName.get(id);

        return recipeholder != null && recipeholder.value().getType().equals(type) ? (RecipeHolder) recipeholder : null; // CraftBukkit - decompile error
    }

    public Collection<RecipeHolder<?>> getOrderedRecipes() {
        return this.byType.values();
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return this.byName.values();
    }

    public Stream<ResourceLocation> getRecipeIds() {
        return this.byName.keySet().stream();
    }

    @VisibleForTesting
    protected static RecipeHolder<?> fromJson(ResourceLocation id, JsonObject json, HolderLookup.Provider registryLookup) {
        Recipe<?> irecipe = (Recipe) Recipe.CODEC.parse(registryLookup.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);

        return new RecipeHolder<>(id, irecipe);
    }

    public void replaceRecipes(Iterable<RecipeHolder<?>> recipes) {
        this.hasErrors = false;
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> com_google_common_collect_immutablemap_builder = ImmutableMap.builder();
        Iterator iterator = recipes.iterator();

        while (iterator.hasNext()) {
            RecipeHolder<?> recipeholder = (RecipeHolder) iterator.next();
            RecipeType<?> recipes1 = recipeholder.value().getType();

            builder.put(recipes1, recipeholder);
            com_google_common_collect_immutablemap_builder.put(recipeholder.id(), recipeholder);
        }

        // CraftBukkit start - mutable
        this.byType = LinkedHashMultimap.create(builder.build());
        this.byName = Maps.newHashMap(com_google_common_collect_immutablemap_builder.build());
        // CraftBukkit end
    }

    // CraftBukkit start
    public boolean removeRecipe(ResourceLocation mcKey) {
        Iterator<RecipeHolder<?>> iter = this.byType.values().iterator();
        while (iter.hasNext()) {
            RecipeHolder<?> recipe = iter.next();
            if (recipe.id().equals(mcKey)) {
                iter.remove();
            }
        }

        return this.byName.remove(mcKey) != null;
    }

    public void clearRecipes() {
        this.byType = LinkedHashMultimap.create();
        this.byName = Maps.newHashMap();
    }
    // CraftBukkit end

    public static <I extends RecipeInput, T extends Recipe<I>> RecipeManager.CachedCheck<I, T> createCheck(final RecipeType<T> type) {
        return new RecipeManager.CachedCheck<I, T>() {
            @Nullable
            private ResourceLocation lastRecipe;

            @Override
            public Optional<RecipeHolder<T>> getRecipeFor(I input, Level world) {
                RecipeManager craftingmanager = world.getRecipeManager();
                Optional<RecipeHolder<T>> optional = craftingmanager.getRecipeFor(type, input, world, this.lastRecipe);

                if (optional.isPresent()) {
                    RecipeHolder<T> recipeholder = (RecipeHolder) optional.get();

                    this.lastRecipe = recipeholder.id();
                    return Optional.of(recipeholder);
                } else {
                    return Optional.empty();
                }
            }
        };
    }

    public interface CachedCheck<I extends RecipeInput, T extends Recipe<I>> {

        Optional<RecipeHolder<T>> getRecipeFor(I input, Level world);
    }
}
