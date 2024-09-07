package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TradeWithVillager extends Behavior<Villager> {
    private Set<Item> trades = ImmutableSet.of();

    public TradeWithVillager() {
        super(
            ImmutableMap.of(
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
            )
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Villager entity) {
        return BehaviorUtils.targetIsValid(entity.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Villager entity, long time) {
        return this.checkExtraStartConditions(world, entity);
    }

    @Override
    protected void start(ServerLevel serverLevel, Villager villager, long l) {
        Villager villager2 = (Villager)villager.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(villager, villager2, 0.5F, 2);
        this.trades = figureOutWhatIAmWillingToTrade(villager, villager2);
    }

    @Override
    protected void tick(ServerLevel world, Villager entity, long time) {
        Villager villager = (Villager)entity.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        if (!(entity.distanceToSqr(villager) > 5.0)) {
            BehaviorUtils.lockGazeAndWalkToEachOther(entity, villager, 0.5F, 2);
            entity.gossip(world, villager, time);
            if (entity.hasExcessFood() && (entity.getVillagerData().getProfession() == VillagerProfession.FARMER || villager.wantsMoreFood())) {
                throwHalfStack(entity, Villager.FOOD_POINTS.keySet(), villager);
            }

            if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER
                && entity.getInventory().countItem(Items.WHEAT) > Items.WHEAT.getDefaultMaxStackSize() / 2) {
                throwHalfStack(entity, ImmutableSet.of(Items.WHEAT), villager);
            }

            if (!this.trades.isEmpty() && entity.getInventory().hasAnyOf(this.trades)) {
                throwHalfStack(entity, this.trades, villager);
            }
        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Villager villager, long l) {
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    private static Set<Item> figureOutWhatIAmWillingToTrade(Villager entity, Villager target) {
        ImmutableSet<Item> immutableSet = target.getVillagerData().getProfession().requestedItems();
        ImmutableSet<Item> immutableSet2 = entity.getVillagerData().getProfession().requestedItems();
        return immutableSet.stream().filter(item -> !immutableSet2.contains(item)).collect(Collectors.toSet());
    }

    private static void throwHalfStack(Villager villager, Set<Item> validItems, LivingEntity target) {
        SimpleContainer simpleContainer = villager.getInventory();
        ItemStack itemStack = ItemStack.EMPTY;
        int i = 0;

        while (i < simpleContainer.getContainerSize()) {
            ItemStack itemStack2;
            Item item;
            int j;
            label28: {
                itemStack2 = simpleContainer.getItem(i);
                if (!itemStack2.isEmpty()) {
                    item = itemStack2.getItem();
                    if (validItems.contains(item)) {
                        if (itemStack2.getCount() > itemStack2.getMaxStackSize() / 2) {
                            j = itemStack2.getCount() / 2;
                            break label28;
                        }

                        if (itemStack2.getCount() > 24) {
                            j = itemStack2.getCount() - 24;
                            break label28;
                        }
                    }
                }

                i++;
                continue;
            }

            itemStack2.shrink(j);
            itemStack = new ItemStack(item, j);
            break;
        }

        if (!itemStack.isEmpty()) {
            BehaviorUtils.throwItem(villager, itemStack, target.position());
        }
    }
}
