package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;

public class FireworkRocketRecipe extends CustomRecipe {
    private static final Ingredient PAPER_INGREDIENT = Ingredient.of(Items.PAPER);
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);
    private static final Ingredient STAR_INGREDIENT = Ingredient.of(Items.FIREWORK_STAR);

    public FireworkRocketRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        boolean bl = false;
        int i = 0;

        for (int j = 0; j < input.size(); j++) {
            ItemStack itemStack = input.getItem(j);
            if (!itemStack.isEmpty()) {
                if (PAPER_INGREDIENT.test(itemStack)) {
                    if (bl) {
                        return false;
                    }

                    bl = true;
                } else if (GUNPOWDER_INGREDIENT.test(itemStack)) {
                    if (++i > 3) {
                        return false;
                    }
                } else if (!STAR_INGREDIENT.test(itemStack)) {
                    return false;
                }
            }
        }

        return bl && i >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        List<FireworkExplosion> list = new ArrayList<>();
        int i = 0;

        for (int j = 0; j < input.size(); j++) {
            ItemStack itemStack = input.getItem(j);
            if (!itemStack.isEmpty()) {
                if (GUNPOWDER_INGREDIENT.test(itemStack)) {
                    i++;
                } else if (STAR_INGREDIENT.test(itemStack)) {
                    FireworkExplosion fireworkExplosion = itemStack.get(DataComponents.FIREWORK_EXPLOSION);
                    if (fireworkExplosion != null) {
                        list.add(fireworkExplosion);
                    }
                }
            }
        }

        ItemStack itemStack2 = new ItemStack(Items.FIREWORK_ROCKET, 3);
        itemStack2.set(DataComponents.FIREWORKS, new Fireworks(i, list));
        return itemStack2;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        return new ItemStack(Items.FIREWORK_ROCKET, 3); // Paper - Fix incorrect crafting result amount
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.FIREWORK_ROCKET;
    }
}
