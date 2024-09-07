package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

public class LootParams {
    private final ServerLevel level;
    private final Map<LootContextParam<?>, Object> params;
    private final Map<ResourceLocation, LootParams.DynamicDrop> dynamicDrops;
    private final float luck;

    public LootParams(ServerLevel world, Map<LootContextParam<?>, Object> parameters, Map<ResourceLocation, LootParams.DynamicDrop> dynamicDrops, float luck) {
        this.level = world;
        this.params = parameters;
        this.dynamicDrops = dynamicDrops;
        this.luck = luck;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public boolean hasParam(LootContextParam<?> parameter) {
        return this.params.containsKey(parameter);
    }

    public <T> T getParameter(LootContextParam<T> parameter) {
        T object = (T)this.params.get(parameter);
        if (object == null) {
            throw new NoSuchElementException(parameter.getName().toString());
        } else {
            return object;
        }
    }

    @Nullable
    public <T> T getOptionalParameter(LootContextParam<T> parameter) {
        return (T)this.params.get(parameter);
    }

    @Nullable
    public <T> T getParamOrNull(LootContextParam<T> parameter) {
        return (T)this.params.get(parameter);
    }

    public void addDynamicDrops(ResourceLocation id, Consumer<ItemStack> lootConsumer) {
        LootParams.DynamicDrop dynamicDrop = this.dynamicDrops.get(id);
        if (dynamicDrop != null) {
            dynamicDrop.add(lootConsumer);
        }
    }

    public float getLuck() {
        return this.luck;
    }

    public static class Builder {
        private final ServerLevel level;
        private final Map<LootContextParam<?>, Object> params = Maps.newIdentityHashMap();
        private final Map<ResourceLocation, LootParams.DynamicDrop> dynamicDrops = Maps.newHashMap();
        private float luck;

        public Builder(ServerLevel world) {
            this.level = world;
        }

        public ServerLevel getLevel() {
            return this.level;
        }

        public <T> LootParams.Builder withParameter(LootContextParam<T> parameter, T value) {
            this.params.put(parameter, value);
            return this;
        }

        public <T> LootParams.Builder withOptionalParameter(LootContextParam<T> parameter, @Nullable T value) {
            if (value == null) {
                this.params.remove(parameter);
            } else {
                this.params.put(parameter, value);
            }

            return this;
        }

        public <T> T getParameter(LootContextParam<T> parameter) {
            T object = (T)this.params.get(parameter);
            if (object == null) {
                throw new NoSuchElementException(parameter.getName().toString());
            } else {
                return object;
            }
        }

        @Nullable
        public <T> T getOptionalParameter(LootContextParam<T> parameter) {
            return (T)this.params.get(parameter);
        }

        public LootParams.Builder withDynamicDrop(ResourceLocation id, LootParams.DynamicDrop dynamicDrop) {
            LootParams.DynamicDrop dynamicDrop2 = this.dynamicDrops.put(id, dynamicDrop);
            if (dynamicDrop2 != null) {
                throw new IllegalStateException("Duplicated dynamic drop '" + this.dynamicDrops + "'");
            } else {
                return this;
            }
        }

        public LootParams.Builder withLuck(float luck) {
            this.luck = luck;
            return this;
        }

        public LootParams create(LootContextParamSet contextType) {
            Set<LootContextParam<?>> set = Sets.difference(this.params.keySet(), contextType.getAllowed());
            if (!set.isEmpty()) {
                throw new IllegalArgumentException("Parameters not allowed in this parameter set: " + set);
            } else {
                Set<LootContextParam<?>> set2 = Sets.difference(contextType.getRequired(), this.params.keySet());
                if (!set2.isEmpty()) {
                    throw new IllegalArgumentException("Missing required parameters: " + set2);
                } else {
                    return new LootParams(this.level, this.params, this.dynamicDrops, this.luck);
                }
            }
        }
    }

    @FunctionalInterface
    public interface DynamicDrop {
        void add(Consumer<ItemStack> lootConsumer);
    }
}
