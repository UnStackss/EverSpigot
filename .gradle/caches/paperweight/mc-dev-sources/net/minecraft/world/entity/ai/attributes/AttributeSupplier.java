package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

public class AttributeSupplier {
    private final Map<Holder<Attribute>, AttributeInstance> instances;

    AttributeSupplier(Map<Holder<Attribute>, AttributeInstance> instances) {
        this.instances = instances;
    }

    public AttributeInstance getAttributeInstance(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.instances.get(attribute);
        if (attributeInstance == null) {
            throw new IllegalArgumentException("Can't find attribute " + attribute.getRegisteredName());
        } else {
            return attributeInstance;
        }
    }

    public double getValue(Holder<Attribute> attribute) {
        return this.getAttributeInstance(attribute).getValue();
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        return this.getAttributeInstance(attribute).getBaseValue();
    }

    public double getModifierValue(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeModifier attributeModifier = this.getAttributeInstance(attribute).getModifier(id);
        if (attributeModifier == null) {
            throw new IllegalArgumentException("Can't find modifier " + id + " on attribute " + attribute.getRegisteredName());
        } else {
            return attributeModifier.amount();
        }
    }

    @Nullable
    public AttributeInstance createInstance(Consumer<AttributeInstance> updateCallback, Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.instances.get(attribute);
        if (attributeInstance == null) {
            return null;
        } else {
            AttributeInstance attributeInstance2 = new AttributeInstance(attribute, updateCallback);
            attributeInstance2.replaceFrom(attributeInstance);
            return attributeInstance2;
        }
    }

    public static AttributeSupplier.Builder builder() {
        return new AttributeSupplier.Builder();
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.instances.containsKey(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance attributeInstance = this.instances.get(attribute);
        return attributeInstance != null && attributeInstance.getModifier(id) != null;
    }

    public static class Builder {
        private final ImmutableMap.Builder<Holder<Attribute>, AttributeInstance> builder = ImmutableMap.builder();
        private boolean instanceFrozen;

        private AttributeInstance create(Holder<Attribute> attribute) {
            AttributeInstance attributeInstance = new AttributeInstance(attribute, attributex -> {
                if (this.instanceFrozen) {
                    throw new UnsupportedOperationException("Tried to change value for default attribute instance: " + attribute.getRegisteredName());
                }
            });
            this.builder.put(attribute, attributeInstance);
            return attributeInstance;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> attribute) {
            this.create(attribute);
            return this;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> attribute, double baseValue) {
            AttributeInstance attributeInstance = this.create(attribute);
            attributeInstance.setBaseValue(baseValue);
            return this;
        }

        public AttributeSupplier build() {
            this.instanceFrozen = true;
            return new AttributeSupplier(this.builder.buildKeepingLast());
        }
    }
}
