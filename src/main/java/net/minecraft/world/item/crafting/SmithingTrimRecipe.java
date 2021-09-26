package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmithingTrimRecipe;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class SmithingTrimRecipe implements SmithingRecipe {

    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;
    final boolean copyDataComponents; // Paper - Option to prevent data components copy

    public SmithingTrimRecipe(Ingredient template, Ingredient base, Ingredient addition) {
        // Paper start - Option to prevent data components copy
        this(template, base, addition, true);
    }
    public SmithingTrimRecipe(Ingredient template, Ingredient base, Ingredient addition, boolean copyDataComponents) {
        this.copyDataComponents = copyDataComponents;
        // Paper end - Option to prevent data components copy
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    public boolean matches(SmithingRecipeInput input, Level world) {
        return this.template.test(input.template()) && this.base.test(input.base()) && this.addition.test(input.addition());
    }

    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider lookup) {
        ItemStack itemstack = input.base();

        if (this.base.test(itemstack)) {
            Optional<Holder.Reference<TrimMaterial>> optional = TrimMaterials.getFromIngredient(lookup, input.addition());
            Optional<Holder.Reference<TrimPattern>> optional1 = TrimPatterns.getFromTemplate(lookup, input.template());

            if (optional.isPresent() && optional1.isPresent()) {
                ArmorTrim armortrim = (ArmorTrim) itemstack.get(DataComponents.TRIM);

                if (armortrim != null && armortrim.hasPatternAndMaterial((Holder) optional1.get(), (Holder) optional.get())) {
                    return ItemStack.EMPTY;
                }

                ItemStack itemstack1 = this.copyDataComponents ? itemstack.copyWithCount(1) : new ItemStack(itemstack.getItem(), 1); // Paper - Option to prevent data components copy

                itemstack1.set(DataComponents.TRIM, new ArmorTrim((Holder) optional.get(), (Holder) optional1.get()));
                return itemstack1;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        ItemStack itemstack = new ItemStack(Items.IRON_CHESTPLATE);
        Optional<Holder.Reference<TrimPattern>> optional = registriesLookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements().findFirst();
        Optional<Holder.Reference<TrimMaterial>> optional1 = registriesLookup.lookupOrThrow(Registries.TRIM_MATERIAL).get(TrimMaterials.REDSTONE);

        if (optional.isPresent() && optional1.isPresent()) {
            itemstack.set(DataComponents.TRIM, new ArmorTrim((Holder) optional1.get(), (Holder) optional.get()));
        }

        return itemstack;
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
        return RecipeSerializer.SMITHING_TRIM;
    }

    @Override
    public boolean isIncomplete() {
        return Stream.of(this.template, this.base, this.addition).anyMatch(Ingredient::isEmpty);
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        return new CraftSmithingTrimRecipe(id, CraftRecipe.toBukkit(this.template), CraftRecipe.toBukkit(this.base), CraftRecipe.toBukkit(this.addition), this.copyDataComponents); // Paper - Option to prevent data components copy
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<SmithingTrimRecipe> {

        private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Ingredient.CODEC.fieldOf("template").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.template;
            }), Ingredient.CODEC.fieldOf("base").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.base;
            }), Ingredient.CODEC.fieldOf("addition").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.addition;
            })).apply(instance, SmithingTrimRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.of(SmithingTrimRecipe.Serializer::toNetwork, SmithingTrimRecipe.Serializer::fromNetwork);

        public Serializer() {}

        @Override
        public MapCodec<SmithingTrimRecipe> codec() {
            return SmithingTrimRecipe.Serializer.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
            return SmithingTrimRecipe.Serializer.STREAM_CODEC;
        }

        private static SmithingTrimRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            Ingredient recipeitemstack = (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient recipeitemstack1 = (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient recipeitemstack2 = (Ingredient) Ingredient.CONTENTS_STREAM_CODEC.decode(buf);

            return new SmithingTrimRecipe(recipeitemstack, recipeitemstack1, recipeitemstack2);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, SmithingTrimRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.addition);
        }
    }
}
