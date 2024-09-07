package net.minecraft.network.protocol.game;

import com.google.common.collect.Queues;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public class ClientboundCommandsPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCommandsPacket> STREAM_CODEC = Packet.codec(
        ClientboundCommandsPacket::write, ClientboundCommandsPacket::new
    );
    private static final byte MASK_TYPE = 3;
    private static final byte FLAG_EXECUTABLE = 4;
    private static final byte FLAG_REDIRECT = 8;
    private static final byte FLAG_CUSTOM_SUGGESTIONS = 16;
    private static final byte TYPE_ROOT = 0;
    private static final byte TYPE_LITERAL = 1;
    private static final byte TYPE_ARGUMENT = 2;
    private final int rootIndex;
    private final List<ClientboundCommandsPacket.Entry> entries;

    public ClientboundCommandsPacket(RootCommandNode<SharedSuggestionProvider> rootNode) {
        Object2IntMap<CommandNode<SharedSuggestionProvider>> object2IntMap = enumerateNodes(rootNode);
        this.entries = createEntries(object2IntMap);
        this.rootIndex = object2IntMap.getInt(rootNode);
    }

    private ClientboundCommandsPacket(FriendlyByteBuf buf) {
        this.entries = buf.readList(ClientboundCommandsPacket::readNode);
        this.rootIndex = buf.readVarInt();
        validateEntries(this.entries);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeCollection(this.entries, (buf2, node) -> node.write(buf2));
        buf.writeVarInt(this.rootIndex);
    }

    private static void validateEntries(List<ClientboundCommandsPacket.Entry> nodeDatas, BiPredicate<ClientboundCommandsPacket.Entry, IntSet> validator) {
        IntSet intSet = new IntOpenHashSet(IntSets.fromTo(0, nodeDatas.size()));

        while (!intSet.isEmpty()) {
            boolean bl = intSet.removeIf(i -> validator.test(nodeDatas.get(i), intSet));
            if (!bl) {
                throw new IllegalStateException("Server sent an impossible command tree");
            }
        }
    }

    private static void validateEntries(List<ClientboundCommandsPacket.Entry> nodeDatas) {
        validateEntries(nodeDatas, ClientboundCommandsPacket.Entry::canBuild);
        validateEntries(nodeDatas, ClientboundCommandsPacket.Entry::canResolve);
    }

    private static Object2IntMap<CommandNode<SharedSuggestionProvider>> enumerateNodes(RootCommandNode<SharedSuggestionProvider> commandTree) {
        Object2IntMap<CommandNode<SharedSuggestionProvider>> object2IntMap = new Object2IntOpenHashMap<>();
        Queue<CommandNode<SharedSuggestionProvider>> queue = Queues.newArrayDeque();
        queue.add(commandTree);

        CommandNode<SharedSuggestionProvider> commandNode;
        while ((commandNode = queue.poll()) != null) {
            if (!object2IntMap.containsKey(commandNode)) {
                int i = object2IntMap.size();
                object2IntMap.put(commandNode, i);
                queue.addAll(commandNode.getChildren());
                if (commandNode.getRedirect() != null) {
                    queue.add(commandNode.getRedirect());
                }
            }
        }

        return object2IntMap;
    }

    private static List<ClientboundCommandsPacket.Entry> createEntries(Object2IntMap<CommandNode<SharedSuggestionProvider>> nodes) {
        ObjectArrayList<ClientboundCommandsPacket.Entry> objectArrayList = new ObjectArrayList<>(nodes.size());
        objectArrayList.size(nodes.size());

        for (Object2IntMap.Entry<CommandNode<SharedSuggestionProvider>> entry : Object2IntMaps.fastIterable(nodes)) {
            objectArrayList.set(entry.getIntValue(), createEntry(entry.getKey(), nodes));
        }

        return objectArrayList;
    }

    private static ClientboundCommandsPacket.Entry readNode(FriendlyByteBuf buf) {
        byte b = buf.readByte();
        int[] is = buf.readVarIntArray();
        int i = (b & 8) != 0 ? buf.readVarInt() : 0;
        ClientboundCommandsPacket.NodeStub nodeStub = read(buf, b);
        return new ClientboundCommandsPacket.Entry(nodeStub, b, i, is);
    }

    @Nullable
    private static ClientboundCommandsPacket.NodeStub read(FriendlyByteBuf buf, byte flags) {
        int i = flags & 3;
        if (i == 2) {
            String string = buf.readUtf();
            int j = buf.readVarInt();
            ArgumentTypeInfo<?, ?> argumentTypeInfo = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.byId(j);
            if (argumentTypeInfo == null) {
                return null;
            } else {
                ArgumentTypeInfo.Template<?> template = argumentTypeInfo.deserializeFromNetwork(buf);
                ResourceLocation resourceLocation = (flags & 16) != 0 ? buf.readResourceLocation() : null;
                return new ClientboundCommandsPacket.ArgumentNodeStub(string, template, resourceLocation);
            }
        } else if (i == 1) {
            String string2 = buf.readUtf();
            return new ClientboundCommandsPacket.LiteralNodeStub(string2);
        } else {
            return null;
        }
    }

    private static ClientboundCommandsPacket.Entry createEntry(
        CommandNode<SharedSuggestionProvider> node, Object2IntMap<CommandNode<SharedSuggestionProvider>> nodes
    ) {
        int i = 0;
        int j;
        if (node.getRedirect() != null) {
            i |= 8;
            j = nodes.getInt(node.getRedirect());
        } else {
            j = 0;
        }

        if (node.getCommand() != null) {
            i |= 4;
        }

        ClientboundCommandsPacket.NodeStub nodeStub;
        if (node instanceof RootCommandNode) {
            i |= 0;
            nodeStub = null;
        } else if (node instanceof ArgumentCommandNode<SharedSuggestionProvider, ?> argumentCommandNode) {
            nodeStub = new ClientboundCommandsPacket.ArgumentNodeStub(argumentCommandNode);
            i |= 2;
            if (argumentCommandNode.getCustomSuggestions() != null) {
                i |= 16;
            }
        } else {
            if (!(node instanceof LiteralCommandNode literalCommandNode)) {
                throw new UnsupportedOperationException("Unknown node type " + node);
            }

            nodeStub = new ClientboundCommandsPacket.LiteralNodeStub(literalCommandNode.getLiteral());
            i |= 1;
        }

        int[] is = node.getChildren().stream().mapToInt(nodes::getInt).toArray();
        return new ClientboundCommandsPacket.Entry(nodeStub, i, j, is);
    }

    @Override
    public PacketType<ClientboundCommandsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_COMMANDS;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleCommands(this);
    }

    public RootCommandNode<SharedSuggestionProvider> getRoot(CommandBuildContext commandRegistryAccess) {
        return (RootCommandNode<SharedSuggestionProvider>)new ClientboundCommandsPacket.NodeResolver(commandRegistryAccess, this.entries)
            .resolve(this.rootIndex);
    }

    static class ArgumentNodeStub implements ClientboundCommandsPacket.NodeStub {
        private final String id;
        private final ArgumentTypeInfo.Template<?> argumentType;
        @Nullable
        private final ResourceLocation suggestionId;

        @Nullable
        private static ResourceLocation getSuggestionId(@Nullable SuggestionProvider<SharedSuggestionProvider> provider) {
            return provider != null ? SuggestionProviders.getName(provider) : null;
        }

        ArgumentNodeStub(String name, ArgumentTypeInfo.Template<?> properties, @Nullable ResourceLocation id) {
            this.id = name;
            this.argumentType = properties;
            this.suggestionId = id;
        }

        public ArgumentNodeStub(ArgumentCommandNode<SharedSuggestionProvider, ?> node) {
            this(node.getName(), ArgumentTypeInfos.unpack(node.getType()), getSuggestionId(node.getCustomSuggestions()));
        }

        @Override
        public ArgumentBuilder<SharedSuggestionProvider, ?> build(CommandBuildContext commandRegistryAccess) {
            ArgumentType<?> argumentType = this.argumentType.instantiate(commandRegistryAccess);
            RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredArgumentBuilder = RequiredArgumentBuilder.argument(this.id, argumentType);
            if (this.suggestionId != null) {
                requiredArgumentBuilder.suggests(SuggestionProviders.getProvider(this.suggestionId));
            }

            return requiredArgumentBuilder;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.id);
            serializeCap(buf, this.argumentType);
            if (this.suggestionId != null) {
                buf.writeResourceLocation(this.suggestionId);
            }
        }

        private static <A extends ArgumentType<?>> void serializeCap(FriendlyByteBuf buf, ArgumentTypeInfo.Template<A> properties) {
            serializeCap(buf, properties.type(), properties);
        }

        private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeCap(
            FriendlyByteBuf buf, ArgumentTypeInfo<A, T> serializer, ArgumentTypeInfo.Template<A> properties
        ) {
            buf.writeVarInt(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getId(serializer));
            serializer.serializeToNetwork((T)properties, buf);
        }
    }

    static class Entry {
        @Nullable
        final ClientboundCommandsPacket.NodeStub stub;
        final int flags;
        final int redirect;
        final int[] children;

        Entry(@Nullable ClientboundCommandsPacket.NodeStub suggestableNode, int flags, int redirectNodeIndex, int[] childNodeIndices) {
            this.stub = suggestableNode;
            this.flags = flags;
            this.redirect = redirectNodeIndex;
            this.children = childNodeIndices;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeByte(this.flags);
            buf.writeVarIntArray(this.children);
            if ((this.flags & 8) != 0) {
                buf.writeVarInt(this.redirect);
            }

            if (this.stub != null) {
                this.stub.write(buf);
            }
        }

        public boolean canBuild(IntSet indices) {
            return (this.flags & 8) == 0 || !indices.contains(this.redirect);
        }

        public boolean canResolve(IntSet indices) {
            for (int i : this.children) {
                if (indices.contains(i)) {
                    return false;
                }
            }

            return true;
        }
    }

    static class LiteralNodeStub implements ClientboundCommandsPacket.NodeStub {
        private final String id;

        LiteralNodeStub(String literal) {
            this.id = literal;
        }

        @Override
        public ArgumentBuilder<SharedSuggestionProvider, ?> build(CommandBuildContext commandRegistryAccess) {
            return LiteralArgumentBuilder.literal(this.id);
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.id);
        }
    }

    static class NodeResolver {
        private final CommandBuildContext context;
        private final List<ClientboundCommandsPacket.Entry> entries;
        private final List<CommandNode<SharedSuggestionProvider>> nodes;

        NodeResolver(CommandBuildContext commandRegistryAccess, List<ClientboundCommandsPacket.Entry> nodeDatas) {
            this.context = commandRegistryAccess;
            this.entries = nodeDatas;
            ObjectArrayList<CommandNode<SharedSuggestionProvider>> objectArrayList = new ObjectArrayList<>();
            objectArrayList.size(nodeDatas.size());
            this.nodes = objectArrayList;
        }

        public CommandNode<SharedSuggestionProvider> resolve(int index) {
            CommandNode<SharedSuggestionProvider> commandNode = this.nodes.get(index);
            if (commandNode != null) {
                return commandNode;
            } else {
                ClientboundCommandsPacket.Entry entry = this.entries.get(index);
                CommandNode<SharedSuggestionProvider> commandNode2;
                if (entry.stub == null) {
                    commandNode2 = new RootCommandNode<>();
                } else {
                    ArgumentBuilder<SharedSuggestionProvider, ?> argumentBuilder = entry.stub.build(this.context);
                    if ((entry.flags & 8) != 0) {
                        argumentBuilder.redirect(this.resolve(entry.redirect));
                    }

                    if ((entry.flags & 4) != 0) {
                        argumentBuilder.executes(context -> 0);
                    }

                    commandNode2 = argumentBuilder.build();
                }

                this.nodes.set(index, commandNode2);

                for (int i : entry.children) {
                    CommandNode<SharedSuggestionProvider> commandNode4 = this.resolve(i);
                    if (!(commandNode4 instanceof RootCommandNode)) {
                        commandNode2.addChild(commandNode4);
                    }
                }

                return commandNode2;
            }
        }
    }

    interface NodeStub {
        ArgumentBuilder<SharedSuggestionProvider, ?> build(CommandBuildContext commandRegistryAccess);

        void write(FriendlyByteBuf buf);
    }
}
