package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class PressurePlateBlock extends BasePressurePlateBlock {

    public static final MapCodec<PressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter((blockpressureplatebinary) -> {
            return blockpressureplatebinary.type;
        }), propertiesCodec()).apply(instance, PressurePlateBlock::new);
    });
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<PressurePlateBlock> codec() {
        return PressurePlateBlock.CODEC;
    }

    protected PressurePlateBlock(BlockSetType type, BlockBehaviour.Properties settings) {
        super(settings, type);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PressurePlateBlock.POWERED, false));
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return (Boolean) state.getValue(PressurePlateBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int rsOut) {
        return (BlockState) state.setValue(PressurePlateBlock.POWERED, rsOut > 0);
    }

    @Override
    protected int getSignalStrength(Level world, BlockPos pos) {
        Class<? extends Entity> oclass; // CraftBukkit

        switch (this.type.pressurePlateSensitivity()) {
            case EVERYTHING:
                oclass = Entity.class;
                break;
            case MOBS:
                oclass = LivingEntity.class;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Class<? extends Entity> oclass1 = oclass;

        // CraftBukkit start - Call interact event when turning on a pressure plate
        for (Entity entity : getEntities(world, PressurePlateBlock.TOUCH_AABB.move(pos), oclass)) {
            if (this.getSignalForState(world.getBlockState(pos)) == 0) {
                org.bukkit.World bworld = world.getWorld();
                org.bukkit.plugin.PluginManager manager = world.getCraftServer().getPluginManager();
                org.bukkit.event.Cancellable cancellable;

                if (entity instanceof Player) {
                    cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                } else {
                    cancellable = new EntityInteractEvent(entity.getBukkitEntity(), bworld.getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                    manager.callEvent((EntityInteractEvent) cancellable);
                }

                // We only want to block turning the plate on if all events are cancelled
                if (cancellable.isCancelled()) {
                    continue;
                }
            }

            return 15;
        }

        return 0;
        // CraftBukkit end
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PressurePlateBlock.POWERED);
    }
}
