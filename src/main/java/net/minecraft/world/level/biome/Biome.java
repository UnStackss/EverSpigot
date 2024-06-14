package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class Biome {
    public static final Codec<Biome> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Biome.ClimateSettings.CODEC.forGetter(biome -> biome.climateSettings),
                    BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(biome -> biome.specialEffects),
                    BiomeGenerationSettings.CODEC.forGetter(biome -> biome.generationSettings),
                    MobSpawnSettings.CODEC.forGetter(biome -> biome.mobSettings)
                )
                .apply(instance, Biome::new)
    );
    public static final Codec<Biome> NETWORK_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Biome.ClimateSettings.CODEC.forGetter(biome -> biome.climateSettings),
                    BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(biome -> biome.specialEffects)
                )
                .apply(instance, (weather, effects) -> new Biome(weather, effects, BiomeGenerationSettings.EMPTY, MobSpawnSettings.EMPTY))
    );
    public static final Codec<Holder<Biome>> CODEC = RegistryFileCodec.create(Registries.BIOME, DIRECT_CODEC);
    public static final Codec<HolderSet<Biome>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.BIOME, DIRECT_CODEC);
    private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), ImmutableList.of(0));
    static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(
        new WorldgenRandom(new LegacyRandomSource(3456L)), ImmutableList.of(-2, -1, 0)
    );
    @Deprecated(
        forRemoval = true
    )
    public static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), ImmutableList.of(0));
    private static final int TEMPERATURE_CACHE_SIZE = 1024;
    public final Biome.ClimateSettings climateSettings;
    private final BiomeGenerationSettings generationSettings;
    private final MobSpawnSettings mobSettings;
    private final BiomeSpecialEffects specialEffects;
    private final ThreadLocal<Long2FloatLinkedOpenHashMap> temperatureCache = ThreadLocal.withInitial(() -> Util.make(() -> {
            Long2FloatLinkedOpenHashMap long2FloatLinkedOpenHashMap = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
                protected void rehash(int i) {
                }
            };
            long2FloatLinkedOpenHashMap.defaultReturnValue(Float.NaN);
            return long2FloatLinkedOpenHashMap;
        }));

    Biome(Biome.ClimateSettings weather, BiomeSpecialEffects effects, BiomeGenerationSettings generationSettings, MobSpawnSettings spawnSettings) {
        this.climateSettings = weather;
        this.generationSettings = generationSettings;
        this.mobSettings = spawnSettings;
        this.specialEffects = effects;
    }

    public int getSkyColor() {
        return this.specialEffects.getSkyColor();
    }

    public MobSpawnSettings getMobSettings() {
        return this.mobSettings;
    }

    public boolean hasPrecipitation() {
        return this.climateSettings.hasPrecipitation();
    }

    public Biome.Precipitation getPrecipitationAt(BlockPos pos) {
        if (!this.hasPrecipitation()) {
            return Biome.Precipitation.NONE;
        } else {
            return this.coldEnoughToSnow(pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
        }
    }

    private float getHeightAdjustedTemperature(BlockPos pos) {
        float f = this.climateSettings.temperatureModifier.modifyTemperature(pos, this.getBaseTemperature());
        if (pos.getY() > 80) {
            float g = (float)(TEMPERATURE_NOISE.getValue((double)((float)pos.getX() / 8.0F), (double)((float)pos.getZ() / 8.0F), false) * 8.0);
            return f - (g + (float)pos.getY() - 80.0F) * 0.05F / 40.0F;
        } else {
            return f;
        }
    }

    @Deprecated
    public float getTemperature(BlockPos blockPos) {
        return this.getHeightAdjustedTemperature(blockPos); // Paper - optimise random ticking
    }

    public boolean shouldFreeze(LevelReader world, BlockPos blockPos) {
        return this.shouldFreeze(world, blockPos, true);
    }

    public boolean shouldFreeze(LevelReader world, BlockPos pos, boolean doWaterCheck) {
        if (this.warmEnoughToRain(pos)) {
            return false;
        } else {
            if (pos.getY() >= world.getMinBuildHeight() && pos.getY() < world.getMaxBuildHeight() && world.getBrightness(LightLayer.BLOCK, pos) < 10) {
                BlockState blockState = world.getBlockState(pos);
                FluidState fluidState = world.getFluidState(pos);
                if (fluidState.getType() == Fluids.WATER && blockState.getBlock() instanceof LiquidBlock) {
                    if (!doWaterCheck) {
                        return true;
                    }

                    boolean bl = world.isWaterAt(pos.west()) && world.isWaterAt(pos.east()) && world.isWaterAt(pos.north()) && world.isWaterAt(pos.south());
                    if (!bl) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public boolean coldEnoughToSnow(BlockPos pos) {
        return !this.warmEnoughToRain(pos);
    }

    public boolean warmEnoughToRain(BlockPos pos) {
        return this.getTemperature(pos) >= 0.15F;
    }

    public boolean shouldMeltFrozenOceanIcebergSlightly(BlockPos pos) {
        return this.getTemperature(pos) > 0.1F;
    }

    public boolean shouldSnow(LevelReader world, BlockPos pos) {
        if (this.warmEnoughToRain(pos)) {
            return false;
        } else {
            if (pos.getY() >= world.getMinBuildHeight() && pos.getY() < world.getMaxBuildHeight() && world.getBrightness(LightLayer.BLOCK, pos) < 10) {
                BlockState blockState = world.getBlockState(pos);
                if ((blockState.isAir() || blockState.is(Blocks.SNOW)) && Blocks.SNOW.defaultBlockState().canSurvive(world, pos)) {
                    return true;
                }
            }

            return false;
        }
    }

    public BiomeGenerationSettings getGenerationSettings() {
        return this.generationSettings;
    }

    public int getFogColor() {
        return this.specialEffects.getFogColor();
    }

    public int getGrassColor(double x, double z) {
        int i = this.specialEffects.getGrassColorOverride().orElseGet(this::getGrassColorFromTexture);
        return this.specialEffects.getGrassColorModifier().modifyColor(x, z, i);
    }

    private int getGrassColorFromTexture() {
        double d = (double)Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
        double e = (double)Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
        return GrassColor.get(d, e);
    }

    public int getFoliageColor() {
        return this.specialEffects.getFoliageColorOverride().orElseGet(this::getFoliageColorFromTexture);
    }

    private int getFoliageColorFromTexture() {
        double d = (double)Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
        double e = (double)Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
        return FoliageColor.get(d, e);
    }

    public float getBaseTemperature() {
        return this.climateSettings.temperature;
    }

    public BiomeSpecialEffects getSpecialEffects() {
        return this.specialEffects;
    }

    public int getWaterColor() {
        return this.specialEffects.getWaterColor();
    }

    public int getWaterFogColor() {
        return this.specialEffects.getWaterFogColor();
    }

    public Optional<AmbientParticleSettings> getAmbientParticle() {
        return this.specialEffects.getAmbientParticleSettings();
    }

    public Optional<Holder<SoundEvent>> getAmbientLoop() {
        return this.specialEffects.getAmbientLoopSoundEvent();
    }

    public Optional<AmbientMoodSettings> getAmbientMood() {
        return this.specialEffects.getAmbientMoodSettings();
    }

    public Optional<AmbientAdditionsSettings> getAmbientAdditions() {
        return this.specialEffects.getAmbientAdditionsSettings();
    }

    public Optional<Music> getBackgroundMusic() {
        return this.specialEffects.getBackgroundMusic();
    }

    public static class BiomeBuilder {
        private boolean hasPrecipitation = true;
        @Nullable
        private Float temperature;
        private Biome.TemperatureModifier temperatureModifier = Biome.TemperatureModifier.NONE;
        @Nullable
        private Float downfall;
        @Nullable
        private BiomeSpecialEffects specialEffects;
        @Nullable
        private MobSpawnSettings mobSpawnSettings;
        @Nullable
        private BiomeGenerationSettings generationSettings;

        public Biome.BiomeBuilder hasPrecipitation(boolean precipitation) {
            this.hasPrecipitation = precipitation;
            return this;
        }

        public Biome.BiomeBuilder temperature(float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Biome.BiomeBuilder downfall(float downfall) {
            this.downfall = downfall;
            return this;
        }

        public Biome.BiomeBuilder specialEffects(BiomeSpecialEffects effects) {
            this.specialEffects = effects;
            return this;
        }

        public Biome.BiomeBuilder mobSpawnSettings(MobSpawnSettings spawnSettings) {
            this.mobSpawnSettings = spawnSettings;
            return this;
        }

        public Biome.BiomeBuilder generationSettings(BiomeGenerationSettings generationSettings) {
            this.generationSettings = generationSettings;
            return this;
        }

        public Biome.BiomeBuilder temperatureAdjustment(Biome.TemperatureModifier temperatureModifier) {
            this.temperatureModifier = temperatureModifier;
            return this;
        }

        public Biome build() {
            if (this.temperature != null
                && this.downfall != null
                && this.specialEffects != null
                && this.mobSpawnSettings != null
                && this.generationSettings != null) {
                return new Biome(
                    new Biome.ClimateSettings(this.hasPrecipitation, this.temperature, this.temperatureModifier, this.downfall),
                    this.specialEffects,
                    this.generationSettings,
                    this.mobSpawnSettings
                );
            } else {
                throw new IllegalStateException("You are missing parameters to build a proper biome\n" + this);
            }
        }

        @Override
        public String toString() {
            return "BiomeBuilder{\nhasPrecipitation="
                + this.hasPrecipitation
                + ",\ntemperature="
                + this.temperature
                + ",\ntemperatureModifier="
                + this.temperatureModifier
                + ",\ndownfall="
                + this.downfall
                + ",\nspecialEffects="
                + this.specialEffects
                + ",\nmobSpawnSettings="
                + this.mobSpawnSettings
                + ",\ngenerationSettings="
                + this.generationSettings
                + ",\n}";
        }
    }

    public static record ClimateSettings(boolean hasPrecipitation, float temperature, Biome.TemperatureModifier temperatureModifier, float downfall) {
        public static final MapCodec<Biome.ClimateSettings> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                        Codec.BOOL.fieldOf("has_precipitation").forGetter(weather -> weather.hasPrecipitation),
                        Codec.FLOAT.fieldOf("temperature").forGetter(weather -> weather.temperature),
                        Biome.TemperatureModifier.CODEC
                            .optionalFieldOf("temperature_modifier", Biome.TemperatureModifier.NONE)
                            .forGetter(weather -> weather.temperatureModifier),
                        Codec.FLOAT.fieldOf("downfall").forGetter(weather -> weather.downfall)
                    )
                    .apply(instance, Biome.ClimateSettings::new)
        );
    }

    public static enum Precipitation implements StringRepresentable {
        NONE("none"),
        RAIN("rain"),
        SNOW("snow");

        public static final Codec<Biome.Precipitation> CODEC = StringRepresentable.fromEnum(Biome.Precipitation::values);
        private final String name;

        private Precipitation(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static enum TemperatureModifier implements StringRepresentable {
        NONE("none") {
            @Override
            public float modifyTemperature(BlockPos pos, float temperature) {
                return temperature;
            }
        },
        FROZEN("frozen") {
            @Override
            public float modifyTemperature(BlockPos pos, float temperature) {
                double d = Biome.FROZEN_TEMPERATURE_NOISE.getValue((double)pos.getX() * 0.05, (double)pos.getZ() * 0.05, false) * 7.0;
                double e = Biome.BIOME_INFO_NOISE.getValue((double)pos.getX() * 0.2, (double)pos.getZ() * 0.2, false);
                double f = d + e;
                if (f < 0.3) {
                    double g = Biome.BIOME_INFO_NOISE.getValue((double)pos.getX() * 0.09, (double)pos.getZ() * 0.09, false);
                    if (g < 0.8) {
                        return 0.2F;
                    }
                }

                return temperature;
            }
        };

        private final String name;
        public static final Codec<Biome.TemperatureModifier> CODEC = StringRepresentable.fromEnum(Biome.TemperatureModifier::values);

        public abstract float modifyTemperature(BlockPos pos, float temperature);

        TemperatureModifier(final String name) {
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
