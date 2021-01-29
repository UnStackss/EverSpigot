package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntitySelector {

    public static final int INFINITE = Integer.MAX_VALUE;
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_ARBITRARY = (vec3d, list) -> {
    };
    private static final EntityTypeTest<Entity, ?> ANY_TYPE = new EntityTypeTest<Entity, Entity>() {
        public Entity tryCast(Entity obj) {
            return obj;
        }

        @Override
        public Class<? extends Entity> getBaseClass() {
            return Entity.class;
        }
    };
    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final List<Predicate<Entity>> contextFreePredicates;
    private final MinMaxBounds.Doubles range;
    private final Function<Vec3, Vec3> position;
    @Nullable
    private final AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    private final boolean currentEntity;
    @Nullable
    private final String playerName;
    @Nullable
    private final UUID entityUUID;
    private final EntityTypeTest<Entity, ?> type;
    private final boolean usesSelector;

    public EntitySelector(int count, boolean includesNonPlayers, boolean localWorldOnly, List<Predicate<Entity>> predicates, MinMaxBounds.Doubles distance, Function<Vec3, Vec3> positionOffset, @Nullable AABB box, BiConsumer<Vec3, List<? extends Entity>> sorter, boolean senderOnly, @Nullable String playerName, @Nullable UUID uuid, @Nullable EntityType<?> type, boolean usesAt) {
        this.maxResults = count;
        this.includesEntities = includesNonPlayers;
        this.worldLimited = localWorldOnly;
        this.contextFreePredicates = predicates;
        this.range = distance;
        this.position = positionOffset;
        this.aabb = box;
        this.order = sorter;
        this.currentEntity = senderOnly;
        this.playerName = playerName;
        this.entityUUID = uuid;
        this.type = (EntityTypeTest) (type == null ? EntitySelector.ANY_TYPE : type);
        this.usesSelector = usesAt;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    public boolean usesSelector() {
        return this.usesSelector;
    }

    private void checkPermissions(CommandSourceStack source) throws CommandSyntaxException {
        if (!source.bypassSelectorPermissions && (this.usesSelector && !source.hasPermission(2, "minecraft.command.selector"))) { // CraftBukkit // Paper - add bypass for selector perms
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<? extends Entity> list = this.findEntities(source);

        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return (Entity) list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        if (!this.includesEntities) {
            return this.findPlayers(source);
        } else if (this.playerName != null) {
            ServerPlayer entityplayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);

            return entityplayer == null ? List.of() : List.of(entityplayer);
        } else if (this.entityUUID != null) {
            Iterator iterator = source.getServer().getAllLevels().iterator();

            while (iterator.hasNext()) {
                ServerLevel worldserver = (ServerLevel) iterator.next();
                Entity entity = worldserver.getEntity(this.entityUUID);

                if (entity != null) {
                    if (entity.getType().isEnabled(source.enabledFeatures())) {
                        return List.of(entity);
                    }
                    break;
                }
            }

            return List.of();
        } else {
            Vec3 vec3d = (Vec3) this.position.apply(source.getPosition());
            AABB axisalignedbb = this.getAbsoluteAabb(vec3d);
            Predicate predicate;

            if (this.currentEntity) {
                predicate = this.getPredicate(vec3d, axisalignedbb, (FeatureFlagSet) null);
                return source.getEntity() != null && predicate.test(source.getEntity()) ? List.of(source.getEntity()) : List.of();
            } else {
                predicate = this.getPredicate(vec3d, axisalignedbb, source.enabledFeatures());
                List<Entity> list = new ObjectArrayList();

                if (this.isWorldLimited()) {
                    this.addEntities(list, source.getLevel(), axisalignedbb, predicate);
                } else {
                    Iterator iterator1 = source.getServer().getAllLevels().iterator();

                    while (iterator1.hasNext()) {
                        ServerLevel worldserver1 = (ServerLevel) iterator1.next();

                        this.addEntities(list, worldserver1, axisalignedbb, predicate);
                    }
                }

                return this.sortAndLimit(vec3d, list);
            }
        }
    }

    private void addEntities(List<Entity> entities, ServerLevel world, @Nullable AABB box, Predicate<Entity> predicate) {
        int i = this.getResultLimit();

        if (entities.size() < i) {
            if (box != null) {
                world.getEntities(this.type, box, predicate, entities, i);
            } else {
                world.getEntities(this.type, predicate, entities, i);
            }

        }
    }

    private int getResultLimit() {
        return this.order == EntitySelector.ORDER_ARBITRARY ? this.maxResults : Integer.MAX_VALUE;
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<ServerPlayer> list = this.findPlayers(source);

        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return (ServerPlayer) list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        ServerPlayer entityplayer;

        if (this.playerName != null) {
            entityplayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);
            return entityplayer == null ? List.of() : List.of(entityplayer);
        } else if (this.entityUUID != null) {
            entityplayer = source.getServer().getPlayerList().getPlayer(this.entityUUID);
            return entityplayer == null ? List.of() : List.of(entityplayer);
        } else {
            Vec3 vec3d = (Vec3) this.position.apply(source.getPosition());
            AABB axisalignedbb = this.getAbsoluteAabb(vec3d);
            Predicate<Entity> predicate = this.getPredicate(vec3d, axisalignedbb, (FeatureFlagSet) null);

            if (this.currentEntity) {
                Entity entity = source.getEntity();

                if (entity instanceof ServerPlayer) {
                    ServerPlayer entityplayer1 = (ServerPlayer) entity;

                    if (predicate.test(entityplayer1)) {
                        return List.of(entityplayer1);
                    }
                }

                return List.of();
            } else {
                int i = this.getResultLimit();
                Object object;

                if (this.isWorldLimited()) {
                    object = source.getLevel().getPlayers(predicate, i);
                } else {
                    object = new ObjectArrayList();
                    Iterator iterator = source.getServer().getPlayerList().getPlayers().iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer2 = (ServerPlayer) iterator.next();

                        if (predicate.test(entityplayer2)) {
                            ((List) object).add(entityplayer2);
                            if (((List) object).size() >= i) {
                                return (List) object;
                            }
                        }
                    }
                }

                return this.sortAndLimit(vec3d, (List) object);
            }
        }
    }

    @Nullable
    private AABB getAbsoluteAabb(Vec3 offset) {
        return this.aabb != null ? this.aabb.move(offset) : null;
    }

    private Predicate<Entity> getPredicate(Vec3 pos, @Nullable AABB box, @Nullable FeatureFlagSet enabledFeatures) {
        boolean flag = enabledFeatures != null;
        boolean flag1 = box != null;
        boolean flag2 = !this.range.isAny();
        int i = (flag ? 1 : 0) + (flag1 ? 1 : 0) + (flag2 ? 1 : 0);
        Object object;

        if (i == 0) {
            object = this.contextFreePredicates;
        } else {
            List<Predicate<Entity>> list = new ObjectArrayList(this.contextFreePredicates.size() + i);

            list.addAll(this.contextFreePredicates);
            if (flag) {
                list.add((entity) -> {
                    return entity.getType().isEnabled(enabledFeatures);
                });
            }

            if (flag1) {
                list.add((entity) -> {
                    return box.intersects(entity.getBoundingBox());
                });
            }

            if (flag2) {
                list.add((entity) -> {
                    return this.range.matchesSqr(entity.distanceToSqr(pos));
                });
            }

            object = list;
        }

        return Util.allOf((List) object);
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 pos, List<T> entities) {
        if (entities.size() > 1) {
            this.order.accept(pos, entities);
        }

        return entities.subList(0, Math.min(this.maxResults, entities.size()));
    }

    public static Component joinNames(List<? extends Entity> entities) {
        return ComponentUtils.formatList(entities, Entity::getDisplayName);
    }
}
