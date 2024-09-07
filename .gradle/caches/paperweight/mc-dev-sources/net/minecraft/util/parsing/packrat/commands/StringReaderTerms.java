package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.stream.Stream;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;

public interface StringReaderTerms {
    static Term<StringReader> word(String string) {
        return new StringReaderTerms.TerminalWord(string);
    }

    static Term<StringReader> character(char c) {
        return new StringReaderTerms.TerminalCharacter(c);
    }

    public static record TerminalCharacter(char value) implements Term<StringReader> {
        @Override
        public boolean parse(ParseState<StringReader> state, Scope results, Control cut) {
            state.input().skipWhitespace();
            int i = state.mark();
            if (state.input().canRead() && state.input().read() == this.value) {
                return true;
            } else {
                state.errorCollector()
                    .store(
                        i,
                        suggestState -> Stream.of(String.valueOf(this.value)),
                        CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().create(this.value)
                    );
                return false;
            }
        }
    }

    public static record TerminalWord(String value) implements Term<StringReader> {
        @Override
        public boolean parse(ParseState<StringReader> state, Scope results, Control cut) {
            state.input().skipWhitespace();
            int i = state.mark();
            String string = state.input().readUnquotedString();
            if (!string.equals(this.value)) {
                state.errorCollector()
                    .store(i, suggestState -> Stream.of(this.value), CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().create(this.value));
                return false;
            } else {
                return true;
            }
        }
    }
}
