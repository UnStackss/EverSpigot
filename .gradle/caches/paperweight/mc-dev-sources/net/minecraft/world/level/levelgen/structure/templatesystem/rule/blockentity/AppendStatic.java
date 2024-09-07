package net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

public class AppendStatic implements RuleBlockEntityModifier {
    public static final MapCodec<AppendStatic> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(CompoundTag.CODEC.fieldOf("data").forGetter(modifier -> modifier.tag)).apply(instance, AppendStatic::new)
    );
    private final CompoundTag tag;

    public AppendStatic(CompoundTag nbt) {
        this.tag = nbt;
    }

    @Override
    public CompoundTag apply(RandomSource random, @Nullable CompoundTag nbt) {
        return nbt == null ? this.tag.copy() : nbt.merge(this.tag);
    }

    @Override
    public RuleBlockEntityModifierType<?> getType() {
        return RuleBlockEntityModifierType.APPEND_STATIC;
    }
}
