package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class FireChargeItem extends Item implements ProjectileItem {

    public FireChargeItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);
        boolean flag = false;

        if (!CampfireBlock.canLight(iblockdata) && !CandleBlock.canLight(iblockdata) && !CandleCakeBlock.canLight(iblockdata)) {
            blockposition = blockposition.relative(context.getClickedFace());
            if (BaseFireBlock.canBePlacedAt(world, blockposition, context.getHorizontalDirection())) {
                // CraftBukkit start - fire BlockIgniteEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL, context.getPlayer()).isCancelled()) {
                    if (!context.getPlayer().getAbilities().instabuild) {
                        context.getItemInHand().shrink(1);
                    }
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                this.playSound(world, blockposition);
                world.setBlockAndUpdate(blockposition, BaseFireBlock.getState(world, blockposition));
                world.gameEvent((Entity) context.getPlayer(), (Holder) GameEvent.BLOCK_PLACE, blockposition);
                flag = true;
            }
        } else {
            // CraftBukkit start - fire BlockIgniteEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL, context.getPlayer()).isCancelled()) {
                if (!context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.playSound(world, blockposition);
            world.setBlockAndUpdate(blockposition, (BlockState) iblockdata.setValue(BlockStateProperties.LIT, true));
            world.gameEvent((Entity) context.getPlayer(), (Holder) GameEvent.BLOCK_CHANGE, blockposition);
            flag = true;
        }

        if (flag) {
            context.getItemInHand().shrink(1);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.FAIL;
        }
    }

    private void playSound(Level world, BlockPos pos) {
        RandomSource randomsource = world.getRandom();

        world.playSound((Player) null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        RandomSource randomsource = world.getRandom();
        double d0 = randomsource.triangle((double) direction.getStepX(), 0.11485000000000001D);
        double d1 = randomsource.triangle((double) direction.getStepY(), 0.11485000000000001D);
        double d2 = randomsource.triangle((double) direction.getStepZ(), 0.11485000000000001D);
        Vec3 vec3d = new Vec3(d0, d1, d2);
        SmallFireball entitysmallfireball = new SmallFireball(world, pos.x(), pos.y(), pos.z(), vec3d.normalize());

        entitysmallfireball.setItem(stack);
        return entitysmallfireball;
    }

    @Override
    public void shoot(Projectile entity, double x, double y, double z, float power, float uncertainty) {}

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder().positionFunction((sourceblock, enumdirection) -> {
            return DispenserBlock.getDispensePosition(sourceblock, 1.0D, Vec3.ZERO);
        }).uncertainty(6.6666665F).power(1.0F).overrideDispenseEvent(1018).build();
    }
}
