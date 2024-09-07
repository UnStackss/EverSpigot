package net.minecraft.commands.synchronization;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

public class ArgumentUtils {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte NUMBER_FLAG_MIN = 1;
    private static final byte NUMBER_FLAG_MAX = 2;

    public static int createNumberFlags(boolean hasMin, boolean hasMax) {
        int i = 0;
        if (hasMin) {
            i |= 1;
        }

        if (hasMax) {
            i |= 2;
        }

        return i;
    }

    public static boolean numberHasMin(byte flags) {
        return (flags & 1) != 0;
    }

    public static boolean numberHasMax(byte flags) {
        return (flags & 2) != 0;
    }

    private static <A extends ArgumentType<?>> void serializeCap(JsonObject json, ArgumentTypeInfo.Template<A> properties) {
        serializeCap(json, properties.type(), properties);
    }

    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeCap(
        JsonObject json, ArgumentTypeInfo<A, T> serializer, ArgumentTypeInfo.Template<A> properties
    ) {
        serializer.serializeToJson((T)properties, json);
    }

    private static <T extends ArgumentType<?>> void serializeArgumentToJson(JsonObject json, T argumentType) {
        ArgumentTypeInfo.Template<T> template = ArgumentTypeInfos.unpack(argumentType);
        json.addProperty("type", "argument");
        json.addProperty("parser", BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(template.type()).toString());
        JsonObject jsonObject = new JsonObject();
        serializeCap(jsonObject, template);
        if (jsonObject.size() > 0) {
            json.add("properties", jsonObject);
        }
    }

    public static <S> JsonObject serializeNodeToJson(CommandDispatcher<S> dispatcher, CommandNode<S> rootNode) {
        JsonObject jsonObject = new JsonObject();
        if (rootNode instanceof RootCommandNode) {
            jsonObject.addProperty("type", "root");
        } else if (rootNode instanceof LiteralCommandNode) {
            jsonObject.addProperty("type", "literal");
        } else if (rootNode instanceof ArgumentCommandNode<?, ?> argumentCommandNode) {
            serializeArgumentToJson(jsonObject, argumentCommandNode.getType());
        } else {
            LOGGER.error("Could not serialize node {} ({})!", rootNode, rootNode.getClass());
            jsonObject.addProperty("type", "unknown");
        }

        JsonObject jsonObject2 = new JsonObject();

        for (CommandNode<S> commandNode : rootNode.getChildren()) {
            jsonObject2.add(commandNode.getName(), serializeNodeToJson(dispatcher, commandNode));
        }

        if (jsonObject2.size() > 0) {
            jsonObject.add("children", jsonObject2);
        }

        if (rootNode.getCommand() != null) {
            jsonObject.addProperty("executable", true);
        }

        if (rootNode.getRedirect() != null) {
            Collection<String> collection = dispatcher.getPath(rootNode.getRedirect());
            if (!collection.isEmpty()) {
                JsonArray jsonArray = new JsonArray();

                for (String string : collection) {
                    jsonArray.add(string);
                }

                jsonObject.add("redirect", jsonArray);
            }
        }

        return jsonObject;
    }

    public static <T> Set<ArgumentType<?>> findUsedArgumentTypes(CommandNode<T> rootNode) {
        Set<CommandNode<T>> set = Sets.newIdentityHashSet();
        Set<ArgumentType<?>> set2 = Sets.newHashSet();
        findUsedArgumentTypes(rootNode, set2, set);
        return set2;
    }

    private static <T> void findUsedArgumentTypes(CommandNode<T> node, Set<ArgumentType<?>> usedArgumentTypes, Set<CommandNode<T>> visitedNodes) {
        if (visitedNodes.add(node)) {
            if (node instanceof ArgumentCommandNode<?, ?> argumentCommandNode) {
                usedArgumentTypes.add(argumentCommandNode.getType());
            }

            node.getChildren().forEach(child -> findUsedArgumentTypes((CommandNode<T>)child, usedArgumentTypes, visitedNodes));
            CommandNode<T> commandNode = node.getRedirect();
            if (commandNode != null) {
                findUsedArgumentTypes(commandNode, usedArgumentTypes, visitedNodes);
            }
        }
    }
}
