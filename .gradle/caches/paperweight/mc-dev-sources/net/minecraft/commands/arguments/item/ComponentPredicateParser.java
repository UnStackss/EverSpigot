package net.minecraft.commands.arguments.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.ResourceLocationParseRule;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.minecraft.util.parsing.packrat.commands.TagParseRule;

public class ComponentPredicateParser {
    public static <T, C, P> Grammar<List<T>> createGrammar(ComponentPredicateParser.Context<T, C, P> callbacks) {
        Atom<List<T>> atom = Atom.of("top");
        Atom<Optional<T>> atom2 = Atom.of("type");
        Atom<Unit> atom3 = Atom.of("any_type");
        Atom<T> atom4 = Atom.of("element_type");
        Atom<T> atom5 = Atom.of("tag_type");
        Atom<List<T>> atom6 = Atom.of("conditions");
        Atom<List<T>> atom7 = Atom.of("alternatives");
        Atom<T> atom8 = Atom.of("term");
        Atom<T> atom9 = Atom.of("negation");
        Atom<T> atom10 = Atom.of("test");
        Atom<C> atom11 = Atom.of("component_type");
        Atom<P> atom12 = Atom.of("predicate_type");
        Atom<ResourceLocation> atom13 = Atom.of("id");
        Atom<Tag> atom14 = Atom.of("tag");
        Dictionary<StringReader> dictionary = new Dictionary<>();
        dictionary.put(
            atom,
            Term.alternative(
                Term.sequence(
                    Term.named(atom2), StringReaderTerms.character('['), Term.cut(), Term.optional(Term.named(atom6)), StringReaderTerms.character(']')
                ),
                Term.named(atom2)
            ),
            results -> {
                Builder<T> builder = ImmutableList.builder();
                results.getOrThrow(atom2).ifPresent(builder::add);
                List<T> list = results.get(atom6);
                if (list != null) {
                    builder.addAll(list);
                }

                return builder.build();
            }
        );
        dictionary.put(
            atom2,
            Term.alternative(Term.named(atom4), Term.sequence(StringReaderTerms.character('#'), Term.cut(), Term.named(atom5)), Term.named(atom3)),
            results -> Optional.ofNullable(results.getAny(atom4, atom5))
        );
        dictionary.put(atom3, StringReaderTerms.character('*'), results -> Unit.INSTANCE);
        dictionary.put(atom4, new ComponentPredicateParser.ElementLookupRule<>(atom13, callbacks));
        dictionary.put(atom5, new ComponentPredicateParser.TagLookupRule<>(atom13, callbacks));
        dictionary.put(atom6, Term.sequence(Term.named(atom7), Term.optional(Term.sequence(StringReaderTerms.character(','), Term.named(atom6)))), results -> {
            T object = callbacks.anyOf(results.getOrThrow(atom7));
            return Optional.ofNullable(results.get(atom6)).map(predicates -> Util.copyAndAdd(object, (List<T>)predicates)).orElse(List.of(object));
        });
        dictionary.put(atom7, Term.sequence(Term.named(atom8), Term.optional(Term.sequence(StringReaderTerms.character('|'), Term.named(atom7)))), results -> {
            T object = results.getOrThrow(atom8);
            return Optional.ofNullable(results.get(atom7)).map(predicates -> Util.copyAndAdd(object, (List<T>)predicates)).orElse(List.of(object));
        });
        dictionary.put(
            atom8,
            Term.alternative(Term.named(atom10), Term.sequence(StringReaderTerms.character('!'), Term.named(atom9))),
            results -> results.getAnyOrThrow(atom10, atom9)
        );
        dictionary.put(atom9, Term.named(atom10), results -> callbacks.negate(results.getOrThrow(atom10)));
        dictionary.put(
            atom10,
            Term.alternative(
                Term.sequence(Term.named(atom11), StringReaderTerms.character('='), Term.cut(), Term.named(atom14)),
                Term.sequence(Term.named(atom12), StringReaderTerms.character('~'), Term.cut(), Term.named(atom14)),
                Term.named(atom11)
            ),
            (state, results) -> {
                P object = results.get(atom12);

                try {
                    if (object != null) {
                        Tag tag = results.getOrThrow(atom14);
                        return Optional.of(callbacks.createPredicateTest(state.input(), object, tag));
                    } else {
                        C object2 = results.getOrThrow(atom11);
                        Tag tag2 = results.get(atom14);
                        return Optional.of(
                            tag2 != null ? callbacks.createComponentTest(state.input(), object2, tag2) : callbacks.createComponentTest(state.input(), object2)
                        );
                    }
                } catch (CommandSyntaxException var9x) {
                    state.errorCollector().store(state.mark(), var9x);
                    return Optional.empty();
                }
            }
        );
        dictionary.put(atom11, new ComponentPredicateParser.ComponentLookupRule<>(atom13, callbacks));
        dictionary.put(atom12, new ComponentPredicateParser.PredicateLookupRule<>(atom13, callbacks));
        dictionary.put(atom14, TagParseRule.INSTANCE);
        dictionary.put(atom13, ResourceLocationParseRule.INSTANCE);
        return new Grammar<>(dictionary, atom);
    }

