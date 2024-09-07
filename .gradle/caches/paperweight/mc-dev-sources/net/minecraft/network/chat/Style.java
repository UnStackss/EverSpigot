package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public class Style {
    public static final Style EMPTY = new Style(null, null, null, null, null, null, null, null, null, null);
    public static final ResourceLocation DEFAULT_FONT = ResourceLocation.withDefaultNamespace("default");
    @Nullable
    final TextColor color;
    @Nullable
    final Boolean bold;
    @Nullable
    final Boolean italic;
    @Nullable
    final Boolean underlined;
    @Nullable
    final Boolean strikethrough;
    @Nullable
    final Boolean obfuscated;
    @Nullable
    final ClickEvent clickEvent;
    @Nullable
    final HoverEvent hoverEvent;
    @Nullable
    final String insertion;
    @Nullable
    final ResourceLocation font;

    private static Style create(
        Optional<TextColor> color,
        Optional<Boolean> bold,
        Optional<Boolean> italic,
        Optional<Boolean> underlined,
        Optional<Boolean> strikethrough,
        Optional<Boolean> obfuscated,
        Optional<ClickEvent> optional,
        Optional<HoverEvent> optional2,
        Optional<String> optional3,
        Optional<ResourceLocation> optional4
    ) {
        Style style = new Style(
            color.orElse(null),
            bold.orElse(null),
            italic.orElse(null),
            underlined.orElse(null),
            strikethrough.orElse(null),
            obfuscated.orElse(null),
            optional.orElse(null),
            optional2.orElse(null),
            optional3.orElse(null),
            optional4.orElse(null)
        );
        return style.equals(EMPTY) ? EMPTY : style;
    }

    private Style(
        @Nullable TextColor color,
        @Nullable Boolean bold,
        @Nullable Boolean italic,
        @Nullable Boolean underlined,
        @Nullable Boolean strikethrough,
        @Nullable Boolean obfuscated,
        @Nullable ClickEvent clickEvent,
        @Nullable HoverEvent hoverEvent,
        @Nullable String insertion,
        @Nullable ResourceLocation font
    ) {
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.insertion = insertion;
        this.font = font;
    }

    @Nullable
    public TextColor getColor() {
        return this.color;
    }

    public boolean isBold() {
        return this.bold == Boolean.TRUE;
    }

    public boolean isItalic() {
        return this.italic == Boolean.TRUE;
    }

    public boolean isStrikethrough() {
        return this.strikethrough == Boolean.TRUE;
    }

    public boolean isUnderlined() {
        return this.underlined == Boolean.TRUE;
    }

    public boolean isObfuscated() {
        return this.obfuscated == Boolean.TRUE;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    @Nullable
    public ClickEvent getClickEvent() {
        return this.clickEvent;
    }

    @Nullable
    public HoverEvent getHoverEvent() {
        return this.hoverEvent;
    }

    @Nullable
    public String getInsertion() {
        return this.insertion;
    }

    public ResourceLocation getFont() {
        return this.font != null ? this.font : DEFAULT_FONT;
    }

    private static <T> Style checkEmptyAfterChange(Style newStyle, @Nullable T oldAttribute, @Nullable T newAttribute) {
        return oldAttribute != null && newAttribute == null && newStyle.equals(EMPTY) ? EMPTY : newStyle;
    }

    public Style withColor(@Nullable TextColor color) {
        return Objects.equals(this.color, color)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.color,
                color
            );
    }

    public Style withColor(@Nullable ChatFormatting color) {
        return this.withColor(color != null ? TextColor.fromLegacyFormat(color) : null);
    }

    public Style withColor(int rgbColor) {
        return this.withColor(TextColor.fromRgb(rgbColor));
    }

    public Style withBold(@Nullable Boolean bold) {
        return Objects.equals(this.bold, bold)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.bold,
                bold
            );
    }

    public Style withItalic(@Nullable Boolean italic) {
        return Objects.equals(this.italic, italic)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.italic,
                italic
            );
    }

    public Style withUnderlined(@Nullable Boolean underline) {
        return Objects.equals(this.underlined, underline)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    underline,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.underlined,
                underline
            );
    }

    public Style withStrikethrough(@Nullable Boolean strikethrough) {
        return Objects.equals(this.strikethrough, strikethrough)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.strikethrough,
                strikethrough
            );
    }

    public Style withObfuscated(@Nullable Boolean obfuscated) {
        return Objects.equals(this.obfuscated, obfuscated)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.obfuscated,
                obfuscated
            );
    }

    public Style withClickEvent(@Nullable ClickEvent clickEvent) {
        return Objects.equals(this.clickEvent, clickEvent)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.clickEvent,
                clickEvent
            );
    }

    public Style withHoverEvent(@Nullable HoverEvent hoverEvent) {
        return Objects.equals(this.hoverEvent, hoverEvent)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.hoverEvent,
                hoverEvent
            );
    }

    public Style withInsertion(@Nullable String insertion) {
        return Objects.equals(this.insertion, insertion)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    insertion,
                    this.font
                ),
                this.insertion,
                insertion
            );
    }

    public Style withFont(@Nullable ResourceLocation font) {
        return Objects.equals(this.font, font)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    font
                ),
                this.font,
                font
            );
    }

    public Style applyFormat(ChatFormatting formatting) {
        TextColor textColor = this.color;
        Boolean boolean_ = this.bold;
        Boolean boolean2 = this.italic;
        Boolean boolean3 = this.strikethrough;
        Boolean boolean4 = this.underlined;
        Boolean boolean5 = this.obfuscated;
        switch (formatting) {
            case OBFUSCATED:
                boolean5 = true;
                break;
            case BOLD:
                boolean_ = true;
                break;
            case STRIKETHROUGH:
                boolean3 = true;
                break;
            case UNDERLINE:
                boolean4 = true;
                break;
            case ITALIC:
                boolean2 = true;
                break;
            case RESET:
                return EMPTY;
            default:
                textColor = TextColor.fromLegacyFormat(formatting);
        }

        return new Style(textColor, boolean_, boolean2, boolean4, boolean3, boolean5, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyLegacyFormat(ChatFormatting formatting) {
        TextColor textColor = this.color;
        Boolean boolean_ = this.bold;
        Boolean boolean2 = this.italic;
        Boolean boolean3 = this.strikethrough;
        Boolean boolean4 = this.underlined;
        Boolean boolean5 = this.obfuscated;
        switch (formatting) {
            case OBFUSCATED:
                boolean5 = true;
                break;
            case BOLD:
                boolean_ = true;
                break;
            case STRIKETHROUGH:
                boolean3 = true;
                break;
            case UNDERLINE:
                boolean4 = true;
                break;
            case ITALIC:
                boolean2 = true;
                break;
            case RESET:
                return EMPTY;
            default:
                boolean5 = false;
                boolean_ = false;
                boolean3 = false;
                boolean4 = false;
                boolean2 = false;
                textColor = TextColor.fromLegacyFormat(formatting);
        }

        return new Style(textColor, boolean_, boolean2, boolean4, boolean3, boolean5, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyFormats(ChatFormatting... formattings) {
        TextColor textColor = this.color;
        Boolean boolean_ = this.bold;
        Boolean boolean2 = this.italic;
        Boolean boolean3 = this.strikethrough;
        Boolean boolean4 = this.underlined;
        Boolean boolean5 = this.obfuscated;

        for (ChatFormatting chatFormatting : formattings) {
            switch (chatFormatting) {
                case OBFUSCATED:
                    boolean5 = true;
                    break;
                case BOLD:
                    boolean_ = true;
                    break;
                case STRIKETHROUGH:
                    boolean3 = true;
                    break;
                case UNDERLINE:
                    boolean4 = true;
                    break;
                case ITALIC:
                    boolean2 = true;
                    break;
                case RESET:
                    return EMPTY;
                default:
                    textColor = TextColor.fromLegacyFormat(chatFormatting);
            }
        }

        return new Style(textColor, boolean_, boolean2, boolean4, boolean3, boolean5, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    public Style applyTo(Style parent) {
        if (this == EMPTY) {
            return parent;
        } else {
            return parent == EMPTY
                ? this
                : new Style(
                    this.color != null ? this.color : parent.color,
                    this.bold != null ? this.bold : parent.bold,
                    this.italic != null ? this.italic : parent.italic,
                    this.underlined != null ? this.underlined : parent.underlined,
                    this.strikethrough != null ? this.strikethrough : parent.strikethrough,
                    this.obfuscated != null ? this.obfuscated : parent.obfuscated,
                    this.clickEvent != null ? this.clickEvent : parent.clickEvent,
                    this.hoverEvent != null ? this.hoverEvent : parent.hoverEvent,
                    this.insertion != null ? this.insertion : parent.insertion,
                    this.font != null ? this.font : parent.font
                );
        }
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("{");

        class Collector {
            private boolean isNotFirst;

            private void prependSeparator() {
                if (this.isNotFirst) {
                    stringBuilder.append(',');
                }

                this.isNotFirst = true;
            }

            void addFlagString(String key, @Nullable Boolean value) {
                if (value != null) {
                    this.prependSeparator();
                    if (!value) {
                        stringBuilder.append('!');
                    }

                    stringBuilder.append(key);
                }
            }

            void addValueString(String key, @Nullable Object value) {
                if (value != null) {
                    this.prependSeparator();
                    stringBuilder.append(key);
                    stringBuilder.append('=');
                    stringBuilder.append(value);
                }
            }
        }

        Collector lv = new Collector();
        lv.addValueString("color", this.color);
        lv.addFlagString("bold", this.bold);
        lv.addFlagString("italic", this.italic);
        lv.addFlagString("underlined", this.underlined);
        lv.addFlagString("strikethrough", this.strikethrough);
        lv.addFlagString("obfuscated", this.obfuscated);
        lv.addValueString("clickEvent", this.clickEvent);
        lv.addValueString("hoverEvent", this.hoverEvent);
        lv.addValueString("insertion", this.insertion);
        lv.addValueString("font", this.font);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof Style style
                && this.bold == style.bold
                && Objects.equals(this.getColor(), style.getColor())
                && this.italic == style.italic
                && this.obfuscated == style.obfuscated
                && this.strikethrough == style.strikethrough
                && this.underlined == style.underlined
                && Objects.equals(this.clickEvent, style.clickEvent)
                && Objects.equals(this.hoverEvent, style.hoverEvent)
                && Objects.equals(this.insertion, style.insertion)
                && Objects.equals(this.font, style.font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.color, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion
        );
    }

    public static class Serializer {
        public static final MapCodec<Style> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                        TextColor.CODEC.optionalFieldOf("color").forGetter(style -> Optional.ofNullable(style.color)),
                        Codec.BOOL.optionalFieldOf("bold").forGetter(style -> Optional.ofNullable(style.bold)),
                        Codec.BOOL.optionalFieldOf("italic").forGetter(style -> Optional.ofNullable(style.italic)),
                        Codec.BOOL.optionalFieldOf("underlined").forGetter(style -> Optional.ofNullable(style.underlined)),
                        Codec.BOOL.optionalFieldOf("strikethrough").forGetter(style -> Optional.ofNullable(style.strikethrough)),
                        Codec.BOOL.optionalFieldOf("obfuscated").forGetter(style -> Optional.ofNullable(style.obfuscated)),
                        ClickEvent.CODEC.optionalFieldOf("clickEvent").forGetter(style -> Optional.ofNullable(style.clickEvent)),
                        HoverEvent.CODEC.optionalFieldOf("hoverEvent").forGetter(style -> Optional.ofNullable(style.hoverEvent)),
                        Codec.STRING.optionalFieldOf("insertion").forGetter(style -> Optional.ofNullable(style.insertion)),
                        ResourceLocation.CODEC.optionalFieldOf("font").forGetter(style -> Optional.ofNullable(style.font))
                    )
                    .apply(instance, Style::create)
        );
        public static final Codec<Style> CODEC = MAP_CODEC.codec();
        public static final StreamCodec<RegistryFriendlyByteBuf, Style> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
    }
}
