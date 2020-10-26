package net.minecraft.world.entity.ai.attributes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class AttributeInstance {
    private static final String BASE_FIELD = "base";
    private static final String MODIFIERS_FIELD = "modifiers";
    public static final String ID_FIELD = "id";
    private final Holder<Attribute> attribute;
    private final Map<AttributeModifier.Operation, Map<ResourceLocation, AttributeModifier>> modifiersByOperation = Maps.newEnumMap(
        AttributeModifier.Operation.class
    );
    private final Map<ResourceLocation, AttributeModifier> modifierById = new Object2ObjectArrayMap<>();
    private final Map<ResourceLocation, AttributeModifier> permanentModifiers = new Object2ObjectArrayMap<>();
    private double baseValue;
    private boolean dirty = true;
    private double cachedValue;
    private final Consumer<AttributeInstance> onDirty;

    public AttributeInstance(Holder<Attribute> type, Consumer<AttributeInstance> updateCallback) {
        this.attribute = type;
        this.onDirty = updateCallback;
        this.baseValue = type.value().getDefaultValue();
    }

    public Holder<Attribute> getAttribute() {
        return this.attribute;
    }

    public double getBaseValue() {
        return this.baseValue;
    }

    public void setBaseValue(double baseValue) {
        if (baseValue != this.baseValue) {
            this.baseValue = baseValue;
            this.setDirty();
        }
    }

    @VisibleForTesting
    Map<ResourceLocation, AttributeModifier> getModifiers(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.computeIfAbsent(operation, operationx -> new Object2ObjectOpenHashMap<>());
    }

    public Set<AttributeModifier> getModifiers() {
        return ImmutableSet.copyOf(this.modifierById.values());
    }

    @Nullable
    public AttributeModifier getModifier(ResourceLocation id) {
        return this.modifierById.get(id);
    }

    public boolean hasModifier(ResourceLocation id) {
        return this.modifierById.get(id) != null;
    }

    private void addModifier(AttributeModifier modifier) {
        AttributeModifier attributeModifier = this.modifierById.putIfAbsent(modifier.id(), modifier);
        if (attributeModifier != null) {
            throw new IllegalArgumentException("Modifier is already applied on this attribute!");
        } else {
            this.getModifiers(modifier.operation()).put(modifier.id(), modifier);
            this.setDirty();
        }
    }

    public void addOrUpdateTransientModifier(AttributeModifier modifier) {
        AttributeModifier attributeModifier = this.modifierById.put(modifier.id(), modifier);
        if (modifier != attributeModifier) {
            this.getModifiers(modifier.operation()).put(modifier.id(), modifier);
            this.setDirty();
        }
    }

    public void addTransientModifier(AttributeModifier modifier) {
        this.addModifier(modifier);
    }

    public void addOrReplacePermanentModifier(AttributeModifier modifier) {
        this.removeModifier(modifier.id());
        this.addModifier(modifier);
        this.permanentModifiers.put(modifier.id(), modifier);
    }

    public void addPermanentModifier(AttributeModifier modifier) {
        this.addModifier(modifier);
        this.permanentModifiers.put(modifier.id(), modifier);
    }

    protected void setDirty() {
        this.dirty = true;
        this.onDirty.accept(this);
    }

    public void removeModifier(AttributeModifier modifier) {
        this.removeModifier(modifier.id());
    }

    public boolean removeModifier(ResourceLocation id) {
        AttributeModifier attributeModifier = this.modifierById.remove(id);
        if (attributeModifier == null) {
            return false;
        } else {
            this.getModifiers(attributeModifier.operation()).remove(id);
            this.permanentModifiers.remove(id);
            this.setDirty();
            return true;
        }
    }

    public void removeModifiers() {
        for (AttributeModifier attributeModifier : this.getModifiers()) {
            this.removeModifier(attributeModifier);
        }
    }

    public double getValue() {
        if (this.dirty) {
            this.cachedValue = this.calculateValue();
            this.dirty = false;
        }

        return this.cachedValue;
    }

    private double calculateValue() {
        double d = this.getBaseValue();

        for (AttributeModifier attributeModifier : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_VALUE)) {
            d += attributeModifier.amount(); // Paper - destroy speed API - diff on change
        }

        double e = d;

        for (AttributeModifier attributeModifier2 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
            e += d * attributeModifier2.amount(); // Paper - destroy speed API - diff on change
        }

        for (AttributeModifier attributeModifier3 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
            e *= 1.0 + attributeModifier3.amount(); // Paper - destroy speed API - diff on change
        }

        return attribute.value().sanitizeValue(e); // Paper - destroy speed API - diff on change
    }

    private Collection<AttributeModifier> getModifiersOrEmpty(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.getOrDefault(operation, Map.of()).values();
    }

    public void replaceFrom(AttributeInstance other) {
        this.baseValue = other.baseValue;
        this.modifierById.clear();
        this.modifierById.putAll(other.modifierById);
        this.permanentModifiers.clear();
        this.permanentModifiers.putAll(other.permanentModifiers);
        this.modifiersByOperation.clear();
        other.modifiersByOperation
            .forEach((operation, modifiers) -> this.getModifiers(operation).putAll((Map<? extends ResourceLocation, ? extends AttributeModifier>)modifiers));
        this.setDirty();
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        ResourceKey<Attribute> resourceKey = this.attribute
            .unwrapKey()
            .orElseThrow(() -> new IllegalStateException("Tried to serialize unregistered attribute"));
        compoundTag.putString("id", resourceKey.location().toString());
        compoundTag.putDouble("base", this.baseValue);
        if (!this.permanentModifiers.isEmpty()) {
            ListTag listTag = new ListTag();

            for (AttributeModifier attributeModifier : this.permanentModifiers.values()) {
                listTag.add(attributeModifier.save());
            }

            compoundTag.put("modifiers", listTag);
        }

        return compoundTag;
    }

    public void load(CompoundTag nbt) {
        this.baseValue = nbt.getDouble("base");
        if (nbt.contains("modifiers", 9)) {
            ListTag listTag = nbt.getList("modifiers", 10);

            for (int i = 0; i < listTag.size(); i++) {
                AttributeModifier attributeModifier = AttributeModifier.load(listTag.getCompound(i));
                if (attributeModifier != null) {
                    this.modifierById.put(attributeModifier.id(), attributeModifier);
                    this.getModifiers(attributeModifier.operation()).put(attributeModifier.id(), attributeModifier);
                    this.permanentModifiers.put(attributeModifier.id(), attributeModifier);
                }
            }
        }

        this.setDirty();
    }
}
