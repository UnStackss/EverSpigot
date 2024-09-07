package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;

public record ItemWritableBookPredicate(Optional<CollectionPredicate<Filterable<String>, ItemWritableBookPredicate.PagePredicate>> pages)
    implements SingleComponentItemPredicate<WritableBookContent> {
    public static final Codec<ItemWritableBookPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    CollectionPredicate.<Filterable<String>, ItemWritableBookPredicate.PagePredicate>codec(ItemWritableBookPredicate.PagePredicate.CODEC)
                        .optionalFieldOf("pages")
                        .forGetter(ItemWritableBookPredicate::pages)
                )
                .apply(instance, ItemWritableBookPredicate::new)
    );

    @Override
    public DataComponentType<WritableBookContent> componentType() {
        return DataComponents.WRITABLE_BOOK_CONTENT;
    }

    @Override
    public boolean matches(ItemStack stack, WritableBookContent component) {
        return !this.pages.isPresent() || this.pages.get().test(component.pages());
    }

    public static record PagePredicate(String contents) implements Predicate<Filterable<String>> {
        public static final Codec<ItemWritableBookPredicate.PagePredicate> CODEC = Codec.STRING
            .xmap(ItemWritableBookPredicate.PagePredicate::new, ItemWritableBookPredicate.PagePredicate::contents);

        @Override
        public boolean test(Filterable<String> filterable) {
            return filterable.raw().equals(this.contents);
        }
    }
}
