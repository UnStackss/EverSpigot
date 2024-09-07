package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetWritableBookPagesFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetWritableBookPagesFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        WritableBookContent.PAGES_CODEC.fieldOf("pages").forGetter(function -> function.pages),
                        ListOperation.codec(100).forGetter(function -> function.pageOperation)
                    )
                )
                .apply(instance, SetWritableBookPagesFunction::new)
    );
    private final List<Filterable<String>> pages;
    private final ListOperation pageOperation;

    protected SetWritableBookPagesFunction(List<LootItemCondition> conditions, List<Filterable<String>> pages, ListOperation operation) {
        super(conditions);
        this.pages = pages;
        this.pageOperation = operation;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY, this::apply);
        return stack;
    }

    public WritableBookContent apply(WritableBookContent current) {
        List<Filterable<String>> list = this.pageOperation.apply(current.pages(), this.pages, 100);
        return current.withReplacedPages(list);
    }

    @Override
    public LootItemFunctionType<SetWritableBookPagesFunction> getType() {
        return LootItemFunctions.SET_WRITABLE_BOOK_PAGES;
    }
}
