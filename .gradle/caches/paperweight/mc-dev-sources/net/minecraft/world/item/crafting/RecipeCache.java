package net.minecraft.world.item.crafting;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RecipeCache {
    private final RecipeCache.Entry[] entries;
    private WeakReference<RecipeManager> cachedRecipeManager = new WeakReference<>(null);

    public RecipeCache(int size) {
        this.entries = new RecipeCache.Entry[size];
    }

    public Optional<RecipeHolder<CraftingRecipe>> get(Level world, CraftingInput input) {
        if (input.isEmpty()) {
            return Optional.empty();
        } else {
            this.validateRecipeManager(world);

            for (int i = 0; i < this.entries.length; i++) {
                RecipeCache.Entry entry = this.entries[i];
                if (entry != null && entry.matches(input)) {
                    this.moveEntryToFront(i);
                    return Optional.ofNullable(entry.value());
                }
            }

            return this.compute(input, world);
        }
    }

    private void validateRecipeManager(Level world) {
        RecipeManager recipeManager = world.getRecipeManager();
        if (recipeManager != this.cachedRecipeManager.get()) {
            this.cachedRecipeManager = new WeakReference<>(recipeManager);
            Arrays.fill(this.entries, null);
        }
    }

    private Optional<RecipeHolder<CraftingRecipe>> compute(CraftingInput input, Level world) {
        Optional<RecipeHolder<CraftingRecipe>> optional = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, world);
        this.insert(input, optional.orElse(null));
        return optional;
    }

    private void moveEntryToFront(int index) {
        if (index > 0) {
            RecipeCache.Entry entry = this.entries[index];
            System.arraycopy(this.entries, 0, this.entries, 1, index);
            this.entries[0] = entry;
        }
    }

    private void insert(CraftingInput input, @Nullable RecipeHolder<CraftingRecipe> recipe) {
        NonNullList<ItemStack> nonNullList = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < input.size(); i++) {
            nonNullList.set(i, input.getItem(i).copyWithCount(1));
        }

        System.arraycopy(this.entries, 0, this.entries, 1, this.entries.length - 1);
        this.entries[0] = new RecipeCache.Entry(nonNullList, input.width(), input.height(), recipe);
    }

    static record Entry(NonNullList<ItemStack> key, int width, int height, @Nullable RecipeHolder<CraftingRecipe> value) {
        public boolean matches(CraftingInput input) {
            if (this.width == input.width() && this.height == input.height()) {
                for (int i = 0; i < this.key.size(); i++) {
                    if (!ItemStack.isSameItemSameComponents(this.key.get(i), input.getItem(i))) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }
}
