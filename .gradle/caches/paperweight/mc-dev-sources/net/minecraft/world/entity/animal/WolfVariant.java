package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class WolfVariant {
    public static final Codec<WolfVariant> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("wild_texture").forGetter(wolfVariant -> wolfVariant.wildTexture),
                    ResourceLocation.CODEC.fieldOf("tame_texture").forGetter(wolfVariant -> wolfVariant.tameTexture),
                    ResourceLocation.CODEC.fieldOf("angry_texture").forGetter(wolfVariant -> wolfVariant.angryTexture),
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(WolfVariant::biomes)
                )
                .apply(instance, WolfVariant::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, WolfVariant> DIRECT_STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC,
        WolfVariant::wildTexture,
        ResourceLocation.STREAM_CODEC,
        WolfVariant::tameTexture,
        ResourceLocation.STREAM_CODEC,
        WolfVariant::angryTexture,
        ByteBufCodecs.holderSet(Registries.BIOME),
        WolfVariant::biomes,
        WolfVariant::new
    );
    public static final Codec<Holder<WolfVariant>> CODEC = RegistryFileCodec.create(Registries.WOLF_VARIANT, DIRECT_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<WolfVariant>> STREAM_CODEC = ByteBufCodecs.holder(
        Registries.WOLF_VARIANT, DIRECT_STREAM_CODEC
    );
    private final ResourceLocation wildTexture;
    private final ResourceLocation tameTexture;
    private final ResourceLocation angryTexture;
    private final ResourceLocation wildTextureFull;
    private final ResourceLocation tameTextureFull;
    private final ResourceLocation angryTextureFull;
    private final HolderSet<Biome> biomes;

    public WolfVariant(ResourceLocation wildId, ResourceLocation tameId, ResourceLocation angryId, HolderSet<Biome> biomes) {
        this.wildTexture = wildId;
        this.wildTextureFull = fullTextureId(wildId);
        this.tameTexture = tameId;
        this.tameTextureFull = fullTextureId(tameId);
        this.angryTexture = angryId;
        this.angryTextureFull = fullTextureId(angryId);
        this.biomes = biomes;
    }

    private static ResourceLocation fullTextureId(ResourceLocation id) {
        return id.withPath(oldPath -> "textures/" + oldPath + ".png");
    }

    public ResourceLocation wildTexture() {
        return this.wildTextureFull;
    }

    public ResourceLocation tameTexture() {
        return this.tameTextureFull;
    }

    public ResourceLocation angryTexture() {
        return this.angryTextureFull;
    }

    public HolderSet<Biome> biomes() {
        return this.biomes;
    }

    @Override
    public boolean equals(Object object) {
        return object == this
            || object instanceof WolfVariant wolfVariant
                && Objects.equals(this.wildTexture, wolfVariant.wildTexture)
                && Objects.equals(this.tameTexture, wolfVariant.tameTexture)
                && Objects.equals(this.angryTexture, wolfVariant.angryTexture)
                && Objects.equals(this.biomes, wolfVariant.biomes);
    }

    @Override
    public int hashCode() {
        int i = 1;
        i = 31 * i + this.wildTexture.hashCode();
        i = 31 * i + this.tameTexture.hashCode();
        i = 31 * i + this.angryTexture.hashCode();
        return 31 * i + this.biomes.hashCode();
    }
}
