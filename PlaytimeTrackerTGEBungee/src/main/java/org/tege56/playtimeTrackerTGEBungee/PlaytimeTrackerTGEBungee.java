package org.tege56.playtimeTrackerTGEBungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.*;

public final class PlaytimeTrackerTGEBungee extends Plugin implements Listener {

    @Override
    public void onEnable() {
        getProxy().registerChannel("tege56:playtimetrackertgebungee");
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("✅ PlaytimeTrackerTGEBungee enabled and channel registered.");
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel("tege56:playtimetrackertgebungee");
        getLogger().info("⛔ PlaytimeTrackerTGEBungee disabled and channel unregistered.");
    }

    @EventHandler
    public void onPluginMessageReceived(PluginMessageEvent event) {
        if (!event.getTag().equals("tege56:playtimetrackertgebungee")) return;

        if (event.getSender() instanceof net.md_5.bungee.api.connection.Server) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
                String subChannel = in.readUTF();
                String message = in.readUTF();

                byte[] forwardData = createMessage(subChannel, message);

                ProxyServer.getInstance().getServers().values().forEach(server -> {
                    if (!server.getPlayers().isEmpty()) {
                        server.sendData("tege56:playtimetrackertgebungee", forwardData);
                    }
                });

                switch (subChannel.toLowerCase()) {
                    case "first_join":
                        getLogger().info("📣 Broadcasted FIRST JOIN message: " + message);
                        break;
                    case "rankup_broadcast":
                        getLogger().info("📣 Broadcasted RANKUP message: " + message);
                        break;
                    default:
                        getLogger().info("📣 Broadcasted message [" + subChannel + "]: " + message);
                        break;
                }

            } catch (Exception e) {
                getLogger().severe("❌ Error while processing plugin message:");
                e.printStackTrace();
            }
        }
    }

    private byte[] createMessage(String subChannel, String message) {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOut)) {
            out.writeUTF(subChannel);
            out.writeUTF(message);
            return byteOut.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
