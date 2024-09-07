package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class GiveGiftToHero extends Behavior<Villager> {
    private static final int THROW_GIFT_AT_DISTANCE = 5;
    private static final int MIN_TIME_BETWEEN_GIFTS = 600;
    private static final int MAX_TIME_BETWEEN_GIFTS = 6600;
    private static final int TIME_TO_DELAY_FOR_HEAD_TO_FINISH_TURNING = 20;
    private static final Map<VillagerProfession, ResourceKey<LootTable>> GIFTS = Util.make(Maps.newHashMap(), gifts -> {
        gifts.put(VillagerProfession.ARMORER, BuiltInLootTables.ARMORER_GIFT);
        gifts.put(VillagerProfession.BUTCHER, BuiltInLootTables.BUTCHER_GIFT);
        gifts.put(VillagerProfession.CARTOGRAPHER, BuiltInLootTables.CARTOGRAPHER_GIFT);
        gifts.put(VillagerProfession.CLERIC, BuiltInLootTables.CLERIC_GIFT);
        gifts.put(VillagerProfession.FARMER, BuiltInLootTables.FARMER_GIFT);
        gifts.put(VillagerProfession.FISHERMAN, BuiltInLootTables.FISHERMAN_GIFT);
        gifts.put(VillagerProfession.FLETCHER, BuiltInLootTables.FLETCHER_GIFT);
        gifts.put(VillagerProfession.LEATHERWORKER, BuiltInLootTables.LEATHERWORKER_GIFT);
        gifts.put(VillagerProfession.LIBRARIAN, BuiltInLootTables.LIBRARIAN_GIFT);
        gifts.put(VillagerProfession.MASON, BuiltInLootTables.MASON_GIFT);
        gifts.put(VillagerProfession.SHEPHERD, BuiltInLootTables.SHEPHERD_GIFT);
        gifts.put(VillagerProfession.TOOLSMITH, BuiltInLootTables.TOOLSMITH_GIFT);
        gifts.put(VillagerProfession.WEAPONSMITH, BuiltInLootTables.WEAPONSMITH_GIFT);
    });
    private static final float SPEED_MODIFIER = 0.5F;
    private int timeUntilNextGift = 600;
    private boolean giftGivenDuringThisRun;
    private long timeSinceStart;

    public GiveGiftToHero(int delay) {
        super(
            ImmutableMap.of(
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.INTERACTION_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryStatus.VALUE_PRESENT
            ),
            delay
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Villager entity) {
        if (!this.isHeroVisible(entity)) {
            return false;
        } else if (this.timeUntilNextGift > 0) {
            this.timeUntilNextGift--;
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected void start(ServerLevel serverLevel, Villager villager, long l) {
        this.giftGivenDuringThisRun = false;
        this.timeSinceStart = l;
        Player player = this.getNearestTargetableHero(villager).get();
        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, player);
        BehaviorUtils.lookAtEntity(villager, player);
    }

    @Override
    protected boolean canStillUse(ServerLevel serverLevel, Villager villager, long l) {
        return this.isHeroVisible(villager) && !this.giftGivenDuringThisRun;
    }

    @Override
    protected void tick(ServerLevel world, Villager entity, long time) {
        Player player = this.getNearestTargetableHero(entity).get();
        BehaviorUtils.lookAtEntity(entity, player);
        if (this.isWithinThrowingDistance(entity, player)) {
            if (time - this.timeSinceStart > 20L) {
                this.throwGift(entity, player);
                this.giftGivenDuringThisRun = true;
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(entity, player, 0.5F, 5);
        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Villager villager, long l) {
        this.timeUntilNextGift = calculateTimeUntilNextGift(serverLevel);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void throwGift(Villager villager, LivingEntity recipient) {
        for (ItemStack itemStack : this.getItemToThrow(villager)) {
            BehaviorUtils.throwItem(villager, itemStack, recipient.position());
        }
    }

    private List<ItemStack> getItemToThrow(Villager villager) {
        if (villager.isBaby()) {
            return ImmutableList.of(new ItemStack(Items.POPPY));
        } else {
            VillagerProfession villagerProfession = villager.getVillagerData().getProfession();
            if (GIFTS.containsKey(villagerProfession)) {
                LootTable lootTable = villager.level().getServer().reloadableRegistries().getLootTable(GIFTS.get(villagerProfession));
                LootParams lootParams = new LootParams.Builder((ServerLevel)villager.level())
                    .withParameter(LootContextParams.ORIGIN, villager.position())
                    .withParameter(LootContextParams.THIS_ENTITY, villager)
                    .create(LootContextParamSets.GIFT);
                return lootTable.getRandomItems(lootParams);
            } else {
                return ImmutableList.of(new ItemStack(Items.WHEAT_SEEDS));
            }
        }
    }

    private boolean isHeroVisible(Villager villager) {
        return this.getNearestTargetableHero(villager).isPresent();
    }

    private Optional<Player> getNearestTargetableHero(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER).filter(this::isHero);
    }

    private boolean isHero(Player player) {
        return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    private boolean isWithinThrowingDistance(Villager villager, Player player) {
        BlockPos blockPos = player.blockPosition();
        BlockPos blockPos2 = villager.blockPosition();
        return blockPos2.closerThan(blockPos, 5.0);
    }

    private static int calculateTimeUntilNextGift(ServerLevel world) {
        return 600 + world.random.nextInt(6001);
    }
}
