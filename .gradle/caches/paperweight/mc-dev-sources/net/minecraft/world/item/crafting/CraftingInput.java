package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;

public class CraftingInput implements RecipeInput {
    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());
    private final int width;
    private final int height;
    private final List<ItemStack> items;
    private final StackedContents stackedContents = new StackedContents();
    private final int ingredientCount;

    private CraftingInput(int width, int height, List<ItemStack> stacks) {
        this.width = width;
        this.height = height;
        this.items = stacks;
        int i = 0;

        for (ItemStack itemStack : stacks) {
            if (!itemStack.isEmpty()) {
                i++;
                this.stackedContents.accountStack(itemStack, 1);
            }
        }

        this.ingredientCount = i;
    }

    public static CraftingInput of(int width, int height, List<ItemStack> stacks) {
        return ofPositioned(width, height, stacks).input();
    }

    public static CraftingInput.Positioned ofPositioned(int width, int height, List<ItemStack> stacks) {
        if (width != 0 && height != 0) {
            int i = width - 1;
            int j = 0;
            int k = height - 1;
            int l = 0;

            for (int m = 0; m < height; m++) {
                boolean bl = true;

                for (int n = 0; n < width; n++) {
                    ItemStack itemStack = stacks.get(n + m * width);
                    if (!itemStack.isEmpty()) {
                        i = Math.min(i, n);
                        j = Math.max(j, n);
                        bl = false;
                    }
                }

                if (!bl) {
                    k = Math.min(k, m);
                    l = Math.max(l, m);
                }
            }

            int o = j - i + 1;
            int p = l - k + 1;
            if (o <= 0 || p <= 0) {
                return CraftingInput.Positioned.EMPTY;
            } else if (o == width && p == height) {
                return new CraftingInput.Positioned(new CraftingInput(width, height, stacks), i, k);
            } else {
                List<ItemStack> list = new ArrayList<>(o * p);

                for (int q = 0; q < p; q++) {
                    for (int r = 0; r < o; r++) {
                        int s = r + i + (q + k) * width;
                        list.add(stacks.get(s));
                    }
                }

                return new CraftingInput.Positioned(new CraftingInput(o, p, list), i, k);
            }
        } else {
            return CraftingInput.Positioned.EMPTY;
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    public ItemStack getItem(int x, int y) {
        return this.items.get(x + y * this.width);
    }

    @Override
    public int size() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.ingredientCount == 0;
    }

    public StackedContents stackedContents() {
        return this.stackedContents;
    }

    public List<ItemStack> items() {
        return this.items;
    }

    public int ingredientCount() {
        return this.ingredientCount;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    @Override
    public boolean equals(Object object) {
        return object == this
            || object instanceof CraftingInput craftingInput
                && this.width == craftingInput.width
                && this.height == craftingInput.height
                && this.ingredientCount == craftingInput.ingredientCount
                && ItemStack.listMatches(this.items, craftingInput.items);
    }

    @Override
    public int hashCode() {
        int i = ItemStack.hashStackList(this.items);
        i = 31 * i + this.width;
        return 31 * i + this.height;
    }

    public static record Positioned(CraftingInput input, int left, int top) {
        public static final CraftingInput.Positioned EMPTY = new CraftingInput.Positioned(CraftingInput.EMPTY, 0, 0);
    }
}
