package net.minecraft.advancements;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CacheableFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record AdvancementRewards(int experience, List<ResourceKey<LootTable>> loot, List<ResourceLocation> recipes, Optional<CacheableFunction> function) {
    public static final Codec<AdvancementRewards> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Codec.INT.optionalFieldOf("experience", Integer.valueOf(0)).forGetter(AdvancementRewards::experience),
                    ResourceKey.codec(Registries.LOOT_TABLE).listOf().optionalFieldOf("loot", List.of()).forGetter(AdvancementRewards::loot),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(AdvancementRewards::recipes),
                    CacheableFunction.CODEC.optionalFieldOf("function").forGetter(AdvancementRewards::function)
                )
                .apply(instance, AdvancementRewards::new)
    );
    public static final AdvancementRewards EMPTY = new AdvancementRewards(0, List.of(), List.of(), Optional.empty());

    public void grant(ServerPlayer player) {
        player.giveExperiencePoints(this.experience);
        LootParams lootParams = new LootParams.Builder(player.serverLevel())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .withParameter(LootContextParams.ORIGIN, player.position())
            .create(LootContextParamSets.ADVANCEMENT_REWARD);
        boolean bl = false;

        for (ResourceKey<LootTable> resourceKey : this.loot) {
            for (ItemStack itemStack : player.server.reloadableRegistries().getLootTable(resourceKey).getRandomItems(lootParams)) {
                if (player.addItem(itemStack)) {
                    player.level()
                        .playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.ITEM_PICKUP,
                            SoundSource.PLAYERS,
                            0.2F,
                            ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                        );
                    bl = true;
                } else {
                    ItemEntity itemEntity = player.drop(itemStack, false);
                    if (itemEntity != null) {
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setTarget(player.getUUID());
                    }
                }
            }
        }

        if (bl) {
            player.containerMenu.broadcastChanges();
        }

        if (!this.recipes.isEmpty()) {
            player.awardRecipesByKey(this.recipes);
        }

        MinecraftServer minecraftServer = player.server;
        this.function
            .flatMap(function -> function.get(minecraftServer.getFunctions()))
            .ifPresent(
                function -> minecraftServer.getFunctions()
                        .execute((CommandFunction<CommandSourceStack>)function, player.createCommandSourceStack().withSuppressedOutput().withPermission(2))
            );
    }

    public static class Builder {
        private int experience;
        private final ImmutableList.Builder<ResourceKey<LootTable>> loot = ImmutableList.builder();
        private final ImmutableList.Builder<ResourceLocation> recipes = ImmutableList.builder();
        private Optional<ResourceLocation> function = Optional.empty();

        public static AdvancementRewards.Builder experience(int experience) {
            return new AdvancementRewards.Builder().addExperience(experience);
        }

        public AdvancementRewards.Builder addExperience(int experience) {
            this.experience += experience;
            return this;
        }

        public static AdvancementRewards.Builder loot(ResourceKey<LootTable> loot) {
            return new AdvancementRewards.Builder().addLootTable(loot);
        }

        public AdvancementRewards.Builder addLootTable(ResourceKey<LootTable> loot) {
            this.loot.add(loot);
            return this;
        }

        public static AdvancementRewards.Builder recipe(ResourceLocation recipe) {
            return new AdvancementRewards.Builder().addRecipe(recipe);
        }

        public AdvancementRewards.Builder addRecipe(ResourceLocation recipe) {
            this.recipes.add(recipe);
            return this;
        }

        public static AdvancementRewards.Builder function(ResourceLocation function) {
            return new AdvancementRewards.Builder().runs(function);
        }

        public AdvancementRewards.Builder runs(ResourceLocation function) {
            this.function = Optional.of(function);
            return this;
        }

        public AdvancementRewards build() {
            return new AdvancementRewards(this.experience, this.loot.build(), this.recipes.build(), this.function.map(CacheableFunction::new));
        }
    }
}
