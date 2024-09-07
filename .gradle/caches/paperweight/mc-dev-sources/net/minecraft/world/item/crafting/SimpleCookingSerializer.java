package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class SimpleCookingSerializer<T extends AbstractCookingRecipe> implements RecipeSerializer<T> {
    private final AbstractCookingRecipe.Factory<T> factory;
    private final MapCodec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

    public SimpleCookingSerializer(AbstractCookingRecipe.Factory<T> recipeFactory, int cookingTime) {
        this.factory = recipeFactory;
        this.codec = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                        CookingBookCategory.CODEC.fieldOf("category").orElse(CookingBookCategory.MISC).forGetter(recipe -> recipe.category),
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(recipe -> recipe.ingredient),
                        ItemStack.STRICT_SINGLE_ITEM_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                        Codec.FLOAT.fieldOf("experience").orElse(0.0F).forGetter(recipe -> recipe.experience),
                        Codec.INT.fieldOf("cookingtime").orElse(cookingTime).forGetter(recipe -> recipe.cookingTime)
                    )
                    .apply(instance, recipeFactory::create)
        );
        this.streamCodec = StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    @Override
    public MapCodec<T> codec() {
        return this.codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
        return this.streamCodec;
    }

    private T fromNetwork(RegistryFriendlyByteBuf buf) {
        String string = buf.readUtf();
        CookingBookCategory cookingBookCategory = buf.readEnum(CookingBookCategory.class);
        Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        ItemStack itemStack = ItemStack.STREAM_CODEC.decode(buf);
        float f = buf.readFloat();
        int i = buf.readVarInt();
        return this.factory.create(string, cookingBookCategory, ingredient, itemStack, f, i);
    }

    private void toNetwork(RegistryFriendlyByteBuf buf, T recipe) {
        buf.writeUtf(recipe.group);
        buf.writeEnum(recipe.category());
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient);
        ItemStack.STREAM_CODEC.encode(buf, recipe.result);
        buf.writeFloat(recipe.experience);
        buf.writeVarInt(recipe.cookingTime);
    }

    public AbstractCookingRecipe create(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        return this.factory.create(group, category, ingredient, result, experience, cookingTime);
    }
}
