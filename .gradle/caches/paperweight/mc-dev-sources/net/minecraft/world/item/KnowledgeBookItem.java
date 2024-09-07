package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class KnowledgeBookItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public KnowledgeBookItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        List<ResourceLocation> list = itemStack.getOrDefault(DataComponents.RECIPES, List.of());
        itemStack.consume(1, user);
        if (list.isEmpty()) {
            return InteractionResultHolder.fail(itemStack);
        } else {
            if (!world.isClientSide) {
                RecipeManager recipeManager = world.getServer().getRecipeManager();
                List<RecipeHolder<?>> list2 = new ArrayList<>(list.size());

                for (ResourceLocation resourceLocation : list) {
                    Optional<RecipeHolder<?>> optional = recipeManager.byKey(resourceLocation);
                    if (!optional.isPresent()) {
                        LOGGER.error("Invalid recipe: {}", resourceLocation);
                        return InteractionResultHolder.fail(itemStack);
                    }

                    list2.add(optional.get());
                }

                user.awardRecipes(list2);
                user.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
        }
    }
}
