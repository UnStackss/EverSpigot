package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.event.block.CauldronLevelChangeEvent;
// CraftBukkit end

public class LayeredCauldronBlock extends AbstractCauldronBlock {

    public static final MapCodec<LayeredCauldronBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Biome.Precipitation.CODEC.fieldOf("precipitation").forGetter((layeredcauldronblock) -> {
            return layeredcauldronblock.precipitationType;
        }), CauldronInteraction.CODEC.fieldOf("interactions").forGetter((layeredcauldronblock) -> {
            return layeredcauldronblock.interactions;
        }), propertiesCodec()).apply(instance, LayeredCauldronBlock::new);
    });
    public static final int MIN_FILL_LEVEL = 1;
    public static final int MAX_FILL_LEVEL = 3;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
    private static final int BASE_CONTENT_HEIGHT = 6;
    private static final double HEIGHT_PER_LEVEL = 3.0D;
    private final Biome.Precipitation precipitationType;

    @Override
    public MapCodec<LayeredCauldronBlock> codec() {
        return LayeredCauldronBlock.CODEC;
    }

    public LayeredCauldronBlock(Biome.Precipitation precipitation, CauldronInteraction.InteractionMap behaviorMap, BlockBehaviour.Properties settings) {
        super(settings, behaviorMap);
        this.precipitationType = precipitation;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LayeredCauldronBlock.LEVEL, 1));
    }

    @Override
    public boolean isFull(BlockState state) {
        return (Integer) state.getValue(LayeredCauldronBlock.LEVEL) == 3;
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return fluid == Fluids.WATER && this.precipitationType == Biome.Precipitation.RAIN;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return (6.0D + (double) (Integer) state.getValue(LayeredCauldronBlock.LEVEL) * 3.0D) / 16.0D;
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (!world.isClientSide && entity.isOnFire() && this.isEntityInsideContent(state, pos, entity)) {
            // CraftBukkit start
            if (entity.mayInteract(world, pos)) {
                if (!LayeredCauldronBlock.lowerFillLevel(state, world, pos, entity, CauldronLevelChangeEvent.ChangeReason.EXTINGUISH)) {
                    return;
                }
            }
            entity.clearFire();
            // CraftBukkit end
        }

    }

    private void handleEntityOnFireInside(BlockState state, Level world, BlockPos pos) {
        if (this.precipitationType == Biome.Precipitation.SNOW) {
            LayeredCauldronBlock.lowerFillLevel((BlockState) Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, (Integer) state.getValue(LayeredCauldronBlock.LEVEL)), world, pos);
        } else {
            LayeredCauldronBlock.lowerFillLevel(state, world, pos);
        }

    }

    public static void lowerFillLevel(BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        LayeredCauldronBlock.lowerFillLevel(state, world, pos, null, CauldronLevelChangeEvent.ChangeReason.UNKNOWN);
    }

    public static boolean lowerFillLevel(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity, CauldronLevelChangeEvent.ChangeReason reason) {
        int i = (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) - 1;
        BlockState iblockdata1 = i == 0 ? Blocks.CAULDRON.defaultBlockState() : (BlockState) iblockdata.setValue(LayeredCauldronBlock.LEVEL, i);

        return LayeredCauldronBlock.changeLevel(iblockdata, world, blockposition, iblockdata1, entity, reason);
    }

    // CraftBukkit start
    // Paper start - Call CauldronLevelChangeEvent
    public static boolean changeLevel(BlockState iblockdata, Level world, BlockPos blockposition, BlockState newBlock, @javax.annotation.Nullable Entity entity, CauldronLevelChangeEvent.ChangeReason reason) { // Paper - entity is nullable
        return changeLevel(iblockdata, world, blockposition, newBlock, entity, reason, true);
    }

    public static boolean changeLevel(BlockState iblockdata, Level world, BlockPos blockposition, BlockState newBlock, @javax.annotation.Nullable Entity entity, CauldronLevelChangeEvent.ChangeReason reason, boolean sendGameEvent) { // Paper - entity is nullable
    // Paper end - Call CauldronLevelChangeEvent
        CraftBlockState newState = CraftBlockStates.getBlockState(world, blockposition);
        newState.setData(newBlock);

        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()),
                (entity == null) ? null : entity.getBukkitEntity(), reason, newState
        );
        world.getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        newState.update(true);
        if (sendGameEvent) world.gameEvent((Holder) GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(newBlock)); // Paper - Call CauldronLevelChangeEvent
        return true;
    }
    // CraftBukkit end

    @Override
    public void handlePrecipitation(BlockState state, Level world, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(world, precipitation) && (Integer) state.getValue(LayeredCauldronBlock.LEVEL) != 3 && precipitation == this.precipitationType) {
            BlockState iblockdata1 = (BlockState) state.cycle(LayeredCauldronBlock.LEVEL);

            LayeredCauldronBlock.changeLevel(state, world, pos, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL); // CraftBukkit
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return (Integer) state.getValue(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level world, BlockPos pos, Fluid fluid) {
        if (!this.isFull(state)) {
            BlockState iblockdata1 = (BlockState) state.setValue(LayeredCauldronBlock.LEVEL, (Integer) state.getValue(LayeredCauldronBlock.LEVEL) + 1);

            // CraftBukkit start
            if (!LayeredCauldronBlock.changeLevel(state, world, pos, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) {
                return;
            }
            // CraftBukkit end
            world.levelEvent(1047, pos, 0);
        }
    }
}
