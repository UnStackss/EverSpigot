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

public class BlockEntitySignDoubleSidedEditableTextFix extends NamedEntityFix {
    public static final String FILTERED_CORRECT = "_filtered_correct";
    private static final String DEFAULT_COLOR = "black";

    public BlockEntitySignDoubleSidedEditableTextFix(Schema outputSchema, String name, String blockEntityId) {
        super(outputSchema, false, name, References.BLOCK_ENTITY, blockEntityId);
    }

    private static <T> Dynamic<T> fixTag(Dynamic<T> signData) {
        return signData.set("front_text", fixFrontTextTag(signData))
            .set("back_text", createDefaultText(signData))
            .set("is_waxed", signData.createBoolean(false));
    }

    private static <T> Dynamic<T> fixFrontTextTag(Dynamic<T> signData) {
        Dynamic<T> dynamic = ComponentDataFixUtils.createEmptyComponent(signData.getOps());
        List<Dynamic<T>> list = getLines(signData, "Text").map(text -> text.orElse(dynamic)).toList();
        Dynamic<T> dynamic2 = signData.emptyMap()
            .set("messages", signData.createList(list.stream()))
            .set("color", signData.get("Color").result().orElse(signData.createString("black")))
            .set("has_glowing_text", signData.get("GlowingText").result().orElse(signData.createBoolean(false)))
            .set("_filtered_correct", signData.createBoolean(true));
        List<Optional<Dynamic<T>>> list2 = getLines(signData, "FilteredText").toList();
        if (list2.stream().anyMatch(Optional::isPresent)) {
            dynamic2 = dynamic2.set("filtered_messages", signData.createList(Streams.mapWithIndex(list2.stream(), (message, index) -> {
                Dynamic<T> dynamicx = list.get((int)index);
                return message.orElse(dynamicx);
            })));
        }

        return dynamic2;
    }

    private static <T> Stream<Optional<Dynamic<T>>> getLines(Dynamic<T> signData, String prefix) {
        return Stream.of(
            signData.get(prefix + "1").result(), signData.get(prefix + "2").result(), signData.get(prefix + "3").result(), signData.get(prefix + "4").result()
        );
    }

    private static <T> Dynamic<T> createDefaultText(Dynamic<T> signData) {
        return signData.emptyMap()
            .set("messages", createEmptyLines(signData))
            .set("color", signData.createString("black"))
            .set("has_glowing_text", signData.createBoolean(false));
    }

    private static <T> Dynamic<T> createEmptyLines(Dynamic<T> signData) {
        Dynamic<T> dynamic = ComponentDataFixUtils.createEmptyComponent(signData.getOps());
        return signData.createList(Stream.of(dynamic, dynamic, dynamic, dynamic));
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), BlockEntitySignDoubleSidedEditableTextFix::fixTag);
    }
}
