package net.minecraft.stats;

import com.google.common.collect.Sets;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeBook {
    public final Set<ResourceLocation> known = Sets.newHashSet();
    protected final Set<ResourceLocation> highlight = Sets.newHashSet();
    private final RecipeBookSettings bookSettings = new RecipeBookSettings();

    public void copyOverData(RecipeBook book) {
        this.known.clear();
        this.highlight.clear();
        this.bookSettings.replaceFrom(book.bookSettings);
        this.known.addAll(book.known);
        this.highlight.addAll(book.highlight);
    }

    public void add(RecipeHolder<?> recipe) {
        if (!recipe.value().isSpecial()) {
            this.add(recipe.id());
        }
    }

    protected void add(ResourceLocation id) {
        this.known.add(id);
    }

    public boolean contains(@Nullable RecipeHolder<?> recipe) {
        return recipe != null && this.known.contains(recipe.id());
    }

    public boolean contains(ResourceLocation id) {
        return this.known.contains(id);
    }

    public void remove(RecipeHolder<?> recipe) {
        this.remove(recipe.id());
    }

    protected void remove(ResourceLocation id) {
        this.known.remove(id);
        this.highlight.remove(id);
    }

    public boolean willHighlight(RecipeHolder<?> recipe) {
        return this.highlight.contains(recipe.id());
    }

    public void removeHighlight(RecipeHolder<?> recipe) {
        this.highlight.remove(recipe.id());
    }

    public void addHighlight(RecipeHolder<?> recipe) {
        this.addHighlight(recipe.id());
    }

    protected void addHighlight(ResourceLocation id) {
        this.highlight.add(id);
    }

    public boolean isOpen(RecipeBookType category) {
        return this.bookSettings.isOpen(category);
    }

    public void setOpen(RecipeBookType category, boolean open) {
        this.bookSettings.setOpen(category, open);
    }

    public boolean isFiltering(RecipeBookMenu<?, ?> handler) {
        return this.isFiltering(handler.getRecipeBookType());
    }

    public boolean isFiltering(RecipeBookType category) {
        return this.bookSettings.isFiltering(category);
    }

    public void setFiltering(RecipeBookType category, boolean filteringCraftable) {
        this.bookSettings.setFiltering(category, filteringCraftable);
    }

    public void setBookSettings(RecipeBookSettings options) {
        this.bookSettings.replaceFrom(options);
    }

    public RecipeBookSettings getBookSettings() {
        return this.bookSettings.copy();
    }

    public void setBookSetting(RecipeBookType category, boolean guiOpen, boolean filteringCraftable) {
        this.bookSettings.setOpen(category, guiOpen);
        this.bookSettings.setFiltering(category, filteringCraftable);
    }
}
