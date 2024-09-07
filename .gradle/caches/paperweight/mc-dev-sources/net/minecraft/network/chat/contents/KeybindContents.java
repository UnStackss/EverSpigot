package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class KeybindContents implements ComponentContents {
    public static final MapCodec<KeybindContents> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.STRING.fieldOf("keybind").forGetter(content -> content.name)).apply(instance, KeybindContents::new)
    );
    public static final ComponentContents.Type<KeybindContents> TYPE = new ComponentContents.Type<>(CODEC, "keybind");
    private final String name;
    @Nullable
    private Supplier<Component> nameResolver;

    public KeybindContents(String key) {
        this.name = key;
    }

    private Component getNestedComponent() {
        if (this.nameResolver == null) {
            this.nameResolver = KeybindResolver.keyResolver.apply(this.name);
        }

        return this.nameResolver.get();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
        return this.getNestedComponent().visit(visitor);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> visitor, Style style) {
        return this.getNestedComponent().visit(visitor, style);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof KeybindContents keybindContents && this.name.equals(keybindContents.name)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public String toString() {
        return "keybind{" + this.name + "}";
    }

    public String getName() {
        return this.name;
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }
}
