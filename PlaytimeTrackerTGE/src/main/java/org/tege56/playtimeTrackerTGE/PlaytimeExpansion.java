package org.tege56.playtimeTrackerTGE;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.tege56.playtimeTrackerTGE.storage.StorageProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PlaytimeExpansion extends PlaceholderExpansion {

    private final PlaytimeTrackerTGE plugin;
    private final StorageProvider storage;

    public PlaytimeExpansion(PlaytimeTrackerTGE plugin, StorageProvider storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playtime"; // Placeholderit alkaa esim. %playtime_minutes%
    }

    @Override
    public @NotNull String getAuthor() {
        return "tege56";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        if (identifier.equalsIgnoreCase("is_afk")) {
            return plugin.isAFK(player.getUniqueId()) ? "true" : "false";
        }

        try {
            PreparedStatement ps = storage.getMainConnection().prepareStatement(
                    "SELECT play_minutes, first_join, last_seen FROM playtime WHERE uuid = ?"
            );
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long minutes = rs.getLong("play_minutes");
                long hours = minutes / 60;
                long mins = minutes % 60;

                long firstJoin = rs.getLong("first_join");
                long lastSeen = rs.getLong("last_seen");

                String firstJoinFormatted = firstJoin > 0
                        ? DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochSecond(firstJoin))
                        : "unknown";

                String lastSeenFormatted = lastSeen > 0
                        ? DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochSecond(lastSeen))
                        : "unknown";

                switch (identifier.toLowerCase()) {
                    case "minutes":
                        return String.valueOf(minutes);
                    case "hours":
                        return String.valueOf(hours);
                    case "formatted":
                        return hours + "h " + mins + "min";
                    case "first_join_date":
                        return firstJoinFormatted;
                    case "last_seen_date":
                        return lastSeenFormatted;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL virhe placeholderia käsiteltäessä: " + e.getMessage());
        }

        return null;
    }
}
