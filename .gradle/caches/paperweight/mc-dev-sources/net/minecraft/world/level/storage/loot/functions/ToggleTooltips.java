package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ToggleTooltips extends LootItemConditionalFunction {
    private static final Map<DataComponentType<?>, ToggleTooltips.ComponentToggle<?>> TOGGLES = Stream.of(
            new ToggleTooltips.ComponentToggle<>(DataComponents.TRIM, ArmorTrim::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.DYED_COLOR, DyedItemColor::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.ENCHANTMENTS, ItemEnchantments::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.UNBREAKABLE, Unbreakable::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.CAN_BREAK, AdventureModePredicate::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.CAN_PLACE_ON, AdventureModePredicate::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.JUKEBOX_PLAYABLE, JukeboxPlayable::withTooltip)
        )
        .collect(Collectors.toMap(ToggleTooltips.ComponentToggle::type, toggle -> (ToggleTooltips.ComponentToggle<?>)toggle));
    private static final Codec<ToggleTooltips.ComponentToggle<?>> TOGGLE_CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE
        .byNameCodec()
        .comapFlatMap(
            componentType -> {
                ToggleTooltips.ComponentToggle<?> componentToggle = TOGGLES.get(componentType);
                return componentToggle != null
                    ? DataResult.success(componentToggle)
                    : DataResult.error(
                        () -> "Can't toggle tooltip visiblity for " + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey((DataComponentType<?>)componentType)
                    );
            },
            ToggleTooltips.ComponentToggle::type
        );
    public static final MapCodec<ToggleTooltips> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(Codec.unboundedMap(TOGGLE_CODEC, Codec.BOOL).fieldOf("toggles").forGetter(lootFunction -> lootFunction.values))
                .apply(instance, ToggleTooltips::new)
    );
    private final Map<ToggleTooltips.ComponentToggle<?>, Boolean> values;

    private ToggleTooltips(List<LootItemCondition> conditions, Map<ToggleTooltips.ComponentToggle<?>, Boolean> toggles) {
        super(conditions);
        this.values = toggles;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        this.values.forEach((componentType, showInTooltip) -> componentType.applyIfPresent(stack, showInTooltip));
        return stack;
    }

    @Override
    public LootItemFunctionType<ToggleTooltips> getType() {
        return LootItemFunctions.TOGGLE_TOOLTIPS;
    }

    static record ComponentToggle<T>(DataComponentType<T> type, ToggleTooltips.TooltipWither<T> setter) {
        public void applyIfPresent(ItemStack stack, boolean showInTooltip) {
            T object = stack.get(this.type);
            if (object != null) {
                stack.set(this.type, this.setter.withTooltip(object, showInTooltip));
            }
        }
    }

    @FunctionalInterface
    interface TooltipWither<T> {
        T withTooltip(T componentType, boolean showInTooltip);
    }
}
