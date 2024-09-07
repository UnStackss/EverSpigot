package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworksFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetFireworksFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        ListOperation.StandAlone.codec(FireworkExplosion.CODEC, 256).optionalFieldOf("explosions").forGetter(function -> function.explosions),
                        ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("flight_duration").forGetter(function -> function.flightDuration)
                    )
                )
                .apply(instance, SetFireworksFunction::new)
    );
    public static final Fireworks DEFAULT_VALUE = new Fireworks(0, List.of());
    private final Optional<ListOperation.StandAlone<FireworkExplosion>> explosions;
    private final Optional<Integer> flightDuration;

    protected SetFireworksFunction(
        List<LootItemCondition> conditions, Optional<ListOperation.StandAlone<FireworkExplosion>> explosions, Optional<Integer> flightDuration
    ) {
        super(conditions);
        this.explosions = explosions;
        this.flightDuration = flightDuration;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.FIREWORKS, DEFAULT_VALUE, this::apply);
        return stack;
    }

    private Fireworks apply(Fireworks fireworksComponent) {
        return new Fireworks(
            this.flightDuration.orElseGet(fireworksComponent::flightDuration),
            this.explosions.<List<FireworkExplosion>>map(values -> values.apply(fireworksComponent.explosions())).orElse(fireworksComponent.explosions())
        );
    }

    @Override
    public LootItemFunctionType<SetFireworksFunction> getType() {
        return LootItemFunctions.SET_FIREWORKS;
    }
}
