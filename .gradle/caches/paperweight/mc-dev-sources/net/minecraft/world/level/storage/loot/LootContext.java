package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LootContext {
    private final LootParams params;
    private final RandomSource random;
    private final HolderGetter.Provider lootDataResolver;
    private final Set<LootContext.VisitedEntry<?>> visitedElements = Sets.newLinkedHashSet();

    LootContext(LootParams parameters, RandomSource random, HolderGetter.Provider lookup) {
        this.params = parameters;
        this.random = random;
        this.lootDataResolver = lookup;
    }

    public boolean hasParam(LootContextParam<?> parameter) {
        return this.params.hasParam(parameter);
    }

    public <T> T getParam(LootContextParam<T> parameter) {
        return this.params.getParameter(parameter);
    }

    public void addDynamicDrops(ResourceLocation id, Consumer<ItemStack> lootConsumer) {
        this.params.addDynamicDrops(id, lootConsumer);
    }

    @Nullable
    public <T> T getParamOrNull(LootContextParam<T> parameter) {
        return this.params.getParamOrNull(parameter);
    }

    public boolean hasVisitedElement(LootContext.VisitedEntry<?> entry) {
        return this.visitedElements.contains(entry);
    }

    public boolean pushVisitedElement(LootContext.VisitedEntry<?> entry) {
        return this.visitedElements.add(entry);
    }

    public void popVisitedElement(LootContext.VisitedEntry<?> entry) {
        this.visitedElements.remove(entry);
    }

    public HolderGetter.Provider getResolver() {
        return this.lootDataResolver;
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public float getLuck() {
        return this.params.getLuck();
    }

    public ServerLevel getLevel() {
        return this.params.getLevel();
    }

    public static LootContext.VisitedEntry<LootTable> createVisitedEntry(LootTable table) {
        return new LootContext.VisitedEntry<>(LootDataType.TABLE, table);
    }

    public static LootContext.VisitedEntry<LootItemCondition> createVisitedEntry(LootItemCondition predicate) {
        return new LootContext.VisitedEntry<>(LootDataType.PREDICATE, predicate);
    }

    public static LootContext.VisitedEntry<LootItemFunction> createVisitedEntry(LootItemFunction itemModifier) {
        return new LootContext.VisitedEntry<>(LootDataType.MODIFIER, itemModifier);
    }

    public static class Builder {
        private final LootParams params;
        @Nullable
        private RandomSource random;

        public Builder(LootParams parameters) {
            this.params = parameters;
        }

        public LootContext.Builder withOptionalRandomSeed(long seed) {
            if (seed != 0L) {
                this.random = RandomSource.create(seed);
            }

            return this;
        }

        public LootContext.Builder withOptionalRandomSource(RandomSource random) {
            this.random = random;
            return this;
        }

        public ServerLevel getLevel() {
            return this.params.getLevel();
        }

        public LootContext create(Optional<ResourceLocation> randomId) {
            ServerLevel serverLevel = this.getLevel();
            MinecraftServer minecraftServer = serverLevel.getServer();
            RandomSource randomSource = Optional.ofNullable(this.random)
                .or(() -> randomId.map(serverLevel::getRandomSequence))
                .orElseGet(serverLevel::getRandom);
            return new LootContext(this.params, randomSource, minecraftServer.reloadableRegistries().lookup());
        }
    }

    public static enum EntityTarget implements StringRepresentable {
        THIS("this", LootContextParams.THIS_ENTITY),
        ATTACKER("attacker", LootContextParams.ATTACKING_ENTITY),
        DIRECT_ATTACKER("direct_attacker", LootContextParams.DIRECT_ATTACKING_ENTITY),
        ATTACKING_PLAYER("attacking_player", LootContextParams.LAST_DAMAGE_PLAYER);

        public static final StringRepresentable.EnumCodec<LootContext.EntityTarget> CODEC = StringRepresentable.fromEnum(LootContext.EntityTarget::values);
        private final String name;
        private final LootContextParam<? extends Entity> param;

        private EntityTarget(final String type, final LootContextParam<? extends Entity> parameter) {
            this.name = type;
            this.param = parameter;
        }

        public LootContextParam<? extends Entity> getParam() {
            return this.param;
        }

        public static LootContext.EntityTarget getByName(String type) {
            LootContext.EntityTarget entityTarget = CODEC.byName(type);
            if (entityTarget != null) {
                return entityTarget;
            } else {
                throw new IllegalArgumentException("Invalid entity target " + type);
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static record VisitedEntry<T>(LootDataType<T> type, T value) {
    }
}
