package net.minecraft.commands;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;

public interface CommandBuildContext extends HolderLookup.Provider {
    static CommandBuildContext simple(HolderLookup.Provider wrapperLookup, FeatureFlagSet enabledFeatures) {
        return new CommandBuildContext() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
                return wrapperLookup.listRegistries();
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return wrapperLookup.lookup(registryRef).map(wrapper -> wrapper.filterFeatures(enabledFeatures));
            }
        };
    }
}
