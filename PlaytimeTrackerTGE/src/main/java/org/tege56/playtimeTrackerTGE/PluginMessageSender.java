package org.tege56.playtimeTrackerTGE;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginMessageSender {

    private final JavaPlugin plugin;

    public PluginMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendPluginMessageToBungee(
            Player player,
            String subChannel,
            String message
    ) {
        if (player == null || !player.isOnline()) return;

        if (!plugin.getServer().getMessenger()
                .isOutgoingChannelRegistered(plugin, "tege56:playtimetrackertgebungee")) {
            plugin.getLogger().warning("❌ Outgoing channel not registered.");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        out.writeUTF(message);

        player.sendPluginMessage(
                plugin,
                "tege56:playtimetrackertgebungee",
                out.toByteArray()
        );

        plugin.getLogger().info("✅ Sent plugin message [" + subChannel + "] via " + player.getName());
    }

    public void sendPluginMessageToBungee(String subChannel, String message) {
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        sendPluginMessageToBungee(player, subChannel, message);
    }

    public void sendLocalBroadcast(String message) {
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(colored);
        }
    }
}
