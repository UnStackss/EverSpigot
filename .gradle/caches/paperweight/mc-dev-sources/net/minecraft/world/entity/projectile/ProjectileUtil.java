package net.minecraft.world.entity.projectile;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ProjectileUtil {
    private static final float DEFAULT_ENTITY_HIT_RESULT_MARGIN = 0.3F;

    public static HitResult getHitResultOnMoveVector(Entity entity, Predicate<Entity> predicate) {
        Vec3 vec3 = entity.getDeltaMovement();
        Level level = entity.level();
        Vec3 vec32 = entity.position();
        return getHitResult(vec32, entity, predicate, vec3, level, 0.3F, ClipContext.Block.COLLIDER);
    }

    public static HitResult getHitResultOnMoveVector(Entity entity, Predicate<Entity> predicate, ClipContext.Block raycastShapeType) {
        Vec3 vec3 = entity.getDeltaMovement();
        Level level = entity.level();
        Vec3 vec32 = entity.position();
        return getHitResult(vec32, entity, predicate, vec3, level, 0.3F, raycastShapeType);
    }

    public static HitResult getHitResultOnViewVector(Entity entity, Predicate<Entity> predicate, double range) {
        Vec3 vec3 = entity.getViewVector(0.0F).scale(range);
        Level level = entity.level();
        Vec3 vec32 = entity.getEyePosition();
        return getHitResult(vec32, entity, predicate, vec3, level, 0.0F, ClipContext.Block.COLLIDER);
    }

    private static HitResult getHitResult(
        Vec3 pos, Entity entity, Predicate<Entity> predicate, Vec3 velocity, Level world, float margin, ClipContext.Block raycastShapeType
    ) {
        Vec3 vec3 = pos.add(velocity);
        HitResult hitResult = world.clip(new ClipContext(pos, vec3, raycastShapeType, ClipContext.Fluid.NONE, entity));
        if (hitResult.getType() != HitResult.Type.MISS) {
            vec3 = hitResult.getLocation();
        }

        HitResult hitResult2 = getEntityHitResult(world, entity, pos, vec3, entity.getBoundingBox().expandTowards(velocity).inflate(1.0), predicate, margin);
        if (hitResult2 != null) {
            hitResult = hitResult2;
        }

        return hitResult;
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Entity entity, Vec3 min, Vec3 max, AABB box, Predicate<Entity> predicate, double maxDistance) {
        Level level = entity.level();
        double d = maxDistance;
        Entity entity2 = null;
        Vec3 vec3 = null;

        for (Entity entity3 : level.getEntities(entity, box, predicate)) {
            AABB aABB = entity3.getBoundingBox().inflate((double)entity3.getPickRadius());
            Optional<Vec3> optional = aABB.clip(min, max);
            if (aABB.contains(min)) {
                if (d >= 0.0) {
                    entity2 = entity3;
                    vec3 = optional.orElse(min);
                    d = 0.0;
                }
            } else if (optional.isPresent()) {
                Vec3 vec32 = optional.get();
                double e = min.distanceToSqr(vec32);
                if (e < d || d == 0.0) {
                    if (entity3.getRootVehicle() == entity.getRootVehicle()) {
                        if (d == 0.0) {
                            entity2 = entity3;
                            vec3 = vec32;
                        }
                    } else {
                        entity2 = entity3;
                        vec3 = vec32;
                        d = e;
                    }
                }
            }
        }

        return entity2 == null ? null : new EntityHitResult(entity2, vec3);
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Level world, Entity entity, Vec3 min, Vec3 max, AABB box, Predicate<Entity> predicate) {
        return getEntityHitResult(world, entity, min, max, box, predicate, 0.3F);
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Level world, Entity entity, Vec3 min, Vec3 max, AABB box, Predicate<Entity> predicate, float margin) {
        double d = Double.MAX_VALUE;
        Entity entity2 = null;

        for (Entity entity3 : world.getEntities(entity, box, predicate)) {
            AABB aABB = entity3.getBoundingBox().inflate((double)margin);
            Optional<Vec3> optional = aABB.clip(min, max);
            if (optional.isPresent()) {
                double e = min.distanceToSqr(optional.get());
                if (e < d) {
                    entity2 = entity3;
                    d = e;
                }
            }
        }

        return entity2 == null ? null : new EntityHitResult(entity2);
    }

    public static void rotateTowardsMovement(Entity entity, float delta) {
        Vec3 vec3 = entity.getDeltaMovement();
        if (vec3.lengthSqr() != 0.0) {
            double d = vec3.horizontalDistance();
            entity.setYRot((float)(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI) + 90.0F);
            entity.setXRot((float)(Mth.atan2(d, vec3.y) * 180.0F / (float)Math.PI) - 90.0F);

            while (entity.getXRot() - entity.xRotO < -180.0F) {
                entity.xRotO -= 360.0F;
            }

            while (entity.getXRot() - entity.xRotO >= 180.0F) {
                entity.xRotO += 360.0F;
            }

            while (entity.getYRot() - entity.yRotO < -180.0F) {
                entity.yRotO -= 360.0F;
            }

            while (entity.getYRot() - entity.yRotO >= 180.0F) {
                entity.yRotO += 360.0F;
            }

            entity.setXRot(Mth.lerp(delta, entity.xRotO, entity.getXRot()));
            entity.setYRot(Mth.lerp(delta, entity.yRotO, entity.getYRot()));
        }
    }

    public static InteractionHand getWeaponHoldingHand(LivingEntity entity, Item item) {
        return entity.getMainHandItem().is(item) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static AbstractArrow getMobArrow(LivingEntity entity, ItemStack stack, float damageModifier, @Nullable ItemStack bow) {
        ArrowItem arrowItem = (ArrowItem)(stack.getItem() instanceof ArrowItem ? stack.getItem() : Items.ARROW);
        AbstractArrow abstractArrow = arrowItem.createArrow(entity.level(), stack, entity, bow);
        abstractArrow.setBaseDamageFromMob(damageModifier);
        return abstractArrow;
    }
}
