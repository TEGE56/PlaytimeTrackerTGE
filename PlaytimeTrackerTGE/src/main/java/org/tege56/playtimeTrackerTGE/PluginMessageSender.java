package org.tege56.playtimeTrackerTGE;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PluginMessageSender {

    private final JavaPlugin plugin;
    private final Set<String> recentlySentPluginMessages = ConcurrentHashMap.newKeySet();

    public PluginMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendPluginMessageToBungee(String subChannel, String message) {
        String key = subChannel + "::" + message;
        if (recentlySentPluginMessages.contains(key)) {
            return;
        }

        Player target = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (target == null) {
            plugin.getLogger().warning("❌ Could not send message – no players online.");
            return;
        }

        boolean useBungee = plugin.getConfig().getBoolean("use_bungee", true);

        if (useBungee) {
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "tege56:playtimetrackertgebungee")) {
                plugin.getLogger().warning("❌ Outgoing channel 'tege56:playtimetrackertgebungee' is not registered.");
                return;
            }

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(subChannel);
            out.writeUTF(message);

            target.sendPluginMessage(plugin, "tege56:playtimetrackertgebungee", out.toByteArray());
            plugin.getLogger().info("✅ Sent plugin message [" + subChannel + "]: " + message);
        } else {
            String colored = ChatColor.translateAlternateColorCodes('&', message);
            Bukkit.broadcastMessage(colored);
            plugin.getLogger().info("ℹ️ Bungee is disabled – message shown on the server: " + colored);
        }

        recentlySentPluginMessages.add(key);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentlySentPluginMessages.remove(key), 20L); // 1 second spam prevention
    }

    public void sendMessageToServers(String message) {
        sendPluginMessageToBungee("Message", message);
    }
}
