package io.papermc.paper.registry;

import io.papermc.paper.registry.data.util.Conversions;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistryBuilderTest extends AbstractTestingBase {

    static List<Arguments> registries() {
        return List.of(
        );
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("registries")
    <M, T> void testEquality(final ResourceKey<? extends Registry<M>> resourceKey, final PaperRegistryBuilder.Filler<M, T, ?> filler) {
        final Registry<M> registry = AbstractTestingBase.REGISTRY_CUSTOM.registryOrThrow(resourceKey);
        for (final Map.Entry<ResourceKey<M>, M> entry : registry.entrySet()) {
            final M built = filler.fill(new Conversions(new RegistryOps.HolderLookupAdapter(AbstractTestingBase.REGISTRY_CUSTOM)), PaperRegistries.fromNms(entry.getKey()), entry.getValue()).build();
            assertEquals(entry.getValue(), built);
        }
    }
}
