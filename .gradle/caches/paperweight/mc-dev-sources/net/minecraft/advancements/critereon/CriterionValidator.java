package net.minecraft.advancements.critereon;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class CriterionValidator {
    private final ProblemReporter reporter;
    private final HolderGetter.Provider lootData;

    public CriterionValidator(ProblemReporter errorReporter, HolderGetter.Provider conditionsLookup) {
        this.reporter = errorReporter;
        this.lootData = conditionsLookup;
    }

    public void validateEntity(Optional<ContextAwarePredicate> predicate, String path) {
        predicate.ifPresent(p -> this.validateEntity(p, path));
    }

    public void validateEntities(List<ContextAwarePredicate> predicates, String path) {
        this.validate(predicates, LootContextParamSets.ADVANCEMENT_ENTITY, path);
    }

    public void validateEntity(ContextAwarePredicate predicate, String path) {
        this.validate(predicate, LootContextParamSets.ADVANCEMENT_ENTITY, path);
    }

    public void validate(ContextAwarePredicate predicate, LootContextParamSet type, String path) {
        predicate.validate(new ValidationContext(this.reporter.forChild(path), type, this.lootData));
    }

    public void validate(List<ContextAwarePredicate> predicates, LootContextParamSet type, String path) {
        for (int i = 0; i < predicates.size(); i++) {
            ContextAwarePredicate contextAwarePredicate = predicates.get(i);
            contextAwarePredicate.validate(new ValidationContext(this.reporter.forChild(path + "[" + i + "]"), type, this.lootData));
        }
    }
}
