package net.minecraft.world.level.gameevent;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityPositionSource implements PositionSource {
    public static final MapCodec<EntityPositionSource> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("source_entity").forGetter(EntityPositionSource::getUuid),
                    Codec.FLOAT.fieldOf("y_offset").orElse(0.0F).forGetter(entityPositionSource -> entityPositionSource.yOffset)
                )
                .apply(instance, (uuid, yOffset) -> new EntityPositionSource(Either.right(Either.left(uuid)), yOffset))
    );
    public static final StreamCodec<ByteBuf, EntityPositionSource> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        EntityPositionSource::getId,
        ByteBufCodecs.FLOAT,
        source -> source.yOffset,
        (entityId, yOffset) -> new EntityPositionSource(Either.right(Either.right(entityId)), yOffset)
    );
    private Either<Entity, Either<UUID, Integer>> entityOrUuidOrId;
    private final float yOffset;

    public EntityPositionSource(Entity entity, float yOffset) {
        this(Either.left(entity), yOffset);
    }

    private EntityPositionSource(Either<Entity, Either<UUID, Integer>> source, float yOffset) {
        this.entityOrUuidOrId = source;
        this.yOffset = yOffset;
    }

    @Override
    public Optional<Vec3> getPosition(Level world) {
        if (this.entityOrUuidOrId.left().isEmpty()) {
            this.resolveEntity(world);
        }

        return this.entityOrUuidOrId.left().map(entity -> entity.position().add(0.0, (double)this.yOffset, 0.0));
    }

    private void resolveEntity(Level world) {
        this.entityOrUuidOrId
            .map(
                Optional::of,
                entityId -> Optional.ofNullable(
                        entityId.map(uuid -> world instanceof ServerLevel serverLevel ? serverLevel.getEntity(uuid) : null, world::getEntity)
                    )
            )
            .ifPresent(entity -> this.entityOrUuidOrId = Either.left(entity));
    }

    private UUID getUuid() {
        return this.entityOrUuidOrId.map(Entity::getUUID, entityId -> entityId.map(Function.identity(), entityIdx -> {
                throw new RuntimeException("Unable to get entityId from uuid");
            }));
    }

    private int getId() {
        return this.entityOrUuidOrId.map(Entity::getId, entityId -> entityId.map(uuid -> {
                throw new IllegalStateException("Unable to get entityId from uuid");
            }, Function.identity()));
    }

    @Override
    public PositionSourceType<EntityPositionSource> getType() {
        return PositionSourceType.ENTITY;
    }

    public static class Type implements PositionSourceType<EntityPositionSource> {
        @Override
        public MapCodec<EntityPositionSource> codec() {
            return EntityPositionSource.CODEC;
        }

        @Override
        public StreamCodec<ByteBuf, EntityPositionSource> streamCodec() {
            return EntityPositionSource.STREAM_CODEC;
        }
    }
}
