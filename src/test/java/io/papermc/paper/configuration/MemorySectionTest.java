package io.papermc.paper.configuration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

public class MemorySectionTest extends ConfigurationSectionTest {
    @Override
    public ConfigurationSection getConfigurationSection() {
        return new MemoryConfiguration().createSection("section");
    }
}
