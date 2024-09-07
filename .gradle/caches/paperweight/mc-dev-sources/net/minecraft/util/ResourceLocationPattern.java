package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public class ResourceLocationPattern {
    public static final Codec<ResourceLocationPattern> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ExtraCodecs.PATTERN.optionalFieldOf("namespace").forGetter(entry -> entry.namespacePattern),
                    ExtraCodecs.PATTERN.optionalFieldOf("path").forGetter(entry -> entry.pathPattern)
                )
                .apply(instance, ResourceLocationPattern::new)
    );
    private final Optional<Pattern> namespacePattern;
    private final Predicate<String> namespacePredicate;
    private final Optional<Pattern> pathPattern;
    private final Predicate<String> pathPredicate;
    private final Predicate<ResourceLocation> locationPredicate;

    private ResourceLocationPattern(Optional<Pattern> namespace, Optional<Pattern> path) {
        this.namespacePattern = namespace;
        this.namespacePredicate = namespace.map(Pattern::asPredicate).orElse(namespace_ -> true);
        this.pathPattern = path;
        this.pathPredicate = path.map(Pattern::asPredicate).orElse(path_ -> true);
        this.locationPredicate = id -> this.namespacePredicate.test(id.getNamespace()) && this.pathPredicate.test(id.getPath());
    }

    public Predicate<String> namespacePredicate() {
        return this.namespacePredicate;
    }

    public Predicate<String> pathPredicate() {
        return this.pathPredicate;
    }

    public Predicate<ResourceLocation> locationPredicate() {
        return this.locationPredicate;
    }
}
