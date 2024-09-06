package net.minecraft.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.util.CraftChatMessage;
// CraftBukkit end

public record LockCode(String key) {

    public static final LockCode NO_LOCK = new LockCode("");
    public static final Codec<LockCode> CODEC = Codec.STRING.xmap(LockCode::new, LockCode::key);
    public static final String TAG_LOCK = "Lock";

    public boolean unlocksWith(ItemStack stack) {
        if (this.key.isEmpty()) {
            return true;
        } else {
            Component ichatbasecomponent = (Component) stack.get(DataComponents.CUSTOM_NAME);

            // CraftBukkit start - SPIGOT-6307: Check for color codes if the lock contains color codes
            if (this.key.isEmpty()) return true;
            if (ichatbasecomponent != null) {
                if (this.key.indexOf(ChatColor.COLOR_CHAR) == -1) {
                    // The lock key contains no color codes, so let's ignore colors in the item display name (vanilla Minecraft behavior):
                    return this.key.equals(ichatbasecomponent.getString());
                } else {
                    // The lock key contains color codes, so let's take them into account:
                    return this.key.equals(CraftChatMessage.fromComponent(ichatbasecomponent));
                }
            }
            return false;
            // CraftBukkit end
        }
    }

    public void addToTag(CompoundTag nbt) {
        if (!this.key.isEmpty()) {
            nbt.putString("Lock", this.key);
        }

    }

    public static LockCode fromTag(CompoundTag nbt) {
        return nbt.contains("Lock", 8) ? new LockCode(nbt.getString("Lock")) : LockCode.NO_LOCK;
    }
}
