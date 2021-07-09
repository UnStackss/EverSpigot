package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class DisplayInfo {
    public static final Codec<DisplayInfo> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ItemStack.STRICT_CODEC.fieldOf("icon").forGetter(DisplayInfo::getIcon),
                    ComponentSerialization.CODEC.fieldOf("title").forGetter(DisplayInfo::getTitle),
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(DisplayInfo::getDescription),
                    ResourceLocation.CODEC.optionalFieldOf("background").forGetter(DisplayInfo::getBackground),
                    AdvancementType.CODEC.optionalFieldOf("frame", AdvancementType.TASK).forGetter(DisplayInfo::getType),
                    Codec.BOOL.optionalFieldOf("show_toast", Boolean.valueOf(true)).forGetter(DisplayInfo::shouldShowToast),
                    Codec.BOOL.optionalFieldOf("announce_to_chat", Boolean.valueOf(true)).forGetter(DisplayInfo::shouldAnnounceChat),
                    Codec.BOOL.optionalFieldOf("hidden", Boolean.valueOf(false)).forGetter(DisplayInfo::isHidden)
                )
                .apply(instance, DisplayInfo::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayInfo> STREAM_CODEC = StreamCodec.ofMember(
        DisplayInfo::serializeToNetwork, DisplayInfo::fromNetwork
    );
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    private final Optional<ResourceLocation> background;
    private final AdvancementType type;
    private final boolean showToast;
    private final boolean announceChat;
    private final boolean hidden;
    private float x;
    private float y;
    public final io.papermc.paper.advancement.AdvancementDisplay paper = new io.papermc.paper.advancement.PaperAdvancementDisplay(this); // Paper - Add more advancement API

    public DisplayInfo(
        ItemStack icon,
        Component title,
        Component description,
        Optional<ResourceLocation> background,
        AdvancementType frame,
        boolean showToast,
        boolean announceToChat,
        boolean hidden
    ) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.background = background;
        this.type = frame;
        this.showToast = showToast;
        this.announceChat = announceToChat;
        this.hidden = hidden;
    }

    public void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getDescription() {
        return this.description;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    public Optional<ResourceLocation> getBackground() {
        return this.background;
    }

    public AdvancementType getType() {
        return this.type;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public boolean shouldShowToast() {
        return this.showToast;
    }

    public boolean shouldAnnounceChat() {
        return this.announceChat;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    private void serializeToNetwork(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, this.title);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, this.description);
        ItemStack.STREAM_CODEC.encode(buf, this.icon);
        buf.writeEnum(this.type);
        int i = 0;
        if (this.background.isPresent()) {
            i |= 1;
        }

        if (this.showToast) {
            i |= 2;
        }

        if (this.hidden) {
            i |= 4;
        }

        buf.writeInt(i);
        this.background.ifPresent(buf::writeResourceLocation);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
    }

    private static DisplayInfo fromNetwork(RegistryFriendlyByteBuf buf) {
        Component component = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        Component component2 = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        ItemStack itemStack = ItemStack.STREAM_CODEC.decode(buf);
        AdvancementType advancementType = buf.readEnum(AdvancementType.class);
        int i = buf.readInt();
        Optional<ResourceLocation> optional = (i & 1) != 0 ? Optional.of(buf.readResourceLocation()) : Optional.empty();
        boolean bl = (i & 2) != 0;
        boolean bl2 = (i & 4) != 0;
        DisplayInfo displayInfo = new DisplayInfo(itemStack, component, component2, optional, advancementType, bl, false, bl2);
        displayInfo.setLocation(buf.readFloat(), buf.readFloat());
        return displayInfo;
    }
}
