package net.minecraft.gametest.framework;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class GameTestHelper {
    private final GameTestInfo testInfo;
    private boolean finalCheckAdded;

    public GameTestHelper(GameTestInfo test) {
        this.testInfo = test;
    }

    public ServerLevel getLevel() {
        return this.testInfo.getLevel();
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.getLevel().getBlockState(this.absolutePos(pos));
    }

    public <T extends BlockEntity> T getBlockEntity(BlockPos pos) {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this.absolutePos(pos));
        if (blockEntity == null) {
            throw new GameTestAssertPosException("Missing block entity", this.absolutePos(pos), pos, this.testInfo.getTick());
        } else {
            return (T)blockEntity;
        }
    }

    public void killAllEntities() {
        this.killAllEntitiesOfClass(Entity.class);
    }

    public void killAllEntitiesOfClass(Class entityClass) {
        AABB aABB = this.getBounds();
        List<Entity> list = this.getLevel().getEntitiesOfClass(entityClass, aABB.inflate(1.0), entity -> !(entity instanceof Player));
        list.forEach(Entity::kill);
    }

    public ItemEntity spawnItem(Item item, Vec3 pos) {
        ServerLevel serverLevel = this.getLevel();
        Vec3 vec3 = this.absoluteVec(pos);
        ItemEntity itemEntity = new ItemEntity(serverLevel, vec3.x, vec3.y, vec3.z, new ItemStack(item, 1));
        itemEntity.setDeltaMovement(0.0, 0.0, 0.0);
        serverLevel.addFreshEntity(itemEntity);
        return itemEntity;
    }

    public ItemEntity spawnItem(Item item, float x, float y, float z) {
        return this.spawnItem(item, new Vec3((double)x, (double)y, (double)z));
    }

    public ItemEntity spawnItem(Item item, BlockPos pos) {
        return this.spawnItem(item, (float)pos.getX(), (float)pos.getY(), (float)pos.getZ());
    }

    public <E extends Entity> E spawn(EntityType<E> type, BlockPos pos) {
        return this.spawn(type, Vec3.atBottomCenterOf(pos));
    }

    public <E extends Entity> E spawn(EntityType<E> type, Vec3 pos) {
        ServerLevel serverLevel = this.getLevel();
        E entity = type.create(serverLevel);
        if (entity == null) {
            throw new NullPointerException("Failed to create entity " + type.builtInRegistryHolder().key().location());
        } else {
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }

            Vec3 vec3 = this.absoluteVec(pos);
            entity.moveTo(vec3.x, vec3.y, vec3.z, entity.getYRot(), entity.getXRot());
            serverLevel.addFreshEntity(entity);
            return entity;
        }
    }

    public <E extends Entity> E findOneEntity(EntityType<E> type) {
        return this.findClosestEntity(type, 0, 0, 0, 2.147483647E9);
    }

    public <E extends Entity> E findClosestEntity(EntityType<E> type, int x, int y, int z, double margin) {
        List<E> list = this.findEntities(type, x, y, z, margin);
        if (list.isEmpty()) {
            throw new GameTestAssertException("Expected " + type.toShortString() + " to exist around " + x + "," + y + "," + z);
        } else if (list.size() > 1) {
            throw new GameTestAssertException(
                "Expected only one " + type.toShortString() + " to exist around " + x + "," + y + "," + z + ", but found " + list.size()
            );
        } else {
            Vec3 vec3 = this.absoluteVec(new Vec3((double)x, (double)y, (double)z));
            list.sort((a, b) -> {
                double d = a.position().distanceTo(vec3);
                double e = b.position().distanceTo(vec3);
                return Double.compare(d, e);
            });
            return list.get(0);
        }
    }

    public <E extends Entity> List<E> findEntities(EntityType<E> type, int x, int y, int z, double margin) {
        return this.findEntities(type, Vec3.atBottomCenterOf(new BlockPos(x, y, z)), margin);
    }

    public <E extends Entity> List<E> findEntities(EntityType<E> type, Vec3 pos, double margin) {
        ServerLevel serverLevel = this.getLevel();
        Vec3 vec3 = this.absoluteVec(pos);
        AABB aABB = this.testInfo.getStructureBounds();
        AABB aABB2 = new AABB(vec3.add(-margin, -margin, -margin), vec3.add(margin, margin, margin));
        return serverLevel.getEntities(type, aABB, entity -> entity.getBoundingBox().intersects(aABB2) && entity.isAlive());
    }

    public <E extends Entity> E spawn(EntityType<E> type, int x, int y, int z) {
        return this.spawn(type, new BlockPos(x, y, z));
    }

    public <E extends Entity> E spawn(EntityType<E> type, float x, float y, float z) {
        return this.spawn(type, new Vec3((double)x, (double)y, (double)z));
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, BlockPos pos) {
        E mob = (E)this.spawn(type, pos);
        mob.removeFreeWill();
        return mob;
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, int x, int y, int z) {
        return this.spawnWithNoFreeWill(type, new BlockPos(x, y, z));
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, Vec3 pos) {
        E mob = (E)this.spawn(type, pos);
        mob.removeFreeWill();
        return mob;
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, float x, float y, float z) {
        return this.spawnWithNoFreeWill(type, new Vec3((double)x, (double)y, (double)z));
    }

    public void moveTo(Mob entity, float x, float y, float z) {
        Vec3 vec3 = this.absoluteVec(new Vec3((double)x, (double)y, (double)z));
        entity.moveTo(vec3.x, vec3.y, vec3.z, entity.getYRot(), entity.getXRot());
    }

    public GameTestSequence walkTo(Mob entity, BlockPos pos, float speed) {
        return this.startSequence().thenExecuteAfter(2, () -> {
            Path path = entity.getNavigation().createPath(this.absolutePos(pos), 0);
            entity.getNavigation().moveTo(path, (double)speed);
        });
    }

    public void pressButton(int x, int y, int z) {
        this.pressButton(new BlockPos(x, y, z));
    }

    public void pressButton(BlockPos pos) {
        this.assertBlockState(pos, state -> state.is(BlockTags.BUTTONS), () -> "Expected button");
        BlockPos blockPos = this.absolutePos(pos);
        BlockState blockState = this.getLevel().getBlockState(blockPos);
        ButtonBlock buttonBlock = (ButtonBlock)blockState.getBlock();
        buttonBlock.press(blockState, this.getLevel(), blockPos, null);
    }

    public void useBlock(BlockPos pos) {
        this.useBlock(pos, this.makeMockPlayer(GameType.CREATIVE));
    }

    public void useBlock(BlockPos pos, Player player) {
        BlockPos blockPos = this.absolutePos(pos);
        this.useBlock(pos, player, new BlockHitResult(Vec3.atCenterOf(blockPos), Direction.NORTH, blockPos, true));
    }

    public void useBlock(BlockPos pos, Player player, BlockHitResult result) {
        BlockPos blockPos = this.absolutePos(pos);
        BlockState blockState = this.getLevel().getBlockState(blockPos);
        InteractionHand interactionHand = InteractionHand.MAIN_HAND;
        ItemInteractionResult itemInteractionResult = blockState.useItemOn(
            player.getItemInHand(interactionHand), this.getLevel(), player, interactionHand, result
        );
        if (!itemInteractionResult.consumesAction()) {
            if (itemInteractionResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                || !blockState.useWithoutItem(this.getLevel(), player, result).consumesAction()) {
                UseOnContext useOnContext = new UseOnContext(player, interactionHand, result);
                player.getItemInHand(interactionHand).useOn(useOnContext);
            }
        }
    }

    public LivingEntity makeAboutToDrown(LivingEntity entity) {
        entity.setAirSupply(0);
        entity.setHealth(0.25F);
        return entity;
    }

    public LivingEntity withLowHealth(LivingEntity entity) {
        entity.setHealth(0.25F);
        return entity;
    }

    public Player makeMockPlayer(GameType gameMode) {
        return new Player(this.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return gameMode == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameMode.isCreative();
            }

            @Override
            public boolean isLocalPlayer() {
                return true;
            }
        };
    }

    @Deprecated(
        forRemoval = true
    )
    public ServerPlayer makeMockServerPlayerInLevel() {
        CommonListenerCookie commonListenerCookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer serverPlayer = new ServerPlayer(
            this.getLevel().getServer(), this.getLevel(), commonListenerCookie.gameProfile(), commonListenerCookie.clientInformation()
        ) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        this.getLevel().getServer().getPlayerList().placeNewPlayer(connection, serverPlayer, commonListenerCookie);
        return serverPlayer;
    }

    public void pullLever(int x, int y, int z) {
        this.pullLever(new BlockPos(x, y, z));
    }

    public void pullLever(BlockPos pos) {
        this.assertBlockPresent(Blocks.LEVER, pos);
        BlockPos blockPos = this.absolutePos(pos);
        BlockState blockState = this.getLevel().getBlockState(blockPos);
        LeverBlock leverBlock = (LeverBlock)blockState.getBlock();
        leverBlock.pull(blockState, this.getLevel(), blockPos, null);
    }

    public void pulseRedstone(BlockPos pos, long delay) {
        this.setBlock(pos, Blocks.REDSTONE_BLOCK);
        this.runAfterDelay(delay, () -> this.setBlock(pos, Blocks.AIR));
    }

    public void destroyBlock(BlockPos pos) {
        this.getLevel().destroyBlock(this.absolutePos(pos), false, null);
    }

    public void setBlock(int x, int y, int z, Block block) {
        this.setBlock(new BlockPos(x, y, z), block);
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        this.setBlock(new BlockPos(x, y, z), state);
    }

    public void setBlock(BlockPos pos, Block block) {
        this.setBlock(pos, block.defaultBlockState());
    }

    public void setBlock(BlockPos pos, BlockState state) {
        this.getLevel().setBlock(this.absolutePos(pos), state, 3);
    }

    public void setNight() {
        this.setDayTime(13000);
    }

    public void setDayTime(int timeOfDay) {
        this.getLevel().setDayTime((long)timeOfDay);
    }

    public void assertBlockPresent(Block block, int x, int y, int z) {
        this.assertBlockPresent(block, new BlockPos(x, y, z));
    }

    public void assertBlockPresent(Block block, BlockPos pos) {
        BlockState blockState = this.getBlockState(pos);
        this.assertBlock(
            pos, block1 -> blockState.is(block), "Expected " + block.getName().getString() + ", got " + blockState.getBlock().getName().getString()
        );
    }

    public void assertBlockNotPresent(Block block, int x, int y, int z) {
        this.assertBlockNotPresent(block, new BlockPos(x, y, z));
    }

    public void assertBlockNotPresent(Block block, BlockPos pos) {
        this.assertBlock(pos, block1 -> !this.getBlockState(pos).is(block), "Did not expect " + block.getName().getString());
    }

    public void succeedWhenBlockPresent(Block block, int x, int y, int z) {
        this.succeedWhenBlockPresent(block, new BlockPos(x, y, z));
    }

    public void succeedWhenBlockPresent(Block block, BlockPos pos) {
        this.succeedWhen(() -> this.assertBlockPresent(block, pos));
    }

    public void assertBlock(BlockPos pos, Predicate<Block> predicate, String errorMessage) {
        this.assertBlock(pos, predicate, () -> errorMessage);
    }

    public void assertBlock(BlockPos pos, Predicate<Block> predicate, Supplier<String> errorMessageSupplier) {
        this.assertBlockState(pos, state -> predicate.test(state.getBlock()), errorMessageSupplier);
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, T value) {
        BlockState blockState = this.getBlockState(pos);
        boolean bl = blockState.hasProperty(property);
        if (!bl || !blockState.<T>getValue(property).equals(value)) {
            String string = bl ? "was " + blockState.getValue(property) : "property " + property.getName() + " is missing";
            String string2 = String.format(Locale.ROOT, "Expected property %s to be %s, %s", property.getName(), value, string);
            throw new GameTestAssertPosException(string2, this.absolutePos(pos), pos, this.testInfo.getTick());
        }
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, Predicate<T> predicate, String errorMessage) {
        this.assertBlockState(pos, state -> {
            if (!state.hasProperty(property)) {
                return false;
            } else {
                T comparable = state.getValue(property);
                return predicate.test(comparable);
            }
        }, () -> errorMessage);
    }

    public void assertBlockState(BlockPos pos, Predicate<BlockState> predicate, Supplier<String> errorMessageSupplier) {
        BlockState blockState = this.getBlockState(pos);
        if (!predicate.test(blockState)) {
            throw new GameTestAssertPosException(errorMessageSupplier.get(), this.absolutePos(pos), pos, this.testInfo.getTick());
        }
    }

    public <T extends BlockEntity> void assertBlockEntityData(BlockPos pos, Predicate<T> predicate, Supplier<String> errorMessageSupplier) {
        T blockEntity = this.getBlockEntity(pos);
        if (!predicate.test(blockEntity)) {
            throw new GameTestAssertPosException(errorMessageSupplier.get(), this.absolutePos(pos), pos, this.testInfo.getTick());
        }
    }

    public void assertRedstoneSignal(BlockPos pos, Direction direction, IntPredicate powerPredicate, Supplier<String> errorMessage) {
        BlockPos blockPos = this.absolutePos(pos);
        ServerLevel serverLevel = this.getLevel();
        BlockState blockState = serverLevel.getBlockState(blockPos);
        int i = blockState.getSignal(serverLevel, blockPos, direction);
        if (!powerPredicate.test(i)) {
            throw new GameTestAssertPosException(errorMessage.get(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public void assertEntityPresent(EntityType<?> type) {
        List<? extends Entity> list = this.getLevel().getEntities(type, this.getBounds(), Entity::isAlive);
        if (list.isEmpty()) {
            throw new GameTestAssertException("Expected " + type.toShortString() + " to exist");
        }
    }

    public void assertEntityPresent(EntityType<?> type, int x, int y, int z) {
        this.assertEntityPresent(type, new BlockPos(x, y, z));
    }

    public void assertEntityPresent(EntityType<?> type, BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> list = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (list.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public void assertEntityPresent(EntityType<?> type, Vec3 pos1, Vec3 pos2) {
        List<? extends Entity> list = this.getLevel().getEntities(type, new AABB(pos1, pos2), Entity::isAlive);
        if (list.isEmpty()) {
            throw new GameTestAssertPosException(
                "Expected " + type.toShortString() + " between ", BlockPos.containing(pos1), BlockPos.containing(pos2), this.testInfo.getTick()
            );
        }
    }

    public void assertEntitiesPresent(EntityType<?> type, int amount) {
        List<? extends Entity> list = this.getLevel().getEntities(type, this.getBounds(), Entity::isAlive);
        if (list.size() != amount) {
            throw new GameTestAssertException("Expected " + amount + " of type " + type.toShortString() + " to exist, found " + list.size());
        }
    }

    public void assertEntitiesPresent(EntityType<?> type, BlockPos pos, int amount, double radius) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> list = this.getEntities((EntityType<? extends Entity>)type, pos, radius);
        if (list.size() != amount) {
            throw new GameTestAssertPosException(
                "Expected " + amount + " entities of type " + type.toShortString() + ", actual number of entities found=" + list.size(),
                blockPos,
                pos,
                this.testInfo.getTick()
            );
        }
    }

    public void assertEntityPresent(EntityType<?> type, BlockPos pos, double radius) {
        List<? extends Entity> list = this.getEntities((EntityType<? extends Entity>)type, pos, radius);
        if (list.isEmpty()) {
            BlockPos blockPos = this.absolutePos(pos);
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public <T extends Entity> List<T> getEntities(EntityType<T> type, BlockPos pos, double radius) {
        BlockPos blockPos = this.absolutePos(pos);
        return this.getLevel().getEntities(type, new AABB(blockPos).inflate(radius), Entity::isAlive);
    }

    public <T extends Entity> List<T> getEntities(EntityType<T> type) {
        return this.getLevel().getEntities(type, this.getBounds(), Entity::isAlive);
    }

    public void assertEntityInstancePresent(Entity entity, int x, int y, int z) {
        this.assertEntityInstancePresent(entity, new BlockPos(x, y, z));
    }

    public void assertEntityInstancePresent(Entity entity, BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> list = this.getLevel().getEntities(entity.getType(), new AABB(blockPos), Entity::isAlive);
        list.stream()
            .filter(e -> e == entity)
            .findFirst()
            .orElseThrow(() -> new GameTestAssertPosException("Expected " + entity.getType().toShortString(), blockPos, pos, this.testInfo.getTick()));
    }

    public void assertItemEntityCountIs(Item item, BlockPos pos, double radius, int amount) {
        BlockPos blockPos = this.absolutePos(pos);
        List<ItemEntity> list = this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos).inflate(radius), Entity::isAlive);
        int i = 0;

        for (ItemEntity itemEntity : list) {
            ItemStack itemStack = itemEntity.getItem();
            if (itemStack.is(item)) {
                i += itemStack.getCount();
            }
        }

        if (i != amount) {
            throw new GameTestAssertPosException(
                "Expected " + amount + " " + item.getDescription().getString() + " items to exist (found " + i + ")", blockPos, pos, this.testInfo.getTick()
            );
        }
    }

    public void assertItemEntityPresent(Item item, BlockPos pos, double radius) {
        BlockPos blockPos = this.absolutePos(pos);

        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos).inflate(radius), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                return;
            }
        }

        throw new GameTestAssertPosException("Expected " + item.getDescription().getString() + " item", blockPos, pos, this.testInfo.getTick());
    }

    public void assertItemEntityNotPresent(Item item, BlockPos pos, double radius) {
        BlockPos blockPos = this.absolutePos(pos);

        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos).inflate(radius), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                throw new GameTestAssertPosException("Did not expect " + item.getDescription().getString() + " item", blockPos, pos, this.testInfo.getTick());
            }
        }
    }

    public void assertItemEntityPresent(Item item) {
        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, this.getBounds(), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                return;
            }
        }

        throw new GameTestAssertException("Expected " + item.getDescription().getString() + " item");
    }

    public void assertItemEntityNotPresent(Item item) {
        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, this.getBounds(), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                throw new GameTestAssertException("Did not expect " + item.getDescription().getString() + " item");
            }
        }
    }

    public void assertEntityNotPresent(EntityType<?> type) {
        List<? extends Entity> list = this.getLevel().getEntities(type, this.getBounds(), Entity::isAlive);
        if (!list.isEmpty()) {
            throw new GameTestAssertException("Did not expect " + type.toShortString() + " to exist");
        }
    }

    public void assertEntityNotPresent(EntityType<?> type, int x, int y, int z) {
        this.assertEntityNotPresent(type, new BlockPos(x, y, z));
    }

    public void assertEntityNotPresent(EntityType<?> type, BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> list = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (!list.isEmpty()) {
            throw new GameTestAssertPosException("Did not expect " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public void assertEntityNotPresent(EntityType<?> type, Vec3 pos1, Vec3 pos2) {
        List<? extends Entity> list = this.getLevel().getEntities(type, new AABB(pos1, pos2), Entity::isAlive);
        if (!list.isEmpty()) {
            throw new GameTestAssertPosException(
                "Did not expect " + type.toShortString() + " between ", BlockPos.containing(pos1), BlockPos.containing(pos2), this.testInfo.getTick()
            );
        }
    }

    public void assertEntityTouching(EntityType<?> type, double x, double y, double z) {
        Vec3 vec3 = new Vec3(x, y, z);
        Vec3 vec32 = this.absoluteVec(vec3);
        Predicate<? super Entity> predicate = entity -> entity.getBoundingBox().intersects(vec32, vec32);
        List<? extends Entity> list = this.getLevel().getEntities(type, this.getBounds(), predicate);
        if (list.isEmpty()) {
            throw new GameTestAssertException("Expected " + type.toShortString() + " to touch " + vec32 + " (relative " + vec3 + ")");
        }
    }

    public void assertEntityNotTouching(EntityType<?> type, double x, double y, double z) {
        Vec3 vec3 = new Vec3(x, y, z);
        Vec3 vec32 = this.absoluteVec(vec3);
        Predicate<? super Entity> predicate = entity -> !entity.getBoundingBox().intersects(vec32, vec32);
        List<? extends Entity> list = this.getLevel().getEntities(type, this.getBounds(), predicate);
        if (list.isEmpty()) {
            throw new GameTestAssertException("Did not expect " + type.toShortString() + " to touch " + vec32 + " (relative " + vec3 + ")");
        }
    }

    public <E extends Entity, T> void assertEntityData(BlockPos pos, EntityType<E> type, Function<? super E, T> entityDataGetter, @Nullable T data) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> list = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (list.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        } else {
            for (E entity : list) {
                T object = entityDataGetter.apply(entity);
                if (object == null) {
                    if (data != null) {
                        throw new GameTestAssertException("Expected entity data to be: " + data + ", but was: " + object);
                    }
                } else if (!object.equals(data)) {
                    throw new GameTestAssertException("Expected entity data to be: " + data + ", but was: " + object);
                }
            }
        }
    }

    public <E extends LivingEntity> void assertEntityIsHolding(BlockPos pos, EntityType<E> entityType, Item item) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> list = this.getLevel().getEntities(entityType, new AABB(blockPos), Entity::isAlive);
        if (list.isEmpty()) {
            throw new GameTestAssertPosException("Expected entity of type: " + entityType, blockPos, pos, this.getTick());
        } else {
            for (E livingEntity : list) {
                if (livingEntity.isHolding(item)) {
                    return;
                }
            }

            throw new GameTestAssertPosException("Entity should be holding: " + item, blockPos, pos, this.getTick());
        }
    }

    public <E extends Entity & InventoryCarrier> void assertEntityInventoryContains(BlockPos pos, EntityType<E> entityType, Item item) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> list = this.getLevel().getEntities(entityType, new AABB(blockPos), entityx -> ((Entity)entityx).isAlive());
        if (list.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + entityType.toShortString() + " to exist", blockPos, pos, this.getTick());
        } else {
            for (E entity : list) {
                if (entity.getInventory().hasAnyMatching(stack -> stack.is(item))) {
                    return;
                }
            }

            throw new GameTestAssertPosException("Entity inventory should contain: " + item, blockPos, pos, this.getTick());
        }
    }

    public void assertContainerEmpty(BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        BlockEntity blockEntity = this.getLevel().getBlockEntity(blockPos);
        if (blockEntity instanceof BaseContainerBlockEntity && !((BaseContainerBlockEntity)blockEntity).isEmpty()) {
            throw new GameTestAssertException("Container should be empty");
        }
    }

    public void assertContainerContains(BlockPos pos, Item item) {
        BlockPos blockPos = this.absolutePos(pos);
        BlockEntity blockEntity = this.getLevel().getBlockEntity(blockPos);
        if (!(blockEntity instanceof BaseContainerBlockEntity)) {
            throw new GameTestAssertException("Expected a container at " + pos + ", found " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
        } else if (((BaseContainerBlockEntity)blockEntity).countItem(item) != 1) {
            throw new GameTestAssertException("Container should contain: " + item);
        }
    }

    public void assertSameBlockStates(BoundingBox checkedBlockBox, BlockPos correctStatePos) {
        BlockPos.betweenClosedStream(checkedBlockBox)
            .forEach(
                checkedPos -> {
                    BlockPos blockPos2 = correctStatePos.offset(
                        checkedPos.getX() - checkedBlockBox.minX(), checkedPos.getY() - checkedBlockBox.minY(), checkedPos.getZ() - checkedBlockBox.minZ()
                    );
                    this.assertSameBlockState(checkedPos, blockPos2);
                }
            );
    }

    public void assertSameBlockState(BlockPos checkedPos, BlockPos correctStatePos) {
        BlockState blockState = this.getBlockState(checkedPos);
        BlockState blockState2 = this.getBlockState(correctStatePos);
        if (blockState != blockState2) {
            this.fail("Incorrect state. Expected " + blockState2 + ", got " + blockState, checkedPos);
        }
    }

    public void assertAtTickTimeContainerContains(long delay, BlockPos pos, Item item) {
        this.runAtTickTime(delay, () -> this.assertContainerContains(pos, item));
    }

    public void assertAtTickTimeContainerEmpty(long delay, BlockPos pos) {
        this.runAtTickTime(delay, () -> this.assertContainerEmpty(pos));
    }

    public <E extends Entity, T> void succeedWhenEntityData(BlockPos pos, EntityType<E> type, Function<E, T> entityDataGetter, T data) {
        this.succeedWhen(() -> this.assertEntityData(pos, type, entityDataGetter, data));
    }

    public void assertEntityPosition(Entity entity, AABB box, String message) {
        if (!box.contains(this.relativeVec(entity.position()))) {
            this.fail(message);
        }
    }

    public <E extends Entity> void assertEntityProperty(E entity, Predicate<E> predicate, String testName) {
        if (!predicate.test(entity)) {
            throw new GameTestAssertException("Entity " + entity + " failed " + testName + " test");
        }
    }

    public <E extends Entity, T> void assertEntityProperty(E entity, Function<E, T> propertyGetter, String propertyName, T expectedValue) {
        T object = propertyGetter.apply(entity);
        if (!object.equals(expectedValue)) {
            throw new GameTestAssertException("Entity " + entity + " value " + propertyName + "=" + object + " is not equal to expected " + expectedValue);
        }
    }

    public void assertLivingEntityHasMobEffect(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance mobEffectInstance = entity.getEffect(effect);
        if (mobEffectInstance == null || mobEffectInstance.getAmplifier() != amplifier) {
            int i = amplifier + 1;
            throw new GameTestAssertException("Entity " + entity + " failed has " + effect.value().getDescriptionId() + " x " + i + " test");
        }
    }

    public void succeedWhenEntityPresent(EntityType<?> type, int x, int y, int z) {
        this.succeedWhenEntityPresent(type, new BlockPos(x, y, z));
    }

    public void succeedWhenEntityPresent(EntityType<?> type, BlockPos pos) {
        this.succeedWhen(() -> this.assertEntityPresent(type, pos));
    }

    public void succeedWhenEntityNotPresent(EntityType<?> type, int x, int y, int z) {
        this.succeedWhenEntityNotPresent(type, new BlockPos(x, y, z));
    }

    public void succeedWhenEntityNotPresent(EntityType<?> type, BlockPos pos) {
        this.succeedWhen(() -> this.assertEntityNotPresent(type, pos));
    }

    public void succeed() {
        this.testInfo.succeed();
    }

    private void ensureSingleFinalCheck() {
        if (this.finalCheckAdded) {
            throw new IllegalStateException("This test already has final clause");
        } else {
            this.finalCheckAdded = true;
        }
    }

    public void succeedIf(Runnable runnable) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(0L, runnable).thenSucceed();
    }

    public void succeedWhen(Runnable runnable) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(runnable).thenSucceed();
    }

    public void succeedOnTickWhen(int duration, Runnable runnable) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil((long)duration, runnable).thenSucceed();
    }

    public void runAtTickTime(long tick, Runnable runnable) {
        this.testInfo.setRunAtTickTime(tick, runnable);
    }

    public void runAfterDelay(long ticks, Runnable runnable) {
        this.runAtTickTime(this.testInfo.getTick() + ticks, runnable);
    }

    public void randomTick(BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        ServerLevel serverLevel = this.getLevel();
        serverLevel.getBlockState(blockPos).randomTick(serverLevel, blockPos, serverLevel.random);
    }

    public void tickPrecipitation(BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        ServerLevel serverLevel = this.getLevel();
        serverLevel.tickPrecipitation(blockPos);
    }

    public void tickPrecipitation() {
        AABB aABB = this.getRelativeBounds();
        int i = (int)Math.floor(aABB.maxX);
        int j = (int)Math.floor(aABB.maxZ);
        int k = (int)Math.floor(aABB.maxY);

        for (int l = (int)Math.floor(aABB.minX); l < i; l++) {
            for (int m = (int)Math.floor(aABB.minZ); m < j; m++) {
                this.tickPrecipitation(new BlockPos(l, k, m));
            }
        }
    }

    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        BlockPos blockPos = this.absolutePos(new BlockPos(x, 0, z));
        return this.relativePos(this.getLevel().getHeightmapPos(heightmap, blockPos)).getY();
    }

    public void fail(String message, BlockPos pos) {
        throw new GameTestAssertPosException(message, this.absolutePos(pos), pos, this.getTick());
    }

    public void fail(String message, Entity entity) {
        throw new GameTestAssertPosException(message, entity.blockPosition(), this.relativePos(entity.blockPosition()), this.getTick());
    }

    public void fail(String message) {
        throw new GameTestAssertException(message);
    }

    public void failIf(Runnable task) {
        this.testInfo.createSequence().thenWaitUntil(task).thenFail(() -> new GameTestAssertException("Fail conditions met"));
    }

    public void failIfEver(Runnable task) {
        LongStream.range(this.testInfo.getTick(), (long)this.testInfo.getTimeoutTicks()).forEach(tick -> this.testInfo.setRunAtTickTime(tick, task::run));
    }

    public GameTestSequence startSequence() {
        return this.testInfo.createSequence();
    }

    public BlockPos absolutePos(BlockPos pos) {
        BlockPos blockPos = this.testInfo.getStructureBlockPos();
        BlockPos blockPos2 = blockPos.offset(pos);
        return StructureTemplate.transform(blockPos2, Mirror.NONE, this.testInfo.getRotation(), blockPos);
    }

    public BlockPos relativePos(BlockPos pos) {
        BlockPos blockPos = this.testInfo.getStructureBlockPos();
        Rotation rotation = this.testInfo.getRotation().getRotated(Rotation.CLOCKWISE_180);
        BlockPos blockPos2 = StructureTemplate.transform(pos, Mirror.NONE, rotation, blockPos);
        return blockPos2.subtract(blockPos);
    }

    public Vec3 absoluteVec(Vec3 pos) {
        Vec3 vec3 = Vec3.atLowerCornerOf(this.testInfo.getStructureBlockPos());
        return StructureTemplate.transform(vec3.add(pos), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getStructureBlockPos());
    }

    public Vec3 relativeVec(Vec3 pos) {
        Vec3 vec3 = Vec3.atLowerCornerOf(this.testInfo.getStructureBlockPos());
        return StructureTemplate.transform(pos.subtract(vec3), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getStructureBlockPos());
    }

    public Rotation getTestRotation() {
        return this.testInfo.getRotation();
    }

    public void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    public <N> void assertValueEqual(N value, N expected, String name) {
        if (!value.equals(expected)) {
            throw new GameTestAssertException("Expected " + name + " to be " + expected + ", but was " + value);
        }
    }

    public void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new GameTestAssertException(message);
        }
    }

    public long getTick() {
        return this.testInfo.getTick();
    }

    public AABB getBounds() {
        return this.testInfo.getStructureBounds();
    }

    private AABB getRelativeBounds() {
        AABB aABB = this.testInfo.getStructureBounds();
        return aABB.move(BlockPos.ZERO.subtract(this.absolutePos(BlockPos.ZERO)));
    }

    public void forEveryBlockInStructure(Consumer<BlockPos> posConsumer) {
        AABB aABB = this.getRelativeBounds().contract(1.0, 1.0, 1.0);
        BlockPos.MutableBlockPos.betweenClosedStream(aABB).forEach(posConsumer);
    }

    public void onEachTick(Runnable runnable) {
        LongStream.range(this.testInfo.getTick(), (long)this.testInfo.getTimeoutTicks()).forEach(tick -> this.testInfo.setRunAtTickTime(tick, runnable::run));
    }

    public void placeAt(Player player, ItemStack stack, BlockPos pos, Direction direction) {
        BlockPos blockPos = this.absolutePos(pos.relative(direction));
        BlockHitResult blockHitResult = new BlockHitResult(Vec3.atCenterOf(blockPos), direction, blockPos, false);
        UseOnContext useOnContext = new UseOnContext(player, InteractionHand.MAIN_HAND, blockHitResult);
        stack.useOn(useOnContext);
    }

    public void setBiome(ResourceKey<Biome> biome) {
        AABB aABB = this.getBounds();
        BlockPos blockPos = BlockPos.containing(aABB.minX, aABB.minY, aABB.minZ);
        BlockPos blockPos2 = BlockPos.containing(aABB.maxX, aABB.maxY, aABB.maxZ);
        Either<Integer, CommandSyntaxException> either = FillBiomeCommand.fill(
            this.getLevel(), blockPos, blockPos2, this.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome)
        );
        if (either.right().isPresent()) {
            this.fail("Failed to set biome for test");
        }
    }
}
