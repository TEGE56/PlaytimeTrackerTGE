package org.tege56.playtimeTrackerTGE;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RemoveLastRankCommand implements CommandExecutor {

    private final PlaytimeTrackerTGE plugin;
    private final AutoRankManager autoRankManager;

    public RemoveLastRankCommand(PlaytimeTrackerTGE plugin, AutoRankManager autoRankManager) {
        this.plugin = plugin;
        this.autoRankManager = autoRankManager;
        plugin.getLogger().info("RemoveLastRankCommand created, autoRankManager = " + (autoRankManager != null));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playtimetracker.removelastrank")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /removelastrank <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online.");
            return true;
        }

        autoRankManager.removeLastRank(target);
        return true;
    }
}