package net.minecraft.network.protocol.game;

import com.google.common.base.MoreObjects;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class ClientboundPlayerInfoUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerInfoUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerInfoUpdatePacket::write, ClientboundPlayerInfoUpdatePacket::new
    );
    private final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions;
    private final List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    public ClientboundPlayerInfoUpdatePacket(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions, Collection<ServerPlayer> players) {
        this.actions = actions;
        this.entries = players.stream().map(ClientboundPlayerInfoUpdatePacket.Entry::new).toList();
    }

    public ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action action, ServerPlayer player) {
        this.actions = EnumSet.of(action);
        this.entries = List.of(new ClientboundPlayerInfoUpdatePacket.Entry(player));
    }

    public static ClientboundPlayerInfoUpdatePacket createPlayerInitializing(Collection<ServerPlayer> players) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> enumSet = EnumSet.of(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
        );
        return new ClientboundPlayerInfoUpdatePacket(enumSet, players);
    }

    private ClientboundPlayerInfoUpdatePacket(RegistryFriendlyByteBuf buf) {
        this.actions = buf.readEnumSet(ClientboundPlayerInfoUpdatePacket.Action.class);
        this.entries = buf.readList(buf2 -> {
            ClientboundPlayerInfoUpdatePacket.EntryBuilder entryBuilder = new ClientboundPlayerInfoUpdatePacket.EntryBuilder(buf2.readUUID());

            for (ClientboundPlayerInfoUpdatePacket.Action action : this.actions) {
                action.reader.read(entryBuilder, (RegistryFriendlyByteBuf)buf2);
            }

            return entryBuilder.build();
        });
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnumSet(this.actions, ClientboundPlayerInfoUpdatePacket.Action.class);
        buf.writeCollection(this.entries, (buf2, entry) -> {
            buf2.writeUUID(entry.profileId());

            for (ClientboundPlayerInfoUpdatePacket.Action action : this.actions) {
                action.writer.write((RegistryFriendlyByteBuf)buf2, entry);
            }
        });
    }

    @Override
    public PacketType<ClientboundPlayerInfoUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handlePlayerInfoUpdate(this);
    }

    public EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions() {
        return this.actions;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> entries() {
        return this.entries;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries() {
        return this.actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) ? this.entries : List.of();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("actions", this.actions).add("entries", this.entries).toString();
    }

    public static enum Action {
        ADD_PLAYER((serialized, buf) -> {
            GameProfile gameProfile = new GameProfile(serialized.profileId, buf.readUtf(16));
            gameProfile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(buf));
            serialized.profile = gameProfile;
        }, (buf, entry) -> {
            GameProfile gameProfile = Objects.requireNonNull(entry.profile());
            buf.writeUtf(gameProfile.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buf, gameProfile.getProperties());
        }),
        INITIALIZE_CHAT(
            (serialized, buf) -> serialized.chatSession = buf.readNullable(RemoteChatSession.Data::read),
            (buf, entry) -> buf.writeNullable(entry.chatSession, RemoteChatSession.Data::write)
        ),
        UPDATE_GAME_MODE((serialized, buf) -> serialized.gameMode = GameType.byId(buf.readVarInt()), (buf, entry) -> buf.writeVarInt(entry.gameMode().getId())),
        UPDATE_LISTED((serialized, buf) -> serialized.listed = buf.readBoolean(), (buf, entry) -> buf.writeBoolean(entry.listed())),
        UPDATE_LATENCY((serialized, buf) -> serialized.latency = buf.readVarInt(), (buf, entry) -> buf.writeVarInt(entry.latency())),
        UPDATE_DISPLAY_NAME(
            (serialized, buf) -> serialized.displayName = FriendlyByteBuf.readNullable(buf, ComponentSerialization.TRUSTED_STREAM_CODEC),
            (buf, entry) -> FriendlyByteBuf.writeNullable(buf, entry.displayName(), ComponentSerialization.TRUSTED_STREAM_CODEC)
        );

        final ClientboundPlayerInfoUpdatePacket.Action.Reader reader;
        final ClientboundPlayerInfoUpdatePacket.Action.Writer writer;

        private Action(final ClientboundPlayerInfoUpdatePacket.Action.Reader reader, final ClientboundPlayerInfoUpdatePacket.Action.Writer writer) {
            this.reader = reader;
            this.writer = writer;
        }

        public interface Reader {
            void read(ClientboundPlayerInfoUpdatePacket.EntryBuilder serialized, RegistryFriendlyByteBuf buf);
        }

        public interface Writer {
            void write(RegistryFriendlyByteBuf buf, ClientboundPlayerInfoUpdatePacket.Entry entry);
        }
    }

    public static record Entry(
        UUID profileId,
        @Nullable GameProfile profile,
        boolean listed,
        int latency,
        GameType gameMode,
        @Nullable Component displayName,
        @Nullable RemoteChatSession.Data chatSession
    ) {
        Entry(ServerPlayer player) {
            this(
                player.getUUID(),
                player.getGameProfile(),
                true,
                player.connection.latency(),
                player.gameMode.getGameModeForPlayer(),
                player.getTabListDisplayName(),
                Optionull.map(player.getChatSession(), RemoteChatSession::asData)
            );
        }
    }

    static class EntryBuilder {
        final UUID profileId;
        @Nullable
        GameProfile profile;
        boolean listed;
        int latency;
        GameType gameMode = GameType.DEFAULT_MODE;
        @Nullable
        Component displayName;
        @Nullable
        RemoteChatSession.Data chatSession;

        EntryBuilder(UUID profileId) {
            this.profileId = profileId;
        }

        ClientboundPlayerInfoUpdatePacket.Entry build() {
            return new ClientboundPlayerInfoUpdatePacket.Entry(
                this.profileId, this.profile, this.listed, this.latency, this.gameMode, this.displayName, this.chatSession
            );
        }
    }
}