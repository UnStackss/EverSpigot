package org.bukkit.support.provider;

import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.util.Handleable;
import org.bukkit.support.test.RegistryTest;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

public class RegistryArgumentProvider implements ArgumentsProvider, AnnotationConsumer<RegistryTest> {

    private Class<? extends Keyed> registryType;

    @Override
    public void accept(RegistryTest registryTest) {
        this.registryType = registryTest.value();
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
        return RegistryArgumentProvider.getValues(io.papermc.paper.registry.PaperRegistryAccess.byType(this.registryType)); // Paper
    }

    public static Stream<? extends Arguments> getValues(io.papermc.paper.registry.RegistryKey<? extends Keyed> registryType) { // Paper
        Registry<?> registry = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(registryType); // Paper
        return registry.stream().map(keyed -> (Handleable<?>) keyed)
                .map(handleAble -> Arguments.of(handleAble, handleAble.getHandle()));
    }
}
