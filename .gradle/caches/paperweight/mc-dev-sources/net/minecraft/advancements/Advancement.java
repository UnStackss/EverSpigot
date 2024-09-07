package net.minecraft.advancements;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.CriterionValidator;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record Advancement(
    Optional<ResourceLocation> parent,
    Optional<DisplayInfo> display,
    AdvancementRewards rewards,
    Map<String, Criterion<?>> criteria,
    AdvancementRequirements requirements,
    boolean sendsTelemetryEvent,
    Optional<Component> name
) {
    private static final Codec<Map<String, Criterion<?>>> CRITERIA_CODEC = Codec.unboundedMap(Codec.STRING, Criterion.CODEC)
        .validate(criteria -> criteria.isEmpty() ? DataResult.error(() -> "Advancement criteria cannot be empty") : DataResult.success(criteria));
    public static final Codec<Advancement> CODEC = RecordCodecBuilder.<Advancement>create(
            instance -> instance.group(
                        ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(Advancement::parent),
                        DisplayInfo.CODEC.optionalFieldOf("display").forGetter(Advancement::display),
                        AdvancementRewards.CODEC.optionalFieldOf("rewards", AdvancementRewards.EMPTY).forGetter(Advancement::rewards),
                        CRITERIA_CODEC.fieldOf("criteria").forGetter(Advancement::criteria),
                        AdvancementRequirements.CODEC.optionalFieldOf("requirements").forGetter(advancement -> Optional.of(advancement.requirements())),
                        Codec.BOOL.optionalFieldOf("sends_telemetry_event", Boolean.valueOf(false)).forGetter(Advancement::sendsTelemetryEvent)
                    )
                    .apply(instance, (parent, display, rewards, criteria, requirements, sendsTelemetryEvent) -> {
                        AdvancementRequirements advancementRequirements = requirements.orElseGet(() -> AdvancementRequirements.allOf(criteria.keySet()));
                        return new Advancement(parent, display, rewards, criteria, advancementRequirements, sendsTelemetryEvent);
                    })
        )
        .validate(Advancement::validate);
    public static final StreamCodec<RegistryFriendlyByteBuf, Advancement> STREAM_CODEC = StreamCodec.ofMember(Advancement::write, Advancement::read);

    public Advancement(
        Optional<ResourceLocation> parent,
        Optional<DisplayInfo> display,
        AdvancementRewards rewards,
        Map<String, Criterion<?>> criteria,
        AdvancementRequirements requirements,
        boolean sendsTelemetryEvent
    ) {
        this(parent, display, rewards, Map.copyOf(criteria), requirements, sendsTelemetryEvent, display.map(Advancement::decorateName));
    }

    private static DataResult<Advancement> validate(Advancement advancement) {
        return advancement.requirements().validate(advancement.criteria().keySet()).map(validated -> advancement);
    }

    public static Component decorateName(DisplayInfo display) {
        Component component = display.getTitle();
        ChatFormatting chatFormatting = display.getType().getChatColor();
        Component component2 = ComponentUtils.mergeStyles(component.copy(), Style.EMPTY.withColor(chatFormatting))
            .append("\n")
            .append(display.getDescription());
        Component component3 = component.copy().withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, component2)));
        return ComponentUtils.wrapInSquareBrackets(component3).withStyle(chatFormatting);
    }

    public static Component name(AdvancementHolder identifiedAdvancement) {
        return identifiedAdvancement.value().name().orElseGet(() -> Component.literal(identifiedAdvancement.id().toString()));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeOptional(this.parent, FriendlyByteBuf::writeResourceLocation);
        DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, this.display);
        this.requirements.write(buf);
        buf.writeBoolean(this.sendsTelemetryEvent);
    }

    private static Advancement read(RegistryFriendlyByteBuf buf) {
        return new Advancement(
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            (Optional<DisplayInfo>)DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buf),
            AdvancementRewards.EMPTY,
            Map.of(),
            new AdvancementRequirements(buf),
            buf.readBoolean()
        );
    }

    public boolean isRoot() {
        return this.parent.isEmpty();
    }

    public void validate(ProblemReporter errorReporter, HolderGetter.Provider lookup) {
        this.criteria.forEach((name, criterion) -> {
            CriterionValidator criterionValidator = new CriterionValidator(errorReporter.forChild(name), lookup);
            criterion.triggerInstance().validate(criterionValidator);
        });
    }

    public static class Builder {
        private Optional<ResourceLocation> parent = Optional.empty();
        private Optional<DisplayInfo> display = Optional.empty();
        private AdvancementRewards rewards = AdvancementRewards.EMPTY;
        private final ImmutableMap.Builder<String, Criterion<?>> criteria = ImmutableMap.builder();
        private Optional<AdvancementRequirements> requirements = Optional.empty();
        private AdvancementRequirements.Strategy requirementsStrategy = AdvancementRequirements.Strategy.AND;
        private boolean sendsTelemetryEvent;

        public static Advancement.Builder advancement() {
            return new Advancement.Builder().sendsTelemetryEvent();
        }

        public static Advancement.Builder recipeAdvancement() {
            return new Advancement.Builder();
        }

        public Advancement.Builder parent(AdvancementHolder parent) {
            this.parent = Optional.of(parent.id());
            return this;
        }

        @Deprecated(
            forRemoval = true
        )
        public Advancement.Builder parent(ResourceLocation parentId) {
            this.parent = Optional.of(parentId);
            return this;
        }

        public Advancement.Builder display(
            ItemStack icon,
            Component title,
            Component description,
            @Nullable ResourceLocation background,
            AdvancementType frame,
            boolean showToast,
            boolean announceToChat,
            boolean hidden
        ) {
            return this.display(new DisplayInfo(icon, title, description, Optional.ofNullable(background), frame, showToast, announceToChat, hidden));
        }

        public Advancement.Builder display(
            ItemLike icon,
            Component title,
            Component description,
            @Nullable ResourceLocation background,
            AdvancementType frame,
            boolean showToast,
            boolean announceToChat,
            boolean hidden
        ) {
            return this.display(
                new DisplayInfo(new ItemStack(icon.asItem()), title, description, Optional.ofNullable(background), frame, showToast, announceToChat, hidden)
            );
        }

        public Advancement.Builder display(DisplayInfo display) {
            this.display = Optional.of(display);
            return this;
        }

        public Advancement.Builder rewards(AdvancementRewards.Builder builder) {
            return this.rewards(builder.build());
        }

        public Advancement.Builder rewards(AdvancementRewards rewards) {
            this.rewards = rewards;
            return this;
        }

        public Advancement.Builder addCriterion(String name, Criterion<?> criterion) {
            this.criteria.put(name, criterion);
            return this;
        }

        public Advancement.Builder requirements(AdvancementRequirements.Strategy merger) {
            this.requirementsStrategy = merger;
            return this;
        }

        public Advancement.Builder requirements(AdvancementRequirements requirements) {
            this.requirements = Optional.of(requirements);
            return this;
        }

        public Advancement.Builder sendsTelemetryEvent() {
            this.sendsTelemetryEvent = true;
            return this;
        }

        public AdvancementHolder build(ResourceLocation id) {
            Map<String, Criterion<?>> map = this.criteria.buildOrThrow();
            AdvancementRequirements advancementRequirements = this.requirements.orElseGet(() -> this.requirementsStrategy.create(map.keySet()));
            return new AdvancementHolder(id, new Advancement(this.parent, this.display, this.rewards, map, advancementRequirements, this.sendsTelemetryEvent));
        }

        public AdvancementHolder save(Consumer<AdvancementHolder> exporter, String id) {
            AdvancementHolder advancementHolder = this.build(ResourceLocation.parse(id));
            exporter.accept(advancementHolder);
            return advancementHolder;
        }
    }
}
