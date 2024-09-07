package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworkExplosionFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetFireworkExplosionFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        FireworkExplosion.Shape.CODEC.optionalFieldOf("shape").forGetter(function -> function.shape),
                        FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("colors").forGetter(function -> function.colors),
                        FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("fade_colors").forGetter(function -> function.fadeColors),
                        Codec.BOOL.optionalFieldOf("trail").forGetter(function -> function.trail),
                        Codec.BOOL.optionalFieldOf("twinkle").forGetter(function -> function.twinkle)
                    )
                )
                .apply(instance, SetFireworkExplosionFunction::new)
    );
    public static final FireworkExplosion DEFAULT_VALUE = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);
    final Optional<FireworkExplosion.Shape> shape;
    final Optional<IntList> colors;
    final Optional<IntList> fadeColors;
    final Optional<Boolean> trail;
    final Optional<Boolean> twinkle;

    public SetFireworkExplosionFunction(
        List<LootItemCondition> conditions,
        Optional<FireworkExplosion.Shape> shape,
        Optional<IntList> colors,
        Optional<IntList> fadeColors,
        Optional<Boolean> trail,
        Optional<Boolean> twinkle
    ) {
        super(conditions);
        this.shape = shape;
        this.colors = colors;
        this.fadeColors = fadeColors;
        this.trail = trail;
        this.twinkle = twinkle;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.FIREWORK_EXPLOSION, DEFAULT_VALUE, this::apply);
        return stack;
    }

    private FireworkExplosion apply(FireworkExplosion current) {
        return new FireworkExplosion(
            this.shape.orElseGet(current::shape),
            this.colors.orElseGet(current::colors),
            this.fadeColors.orElseGet(current::fadeColors),
            this.trail.orElseGet(current::hasTrail),
            this.twinkle.orElseGet(current::hasTwinkle)
        );
    }

    @Override
    public LootItemFunctionType<SetFireworkExplosionFunction> getType() {
        return LootItemFunctions.SET_FIREWORK_EXPLOSION;
    }
}
