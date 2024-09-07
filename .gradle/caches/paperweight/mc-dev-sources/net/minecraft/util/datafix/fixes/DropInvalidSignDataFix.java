package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class DropInvalidSignDataFix extends NamedEntityFix {
    private static final String[] FIELDS_TO_DROP = new String[]{
        "Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4", "Color", "GlowingText"
    };

    public DropInvalidSignDataFix(Schema outputSchema, String name, String blockEntityId) {
        super(outputSchema, false, name, References.BLOCK_ENTITY, blockEntityId);
    }

    private static <T> Dynamic<T> fix(Dynamic<T> blockEntityData) {
        blockEntityData = blockEntityData.update("front_text", DropInvalidSignDataFix::fixText);
        blockEntityData = blockEntityData.update("back_text", DropInvalidSignDataFix::fixText);

        for (String string : FIELDS_TO_DROP) {
            blockEntityData = blockEntityData.remove(string);
        }

        return blockEntityData;
    }

    private static <T> Dynamic<T> fixText(Dynamic<T> textData) {
        boolean bl = textData.get("_filtered_correct").asBoolean(false);
        if (bl) {
            return textData.remove("_filtered_correct");
        } else {
            Optional<Stream<Dynamic<T>>> optional = textData.get("filtered_messages").asStreamOpt().result();
            if (optional.isEmpty()) {
                return textData;
            } else {
                Dynamic<T> dynamic = ComponentDataFixUtils.createEmptyComponent(textData.getOps());
                List<Dynamic<T>> list = textData.get("messages").asStreamOpt().result().orElse(Stream.of()).toList();
                List<Dynamic<T>> list2 = Streams.mapWithIndex(optional.get(), (message, index) -> {
                    Dynamic<T> dynamic2 = index < (long)list.size() ? list.get((int)index) : dynamic;
                    return message.equals(dynamic) ? dynamic2 : message;
                }).toList();
                return list2.stream().allMatch(message -> message.equals(dynamic))
                    ? textData.remove("filtered_messages")
                    : textData.set("filtered_messages", textData.createList(list2.stream()));
            }
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), DropInvalidSignDataFix::fix);
    }
}
