package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

public class MutableComponent implements Component {
    private final ComponentContents contents;
    private final List<Component> siblings;
    private Style style;
    private FormattedCharSequence visualOrderText = FormattedCharSequence.EMPTY;
    @Nullable
    private Language decomposedWith;

    MutableComponent(ComponentContents content, List<Component> siblings, Style style) {
        this.contents = content;
        this.siblings = siblings;
        this.style = style;
    }

    public static MutableComponent create(ComponentContents content) {
        return new MutableComponent(content, Lists.newArrayList(), Style.EMPTY);
    }

    @Override
    public ComponentContents getContents() {
        return this.contents;
    }

    @Override
    public List<Component> getSiblings() {
        return this.siblings;
    }

    public MutableComponent setStyle(Style style) {
        this.style = style;
        return this;
    }

    @Override
    public Style getStyle() {
        return this.style;
    }

    public MutableComponent append(String text) {
        return text.isEmpty() ? this : this.append(Component.literal(text));
    }

    public MutableComponent append(Component text) {
        this.siblings.add(text);
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> styleUpdater) {
        this.setStyle(styleUpdater.apply(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(Style styleOverride) {
        this.setStyle(styleOverride.applyTo(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formattings) {
        this.setStyle(this.getStyle().applyFormats(formattings));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting formatting) {
        this.setStyle(this.getStyle().applyFormat(formatting));
        return this;
    }

    public MutableComponent withColor(int color) {
        this.setStyle(this.getStyle().withColor(color));
        return this;
    }

    @Override
    public FormattedCharSequence getVisualOrderText() {
        Language language = Language.getInstance();
        if (this.decomposedWith != language) {
            this.visualOrderText = language.getVisualOrder(this);
            this.decomposedWith = language;
        }

        return this.visualOrderText;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof MutableComponent mutableComponent
                && this.contents.equals(mutableComponent.contents)
                && this.style.equals(mutableComponent.style)
                && this.siblings.equals(mutableComponent.siblings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.contents, this.style, this.siblings);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(this.contents.toString());
        boolean bl = !this.style.isEmpty();
        boolean bl2 = !this.siblings.isEmpty();
        if (bl || bl2) {
            stringBuilder.append('[');
            if (bl) {
                stringBuilder.append("style=");
                stringBuilder.append(this.style);
            }

            if (bl && bl2) {
                stringBuilder.append(", ");
            }

            if (bl2) {
                stringBuilder.append("siblings=");
                stringBuilder.append(this.siblings);
            }

            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }
}
