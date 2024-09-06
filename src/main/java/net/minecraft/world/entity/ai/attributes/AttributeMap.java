package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AttributeMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Holder<Attribute>, AttributeInstance> attributes = new Object2ObjectOpenHashMap<>();
    private final Set<AttributeInstance> attributesToSync = new ObjectOpenHashSet<>();
    private final Set<AttributeInstance> attributesToUpdate = new ObjectOpenHashSet<>();
    private final AttributeSupplier supplier;

    public AttributeMap(AttributeSupplier defaultAttributes) {
        this.supplier = defaultAttributes;
    }

    private void onAttributeModified(AttributeInstance instance) {
        this.attributesToUpdate.add(instance);
        if (instance.getAttribute().value().isClientSyncable()) {
            this.attributesToSync.add(instance);
        }
    }

    public Set<AttributeInstance> getAttributesToSync() {
        return this.attributesToSync;
    }

    public Set<AttributeInstance> getAttributesToUpdate() {
        return this.attributesToUpdate;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return this.attributes.values().stream().filter(attribute -> attribute.getAttribute().value().isClientSyncable()).collect(Collectors.toList());
    }

    @Nullable
    public AttributeInstance getInstance(Holder<Attribute> attribute) {
        return this.attributes.computeIfAbsent(attribute, attributex -> this.supplier.createInstance(this::onAttributeModified, attributex));
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id) != null : this.supplier.hasModifier(attribute, id);
    }

    public double getValue(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id).amount() : this.supplier.getModifierValue(attribute, id);
    }

    public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiersMap) {
        modifiersMap.forEach((attribute, modifier) -> {
            AttributeInstance attributeInstance = this.getInstance((Holder<Attribute>)attribute);
            if (attributeInstance != null) {
                attributeInstance.removeModifier(modifier.id());
                attributeInstance.addTransientModifier(modifier);
            }
        });
    }

    public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiersMap) {
        modifiersMap.asMap().forEach((attribute, modifiers) -> {
            AttributeInstance attributeInstance = this.attributes.get(attribute);
            if (attributeInstance != null) {
                modifiers.forEach(modifier -> attributeInstance.removeModifier(modifier.id()));
            }
        });
    }

    public void assignAllValues(AttributeMap other) {
        other.attributes.values().forEach(attributeInstance -> {
            AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
            if (attributeInstance2 != null) {
                attributeInstance2.replaceFrom(attributeInstance);
            }
        });
    }

    public void assignBaseValues(AttributeMap other) {
        other.attributes.values().forEach(attributeInstance -> {
            AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
            if (attributeInstance2 != null) {
                attributeInstance2.setBaseValue(attributeInstance.getBaseValue());
            }
        });
    }

    public ListTag save() {
        ListTag listTag = new ListTag();

        for (AttributeInstance attributeInstance : this.attributes.values()) {
            listTag.add(attributeInstance.save());
        }

        return listTag;
    }

    public void load(ListTag nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            CompoundTag compoundTag = nbt.getCompound(i);
            String string = compoundTag.getString("id");
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            if (resourceLocation != null) {
                Util.ifElse(BuiltInRegistries.ATTRIBUTE.getHolder(resourceLocation), attribute -> {
                    AttributeInstance attributeInstance = this.getInstance(attribute);
                    if (attributeInstance != null) {
                        attributeInstance.load(compoundTag);
                    }
                }, () -> LOGGER.warn("Ignoring unknown attribute '{}'", resourceLocation));
            } else {
                LOGGER.warn("Ignoring malformed attribute '{}'", string);
            }
        }
    }
}
