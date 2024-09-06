package net.minecraft.world.level.storage.loot;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
// CraftBukkit end

public record LootDataType<T>(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.Validator<T> validator) {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final LootDataType<LootItemCondition> PREDICATE = new LootDataType<>(Registries.PREDICATE, LootItemCondition.DIRECT_CODEC, createSimpleValidator());
    public static final LootDataType<LootItemFunction> MODIFIER = new LootDataType<>(Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC, createSimpleValidator());
    public static final LootDataType<LootTable> TABLE = new LootDataType<>(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC, createLootTableValidator());

    public void runValidation(ValidationContext reporter, ResourceKey<T> key, T value) {
        this.validator.run(reporter, key, value);
    }

    public <V> Optional<T> deserialize(ResourceLocation id, DynamicOps<V> ops, V json) {
        DataResult<T> dataresult = this.codec.parse(ops, json);

        dataresult.error().ifPresent((error) -> {
            LootDataType.LOGGER.error("Couldn't parse element {}/{} - {}", new Object[]{this.registryKey.location(), id, error.message()});
        });
        return dataresult.result();
    }

    public static Stream<LootDataType<?>> values() {
        return Stream.of(LootDataType.PREDICATE, LootDataType.MODIFIER, LootDataType.TABLE);
    }

    private static <T extends LootContextUser> LootDataType.Validator<T> createSimpleValidator() {
        return (lootcollector, resourcekey, lootitemuser) -> {
            lootitemuser.validate(lootcollector.enterElement("{" + String.valueOf(resourcekey.registry()) + "/" + String.valueOf(resourcekey.location()) + "}", resourcekey));
        };
    }

    private static LootDataType.Validator<LootTable> createLootTableValidator() {
        return (lootcollector, resourcekey, loottable) -> {
            loottable.validate(lootcollector.setParams(loottable.getParamSet()).enterElement("{" + String.valueOf(resourcekey.registry()) + "/" + String.valueOf(resourcekey.location()) + "}", resourcekey));
            loottable.craftLootTable = new CraftLootTable(CraftNamespacedKey.fromMinecraft(resourcekey.location()), loottable); // CraftBukkit
        };
    }

    @FunctionalInterface
    public interface Validator<T> {

        void run(ValidationContext reporter, ResourceKey<T> key, T value);
    }
}
