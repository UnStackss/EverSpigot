package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Iterator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapelessRecipe;
// CraftBukkit end

public class ShapelessRecipe implements CraftingRecipe {

    final String group;
    final CraftingBookCategory category;
    final ItemStack result;
    final NonNullList<Ingredient> ingredients;

    public ShapelessRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        this.group = group;
        this.category = category;
        this.result = result;
        this.ingredients = ingredients;
    }

    // CraftBukkit start
    @SuppressWarnings("unchecked")
    @Override
    public org.bukkit.inventory.ShapelessRecipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapelessRecipe recipe = new CraftShapelessRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        for (Ingredient list : this.ingredients) {
            recipe.addIngredient(CraftRecipe.toBukkit(list));
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPELESS_RECIPE;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    public boolean matches(CraftingInput input, Level world) {
        return input.ingredientCount() != this.ingredients.size() ? false : (input.size() == 1 && this.ingredients.size() == 1 ? ((Ingredient) this.ingredients.getFirst()).test(input.getItem(0)) : input.stackedContents().canCraft(this, (IntList) null));
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= this.ingredients.size();
    }

    public static class Serializer implements RecipeSerializer<ShapelessRecipe> {

        private static final MapCodec<ShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.STRING.optionalFieldOf("group", "").forGetter((shapelessrecipes) -> {
                return shapelessrecipes.group;
            }), CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter((shapelessrecipes) -> {
                return shapelessrecipes.category;
            }), ItemStack.STRICT_CODEC.fieldOf("result").forGetter((shapelessrecipes) -> {
                return shapelessrecipes.result;
            }), Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap((list) -> {
                Ingredient[] arecipeitemstack = (Ingredient[]) list.stream().filter((recipeitemstack) -> {
                    return !recipeitemstack.isEmpty();
                }).toArray((i) -> {
                    return new Ingredient[i];
                });

                return arecipeitemstack.length == 0 ? DataResult.error(() -> {
                    return "No ingredients for shapeless recipe";
                }) : (arecipeitemstack.length > 9 ? DataResult.error(() -> {
                    return "Too many ingredients for shapeless recipe";
                }) : DataResult.success(NonNullList.of(Ingredient.EMPTY, arecipeitemstack)));
            }, DataResult::success).forGetter((shapelessrecipes) -> {
                return shapelessrecipes.ingredients;
            })).apply(instance, ShapelessRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC = StreamCodec.of(ShapelessRecipe.Serializer::toNetwork, ShapelessRecipe.Serializer::fromNetwork);

        public Serializer() {}

        @Override
        public MapCodec<ShapelessRecipe> codec() {
            return ShapelessRecipe.Serializer.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> streamCodec() {
            return ShapelessRecipe.Serializer.STREAM_CODEC;
        }

        private static ShapelessRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            String s = buf.readUtf();
            CraftingBookCategory craftingbookcategory = (CraftingBookCategory) buf.readEnum(CraftingBookCategory.class);
            int i = buf.readVarInt();
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i, Ingredient.EMPTY);

            nonnulllist.replaceAll((recipeitemstack) -> {
                return (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            });
            ItemStack itemstack = (ItemStack) ItemStack.STREAM_CODEC.decode(buf);

            return new ShapelessRecipe(s, craftingbookcategory, itemstack, nonnulllist);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, ShapelessRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeEnum(recipe.category);
            buf.writeVarInt(recipe.ingredients.size());
            Iterator iterator = recipe.ingredients.iterator();

            while (iterator.hasNext()) {
                Ingredient recipeitemstack = (Ingredient) iterator.next();

                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipeitemstack);
            }

            ItemStack.STREAM_CODEC.encode(buf, recipe.result);
        }
    }
}
