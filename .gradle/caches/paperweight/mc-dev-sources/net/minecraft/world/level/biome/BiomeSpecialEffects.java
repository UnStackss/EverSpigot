package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;

public class BiomeSpecialEffects {
    public static final Codec<BiomeSpecialEffects> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Codec.INT.fieldOf("fog_color").forGetter(effects -> effects.fogColor),
                    Codec.INT.fieldOf("water_color").forGetter(effects -> effects.waterColor),
                    Codec.INT.fieldOf("water_fog_color").forGetter(effects -> effects.waterFogColor),
                    Codec.INT.fieldOf("sky_color").forGetter(effects -> effects.skyColor),
                    Codec.INT.optionalFieldOf("foliage_color").forGetter(effects -> effects.foliageColorOverride),
                    Codec.INT.optionalFieldOf("grass_color").forGetter(effects -> effects.grassColorOverride),
                    BiomeSpecialEffects.GrassColorModifier.CODEC
                        .optionalFieldOf("grass_color_modifier", BiomeSpecialEffects.GrassColorModifier.NONE)
                        .forGetter(effects -> effects.grassColorModifier),
                    AmbientParticleSettings.CODEC.optionalFieldOf("particle").forGetter(effects -> effects.ambientParticleSettings),
                    SoundEvent.CODEC.optionalFieldOf("ambient_sound").forGetter(effects -> effects.ambientLoopSoundEvent),
                    AmbientMoodSettings.CODEC.optionalFieldOf("mood_sound").forGetter(effects -> effects.ambientMoodSettings),
                    AmbientAdditionsSettings.CODEC.optionalFieldOf("additions_sound").forGetter(effects -> effects.ambientAdditionsSettings),
                    Music.CODEC.optionalFieldOf("music").forGetter(effects -> effects.backgroundMusic)
                )
                .apply(instance, BiomeSpecialEffects::new)
    );
    private final int fogColor;
    private final int waterColor;
    private final int waterFogColor;
    private final int skyColor;
    private final Optional<Integer> foliageColorOverride;
    private final Optional<Integer> grassColorOverride;
    private final BiomeSpecialEffects.GrassColorModifier grassColorModifier;
    private final Optional<AmbientParticleSettings> ambientParticleSettings;
    private final Optional<Holder<SoundEvent>> ambientLoopSoundEvent;
    private final Optional<AmbientMoodSettings> ambientMoodSettings;
    private final Optional<AmbientAdditionsSettings> ambientAdditionsSettings;
    private final Optional<Music> backgroundMusic;

    BiomeSpecialEffects(
        int fogColor,
        int waterColor,
        int waterFogColor,
        int skyColor,
        Optional<Integer> foliageColor,
        Optional<Integer> grassColor,
        BiomeSpecialEffects.GrassColorModifier grassColorModifier,
        Optional<AmbientParticleSettings> particleConfig,
        Optional<Holder<SoundEvent>> loopSound,
        Optional<AmbientMoodSettings> moodSound,
        Optional<AmbientAdditionsSettings> additionsSound,
        Optional<Music> music
    ) {
        this.fogColor = fogColor;
        this.waterColor = waterColor;
        this.waterFogColor = waterFogColor;
        this.skyColor = skyColor;
        this.foliageColorOverride = foliageColor;
        this.grassColorOverride = grassColor;
        this.grassColorModifier = grassColorModifier;
        this.ambientParticleSettings = particleConfig;
        this.ambientLoopSoundEvent = loopSound;
        this.ambientMoodSettings = moodSound;
        this.ambientAdditionsSettings = additionsSound;
        this.backgroundMusic = music;
    }

    public int getFogColor() {
        return this.fogColor;
    }

    public int getWaterColor() {
        return this.waterColor;
    }

    public int getWaterFogColor() {
        return this.waterFogColor;
    }

    public int getSkyColor() {
        return this.skyColor;
    }

    public Optional<Integer> getFoliageColorOverride() {
        return this.foliageColorOverride;
    }

    public Optional<Integer> getGrassColorOverride() {
        return this.grassColorOverride;
    }

    public BiomeSpecialEffects.GrassColorModifier getGrassColorModifier() {
        return this.grassColorModifier;
    }

    public Optional<AmbientParticleSettings> getAmbientParticleSettings() {
        return this.ambientParticleSettings;
    }

    public Optional<Holder<SoundEvent>> getAmbientLoopSoundEvent() {
        return this.ambientLoopSoundEvent;
    }

    public Optional<AmbientMoodSettings> getAmbientMoodSettings() {
        return this.ambientMoodSettings;
    }

    public Optional<AmbientAdditionsSettings> getAmbientAdditionsSettings() {
        return this.ambientAdditionsSettings;
    }

    public Optional<Music> getBackgroundMusic() {
        return this.backgroundMusic;
    }

    public static class Builder {
        private OptionalInt fogColor = OptionalInt.empty();
        private OptionalInt waterColor = OptionalInt.empty();
        private OptionalInt waterFogColor = OptionalInt.empty();
        private OptionalInt skyColor = OptionalInt.empty();
        private Optional<Integer> foliageColorOverride = Optional.empty();
        private Optional<Integer> grassColorOverride = Optional.empty();
        private BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;
        private Optional<AmbientParticleSettings> ambientParticle = Optional.empty();
        private Optional<Holder<SoundEvent>> ambientLoopSoundEvent = Optional.empty();
        private Optional<AmbientMoodSettings> ambientMoodSettings = Optional.empty();
        private Optional<AmbientAdditionsSettings> ambientAdditionsSettings = Optional.empty();
        private Optional<Music> backgroundMusic = Optional.empty();

        public BiomeSpecialEffects.Builder fogColor(int fogColor) {
            this.fogColor = OptionalInt.of(fogColor);
            return this;
        }

        public BiomeSpecialEffects.Builder waterColor(int waterColor) {
            this.waterColor = OptionalInt.of(waterColor);
            return this;
        }

        public BiomeSpecialEffects.Builder waterFogColor(int waterFogColor) {
            this.waterFogColor = OptionalInt.of(waterFogColor);
            return this;
        }

        public BiomeSpecialEffects.Builder skyColor(int skyColor) {
            this.skyColor = OptionalInt.of(skyColor);
            return this;
        }

        public BiomeSpecialEffects.Builder foliageColorOverride(int foliageColor) {
            this.foliageColorOverride = Optional.of(foliageColor);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorOverride(int grassColor) {
            this.grassColorOverride = Optional.of(grassColor);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorModifier(BiomeSpecialEffects.GrassColorModifier grassColorModifier) {
            this.grassColorModifier = grassColorModifier;
            return this;
        }

        public BiomeSpecialEffects.Builder ambientParticle(AmbientParticleSettings particleConfig) {
            this.ambientParticle = Optional.of(particleConfig);
            return this;
        }

        public BiomeSpecialEffects.Builder ambientLoopSound(Holder<SoundEvent> loopSound) {
            this.ambientLoopSoundEvent = Optional.of(loopSound);
            return this;
        }

        public BiomeSpecialEffects.Builder ambientMoodSound(AmbientMoodSettings moodSound) {
            this.ambientMoodSettings = Optional.of(moodSound);
            return this;
        }

        public BiomeSpecialEffects.Builder ambientAdditionsSound(AmbientAdditionsSettings additionsSound) {
            this.ambientAdditionsSettings = Optional.of(additionsSound);
            return this;
        }

        public BiomeSpecialEffects.Builder backgroundMusic(@Nullable Music music) {
            this.backgroundMusic = Optional.ofNullable(music);
            return this;
        }

        public BiomeSpecialEffects build() {
            return new BiomeSpecialEffects(
                this.fogColor.orElseThrow(() -> new IllegalStateException("Missing 'fog' color.")),
                this.waterColor.orElseThrow(() -> new IllegalStateException("Missing 'water' color.")),
                this.waterFogColor.orElseThrow(() -> new IllegalStateException("Missing 'water fog' color.")),
                this.skyColor.orElseThrow(() -> new IllegalStateException("Missing 'sky' color.")),
                this.foliageColorOverride,
                this.grassColorOverride,
                this.grassColorModifier,
                this.ambientParticle,
                this.ambientLoopSoundEvent,
                this.ambientMoodSettings,
                this.ambientAdditionsSettings,
                this.backgroundMusic
            );
        }
    }

    public static enum GrassColorModifier implements StringRepresentable {
        NONE("none") {
            @Override
            public int modifyColor(double x, double z, int color) {
                return color;
            }
        },
        DARK_FOREST("dark_forest") {
            @Override
            public int modifyColor(double x, double z, int color) {
                return (color & 16711422) + 2634762 >> 1;
            }
        },
        SWAMP("swamp") {
            @Override
            public int modifyColor(double x, double z, int color) {
                double d = Biome.BIOME_INFO_NOISE.getValue(x * 0.0225, z * 0.0225, false);
                return d < -0.1 ? 5011004 : 6975545;
            }
        };

        private final String name;
        public static final Codec<BiomeSpecialEffects.GrassColorModifier> CODEC = StringRepresentable.fromEnum(BiomeSpecialEffects.GrassColorModifier::values);

        public abstract int modifyColor(double x, double z, int color);

        GrassColorModifier(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
