package net.minecraft.util.parsing.packrat;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class Dictionary<S> {
    private final Map<Atom<?>, Rule<S, ?>> terms = new HashMap<>();

    public <T> void put(Atom<T> symbol, Rule<S, T> rule) {
        Rule<S, ?> rule2 = this.terms.putIfAbsent(symbol, rule);
        if (rule2 != null) {
            throw new IllegalArgumentException("Trying to override rule: " + symbol);
        }
    }

    public <T> void put(Atom<T> symbol, Term<S> term, Rule.RuleAction<S, T> action) {
        this.put(symbol, Rule.fromTerm(term, action));
    }

    public <T> void put(Atom<T> symbol, Term<S> term, Rule.SimpleRuleAction<T> action) {
        this.put(symbol, Rule.fromTerm(term, action));
    }

    @Nullable
    public <T> Rule<S, T> get(Atom<T> symbol) {
        return (Rule<S, T>)this.terms.get(symbol);
    }
}
