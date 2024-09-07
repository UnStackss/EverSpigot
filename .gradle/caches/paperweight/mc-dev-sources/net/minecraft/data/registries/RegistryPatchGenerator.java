package net.minecraft.data.registries;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Cloner;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RegistryPatchGenerator {
    public static CompletableFuture<RegistrySetBuilder.PatchedRegistries> createLookup(
        CompletableFuture<HolderLookup.Provider> registriesFuture, RegistrySetBuilder builder
    ) {
        return registriesFuture.thenApply(
            lookup -> {
                RegistryAccess.Frozen frozen = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                Cloner.Factory factory = new Cloner.Factory();
                RegistryDataLoader.WORLDGEN_REGISTRIES.forEach(entry -> entry.runWithArguments(factory::addCodec));
                RegistrySetBuilder.PatchedRegistries patchedRegistries = builder.buildPatch(frozen, lookup, factory);
                HolderLookup.Provider provider = patchedRegistries.full();
                Optional<HolderLookup.RegistryLookup<Biome>> optional = provider.lookup(Registries.BIOME);
                Optional<HolderLookup.RegistryLookup<PlacedFeature>> optional2 = provider.lookup(Registries.PLACED_FEATURE);
                if (optional.isPresent() || optional2.isPresent()) {
                    VanillaRegistries.validateThatAllBiomeFeaturesHaveBiomeFilter(
                        optional2.orElseGet(() -> lookup.lookupOrThrow(Registries.PLACED_FEATURE)),
                        optional.orElseGet(() -> lookup.lookupOrThrow(Registries.BIOME))
                    );
                }

                return patchedRegistries;
            }
        );
    }
}
