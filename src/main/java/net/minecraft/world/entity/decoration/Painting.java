package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Painting extends HangingEntity implements VariantHolder<Holder<PaintingVariant>> {

    private static final EntityDataAccessor<Holder<PaintingVariant>> DATA_PAINTING_VARIANT_ID = SynchedEntityData.defineId(Painting.class, EntityDataSerializers.PAINTING_VARIANT);
    public static final MapCodec<Holder<PaintingVariant>> VARIANT_MAP_CODEC = PaintingVariant.CODEC.fieldOf("variant");
    public static final Codec<Holder<PaintingVariant>> VARIANT_CODEC = Painting.VARIANT_MAP_CODEC.codec();
    public static final float DEPTH = 0.0625F;

    public Painting(EntityType<? extends Painting> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(Painting.DATA_PAINTING_VARIANT_ID, (Holder) this.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getAny().orElseThrow());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Painting.DATA_PAINTING_VARIANT_ID.equals(data)) {
            this.recalculateBoundingBox();
        }

    }

    public void setVariant(Holder<PaintingVariant> variant) {
        this.entityData.set(Painting.DATA_PAINTING_VARIANT_ID, variant);
    }

    @Override
    public Holder<PaintingVariant> getVariant() {
        return (Holder) this.entityData.get(Painting.DATA_PAINTING_VARIANT_ID);
    }

    public static Optional<Painting> create(Level world, BlockPos pos, Direction facing) {
        Painting entitypainting = new Painting(world, pos);
        List<Holder<PaintingVariant>> list = new ArrayList();
        Iterable<Holder<PaintingVariant>> iterable = world.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getTagOrEmpty(PaintingVariantTags.PLACEABLE); // CraftBukkit - decompile error

        Objects.requireNonNull(list);
        iterable.forEach(list::add);
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            entitypainting.setDirection(facing);
            list.removeIf((holder) -> {
                entitypainting.setVariant(holder);
                return !entitypainting.survives();
            });
            if (list.isEmpty()) {
                return Optional.empty();
            } else {
                int i = list.stream().mapToInt(Painting::variantArea).max().orElse(0);

                list.removeIf((holder) -> {
                    return Painting.variantArea(holder) < i;
                });
                Optional<Holder<PaintingVariant>> optional = Util.getRandomSafe(list, entitypainting.random);

                if (optional.isEmpty()) {
                    return Optional.empty();
                } else {
                    entitypainting.setVariant((Holder) optional.get());
                    entitypainting.setDirection(facing);
                    return Optional.of(entitypainting);
                }
            }
        }
    }

    private static int variantArea(Holder<PaintingVariant> variant) {
        return ((PaintingVariant) variant.value()).area();
    }

    private Painting(Level world, BlockPos pos) {
        super(EntityType.PAINTING, world, pos);
    }

    public Painting(Level world, BlockPos pos, Direction direction, Holder<PaintingVariant> variant) {
        this(world, pos);
        this.setVariant(variant);
        this.setDirection(direction);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        Painting.VARIANT_CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.getVariant()).ifSuccess((nbtbase) -> {
            nbt.merge((CompoundTag) nbtbase);
        });
        nbt.putByte("facing", (byte) this.direction.get2DDataValue());
        super.addAdditionalSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        Painting.VARIANT_CODEC.parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), nbt).ifSuccess(this::setVariant);
        this.direction = Direction.from2DDataValue(nbt.getByte("facing"));
        super.readAdditionalSaveData(nbt);
        this.setDirection(this.direction);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction side) {
        // CraftBukkit start
        PaintingVariant paintingvariant = (PaintingVariant) this.getVariant().value();
        return Painting.calculateBoundingBoxStatic(pos, side, paintingvariant.width(), paintingvariant.height());
    }

    public static AABB calculateBoundingBoxStatic(BlockPos blockposition, Direction enumdirection, int width, int height) {
        // CraftBukkit end
        float f = 0.46875F;
        Vec3 vec3d = Vec3.atCenterOf(blockposition).relative(enumdirection, -0.46875D);
        // CraftBukkit start
        double d0 = Painting.offsetForPaintingSize(width);
        double d1 = Painting.offsetForPaintingSize(height);
        // CraftBukkit end
        Direction enumdirection1 = enumdirection.getCounterClockWise();
        Vec3 vec3d1 = vec3d.relative(enumdirection1, d0).relative(Direction.UP, d1);
        Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
        // CraftBukkit start
        double d2 = enumdirection_enumaxis == Direction.Axis.X ? 0.0625D : (double) width;
        double d3 = (double) height;
        double d4 = enumdirection_enumaxis == Direction.Axis.Z ? 0.0625D : (double) width;
        // CraftBukkit end

        return AABB.ofSize(vec3d1, d2, d3, d4);
    }

    private static double offsetForPaintingSize(int length) { // CraftBukkit - static
        return length % 2 == 0 ? 0.5D : 0.0D;
    }

    @Override
    public void dropItem(@Nullable Entity breaker) {
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (breaker instanceof Player) {
                Player entityhuman = (Player) breaker;

                if (entityhuman.hasInfiniteMaterials()) {
                    return;
                }
            }

            this.spawnAtLocation((ItemLike) Items.PAINTING);
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.PAINTING);
    }
}
