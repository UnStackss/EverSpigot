package net.minecraft.core;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import com.mojang.util.UndashedUuid;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class UUIDUtil {
    public static final Codec<UUID> CODEC = Codec.INT_STREAM
        .comapFlatMap(uuidStream -> Util.fixedSize(uuidStream, 4).map(UUIDUtil::uuidFromIntArray), uuid -> Arrays.stream(uuidToIntArray(uuid)));
    public static final Codec<Set<UUID>> CODEC_SET = Codec.list(CODEC).xmap(Sets::newHashSet, Lists::newArrayList);
    public static final Codec<Set<UUID>> CODEC_LINKED_SET = Codec.list(CODEC).xmap(Sets::newLinkedHashSet, Lists::newArrayList);
    public static final Codec<UUID> STRING_CODEC = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(UUID.fromString(string), Lifecycle.stable());
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> "Invalid UUID " + string + ": " + var2.getMessage());
        }
    }, UUID::toString);
    public static final Codec<UUID> AUTHLIB_CODEC = Codec.withAlternative(Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(UndashedUuid.fromStringLenient(string), Lifecycle.stable());
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> "Invalid UUID " + string + ": " + var2.getMessage());
        }
    }, UndashedUuid::toString), CODEC);
    public static final Codec<UUID> LENIENT_CODEC = Codec.withAlternative(CODEC, STRING_CODEC);
    public static final StreamCodec<ByteBuf, UUID> STREAM_CODEC = new StreamCodec<ByteBuf, UUID>() {
        @Override
        public UUID decode(ByteBuf byteBuf) {
            return FriendlyByteBuf.readUUID(byteBuf);
        }

        @Override
        public void encode(ByteBuf byteBuf, UUID uUID) {
            FriendlyByteBuf.writeUUID(byteBuf, uUID);
        }
    };
    public static final int UUID_BYTES = 16;
    private static final String UUID_PREFIX_OFFLINE_PLAYER = "OfflinePlayer:";

    private UUIDUtil() {
    }

    public static UUID uuidFromIntArray(int[] array) {
        return new UUID((long)array[0] << 32 | (long)array[1] & 4294967295L, (long)array[2] << 32 | (long)array[3] & 4294967295L);
    }

    public static int[] uuidToIntArray(UUID uuid) {
        long l = uuid.getMostSignificantBits();
        long m = uuid.getLeastSignificantBits();
        return leastMostToIntArray(l, m);
    }

    private static int[] leastMostToIntArray(long uuidMost, long uuidLeast) {
        return new int[]{(int)(uuidMost >> 32), (int)uuidMost, (int)(uuidLeast >> 32), (int)uuidLeast};
    }

    public static byte[] uuidToByteArray(UUID uuid) {
        byte[] bs = new byte[16];
        ByteBuffer.wrap(bs).order(ByteOrder.BIG_ENDIAN).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return bs;
    }

    public static UUID readUUID(Dynamic<?> dynamic) {
        int[] is = dynamic.asIntStream().toArray();
        if (is.length != 4) {
            throw new IllegalArgumentException("Could not read UUID. Expected int-array of length 4, got " + is.length + ".");
        } else {
            return uuidFromIntArray(is);
        }
    }

    public static UUID createOfflinePlayerUUID(String nickname) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));
    }

    public static GameProfile createOfflineProfile(String nickname) {
        UUID uUID = createOfflinePlayerUUID(nickname);
        return new GameProfile(uUID, nickname);
    }
}
