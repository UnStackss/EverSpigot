package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

public record FluidPredicate(Optional<HolderSet<Fluid>> fluids, Optional<StatePropertiesPredicate> properties) {
    public static final Codec<FluidPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.FLUID).optionalFieldOf("fluids").forGetter(FluidPredicate::fluids),
                    StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(FluidPredicate::properties)
                )
                .apply(instance, FluidPredicate::new)
    );

    public boolean matches(ServerLevel world, BlockPos pos) {
        if (!world.isLoaded(pos)) {
            return false;
        } else {
            FluidState fluidState = world.getFluidState(pos);
            return (!this.fluids.isPresent() || fluidState.is(this.fluids.get()))
                && (!this.properties.isPresent() || this.properties.get().matches(fluidState));
        }
    }

    public static class Builder {
        private Optional<HolderSet<Fluid>> fluids = Optional.empty();
        private Optional<StatePropertiesPredicate> properties = Optional.empty();

        private Builder() {
        }

        public static FluidPredicate.Builder fluid() {
            return new FluidPredicate.Builder();
        }

        public FluidPredicate.Builder of(Fluid fluid) {
            this.fluids = Optional.of(HolderSet.direct(fluid.builtInRegistryHolder()));
            return this;
        }

        public FluidPredicate.Builder of(HolderSet<Fluid> tag) {
            this.fluids = Optional.of(tag);
            return this;
        }

        public FluidPredicate.Builder setProperties(StatePropertiesPredicate state) {
            this.properties = Optional.of(state);
            return this;
        }

        public FluidPredicate build() {
            return new FluidPredicate(this.fluids, this.properties);
        }
    }
}
