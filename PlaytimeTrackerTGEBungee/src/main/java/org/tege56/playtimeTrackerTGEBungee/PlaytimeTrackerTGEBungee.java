package org.tege56.playtimeTrackerTGEBungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public final class PlaytimeTrackerTGEBungee extends Plugin implements Listener {

    @Override
    public void onEnable() {
        getProxy().registerChannel("tege56:playtimetrackertgebungee");
        getProxy().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel("tege56:playtimetrackertgebungee");
    }

    @EventHandler
    public void onPluginMessageReceived(PluginMessageEvent event) {
        if (!event.getTag().equals("tege56:playtimetrackertgebungee")) return;

        if (event.getSender() instanceof net.md_5.bungee.api.connection.Server) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
                String subChannel = in.readUTF();
                String message = in.readUTF();

                ProxyServer.getInstance().broadcast(ChatColor.translateAlternateColorCodes('&', message));
                getLogger().info("✅ Received and displayed message: " + message);
            } catch (Exception e) {
                getLogger().severe("❌ Error while processing the message:");
                e.printStackTrace();
            }
        }
    }
}
