package net.minecraft.world.level.storage.loot;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public interface ContainerComponentManipulator<T> {
    DataComponentType<T> type();

    T empty();

    T setContents(T component, Stream<ItemStack> contents);

    Stream<ItemStack> getContents(T component);

    default void setContents(ItemStack stack, T component, Stream<ItemStack> contents) {
        T object = stack.getOrDefault(this.type(), component);
        T object2 = this.setContents(object, contents);
        stack.set(this.type(), object2);
    }

    default void setContents(ItemStack stack, Stream<ItemStack> contents) {
        this.setContents(stack, this.empty(), contents);
    }

    default void modifyItems(ItemStack stack, UnaryOperator<ItemStack> contentsOperator) {
        T object = stack.get(this.type());
        if (object != null) {
            UnaryOperator<ItemStack> unaryOperator = contentStack -> {
                if (contentStack.isEmpty()) {
                    return contentStack;
                } else {
                    ItemStack itemStack = contentsOperator.apply(contentStack);
                    itemStack.limitSize(itemStack.getMaxStackSize());
                    return itemStack;
                }
            };
            this.setContents(stack, this.getContents(object).map(unaryOperator));
        }
    }
}
