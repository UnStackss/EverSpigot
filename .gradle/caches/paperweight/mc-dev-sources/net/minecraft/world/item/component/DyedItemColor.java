package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public record DyedItemColor(int rgb, boolean showInTooltip) implements TooltipProvider {
    private static final Codec<DyedItemColor> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Codec.INT.fieldOf("rgb").forGetter(DyedItemColor::rgb),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(DyedItemColor::showInTooltip)
                )
                .apply(instance, DyedItemColor::new)
    );
    public static final Codec<DyedItemColor> CODEC = Codec.withAlternative(FULL_CODEC, Codec.INT, rgb -> new DyedItemColor(rgb, true));
    public static final StreamCodec<ByteBuf, DyedItemColor> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, DyedItemColor::rgb, ByteBufCodecs.BOOL, DyedItemColor::showInTooltip, DyedItemColor::new
    );
    public static final int LEATHER_COLOR = -6265536;

    public static int getOrDefault(ItemStack stack, int defaultColor) {
        DyedItemColor dyedItemColor = stack.get(DataComponents.DYED_COLOR);
        return dyedItemColor != null ? FastColor.ARGB32.opaque(dyedItemColor.rgb()) : defaultColor;
    }

    public static ItemStack applyDyes(ItemStack stack, List<DyeItem> dyes) {
        if (!stack.is(ItemTags.DYEABLE)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = stack.copyWithCount(1);
            int i = 0;
            int j = 0;
            int k = 0;
            int l = 0;
            int m = 0;
            DyedItemColor dyedItemColor = itemStack.get(DataComponents.DYED_COLOR);
            if (dyedItemColor != null) {
                int n = FastColor.ARGB32.red(dyedItemColor.rgb());
                int o = FastColor.ARGB32.green(dyedItemColor.rgb());
                int p = FastColor.ARGB32.blue(dyedItemColor.rgb());
                l += Math.max(n, Math.max(o, p));
                i += n;
                j += o;
                k += p;
                m++;
            }

            for (DyeItem dyeItem : dyes) {
                int q = dyeItem.getDyeColor().getTextureDiffuseColor();
                int r = FastColor.ARGB32.red(q);
                int s = FastColor.ARGB32.green(q);
                int t = FastColor.ARGB32.blue(q);
                l += Math.max(r, Math.max(s, t));
                i += r;
                j += s;
                k += t;
                m++;
            }

            int u = i / m;
            int v = j / m;
            int w = k / m;
            float f = (float)l / (float)m;
            float g = (float)Math.max(u, Math.max(v, w));
            u = (int)((float)u * f / g);
            v = (int)((float)v * f / g);
            w = (int)((float)w * f / g);
            int x = FastColor.ARGB32.color(0, u, v, w);
            boolean bl = dyedItemColor == null || dyedItemColor.showInTooltip();
            itemStack.set(DataComponents.DYED_COLOR, new DyedItemColor(x, bl));
            return itemStack;
        }
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag type) {
        if (this.showInTooltip) {
            if (type.isAdvanced()) {
                tooltip.accept(Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", this.rgb)).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.accept(Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    public DyedItemColor withTooltip(boolean showInTooltip) {
        return new DyedItemColor(this.rgb, showInTooltip);
    }
}
