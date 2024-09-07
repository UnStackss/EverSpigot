package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class EntityAttachments {
    private final Map<EntityAttachment, List<Vec3>> attachments;

    EntityAttachments(Map<EntityAttachment, List<Vec3>> points) {
        this.attachments = points;
    }

    public static EntityAttachments createDefault(float width, float height) {
        return builder().build(width, height);
    }

    public static EntityAttachments.Builder builder() {
        return new EntityAttachments.Builder();
    }

    public EntityAttachments scale(float xScale, float yScale, float zScale) {
        Map<EntityAttachment, List<Vec3>> map = new EnumMap<>(EntityAttachment.class);

        for (Entry<EntityAttachment, List<Vec3>> entry : this.attachments.entrySet()) {
            map.put(entry.getKey(), scalePoints(entry.getValue(), xScale, yScale, zScale));
        }

        return new EntityAttachments(map);
    }

    private static List<Vec3> scalePoints(List<Vec3> points, float xScale, float yScale, float zScale) {
        List<Vec3> list = new ArrayList<>(points.size());

        for (Vec3 vec3 : points) {
            list.add(vec3.multiply((double)xScale, (double)yScale, (double)zScale));
        }

        return list;
    }

    @Nullable
    public Vec3 getNullable(EntityAttachment type, int index, float yaw) {
        List<Vec3> list = this.attachments.get(type);
        return index >= 0 && index < list.size() ? transformPoint(list.get(index), yaw) : null;
    }

    public Vec3 get(EntityAttachment type, int index, float yaw) {
        Vec3 vec3 = this.getNullable(type, index, yaw);
        if (vec3 == null) {
            throw new IllegalStateException("Had no attachment point of type: " + type + " for index: " + index);
        } else {
            return vec3;
        }
    }

    public Vec3 getClamped(EntityAttachment type, int index, float yaw) {
        List<Vec3> list = this.attachments.get(type);
        if (list.isEmpty()) {
            throw new IllegalStateException("Had no attachment points of type: " + type);
        } else {
            Vec3 vec3 = list.get(Mth.clamp(index, 0, list.size() - 1));
            return transformPoint(vec3, yaw);
        }
    }

    private static Vec3 transformPoint(Vec3 point, float yaw) {
        return point.yRot(-yaw * (float) (Math.PI / 180.0));
    }

    public static class Builder {
        private final Map<EntityAttachment, List<Vec3>> attachments = new EnumMap<>(EntityAttachment.class);

        Builder() {
        }

        public EntityAttachments.Builder attach(EntityAttachment type, float x, float y, float z) {
            return this.attach(type, new Vec3((double)x, (double)y, (double)z));
        }

        public EntityAttachments.Builder attach(EntityAttachment type, Vec3 point) {
            this.attachments.computeIfAbsent(type, list -> new ArrayList<>(1)).add(point);
            return this;
        }

        public EntityAttachments build(float width, float height) {
            Map<EntityAttachment, List<Vec3>> map = new EnumMap<>(EntityAttachment.class);

            for (EntityAttachment entityAttachment : EntityAttachment.values()) {
                List<Vec3> list = this.attachments.get(entityAttachment);
                map.put(entityAttachment, list != null ? List.copyOf(list) : entityAttachment.createFallbackPoints(width, height));
            }

            return new EntityAttachments(map);
        }
    }
}
