package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CrossbowItem extends ProjectileWeaponItem {
    private static final float MAX_CHARGE_DURATION = 1.25F;
    public static final int DEFAULT_RANGE = 8;
    private boolean startSoundPlayed = false;
    private boolean midLoadSoundPlayed = false;
    private static final float START_SOUND_PERCENT = 0.2F;
    private static final float MID_SOUND_PERCENT = 0.5F;
    private static final float ARROW_POWER = 3.15F;
    private static final float FIREWORK_POWER = 1.6F;
    public static final float MOB_ARROW_POWER = 1.6F;
    private static final CrossbowItem.ChargingSounds DEFAULT_SOUNDS = new CrossbowItem.ChargingSounds(
        Optional.of(SoundEvents.CROSSBOW_LOADING_START), Optional.of(SoundEvents.CROSSBOW_LOADING_MIDDLE), Optional.of(SoundEvents.CROSSBOW_LOADING_END)
    );

    public CrossbowItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return ARROW_OR_FIREWORK;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return ARROW_ONLY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        ChargedProjectiles chargedProjectiles = itemStack.get(DataComponents.CHARGED_PROJECTILES);
        if (chargedProjectiles != null && !chargedProjectiles.isEmpty()) {
            this.performShooting(world, user, hand, itemStack, getShootingPower(chargedProjectiles), 1.0F, null);
            return InteractionResultHolder.consume(itemStack);
        } else if (!user.getProjectile(itemStack).isEmpty()) {
            this.startSoundPlayed = false;
            this.midLoadSoundPlayed = false;
            user.startUsingItem(hand);
            return InteractionResultHolder.consume(itemStack);
        } else {
            return InteractionResultHolder.fail(itemStack);
        }
    }

    private static float getShootingPower(ChargedProjectiles stack) {
        return stack.contains(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        int i = this.getUseDuration(stack, user) - remainingUseTicks;
        float f = getPowerForTime(i, stack, user);
        // Paper start - Add EntityLoadCrossbowEvent
        if (f >= 1.0F && !isCharged(stack)) {
            final io.papermc.paper.event.entity.EntityLoadCrossbowEvent event = new io.papermc.paper.event.entity.EntityLoadCrossbowEvent(user.getBukkitLivingEntity(), stack.asBukkitMirror(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(user.getUsedItemHand()));
            if (!event.callEvent() || !tryLoadProjectiles(user, stack, event.shouldConsumeItem()) || !event.shouldConsumeItem()) {
                if (user instanceof ServerPlayer player) player.containerMenu.sendAllDataToRemote();
                return;
            }
            // Paper end - Add EntityLoadCrossbowEvent
            CrossbowItem.ChargingSounds chargingSounds = this.getChargingSounds(stack);
            chargingSounds.end()
                .ifPresent(
                    sound -> world.playSound(
                            null,
                            user.getX(),
                            user.getY(),
                            user.getZ(),
                            sound.value(),
                            user.getSoundSource(),
                            1.0F,
                            1.0F / (world.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F
                        )
                );
        }
    }

    @io.papermc.paper.annotation.DoNotUse // Paper - Add EntityLoadCrossbowEvent
    private static boolean tryLoadProjectiles(LivingEntity shooter, ItemStack crossbow)  {
        // Paper start - Add EntityLoadCrossbowEvent
        return CrossbowItem.tryLoadProjectiles(shooter, crossbow, true);
    }
    private static boolean tryLoadProjectiles(LivingEntity shooter, ItemStack crossbow, boolean consume) {
        List<ItemStack> list = draw(crossbow, shooter.getProjectile(crossbow), shooter, consume);
        // Paper end - Add EntityLoadCrossbowEvent
        if (!list.isEmpty()) {
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(list));
            return true;
        } else {
            return false;
        }
    }

    public static boolean isCharged(ItemStack stack) {
        ChargedProjectiles chargedProjectiles = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        return !chargedProjectiles.isEmpty();
    }

    @Override
    protected void shootProjectile(
        LivingEntity shooter, Projectile projectile, int index, float speed, float divergence, float yaw, @Nullable LivingEntity target
    ) {
        Vector3f vector3f;
        if (target != null) {
            double d = target.getX() - shooter.getX();
            double e = target.getZ() - shooter.getZ();
            double f = Math.sqrt(d * d + e * e);
            double g = target.getY(0.3333333333333333) - projectile.getY() + f * 0.2F;
            vector3f = getProjectileShotVector(shooter, new Vec3(d, g, e), yaw);
        } else {
            Vec3 vec3 = shooter.getUpVector(1.0F);
            Quaternionf quaternionf = new Quaternionf().setAngleAxis((double)(yaw * (float) (Math.PI / 180.0)), vec3.x, vec3.y, vec3.z);
            Vec3 vec32 = shooter.getViewVector(1.0F);
            vector3f = vec32.toVector3f().rotate(quaternionf);
        }

        projectile.shoot((double)vector3f.x(), (double)vector3f.y(), (double)vector3f.z(), speed, divergence);
        float h = getShotPitch(shooter.getRandom(), index);
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.CROSSBOW_SHOOT, shooter.getSoundSource(), 1.0F, h);
    }

    private static Vector3f getProjectileShotVector(LivingEntity shooter, Vec3 direction, float yaw) {
        Vector3f vector3f = direction.toVector3f().normalize();
        Vector3f vector3f2 = new Vector3f(vector3f).cross(new Vector3f(0.0F, 1.0F, 0.0F));
        if ((double)vector3f2.lengthSquared() <= 1.0E-7) {
            Vec3 vec3 = shooter.getUpVector(1.0F);
            vector3f2 = new Vector3f(vector3f).cross(vec3.toVector3f());
        }

        Vector3f vector3f3 = new Vector3f(vector3f).rotateAxis((float) (Math.PI / 2), vector3f2.x, vector3f2.y, vector3f2.z);
        return new Vector3f(vector3f).rotateAxis(yaw * (float) (Math.PI / 180.0), vector3f3.x, vector3f3.y, vector3f3.z);
    }

    @Override
    protected Projectile createProjectile(Level world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
        if (projectileStack.is(Items.FIREWORK_ROCKET)) {
            // Paper start
            FireworkRocketEntity entity =  new FireworkRocketEntity(world, projectileStack, shooter, shooter.getX(), shooter.getEyeY() - 0.15F, shooter.getZ(), true);
            entity.spawningEntity = shooter.getUUID(); // Paper
            return entity;
            // Paper end
        } else {
            Projectile projectile = super.createProjectile(world, shooter, weaponStack, projectileStack, critical);
            if (projectile instanceof AbstractArrow abstractArrow) {
                abstractArrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
            }

            return projectile;
        }
    }

    @Override
    protected int getDurabilityUse(ItemStack projectile) {
        return projectile.is(Items.FIREWORK_ROCKET) ? 3 : 1;
    }

    public void performShooting(
        Level world, LivingEntity shooter, InteractionHand hand, ItemStack stack, float speed, float divergence, @Nullable LivingEntity target
    ) {
        if (world instanceof ServerLevel serverLevel) {
            ChargedProjectiles chargedProjectiles = stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (chargedProjectiles != null && !chargedProjectiles.isEmpty()) {
                this.shoot(serverLevel, shooter, hand, stack, chargedProjectiles.getItems(), speed, divergence, shooter instanceof Player, target);
                if (shooter instanceof ServerPlayer serverPlayer) {
                    CriteriaTriggers.SHOT_CROSSBOW.trigger(serverPlayer, stack);
                    serverPlayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }
            }
        }
    }

    private static float getShotPitch(RandomSource random, int index) {
        return index == 0 ? 1.0F : getRandomShotPitch((index & 1) == 1, random);
    }

    private static float getRandomShotPitch(boolean flag, RandomSource random) {
        float f = flag ? 0.63F : 0.43F;
        return 1.0F / (random.nextFloat() * 0.5F + 1.8F) + f;
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClientSide) {
            CrossbowItem.ChargingSounds chargingSounds = this.getChargingSounds(stack);
            float f = (float)(stack.getUseDuration(user) - remainingUseTicks) / (float)getChargeDuration(stack, user);
            if (f < 0.2F) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
            }

            if (f >= 0.2F && !this.startSoundPlayed) {
                this.startSoundPlayed = true;
                chargingSounds.start()
                    .ifPresent(sound -> world.playSound(null, user.getX(), user.getY(), user.getZ(), sound.value(), SoundSource.PLAYERS, 0.5F, 1.0F));
            }

            if (f >= 0.5F && !this.midLoadSoundPlayed) {
                this.midLoadSoundPlayed = true;
                chargingSounds.mid()
                    .ifPresent(sound -> world.playSound(null, user.getX(), user.getY(), user.getZ(), sound.value(), SoundSource.PLAYERS, 0.5F, 1.0F));
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return getChargeDuration(stack, user) + 3;
    }

    public static int getChargeDuration(ItemStack stack, LivingEntity user) {
        float f = EnchantmentHelper.modifyCrossbowChargingTime(stack, user, 1.25F);
        return Mth.floor(f * 20.0F);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    CrossbowItem.ChargingSounds getChargingSounds(ItemStack stack) {
        return EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS).orElse(DEFAULT_SOUNDS);
    }

    private static float getPowerForTime(int useTicks, ItemStack stack, LivingEntity user) {
        float f = (float)useTicks / (float)getChargeDuration(stack, user);
        if (f > 1.0F) {
            f = 1.0F;
        }

        return f;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (chargedProjectiles != null && !chargedProjectiles.isEmpty()) {
            ItemStack itemStack = chargedProjectiles.getItems().get(0);
            tooltip.add(Component.translatable("item.minecraft.crossbow.projectile").append(CommonComponents.SPACE).append(itemStack.getDisplayName()));
            if (type.isAdvanced() && itemStack.is(Items.FIREWORK_ROCKET)) {
                List<Component> list = Lists.newArrayList();
                Items.FIREWORK_ROCKET.appendHoverText(itemStack, context, list, type);
                if (!list.isEmpty()) {
                    for (int i = 0; i < list.size(); i++) {
                        list.set(i, Component.literal("  ").append(list.get(i)).withStyle(ChatFormatting.GRAY));
                    }

                    tooltip.addAll(list);
                }
            }
        }
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return stack.is(this);
    }

    @Override
    public int getDefaultProjectileRange() {
        return 8;
    }

    public static record ChargingSounds(Optional<Holder<SoundEvent>> start, Optional<Holder<SoundEvent>> mid, Optional<Holder<SoundEvent>> end) {
        public static final Codec<CrossbowItem.ChargingSounds> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        SoundEvent.CODEC.optionalFieldOf("start").forGetter(CrossbowItem.ChargingSounds::start),
                        SoundEvent.CODEC.optionalFieldOf("mid").forGetter(CrossbowItem.ChargingSounds::mid),
                        SoundEvent.CODEC.optionalFieldOf("end").forGetter(CrossbowItem.ChargingSounds::end)
                    )
                    .apply(instance, CrossbowItem.ChargingSounds::new)
        );
    }
}
