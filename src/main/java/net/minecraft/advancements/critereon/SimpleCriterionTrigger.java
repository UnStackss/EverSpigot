package net.minecraft.advancements.critereon;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;

public abstract class SimpleCriterionTrigger<T extends SimpleCriterionTrigger.SimpleInstance> implements CriterionTrigger<T> {
    private final Map<PlayerAdvancements, Set<CriterionTrigger.Listener<T>>> players = Maps.newIdentityHashMap();

    @Override
    public final void addPlayerListener(PlayerAdvancements manager, CriterionTrigger.Listener<T> conditions) {
        this.players.computeIfAbsent(manager, managerx -> Sets.newHashSet()).add(conditions);
    }

    @Override
    public final void removePlayerListener(PlayerAdvancements manager, CriterionTrigger.Listener<T> conditions) {
        Set<CriterionTrigger.Listener<T>> set = this.players.get(manager);
        if (set != null) {
            set.remove(conditions);
            if (set.isEmpty()) {
                this.players.remove(manager);
            }
        }
    }

    @Override
    public final void removePlayerListeners(PlayerAdvancements tracker) {
        this.players.remove(tracker);
    }

    protected void trigger(ServerPlayer player, Predicate<T> predicate) {
        PlayerAdvancements playerAdvancements = player.getAdvancements();
        Set<CriterionTrigger.Listener<T>> set = this.players.get(playerAdvancements);
        if (set != null && !set.isEmpty()) {
            LootContext lootContext = EntityPredicate.createContext(player, player);
            List<CriterionTrigger.Listener<T>> list = null;

            for (CriterionTrigger.Listener<T> listener : set) {
                T simpleInstance = listener.trigger();
                if (predicate.test(simpleInstance)) {
                    Optional<ContextAwarePredicate> optional = simpleInstance.player();
                    if (optional.isEmpty() || optional.get().matches(lootContext)) {
                        if (list == null) {
                            list = Lists.newArrayList();
                        }

                        list.add(listener);
                    }
                }
            }

            if (list != null) {
                for (CriterionTrigger.Listener<T> listener2 : list) {
                    listener2.run(playerAdvancements);
                }
            }
        }
    }

    public interface SimpleInstance extends CriterionTriggerInstance {
        @Override
        default void validate(CriterionValidator validator) {
            validator.validateEntity(this.player(), ".player");
        }

        Optional<ContextAwarePredicate> player();
    }
}
