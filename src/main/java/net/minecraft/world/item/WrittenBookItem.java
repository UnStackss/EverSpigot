package net.minecraft.world.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class WrittenBookItem extends Item {
    public WrittenBookItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public Component getName(ItemStack stack) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            String string = writtenBookContent.title().raw();
            if (!StringUtil.isBlank(string)) {
                return Component.literal(string);
            }
        }

        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            if (!StringUtil.isBlank(writtenBookContent.author())) {
                tooltip.add(Component.translatable("book.byAuthor", writtenBookContent.author()).withStyle(ChatFormatting.GRAY));
            }

            tooltip.add(Component.translatable("book.generation." + writtenBookContent.generation()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.openItemGui(itemStack, hand);
        user.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }

    public static boolean resolveBookComponents(ItemStack book, CommandSourceStack commandSource, @Nullable Player player) {
        WrittenBookContent writtenBookContent = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.resolveSelectorsInBooks && writtenBookContent != null && !writtenBookContent.resolved()) { // Paper - Disable component selector resolving in books by default
            WrittenBookContent writtenBookContent2 = writtenBookContent.resolve(commandSource, player);
            if (writtenBookContent2 != null) {
                book.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent2);
                return true;
            }

            book.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent.markResolved());
        }

        return false;
    }
}
