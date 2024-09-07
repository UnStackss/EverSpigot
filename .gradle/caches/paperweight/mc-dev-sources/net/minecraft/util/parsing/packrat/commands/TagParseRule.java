package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;

public class TagParseRule implements Rule<StringReader, Tag> {
    public static final Rule<StringReader, Tag> INSTANCE = new TagParseRule();

    private TagParseRule() {
    }

    @Override
    public Optional<Tag> parse(ParseState<StringReader> state) {
        state.input().skipWhitespace();
        int i = state.mark();

        try {
            return Optional.of(new TagParser(state.input()).readValue());
        } catch (Exception var4) {
            state.errorCollector().store(i, var4);
            return Optional.empty();
        }
    }
}
