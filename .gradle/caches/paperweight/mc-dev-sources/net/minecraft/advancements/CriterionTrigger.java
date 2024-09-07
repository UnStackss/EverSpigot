package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import net.minecraft.server.PlayerAdvancements;

public interface CriterionTrigger<T extends CriterionTriggerInstance> {
    void addPlayerListener(PlayerAdvancements manager, CriterionTrigger.Listener<T> conditions);

    void removePlayerListener(PlayerAdvancements manager, CriterionTrigger.Listener<T> conditions);

    void removePlayerListeners(PlayerAdvancements tracker);

    Codec<T> codec();

    default Criterion<T> createCriterion(T conditions) {
        return new Criterion<>(this, conditions);
    }

    public static record Listener<T extends CriterionTriggerInstance>(T trigger, AdvancementHolder advancement, String criterion) {
        public void run(PlayerAdvancements tracker) {
            tracker.award(this.advancement, this.criterion);
        }
    }
}
