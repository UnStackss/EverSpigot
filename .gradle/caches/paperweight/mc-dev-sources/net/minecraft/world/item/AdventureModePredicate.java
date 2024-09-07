package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public class AdventureModePredicate {
    private static final Codec<AdventureModePredicate> SIMPLE_CODEC = BlockPredicate.CODEC
        .flatComapMap(predicate -> new AdventureModePredicate(List.of(predicate), true), checker -> DataResult.error(() -> "Cannot encode"));
    private static final Codec<AdventureModePredicate> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ExtraCodecs.nonEmptyList(BlockPredicate.CODEC.listOf()).fieldOf("predicates").forGetter(checker -> checker.predicates),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(AdventureModePredicate::showInTooltip)
                )
                .apply(instance, AdventureModePredicate::new)
    );
    public static final Codec<AdventureModePredicate> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, AdventureModePredicate> STREAM_CODEC = StreamCodec.composite(
        BlockPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()),
        adventureModePredicate -> adventureModePredicate.predicates,
        ByteBufCodecs.BOOL,
        AdventureModePredicate::showInTooltip,
        AdventureModePredicate::new
    );
    public static final Component CAN_BREAK_HEADER = Component.translatable("item.canBreak").withStyle(ChatFormatting.GRAY);
    public static final Component CAN_PLACE_HEADER = Component.translatable("item.canPlace").withStyle(ChatFormatting.GRAY);
    private static final Component UNKNOWN_USE = Component.translatable("item.canUse.unknown").withStyle(ChatFormatting.GRAY);
    public final List<BlockPredicate> predicates;
    private final boolean showInTooltip;
    private final List<Component> tooltip;
    @Nullable
    private BlockInWorld lastCheckedBlock;
    private boolean lastResult;
    private boolean checksBlockEntity;

    private AdventureModePredicate(List<BlockPredicate> predicates, boolean showInTooltip, List<Component> tooltipText) {
        this.predicates = predicates;
        this.showInTooltip = showInTooltip;
        this.tooltip = tooltipText;
    }

    public AdventureModePredicate(List<BlockPredicate> predicates, boolean showInTooltip) {
        this.predicates = predicates;
        this.showInTooltip = showInTooltip;
        this.tooltip = computeTooltip(predicates);
    }

    private static boolean areSameBlocks(BlockInWorld pos, @Nullable BlockInWorld cachedPos, boolean nbtAware) {
        if (cachedPos == null || pos.getState() != cachedPos.getState()) {
            return false;
        } else if (!nbtAware) {
            return true;
        } else if (pos.getEntity() == null && cachedPos.getEntity() == null) {
            return true;
        } else if (pos.getEntity() != null && cachedPos.getEntity() != null) {
            RegistryAccess registryAccess = pos.getLevel().registryAccess();
            return Objects.equals(pos.getEntity().saveWithId(registryAccess), cachedPos.getEntity().saveWithId(registryAccess));
        } else {
            return false;
        }
    }

    public boolean test(BlockInWorld cachedPos) {
        if (areSameBlocks(cachedPos, this.lastCheckedBlock, this.checksBlockEntity)) {
            return this.lastResult;
        } else {
            this.lastCheckedBlock = cachedPos;
            this.checksBlockEntity = false;

            for (BlockPredicate blockPredicate : this.predicates) {
                if (blockPredicate.matches(cachedPos)) {
                    this.checksBlockEntity = this.checksBlockEntity | blockPredicate.requiresNbt();
                    this.lastResult = true;
                    return true;
                }
            }

            this.lastResult = false;
            return false;
        }
    }

    public void addToTooltip(Consumer<Component> adder) {
        this.tooltip.forEach(adder);
    }

    public AdventureModePredicate withTooltip(boolean showInTooltip) {
        return new AdventureModePredicate(this.predicates, showInTooltip, this.tooltip);
    }

    private static List<Component> computeTooltip(List<BlockPredicate> blockPredicates) {
        for (BlockPredicate blockPredicate : blockPredicates) {
            if (blockPredicate.blocks().isEmpty()) {
                return List.of(UNKNOWN_USE);
            }
        }

        return blockPredicates.stream()
            .flatMap(predicate -> predicate.blocks().orElseThrow().stream())
            .distinct()
            .map(holder -> holder.value().getName().withStyle(ChatFormatting.DARK_GRAY))
            .toList();
    }

    public boolean showInTooltip() {
        return this.showInTooltip;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof AdventureModePredicate adventureModePredicate
                && this.predicates.equals(adventureModePredicate.predicates)
                && this.showInTooltip == adventureModePredicate.showInTooltip;
    }

    @Override
    public int hashCode() {
        return this.predicates.hashCode() * 31 + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public String toString() {
        return "AdventureModePredicate{predicates=" + this.predicates + ", showInTooltip=" + this.showInTooltip + "}";
    }
}
