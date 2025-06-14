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

        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.isOnline() && p.isValid())
                .findFirst().orElse(null);
        if (target == null) {
            plugin.getLogger().warning("❌ Could not send message – no players online.");
            return;
        }

        boolean useBungee = plugin.getConfig().getBoolean("use_bungee", true);
        if (!useBungee) {
            String colored = ChatColor.translateAlternateColorCodes('&', message);
            Bukkit.broadcastMessage(colored);
            plugin.getLogger().info("ℹ️ Bungee is disabled – message shown on the server: " + colored);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "tege56:playtimetrackertgebungee")) {
                plugin.getLogger().warning("❌ Outgoing channel 'tege56:playtimetrackertgebungee' is not registered.");
                return;
            }

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(subChannel);
            out.writeUTF(message);

            target.sendPluginMessage(plugin, "tege56:playtimetrackertgebungee", out.toByteArray());
            plugin.getLogger().info("✅ Sent plugin message [" + subChannel + "]: " + message);
        }, 2L); // 2 tick delay = ~0.1 sec

        recentlySentPluginMessages.add(key);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentlySentPluginMessages.remove(key), 20L); // 1s duplikaattisuoja
    }

    public void sendMessageToServers(String message) {
        sendPluginMessageToBungee("Message", message);
    }
}
