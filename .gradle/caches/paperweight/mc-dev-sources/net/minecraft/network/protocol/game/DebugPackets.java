package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.GameTestAddMarkerDebugPayload;
import net.minecraft.network.protocol.common.custom.GameTestClearMarkersDebugPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class DebugPackets {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void sendGameTestAddMarker(ServerLevel world, BlockPos pos, String message, int color, int duration) {
        sendPacketToAllPlayers(world, new GameTestAddMarkerDebugPayload(pos, color, message, duration));
    }

    public static void sendGameTestClearPacket(ServerLevel world) {
        sendPacketToAllPlayers(world, new GameTestClearMarkersDebugPayload());
    }

    public static void sendPoiPacketsForChunk(ServerLevel world, ChunkPos pos) {
    }

    public static void sendPoiAddedPacket(ServerLevel world, BlockPos pos) {
        sendVillageSectionsPacket(world, pos);
    }

    public static void sendPoiRemovedPacket(ServerLevel world, BlockPos pos) {
        sendVillageSectionsPacket(world, pos);
    }

    public static void sendPoiTicketCountPacket(ServerLevel world, BlockPos pos) {
        sendVillageSectionsPacket(world, pos);
    }

    private static void sendVillageSectionsPacket(ServerLevel world, BlockPos pos) {
    }

    public static void sendPathFindingPacket(Level world, Mob mob, @Nullable Path path, float nodeReachProximity) {
    }

    public static void sendNeighborsUpdatePacket(Level world, BlockPos pos) {
    }

    public static void sendStructurePacket(WorldGenLevel world, StructureStart structureStart) {
    }

    public static void sendGoalSelector(Level world, Mob mob, GoalSelector goalSelector) {
    }

    public static void sendRaids(ServerLevel server, Collection<Raid> raids) {
    }

    public static void sendEntityBrain(LivingEntity living) {
    }

    public static void sendBeeInfo(Bee bee) {
    }

    public static void sendBreezeInfo(Breeze breeze) {
    }

    public static void sendGameEventInfo(Level world, Holder<GameEvent> event, Vec3 pos) {
    }

    public static void sendGameEventListenerInfo(Level world, GameEventListener eventListener) {
    }

    public static void sendHiveInfo(Level world, BlockPos pos, BlockState state, BeehiveBlockEntity blockEntity) {
    }

    private static List<String> getMemoryDescriptions(LivingEntity entity, long currentTime) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> map = entity.getBrain().getMemories();
        List<String> list = Lists.newArrayList();

        for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : map.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            Optional<? extends ExpirableValue<?>> optional = entry.getValue();
            String string;
            if (optional.isPresent()) {
                ExpirableValue<?> expirableValue = (ExpirableValue<?>)optional.get();
                Object object = expirableValue.getValue();
                if (memoryModuleType == MemoryModuleType.HEARD_BELL_TIME) {
                    long l = currentTime - (Long)object;
                    string = l + " ticks ago";
                } else if (expirableValue.canExpire()) {
                    string = getShortDescription((ServerLevel)entity.level(), object) + " (ttl: " + expirableValue.getTimeToLive() + ")";
                } else {
                    string = getShortDescription((ServerLevel)entity.level(), object);
                }
            } else {
                string = "-";
            }

            list.add(BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(memoryModuleType).getPath() + ": " + string);
        }

        list.sort(String::compareTo);
        return list;
    }

    private static String getShortDescription(ServerLevel world, @Nullable Object object) {
        if (object == null) {
            return "-";
        } else if (object instanceof UUID) {
            return getShortDescription(world, world.getEntity((UUID)object));
        } else if (object instanceof LivingEntity) {
            Entity entity = (Entity)object;
            return DebugEntityNameGenerator.getEntityName(entity);
        } else if (object instanceof Nameable) {
            return ((Nameable)object).getName().getString();
        } else if (object instanceof WalkTarget) {
            return getShortDescription(world, ((WalkTarget)object).getTarget());
        } else if (object instanceof EntityTracker) {
            return getShortDescription(world, ((EntityTracker)object).getEntity());
        } else if (object instanceof GlobalPos) {
            return getShortDescription(world, ((GlobalPos)object).pos());
        } else if (object instanceof BlockPosTracker) {
            return getShortDescription(world, ((BlockPosTracker)object).currentBlockPosition());
        } else if (object instanceof DamageSource) {
            Entity entity2 = ((DamageSource)object).getEntity();
            return entity2 == null ? object.toString() : getShortDescription(world, entity2);
        } else if (!(object instanceof Collection)) {
            return object.toString();
        } else {
            List<String> list = Lists.newArrayList();

            for (Object object2 : (Iterable)object) {
                list.add(getShortDescription(world, object2));
            }

            return list.toString();
        }
    }

    private static void sendPacketToAllPlayers(ServerLevel world, CustomPacketPayload payload) {
        Packet<?> packet = new ClientboundCustomPayloadPacket(payload);

        for (ServerPlayer serverPlayer : world.players()) {
            serverPlayer.connection.send(packet);
        }
    }
}
