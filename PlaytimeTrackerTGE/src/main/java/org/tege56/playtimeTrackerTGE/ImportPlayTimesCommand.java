package org.tege56.playtimeTrackerTGE;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.tege56.playtimeTrackerTGE.storage.StorageProvider;

public class ImportPlayTimesCommand implements CommandExecutor {
    private final PlaytimeTrackerTGE plugin;
    private final AutoRankManager autoRankManager;
    private final StorageProvider mainStorage;

    public ImportPlayTimesCommand(PlaytimeTrackerTGE plugin, AutoRankManager autoRankManager, StorageProvider mainStorage) {
        this.plugin = plugin;
        this.autoRankManager = autoRankManager;
        this.mainStorage = mainStorage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playtimetracker.import")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            sender.sendMessage("§7Connecting to PlayTimes database...");
            PlayTimesMySQLStorage importStorage = new PlayTimesMySQLStorage(plugin);

            if (!importStorage.connect()) {
                sender.sendMessage("§cConnection failed.");
                return;
            }

            sender.sendMessage("§aStarting import...");
            autoRankManager.importPlayTimesDataWithAfk(importStorage, mainStorage);
            sender.sendMessage("§aImport completed!");
        });

        return true;
    }
}