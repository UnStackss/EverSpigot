package net.minecraft.core.particles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class DustColorTransitionOptions extends ScalableParticleOptionsBase {
    public static final Vector3f SCULK_PARTICLE_COLOR = Vec3.fromRGB24(3790560).toVector3f();
    public static final DustColorTransitionOptions SCULK_TO_REDSTONE = new DustColorTransitionOptions(
        SCULK_PARTICLE_COLOR, DustParticleOptions.REDSTONE_PARTICLE_COLOR, 1.0F
    );
    public static final MapCodec<DustColorTransitionOptions> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    ExtraCodecs.VECTOR3F.fieldOf("from_color").forGetter(effect -> effect.fromColor),
                    ExtraCodecs.VECTOR3F.fieldOf("to_color").forGetter(effect -> effect.toColor),
                    SCALE.fieldOf("scale").forGetter(ScalableParticleOptionsBase::getScale)
                )
                .apply(instance, DustColorTransitionOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DustColorTransitionOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VECTOR3F,
        effect -> effect.fromColor,
        ByteBufCodecs.VECTOR3F,
        effect -> effect.toColor,
        ByteBufCodecs.FLOAT,
        ScalableParticleOptionsBase::getScale,
        DustColorTransitionOptions::new
    );
    private final Vector3f fromColor;
    private final Vector3f toColor;

    public DustColorTransitionOptions(Vector3f fromColor, Vector3f toColor, float scale) {
        super(scale);
        this.fromColor = fromColor;
        this.toColor = toColor;
    }

    public Vector3f getFromColor() {
        return this.fromColor;
    }

    public Vector3f getToColor() {
        return this.toColor;
    }

    @Override
    public ParticleType<DustColorTransitionOptions> getType() {
        return ParticleTypes.DUST_COLOR_TRANSITION;
    }
}
