package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class SignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final float AABB_OFFSET = 4.0F;
    protected static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private final WoodType type;

    protected SignBlock(WoodType type, BlockBehaviour.Properties settings) {
        super(settings);
        this.type = type;
    }

    @Override
    protected abstract MapCodec<? extends SignBlock> codec();

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(SignBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SignBlock.SHAPE;
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof SignBlockEntity tileentitysign) {
            Item item = stack.getItem();
            SignApplicator signapplicator;

            if (item instanceof SignApplicator signapplicator1) {
                signapplicator = signapplicator1;
            } else {
                signapplicator = null;
            }

            SignApplicator signapplicator2 = signapplicator;
            boolean flag = signapplicator2 != null && player.mayBuild();

            if (!world.isClientSide) {
                if (flag && !tileentitysign.isWaxed() && !this.otherPlayerIsEditingSign(player, tileentitysign)) {
                    boolean flag1 = tileentitysign.isFacingFrontText(player);

                    if (signapplicator2.canApplyToSign(tileentitysign.getText(flag1), player) && signapplicator2.tryApplyToSign(world, tileentitysign, flag1, player)) {
                        tileentitysign.executeClickCommandsIfPresent(player, world, pos, flag1);
                        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                        world.gameEvent((Holder) GameEvent.BLOCK_CHANGE, tileentitysign.getBlockPos(), GameEvent.Context.of(player, tileentitysign.getBlockState()));
                        stack.consume(1, player);
                        return ItemInteractionResult.SUCCESS;
                    } else {
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    }
                } else {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            } else {
                return !flag && !tileentitysign.isWaxed() ? ItemInteractionResult.CONSUME : ItemInteractionResult.SUCCESS;
            }
        } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof SignBlockEntity tileentitysign) {
            if (world.isClientSide) {
                Util.pauseInIde(new IllegalStateException("Expected to only call this on server"));
            }

            boolean flag = tileentitysign.isFacingFrontText(player);
            boolean flag1 = tileentitysign.executeClickCommandsIfPresent(player, world, pos, flag);

            if (tileentitysign.isWaxed()) {
                world.playSound((Player) null, tileentitysign.getBlockPos(), tileentitysign.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
                return InteractionResult.SUCCESS;
            } else if (flag1) {
                return InteractionResult.SUCCESS;
            } else if (!this.otherPlayerIsEditingSign(player, tileentitysign) && player.mayBuild() && this.hasEditableText(player, tileentitysign, flag)) {
                this.openTextEdit(player, tileentitysign, flag, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.INTERACT); // Paper - Add PlayerOpenSignEvent
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private boolean hasEditableText(Player player, SignBlockEntity blockEntity, boolean front) {
        SignText signtext = blockEntity.getText(front);

        return Arrays.stream(signtext.getMessages(player.isTextFilteringEnabled())).allMatch((ichatbasecomponent) -> {
            return ichatbasecomponent.equals(CommonComponents.EMPTY) || ichatbasecomponent.getContents() instanceof PlainTextContents;
        });
    }

    public abstract float getYRotationDegrees(BlockState state);

    public Vec3 getSignHitboxCenterPosition(BlockState state) {
        return new Vec3(0.5D, 0.5D, 0.5D);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SignBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    public WoodType type() {
        return this.type;
    }

    public static WoodType getWoodType(Block block) {
        WoodType blockpropertywood;

        if (block instanceof SignBlock) {
            blockpropertywood = ((SignBlock) block).type();
        } else {
            blockpropertywood = WoodType.OAK;
        }

        return blockpropertywood;
    }

    @io.papermc.paper.annotation.DoNotUse @Deprecated // Paper - Add PlayerOpenSignEvent
    public void openTextEdit(Player player, SignBlockEntity blockEntity, boolean front) {
        // Paper start - Add PlayerOpenSignEvent
        this.openTextEdit(player, blockEntity, front, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.UNKNOWN);
    }
    public void openTextEdit(Player entityhuman, SignBlockEntity tileentitysign, boolean flag, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause cause) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) entityhuman.getBukkitEntity();
        org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(tileentitysign.getLevel(), tileentitysign.getBlockPos());
        org.bukkit.craftbukkit.block.CraftSign<?> bukkitSign = (org.bukkit.craftbukkit.block.CraftSign<?>) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(bukkitBlock);
        io.papermc.paper.event.player.PlayerOpenSignEvent event = new io.papermc.paper.event.player.PlayerOpenSignEvent(
            bukkitPlayer,
            bukkitSign,
            flag ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK,
            cause);
        if (!event.callEvent()) return;
        if (org.bukkit.event.player.PlayerSignOpenEvent.getHandlerList().getRegisteredListeners().length > 0) {
            final org.bukkit.event.player.PlayerSignOpenEvent.Cause legacyCause = switch (cause) {
                case PLACE -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.PLACE;
                case PLUGIN -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.PLUGIN;
                case INTERACT -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.INTERACT;
                case UNKNOWN -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.UNKNOWN;
            };
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerSignOpenEvent(entityhuman, tileentitysign, flag, legacyCause)) {
        // Paper end - Add PlayerOpenSignEvent
            return;
        }
        } // Paper - Add PlayerOpenSignEvent
        tileentitysign.setAllowedPlayerEditor(entityhuman.getUUID());
        entityhuman.openTextEdit(tileentitysign, flag);
    }

    private boolean otherPlayerIsEditingSign(Player player, SignBlockEntity blockEntity) {
        UUID uuid = blockEntity.getPlayerWhoMayEdit();

        return uuid != null && !uuid.equals(player.getUUID());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return null; // Craftbukkit - remove unnecessary sign ticking
    }
}
