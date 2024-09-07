package net.minecraft.advancements.critereon;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;

public record SlimePredicate(MinMaxBounds.Ints size) implements EntitySubPredicate {
    public static final MapCodec<SlimePredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("size", MinMaxBounds.Ints.ANY).forGetter(SlimePredicate::size))
                .apply(instance, SlimePredicate::new)
    );

    public static SlimePredicate sized(MinMaxBounds.Ints size) {
        return new SlimePredicate(size);
    }

    @Override
    public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
        return entity instanceof Slime slime && this.size.matches(slime.getSize());
    }

    @Override
    public MapCodec<SlimePredicate> codec() {
        return EntitySubPredicates.SLIME;
    }
}
