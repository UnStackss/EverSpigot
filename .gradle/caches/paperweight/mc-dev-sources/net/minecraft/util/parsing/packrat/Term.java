package net.minecraft.util.parsing.packrat;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.mutable.MutableBoolean;

public interface Term<S> {
    boolean parse(ParseState<S> state, Scope results, Control cut);

    static <S> Term<S> named(Atom<?> symbol) {
        return new Term.Reference<>(symbol);
    }

    static <S, T> Term<S> marker(Atom<T> symbol, T value) {
        return new Term.Marker<>(symbol, value);
    }

    @SafeVarargs
    static <S> Term<S> sequence(Term<S>... terms) {
        return new Term.Sequence<>(List.of(terms));
    }

    @SafeVarargs
    static <S> Term<S> alternative(Term<S>... terms) {
        return new Term.Alternative<>(List.of(terms));
    }

    static <S> Term<S> optional(Term<S> term) {
        return new Term.Maybe<>(term);
    }

    static <S> Term<S> cut() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> state, Scope results, Control cut) {
                cut.cut();
                return true;
            }

            @Override
            public String toString() {
                return "↑";
            }
        };
    }

    static <S> Term<S> empty() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> state, Scope results, Control cut) {
                return true;
            }

            @Override
            public String toString() {
                return "ε";
            }
        };
    }

    public static record Alternative<S>(List<Term<S>> elements) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> state, Scope results, Control cut) {
            MutableBoolean mutableBoolean = new MutableBoolean();
            Control control = mutableBoolean::setTrue;
            int i = state.mark();

            for (Term<S> term : this.elements) {
                if (mutableBoolean.isTrue()) {
                    break;
                }

                Scope scope = new Scope();
                if (term.parse(state, scope, control)) {
                    results.putAll(scope);
                    return true;
                }

                state.restore(i);
            }

            return false;
        }
    }

    public static record Marker<S, T>(Atom<T> name, T value) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> state, Scope results, Control cut) {
            results.put(this.name, this.value);
            return true;
        }
    }

    public static record Maybe<S>(Term<S> term) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> state, Scope results, Control cut) {
            int i = state.mark();
            if (!this.term.parse(state, results, cut)) {
                state.restore(i);
            }

            return true;
        }
    }

    public static record Reference<S, T>(Atom<T> name) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> state, Scope results, Control cut) {
            Optional<T> optional = state.parse(this.name);
            if (optional.isEmpty()) {
                return false;
            } else {
                results.put(this.name, optional.get());
                return true;
            }
        }
    }

    public static record Sequence<S>(List<Term<S>> elements) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> state, Scope results, Control cut) {
            int i = state.mark();

            for (Term<S> term : this.elements) {
                if (!term.parse(state, results, cut)) {
                    state.restore(i);
                    return false;
                }
            }

            return true;
        }
    }
}
