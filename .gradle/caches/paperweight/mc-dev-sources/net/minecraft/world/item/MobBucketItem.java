package net.minecraft.world.item;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;

public class MobBucketItem extends BucketItem {
    private static final MapCodec<TropicalFish.Variant> VARIANT_FIELD_CODEC = TropicalFish.Variant.CODEC.fieldOf("BucketVariantTag");
    private final EntityType<?> type;
    private final SoundEvent emptySound;

    public MobBucketItem(EntityType<?> type, Fluid fluid, SoundEvent emptyingSound, Item.Properties settings) {
        super(fluid, settings);
        this.type = type;
        this.emptySound = emptyingSound;
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level world, ItemStack stack, BlockPos pos) {
        if (world instanceof ServerLevel) {
            this.spawn((ServerLevel)world, stack, pos);
            world.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        }
    }

    @Override
    protected void playEmptySound(@Nullable Player player, LevelAccessor world, BlockPos pos) {
        world.playSound(player, pos, this.emptySound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private void spawn(ServerLevel world, ItemStack stack, BlockPos pos) {
        if (this.type.spawn(world, stack, null, pos, MobSpawnType.BUCKET, true, false) instanceof Bucketable bucketable) {
            CustomData customData = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            bucketable.loadFromBucketTag(customData.copyTag());
            bucketable.setFromBucket(true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        if (this.type == EntityType.TROPICAL_FISH) {
            CustomData customData = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            if (customData.isEmpty()) {
                return;
            }

            Optional<TropicalFish.Variant> optional = customData.read(VARIANT_FIELD_CODEC).result();
            if (optional.isPresent()) {
                TropicalFish.Variant variant = optional.get();
                ChatFormatting[] chatFormattings = new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.GRAY};
                String string = "color.minecraft." + variant.baseColor();
                String string2 = "color.minecraft." + variant.patternColor();
                int i = TropicalFish.COMMON_VARIANTS.indexOf(variant);
                if (i != -1) {
                    tooltip.add(Component.translatable(TropicalFish.getPredefinedName(i)).withStyle(chatFormattings));
                    return;
                }

                tooltip.add(variant.pattern().displayName().plainCopy().withStyle(chatFormattings));
                MutableComponent mutableComponent = Component.translatable(string);
                if (!string.equals(string2)) {
                    mutableComponent.append(", ").append(Component.translatable(string2));
                }

                mutableComponent.withStyle(chatFormattings);
                tooltip.add(mutableComponent);
            }
        }
    }
}
