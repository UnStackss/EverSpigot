package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CartographyTableBlock extends Block {
    public static final MapCodec<CartographyTableBlock> CODEC = simpleCodec(CartographyTableBlock::new);
    private static final Component CONTAINER_TITLE = Component.translatable("container.cartography_table");

    @Override
    public MapCodec<CartographyTableBlock> codec() {
        return CODEC;
    }

    protected CartographyTableBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else if (player.openMenu(state.getMenuProvider(world, pos)).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(Stats.INTERACT_WITH_CARTOGRAPHY_TABLE);
        }
        return InteractionResult.CONSUME; // Paper - Fix InventoryOpenEvent cancellation
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        return new SimpleMenuProvider(
            (syncId, inventory, player) -> new CartographyTableMenu(syncId, inventory, ContainerLevelAccess.create(world, pos)), CONTAINER_TITLE
        );
    }
}
