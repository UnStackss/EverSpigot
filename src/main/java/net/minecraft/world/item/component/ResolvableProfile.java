package net.minecraft.world.item.component;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public record ResolvableProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties, GameProfile gameProfile) {
    private static final Codec<ResolvableProfile> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ExtraCodecs.PLAYER_NAME.optionalFieldOf("name").forGetter(ResolvableProfile::name),
                    UUIDUtil.CODEC.optionalFieldOf("id").forGetter(ResolvableProfile::id),
                    UUIDUtil.STRING_CODEC.lenientOptionalFieldOf("Id").forGetter($ -> Optional.empty()), // Paper
                    ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", new PropertyMap()).forGetter(ResolvableProfile::properties)
                )
                .apply(instance, (s, uuid, uuid2, propertyMap) -> new ResolvableProfile(s, uuid2.or(() -> uuid), propertyMap)) // Paper
    );
    public static final Codec<ResolvableProfile> CODEC = Codec.withAlternative(
        FULL_CODEC, ExtraCodecs.PLAYER_NAME, name -> new ResolvableProfile(Optional.of(name), Optional.empty(), new PropertyMap())
    );
    public static final StreamCodec<ByteBuf, ResolvableProfile> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(16).apply(ByteBufCodecs::optional),
        ResolvableProfile::name,
        UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional),
        ResolvableProfile::id,
        ByteBufCodecs.GAME_PROFILE_PROPERTIES,
        ResolvableProfile::properties,
        ResolvableProfile::new
    );

    public ResolvableProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        this(name, id, properties, createProfile(name, id, properties));
    }

    public ResolvableProfile(GameProfile gameProfile) {
        this(Optional.of(gameProfile.getName()), Optional.of(gameProfile.getId()), gameProfile.getProperties(), gameProfile);
    }

    public CompletableFuture<ResolvableProfile> resolve() {
        if (this.isResolved()) {
            return CompletableFuture.completedFuture(this);
        } else {
            return this.id.isPresent() ? SkullBlockEntity.fetchGameProfile(this.id.get(), this.name.orElse(null)).thenApply(optional -> { // Paper - player profile events
                GameProfile gameProfile = optional.orElseGet(() -> new GameProfile(this.id.get(), this.name.orElse("")));
                return new ResolvableProfile(gameProfile);
            }) : SkullBlockEntity.fetchGameProfile(this.name.orElseThrow()).thenApply(profile -> {
                GameProfile gameProfile = profile.orElseGet(() -> new GameProfile(Util.NIL_UUID, this.name.get()));
                return new ResolvableProfile(gameProfile);
            });
        }
    }

    private static GameProfile createProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        GameProfile gameProfile = new GameProfile(id.orElse(Util.NIL_UUID), name.orElse(""));
        gameProfile.getProperties().putAll(properties);
        return gameProfile;
    }

    public boolean isResolved() {
        return !this.properties.isEmpty() || this.id.isPresent() == this.name.isPresent();
    }
}
