package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public class WindChargeItem extends Item implements ProjectileItem {
    private static final int COOLDOWN = 10;

    public WindChargeItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand); // Paper - PlayerLaunchProjectileEvent; moved from below
        boolean shouldConsume = true; // Paper - PlayerLaunchProjectileEvent
        if (!world.isClientSide()) {
            WindCharge windCharge = new WindCharge(user, world, user.position().x(), user.getEyePosition().y(), user.position().z());
            windCharge.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 1.0F);
            // Paper start - PlayerLaunchProjectileEvent
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) user.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack), (org.bukkit.entity.Projectile) windCharge.getBukkitEntity());
            if (!event.callEvent() || !world.addFreshEntity(windCharge)) {
                user.containerMenu.sendAllDataToRemote();
                if (user instanceof net.minecraft.server.level.ServerPlayer player) {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundCooldownPacket(this, 0)); // prevent visual desync of cooldown on the slot
                }
                return InteractionResultHolder.fail(itemStack);
            }

            shouldConsume = event.shouldConsume();
            if (!shouldConsume && !user.hasInfiniteMaterials()) {
                user.containerMenu.sendAllDataToRemote();
            }
            // Paper end - PlayerLaunchProjectileEvent
        }

        world.playSound(
            null,
            user.getX(),
            user.getY(),
            user.getZ(),
            SoundEvents.WIND_CHARGE_THROW,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        user.getCooldowns().addCooldown(this, 10);
        user.awardStat(Stats.ITEM_USED.get(this));
        if (shouldConsume) itemStack.consume(1, user); // Paper - PlayerLaunchProjectileEvent
        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        RandomSource randomSource = world.getRandom();
        double d = randomSource.triangle((double)direction.getStepX(), 0.11485000000000001);
        double e = randomSource.triangle((double)direction.getStepY(), 0.11485000000000001);
        double f = randomSource.triangle((double)direction.getStepZ(), 0.11485000000000001);
        Vec3 vec3 = new Vec3(d, e, f);
        WindCharge windCharge = new WindCharge(world, pos.x(), pos.y(), pos.z(), vec3);
        windCharge.setDeltaMovement(vec3);
        return windCharge;
    }

    @Override
    public void shoot(Projectile entity, double x, double y, double z, float power, float uncertainty) {
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction((pointer, facing) -> DispenserBlock.getDispensePosition(pointer, 1.0, Vec3.ZERO))
            .uncertainty(6.6666665F)
            .power(1.0F)
            .overrideDispenseEvent(1051)
            .build();
    }
}
