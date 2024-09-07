package net.minecraft.world.item.crafting;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

public class FireworkStarRecipe extends CustomRecipe {
    private static final Ingredient SHAPE_INGREDIENT = Ingredient.of(
        Items.FIRE_CHARGE,
        Items.FEATHER,
        Items.GOLD_NUGGET,
        Items.SKELETON_SKULL,
        Items.WITHER_SKELETON_SKULL,
        Items.CREEPER_HEAD,
        Items.PLAYER_HEAD,
        Items.DRAGON_HEAD,
        Items.ZOMBIE_HEAD,
        Items.PIGLIN_HEAD
    );
    private static final Ingredient TRAIL_INGREDIENT = Ingredient.of(Items.DIAMOND);
    private static final Ingredient TWINKLE_INGREDIENT = Ingredient.of(Items.GLOWSTONE_DUST);
    private static final Map<Item, FireworkExplosion.Shape> SHAPE_BY_ITEM = Util.make(Maps.newHashMap(), typeModifiers -> {
        typeModifiers.put(Items.FIRE_CHARGE, FireworkExplosion.Shape.LARGE_BALL);
        typeModifiers.put(Items.FEATHER, FireworkExplosion.Shape.BURST);
        typeModifiers.put(Items.GOLD_NUGGET, FireworkExplosion.Shape.STAR);
        typeModifiers.put(Items.SKELETON_SKULL, FireworkExplosion.Shape.CREEPER);
        typeModifiers.put(Items.WITHER_SKELETON_SKULL, FireworkExplosion.Shape.CREEPER);
        typeModifiers.put(Items.CREEPER_HEAD, FireworkExplosion.Shape.CREEPER);
        typeModifiers.put(Items.PLAYER_HEAD, FireworkExplosion.Shape.CREEPER);
        typeModifiers.put(Items.DRAGON_HEAD, FireworkExplosion.Shape.CREEPER);
        typeModifiers.put(Items.ZOMBIE_HEAD, FireworkExplosion.Shape.CREEPER);
        typeModifiers.put(Items.PIGLIN_HEAD, FireworkExplosion.Shape.CREEPER);
    });
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);

    public FireworkStarRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        boolean bl = false;
        boolean bl2 = false;
        boolean bl3 = false;
        boolean bl4 = false;
        boolean bl5 = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack = input.getItem(i);
            if (!itemStack.isEmpty()) {
                if (SHAPE_INGREDIENT.test(itemStack)) {
                    if (bl3) {
                        return false;
                    }

                    bl3 = true;
                } else if (TWINKLE_INGREDIENT.test(itemStack)) {
                    if (bl5) {
                        return false;
                    }

                    bl5 = true;
                } else if (TRAIL_INGREDIENT.test(itemStack)) {
                    if (bl4) {
                        return false;
                    }

                    bl4 = true;
                } else if (GUNPOWDER_INGREDIENT.test(itemStack)) {
                    if (bl) {
                        return false;
                    }

                    bl = true;
                } else {
                    if (!(itemStack.getItem() instanceof DyeItem)) {
                        return false;
                    }

                    bl2 = true;
                }
            }
        }

        return bl && bl2;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.SMALL_BALL;
        boolean bl = false;
        boolean bl2 = false;
        IntList intList = new IntArrayList();

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack = input.getItem(i);
            if (!itemStack.isEmpty()) {
                if (SHAPE_INGREDIENT.test(itemStack)) {
                    shape = SHAPE_BY_ITEM.get(itemStack.getItem());
                } else if (TWINKLE_INGREDIENT.test(itemStack)) {
                    bl = true;
                } else if (TRAIL_INGREDIENT.test(itemStack)) {
                    bl2 = true;
                } else if (itemStack.getItem() instanceof DyeItem) {
                    intList.add(((DyeItem)itemStack.getItem()).getDyeColor().getFireworkColor());
                }
            }
        }

        ItemStack itemStack2 = new ItemStack(Items.FIREWORK_STAR);
        itemStack2.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(shape, intList, IntList.of(), bl2, bl));
        return itemStack2;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        return new ItemStack(Items.FIREWORK_STAR);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.FIREWORK_STAR;
    }
}
