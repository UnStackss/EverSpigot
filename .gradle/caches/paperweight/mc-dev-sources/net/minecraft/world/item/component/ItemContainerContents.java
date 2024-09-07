package net.minecraft.world.item.component;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public final class ItemContainerContents {
    private static final int NO_SLOT = -1;
    private static final int MAX_SIZE = 256;
    public static final ItemContainerContents EMPTY = new ItemContainerContents(NonNullList.create());
    public static final Codec<ItemContainerContents> CODEC = ItemContainerContents.Slot.CODEC
        .sizeLimitedListOf(256)
        .xmap(ItemContainerContents::fromSlots, ItemContainerContents::asSlots);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemContainerContents> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC
        .apply(ByteBufCodecs.list(256))
        .map(ItemContainerContents::new, component -> component.items);
    private final NonNullList<ItemStack> items;
    private final int hashCode;

    private ItemContainerContents(NonNullList<ItemStack> stacks) {
        if (stacks.size() > 256) {
            throw new IllegalArgumentException("Got " + stacks.size() + " items, but maximum is 256");
        } else {
            this.items = stacks;
            this.hashCode = ItemStack.hashStackList(stacks);
        }
    }

    private ItemContainerContents(int size) {
        this(NonNullList.withSize(size, ItemStack.EMPTY));
    }

    private ItemContainerContents(List<ItemStack> stacks) {
        this(stacks.size());

        for (int i = 0; i < stacks.size(); i++) {
            this.items.set(i, stacks.get(i));
        }
    }

    private static ItemContainerContents fromSlots(List<ItemContainerContents.Slot> slots) {
        OptionalInt optionalInt = slots.stream().mapToInt(ItemContainerContents.Slot::index).max();
        if (optionalInt.isEmpty()) {
            return EMPTY;
        } else {
            ItemContainerContents itemContainerContents = new ItemContainerContents(optionalInt.getAsInt() + 1);

            for (ItemContainerContents.Slot slot : slots) {
                itemContainerContents.items.set(slot.index(), slot.item());
            }

            return itemContainerContents;
        }
    }

    public static ItemContainerContents fromItems(List<ItemStack> stacks) {
        int i = findLastNonEmptySlot(stacks);
        if (i == -1) {
            return EMPTY;
        } else {
            ItemContainerContents itemContainerContents = new ItemContainerContents(i + 1);

            for (int j = 0; j <= i; j++) {
                itemContainerContents.items.set(j, stacks.get(j).copy());
            }

            return itemContainerContents;
        }
    }

    private static int findLastNonEmptySlot(List<ItemStack> stacks) {
        for (int i = stacks.size() - 1; i >= 0; i--) {
            if (!stacks.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private List<ItemContainerContents.Slot> asSlots() {
        List<ItemContainerContents.Slot> list = new ArrayList<>();

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()) {
                list.add(new ItemContainerContents.Slot(i, itemStack));
            }
        }

        return list;
    }

    public void copyInto(NonNullList<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack itemStack = i < this.items.size() ? this.items.get(i) : ItemStack.EMPTY;
            stacks.set(i, itemStack.copy());
        }
    }

    public ItemStack copyOne() {
        return this.items.isEmpty() ? ItemStack.EMPTY : this.items.get(0).copy();
    }

    public Stream<ItemStack> stream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Stream<ItemStack> nonEmptyStream() {
        return this.items.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
    }

    public Iterable<ItemStack> nonEmptyItems() {
        return Iterables.filter(this.items, stack -> !stack.isEmpty());
    }

    public Iterable<ItemStack> nonEmptyItemsCopy() {
        return Iterables.transform(this.nonEmptyItems(), ItemStack::copy);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof ItemContainerContents itemContainerContents && ItemStack.listMatches(this.items, itemContainerContents.items)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    static record Slot(int index, ItemStack item) {
        public static final Codec<ItemContainerContents.Slot> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        Codec.intRange(0, 255).fieldOf("slot").forGetter(ItemContainerContents.Slot::index),
                        ItemStack.CODEC.fieldOf("item").forGetter(ItemContainerContents.Slot::item)
                    )
                    .apply(instance, ItemContainerContents.Slot::new)
        );
    }
}
