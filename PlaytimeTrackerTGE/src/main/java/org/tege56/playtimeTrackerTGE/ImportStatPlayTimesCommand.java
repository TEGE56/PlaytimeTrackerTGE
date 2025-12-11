package org.tege56.playtimeTrackerTGE;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.tege56.playtimeTrackerTGE.storage.StorageProvider;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImportStatPlayTimesCommand implements CommandExecutor {
    private final PlaytimeTrackerTGE plugin;
    private final StorageProvider storage;

    public ImportStatPlayTimesCommand(PlaytimeTrackerTGE plugin, StorageProvider storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playtimetracker.import")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        sender.sendMessage("§7Starting stats-playtime import...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File folder1 = new File(plugin.getDataFolder().getParentFile(), "statimport/statsimport");

            Map<UUID, Long> playtimes = new HashMap<>();

            importPlaytimesFromStatsFolder(folder1, playtimes);

            int imported = 0;
            for (Map.Entry<UUID, Long> entry : playtimes.entrySet()) {
                UUID uuid = entry.getKey();
                long ticks = entry.getValue();
                long minutes = ticks / 1200;

                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                String username = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
                long firstJoin = offlinePlayer.getFirstPlayed() > 0 ? offlinePlayer.getFirstPlayed() / 1000L : System.currentTimeMillis() / 1000L;

                storage.saveImportedPlaytime(uuid, minutes, username, firstJoin);
                imported++;
            }

            sender.sendMessage("§aImported " + imported + " player record(s) from stats folders.");
            plugin.getLogger().info("Imported " + imported + " player record(s) from stats folders.");
        });

        return true;
    }

    private void importPlaytimesFromStatsFolder(File folder, Map<UUID, Long> playtimes) {
        if (!folder.exists() || !folder.isDirectory()) {
            plugin.getLogger().warning("Stats-folder doesen't exist: " + folder.getAbsolutePath());
            return;
        }

        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".json")) continue;

            try (FileReader reader = new FileReader(file)) {
                UUID uuid = UUID.fromString(file.getName().replace(".json", ""));
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                JsonObject stats = json.getAsJsonObject("stats");
                if (stats == null) continue;

                JsonObject minecraftStats = stats.getAsJsonObject("minecraft:custom");
                if (minecraftStats == null) continue;

                long playTimeTicks = minecraftStats.has("minecraft:play_time")
                        ? minecraftStats.get("minecraft:play_time").getAsLong()
                        : 0;

                playtimes.merge(uuid, playTimeTicks, Long::sum);

            } catch (Exception e) {
                plugin.getLogger().warning("Error in stats-data: " + file.getName() + " - " + e.getMessage());
            }
        }
    }
}
