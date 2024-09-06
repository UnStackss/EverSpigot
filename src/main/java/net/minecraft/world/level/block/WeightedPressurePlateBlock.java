package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {

    public static final MapCodec<WeightedPressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.intRange(1, 1024).fieldOf("max_weight").forGetter((blockpressureplateweighted) -> {
            return blockpressureplateweighted.maxWeight;
        }), BlockSetType.CODEC.fieldOf("block_set_type").forGetter((blockpressureplateweighted) -> {
            return blockpressureplateweighted.type;
        }), propertiesCodec()).apply(instance, WeightedPressurePlateBlock::new);
    });
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;

    @Override
    public MapCodec<WeightedPressurePlateBlock> codec() {
        return WeightedPressurePlateBlock.CODEC;
    }

    protected WeightedPressurePlateBlock(int weight, BlockSetType type, BlockBehaviour.Properties settings) {
        super(settings, type);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(WeightedPressurePlateBlock.POWER, 0));
        this.maxWeight = weight;
    }

    @Override
    protected int getSignalStrength(Level world, BlockPos pos) {
        // CraftBukkit start
        // int i = Math.min(getEntityCount(world, BlockPressurePlateWeighted.TOUCH_AABB.move(blockposition), Entity.class), this.maxWeight);
        int i = 0;
        for (Entity entity : getEntities(world, WeightedPressurePlateBlock.TOUCH_AABB.move(pos), Entity.class)) {
            org.bukkit.event.Cancellable cancellable;

            if (entity instanceof Player) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                world.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            // We only want to block turning the plate on if all events are cancelled
            if (!cancellable.isCancelled()) {
                i++;
            }
        }

        i = Math.min(i, this.maxWeight);
        // CraftBukkit end

        if (i > 0) {
            float f = (float) Math.min(this.maxWeight, i) / (float) this.maxWeight;

            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return (Integer) state.getValue(WeightedPressurePlateBlock.POWER);
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int rsOut) {
        return (BlockState) state.setValue(WeightedPressurePlateBlock.POWER, rsOut);
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WeightedPressurePlateBlock.POWER);
    }
}
