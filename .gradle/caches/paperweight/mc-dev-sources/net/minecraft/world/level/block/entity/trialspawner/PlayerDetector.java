package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public interface PlayerDetector {
    PlayerDetector NO_CREATIVE_PLAYERS = (world, selector, center, radius, spawner) -> selector.getPlayers(
                world, player -> player.blockPosition().closerThan(center, radius) && !player.isCreative() && !player.isSpectator()
            )
            .stream()
            .filter(entity -> !spawner || inLineOfSight(world, center.getCenter(), entity.getEyePosition()))
            .map(Entity::getUUID)
            .toList();
    PlayerDetector INCLUDING_CREATIVE_PLAYERS = (world, selector, center, radius, spawner) -> selector.getPlayers(
                world, player -> player.blockPosition().closerThan(center, radius) && !player.isSpectator()
            )
            .stream()
            .filter(entity -> !spawner || inLineOfSight(world, center.getCenter(), entity.getEyePosition()))
            .map(Entity::getUUID)
            .toList();
    PlayerDetector SHEEP = (world, selector, center, radius, spawner) -> {
        AABB aABB = new AABB(center).inflate(radius);
        return selector.getEntities(world, EntityType.SHEEP, aABB, LivingEntity::isAlive)
            .stream()
            .filter(entity -> !spawner || inLineOfSight(world, center.getCenter(), entity.getEyePosition()))
            .map(Entity::getUUID)
            .toList();
    };

    List<UUID> detect(ServerLevel world, PlayerDetector.EntitySelector selector, BlockPos center, double radius, boolean spawner);

    private static boolean inLineOfSight(Level world, Vec3 pos, Vec3 entityEyePos) {
        BlockHitResult blockHitResult = world.clip(
            new ClipContext(entityEyePos, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())
        );
        return blockHitResult.getBlockPos().equals(BlockPos.containing(pos)) || blockHitResult.getType() == HitResult.Type.MISS;
    }

    public interface EntitySelector {
        PlayerDetector.EntitySelector SELECT_FROM_LEVEL = new PlayerDetector.EntitySelector() {
            @Override
            public List<ServerPlayer> getPlayers(ServerLevel world, Predicate<? super Player> predicate) {
                return world.getPlayers(predicate);
            }

            @Override
            public <T extends Entity> List<T> getEntities(ServerLevel world, EntityTypeTest<Entity, T> typeFilter, AABB box, Predicate<? super T> predicate) {
                return world.getEntities(typeFilter, box, predicate);
            }
        };

        List<? extends Player> getPlayers(ServerLevel world, Predicate<? super Player> predicate);

        <T extends Entity> List<T> getEntities(ServerLevel world, EntityTypeTest<Entity, T> typeFilter, AABB box, Predicate<? super T> predicate);

        static PlayerDetector.EntitySelector onlySelectPlayer(Player player) {
            return onlySelectPlayers(List.of(player));
        }

        static PlayerDetector.EntitySelector onlySelectPlayers(List<Player> players) {
            return new PlayerDetector.EntitySelector() {
                @Override
                public List<Player> getPlayers(ServerLevel world, Predicate<? super Player> predicate) {
                    return players.stream().filter(predicate).toList();
                }

                @Override
                public <T extends Entity> List<T> getEntities(ServerLevel world, EntityTypeTest<Entity, T> typeFilter, AABB box, Predicate<? super T> predicate) {
                    return players.stream().map(typeFilter::tryCast).filter(Objects::nonNull).filter(predicate).toList();
                }
            };
        }
    }
}
