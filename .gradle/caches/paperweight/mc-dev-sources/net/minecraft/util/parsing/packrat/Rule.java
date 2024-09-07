package net.minecraft.util.parsing.packrat;

import java.util.Optional;

public interface Rule<S, T> {
    Optional<T> parse(ParseState<S> state);

    static <S, T> Rule<S, T> fromTerm(Term<S> term, Rule.RuleAction<S, T> action) {
        return new Rule.WrappedTerm<>(action, term);
    }

    static <S, T> Rule<S, T> fromTerm(Term<S> term, Rule.SimpleRuleAction<T> action) {
        return new Rule.WrappedTerm<>((state, results) -> Optional.of(action.run(results)), term);
    }

    @FunctionalInterface
    public interface RuleAction<S, T> {
        Optional<T> run(ParseState<S> state, Scope results);
    }

    @FunctionalInterface
    public interface SimpleRuleAction<T> {
        T run(Scope results);
    }

    public static record WrappedTerm<S, T>(Rule.RuleAction<S, T> action, Term<S> child) implements Rule<S, T> {
        @Override
        public Optional<T> parse(ParseState<S> state) {
            Scope scope = new Scope();
            return this.child.parse(state, scope, Control.UNBOUND) ? this.action.run(state, scope) : Optional.empty();
        }
    }
}
