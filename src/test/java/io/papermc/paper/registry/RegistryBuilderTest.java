package io.papermc.paper.registry;

import io.papermc.paper.registry.data.PaperEnchantmentRegistryEntry;
import io.papermc.paper.registry.data.PaperGameEventRegistryEntry;
import io.papermc.paper.registry.data.util.Conversions;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class RegistryBuilderTest extends AbstractTestingBase {

    static List<Arguments> registries() {
        return List.of(
            arguments(Registries.ENCHANTMENT, (PaperRegistryBuilder.Filler<Enchantment, org.bukkit.enchantments.Enchantment, PaperEnchantmentRegistryEntry.PaperBuilder>) PaperEnchantmentRegistryEntry.PaperBuilder::new),
            arguments(Registries.GAME_EVENT, (PaperRegistryBuilder.Filler<GameEvent, org.bukkit.GameEvent, PaperGameEventRegistryEntry.PaperBuilder>) PaperGameEventRegistryEntry.PaperBuilder::new)
        );
    }

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
