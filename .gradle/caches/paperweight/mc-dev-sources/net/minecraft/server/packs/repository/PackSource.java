package net.minecraft.server.packs.repository;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public interface PackSource {
    UnaryOperator<Component> NO_DECORATION = UnaryOperator.identity();
    PackSource DEFAULT = create(NO_DECORATION, true);
    PackSource BUILT_IN = create(decorateWithSource("pack.source.builtin"), true);
    PackSource FEATURE = create(decorateWithSource("pack.source.feature"), false);
    PackSource WORLD = create(decorateWithSource("pack.source.world"), true);
    PackSource SERVER = create(decorateWithSource("pack.source.server"), true);

    Component decorate(Component packDisplayName);

    boolean shouldAddAutomatically();

    static PackSource create(UnaryOperator<Component> sourceTextSupplier, boolean canBeEnabledLater) {
        return new PackSource() {
            @Override
            public Component decorate(Component packDisplayName) {
                return sourceTextSupplier.apply(packDisplayName);
            }

            @Override
            public boolean shouldAddAutomatically() {
                return canBeEnabledLater;
            }
        };
    }

    private static UnaryOperator<Component> decorateWithSource(String translationKey) {
        Component component = Component.translatable(translationKey);
        return name -> Component.translatable("pack.nameAndSource", name, component).withStyle(ChatFormatting.GRAY);
    }
}
