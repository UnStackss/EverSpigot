package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HoverEvent {
    public static final Codec<HoverEvent> CODEC = Codec.withAlternative(
            HoverEvent.TypedHoverEvent.CODEC.codec(), HoverEvent.TypedHoverEvent.LEGACY_CODEC.codec()
        )
        .xmap(HoverEvent::new, event -> event.event);
    private final HoverEvent.TypedHoverEvent<?> event;

    public <T> HoverEvent(HoverEvent.Action<T> action, T contents) {
        this(new HoverEvent.TypedHoverEvent<>(action, contents));
    }

    private HoverEvent(HoverEvent.TypedHoverEvent<?> data) {
        this.event = data;
    }

    public HoverEvent.Action<?> getAction() {
        return this.event.action;
    }

    @Nullable
    public <T> T getValue(HoverEvent.Action<T> action) {
        return this.event.action == action ? action.cast(this.event.value) : null;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object != null && this.getClass() == object.getClass() && ((HoverEvent)object).event.equals(this.event);
    }

    @Override
    public String toString() {
        return this.event.toString();
    }

    @Override
    public int hashCode() {
        return this.event.hashCode();
    }

    public static class Action<T> implements StringRepresentable {
        public static final HoverEvent.Action<Component> SHOW_TEXT = new HoverEvent.Action<>(
            "show_text", true, ComponentSerialization.CODEC, (text, ops) -> DataResult.success(text)
        );
        public static final HoverEvent.Action<HoverEvent.ItemStackInfo> SHOW_ITEM = new HoverEvent.Action<>(
            "show_item", true, HoverEvent.ItemStackInfo.CODEC, HoverEvent.ItemStackInfo::legacyCreate
        );
        public static final HoverEvent.Action<HoverEvent.EntityTooltipInfo> SHOW_ENTITY = new HoverEvent.Action<>(
            "show_entity", true, HoverEvent.EntityTooltipInfo.CODEC, HoverEvent.EntityTooltipInfo::legacyCreate
        );
        public static final Codec<HoverEvent.Action<?>> UNSAFE_CODEC = StringRepresentable.fromValues(
            () -> new HoverEvent.Action[]{SHOW_TEXT, SHOW_ITEM, SHOW_ENTITY}
        );
        public static final Codec<HoverEvent.Action<?>> CODEC = UNSAFE_CODEC.validate(HoverEvent.Action::filterForSerialization);
        private final String name;
        private final boolean allowFromServer;
        final MapCodec<HoverEvent.TypedHoverEvent<T>> codec;
        final MapCodec<HoverEvent.TypedHoverEvent<T>> legacyCodec;

        public Action(String name, boolean parsable, Codec<T> contentCodec, HoverEvent.LegacyConverter<T> legacySerializer) {
            this.name = name;
            this.allowFromServer = parsable;
            this.codec = contentCodec.xmap(value -> new HoverEvent.TypedHoverEvent<>(this, (T)value), action -> action.value).fieldOf("contents");
            this.legacyCodec = (new Codec<HoverEvent.TypedHoverEvent<T>>() {
                public <D> DataResult<Pair<HoverEvent.TypedHoverEvent<T>, D>> decode(DynamicOps<D> dynamicOps, D object) {
                    return ComponentSerialization.CODEC.decode(dynamicOps, object).flatMap(pair -> {
                        DataResult<T> dataResult;
                        if (dynamicOps instanceof RegistryOps<D> registryOps) {
                            dataResult = legacySerializer.parse(pair.getFirst(), registryOps);
                        } else {
                            dataResult = legacySerializer.parse(pair.getFirst(), null);
                        }

                        return dataResult.map(value -> Pair.of(new HoverEvent.TypedHoverEvent<>(Action.this, (T)value), (D)pair.getSecond()));
                    });
                }

                public <D> DataResult<D> encode(HoverEvent.TypedHoverEvent<T> typedHoverEvent, DynamicOps<D> dynamicOps, D object) {
                    return DataResult.error(() -> "Can't encode in legacy format");
                }
            }).fieldOf("value");
        }

        public boolean isAllowedFromServer() {
            return this.allowFromServer;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        T cast(Object o) {
            return (T)o;
        }

        @Override
        public String toString() {
            return "<action " + this.name + ">";
        }

        private static DataResult<HoverEvent.Action<?>> filterForSerialization(@Nullable HoverEvent.Action<?> action) {
            if (action == null) {
                return DataResult.error(() -> "Unknown action");
            } else {
                return !action.isAllowedFromServer() ? DataResult.error(() -> "Action not allowed: " + action) : DataResult.success(action, Lifecycle.stable());
            }
        }
    }

    public static class EntityTooltipInfo {
        public static final Codec<HoverEvent.EntityTooltipInfo> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(content -> content.type),
                        UUIDUtil.LENIENT_CODEC.fieldOf("id").forGetter(content -> content.id),
                        ComponentSerialization.CODEC.lenientOptionalFieldOf("name").forGetter(content -> content.name)
                    )
                    .apply(instance, HoverEvent.EntityTooltipInfo::new)
        );
        public final EntityType<?> type;
        public final UUID id;
        public final Optional<Component> name;
        @Nullable
        private List<Component> linesCache;

        public EntityTooltipInfo(EntityType<?> entityType, UUID uuid, @Nullable Component name) {
            this(entityType, uuid, Optional.ofNullable(name));
        }

        public EntityTooltipInfo(EntityType<?> entityType, UUID uuid, Optional<Component> name) {
            this.type = entityType;
            this.id = uuid;
            this.name = name;
        }

        public static DataResult<HoverEvent.EntityTooltipInfo> legacyCreate(Component text, @Nullable RegistryOps<?> ops) {
            try {
                CompoundTag compoundTag = TagParser.parseTag(text.getString());
                DynamicOps<JsonElement> dynamicOps = (DynamicOps<JsonElement>)(ops != null ? ops.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
                DataResult<Component> dataResult = ComponentSerialization.CODEC.parse(dynamicOps, JsonParser.parseString(compoundTag.getString("name")));
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(compoundTag.getString("type")));
                UUID uUID = UUID.fromString(compoundTag.getString("id"));
                return dataResult.map(textx -> new HoverEvent.EntityTooltipInfo(entityType, uUID, textx));
            } catch (Exception var7) {
                return DataResult.error(() -> "Failed to parse tooltip: " + var7.getMessage());
            }
        }

        public List<Component> getTooltipLines() {
            if (this.linesCache == null) {
                this.linesCache = new ArrayList<>();
                this.name.ifPresent(this.linesCache::add);
                this.linesCache.add(Component.translatable("gui.entity_tooltip.type", this.type.getDescription()));
                this.linesCache.add(Component.literal(this.id.toString()));
            }

            return this.linesCache;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                HoverEvent.EntityTooltipInfo entityTooltipInfo = (HoverEvent.EntityTooltipInfo)object;
                return this.type.equals(entityTooltipInfo.type) && this.id.equals(entityTooltipInfo.id) && this.name.equals(entityTooltipInfo.name);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int i = this.type.hashCode();
            i = 31 * i + this.id.hashCode();
            return 31 * i + this.name.hashCode();
        }
    }

    public static class ItemStackInfo {
        public static final Codec<HoverEvent.ItemStackInfo> FULL_CODEC = ItemStack.CODEC
            .xmap(HoverEvent.ItemStackInfo::new, HoverEvent.ItemStackInfo::getItemStack);
        private static final Codec<HoverEvent.ItemStackInfo> SIMPLE_CODEC = ItemStack.SIMPLE_ITEM_CODEC
            .xmap(HoverEvent.ItemStackInfo::new, HoverEvent.ItemStackInfo::getItemStack);
        public static final Codec<HoverEvent.ItemStackInfo> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);
        public final Holder<Item> item;
        public final int count;
        public final DataComponentPatch components;
        @Nullable
        private ItemStack itemStack;

        ItemStackInfo(Holder<Item> item, int count, DataComponentPatch changes) {
            this.item = item;
            this.count = count;
            this.components = changes;
        }

        public ItemStackInfo(ItemStack stack) {
            this(stack.getItemHolder(), stack.getCount(), stack.getComponentsPatch());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                HoverEvent.ItemStackInfo itemStackInfo = (HoverEvent.ItemStackInfo)object;
                return this.count == itemStackInfo.count && this.item.equals(itemStackInfo.item) && this.components.equals(itemStackInfo.components);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int i = this.item.hashCode();
            i = 31 * i + this.count;
            return 31 * i + this.components.hashCode();
        }

        public ItemStack getItemStack() {
            if (this.itemStack == null) {
                this.itemStack = new ItemStack(this.item, this.count, this.components);
            }

            return this.itemStack;
        }

        private static DataResult<HoverEvent.ItemStackInfo> legacyCreate(Component text, @Nullable RegistryOps<?> ops) {
            try {
                CompoundTag compoundTag = TagParser.parseTag(text.getString());
                DynamicOps<Tag> dynamicOps = (DynamicOps<Tag>)(ops != null ? ops.withParent(NbtOps.INSTANCE) : NbtOps.INSTANCE);
                return ItemStack.CODEC.parse(dynamicOps, compoundTag).map(HoverEvent.ItemStackInfo::new);
            } catch (CommandSyntaxException var4) {
                return DataResult.error(() -> "Failed to parse item tag: " + var4.getMessage());
            }
        }
    }

    public interface LegacyConverter<T> {
        DataResult<T> parse(Component text, @Nullable RegistryOps<?> os);
    }

    static record TypedHoverEvent<T>(HoverEvent.Action<T> action, T value) {
        public static final MapCodec<HoverEvent.TypedHoverEvent<?>> CODEC = HoverEvent.Action.CODEC
            .dispatchMap("action", HoverEvent.TypedHoverEvent::action, action -> action.codec);
        public static final MapCodec<HoverEvent.TypedHoverEvent<?>> LEGACY_CODEC = HoverEvent.Action.CODEC
            .dispatchMap("action", HoverEvent.TypedHoverEvent::action, action -> action.legacyCodec);
    }
}
