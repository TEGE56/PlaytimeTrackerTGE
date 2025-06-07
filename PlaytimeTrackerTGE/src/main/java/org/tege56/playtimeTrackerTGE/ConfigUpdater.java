package org.tege56.playtimeTrackerTGE;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ConfigUpdater {

    public static void update(JavaPlugin plugin, String resourcePath, File configFile) {
        try {
            if (!configFile.exists()) {
                plugin.saveResource(resourcePath, false);
                return;
            }

            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(plugin.getResource(resourcePath), StandardCharsets.UTF_8));
            YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);

            boolean changed = false;
            Set<String> keys = defaultConfig.getKeys(true);

            for (String key : keys) {
                if (!userConfig.contains(key)) {
                    userConfig.set(key, defaultConfig.get(key));
                    plugin.getLogger().info("Added missing config key: " + key);
                    changed = true;
                }
            }

            if (changed) {
                userConfig.save(configFile);
                plugin.getLogger().info("Config updated (missing keys added).");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("ConfigUpdater failed: " + e.getMessage());
        }
    }
}
