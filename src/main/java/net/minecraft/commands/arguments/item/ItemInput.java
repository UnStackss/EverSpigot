package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemInput {
    private static final Dynamic2CommandExceptionType ERROR_STACK_TOO_BIG = new Dynamic2CommandExceptionType(
        (item, maxCount) -> Component.translatableEscape("arguments.item.overstacked", item, maxCount)
    );
    private final Holder<Item> item;
    private final DataComponentPatch components;

    public ItemInput(Holder<Item> item, DataComponentPatch components) {
        this.item = item;
        this.components = components;
    }

    public Item getItem() {
        return this.item.value();
    }

    public ItemStack createItemStack(int amount, boolean checkOverstack) throws CommandSyntaxException {
        ItemStack itemStack = new ItemStack(this.item, amount);
        itemStack.applyComponents(this.components);
        if (checkOverstack && amount > itemStack.getMaxStackSize()) {
            throw ERROR_STACK_TOO_BIG.create(this.getItemName(), itemStack.getMaxStackSize());
        } else {
            return itemStack;
        }
    }

    public String serialize(HolderLookup.Provider registries) {
        StringBuilder stringBuilder = new StringBuilder(this.getItemName());
        String string = this.serializeComponents(registries);
        if (!string.isEmpty()) {
            stringBuilder.append('[');
            stringBuilder.append(string);
            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }

    private String serializeComponents(HolderLookup.Provider registries) {
        DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
        return this.components.entrySet().stream().flatMap(entry -> {
            DataComponentType<?> dataComponentType = entry.getKey();
            ResourceLocation resourceLocation = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(dataComponentType);
            if (resourceLocation == null) {
                return Stream.empty();
            } else {
                Optional<?> optional = entry.getValue();
                if (optional.isPresent()) {
                    TypedDataComponent<?> typedDataComponent = TypedDataComponent.createUnchecked(dataComponentType, optional.get());
                    return typedDataComponent.encodeValue(dynamicOps).result().stream().map(value -> resourceLocation.toString() + "=" + value);
                } else {
                    return Stream.of("!" + resourceLocation.toString());
                }
            }
        }).collect(Collectors.joining(String.valueOf(',')));
    }

    private String getItemName() {
        return this.item.unwrapKey().<Object>map(ResourceKey::location).orElseGet(() -> "unknown[" + this.item + "]").toString(); // Paper - decompile fix
    }
}
