package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeUnlockedTrigger extends SimpleCriterionTrigger<RecipeUnlockedTrigger.TriggerInstance> {
    @Override
    public Codec<RecipeUnlockedTrigger.TriggerInstance> codec() {
        return RecipeUnlockedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, RecipeHolder<?> recipe) {
        this.trigger(player, conditions -> conditions.matches(recipe));
    }

    public static Criterion<RecipeUnlockedTrigger.TriggerInstance> unlocked(ResourceLocation id) {
        return CriteriaTriggers.RECIPE_UNLOCKED.createCriterion(new RecipeUnlockedTrigger.TriggerInstance(Optional.empty(), id));
    }

    public static record TriggerInstance(@Override Optional<ContextAwarePredicate> player, ResourceLocation recipe)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<RecipeUnlockedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RecipeUnlockedTrigger.TriggerInstance::player),
                        ResourceLocation.CODEC.fieldOf("recipe").forGetter(RecipeUnlockedTrigger.TriggerInstance::recipe)
                    )
                    .apply(instance, RecipeUnlockedTrigger.TriggerInstance::new)
        );

        public boolean matches(RecipeHolder<?> recipe) {
            return this.recipe.equals(recipe.id());
        }
    }
}
