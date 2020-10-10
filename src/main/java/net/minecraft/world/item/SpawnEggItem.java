package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SpawnEggItem extends Item {

    private static final Map<EntityType<? extends Mob>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();
    private static final MapCodec<EntityType<?>> ENTITY_TYPE_FIELD_CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("id");
    private final int backgroundColor;
    private final int highlightColor;
    private final EntityType<?> defaultType;

    public SpawnEggItem(EntityType<? extends Mob> type, int primaryColor, int secondaryColor, Item.Properties settings) {
        super(settings);
        this.defaultType = type;
        this.backgroundColor = primaryColor;
        this.highlightColor = secondaryColor;
        SpawnEggItem.BY_ID.put(type, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();

        if (!(world instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemstack = context.getItemInHand();
            BlockPos blockposition = context.getClickedPos();
            Direction enumdirection = context.getClickedFace();
            BlockState iblockdata = world.getBlockState(blockposition);
            BlockEntity tileentity = world.getBlockEntity(blockposition);
            EntityType entitytypes;

            if (tileentity instanceof Spawner) {
                if (world.paperConfig().entities.spawning.disableMobSpawnerSpawnEggTransformation) return InteractionResult.FAIL; // Paper - Allow disabling mob spawner spawn egg transformation

                Spawner spawner = (Spawner) tileentity;

                entitytypes = this.getType(itemstack);
                spawner.setEntityId(entitytypes, world.getRandom());
                world.sendBlockUpdated(blockposition, iblockdata, iblockdata, 3);
                world.gameEvent((Entity) context.getPlayer(), (Holder) GameEvent.BLOCK_CHANGE, blockposition);
                itemstack.shrink(1);
                return InteractionResult.CONSUME;
            } else {
                BlockPos blockposition1;

                if (iblockdata.getCollisionShape(world, blockposition).isEmpty()) {
                    blockposition1 = blockposition;
                } else {
                    blockposition1 = blockposition.relative(enumdirection);
                }

                entitytypes = this.getType(itemstack);
                if (entitytypes.spawn((ServerLevel) world, itemstack, context.getPlayer(), blockposition1, MobSpawnType.SPAWN_EGG, true, !Objects.equals(blockposition, blockposition1) && enumdirection == Direction.UP) != null) {
                    itemstack.shrink(1);
                    world.gameEvent((Entity) context.getPlayer(), (Holder) GameEvent.ENTITY_PLACE, blockposition);
                }

                return InteractionResult.CONSUME;
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);
        BlockHitResult movingobjectpositionblock = getPlayerPOVHitResult(world, user, ClipContext.Fluid.SOURCE_ONLY);

        if (movingobjectpositionblock.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        } else if (!(world instanceof ServerLevel)) {
            return InteractionResultHolder.success(itemstack);
        } else {
            BlockPos blockposition = movingobjectpositionblock.getBlockPos();

            if (!(world.getBlockState(blockposition).getBlock() instanceof LiquidBlock)) {
                return InteractionResultHolder.pass(itemstack);
            } else if (world.mayInteract(user, blockposition) && user.mayUseItemAt(blockposition, movingobjectpositionblock.getDirection(), itemstack)) {
                EntityType<?> entitytypes = this.getType(itemstack);
                Entity entity = entitytypes.spawn((ServerLevel) world, itemstack, user, blockposition, MobSpawnType.SPAWN_EGG, false, false);

                if (entity == null) {
                    return InteractionResultHolder.pass(itemstack);
                } else {
                    itemstack.consume(1, user);
                    user.awardStat(Stats.ITEM_USED.get(this));
                    world.gameEvent((Entity) user, (Holder) GameEvent.ENTITY_PLACE, entity.position());
                    return InteractionResultHolder.consume(itemstack);
                }
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        }
    }

    public boolean spawnsEntity(ItemStack stack, EntityType<?> type) {
        return Objects.equals(this.getType(stack), type);
    }

    public int getColor(int tintIndex) {
        return tintIndex == 0 ? this.backgroundColor : this.highlightColor;
    }

    @Nullable
    public static SpawnEggItem byId(@Nullable EntityType<?> type) {
        return (SpawnEggItem) SpawnEggItem.BY_ID.get(type);
    }

    public static Iterable<SpawnEggItem> eggs() {
        return Iterables.unmodifiableIterable(SpawnEggItem.BY_ID.values());
    }

    public EntityType<?> getType(ItemStack stack) {
        CustomData customdata = (CustomData) stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);

        return !customdata.isEmpty() ? (EntityType) customdata.read(SpawnEggItem.ENTITY_TYPE_FIELD_CODEC).result().orElse(this.defaultType) : this.defaultType;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.defaultType.requiredFeatures();
    }

    public Optional<Mob> spawnOffspringFromSpawnEgg(Player user, Mob entity, EntityType<? extends Mob> entityType, ServerLevel world, Vec3 pos, ItemStack stack) {
        if (!this.spawnsEntity(stack, entityType)) {
            return Optional.empty();
        } else {
            Object object;

            if (entity instanceof AgeableMob) {
                object = ((AgeableMob) entity).getBreedOffspring(world, (AgeableMob) entity);
            } else {
                object = (Mob) entityType.create(world);
            }

            if (object == null) {
                return Optional.empty();
            } else {
                ((Mob) object).setBaby(true);
                if (!((Mob) object).isBaby()) {
                    return Optional.empty();
                } else {
                    ((Mob) object).moveTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
                    world.addFreshEntityWithPassengers((Entity) object, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG); // CraftBukkit
                    ((Mob) object).setCustomName((Component) stack.get(DataComponents.CUSTOM_NAME));
                    stack.consume(1, user);
                    return Optional.of((Mob) object); // CraftBukkit - decompile error
                }
            }
        }
    }
}
