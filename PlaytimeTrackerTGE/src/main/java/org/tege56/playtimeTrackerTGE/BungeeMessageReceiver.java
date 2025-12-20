package org.tege56.playtimeTrackerTGE;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class BungeeMessageReceiver implements PluginMessageListener {

    private final JavaPlugin plugin;

    public BungeeMessageReceiver(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("tege56:playtimetrackertgebungee")) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = in.readUTF();
            String msg = in.readUTF();

            Bukkit.getScheduler().runTask(plugin, () -> {
                String coloredMessage = ChatColor.translateAlternateColorCodes('&', msg);

                switch (subChannel.toLowerCase()) {
                    case "rankup_broadcast":
                    case "message":
                    case "first_join":
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(coloredMessage);
                        }
                        plugin.getLogger().info(
                                "📩 Received proxy message [" + subChannel + "]: " + msg
                        );
                        break;

                    default:
                        plugin.getLogger().warning("⚠️ Unknown subchannel: " + subChannel);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error receiving plugin message from proxy:");
            e.printStackTrace();
        }
    }
}
