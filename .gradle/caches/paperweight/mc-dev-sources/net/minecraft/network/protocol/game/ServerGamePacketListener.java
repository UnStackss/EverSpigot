package net.minecraft.network.protocol.game;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.ping.ServerPingPacketListener;

public interface ServerGamePacketListener extends ServerPingPacketListener, ServerCommonPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    void handleAnimate(ServerboundSwingPacket packet);

    void handleChat(ServerboundChatPacket packet);

    void handleChatCommand(ServerboundChatCommandPacket packet);

    void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet);

    void handleChatAck(ServerboundChatAckPacket packet);

    void handleClientCommand(ServerboundClientCommandPacket packet);

    void handleContainerButtonClick(ServerboundContainerButtonClickPacket packet);

    void handleContainerClick(ServerboundContainerClickPacket packet);

    void handlePlaceRecipe(ServerboundPlaceRecipePacket packet);

    void handleContainerClose(ServerboundContainerClosePacket packet);

    void handleInteract(ServerboundInteractPacket packet);

    void handleMovePlayer(ServerboundMovePlayerPacket packet);

    void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet);

    void handlePlayerAction(ServerboundPlayerActionPacket packet);

    void handlePlayerCommand(ServerboundPlayerCommandPacket packet);

    void handlePlayerInput(ServerboundPlayerInputPacket packet);

    void handleSetCarriedItem(ServerboundSetCarriedItemPacket packet);

    void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet);

    void handleSignUpdate(ServerboundSignUpdatePacket packet);

    void handleUseItemOn(ServerboundUseItemOnPacket packet);

    void handleUseItem(ServerboundUseItemPacket packet);

    void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packet);

    void handlePaddleBoat(ServerboundPaddleBoatPacket packet);

    void handleMoveVehicle(ServerboundMoveVehiclePacket packet);

    void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet);

    void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket packet);

    void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket packet);

    void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packet);

    void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packet);

    void handleSetCommandBlock(ServerboundSetCommandBlockPacket packet);

    void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packet);

    void handlePickItem(ServerboundPickItemPacket packet);

    void handleRenameItem(ServerboundRenameItemPacket packet);

    void handleSetBeaconPacket(ServerboundSetBeaconPacket packet);

    void handleSetStructureBlock(ServerboundSetStructureBlockPacket packet);

    void handleSelectTrade(ServerboundSelectTradePacket packet);

    void handleEditBook(ServerboundEditBookPacket packet);

    void handleEntityTagQuery(ServerboundEntityTagQueryPacket packet);

    void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket packet);

    void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket packet);

    void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packet);

    void handleJigsawGenerate(ServerboundJigsawGeneratePacket packet);

    void handleChangeDifficulty(ServerboundChangeDifficultyPacket packet);

    void handleLockDifficulty(ServerboundLockDifficultyPacket packet);

    void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet);

    void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket packet);

    void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet);

    void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket packet);
}
