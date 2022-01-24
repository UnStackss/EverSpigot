package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.tree.CommandNode; // CraftBukkit

public class CommandSourceStack implements ExecutionCommandSource<CommandSourceStack>, SharedSuggestionProvider, com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource { // Paper - Brigadier API

    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Component.translatable("permissions.requires.entity"));
    public final CommandSource source;
    private final Vec3 worldPosition;
    private final ServerLevel level;
    private final int permissionLevel;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final boolean silent;
    @Nullable
    private final Entity entity;
    private final CommandResultCallback resultCallback;
    private final EntityAnchorArgument.Anchor anchor;
    private final Vec2 rotation;
    private final CommandSigningContext signingContext;
    private final TaskChainer chatMessageChainer;
    public java.util.Map<Thread, CommandNode> currentCommand = new java.util.concurrent.ConcurrentHashMap<>(); // CraftBukkit // Paper - Thread Safe Vanilla Command permission checking
    public boolean bypassSelectorPermissions = false; // Paper - add bypass for selector permissions

    public CommandSourceStack(CommandSource output, Vec3 pos, Vec2 rot, ServerLevel world, int level, String name, Component displayName, MinecraftServer server, @Nullable Entity entity) {
        this(output, pos, rot, world, level, name, displayName, server, entity, false, CommandResultCallback.EMPTY, EntityAnchorArgument.Anchor.FEET, CommandSigningContext.ANONYMOUS, TaskChainer.immediate(server));
    }

    protected CommandSourceStack(CommandSource output, Vec3 pos, Vec2 rot, ServerLevel world, int level, String name, Component displayName, MinecraftServer server, @Nullable Entity entity, boolean silent, CommandResultCallback resultStorer, EntityAnchorArgument.Anchor entityAnchor, CommandSigningContext signedArguments, TaskChainer messageChainTaskQueue) {
        this.source = output;
        this.worldPosition = pos;
        this.level = world;
        this.silent = silent;
        this.entity = entity;
        this.permissionLevel = level;
        this.textName = name;
        this.displayName = displayName;
        this.server = server;
        this.resultCallback = resultStorer;
        this.anchor = entityAnchor;
        this.rotation = rot;
        this.signingContext = signedArguments;
        this.chatMessageChainer = messageChainTaskQueue;
    }

    public CommandSourceStack withSource(CommandSource output) {
        return this.source == output ? this : new CommandSourceStack(output, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withEntity(Entity entity) {
        return this.entity == entity ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, entity.getName().getString(), entity.getDisplayName(), this.server, entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withPosition(Vec3 position) {
        return this.worldPosition.equals(position) ? this : new CommandSourceStack(this.source, position, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withRotation(Vec2 rotation) {
        return this.rotation.equals(rotation) ? this : new CommandSourceStack(this.source, this.worldPosition, rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    @Override
    public CommandSourceStack withCallback(CommandResultCallback returnValueConsumer) {
        return Objects.equals(this.resultCallback, returnValueConsumer) ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, returnValueConsumer, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withCallback(CommandResultCallback returnValueConsumer, BinaryOperator<CommandResultCallback> merger) {
        CommandResultCallback commandresultcallback1 = (CommandResultCallback) merger.apply(this.resultCallback, returnValueConsumer);

        return this.withCallback(commandresultcallback1);
    }

    public CommandSourceStack withSuppressedOutput() {
        return !this.silent && !this.source.alwaysAccepts() ? new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, true, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer) : this;
    }

    public CommandSourceStack withPermission(int level) {
        return level == this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, level, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withMaximumPermission(int level) {
        return level <= this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, level, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withAnchor(EntityAnchorArgument.Anchor anchor) {
        return anchor == this.anchor ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withLevel(ServerLevel world) {
        if (world == this.level) {
            return this;
        } else {
            double d0 = DimensionType.getTeleportationScale(this.level.dimensionType(), world.dimensionType());
            Vec3 vec3d = new Vec3(this.worldPosition.x * d0, this.worldPosition.y, this.worldPosition.z * d0);

            return new CommandSourceStack(this.source, vec3d, this.rotation, world, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
        }
    }

    public CommandSourceStack facing(Entity entity, EntityAnchorArgument.Anchor anchor) {
        return this.facing(anchor.apply(entity));
    }

    public CommandSourceStack facing(Vec3 position) {
        Vec3 vec3d1 = this.anchor.apply(this);
        double d0 = position.x - vec3d1.x;
        double d1 = position.y - vec3d1.y;
        double d2 = position.z - vec3d1.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float f = Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 57.2957763671875D)));
        float f1 = Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F);

        return this.withRotation(new Vec2(f, f1));
    }

    public CommandSourceStack withSigningContext(CommandSigningContext signedArguments, TaskChainer messageChainTaskQueue) {
        return signedArguments == this.signingContext && messageChainTaskQueue == this.chatMessageChainer ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, signedArguments, messageChainTaskQueue);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTextName() {
        return this.textName;
    }

    // Paper start - Brigadier API
    @Override
    public org.bukkit.entity.Entity getBukkitEntity() {
        return getEntity() != null ? getEntity().getBukkitEntity() : null;
    }

    @Override
    public org.bukkit.World getBukkitWorld() {
        return getLevel() != null ? getLevel().getWorld() : null;
    }

    @Override
    public org.bukkit.Location getBukkitLocation() {
        Vec3 pos = getPosition();
        org.bukkit.World world = getBukkitWorld();
        Vec2 rot = getRotation();
        return world != null && pos != null ? new org.bukkit.Location(world, pos.x, pos.y, pos.z, rot != null ? rot.y : 0, rot != null ? rot.x : 0) : null;
    }
    // Paper end - Brigadier API

    @Override
    public boolean hasPermission(int level) {
        // CraftBukkit start
        // Paper start - Thread Safe Vanilla Command permission checking
        CommandNode currentCommand = this.currentCommand.get(Thread.currentThread());
        if (currentCommand != null) {
            return this.hasPermission(level, org.bukkit.craftbukkit.command.VanillaCommandWrapper.getPermission(currentCommand));
            // Paper end - Thread Safe Vanilla Command permission checking
        }
        // CraftBukkit end

        return this.permissionLevel >= level;
    }

    // Paper start - Fix permission levels for command blocks
    private boolean forceRespectPermissionLevel() {
        return this.source == CommandSource.NULL || (this.source instanceof final net.minecraft.world.level.BaseCommandBlock commandBlock && commandBlock.getLevel().paperConfig().commandBlocks.forceFollowPermLevel);
    }
    // Paper end - Fix permission levels for command blocks

    // CraftBukkit start
    public boolean hasPermission(int i, String bukkitPermission) {
        // Paper start - Fix permission levels for command blocks
        final java.util.function.BooleanSupplier hasBukkitPerm = () -> this.source == CommandSource.NULL /*treat NULL as having all bukkit perms*/ || this.getBukkitSender().hasPermission(bukkitPermission); // lazily check bukkit perms to the benefit of custom permission setups
        // if the server is null, we must check the vanilla perm level system
        // if ignoreVanillaPermissions is true, we can skip vanilla perms and just run the bukkit perm check
        //noinspection ConstantValue
        if (this.getServer() == null || !this.getServer().server.ignoreVanillaPermissions) { // server & level are null for command function loading
            final boolean hasPermLevel = this.permissionLevel >= i;
            if (this.forceRespectPermissionLevel()) { // NULL CommandSource and command blocks (if setting is enabled) should always pass the vanilla perm check
                return hasPermLevel && hasBukkitPerm.getAsBoolean();
            } else { // otherwise check vanilla perm first then bukkit perm, matching upstream behavior
                return hasPermLevel || hasBukkitPerm.getAsBoolean();
            }
        }
        return hasBukkitPerm.getAsBoolean();
        // Paper end - Fix permission levels for command blocks
    }
    // CraftBukkit end

    public Vec3 getPosition() {
        return this.worldPosition;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (this.entity == null) {
            throw CommandSourceStack.ERROR_NOT_ENTITY.create();
        } else {
            return this.entity;
        }
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        Entity entity = this.entity;

        if (entity instanceof ServerPlayer entityplayer) {
            return entityplayer;
        } else {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }
    }

    @Nullable
    public ServerPlayer getPlayer() {
        Entity entity = this.entity;
        ServerPlayer entityplayer;

        if (entity instanceof ServerPlayer entityplayer1) {
            entityplayer = entityplayer1;
        } else {
            entityplayer = null;
        }

        return entityplayer;
    }

    public boolean isPlayer() {
        return this.entity instanceof ServerPlayer;
    }

    public Vec2 getRotation() {
        return this.rotation;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public EntityAnchorArgument.Anchor getAnchor() {
        return this.anchor;
    }

    public CommandSigningContext getSigningContext() {
        return this.signingContext;
    }

    public TaskChainer getChatMessageChainer() {
        return this.chatMessageChainer;
    }

    public boolean shouldFilterMessageTo(ServerPlayer recipient) {
        ServerPlayer entityplayer1 = this.getPlayer();

        return recipient == entityplayer1 ? false : entityplayer1 != null && entityplayer1.isTextFilteringEnabled() || recipient.isTextFilteringEnabled();
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filterMaskEnabled, ChatType.Bound params) {
        if (!this.silent) {
            ServerPlayer entityplayer = this.getPlayer();

            if (entityplayer != null) {
                entityplayer.sendChatMessage(message, filterMaskEnabled, params);
            } else {
                this.source.sendSystemMessage(params.decorate(message.content()));
            }

        }
    }

    public void sendSystemMessage(Component message) {
        if (!this.silent) {
            ServerPlayer entityplayer = this.getPlayer();

            if (entityplayer != null) {
                entityplayer.sendSystemMessage(message);
            } else {
                this.source.sendSystemMessage(message);
            }

        }
    }

    public void sendSuccess(Supplier<Component> feedbackSupplier, boolean broadcastToOps) {
        boolean flag1 = this.source.acceptsSuccess() && !this.silent;
        boolean flag2 = broadcastToOps && this.source.shouldInformAdmins() && !this.silent;

        if (flag1 || flag2) {
            Component ichatbasecomponent = (Component) feedbackSupplier.get();

            if (flag1) {
                this.source.sendSystemMessage(ichatbasecomponent);
            }

            if (flag2) {
                this.broadcastToAdmins(ichatbasecomponent);
            }

        }
    }

    private void broadcastToAdmins(Component message) {
        MutableComponent ichatmutablecomponent = Component.translatable("chat.type.admin", this.getDisplayName(), message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        if (this.server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
            Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (entityplayer != this.source && entityplayer.getBukkitEntity().hasPermission("minecraft.admin.command_feedback")) { // CraftBukkit
                    entityplayer.sendSystemMessage(ichatmutablecomponent);
                }
            }
        }

        if (this.source != this.server && this.server.getGameRules().getBoolean(GameRules.RULE_LOGADMINCOMMANDS) && !org.spigotmc.SpigotConfig.silentCommandBlocks) { // Spigot
            this.server.sendSystemMessage(ichatmutablecomponent);
        }

    }

    public void sendFailure(Component message) {
        // Paper start - Add UnknownCommandEvent
        this.sendFailure(message, true);
    }
    public void sendFailure(Component message, boolean withStyle) {
        // Paper end - Add UnknownCommandEvent
        if (this.source.acceptsFailure() && !this.silent) {
            this.source.sendSystemMessage(withStyle ? Component.empty().append(message).withStyle(ChatFormatting.RED) : message); // Paper - Add UnknownCommandEvent
        }

    }

    @Override
    public CommandResultCallback callback() {
        return this.resultCallback;
    }

    @Override
    public Collection<String> getOnlinePlayerNames() {
        return Lists.newArrayList(this.server.getPlayerNames());
    }

    @Override
    public Collection<String> getAllTeams() {
        return this.server.getScoreboard().getTeamNames();
    }

    @Override
    public Stream<ResourceLocation> getAvailableSounds() {
        return BuiltInRegistries.SOUND_EVENT.stream().map(SoundEvent::getLocation);
    }

    @Override
    public Stream<ResourceLocation> getRecipeNames() {
        return this.server.getRecipeManager().getRecipeIds();
    }

    @Override
    public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context) {
        return Suggestions.empty();
    }

    @Override
    public CompletableFuture<Suggestions> suggestRegistryElements(ResourceKey<? extends Registry<?>> registryRef, SharedSuggestionProvider.ElementSuggestionType suggestedIdType, SuggestionsBuilder builder, CommandContext<?> context) {
        return (CompletableFuture) this.registryAccess().registry(registryRef).map((iregistry) -> {
            this.suggestRegistryElements(iregistry, suggestedIdType, builder);
            return builder.buildFuture();
        }).orElseGet(Suggestions::empty);
    }

    @Override
    public Set<ResourceKey<Level>> levels() {
        return this.server.levelKeys();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.server.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher() {
        return this.getServer().getFunctions().getDispatcher();
    }

    @Override
    public void handleError(CommandExceptionType type, Message message, boolean silent, @Nullable TraceCallbacks tracer) {
        if (tracer != null) {
            tracer.onError(message.getString());
        }

        if (!silent) {
            this.sendFailure(ComponentUtils.fromMessage(message));
        }

    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }

    // CraftBukkit start
    public org.bukkit.command.CommandSender getBukkitSender() {
        return this.source.getBukkitSender(this);
    }
    // CraftBukkit end
}
