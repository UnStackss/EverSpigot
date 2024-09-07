package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

public class EntitySubPredicates {
    public static final MapCodec<LightningBoltPredicate> LIGHTNING = register("lightning", LightningBoltPredicate.CODEC);
    public static final MapCodec<FishingHookPredicate> FISHING_HOOK = register("fishing_hook", FishingHookPredicate.CODEC);
    public static final MapCodec<PlayerPredicate> PLAYER = register("player", PlayerPredicate.CODEC);
    public static final MapCodec<SlimePredicate> SLIME = register("slime", SlimePredicate.CODEC);
    public static final MapCodec<RaiderPredicate> RAIDER = register("raider", RaiderPredicate.CODEC);
    public static final EntitySubPredicates.EntityVariantPredicateType<Axolotl.Variant> AXOLOTL = register(
        "axolotl",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Axolotl.Variant.CODEC, entity -> entity instanceof Axolotl axolotl ? Optional.of(axolotl.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Boat.Type> BOAT = register(
        "boat",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Boat.Type.CODEC, entity -> entity instanceof Boat boat ? Optional.of(boat.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Fox.Type> FOX = register(
        "fox",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Fox.Type.CODEC, entity -> entity instanceof Fox fox ? Optional.of(fox.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<MushroomCow.MushroomType> MOOSHROOM = register(
        "mooshroom",
        EntitySubPredicates.EntityVariantPredicateType.create(
            MushroomCow.MushroomType.CODEC, entity -> entity instanceof MushroomCow mushroomCow ? Optional.of(mushroomCow.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Rabbit.Variant> RABBIT = register(
        "rabbit",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Rabbit.Variant.CODEC, entity -> entity instanceof Rabbit rabbit ? Optional.of(rabbit.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Variant> HORSE = register(
        "horse",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Variant.CODEC, entity -> entity instanceof Horse horse ? Optional.of(horse.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Llama.Variant> LLAMA = register(
        "llama",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Llama.Variant.CODEC, entity -> entity instanceof Llama llama ? Optional.of(llama.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<VillagerType> VILLAGER = register(
        "villager",
        EntitySubPredicates.EntityVariantPredicateType.create(
            BuiltInRegistries.VILLAGER_TYPE.byNameCodec(),
            entity -> entity instanceof VillagerDataHolder villagerDataHolder ? Optional.of(villagerDataHolder.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Parrot.Variant> PARROT = register(
        "parrot",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Parrot.Variant.CODEC, entity -> entity instanceof Parrot parrot ? Optional.of(parrot.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<TropicalFish.Pattern> TROPICAL_FISH = register(
        "tropical_fish",
        EntitySubPredicates.EntityVariantPredicateType.create(
            TropicalFish.Pattern.CODEC, entity -> entity instanceof TropicalFish tropicalFish ? Optional.of(tropicalFish.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<PaintingVariant> PAINTING = register(
        "painting",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.PAINTING_VARIANT, entity -> entity instanceof Painting painting ? Optional.of(painting.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<CatVariant> CAT = register(
        "cat",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.CAT_VARIANT, entity -> entity instanceof Cat cat ? Optional.of(cat.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<FrogVariant> FROG = register(
        "frog",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.FROG_VARIANT, entity -> entity instanceof Frog frog ? Optional.of(frog.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<WolfVariant> WOLF = register(
        "wolf",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.WOLF_VARIANT, entity -> entity instanceof Wolf wolf ? Optional.of(wolf.getVariant()) : Optional.empty()
        )
    );

    private static <T extends EntitySubPredicate> MapCodec<T> register(String id, MapCodec<T> codec) {
        return Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, id, codec);
    }

    private static <V> EntitySubPredicates.EntityVariantPredicateType<V> register(String id, EntitySubPredicates.EntityVariantPredicateType<V> type) {
        Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, id, type.codec);
        return type;
    }

    private static <V> EntitySubPredicates.EntityHolderVariantPredicateType<V> register(String id, EntitySubPredicates.EntityHolderVariantPredicateType<V> type) {
        Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, id, type.codec);
        return type;
    }

    public static MapCodec<? extends EntitySubPredicate> bootstrap(Registry<MapCodec<? extends EntitySubPredicate>> registry) {
        return LIGHTNING;
    }

    public static EntitySubPredicate catVariant(Holder<CatVariant> catVariant) {
        return CAT.createPredicate(HolderSet.direct(catVariant));
    }

    public static EntitySubPredicate frogVariant(Holder<FrogVariant> frogVariant) {
        return FROG.createPredicate(HolderSet.direct(frogVariant));
    }

    public static EntitySubPredicate wolfVariant(HolderSet<WolfVariant> wolfVariant) {
        return WOLF.createPredicate(wolfVariant);
    }

    public static class EntityHolderVariantPredicateType<V> {
        final MapCodec<EntitySubPredicates.EntityHolderVariantPredicateType<V>.Instance> codec;
        final Function<Entity, Optional<Holder<V>>> getter;

        public static <V> EntitySubPredicates.EntityHolderVariantPredicateType<V> create(
            ResourceKey<? extends Registry<V>> registryRef, Function<Entity, Optional<Holder<V>>> variantGetter
        ) {
            return new EntitySubPredicates.EntityHolderVariantPredicateType<>(registryRef, variantGetter);
        }

        public EntityHolderVariantPredicateType(ResourceKey<? extends Registry<V>> registryRef, Function<Entity, Optional<Holder<V>>> variantGetter) {
            this.getter = variantGetter;
            this.codec = RecordCodecBuilder.mapCodec(
                instance -> instance.group(RegistryCodecs.homogeneousList(registryRef).fieldOf("variant").forGetter(type -> type.variants))
                        .apply(instance, entries -> new EntitySubPredicates.EntityHolderVariantPredicateType.Instance(entries))
            );
        }

        public EntitySubPredicate createPredicate(HolderSet<V> variants) {
            return new EntitySubPredicates.EntityHolderVariantPredicateType.Instance(variants);
        }

        class Instance implements EntitySubPredicate {
            final HolderSet<V> variants;

            Instance(final HolderSet<V> variants) {
                this.variants = variants;
            }

            @Override
            public MapCodec<EntitySubPredicates.EntityHolderVariantPredicateType<V>.Instance> codec() {
                return EntityHolderVariantPredicateType.this.codec;
            }

            @Override
            public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
                return EntityHolderVariantPredicateType.this.getter.apply(entity).filter(this.variants::contains).isPresent();
            }
        }
    }

    public static class EntityVariantPredicateType<V> {
        final MapCodec<EntitySubPredicates.EntityVariantPredicateType<V>.Instance> codec;
        final Function<Entity, Optional<V>> getter;

        public static <V> EntitySubPredicates.EntityVariantPredicateType<V> create(Registry<V> registry, Function<Entity, Optional<V>> variantGetter) {
            return new EntitySubPredicates.EntityVariantPredicateType<>(registry.byNameCodec(), variantGetter);
        }

        public static <V> EntitySubPredicates.EntityVariantPredicateType<V> create(Codec<V> codec, Function<Entity, Optional<V>> variantGetter) {
            return new EntitySubPredicates.EntityVariantPredicateType<>(codec, variantGetter);
        }

        public EntityVariantPredicateType(Codec<V> variantCodec, Function<Entity, Optional<V>> variantGetter) {
            this.getter = variantGetter;
            this.codec = RecordCodecBuilder.mapCodec(
                instance -> instance.group(variantCodec.fieldOf("variant").forGetter(predicate -> predicate.variant))
                        .apply(instance, variant -> new EntitySubPredicates.EntityVariantPredicateType.Instance(variant))
            );
        }

        public EntitySubPredicate createPredicate(V variant) {
            return new EntitySubPredicates.EntityVariantPredicateType.Instance(variant);
        }

        class Instance implements EntitySubPredicate {
            final V variant;

            Instance(final V variant) {
                this.variant = variant;
            }

            @Override
            public MapCodec<EntitySubPredicates.EntityVariantPredicateType<V>.Instance> codec() {
                return EntityVariantPredicateType.this.codec;
            }

            @Override
            public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
                return EntityVariantPredicateType.this.getter.apply(entity).filter(this.variant::equals).isPresent();
            }
        }
    }
}
