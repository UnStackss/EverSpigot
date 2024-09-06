package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

public class ItemEnchantments implements TooltipProvider {
    public static final ItemEnchantments EMPTY = new ItemEnchantments(new Object2IntOpenHashMap<>(), true);
    private static final Codec<Integer> LEVEL_CODEC = Codec.intRange(0, 255);
    private static final Codec<Object2IntOpenHashMap<Holder<Enchantment>>> LEVELS_CODEC = Codec.unboundedMap(Enchantment.CODEC, LEVEL_CODEC)
        .xmap(Object2IntOpenHashMap::new, Function.identity());
    private static final Codec<ItemEnchantments> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    LEVELS_CODEC.fieldOf("levels").forGetter(component -> component.enchantments),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(component -> component.showInTooltip)
                )
                .apply(instance, ItemEnchantments::new)
    );
    public static final Codec<ItemEnchantments> CODEC = Codec.withAlternative(FULL_CODEC, LEVELS_CODEC, map -> new ItemEnchantments(map, true));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemEnchantments> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(Object2IntOpenHashMap::new, Enchantment.STREAM_CODEC, ByteBufCodecs.VAR_INT),
        component -> component.enchantments,
        ByteBufCodecs.BOOL,
        component -> component.showInTooltip,
        ItemEnchantments::new
    );
    final Object2IntOpenHashMap<Holder<Enchantment>> enchantments;
    public final boolean showInTooltip;

    ItemEnchantments(Object2IntOpenHashMap<Holder<Enchantment>> enchantments, boolean showInTooltip) {
        this.enchantments = enchantments;
        this.showInTooltip = showInTooltip;

        for (Entry<Holder<Enchantment>> entry : enchantments.object2IntEntrySet()) {
            int i = entry.getIntValue();
            if (i < 0 || i > 255) {
                throw new IllegalArgumentException("Enchantment " + entry.getKey() + " has invalid level " + i);
            }
        }
    }

    public int getLevel(Holder<Enchantment> enchantment) {
        return this.enchantments.getInt(enchantment);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag type) {
        if (this.showInTooltip) {
            HolderLookup.Provider provider = context.registries();
            HolderSet<Enchantment> holderSet = getTagOrEmpty(provider, Registries.ENCHANTMENT, EnchantmentTags.TOOLTIP_ORDER);

            for (Holder<Enchantment> holder : holderSet) {
                int i = this.enchantments.getInt(holder);
                if (i > 0) {
                    tooltip.accept(Enchantment.getFullname(holder, i));
                }
            }

            for (Entry<Holder<Enchantment>> entry : this.enchantments.object2IntEntrySet()) {
                Holder<Enchantment> holder2 = entry.getKey();
                if (!holderSet.contains(holder2)) {
                    tooltip.accept(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
                }
            }
        }
    }

    private static <T> HolderSet<T> getTagOrEmpty(
        @Nullable HolderLookup.Provider registryLookup, ResourceKey<Registry<T>> registryRef, TagKey<T> tooltipOrderTag
    ) {
        if (registryLookup != null) {
            Optional<HolderSet.Named<T>> optional = registryLookup.lookupOrThrow(registryRef).get(tooltipOrderTag);
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        return HolderSet.direct();
    }

    public ItemEnchantments withTooltip(boolean showInTooltip) {
        return new ItemEnchantments(this.enchantments, showInTooltip);
    }

    public Set<Holder<Enchantment>> keySet() {
        return Collections.unmodifiableSet(this.enchantments.keySet());
    }

    public Set<Entry<Holder<Enchantment>>> entrySet() {
        return Collections.unmodifiableSet(this.enchantments.object2IntEntrySet());
    }

    public int size() {
        return this.enchantments.size();
    }

    public boolean isEmpty() {
        return this.enchantments.isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof ItemEnchantments itemEnchantments
                && this.showInTooltip == itemEnchantments.showInTooltip
                && this.enchantments.equals(itemEnchantments.enchantments);
    }

    @Override
    public int hashCode() {
        int i = this.enchantments.hashCode();
        return 31 * i + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public String toString() {
        return "ItemEnchantments{enchantments=" + this.enchantments + ", showInTooltip=" + this.showInTooltip + "}";
    }

    public static class Mutable {
        private final Object2IntOpenHashMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        public boolean showInTooltip;

        public Mutable(ItemEnchantments enchantmentsComponent) {
            this.enchantments.putAll(enchantmentsComponent.enchantments);
            this.showInTooltip = enchantmentsComponent.showInTooltip;
        }

        public void set(Holder<Enchantment> enchantment, int level) {
            if (level <= 0) {
                this.enchantments.removeInt(enchantment);
            } else {
                this.enchantments.put(enchantment, Math.min(level, 255));
            }
        }

        public void upgrade(Holder<Enchantment> enchantment, int level) {
            if (level > 0) {
                this.enchantments.merge(enchantment, Math.min(level, 255), Integer::max);
            }
        }

        public void removeIf(Predicate<Holder<Enchantment>> predicate) {
            this.enchantments.keySet().removeIf(predicate);
        }

        public int getLevel(Holder<Enchantment> enchantment) {
            return this.enchantments.getOrDefault(enchantment, 0);
        }

        public Set<Holder<Enchantment>> keySet() {
            return this.enchantments.keySet();
        }

        public ItemEnchantments toImmutable() {
            return new ItemEnchantments(this.enchantments, this.showInTooltip);
        }
    }
}
