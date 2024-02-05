package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

public class SelectorContents implements ComponentContents {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SelectorContents> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.STRING.fieldOf("selector").forGetter(SelectorContents::getPattern),
                    ComponentSerialization.CODEC.optionalFieldOf("separator").forGetter(SelectorContents::getSeparator)
                )
                .apply(instance, SelectorContents::new)
    );
    public static final ComponentContents.Type<SelectorContents> TYPE = new ComponentContents.Type<>(CODEC, "selector");
    private final String pattern;
    @Nullable
    private final EntitySelector selector;
    protected final Optional<Component> separator;

    public SelectorContents(String pattern, Optional<Component> separator) {
        this.pattern = pattern;
        this.separator = separator;
        this.selector = parseSelector(pattern);
    }

    @Nullable
    private static EntitySelector parseSelector(String pattern) {
        EntitySelector entitySelector = null;

        try {
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(new StringReader(pattern), true);
            entitySelector = entitySelectorParser.parse();
        } catch (CommandSyntaxException var3) {
            return null; // Paper
        }

        return entitySelector;
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    public String getPattern() {
        return this.pattern;
    }

    @Nullable
    public EntitySelector getSelector() {
        return this.selector;
    }

    public Optional<Component> getSeparator() {
        return this.separator;
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity sender, int depth) throws CommandSyntaxException {
        if (source != null && this.selector != null) {
            Optional<? extends Component> optional = ComponentUtils.updateSeparatorForEntity(source, this.separator, sender, depth); // Paper - validate separator
            return ComponentUtils.formatList(this.selector.findEntities(source), optional, Entity::getDisplayName);
        } else {
            return Component.empty();
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> visitor, Style style) {
        return visitor.accept(style, this.pattern);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
        return visitor.accept(this.pattern);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof SelectorContents selectorContents
                && this.pattern.equals(selectorContents.pattern)
                && this.separator.equals(selectorContents.separator)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.pattern.hashCode();
        return 31 * i + this.separator.hashCode();
    }

    @Override
    public String toString() {
        return "pattern{" + this.pattern + "}";
    }
}
