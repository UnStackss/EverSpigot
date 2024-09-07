package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

public record FishingHookPredicate(Optional<Boolean> inOpenWater) implements EntitySubPredicate {
    public static final FishingHookPredicate ANY = new FishingHookPredicate(Optional.empty());
    public static final MapCodec<FishingHookPredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.BOOL.optionalFieldOf("in_open_water").forGetter(FishingHookPredicate::inOpenWater))
                .apply(instance, FishingHookPredicate::new)
    );

    public static FishingHookPredicate inOpenWater(boolean inOpenWater) {
        return new FishingHookPredicate(Optional.of(inOpenWater));
    }

    @Override
    public MapCodec<FishingHookPredicate> codec() {
        return EntitySubPredicates.FISHING_HOOK;
    }

    @Override
    public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
        return this.inOpenWater.isEmpty() || entity instanceof FishingHook fishingHook && this.inOpenWater.get() == fishingHook.isOpenWaterFishing();
    }
}
