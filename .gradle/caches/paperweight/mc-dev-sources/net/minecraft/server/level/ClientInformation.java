package net.minecraft.server.level;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;

public record ClientInformation(
    String language,
    int viewDistance,
    ChatVisiblity chatVisibility,
    boolean chatColors,
    int modelCustomisation,
    HumanoidArm mainHand,
    boolean textFilteringEnabled,
    boolean allowsListing
) {
    public static final int MAX_LANGUAGE_LENGTH = 16;

    public ClientInformation(FriendlyByteBuf buf) {
        this(
            buf.readUtf(16),
            buf.readByte(),
            buf.readEnum(ChatVisiblity.class),
            buf.readBoolean(),
            buf.readUnsignedByte(),
            buf.readEnum(HumanoidArm.class),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.language);
        buf.writeByte(this.viewDistance);
        buf.writeEnum(this.chatVisibility);
        buf.writeBoolean(this.chatColors);
        buf.writeByte(this.modelCustomisation);
        buf.writeEnum(this.mainHand);
        buf.writeBoolean(this.textFilteringEnabled);
        buf.writeBoolean(this.allowsListing);
    }

    public static ClientInformation createDefault() {
        return new ClientInformation("en_us", 2, ChatVisiblity.FULL, true, 0, Player.DEFAULT_MAIN_HAND, false, false);
    }
}
