package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public interface DensityFunction {
    Codec<DensityFunction> DIRECT_CODEC = DensityFunctions.DIRECT_CODEC;
    Codec<Holder<DensityFunction>> CODEC = RegistryFileCodec.create(Registries.DENSITY_FUNCTION, DIRECT_CODEC);
    Codec<DensityFunction> HOLDER_HELPER_CODEC = CODEC.xmap(
        DensityFunctions.HolderHolder::new,
        function -> (Holder<DensityFunction>)(function instanceof DensityFunctions.HolderHolder holderHolder
                ? holderHolder.function()
                : new Holder.Direct<>(function))
    );

    double compute(DensityFunction.FunctionContext pos);

    void fillArray(double[] densities, DensityFunction.ContextProvider applier);

    DensityFunction mapAll(DensityFunction.Visitor visitor);

    double minValue();

    double maxValue();

    KeyDispatchDataCodec<? extends DensityFunction> codec();

    default DensityFunction clamp(double min, double max) {
        return new DensityFunctions.Clamp(this, min, max);
    }

    default DensityFunction abs() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
    }

    default DensityFunction square() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
    }

    default DensityFunction cube() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
    }

    public interface ContextProvider {
        DensityFunction.FunctionContext forIndex(int index);

        void fillAllDirectly(double[] densities, DensityFunction densityFunction);
    }

    public interface FunctionContext {
        int blockX();

        int blockY();

        int blockZ();

        default Blender getBlender() {
            return Blender.empty();
        }
    }

    public static record NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData, @Nullable NormalNoise noise) {
        public static final Codec<DensityFunction.NoiseHolder> CODEC = NormalNoise.NoiseParameters.CODEC
            .xmap(noiseData -> new DensityFunction.NoiseHolder((Holder<NormalNoise.NoiseParameters>)noiseData, null), DensityFunction.NoiseHolder::noiseData);

        public NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData) {
            this(noiseData, null);
        }

        public double getValue(double x, double y, double z) {
            return this.noise == null ? 0.0 : this.noise.getValue(x, y, z);
        }

        public double maxValue() {
            return this.noise == null ? 2.0 : this.noise.maxValue();
        }
    }

    public interface SimpleFunction extends DensityFunction {
        @Override
        default void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            applier.fillAllDirectly(densities, this);
        }

        @Override
        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(this);
        }
    }

    public static record SinglePointContext(@Override int blockX, @Override int blockY, @Override int blockZ) implements DensityFunction.FunctionContext {
    }

    public interface Visitor {
        DensityFunction apply(DensityFunction densityFunction);

        default DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseDensityFunction) {
            return noiseDensityFunction;
        }
    }
}
