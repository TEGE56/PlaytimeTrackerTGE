package org.tege56.playtimeTrackerTGE.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SQLiteStorage implements StorageProvider {
    private Connection connection;
    private final File dbFile;
    private final JavaPlugin plugin;

    public SQLiteStorage(JavaPlugin plugin, String filename) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), filename);
    }

    @Override
    public boolean ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS playtime (" +
                            "uuid TEXT PRIMARY KEY," +
                            "username TEXT," +
                            "play_minutes INTEGER," +
                            "first_join INTEGER," +
                            "last_given_rank TEXT" +
                            ")");
                }

                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA table_info(playtime)")) {

                    boolean hasLastGivenRank = false;
                    boolean hasLastSeen = false;

                    while (rs.next()) {
                        String columnName = rs.getString("name");
                        if ("last_given_rank".equalsIgnoreCase(columnName)) {
                            hasLastGivenRank = true;
                        } else if ("last_seen".equalsIgnoreCase(columnName)) {
                            hasLastSeen = true;
                        }
                    }

                    if (!hasLastGivenRank) {
                        try (Statement alterStmt = connection.createStatement()) {
                            alterStmt.executeUpdate("ALTER TABLE playtime ADD COLUMN last_given_rank TEXT");
                        }
                    }

                    if (!hasLastSeen) {
                        try (Statement alterStmt = connection.createStatement()) {
                            alterStmt.executeUpdate("ALTER TABLE playtime ADD COLUMN last_seen INTEGER DEFAULT 0");
                        }
                    }
                }
            }
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Database connection verification failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void connect() {
        ensureConnection();
    }

    @Override
    public void savePlaytime(UUID uuid, String name, long minutes) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO playtime (uuid, username, play_minutes, first_join) " +
                            "VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET " +
                            "play_minutes = playtime.play_minutes + excluded.play_minutes, " +
                            "first_join = CASE WHEN playtime.first_join = 0 THEN excluded.first_join ELSE playtime.first_join END"
            );
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, minutes);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving playtime to SQLite database.");
            e.printStackTrace();
        }
    }

    @Override
    public void updatePlaytime(UUID uuid, String username, long minutes) {
        savePlaytime(uuid, username, minutes);
    }

    @Override
    public void resetPlaytime(String name) {
        String sql = "UPDATE playtime SET play_minutes = 0 WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error resetting playtime for player: " + name);
            e.printStackTrace();
        }
    }

    @Override
    public void addPlaytime(String username, long minutesToAdd) throws SQLException {
        String sql = "UPDATE playtime SET play_minutes = play_minutes + ? WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, minutesToAdd);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding playtime to SQLite: " + username);
            e.printStackTrace();
        }
    }

    @Override
    public void setPlaytime(String username, long newMinutes) throws SQLException {
        String sql = "UPDATE playtime SET play_minutes = ? WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, newMinutes);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting playtime for player: " + username);
            e.printStackTrace();
        }
    }

    @Override
    public PreparedStatement prepareMainStatement(String sql) {
        try {
            return connection.prepareStatement(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error preparing SQL statement: " + sql);
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Optional<PlayerData> getPlaytime(String username) {
        String sql = "SELECT uuid, play_minutes, first_join FROM playtime WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long playMinutes = rs.getLong("play_minutes");
                    long firstJoin = rs.getLong("first_join");
                    return Optional.of(new PlayerData(uuid, username, playMinutes, firstJoin));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching playtime for player: " + username);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public List<String> getTopPlaytimeLines(int limit, String format) {
        List<String> lines = new ArrayList<>();
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT username, play_minutes FROM playtime ORDER BY play_minutes DESC LIMIT ?"
            );
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            int rank = 1;

            while (rs.next()) {
                String name = rs.getString("username");
                long totalMinutes = rs.getLong("play_minutes");
                long hours = totalMinutes / 60;
                long minutes = totalMinutes % 60;

                String line = format
                        .replace("%rank%", String.valueOf(rank))
                        .replace("%player%", name)
                        .replace("%hours%", String.valueOf(hours))
                        .replace("%minutes%", String.valueOf(minutes));
                lines.add(line);
                rank++;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lines;
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        String sql = "SELECT uuid FROM playtime";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String uuidStr = rs.getString("uuid");
                try {
                    uuids.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in database: " + uuidStr);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching all player UUIDs.");
            e.printStackTrace();
        }

        return uuids;
    }

    @Override
    public Set<String> getAllUsernames() {
        Set<String> usernames = new HashSet<>();
        String sql = "SELECT username FROM playtime";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                if (username != null && !username.isEmpty()) {
                    usernames.add(username);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching all usernames.");
            e.printStackTrace();
        }

        return usernames;
    }

    @Override
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection.");
            e.printStackTrace();
        }
    }

    @Override
    public Connection getMainConnection() {
        return this.connection;
    }

    @Override
    public Connection getPlayTimesConnection() {
        return this.connection;
    }

    @Override
    public void insertOrUpdatePlayer(UUID uuid, String username, long firstJoin) throws SQLException {
        String sql = "INSERT INTO playtime (uuid, username, play_minutes, first_join) VALUES (?, ?, 0, ?) ON CONFLICT(uuid) DO UPDATE SET first_join = CASE WHEN first_join = 0 THEN excluded.first_join ELSE first_join END";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, firstJoin);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting/updating player in SQLite: " + username);
            e.printStackTrace();
        }
    }

    public List<PlayerData> getTopPlaytimes(int limit) {
        List<PlayerData> topPlayers = new ArrayList<>();
        String sql = "SELECT uuid, username, play_minutes, first_join FROM playtime ORDER BY play_minutes DESC LIMIT ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    long playMinutes = rs.getLong("play_minutes");
                    long firstJoin = rs.getLong("first_join");
                    topPlayers.add(new PlayerData(uuid, username, playMinutes, firstJoin));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching top players.");
            e.printStackTrace();
        }
        return topPlayers;
    }

    @Override
    public List<String> getTopPlaytimeLines(int amount) {
        List<String> lines = new ArrayList<>();

        String format = plugin.getConfig().getString("top_playtime_format", "&e%rank%. &a%player%: %hours%h %minutes%min");

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT username, play_minutes FROM playtime ORDER BY play_minutes DESC LIMIT " + amount);

            int rank = 1;
            while (rs.next()) {
                String name = rs.getString("username");
                int minutes = rs.getInt("play_minutes");

                int hours = minutes / 60;
                int remainingMinutes = minutes % 60;

                String formattedLine = format
                        .replace("%rank%", String.valueOf(rank))
                        .replace("%player%", name)
                        .replace("%hours%", String.valueOf(hours))
                        .replace("%minutes%", String.valueOf(remainingMinutes));

                lines.add(formattedLine);
                rank++;
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching top playtimes.");
            e.printStackTrace();
        }

        return lines;
    }

    @Override
    public PreparedStatement preparePlaytimeStatement(UUID uuid, String username, long minutes) throws SQLException {
        String sql = "INSERT INTO playtime (uuid, username, play_minutes, first_join) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "play_minutes = play_minutes + excluded.play_minutes, " +
                "username = excluded.username";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, uuid.toString());
        ps.setString(2, username);
        ps.setLong(3, minutes);
        ps.setLong(4, Instant.now().getEpochSecond());
        return ps;
    }

    @Override
    public PreparedStatement prepareFirstJoinStatement(UUID uuid, String username) throws SQLException {
        String sql = "INSERT INTO playtime (uuid, username, play_minutes, first_join) " +
                "VALUES (?, ?, 0, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "first_join = CASE WHEN first_join = 0 THEN excluded.first_join ELSE first_join END";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, uuid.toString());
        ps.setString(2, username);
        ps.setLong(3, Instant.now().getEpochSecond());
        return ps;
    }

    @Override
    public boolean isFirstJoin(UUID uuid) {
        String sql = "SELECT COUNT(*) AS count FROM playtime WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    return count == 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking if player is first time: " + uuid);
            e.printStackTrace();
        }
        return false; // If error or not found, not first time
    }
}
