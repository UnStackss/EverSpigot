package io.papermc.paper.world;

import com.destroystokyo.paper.ClientOption;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Difficulty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TranslationKeyTest {

    @Test
    public void testChatVisibilityKeys() {
        for (ClientOption.ChatVisibility chatVisibility : ClientOption.ChatVisibility.values()) {
            if (chatVisibility == ClientOption.ChatVisibility.UNKNOWN) continue;
            Assertions.assertEquals(ChatVisiblity.valueOf(chatVisibility.name()).getKey(), chatVisibility.translationKey(), chatVisibility + "'s translation key doesn't match");
        }
    }
}
