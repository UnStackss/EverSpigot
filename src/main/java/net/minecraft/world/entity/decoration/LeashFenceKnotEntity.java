package net.minecraft.world.entity.decoration;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class LeashFenceKnotEntity extends BlockAttachedEntity {

    public static final double OFFSET_Y = 0.375D;

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> type, Level world) {
        super(type, world);
    }

    public LeashFenceKnotEntity(Level world, BlockPos pos) {
        super(EntityType.LEASH_KNOT, world, pos);
        this.setPos((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.375D, (double) this.pos.getZ() + 0.5D);
        double d0 = (double) this.getType().getWidth() / 2.0D;
        double d1 = (double) this.getType().getHeight();

        this.setBoundingBox(new AABB(this.getX() - d0, this.getY(), this.getZ() - d0, this.getX() + d0, this.getY() + d1, this.getZ() + d0));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0D;
    }

    @Override
    public void dropItem(@Nullable Entity breaker) {
        this.playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {}

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {}

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            boolean flag = false;
            List<Leashable> list = LeadItem.leashableInArea(this.level(), this.getPos(), (leashable) -> {
                Entity entity = leashable.getLeashHolder();

                return entity == player || entity == this;
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Leashable leashable = (Leashable) iterator.next();

                if (leashable.getLeashHolder() == player) {
                    // CraftBukkit start
                    if (leashable instanceof Entity leashed) {
                        if (CraftEventFactory.callPlayerLeashEntityEvent(leashed, this, player, hand).isCancelled()) {
                            ((ServerPlayer) player).connection.send(new ClientboundSetEntityLinkPacket(leashed, leashable.getLeashHolder()));
                            flag = true; // Also set true when the event is cancelled otherwise it tries to unleash the entities
                            continue;
                        }
                    }
                    // CraftBukkit end
                    leashable.setLeashedTo(this, true);
                    flag = true;
                }
            }

            boolean flag1 = false;

            if (!flag) {
                // CraftBukkit start - Move below
                // this.discard();
                boolean die = true;
                // CraftBukkit end
                if (true || player.getAbilities().instabuild) { // CraftBukkit - Process for non-creative as well
                    Iterator iterator1 = list.iterator();

                    while (iterator1.hasNext()) {
                        Leashable leashable1 = (Leashable) iterator1.next();

                        if (leashable1.isLeashed() && leashable1.getLeashHolder() == this) {
                            // CraftBukkit start
                            if (leashable1 instanceof Entity leashed) {
                                if (CraftEventFactory.callPlayerUnleashEntityEvent(leashed, player, hand).isCancelled()) {
                                    die = false;
                                    continue;
                                }
                            }
                            leashable1.dropLeash(true, !player.getAbilities().instabuild); // false -> survival mode boolean
                            // CraftBukkit end
                            flag1 = true;
                        }
                    }
                    // CraftBukkit start
                    if (die) {
                        this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    }
                    // CraftBukkit end
                }
            }

            if (flag || flag1) {
                this.gameEvent(GameEvent.BLOCK_ATTACH, player);
            }

            return InteractionResult.CONSUME;
        }
    }

    @Override
    public boolean survives() {
        return this.level().getBlockState(this.pos).is(BlockTags.FENCES);
    }

    public static LeashFenceKnotEntity getOrCreateKnot(Level world, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        List<LeashFenceKnotEntity> list = world.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB((double) i - 1.0D, (double) j - 1.0D, (double) k - 1.0D, (double) i + 1.0D, (double) j + 1.0D, (double) k + 1.0D));
        Iterator iterator = list.iterator();

        LeashFenceKnotEntity entityleash;

        do {
            if (!iterator.hasNext()) {
                LeashFenceKnotEntity entityleash1 = new LeashFenceKnotEntity(world, pos);

                world.addFreshEntity(entityleash1);
                return entityleash1;
            }

            entityleash = (LeashFenceKnotEntity) iterator.next();
        } while (!entityleash.getPos().equals(pos));

        return entityleash;
    }

    public void playPlacementSound() {
        this.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    @Override
    public Vec3 getRopeHoldPosition(float delta) {
        return this.getPosition(delta).add(0.0D, 0.2D, 0.0D);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
