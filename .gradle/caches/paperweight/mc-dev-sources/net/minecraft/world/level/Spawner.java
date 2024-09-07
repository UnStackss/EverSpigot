package net.minecraft.world.level;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public interface Spawner {
    void setEntityId(EntityType<?> type, RandomSource random);

    static void appendHoverText(ItemStack stack, List<Component> tooltip, String spawnDataKey) {
        Component component = getSpawnEntityDisplayName(stack, spawnDataKey);
        if (component != null) {
            tooltip.add(component);
        } else {
            tooltip.add(CommonComponents.EMPTY);
            tooltip.add(Component.translatable("block.minecraft.spawner.desc1").withStyle(ChatFormatting.GRAY));
            tooltip.add(CommonComponents.space().append(Component.translatable("block.minecraft.spawner.desc2").withStyle(ChatFormatting.BLUE)));
        }
    }

    @Nullable
    static Component getSpawnEntityDisplayName(ItemStack stack, String spawnDataKey) {
        CompoundTag compoundTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).getUnsafe();
        ResourceLocation resourceLocation = getEntityKey(compoundTag, spawnDataKey);
        return resourceLocation != null
            ? BuiltInRegistries.ENTITY_TYPE
                .getOptional(resourceLocation)
                .map(entityType -> Component.translatable(entityType.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                .orElse(null)
            : null;
    }

    @Nullable
    private static ResourceLocation getEntityKey(CompoundTag nbt, String spawnDataKey) {
        if (nbt.contains(spawnDataKey, 10)) {
            String string = nbt.getCompound(spawnDataKey).getCompound("entity").getString("id");
            return ResourceLocation.tryParse(string);
        } else {
            return null;
        }
    }
}
