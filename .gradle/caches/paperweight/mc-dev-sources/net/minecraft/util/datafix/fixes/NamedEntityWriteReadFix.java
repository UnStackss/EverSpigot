package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.View;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.BitSet;
import net.minecraft.Util;

public abstract class NamedEntityWriteReadFix extends DataFix {
    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema outputSchema, boolean changesType, String name, TypeReference type, String choiceName) {
        super(outputSchema, changesType);
        this.name = name;
        this.type = type;
        this.entityName = choiceName;
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> type2 = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type3 = this.getOutputSchema().getType(this.type);
        Type<?> type4 = this.getOutputSchema().getChoiceType(this.type, this.entityName);
        OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, type2);
        Type<?> type5 = type2.all(typePatcher(type, type3), true, false).view().newType();
        return this.fix(type, type3, opticFinder, type4, type5);
    }

    private <S, T, A, B> TypeRewriteRule fix(Type<S> inputType, Type<T> outputType, OpticFinder<A> opticFinder, Type<B> outputSubtype, Type<?> rewrittenType) {
        return this.fixTypeEverywhere(this.name, inputType, outputType, dynamicOps -> input -> {
                Typed<S> typed = new Typed<>(inputType, dynamicOps, input);
                return (T)typed.update(opticFinder, outputSubtype, object -> {
                    Typed<A> typedx = new Typed<>((Type<A>)rewrittenType, dynamicOps, object);
                    return Util.<A, B>writeAndReadTypedOrThrow(typedx, outputSubtype, this::fix).getValue();
                }).getValue();
            });
    }

    private static <A, B> TypeRewriteRule typePatcher(Type<A> inputSubtype, Type<B> outputSubtype) {
        RewriteResult<A, B> rewriteResult = RewriteResult.create(View.create("Patcher", inputSubtype, outputSubtype, dynamicOps -> object -> {
                throw new UnsupportedOperationException();
            }), new BitSet());
        return TypeRewriteRule.everywhere(TypeRewriteRule.ifSame(inputSubtype, rewriteResult), PointFreeRule.nop(), true, true);
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> data);
}
