package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class Attribute {
    public static final Codec<Holder<Attribute>> CODEC = BuiltInRegistries.ATTRIBUTE.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Attribute>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE);
    private final double defaultValue;
    private boolean syncable;
    private final String descriptionId;
    private Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;

    protected Attribute(String translationKey, double fallback) {
        this.defaultValue = fallback;
        this.descriptionId = translationKey;
    }

    public double getDefaultValue() {
        return this.defaultValue;
    }

    public boolean isClientSyncable() {
        return this.syncable;
    }

    public Attribute setSyncable(boolean tracked) {
        this.syncable = tracked;
        return this;
    }

    public Attribute setSentiment(Attribute.Sentiment category) {
        this.sentiment = category;
        return this;
    }

    public double sanitizeValue(double value) {
        return value;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public ChatFormatting getStyle(boolean addition) {
        return this.sentiment.getStyle(addition);
    }

    public static enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE;

        public ChatFormatting getStyle(boolean addition) {
            return switch (this) {
                case POSITIVE -> addition ? ChatFormatting.BLUE : ChatFormatting.RED;
                case NEUTRAL -> ChatFormatting.GRAY;
                case NEGATIVE -> addition ? ChatFormatting.RED : ChatFormatting.BLUE;
            };
        }
    }
}
