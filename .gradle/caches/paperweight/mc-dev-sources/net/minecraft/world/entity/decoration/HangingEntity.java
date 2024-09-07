package net.minecraft.world.entity.decoration;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

public abstract class HangingEntity extends BlockAttachedEntity {
    protected static final Predicate<Entity> HANGING_ENTITY = entity -> entity instanceof HangingEntity;
    protected Direction direction = Direction.SOUTH;

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level world) {
        super(type, world);
    }

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level world, BlockPos pos) {
        this(type, world);
        this.pos = pos;
    }

    public void setDirection(Direction facing) {
        Objects.requireNonNull(facing);
        Validate.isTrue(facing.getAxis().isHorizontal());
        this.direction = facing;
        this.setYRot((float)(this.direction.get2DDataValue() * 90));
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected final void recalculateBoundingBox() {
        if (this.direction != null) {
            AABB aABB = this.calculateBoundingBox(this.pos, this.direction);
            Vec3 vec3 = aABB.getCenter();
            this.setPosRaw(vec3.x, vec3.y, vec3.z);
            this.setBoundingBox(aABB);
        }
    }

    protected abstract AABB calculateBoundingBox(BlockPos pos, Direction side);

    @Override
    public boolean survives() {
        if (!this.level().noCollision(this)) {
            return false;
        } else {
            boolean bl = BlockPos.betweenClosedStream(this.calculateSupportBox()).allMatch(pos -> {
                BlockState blockState = this.level().getBlockState(pos);
                return blockState.isSolid() || DiodeBlock.isDiode(blockState);
            });
            return bl && this.level().getEntities(this, this.getBoundingBox(), HANGING_ENTITY).isEmpty();
        }
    }

    protected AABB calculateSupportBox() {
        return this.getBoundingBox().move(this.direction.step().mul(-0.5F)).deflate(1.0E-7);
    }

    @Override
    public Direction getDirection() {
        return this.direction;
    }

    public abstract void playPlacementSound();

    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        ItemEntity itemEntity = new ItemEntity(
            this.level(),
            this.getX() + (double)((float)this.direction.getStepX() * 0.15F),
            this.getY() + (double)yOffset,
            this.getZ() + (double)((float)this.direction.getStepZ() * 0.15F),
            stack
        );
        itemEntity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itemEntity);
        return itemEntity;
    }

    @Override
    public float rotate(Rotation rotation) {
        if (this.direction.getAxis() != Direction.Axis.Y) {
            switch (rotation) {
                case CLOCKWISE_180:
                    this.direction = this.direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    this.direction = this.direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    this.direction = this.direction.getClockWise();
            }
        }

        float f = Mth.wrapDegrees(this.getYRot());

        return switch (rotation) {
            case CLOCKWISE_180 -> f + 180.0F;
            case COUNTERCLOCKWISE_90 -> f + 90.0F;
            case CLOCKWISE_90 -> f + 270.0F;
            default -> f;
        };
    }

    @Override
    public float mirror(Mirror mirror) {
        return this.rotate(mirror.getRotation(this.direction));
    }
}
