package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;

public abstract class ResourceLookupRule<C, V> implements Rule<StringReader, V>, ResourceSuggestion {
    private final Atom<ResourceLocation> idParser;
    protected final C context;

    protected ResourceLookupRule(Atom<ResourceLocation> symbol, C callbacks) {
        this.idParser = symbol;
        this.context = callbacks;
    }

    @Override
    public Optional<V> parse(ParseState<StringReader> state) {
        state.input().skipWhitespace();
        int i = state.mark();
        Optional<ResourceLocation> optional = state.parse(this.idParser);
        if (optional.isPresent()) {
            try {
                return Optional.of(this.validateElement(state.input(), optional.get()));
            } catch (Exception var5) {
                state.errorCollector().store(i, this, var5);
                return Optional.empty();
            }
        } else {
            state.errorCollector().store(i, this, ResourceLocation.ERROR_INVALID.createWithContext(state.input()));
            return Optional.empty();
        }
    }

    protected abstract V validateElement(ImmutableStringReader reader, ResourceLocation id) throws Exception;
}
