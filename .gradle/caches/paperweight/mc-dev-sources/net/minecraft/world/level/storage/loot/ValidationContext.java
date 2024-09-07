package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

public class ValidationContext {
    private final ProblemReporter reporter;
    private final LootContextParamSet params;
    private final Optional<HolderGetter.Provider> resolver;
    private final Set<ResourceKey<?>> visitedElements;

    public ValidationContext(ProblemReporter errorReporter, LootContextParamSet contextType, HolderGetter.Provider dataLookup) {
        this(errorReporter, contextType, Optional.of(dataLookup), Set.of());
    }

    public ValidationContext(ProblemReporter errorReporter, LootContextParamSet contextType) {
        this(errorReporter, contextType, Optional.empty(), Set.of());
    }

    private ValidationContext(
        ProblemReporter errorReporter, LootContextParamSet contextType, Optional<HolderGetter.Provider> dataLookup, Set<ResourceKey<?>> referenceStack
    ) {
        this.reporter = errorReporter;
        this.params = contextType;
        this.resolver = dataLookup;
        this.visitedElements = referenceStack;
    }

    public ValidationContext forChild(String name) {
        return new ValidationContext(this.reporter.forChild(name), this.params, this.resolver, this.visitedElements);
    }

    public ValidationContext enterElement(String name, ResourceKey<?> key) {
        Set<ResourceKey<?>> set = ImmutableSet.<ResourceKey<?>>builder().addAll(this.visitedElements).add(key).build();
        return new ValidationContext(this.reporter.forChild(name), this.params, this.resolver, set);
    }

    public boolean hasVisitedElement(ResourceKey<?> key) {
        return this.visitedElements.contains(key);
    }

    public void reportProblem(String message) {
        this.reporter.report(message);
    }

    public void validateUser(LootContextUser contextAware) {
        this.params.validateUser(this, contextAware);
    }

    public HolderGetter.Provider resolver() {
        return this.resolver.orElseThrow(() -> new UnsupportedOperationException("References not allowed"));
    }

    public boolean allowsReferences() {
        return this.resolver.isPresent();
    }

    public ValidationContext setParams(LootContextParamSet contextType) {
        return new ValidationContext(this.reporter, contextType, this.resolver, this.visitedElements);
    }

    public ProblemReporter reporter() {
        return this.reporter;
    }
}
