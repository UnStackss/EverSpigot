package net.minecraft.network.codec;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;
    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<ByteBuf, Boolean>() {
        @Override
        public Boolean decode(ByteBuf byteBuf) {
            return byteBuf.readBoolean();
        }

        @Override
        public void encode(ByteBuf byteBuf, Boolean boolean_) {
            byteBuf.writeBoolean(boolean_);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<ByteBuf, Byte>() {
        @Override
        public Byte decode(ByteBuf byteBuf) {
            return byteBuf.readByte();
        }

        @Override
        public void encode(ByteBuf byteBuf, Byte byte_) {
            byteBuf.writeByte(byte_);
        }
    };
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<ByteBuf, Short>() {
        @Override
        public Short decode(ByteBuf byteBuf) {
            return byteBuf.readShort();
        }

        @Override
        public void encode(ByteBuf byteBuf, Short short_) {
            byteBuf.writeShort(short_);
        }
    };
    StreamCodec<ByteBuf, Integer> UNSIGNED_SHORT = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf byteBuf) {
            return byteBuf.readUnsignedShort();
        }

        @Override
        public void encode(ByteBuf byteBuf, Integer integer) {
            byteBuf.writeShort(integer);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf byteBuf) {
            return byteBuf.readInt();
        }

        @Override
        public void encode(ByteBuf byteBuf, Integer integer) {
            byteBuf.writeInt(integer);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf byteBuf) {
            return VarInt.read(byteBuf);
        }

        @Override
        public void encode(ByteBuf byteBuf, Integer integer) {
            VarInt.write(byteBuf, integer);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<ByteBuf, Long>() {
        @Override
        public Long decode(ByteBuf byteBuf) {
            return VarLong.read(byteBuf);
        }

        @Override
        public void encode(ByteBuf byteBuf, Long long_) {
            VarLong.write(byteBuf, long_);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<ByteBuf, Float>() {
        @Override
        public Float decode(ByteBuf byteBuf) {
            return byteBuf.readFloat();
        }

        @Override
        public void encode(ByteBuf byteBuf, Float float_) {
            byteBuf.writeFloat(float_);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<ByteBuf, Double>() {
        @Override
        public Double decode(ByteBuf byteBuf) {
            return byteBuf.readDouble();
        }

        @Override
        public void encode(ByteBuf byteBuf, Double double_) {
            byteBuf.writeDouble(double_);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<ByteBuf, byte[]>() {
        @Override
        public byte[] decode(ByteBuf byteBuf) {
            return FriendlyByteBuf.readByteArray(byteBuf);
        }

        @Override
        public void encode(ByteBuf byteBuf, byte[] bs) {
            FriendlyByteBuf.writeByteArray(byteBuf, bs);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, Tag> TAG = tagCodec(() -> NbtAccounter.create(2097152L));
    StreamCodec<ByteBuf, Tag> TRUSTED_TAG = tagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = compoundTagCodec(() -> NbtAccounter.create(2097152L));
    StreamCodec<ByteBuf, CompoundTag> TRUSTED_COMPOUND_TAG = compoundTagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<ByteBuf, Optional<CompoundTag>>() {
        @Override
        public Optional<CompoundTag> decode(ByteBuf byteBuf) {
            return Optional.ofNullable(FriendlyByteBuf.readNbt(byteBuf));
        }

        @Override
        public void encode(ByteBuf byteBuf, Optional<CompoundTag> optional) {
            FriendlyByteBuf.writeNbt(byteBuf, optional.orElse(null));
        }
    };
    StreamCodec<ByteBuf, Vector3f> VECTOR3F = new StreamCodec<ByteBuf, Vector3f>() {
        @Override
        public Vector3f decode(ByteBuf byteBuf) {
            return FriendlyByteBuf.readVector3f(byteBuf);
        }

        @Override
        public void encode(ByteBuf byteBuf, Vector3f vector3f) {
            FriendlyByteBuf.writeVector3f(byteBuf, vector3f);
        }
    };
    StreamCodec<ByteBuf, Quaternionf> QUATERNIONF = new StreamCodec<ByteBuf, Quaternionf>() {
        @Override
        public Quaternionf decode(ByteBuf byteBuf) {
            return FriendlyByteBuf.readQuaternion(byteBuf);
        }

        @Override
        public void encode(ByteBuf byteBuf, Quaternionf quaternionf) {
            FriendlyByteBuf.writeQuaternion(byteBuf, quaternionf);
        }
    };
    StreamCodec<ByteBuf, PropertyMap> GAME_PROFILE_PROPERTIES = new StreamCodec<ByteBuf, PropertyMap>() {
        private static final int MAX_PROPERTY_NAME_LENGTH = 64;
        private static final int MAX_PROPERTY_VALUE_LENGTH = 32767;
        private static final int MAX_PROPERTY_SIGNATURE_LENGTH = 1024;
        private static final int MAX_PROPERTIES = 16;

        @Override
        public PropertyMap decode(ByteBuf byteBuf) {
            int i = ByteBufCodecs.readCount(byteBuf, 16);
            PropertyMap propertyMap = new PropertyMap();

            for (int j = 0; j < i; j++) {
                String string = Utf8String.read(byteBuf, 64);
                String string2 = Utf8String.read(byteBuf, 32767);
                String string3 = FriendlyByteBuf.readNullable(byteBuf, buf2 -> Utf8String.read(buf2, 1024));
                Property property = new Property(string, string2, string3);
                propertyMap.put(property.name(), property);
            }

            return propertyMap;
        }

        @Override
        public void encode(ByteBuf byteBuf, PropertyMap propertyMap) {
            ByteBufCodecs.writeCount(byteBuf, propertyMap.size(), 16);

            for (Property property : propertyMap.values()) {
                Utf8String.write(byteBuf, property.name(), 64);
                Utf8String.write(byteBuf, property.value(), 32767);
                FriendlyByteBuf.writeNullable(byteBuf, property.signature(), (buf2, signature) -> Utf8String.write(buf2, signature, 1024));
            }
        }
    };
    StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = new StreamCodec<ByteBuf, GameProfile>() {
        @Override
        public GameProfile decode(ByteBuf byteBuf) {
            UUID uUID = UUIDUtil.STREAM_CODEC.decode(byteBuf);
            String string = Utf8String.read(byteBuf, 16);
            GameProfile gameProfile = new GameProfile(uUID, string);
            gameProfile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(byteBuf));
            return gameProfile;
        }

        @Override
        public void encode(ByteBuf byteBuf, GameProfile gameProfile) {
            UUIDUtil.STREAM_CODEC.encode(byteBuf, gameProfile.getId());
            Utf8String.write(byteBuf, gameProfile.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(byteBuf, gameProfile.getProperties());
        }
    };

    static StreamCodec<ByteBuf, byte[]> byteArray(int maxLength) {
        return new StreamCodec<ByteBuf, byte[]>() {
            @Override
            public byte[] decode(ByteBuf buf) {
                return FriendlyByteBuf.readByteArray(buf, maxLength);
            }

            @Override
            public void encode(ByteBuf byteBuf, byte[] bs) {
                if (bs.length > maxLength) {
                    throw new EncoderException("ByteArray with size " + bs.length + " is bigger than allowed " + maxLength);
                } else {
                    FriendlyByteBuf.writeByteArray(byteBuf, bs);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, String> stringUtf8(int maxLength) {
        return new StreamCodec<ByteBuf, String>() {
            @Override
            public String decode(ByteBuf byteBuf) {
                return Utf8String.read(byteBuf, maxLength);
            }

            @Override
            public void encode(ByteBuf byteBuf, String string) {
                Utf8String.write(byteBuf, string, maxLength);
            }
        };
    }

    static StreamCodec<ByteBuf, Tag> tagCodec(Supplier<NbtAccounter> sizeTracker) {
        return new StreamCodec<ByteBuf, Tag>() {
            @Override
            public Tag decode(ByteBuf byteBuf) {
                Tag tag = FriendlyByteBuf.readNbt(byteBuf, sizeTracker.get());
                if (tag == null) {
                    throw new DecoderException("Expected non-null compound tag");
                } else {
                    return tag;
                }
            }

            @Override
            public void encode(ByteBuf byteBuf, Tag tag) {
                if (tag == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null compound tag");
                } else {
                    FriendlyByteBuf.writeNbt(byteBuf, tag);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> sizeTracker) {
        return tagCodec(sizeTracker).map(nbt -> {
            if (nbt instanceof CompoundTag) {
                return (CompoundTag)nbt;
            } else {
                throw new DecoderException("Not a compound tag: " + nbt);
            }
        }, nbt -> (Tag)nbt);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodecTrusted(Codec<T> codec) {
        return fromCodec(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return fromCodec(codec, () -> NbtAccounter.create(2097152L));
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec, Supplier<NbtAccounter> sizeTracker) {
        return tagCodec(sizeTracker)
            .map(
                nbt -> codec.parse(NbtOps.INSTANCE, nbt).getOrThrow(error -> new DecoderException("Failed to decode: " + error + " " + nbt)),
                value -> codec.encodeStart(NbtOps.INSTANCE, (T)value).getOrThrow(error -> new EncoderException("Failed to encode: " + error + " " + value))
            );
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistriesTrusted(Codec<T> codec) {
        return fromCodecWithRegistries(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodecWithRegistries(codec, () -> NbtAccounter.create(2097152L));
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec, Supplier<NbtAccounter> sizeTracker) {
        final StreamCodec<ByteBuf, Tag> streamCodec = tagCodec(sizeTracker);
        return new StreamCodec<RegistryFriendlyByteBuf, T>() {
            @Override
            public T decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
                Tag tag = streamCodec.decode(registryFriendlyByteBuf);
                RegistryOps<Tag> registryOps = registryFriendlyByteBuf.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                return codec.parse(registryOps, tag).getOrThrow(error -> new DecoderException("Failed to decode: " + error + " " + tag));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, T object) {
                RegistryOps<Tag> registryOps = registryFriendlyByteBuf.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                Tag tag = codec.encodeStart(registryOps, object).getOrThrow(error -> new EncoderException("Failed to encode: " + error + " " + object));
                streamCodec.encode(registryFriendlyByteBuf, tag);
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(StreamCodec<B, V> codec) {
        return new StreamCodec<B, Optional<V>>() {
            @Override
            public Optional<V> decode(B byteBuf) {
                return byteBuf.readBoolean() ? Optional.of(codec.decode(byteBuf)) : Optional.empty();
            }

            @Override
            public void encode(B byteBuf, Optional<V> optional) {
                if (optional.isPresent()) {
                    byteBuf.writeBoolean(true);
                    codec.encode(byteBuf, optional.get());
                } else {
                    byteBuf.writeBoolean(false);
                }
            }
        };
    }

    static int readCount(ByteBuf buf, int maxSize) {
        int i = VarInt.read(buf);
        if (i > maxSize) {
            throw new DecoderException(i + " elements exceeded max size of: " + maxSize);
        } else {
            return i;
        }
    }

    static void writeCount(ByteBuf buf, int size, int maxSize) {
        if (size > maxSize) {
            throw new EncoderException(size + " elements exceeded max size of: " + maxSize);
        } else {
            VarInt.write(buf, size);
        }
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory, StreamCodec<? super B, V> elementCodec) {
        return collection(factory, elementCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
        IntFunction<C> factory, StreamCodec<? super B, V> elementCodec, int maxSize
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B byteBuf) {
                int i = ByteBufCodecs.readCount(byteBuf, maxSize);
                C collection = factory.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    collection.add(elementCodec.decode(byteBuf));
                }

                return collection;
            }

            @Override
            public void encode(B byteBuf, C collection) {
                ByteBufCodecs.writeCount(byteBuf, collection.size(), maxSize);

                for (V object : collection) {
                    elementCodec.encode(byteBuf, object);
                }
            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> collectionFactory) {
        return codec -> collection(collectionFactory, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxLength) {
        return codec -> collection(ArrayList::new, codec, maxLength);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec
    ) {
        return map(factory, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec, int maxSize
    ) {
        return new StreamCodec<B, M>() {
            @Override
            public void encode(B byteBuf, M map) {
                ByteBufCodecs.writeCount(byteBuf, map.size(), maxSize);
                map.forEach((k, v) -> {
                    keyCodec.encode(byteBuf, (K)k);
                    valueCodec.encode(byteBuf, (V)v);
                });
            }

            @Override
            public M decode(B byteBuf) {
                int i = ByteBufCodecs.readCount(byteBuf, maxSize);
                M map = (M)factory.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    K object = keyCodec.decode(byteBuf);
                    V object2 = valueCodec.decode(byteBuf);
                    map.put(object, object2);
                }

                return map;
            }
        };
    }

    static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(StreamCodec<? super B, L> left, StreamCodec<? super B, R> right) {
        return new StreamCodec<B, Either<L, R>>() {
            @Override
            public Either<L, R> decode(B byteBuf) {
                return byteBuf.readBoolean() ? Either.left(left.decode(byteBuf)) : Either.right(right.decode(byteBuf));
            }

            @Override
            public void encode(B byteBuf, Either<L, R> either) {
                either.ifLeft(leftxx -> {
                    byteBuf.writeBoolean(true);
                    left.encode(byteBuf, (L)leftxx);
                }).ifRight(rightxx -> {
                    byteBuf.writeBoolean(false);
                    right.encode(byteBuf, (R)rightxx);
                });
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IntFunction<T> indexToValue, ToIntFunction<T> valueToIndex) {
        return new StreamCodec<ByteBuf, T>() {
            @Override
            public T decode(ByteBuf byteBuf) {
                int i = VarInt.read(byteBuf);
                return indexToValue.apply(i);
            }

            @Override
            public void encode(ByteBuf byteBuf, T object) {
                int i = valueToIndex.applyAsInt(object);
                VarInt.write(byteBuf, i);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> iterable) {
        return idMapper(iterable::byIdOrThrow, iterable::getIdOrThrow);
    }

    private static <T, R> StreamCodec<RegistryFriendlyByteBuf, R> registry(
        ResourceKey<? extends Registry<T>> registry, Function<Registry<T>, IdMap<R>> registryTransformer
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, R>() {
            private IdMap<R> getRegistryOrThrow(RegistryFriendlyByteBuf buf) {
                return registryTransformer.apply(buf.registryAccess().registryOrThrow(registry));
            }

            @Override
            public R decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
                int i = VarInt.read(registryFriendlyByteBuf);
                return (R)this.getRegistryOrThrow(registryFriendlyByteBuf).byIdOrThrow(i);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, R object) {
                int i = this.getRegistryOrThrow(registryFriendlyByteBuf).getIdOrThrow(object);
                VarInt.write(registryFriendlyByteBuf, i);
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> registry) {
        return registry(registry, registryx -> registryx);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> registry) {
        return registry(registry, Registry::asHolderIdMap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holder(
        ResourceKey<? extends Registry<T>> registry, StreamCodec<? super RegistryFriendlyByteBuf, T> directCodec
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, Holder<T>>() {
            private static final int DIRECT_HOLDER_ID = 0;

            private IdMap<Holder<T>> getRegistryOrThrow(RegistryFriendlyByteBuf buf) {
                return buf.registryAccess().registryOrThrow(registry).asHolderIdMap();
            }

            @Override
            public Holder<T> decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
                int i = VarInt.read(registryFriendlyByteBuf);
                return i == 0
                    ? Holder.direct(directCodec.decode(registryFriendlyByteBuf))
                    : (Holder)this.getRegistryOrThrow(registryFriendlyByteBuf).byIdOrThrow(i - 1);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, Holder<T> holder) {
                switch (holder.kind()) {
                    case REFERENCE:
                        int i = this.getRegistryOrThrow(registryFriendlyByteBuf).getIdOrThrow(holder);
                        VarInt.write(registryFriendlyByteBuf, i + 1);
                        break;
                    case DIRECT:
                        VarInt.write(registryFriendlyByteBuf, 0);
                        directCodec.encode(registryFriendlyByteBuf, holder.value());
                }
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>> holderSet(ResourceKey<? extends Registry<T>> registryRef) {
        return new StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>>() {
            private static final int NAMED_SET = -1;
            private final StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderCodec = ByteBufCodecs.holderRegistry(registryRef);

            @Override
            public HolderSet<T> decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
                int i = VarInt.read(registryFriendlyByteBuf) - 1;
                if (i == -1) {
                    Registry<T> registry = registryFriendlyByteBuf.registryAccess().registryOrThrow(registryRef);
                    return registry.getTag(TagKey.create(registryRef, ResourceLocation.STREAM_CODEC.decode(registryFriendlyByteBuf))).orElseThrow();
                } else {
                    List<Holder<T>> list = new ArrayList<>(Math.min(i, 65536));

                    for (int j = 0; j < i; j++) {
                        list.add(this.holderCodec.decode(registryFriendlyByteBuf));
                    }

                    return HolderSet.direct(list);
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, HolderSet<T> holderSet) {
                Optional<TagKey<T>> optional = holderSet.unwrapKey();
                if (optional.isPresent()) {
                    VarInt.write(registryFriendlyByteBuf, 0);
                    ResourceLocation.STREAM_CODEC.encode(registryFriendlyByteBuf, optional.get().location());
                } else {
                    VarInt.write(registryFriendlyByteBuf, holderSet.size() + 1);

                    for (Holder<T> holder : holderSet) {
                        this.holderCodec.encode(registryFriendlyByteBuf, holder);
                    }
                }
            }
        };
    }
}
