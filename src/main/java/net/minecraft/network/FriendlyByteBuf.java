package net.minecraft.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ByteProcessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FriendlyByteBuf extends ByteBuf {

    public static final int DEFAULT_NBT_QUOTA = 2097152;
    private final ByteBuf source;
    public static final short MAX_STRING_LENGTH = Short.MAX_VALUE;
    public static final int MAX_COMPONENT_STRING_LENGTH = 262144;
    private static final int PUBLIC_KEY_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_HEADER_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_LENGTH = 512;
    private static final Gson GSON = new Gson();

    public FriendlyByteBuf(ByteBuf parent) {
        this.source = parent;
    }

    /** @deprecated */
    @Deprecated
    public <T> T readWithCodecTrusted(DynamicOps<Tag> ops, Codec<T> codec) {
        return this.readWithCodec(ops, codec, NbtAccounter.unlimitedHeap());
    }

    /** @deprecated */
    @Deprecated
    public <T> T readWithCodec(DynamicOps<Tag> ops, Codec<T> codec, NbtAccounter sizeTracker) {
        Tag nbtbase = this.readNbt(sizeTracker);

        return codec.parse(ops, nbtbase).getOrThrow((s) -> {
            return new DecoderException("Failed to decode: " + s + " " + String.valueOf(nbtbase));
        });
    }

    /** @deprecated */
    @Deprecated
    public <T> FriendlyByteBuf writeWithCodec(DynamicOps<Tag> ops, Codec<T> codec, T value) {
        Tag nbtbase = (Tag) codec.encodeStart(ops, value).getOrThrow((s) -> {
            return new EncoderException("Failed to encode: " + s + " " + String.valueOf(value));
        });

        this.writeNbt(nbtbase);
        return this;
    }

    public <T> T readJsonWithCodec(Codec<T> codec) {
        JsonElement jsonelement = (JsonElement) GsonHelper.fromJson(FriendlyByteBuf.GSON, this.readUtf(), JsonElement.class);
        DataResult<T> dataresult = codec.parse(JsonOps.INSTANCE, jsonelement);

        return dataresult.getOrThrow((s) -> {
            return new DecoderException("Failed to decode json: " + s);
        });
    }

    public <T> void writeJsonWithCodec(Codec<T> codec, T value) {
        DataResult<JsonElement> dataresult = codec.encodeStart(JsonOps.INSTANCE, value);

        this.writeUtf(FriendlyByteBuf.GSON.toJson((JsonElement) dataresult.getOrThrow((s) -> {
            return new EncoderException("Failed to encode: " + s + " " + String.valueOf(value));
        })));
    }

    public static <T> IntFunction<T> limitValue(IntFunction<T> applier, int max) {
        return (j) -> {
            if (j > max) {
                throw new DecoderException("Value " + j + " is larger than limit " + max);
            } else {
                return applier.apply(j);
            }
        };
    }

    public <T, C extends Collection<T>> C readCollection(IntFunction<C> collectionFactory, StreamDecoder<? super FriendlyByteBuf, T> reader) {
        int i = this.readVarInt();
        C c0 = collectionFactory.apply(i); // CraftBukkit - decompile error

        for (int j = 0; j < i; ++j) {
            c0.add(reader.decode(this));
        }

        return c0;
    }

    public <T> void writeCollection(Collection<T> collection, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        this.writeVarInt(collection.size());
        Iterator<T> iterator = collection.iterator(); // CraftBukkit - decompile error

        while (iterator.hasNext()) {
            T t0 = iterator.next();

            writer.encode(this, t0);
        }

    }

    public <T> List<T> readList(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return (List) this.readCollection(Lists::newArrayListWithCapacity, reader);
    }

    public IntList readIntIdList() {
        int i = this.readVarInt();
        IntArrayList intarraylist = new IntArrayList();

        for (int j = 0; j < i; ++j) {
            intarraylist.add(this.readVarInt());
        }

        return intarraylist;
    }

    public void writeIntIdList(IntList list) {
        this.writeVarInt(list.size());
        list.forEach((java.util.function.IntConsumer) this::writeVarInt); // CraftBukkit - decompile error
    }

    public <K, V, M extends Map<K, V>> M readMap(IntFunction<M> mapFactory, StreamDecoder<? super FriendlyByteBuf, K> keyReader, StreamDecoder<? super FriendlyByteBuf, V> valueReader) {
        int i = this.readVarInt();
        M m0 = mapFactory.apply(i); // CraftBukkit - decompile error

        for (int j = 0; j < i; ++j) {
            K k0 = keyReader.decode(this);
            V v0 = valueReader.decode(this);

            m0.put(k0, v0);
        }

        return m0;
    }

    public <K, V> Map<K, V> readMap(StreamDecoder<? super FriendlyByteBuf, K> keyReader, StreamDecoder<? super FriendlyByteBuf, V> valueReader) {
        return this.readMap(Maps::newHashMapWithExpectedSize, keyReader, valueReader);
    }

    public <K, V> void writeMap(Map<K, V> map, StreamEncoder<? super FriendlyByteBuf, K> keyWriter, StreamEncoder<? super FriendlyByteBuf, V> valueWriter) {
        this.writeVarInt(map.size());
        map.forEach((object, object1) -> {
            keyWriter.encode(this, object);
            valueWriter.encode(this, object1);
        });
    }

    public void readWithCount(Consumer<FriendlyByteBuf> consumer) {
        int i = this.readVarInt();

        for (int j = 0; j < i; ++j) {
            consumer.accept(this);
        }

    }

    public <E extends Enum<E>> void writeEnumSet(EnumSet<E> enumSet, Class<E> type) {
        E[] ae = type.getEnumConstants(); // CraftBukkit - decompile error
        BitSet bitset = new BitSet(ae.length);

        for (int i = 0; i < ae.length; ++i) {
            bitset.set(i, enumSet.contains(ae[i]));
        }

        this.writeFixedBitSet(bitset, ae.length);
    }

    public <E extends Enum<E>> EnumSet<E> readEnumSet(Class<E> type) {
        E[] ae = type.getEnumConstants(); // CraftBukkit - decompile error
        BitSet bitset = this.readFixedBitSet(ae.length);
        EnumSet<E> enumset = EnumSet.noneOf(type);

        for (int i = 0; i < ae.length; ++i) {
            if (bitset.get(i)) {
                enumset.add(ae[i]);
            }
        }

        return enumset;
    }

    public <T> void writeOptional(Optional<T> value, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        if (value.isPresent()) {
            this.writeBoolean(true);
            writer.encode(this, value.get());
        } else {
            this.writeBoolean(false);
        }

    }

    public <T> Optional<T> readOptional(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return this.readBoolean() ? Optional.of(reader.decode(this)) : Optional.empty();
    }

    @Nullable
    public <T> T readNullable(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return FriendlyByteBuf.readNullable(this, reader);
    }

    @Nullable
    public static <T, B extends ByteBuf> T readNullable(B buf, StreamDecoder<? super B, T> reader) {
        return buf.readBoolean() ? reader.decode(buf) : null;
    }

    public <T> void writeNullable(@Nullable T value, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        FriendlyByteBuf.writeNullable(this, value, writer);
    }

    public static <T, B extends ByteBuf> void writeNullable(B buf, @Nullable T value, StreamEncoder<? super B, T> writer) {
        if (value != null) {
            buf.writeBoolean(true);
            writer.encode(buf, value);
        } else {
            buf.writeBoolean(false);
        }

    }

    public byte[] readByteArray() {
        return FriendlyByteBuf.readByteArray(this);
    }

    public static byte[] readByteArray(ByteBuf buf) {
        return FriendlyByteBuf.readByteArray(buf, buf.readableBytes());
    }

    public FriendlyByteBuf writeByteArray(byte[] array) {
        FriendlyByteBuf.writeByteArray(this, array);
        return this;
    }

    public static void writeByteArray(ByteBuf buf, byte[] array) {
        VarInt.write(buf, array.length);
        buf.writeBytes(array);
    }

    public byte[] readByteArray(int maxSize) {
        return FriendlyByteBuf.readByteArray(this, maxSize);
    }

    public static byte[] readByteArray(ByteBuf buf, int maxSize) {
        int j = VarInt.read(buf);

        if (j > maxSize) {
            throw new DecoderException("ByteArray with size " + j + " is bigger than allowed " + maxSize);
        } else {
            byte[] abyte = new byte[j];

            buf.readBytes(abyte);
            return abyte;
        }
    }

    public FriendlyByteBuf writeVarIntArray(int[] array) {
        this.writeVarInt(array.length);
        int[] aint1 = array;
        int i = array.length;

        for (int j = 0; j < i; ++j) {
            int k = aint1[j];

            this.writeVarInt(k);
        }

        return this;
    }

    public int[] readVarIntArray() {
        return this.readVarIntArray(this.readableBytes());
    }

    public int[] readVarIntArray(int maxSize) {
        int j = this.readVarInt();

        if (j > maxSize) {
            throw new DecoderException("VarIntArray with size " + j + " is bigger than allowed " + maxSize);
        } else {
            int[] aint = new int[j];

            for (int k = 0; k < aint.length; ++k) {
                aint[k] = this.readVarInt();
            }

            return aint;
        }
    }

    public FriendlyByteBuf writeLongArray(long[] array) {
        this.writeVarInt(array.length);
        long[] along1 = array;
        int i = array.length;

        for (int j = 0; j < i; ++j) {
            long k = along1[j];

            this.writeLong(k);
        }

        return this;
    }

    public long[] readLongArray() {
        return this.readLongArray((long[]) null);
    }

    public long[] readLongArray(@Nullable long[] toArray) {
        return this.readLongArray(toArray, this.readableBytes() / 8);
    }

    public long[] readLongArray(@Nullable long[] toArray, int maxSize) {
        int j = this.readVarInt();

        if (toArray == null || toArray.length != j) {
            if (j > maxSize) {
                throw new DecoderException("LongArray with size " + j + " is bigger than allowed " + maxSize);
            }

            toArray = new long[j];
        }

        for (int k = 0; k < toArray.length; ++k) {
            toArray[k] = this.readLong();
        }

        return toArray;
    }

    public BlockPos readBlockPos() {
        return FriendlyByteBuf.readBlockPos(this);
    }

    public static BlockPos readBlockPos(ByteBuf buf) {
        return BlockPos.of(buf.readLong());
    }

    public FriendlyByteBuf writeBlockPos(BlockPos pos) {
        FriendlyByteBuf.writeBlockPos(this, pos);
        return this;
    }

    public static void writeBlockPos(ByteBuf buf, BlockPos pos) {
        buf.writeLong(pos.asLong());
    }

    public ChunkPos readChunkPos() {
        return new ChunkPos(this.readLong());
    }

    public FriendlyByteBuf writeChunkPos(ChunkPos pos) {
        this.writeLong(pos.toLong());
        return this;
    }

    public SectionPos readSectionPos() {
        return SectionPos.of(this.readLong());
    }

    public FriendlyByteBuf writeSectionPos(SectionPos pos) {
        this.writeLong(pos.asLong());
        return this;
    }

    public GlobalPos readGlobalPos() {
        ResourceKey<Level> resourcekey = this.readResourceKey(Registries.DIMENSION);
        BlockPos blockposition = this.readBlockPos();

        return GlobalPos.of(resourcekey, blockposition);
    }

    public void writeGlobalPos(GlobalPos pos) {
        this.writeResourceKey(pos.dimension());
        this.writeBlockPos(pos.pos());
    }

    public Vector3f readVector3f() {
        return FriendlyByteBuf.readVector3f(this);
    }

    public static Vector3f readVector3f(ByteBuf buf) {
        return new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void writeVector3f(Vector3f vector3f) {
        FriendlyByteBuf.writeVector3f(this, vector3f);
    }

    public static void writeVector3f(ByteBuf buf, Vector3f vector) {
        buf.writeFloat(vector.x());
        buf.writeFloat(vector.y());
        buf.writeFloat(vector.z());
    }

    public Quaternionf readQuaternion() {
        return FriendlyByteBuf.readQuaternion(this);
    }

    public static Quaternionf readQuaternion(ByteBuf buf) {
        return new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void writeQuaternion(Quaternionf quaternionf) {
        FriendlyByteBuf.writeQuaternion(this, quaternionf);
    }

    public static void writeQuaternion(ByteBuf buf, Quaternionf quaternion) {
        buf.writeFloat(quaternion.x);
        buf.writeFloat(quaternion.y);
        buf.writeFloat(quaternion.z);
        buf.writeFloat(quaternion.w);
    }

    public Vec3 readVec3() {
        return new Vec3(this.readDouble(), this.readDouble(), this.readDouble());
    }

    public void writeVec3(Vec3 vec) {
        this.writeDouble(vec.x());
        this.writeDouble(vec.y());
        this.writeDouble(vec.z());
    }

    public <T extends Enum<T>> T readEnum(Class<T> enumClass) {
        return ((T[]) enumClass.getEnumConstants())[this.readVarInt()]; // CraftBukkit - fix decompile error
    }

    public FriendlyByteBuf writeEnum(Enum<?> instance) {
        return this.writeVarInt(instance.ordinal());
    }

    public <T> T readById(IntFunction<T> idToValue) {
        int i = this.readVarInt();

        return idToValue.apply(i);
    }

    public <T> FriendlyByteBuf writeById(ToIntFunction<T> valueToId, T value) {
        int i = valueToId.applyAsInt(value);

        return this.writeVarInt(i);
    }

    public int readVarInt() {
        return VarInt.read(this.source);
    }

    public long readVarLong() {
        return VarLong.read(this.source);
    }

    public FriendlyByteBuf writeUUID(UUID uuid) {
        FriendlyByteBuf.writeUUID(this, uuid);
        return this;
    }

    public static void writeUUID(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public UUID readUUID() {
        return FriendlyByteBuf.readUUID(this);
    }

    public static UUID readUUID(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public FriendlyByteBuf writeVarInt(int value) {
        VarInt.write(this.source, value);
        return this;
    }

    public FriendlyByteBuf writeVarLong(long value) {
        VarLong.write(this.source, value);
        return this;
    }

    public FriendlyByteBuf writeNbt(@Nullable Tag nbt) {
        FriendlyByteBuf.writeNbt(this, nbt);
        return this;
    }

    public static void writeNbt(ByteBuf buf, @Nullable Tag nbt) {
        if (nbt == null) {
            nbt = EndTag.INSTANCE;
        }

        try {
            NbtIo.writeAnyTag((Tag) nbt, new ByteBufOutputStream(buf));
        } catch (Exception ioexception) { // CraftBukkit - IOException -> Exception
            throw new EncoderException(ioexception);
        }
    }

    @Nullable
    public CompoundTag readNbt() {
        return FriendlyByteBuf.readNbt((ByteBuf) this);
    }

    @Nullable
    public static CompoundTag readNbt(ByteBuf buf) {
        Tag nbtbase = FriendlyByteBuf.readNbt(buf, NbtAccounter.create(2097152L));

        if (nbtbase != null && !(nbtbase instanceof CompoundTag)) {
            throw new DecoderException("Not a compound tag: " + String.valueOf(nbtbase));
        } else {
            return (CompoundTag) nbtbase;
        }
    }

    @Nullable
    public static Tag readNbt(ByteBuf buf, NbtAccounter sizeTracker) {
        try {
            Tag nbtbase = NbtIo.readAnyTag(new ByteBufInputStream(buf), sizeTracker);

            return nbtbase.getId() == 0 ? null : nbtbase;
        } catch (IOException ioexception) {
            throw new EncoderException(ioexception);
        }
    }

    @Nullable
    public Tag readNbt(NbtAccounter sizeTracker) {
        return FriendlyByteBuf.readNbt(this, sizeTracker);
    }

    public String readUtf() {
        return this.readUtf(32767);
    }

    public String readUtf(int maxLength) {
        return Utf8String.read(this.source, maxLength);
    }

    public FriendlyByteBuf writeUtf(String string) {
        return this.writeUtf(string, 32767);
    }

    public FriendlyByteBuf writeUtf(String string, int maxLength) {
        Utf8String.write(this.source, string, maxLength);
        return this;
    }

    public ResourceLocation readResourceLocation() {
        return ResourceLocation.parse(this.readUtf(32767));
    }

    public FriendlyByteBuf writeResourceLocation(ResourceLocation id) {
        this.writeUtf(id.toString());
        return this;
    }

    public <T> ResourceKey<T> readResourceKey(ResourceKey<? extends Registry<T>> registryRef) {
        ResourceLocation minecraftkey = this.readResourceLocation();

        return ResourceKey.create(registryRef, minecraftkey);
    }

    public void writeResourceKey(ResourceKey<?> key) {
        this.writeResourceLocation(key.location());
    }

    public <T> ResourceKey<? extends Registry<T>> readRegistryKey() {
        ResourceLocation minecraftkey = this.readResourceLocation();

        return ResourceKey.createRegistryKey(minecraftkey);
    }

    public Date readDate() {
        return new Date(this.readLong());
    }

    public FriendlyByteBuf writeDate(Date date) {
        this.writeLong(date.getTime());
        return this;
    }

    public Instant readInstant() {
        return Instant.ofEpochMilli(this.readLong());
    }

    public void writeInstant(Instant instant) {
        this.writeLong(instant.toEpochMilli());
    }

    public PublicKey readPublicKey() {
        try {
            return Crypt.byteToPublicKey(this.readByteArray(512));
        } catch (CryptException cryptographyexception) {
            throw new DecoderException("Malformed public key bytes", cryptographyexception);
        }
    }

    public FriendlyByteBuf writePublicKey(PublicKey publicKey) {
        this.writeByteArray(publicKey.getEncoded());
        return this;
    }

    public BlockHitResult readBlockHitResult() {
        BlockPos blockposition = this.readBlockPos();
        Direction enumdirection = (Direction) this.readEnum(Direction.class);
        float f = this.readFloat();
        float f1 = this.readFloat();
        float f2 = this.readFloat();
        boolean flag = this.readBoolean();

        return new BlockHitResult(new Vec3((double) blockposition.getX() + (double) f, (double) blockposition.getY() + (double) f1, (double) blockposition.getZ() + (double) f2), enumdirection, blockposition, flag);
    }

    public void writeBlockHitResult(BlockHitResult hitResult) {
        BlockPos blockposition = hitResult.getBlockPos();

        this.writeBlockPos(blockposition);
        this.writeEnum(hitResult.getDirection());
        Vec3 vec3d = hitResult.getLocation();

        this.writeFloat((float) (vec3d.x - (double) blockposition.getX()));
        this.writeFloat((float) (vec3d.y - (double) blockposition.getY()));
        this.writeFloat((float) (vec3d.z - (double) blockposition.getZ()));
        this.writeBoolean(hitResult.isInside());
    }

    public BitSet readBitSet() {
        return BitSet.valueOf(this.readLongArray());
    }

    public void writeBitSet(BitSet bitSet) {
        this.writeLongArray(bitSet.toLongArray());
    }

    public BitSet readFixedBitSet(int size) {
        byte[] abyte = new byte[Mth.positiveCeilDiv(size, 8)];

        this.readBytes(abyte);
        return BitSet.valueOf(abyte);
    }

    public void writeFixedBitSet(BitSet bitSet, int size) {
        if (bitSet.length() > size) {
            int j = bitSet.length();

            throw new EncoderException("BitSet is larger than expected size (" + j + ">" + size + ")");
        } else {
            byte[] abyte = bitSet.toByteArray();

            this.writeBytes(Arrays.copyOf(abyte, Mth.positiveCeilDiv(size, 8)));
        }
    }

    public boolean isContiguous() {
        return this.source.isContiguous();
    }

    public int maxFastWritableBytes() {
        return this.source.maxFastWritableBytes();
    }

    public int capacity() {
        return this.source.capacity();
    }

    public FriendlyByteBuf capacity(int i) {
        this.source.capacity(i);
        return this;
    }

    public int maxCapacity() {
        return this.source.maxCapacity();
    }

    public ByteBufAllocator alloc() {
        return this.source.alloc();
    }

    public ByteOrder order() {
        return this.source.order();
    }

    public ByteBuf order(ByteOrder byteorder) {
        return this.source.order(byteorder);
    }

    public ByteBuf unwrap() {
        return this.source;
    }

    public boolean isDirect() {
        return this.source.isDirect();
    }

    public boolean isReadOnly() {
        return this.source.isReadOnly();
    }

    public ByteBuf asReadOnly() {
        return this.source.asReadOnly();
    }

    public int readerIndex() {
        return this.source.readerIndex();
    }

    public FriendlyByteBuf readerIndex(int i) {
        this.source.readerIndex(i);
        return this;
    }

    public int writerIndex() {
        return this.source.writerIndex();
    }

    public FriendlyByteBuf writerIndex(int i) {
        this.source.writerIndex(i);
        return this;
    }

    public FriendlyByteBuf setIndex(int i, int j) {
        this.source.setIndex(i, j);
        return this;
    }

    public int readableBytes() {
        return this.source.readableBytes();
    }

    public int writableBytes() {
        return this.source.writableBytes();
    }

    public int maxWritableBytes() {
        return this.source.maxWritableBytes();
    }

    public boolean isReadable() {
        return this.source.isReadable();
    }

    public boolean isReadable(int i) {
        return this.source.isReadable(i);
    }

    public boolean isWritable() {
        return this.source.isWritable();
    }

    public boolean isWritable(int i) {
        return this.source.isWritable(i);
    }

    public FriendlyByteBuf clear() {
        this.source.clear();
        return this;
    }

    public FriendlyByteBuf markReaderIndex() {
        this.source.markReaderIndex();
        return this;
    }

    public FriendlyByteBuf resetReaderIndex() {
        this.source.resetReaderIndex();
        return this;
    }

    public FriendlyByteBuf markWriterIndex() {
        this.source.markWriterIndex();
        return this;
    }

    public FriendlyByteBuf resetWriterIndex() {
        this.source.resetWriterIndex();
        return this;
    }

    public FriendlyByteBuf discardReadBytes() {
        this.source.discardReadBytes();
        return this;
    }

    public FriendlyByteBuf discardSomeReadBytes() {
        this.source.discardSomeReadBytes();
        return this;
    }

    public FriendlyByteBuf ensureWritable(int i) {
        this.source.ensureWritable(i);
        return this;
    }

    public int ensureWritable(int i, boolean flag) {
        return this.source.ensureWritable(i, flag);
    }

    public boolean getBoolean(int i) {
        return this.source.getBoolean(i);
    }

    public byte getByte(int i) {
        return this.source.getByte(i);
    }

    public short getUnsignedByte(int i) {
        return this.source.getUnsignedByte(i);
    }

    public short getShort(int i) {
        return this.source.getShort(i);
    }

    public short getShortLE(int i) {
        return this.source.getShortLE(i);
    }

    public int getUnsignedShort(int i) {
        return this.source.getUnsignedShort(i);
    }

    public int getUnsignedShortLE(int i) {
        return this.source.getUnsignedShortLE(i);
    }

    public int getMedium(int i) {
        return this.source.getMedium(i);
    }

    public int getMediumLE(int i) {
        return this.source.getMediumLE(i);
    }

    public int getUnsignedMedium(int i) {
        return this.source.getUnsignedMedium(i);
    }

    public int getUnsignedMediumLE(int i) {
        return this.source.getUnsignedMediumLE(i);
    }

    public int getInt(int i) {
        return this.source.getInt(i);
    }

    public int getIntLE(int i) {
        return this.source.getIntLE(i);
    }

    public long getUnsignedInt(int i) {
        return this.source.getUnsignedInt(i);
    }

    public long getUnsignedIntLE(int i) {
        return this.source.getUnsignedIntLE(i);
    }

    public long getLong(int i) {
        return this.source.getLong(i);
    }

    public long getLongLE(int i) {
        return this.source.getLongLE(i);
    }

    public char getChar(int i) {
        return this.source.getChar(i);
    }

    public float getFloat(int i) {
        return this.source.getFloat(i);
    }

    public double getDouble(int i) {
        return this.source.getDouble(i);
    }

    public FriendlyByteBuf getBytes(int i, ByteBuf bytebuf) {
        this.source.getBytes(i, bytebuf);
        return this;
    }

    public FriendlyByteBuf getBytes(int i, ByteBuf bytebuf, int j) {
        this.source.getBytes(i, bytebuf, j);
        return this;
    }

    public FriendlyByteBuf getBytes(int i, ByteBuf bytebuf, int j, int k) {
        this.source.getBytes(i, bytebuf, j, k);
        return this;
    }

    public FriendlyByteBuf getBytes(int i, byte[] abyte) {
        this.source.getBytes(i, abyte);
        return this;
    }

    public FriendlyByteBuf getBytes(int i, byte[] abyte, int j, int k) {
        this.source.getBytes(i, abyte, j, k);
        return this;
    }

    public FriendlyByteBuf getBytes(int i, ByteBuffer bytebuffer) {
        this.source.getBytes(i, bytebuffer);
        return this;
    }

    public FriendlyByteBuf getBytes(int i, OutputStream outputstream, int j) throws IOException {
        this.source.getBytes(i, outputstream, j);
        return this;
    }

    public int getBytes(int i, GatheringByteChannel gatheringbytechannel, int j) throws IOException {
        return this.source.getBytes(i, gatheringbytechannel, j);
    }

    public int getBytes(int i, FileChannel filechannel, long j, int k) throws IOException {
        return this.source.getBytes(i, filechannel, j, k);
    }

    public CharSequence getCharSequence(int i, int j, Charset charset) {
        return this.source.getCharSequence(i, j, charset);
    }

    public FriendlyByteBuf setBoolean(int i, boolean flag) {
        this.source.setBoolean(i, flag);
        return this;
    }

    public FriendlyByteBuf setByte(int i, int j) {
        this.source.setByte(i, j);
        return this;
    }

    public FriendlyByteBuf setShort(int i, int j) {
        this.source.setShort(i, j);
        return this;
    }

    public FriendlyByteBuf setShortLE(int i, int j) {
        this.source.setShortLE(i, j);
        return this;
    }

    public FriendlyByteBuf setMedium(int i, int j) {
        this.source.setMedium(i, j);
        return this;
    }

    public FriendlyByteBuf setMediumLE(int i, int j) {
        this.source.setMediumLE(i, j);
        return this;
    }

    public FriendlyByteBuf setInt(int i, int j) {
        this.source.setInt(i, j);
        return this;
    }

    public FriendlyByteBuf setIntLE(int i, int j) {
        this.source.setIntLE(i, j);
        return this;
    }

    public FriendlyByteBuf setLong(int i, long j) {
        this.source.setLong(i, j);
        return this;
    }

    public FriendlyByteBuf setLongLE(int i, long j) {
        this.source.setLongLE(i, j);
        return this;
    }

    public FriendlyByteBuf setChar(int i, int j) {
        this.source.setChar(i, j);
        return this;
    }

    public FriendlyByteBuf setFloat(int i, float f) {
        this.source.setFloat(i, f);
        return this;
    }

    public FriendlyByteBuf setDouble(int i, double d0) {
        this.source.setDouble(i, d0);
        return this;
    }

    public FriendlyByteBuf setBytes(int i, ByteBuf bytebuf) {
        this.source.setBytes(i, bytebuf);
        return this;
    }

    public FriendlyByteBuf setBytes(int i, ByteBuf bytebuf, int j) {
        this.source.setBytes(i, bytebuf, j);
        return this;
    }

    public FriendlyByteBuf setBytes(int i, ByteBuf bytebuf, int j, int k) {
        this.source.setBytes(i, bytebuf, j, k);
        return this;
    }

    public FriendlyByteBuf setBytes(int i, byte[] abyte) {
        this.source.setBytes(i, abyte);
        return this;
    }

    public FriendlyByteBuf setBytes(int i, byte[] abyte, int j, int k) {
        this.source.setBytes(i, abyte, j, k);
        return this;
    }

    public FriendlyByteBuf setBytes(int i, ByteBuffer bytebuffer) {
        this.source.setBytes(i, bytebuffer);
        return this;
    }

    public int setBytes(int i, InputStream inputstream, int j) throws IOException {
        return this.source.setBytes(i, inputstream, j);
    }

    public int setBytes(int i, ScatteringByteChannel scatteringbytechannel, int j) throws IOException {
        return this.source.setBytes(i, scatteringbytechannel, j);
    }

    public int setBytes(int i, FileChannel filechannel, long j, int k) throws IOException {
        return this.source.setBytes(i, filechannel, j, k);
    }

    public FriendlyByteBuf setZero(int i, int j) {
        this.source.setZero(i, j);
        return this;
    }

    public int setCharSequence(int i, CharSequence charsequence, Charset charset) {
        return this.source.setCharSequence(i, charsequence, charset);
    }

    public boolean readBoolean() {
        return this.source.readBoolean();
    }

    public byte readByte() {
        return this.source.readByte();
    }

    public short readUnsignedByte() {
        return this.source.readUnsignedByte();
    }

    public short readShort() {
        return this.source.readShort();
    }

    public short readShortLE() {
        return this.source.readShortLE();
    }

    public int readUnsignedShort() {
        return this.source.readUnsignedShort();
    }

    public int readUnsignedShortLE() {
        return this.source.readUnsignedShortLE();
    }

    public int readMedium() {
        return this.source.readMedium();
    }

    public int readMediumLE() {
        return this.source.readMediumLE();
    }

    public int readUnsignedMedium() {
        return this.source.readUnsignedMedium();
    }

    public int readUnsignedMediumLE() {
        return this.source.readUnsignedMediumLE();
    }

    public int readInt() {
        return this.source.readInt();
    }

    public int readIntLE() {
        return this.source.readIntLE();
    }

    public long readUnsignedInt() {
        return this.source.readUnsignedInt();
    }

    public long readUnsignedIntLE() {
        return this.source.readUnsignedIntLE();
    }

    public long readLong() {
        return this.source.readLong();
    }

    public long readLongLE() {
        return this.source.readLongLE();
    }

    public char readChar() {
        return this.source.readChar();
    }

    public float readFloat() {
        return this.source.readFloat();
    }

    public double readDouble() {
        return this.source.readDouble();
    }

    public ByteBuf readBytes(int i) {
        return this.source.readBytes(i);
    }

    public ByteBuf readSlice(int i) {
        return this.source.readSlice(i);
    }

    public ByteBuf readRetainedSlice(int i) {
        return this.source.readRetainedSlice(i);
    }

    public FriendlyByteBuf readBytes(ByteBuf bytebuf) {
        this.source.readBytes(bytebuf);
        return this;
    }

    public FriendlyByteBuf readBytes(ByteBuf bytebuf, int i) {
        this.source.readBytes(bytebuf, i);
        return this;
    }

    public FriendlyByteBuf readBytes(ByteBuf bytebuf, int i, int j) {
        this.source.readBytes(bytebuf, i, j);
        return this;
    }

    public FriendlyByteBuf readBytes(byte[] abyte) {
        this.source.readBytes(abyte);
        return this;
    }

    public FriendlyByteBuf readBytes(byte[] abyte, int i, int j) {
        this.source.readBytes(abyte, i, j);
        return this;
    }

    public FriendlyByteBuf readBytes(ByteBuffer bytebuffer) {
        this.source.readBytes(bytebuffer);
        return this;
    }

    public FriendlyByteBuf readBytes(OutputStream outputstream, int i) throws IOException {
        this.source.readBytes(outputstream, i);
        return this;
    }

    public int readBytes(GatheringByteChannel gatheringbytechannel, int i) throws IOException {
        return this.source.readBytes(gatheringbytechannel, i);
    }

    public CharSequence readCharSequence(int i, Charset charset) {
        return this.source.readCharSequence(i, charset);
    }

    public int readBytes(FileChannel filechannel, long i, int j) throws IOException {
        return this.source.readBytes(filechannel, i, j);
    }

    public FriendlyByteBuf skipBytes(int i) {
        this.source.skipBytes(i);
        return this;
    }

    public FriendlyByteBuf writeBoolean(boolean flag) {
        this.source.writeBoolean(flag);
        return this;
    }

    public FriendlyByteBuf writeByte(int i) {
        this.source.writeByte(i);
        return this;
    }

    public FriendlyByteBuf writeShort(int i) {
        this.source.writeShort(i);
        return this;
    }

    public FriendlyByteBuf writeShortLE(int i) {
        this.source.writeShortLE(i);
        return this;
    }

    public FriendlyByteBuf writeMedium(int i) {
        this.source.writeMedium(i);
        return this;
    }

    public FriendlyByteBuf writeMediumLE(int i) {
        this.source.writeMediumLE(i);
        return this;
    }

    public FriendlyByteBuf writeInt(int i) {
        this.source.writeInt(i);
        return this;
    }

    public FriendlyByteBuf writeIntLE(int i) {
        this.source.writeIntLE(i);
        return this;
    }

    public FriendlyByteBuf writeLong(long i) {
        this.source.writeLong(i);
        return this;
    }

    public FriendlyByteBuf writeLongLE(long i) {
        this.source.writeLongLE(i);
        return this;
    }

    public FriendlyByteBuf writeChar(int i) {
        this.source.writeChar(i);
        return this;
    }

    public FriendlyByteBuf writeFloat(float f) {
        this.source.writeFloat(f);
        return this;
    }

    public FriendlyByteBuf writeDouble(double d0) {
        this.source.writeDouble(d0);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuf bytebuf) {
        this.source.writeBytes(bytebuf);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuf bytebuf, int i) {
        this.source.writeBytes(bytebuf, i);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuf bytebuf, int i, int j) {
        this.source.writeBytes(bytebuf, i, j);
        return this;
    }

    public FriendlyByteBuf writeBytes(byte[] abyte) {
        this.source.writeBytes(abyte);
        return this;
    }

    public FriendlyByteBuf writeBytes(byte[] abyte, int i, int j) {
        this.source.writeBytes(abyte, i, j);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuffer bytebuffer) {
        this.source.writeBytes(bytebuffer);
        return this;
    }

    public int writeBytes(InputStream inputstream, int i) throws IOException {
        return this.source.writeBytes(inputstream, i);
    }

    public int writeBytes(ScatteringByteChannel scatteringbytechannel, int i) throws IOException {
        return this.source.writeBytes(scatteringbytechannel, i);
    }

    public int writeBytes(FileChannel filechannel, long i, int j) throws IOException {
        return this.source.writeBytes(filechannel, i, j);
    }

    public FriendlyByteBuf writeZero(int i) {
        this.source.writeZero(i);
        return this;
    }

    public int writeCharSequence(CharSequence charsequence, Charset charset) {
        return this.source.writeCharSequence(charsequence, charset);
    }

    public int indexOf(int i, int j, byte b0) {
        return this.source.indexOf(i, j, b0);
    }

    public int bytesBefore(byte b0) {
        return this.source.bytesBefore(b0);
    }

    public int bytesBefore(int i, byte b0) {
        return this.source.bytesBefore(i, b0);
    }

    public int bytesBefore(int i, int j, byte b0) {
        return this.source.bytesBefore(i, j, b0);
    }

    public int forEachByte(ByteProcessor byteprocessor) {
        return this.source.forEachByte(byteprocessor);
    }

    public int forEachByte(int i, int j, ByteProcessor byteprocessor) {
        return this.source.forEachByte(i, j, byteprocessor);
    }

    public int forEachByteDesc(ByteProcessor byteprocessor) {
        return this.source.forEachByteDesc(byteprocessor);
    }

    public int forEachByteDesc(int i, int j, ByteProcessor byteprocessor) {
        return this.source.forEachByteDesc(i, j, byteprocessor);
    }

    public ByteBuf copy() {
        return this.source.copy();
    }

    public ByteBuf copy(int i, int j) {
        return this.source.copy(i, j);
    }

    public ByteBuf slice() {
        return this.source.slice();
    }

    public ByteBuf retainedSlice() {
        return this.source.retainedSlice();
    }

    public ByteBuf slice(int i, int j) {
        return this.source.slice(i, j);
    }

    public ByteBuf retainedSlice(int i, int j) {
        return this.source.retainedSlice(i, j);
    }

    public ByteBuf duplicate() {
        return this.source.duplicate();
    }

    public ByteBuf retainedDuplicate() {
        return this.source.retainedDuplicate();
    }

    public int nioBufferCount() {
        return this.source.nioBufferCount();
    }

    public ByteBuffer nioBuffer() {
        return this.source.nioBuffer();
    }

    public ByteBuffer nioBuffer(int i, int j) {
        return this.source.nioBuffer(i, j);
    }

    public ByteBuffer internalNioBuffer(int i, int j) {
        return this.source.internalNioBuffer(i, j);
    }

    public ByteBuffer[] nioBuffers() {
        return this.source.nioBuffers();
    }

    public ByteBuffer[] nioBuffers(int i, int j) {
        return this.source.nioBuffers(i, j);
    }

    public boolean hasArray() {
        return this.source.hasArray();
    }

    public byte[] array() {
        return this.source.array();
    }

    public int arrayOffset() {
        return this.source.arrayOffset();
    }

    public boolean hasMemoryAddress() {
        return this.source.hasMemoryAddress();
    }

    public long memoryAddress() {
        return this.source.memoryAddress();
    }

    public String toString(Charset charset) {
        return this.source.toString(charset);
    }

    public String toString(int i, int j, Charset charset) {
        return this.source.toString(i, j, charset);
    }

    public int hashCode() {
        return this.source.hashCode();
    }

    public boolean equals(Object object) {
        return this.source.equals(object);
    }

    public int compareTo(ByteBuf bytebuf) {
        return this.source.compareTo(bytebuf);
    }

    public String toString() {
        return this.source.toString();
    }

    public FriendlyByteBuf retain(int i) {
        this.source.retain(i);
        return this;
    }

    public FriendlyByteBuf retain() {
        this.source.retain();
        return this;
    }

    public FriendlyByteBuf touch() {
        this.source.touch();
        return this;
    }

    public FriendlyByteBuf touch(Object object) {
        this.source.touch(object);
        return this;
    }

    public int refCnt() {
        return this.source.refCnt();
    }

    public boolean release() {
        return this.source.release();
    }

    public boolean release(int i) {
        return this.source.release(i);
    }
}
