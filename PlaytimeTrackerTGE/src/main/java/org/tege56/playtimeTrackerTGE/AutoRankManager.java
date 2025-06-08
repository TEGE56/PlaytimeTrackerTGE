package org.tege56.playtimeTrackerTGE;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.tege56.playtimeTrackerTGE.storage.MySQLStorage;
import org.tege56.playtimeTrackerTGE.storage.StorageProvider;
import net.md_5.bungee.api.ChatColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoRankManager {

    private final PlaytimeTrackerTGE plugin;
    private final List<RankReward> rankRewards = new ArrayList<>();
    private final Map<UUID, Long> lastRankCheck = new HashMap<>();
    private final long rankCheckIntervalMillis;
    private LuckPerms luckPerms;
    private final StorageProvider storage;
    private final Set<String> recentlySentPluginMessages = ConcurrentHashMap.newKeySet();
    private final PluginMessageSender messageSender;

    public AutoRankManager(PlaytimeTrackerTGE plugin, LuckPerms luckPerms, StorageProvider storage, PluginMessageSender messageSender) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.storage = storage;
        this.rankCheckIntervalMillis = plugin.getConfig().getLong("rank_check_interval_minutes", 5) * 60_000L;
        this.messageSender = messageSender;

        loadRankRewards();

        if (plugin.getConfig().getBoolean("playtimes_import_mysql.enabled", false) && storage instanceof MySQLStorage) {
            ((MySQLStorage) storage).connect();
        }
    }

    private void loadRankRewards() {
        rankRewards.clear();
        List<Map<?, ?>> rewards = plugin.getConfig().getMapList("rank_rewards");
        for (Map<?, ?> map : rewards) {
            long hours = ((Number) map.get("hours")).longValue();
            String group = (String) map.get("group");
            if (group != null) {
                rankRewards.add(new RankReward(hours, group));
            }
        }
        rankRewards.sort(Comparator.comparingLong(r -> r.requiredHours));
    }

    public void checkAndApplyRank(UUID uuid) {
        long now = System.currentTimeMillis();
        if (lastRankCheck.getOrDefault(uuid, 0L) + rankCheckIntervalMillis > now) return;
        lastRankCheck.put(uuid, now);

        if (!isConnectionValid()) return;

        String sql = "SELECT play_minutes, last_given_rank FROM playtime WHERE uuid = ?";

        try (PreparedStatement ps = storage.getMainConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    handleRankReward(uuid, rs.getLong("play_minutes"), rs.getString("last_given_rank"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleRankReward(UUID uuid, long playMinutes, String lastGivenRank) {
        long hours = playMinutes / 60;
        for (int i = rankRewards.size() - 1; i >= 0; i--) {
            RankReward reward = rankRewards.get(i);
            if (hours >= reward.requiredHours) {
                if (lastGivenRank != null && lastGivenRank.equalsIgnoreCase(reward.group)) return;

                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    promotePlayerAsync(player, reward.group);
                }

                updateLastGivenRank(uuid, reward.group);
                break;
            }
        }
    }

    private String getGroupDisplayName(String groupName) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return groupName; // fallback
        }

        for (Node node : group.data().toCollection()) {
            if (node.getKey().startsWith("displayname.")) {
                String displayName = node.getKey().substring("displayname.".length());
                return displayName.replace("&", "§"); // colors properly
            }
        }

        return groupName;
    }

    private void promotePlayerAsync(Player player, String group) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String command = String.format("lp user %s parent add %s", player.getName(), group);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            String displayName = getGroupDisplayName(group);

            if (plugin.getConfig().getBoolean("rankup_self_message_enabled", false)) {
                String msg = plugin.getConfig().getString("rankup_self_message",
                        "&aCongratulations! You have received a new rank: &e%rank%");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        msg.replace("%player%", player.getName()).replace("%rank%", displayName)));
            }

            if (plugin.getConfig().getBoolean("rankup_broadcast_enabled", false)) {
                String broadcast = plugin.getConfig().getString("rankup_broadcast_message",
                        "&6%player% has reached a new rank: &e%rank%!");
                String parsed = broadcast.replace("%player%", player.getName()).replace("%rank%", displayName);

                messageSender.sendPluginMessageToBungee("rankup_broadcast", parsed);
            }
        });
    }

    public void sendPluginMessageToBungee(String subChannel, String message) {
        String combinedKey = subChannel + "::" + message;

        if (recentlySentPluginMessages.contains(combinedKey)) {
            return;
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.getLogger().info("[PlaytimeTrackerTGE] No online players – cannot send plugin message.");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        out.writeUTF(message);

        Player player = Bukkit.getOnlinePlayers().iterator().next();
        player.sendPluginMessage(plugin, "tege56:playtimetrackertgebungee", out.toByteArray());

        Bukkit.getLogger().info("[PlaytimeTrackerTGE] Sent plugin message to BungeeCord [" + subChannel + "]: " + message);

        recentlySentPluginMessages.add(combinedKey);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentlySentPluginMessages.remove(combinedKey), 20L); // 1 second cooldown
    }

    private void updateLastGivenRank(UUID uuid, String newRank) {
        if (!isConnectionValid()) return;

        String sql = "UPDATE playtime SET last_given_rank = ? WHERE uuid = ?";

        try (PreparedStatement ps = storage.getMainConnection().prepareStatement(sql)) {
            ps.setString(1, newRank);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isConnectionValid() {
        try {
            Connection conn = storage.getMainConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String color(String msg) {
        return msg == null ? "" : msg.replace("&", "§");
    }

    public void removeLastRank(Player player) {
        if (!storage.ensureConnection()) {
            Bukkit.getLogger().warning(color("&cDatabase connection invalid, please try again later."));
            player.sendMessage(color("&cDatabase connection invalid, please try again later."));
            return;
        }

        String sql = "UPDATE playtime SET last_given_rank = NULL WHERE uuid = ?";

        try (PreparedStatement ps = storage.getMainConnection().prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                player.sendMessage(color("&aLast rank successfully removed."));
            } else {
                player.sendMessage(color("&cYou were not in the database? Removal failed."));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(color("&cFailed to remove last rank due to an error."));
        }
    }

    public void importPlayTimesDataWithAfk(PlayTimesMySQLStorage importStorage, StorageProvider mainStorage) {
        if (importStorage == null || mainStorage == null) {
            plugin.getLogger().severe("Import or main storage is null, aborting import.");
            return;
        }

        int imported = 0;

        try (ResultSet rs = importStorage.getPlaytimesData()) {
            if (rs == null) {
                plugin.getLogger().severe("Failed to retrieve data from import storage.");
                return;
            }

            while (rs.next()) {
                String rawUuid = rs.getString("uniqueId");
                UUID uuid;

                try {
                    uuid = UUID.fromString(rawUuid);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID: " + rawUuid + ", skipping.");
                    continue;
                }

                long totalTicks = rs.getLong("totalPlaytime");
                long afkTicks = rs.getLong("totalAFKtime");
                long playtimeMinutes = Math.max((totalTicks - afkTicks) / 60, 0); // estetään negatiivinen aika

                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                String username = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
                long firstJoin = offlinePlayer.getFirstPlayed() > 0 ? offlinePlayer.getFirstPlayed() / 1000L : System.currentTimeMillis() / 1000L;

                mainStorage.saveImportedPlaytime(uuid, playtimeMinutes, username, firstJoin);
                imported++;
            }

            plugin.getLogger().info("Imported " + imported + " player record(s) successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error during import: " + e.getMessage());
            e.printStackTrace();
        } finally {
            importStorage.disconnect();
        }
    }
}
