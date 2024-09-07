package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public record DebugStickState(Map<Holder<Block>, Property<?>> properties) {
    public static final DebugStickState EMPTY = new DebugStickState(Map.of());
    public static final Codec<DebugStickState> CODEC = Codec.<Holder<Block>, Property<?>>dispatchedMap(
            BuiltInRegistries.BLOCK.holderByNameCodec(),
            block -> Codec.STRING
                    .comapFlatMap(
                        property -> {
                            Property<?> property2 = block.value().getStateDefinition().getProperty(property);
                            return property2 != null
                                ? DataResult.success(property2)
                                : DataResult.error(() -> "No property on " + block.getRegisteredName() + " with name: " + property);
                        },
                        Property::getName
                    )
        )
        .xmap(DebugStickState::new, DebugStickState::properties);

    public DebugStickState withProperty(Holder<Block> block, Property<?> property) {
        return new DebugStickState(Util.copyAndPut(this.properties, block, property));
    }
}
