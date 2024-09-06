package net.minecraft.advancements;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
// CraftBukkit start
import org.bukkit.craftbukkit.advancement.CraftAdvancement;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
// CraftBukkit end

public record AdvancementHolder(ResourceLocation id, Advancement value) {

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementHolder> STREAM_CODEC = StreamCodec.composite(ResourceLocation.STREAM_CODEC, AdvancementHolder::id, Advancement.STREAM_CODEC, AdvancementHolder::value, AdvancementHolder::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, List<AdvancementHolder>> LIST_STREAM_CODEC = AdvancementHolder.STREAM_CODEC.apply(ByteBufCodecs.list());

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            boolean flag;

            if (object instanceof AdvancementHolder) {
                AdvancementHolder advancementholder = (AdvancementHolder) object;

                if (this.id.equals(advancementholder.id)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.id.hashCode();
    }

    public String toString() {
        return this.id.toString();
    }

    // CraftBukkit start
    public final org.bukkit.advancement.Advancement toBukkit() {
        return new CraftAdvancement(this);
    }
    // CraftBukkit end
}
