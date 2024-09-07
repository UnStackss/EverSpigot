package net.minecraft.advancements;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import javax.annotation.Nullable;

public class AdvancementNode {
    private final AdvancementHolder holder;
    @Nullable
    private final AdvancementNode parent;
    private final Set<AdvancementNode> children = new ReferenceOpenHashSet<>();

    @VisibleForTesting
    public AdvancementNode(AdvancementHolder advancementEntry, @Nullable AdvancementNode parent) {
        this.holder = advancementEntry;
        this.parent = parent;
    }

    public Advancement advancement() {
        return this.holder.value();
    }

    public AdvancementHolder holder() {
        return this.holder;
    }

    @Nullable
    public AdvancementNode parent() {
        return this.parent;
    }

    public AdvancementNode root() {
        return getRoot(this);
    }

    public static AdvancementNode getRoot(AdvancementNode advancement) {
        AdvancementNode advancementNode = advancement;

        while (true) {
            AdvancementNode advancementNode2 = advancementNode.parent();
            if (advancementNode2 == null) {
                return advancementNode;
            }

            advancementNode = advancementNode2;
        }
    }

    public Iterable<AdvancementNode> children() {
        return this.children;
    }

    @VisibleForTesting
    public void addChild(AdvancementNode advancement) {
        this.children.add(advancement);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof AdvancementNode advancementNode && this.holder.equals(advancementNode.holder)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.holder.hashCode();
    }

    @Override
    public String toString() {
        return this.holder.id().toString();
    }
}
