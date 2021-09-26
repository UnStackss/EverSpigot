package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmithingTransformRecipe;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class SmithingTransformRecipe implements SmithingRecipe {

    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;
    final ItemStack result;
    final boolean copyDataComponents; // Paper - Option to prevent data components copy

    public SmithingTransformRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
        // Paper start - Option to prevent data components copy
        this(template, base, addition, result, true);
    }
    public SmithingTransformRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result, boolean copyDataComponents) {
        this.copyDataComponents = copyDataComponents;
        // Paper end - Option to prevent data components copy
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    public boolean matches(SmithingRecipeInput input, Level world) {
        return this.template.test(input.template()) && this.base.test(input.base()) && this.addition.test(input.addition());
    }

    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider lookup) {
        ItemStack itemstack = input.base().transmuteCopy(this.result.getItem(), this.result.getCount());

        if (this.copyDataComponents) { // Paper - Option to prevent data components copy
        itemstack.applyComponents(this.result.getComponentsPatch());
        } // Paper - Option to prevent data components copy
        return itemstack;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        return this.result;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return this.template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return this.addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SMITHING_TRANSFORM;
    }

    @Override
    public boolean isIncomplete() {
        return Stream.of(this.template, this.base, this.addition).anyMatch(Ingredient::isEmpty);
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftSmithingTransformRecipe recipe = new CraftSmithingTransformRecipe(id, result, CraftRecipe.toBukkit(this.template), CraftRecipe.toBukkit(this.base), CraftRecipe.toBukkit(this.addition), this.copyDataComponents); // Paper - Option to prevent data components copy

        return recipe;
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<SmithingTransformRecipe> {

        private static final MapCodec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Ingredient.CODEC.fieldOf("template").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.template;
            }), Ingredient.CODEC.fieldOf("base").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.base;
            }), Ingredient.CODEC.fieldOf("addition").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.addition;
            }), ItemStack.STRICT_CODEC.fieldOf("result").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.result;
            })).apply(instance, SmithingTransformRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> STREAM_CODEC = StreamCodec.of(SmithingTransformRecipe.Serializer::toNetwork, SmithingTransformRecipe.Serializer::fromNetwork);

        public Serializer() {}

        @Override
        public MapCodec<SmithingTransformRecipe> codec() {
            return SmithingTransformRecipe.Serializer.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> streamCodec() {
            return SmithingTransformRecipe.Serializer.STREAM_CODEC;
        }

        private static SmithingTransformRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            Ingredient recipeitemstack = (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient recipeitemstack1 = (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient recipeitemstack2 = (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            ItemStack itemstack = (ItemStack) ItemStack.STREAM_CODEC.decode(buf);

            return new SmithingTransformRecipe(recipeitemstack, recipeitemstack1, recipeitemstack2, itemstack);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, SmithingTransformRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.addition);
            ItemStack.STREAM_CODEC.encode(buf, recipe.result);
        }
    }
}
