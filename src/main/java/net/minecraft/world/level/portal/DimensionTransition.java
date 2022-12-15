package net.minecraft.world.level.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.player.PlayerTeleportEvent;

public record DimensionTransition(ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, boolean missingRespawnBlock, DimensionTransition.PostDimensionTransition postDimensionTransition, PlayerTeleportEvent.TeleportCause cause) {

    public DimensionTransition(ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, boolean missingRespawnBlock, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        this(newLevel, pos, speed, yRot, xRot, missingRespawnBlock, postDimensionTransition, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    // Paper - remove unused constructor (for safety)
    // CraftBukkit end

    public static final DimensionTransition.PostDimensionTransition DO_NOTHING = (entity) -> {
    };
    public static final DimensionTransition.PostDimensionTransition PLAY_PORTAL_SOUND = DimensionTransition::playPortalSound;
    public static final DimensionTransition.PostDimensionTransition PLACE_PORTAL_TICKET = DimensionTransition::placePortalTicket;

    public DimensionTransition(ServerLevel world, Vec3 pos, Vec3 velocity, float yaw, float pitch, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        // CraftBukkit start
        this(world, pos, velocity, yaw, pitch, postDimensionTransition, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public DimensionTransition(ServerLevel worldserver, Vec3 vec3d, Vec3 vec3d1, float f, float f1, DimensionTransition.PostDimensionTransition dimensiontransition_a, PlayerTeleportEvent.TeleportCause cause) {
        this(worldserver, vec3d, vec3d1, f, f1, false, dimensiontransition_a, cause);
    }

    public DimensionTransition(ServerLevel world, Entity entity, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        this(world, entity, postDimensionTransition, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public DimensionTransition(ServerLevel worldserver, Entity entity, DimensionTransition.PostDimensionTransition dimensiontransition_a, PlayerTeleportEvent.TeleportCause cause) {
        this(worldserver, findAdjustedSharedSpawnPos(worldserver, entity), Vec3.ZERO, worldserver.getSharedSpawnAngle(), 0.0F, false, dimensiontransition_a, cause); // Paper - MC-200092 - fix spawn pos yaw being ignored
        // CraftBukkit end
    }

    private static void playPortalSound(Entity entity) {
        if (entity instanceof ServerPlayer entityplayer) {
            entityplayer.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
        }

    }

    private static void placePortalTicket(Entity entity) {
        entity.placePortalTicket(BlockPos.containing(entity.position()));
    }

    public static DimensionTransition missingRespawnBlock(ServerLevel world, Entity entity, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        return new DimensionTransition(world, findAdjustedSharedSpawnPos(world, entity), Vec3.ZERO, world.getSharedSpawnAngle(), 0.0F, true, postDimensionTransition); // Paper - MC-200092 - fix spawn pos yaw being ignored
    }

    private static Vec3 findAdjustedSharedSpawnPos(ServerLevel world, Entity entity) {
        return entity.adjustSpawnLocation(world, world.getSharedSpawnPos()).getBottomCenter();
    }

    @FunctionalInterface
    public interface PostDimensionTransition {

        void onTransition(Entity entity);

        default DimensionTransition.PostDimensionTransition then(DimensionTransition.PostDimensionTransition next) {
            return (entity) -> {
                this.onTransition(entity);
                next.onTransition(entity);
            };
        }
    }
}
