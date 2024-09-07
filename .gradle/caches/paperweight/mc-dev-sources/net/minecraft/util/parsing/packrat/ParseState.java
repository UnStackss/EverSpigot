package net.minecraft.util.parsing.packrat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public abstract class ParseState<S> {
    private final Map<ParseState.CacheKey<?>, ParseState.CacheEntry<?>> ruleCache = new HashMap<>();
    private final Dictionary<S> dictionary;
    private final ErrorCollector<S> errorCollector;

    protected ParseState(Dictionary<S> rules, ErrorCollector<S> errors) {
        this.dictionary = rules;
        this.errorCollector = errors;
    }

    public ErrorCollector<S> errorCollector() {
        return this.errorCollector;
    }

    public <T> Optional<T> parseTopRule(Atom<T> startSymbol) {
        Optional<T> optional = this.parse(startSymbol);
        if (optional.isPresent()) {
            this.errorCollector.finish(this.mark());
        }

        return optional;
    }

    public <T> Optional<T> parse(Atom<T> symbol) {
        ParseState.CacheKey<T> cacheKey = new ParseState.CacheKey<>(symbol, this.mark());
        ParseState.CacheEntry<T> cacheEntry = this.lookupInCache(cacheKey);
        if (cacheEntry != null) {
            this.restore(cacheEntry.mark());
            return cacheEntry.value;
        } else {
            Rule<S, T> rule = this.dictionary.get(symbol);
            if (rule == null) {
                throw new IllegalStateException("No symbol " + symbol);
            } else {
                Optional<T> optional = rule.parse(this);
                this.storeInCache(cacheKey, optional);
                return optional;
            }
        }
    }

    @Nullable
    private <T> ParseState.CacheEntry<T> lookupInCache(ParseState.CacheKey<T> key) {
        return (ParseState.CacheEntry<T>)this.ruleCache.get(key);
    }

    private <T> void storeInCache(ParseState.CacheKey<T> key, Optional<T> value) {
        this.ruleCache.put(key, new ParseState.CacheEntry<>(value, this.mark()));
    }

    public abstract S input();

    public abstract int mark();

    public abstract void restore(int cursor);

    static record CacheEntry<T>(Optional<T> value, int mark) {
    }

    static record CacheKey<T>(Atom<T> name, int mark) {
    }
}
