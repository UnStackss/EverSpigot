package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SmithingTableBlock extends CraftingTableBlock {
    public static final MapCodec<SmithingTableBlock> CODEC = simpleCodec(SmithingTableBlock::new);
    private static final Component CONTAINER_TITLE = Component.translatable("container.upgrade");

    @Override
    public MapCodec<SmithingTableBlock> codec() {
        return CODEC;
    }

    protected SmithingTableBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        return new SimpleMenuProvider(
            (syncId, inventory, player) -> new SmithingMenu(syncId, inventory, ContainerLevelAccess.create(world, pos)), CONTAINER_TITLE
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else if (player.openMenu(state.getMenuProvider(world, pos)).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(Stats.INTERACT_WITH_SMITHING_TABLE);
        }
        return InteractionResult.CONSUME; // Paper - Fix InventoryOpenEvent cancellation
    }
}
