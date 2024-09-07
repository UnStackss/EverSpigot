package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

public record EntityHasScoreCondition(Map<String, IntRange> scores, LootContext.EntityTarget entityTarget) implements LootItemCondition {
    public static final MapCodec<EntityHasScoreCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, IntRange.CODEC).fieldOf("scores").forGetter(EntityHasScoreCondition::scores),
                    LootContext.EntityTarget.CODEC.fieldOf("entity").forGetter(EntityHasScoreCondition::entityTarget)
                )
                .apply(instance, EntityHasScoreCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.ENTITY_SCORES;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Stream.concat(
                Stream.of(this.entityTarget.getParam()), this.scores.values().stream().flatMap(operator -> operator.getReferencedContextParams().stream())
            )
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public boolean test(LootContext lootContext) {
        Entity entity = lootContext.getParamOrNull(this.entityTarget.getParam());
        if (entity == null) {
            return false;
        } else {
            Scoreboard scoreboard = lootContext.getLevel().getScoreboard();

            for (Entry<String, IntRange> entry : this.scores.entrySet()) {
                if (!this.hasScore(lootContext, entity, scoreboard, entry.getKey(), entry.getValue())) {
                    return false;
                }
            }

            return true;
        }
    }

    protected boolean hasScore(LootContext context, Entity entity, Scoreboard scoreboard, String objectiveName, IntRange range) {
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            return false;
        } else {
            ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(entity, objective);
            return readOnlyScoreInfo != null && range.test(context, readOnlyScoreInfo.value());
        }
    }

    public static EntityHasScoreCondition.Builder hasScores(LootContext.EntityTarget target) {
        return new EntityHasScoreCondition.Builder(target);
    }

    public static class Builder implements LootItemCondition.Builder {
        private final ImmutableMap.Builder<String, IntRange> scores = ImmutableMap.builder();
        private final LootContext.EntityTarget entityTarget;

        public Builder(LootContext.EntityTarget target) {
            this.entityTarget = target;
        }

        public EntityHasScoreCondition.Builder withScore(String name, IntRange value) {
            this.scores.put(name, value);
            return this;
        }

        @Override
        public LootItemCondition build() {
            return new EntityHasScoreCondition(this.scores.build(), this.entityTarget);
        }
    }
}
