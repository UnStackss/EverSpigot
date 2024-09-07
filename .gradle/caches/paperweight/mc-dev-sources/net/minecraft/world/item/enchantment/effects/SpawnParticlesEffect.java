package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public record SpawnParticlesEffect(
    ParticleOptions particle,
    SpawnParticlesEffect.PositionSource horizontalPosition,
    SpawnParticlesEffect.PositionSource verticalPosition,
    SpawnParticlesEffect.VelocitySource horizontalVelocity,
    SpawnParticlesEffect.VelocitySource verticalVelocity,
    FloatProvider speed
) implements EnchantmentEntityEffect {
    public static final MapCodec<SpawnParticlesEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    ParticleTypes.CODEC.fieldOf("particle").forGetter(SpawnParticlesEffect::particle),
                    SpawnParticlesEffect.PositionSource.CODEC.fieldOf("horizontal_position").forGetter(SpawnParticlesEffect::horizontalPosition),
                    SpawnParticlesEffect.PositionSource.CODEC.fieldOf("vertical_position").forGetter(SpawnParticlesEffect::verticalPosition),
                    SpawnParticlesEffect.VelocitySource.CODEC.fieldOf("horizontal_velocity").forGetter(SpawnParticlesEffect::horizontalVelocity),
                    SpawnParticlesEffect.VelocitySource.CODEC.fieldOf("vertical_velocity").forGetter(SpawnParticlesEffect::verticalVelocity),
                    FloatProvider.CODEC.optionalFieldOf("speed", ConstantFloat.ZERO).forGetter(SpawnParticlesEffect::speed)
                )
                .apply(instance, SpawnParticlesEffect::new)
    );

    public static SpawnParticlesEffect.PositionSource offsetFromEntityPosition(float offset) {
        return new SpawnParticlesEffect.PositionSource(SpawnParticlesEffect.PositionSourceType.ENTITY_POSITION, offset, 1.0F);
    }

    public static SpawnParticlesEffect.PositionSource inBoundingBox() {
        return new SpawnParticlesEffect.PositionSource(SpawnParticlesEffect.PositionSourceType.BOUNDING_BOX, 0.0F, 1.0F);
    }

    public static SpawnParticlesEffect.VelocitySource movementScaled(float movementScale) {
        return new SpawnParticlesEffect.VelocitySource(movementScale, ConstantFloat.ZERO);
    }

    public static SpawnParticlesEffect.VelocitySource fixedVelocity(FloatProvider base) {
        return new SpawnParticlesEffect.VelocitySource(0.0F, base);
    }

    @Override
    public void apply(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos) {
        RandomSource randomSource = user.getRandom();
        Vec3 vec3 = user.getKnownMovement();
        float f = user.getBbWidth();
        float g = user.getBbHeight();
        world.sendParticles(
            this.particle,
            this.horizontalPosition.getCoordinate(pos.x(), pos.x(), f, randomSource),
            this.verticalPosition.getCoordinate(pos.y(), pos.y() + (double)(g / 2.0F), g, randomSource),
            this.horizontalPosition.getCoordinate(pos.z(), pos.z(), f, randomSource),
            0,
            this.horizontalVelocity.getVelocity(vec3.x(), randomSource),
            this.verticalVelocity.getVelocity(vec3.y(), randomSource),
            this.horizontalVelocity.getVelocity(vec3.z(), randomSource),
            (double)this.speed.sample(randomSource)
        );
    }

    @Override
    public MapCodec<SpawnParticlesEffect> codec() {
        return CODEC;
    }

    public static record PositionSource(SpawnParticlesEffect.PositionSourceType type, float offset, float scale) {
        public static final MapCodec<SpawnParticlesEffect.PositionSource> CODEC = RecordCodecBuilder.<SpawnParticlesEffect.PositionSource>mapCodec(
                instance -> instance.group(
                            SpawnParticlesEffect.PositionSourceType.CODEC.fieldOf("type").forGetter(SpawnParticlesEffect.PositionSource::type),
                            Codec.FLOAT.optionalFieldOf("offset", Float.valueOf(0.0F)).forGetter(SpawnParticlesEffect.PositionSource::offset),
                            ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("scale", 1.0F).forGetter(SpawnParticlesEffect.PositionSource::scale)
                        )
                        .apply(instance, SpawnParticlesEffect.PositionSource::new)
            )
            .validate(
                source -> source.type() == SpawnParticlesEffect.PositionSourceType.ENTITY_POSITION && source.scale() != 1.0F
                        ? DataResult.error(() -> "Cannot scale an entity position coordinate source")
                        : DataResult.success(source)
            );

        public double getCoordinate(double entityPosition, double boundingBoxCenter, float boundingBoxSize, RandomSource random) {
            return this.type.getCoordinate(entityPosition, boundingBoxCenter, boundingBoxSize * this.scale, random) + (double)this.offset;
        }
    }

    public static enum PositionSourceType implements StringRepresentable {
        ENTITY_POSITION("entity_position", (entityPosition, boundingBoxCenter, boundingBoxSize, random) -> entityPosition),
        BOUNDING_BOX(
            "in_bounding_box",
            (entityPosition, boundingBoxCenter, boundingBoxSize, random) -> boundingBoxCenter + (random.nextDouble() - 0.5) * (double)boundingBoxSize
        );

        public static final Codec<SpawnParticlesEffect.PositionSourceType> CODEC = StringRepresentable.fromEnum(SpawnParticlesEffect.PositionSourceType::values);
        private final String id;
        private final SpawnParticlesEffect.PositionSourceType.CoordinateSource source;

        private PositionSourceType(final String id, final SpawnParticlesEffect.PositionSourceType.CoordinateSource coordinateSource) {
            this.id = id;
            this.source = coordinateSource;
        }

        public double getCoordinate(double entityPosition, double boundingBoxCenter, float boundingBoxSize, RandomSource random) {
            return this.source.getCoordinate(entityPosition, boundingBoxCenter, boundingBoxSize, random);
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        @FunctionalInterface
        interface CoordinateSource {
            double getCoordinate(double entityPosition, double boundingBoxCenter, float boundingBoxSize, RandomSource random);
        }
    }

    public static record VelocitySource(float movementScale, FloatProvider base) {
        public static final MapCodec<SpawnParticlesEffect.VelocitySource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("movement_scale", Float.valueOf(0.0F)).forGetter(SpawnParticlesEffect.VelocitySource::movementScale),
                        FloatProvider.CODEC.optionalFieldOf("base", ConstantFloat.ZERO).forGetter(SpawnParticlesEffect.VelocitySource::base)
                    )
                    .apply(instance, SpawnParticlesEffect.VelocitySource::new)
        );

        public double getVelocity(double entityVelocity, RandomSource random) {
            return entityVelocity * (double)this.movementScale + (double)this.base.sample(random);
        }
    }
}