    static class ComponentLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, C> {
        ComponentLookupRule(Atom<ResourceLocation> symbol, ComponentPredicateParser.Context<T, C, P> callbacks) {
            super(symbol, callbacks);
        }

        @Override
        protected C validateElement(ImmutableStringReader reader, ResourceLocation id) throws Exception {
            return this.context.lookupComponentType(reader, id);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listComponentTypes();
        }
    }

    public interface Context<T, C, P> {
        T forElementType(ImmutableStringReader reader, ResourceLocation id) throws CommandSyntaxException;

        Stream<ResourceLocation> listElementTypes();

        T forTagType(ImmutableStringReader reader, ResourceLocation id) throws CommandSyntaxException;

        Stream<ResourceLocation> listTagTypes();

        C lookupComponentType(ImmutableStringReader reader, ResourceLocation id) throws CommandSyntaxException;

        Stream<ResourceLocation> listComponentTypes();

        T createComponentTest(ImmutableStringReader reader, C check, Tag nbt) throws CommandSyntaxException;

        T createComponentTest(ImmutableStringReader reader, C check);

        P lookupPredicateType(ImmutableStringReader reader, ResourceLocation id) throws CommandSyntaxException;

        Stream<ResourceLocation> listPredicateTypes();

        T createPredicateTest(ImmutableStringReader reader, P check, Tag nbt) throws CommandSyntaxException;

        T negate(T predicate);

        T anyOf(List<T> predicates);
    }

    static class ElementLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {
        ElementLookupRule(Atom<ResourceLocation> symbol, ComponentPredicateParser.Context<T, C, P> callbacks) {
            super(symbol, callbacks);
        }

        @Override
        protected T validateElement(ImmutableStringReader reader, ResourceLocation id) throws Exception {
            return this.context.forElementType(reader, id);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listElementTypes();
        }
    }

    static class PredicateLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, P> {
        PredicateLookupRule(Atom<ResourceLocation> symbol, ComponentPredicateParser.Context<T, C, P> callbacks) {
            super(symbol, callbacks);
        }

        @Override
        protected P validateElement(ImmutableStringReader reader, ResourceLocation id) throws Exception {
            return this.context.lookupPredicateType(reader, id);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listPredicateTypes();
        }
    }

    static class TagLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {
        TagLookupRule(Atom<ResourceLocation> symbol, ComponentPredicateParser.Context<T, C, P> callbacks) {
            super(symbol, callbacks);
        }

        @Override
        protected T validateElement(ImmutableStringReader reader, ResourceLocation id) throws Exception {
            return this.context.forTagType(reader, id);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listTagTypes();
        }
    }
}
