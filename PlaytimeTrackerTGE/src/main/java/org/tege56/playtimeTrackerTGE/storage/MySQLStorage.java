package org.tege56.playtimeTrackerTGE.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class MySQLStorage implements StorageProvider {

    private Connection mainConnection;
    private final JavaPlugin plugin;

    public MySQLStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port");
        String database = plugin.getConfig().getString("database.mysql.database");
        String username = plugin.getConfig().getString("database.mysql.username");
        String password = plugin.getConfig().getString("database.mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";

        try {
            if (mainConnection != null && !mainConnection.isClosed()) {
                mainConnection.close();
            }

            mainConnection = DriverManager.getConnection(url, username, password);

            if (mainConnection.isValid(3)) {
                plugin.getLogger().info("MySQL connection established.");
                createTables();
            } else {
                plugin.getLogger().severe("MySQL connection is not valid.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error connecting to database: " + e.getMessage());
        }
    }

    private void createTables() {
        String create = """
            CREATE TABLE IF NOT EXISTS playtime (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16),
                play_minutes BIGINT DEFAULT 0,
                first_join BIGINT DEFAULT 0,
                last_given_rank VARCHAR(100),
                last_seen BIGINT DEFAULT 0
            )
            """;
        try (Statement stmt = mainConnection.createStatement()) {
            stmt.executeUpdate(create);
            plugin.getLogger().info("Tables checked/created.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    @Override
    public boolean ensureConnection() {
        try {
            if (mainConnection == null || mainConnection.isClosed() || !mainConnection.isValid(2)) {
                connect();
            }
            return mainConnection != null && !mainConnection.isClosed() && mainConnection.isValid(2);
        } catch (SQLException e) {
            plugin.getLogger().severe("Connection check failed: " + e.getMessage());
            return false;
        }
    }

    private PreparedStatement prepareStatement(String sql) throws SQLException {
        if (!ensureConnection()) throw new SQLException("No database connection.");
        return mainConnection.prepareStatement(sql);
    }

    @Override
    public void updatePlaytime(UUID uuid, String username, long minutes) {
        String sql = """
            INSERT INTO playtime (uuid, username, play_minutes, first_join)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                play_minutes = play_minutes + VALUES(play_minutes),
                username = VALUES(username)
            """;
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, minutes);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating playtime: " + e.getMessage());
        }
    }

    @Override
    public void resetPlaytime(String username) {
        try (PreparedStatement ps = prepareStatement("UPDATE playtime SET play_minutes = 0 WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error resetting playtime: " + e.getMessage());
        }
    }

    @Override
    public void addPlaytime(String username, long minutesToAdd) {
        try (PreparedStatement ps = prepareStatement("UPDATE playtime SET play_minutes = play_minutes + ? WHERE username = ?")) {
            ps.setLong(1, minutesToAdd);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding playtime: " + e.getMessage());
        }
    }

    @Override
    public void setPlaytime(String username, long newMinutes) {
        try (PreparedStatement ps = prepareStatement("UPDATE playtime SET play_minutes = ? WHERE username = ?")) {
            ps.setLong(1, newMinutes);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting playtime: " + e.getMessage());
        }
    }

    @Override
    public Optional<PlayerData> getPlaytime(String username) {
        String sql = "SELECT uuid, username, play_minutes, first_join FROM playtime WHERE username = ?";
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    return Optional.of(new PlayerData(
                            uuid,
                            rs.getString("username"),
                            rs.getLong("play_minutes"),
                            rs.getLong("first_join")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching player data: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<String> getTopPlaytimeLines(int limit, String format) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT username, play_minutes FROM playtime ORDER BY play_minutes DESC LIMIT ?";
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    String name = rs.getString("username");
                    long mins = rs.getLong("play_minutes");
                    String line = format
                            .replace("%rank%", String.valueOf(rank))
                            .replace("%player%", name)
                            .replace("%hours%", String.valueOf(mins / 60))
                            .replace("%minutes%", String.valueOf(mins % 60));
                    result.add(line);
                    rank++;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching top list: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<String> getTopPlaytimeLines(int limit) {
        String format = plugin.getConfig().getString("top_playtime_format", "&e%rank%. &a%player%: %hours%h %minutes%min");
        return getTopPlaytimeLines(limit, format);
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() {
        Set<UUID> result = new HashSet<>();
        String sql = "SELECT uuid FROM playtime";
        try (PreparedStatement ps = prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading UUIDs: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Set<String> getAllUsernames() {
        Set<String> result = new HashSet<>();
        String sql = "SELECT username FROM playtime";
        try (PreparedStatement ps = prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading usernames: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void savePlaytime(UUID uuid, String name, long minutes) {
        String sql = """
            INSERT INTO playtime (uuid, username, play_minutes)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE play_minutes = play_minutes + ?
            """;
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, minutes);
            ps.setLong(4, minutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving playtime: " + e.getMessage());
        }
    }

    @Override
    public void closeConnection() {
        try {
            if (mainConnection != null && !mainConnection.isClosed()) {
                mainConnection.close();
                plugin.getLogger().info("MySQL connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing connection: " + e.getMessage());
        }
    }

    @Override
    public Connection getMainConnection() {
        return mainConnection;
    }

    @Override
    public Connection getPlayTimesConnection() {
        return mainConnection;
    }

    @Override
    public PreparedStatement prepareMainStatement(String sql) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public void insertOrUpdatePlayer(UUID uuid, String username, long firstJoin) throws SQLException {
        String sql = """
            INSERT INTO playtime (uuid, username, play_minutes, first_join)
            VALUES (?, ?, 0, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                first_join = CASE WHEN first_join = 0 THEN VALUES(first_join) ELSE first_join END
            """;
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, firstJoin);
            ps.executeUpdate();
        }
    }

    @Override
    public PreparedStatement preparePlaytimeStatement(UUID uuid, String username, long minutes) throws SQLException {
        String sql = """
            INSERT INTO playtime (uuid, username, play_minutes, first_join)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE play_minutes = play_minutes + VALUES(play_minutes), username = VALUES(username)
            """;
        PreparedStatement ps = prepareStatement(sql);
        ps.setString(1, uuid.toString());
        ps.setString(2, username);
        ps.setLong(3, minutes);
        ps.setLong(4, Instant.now().getEpochSecond());
        return ps;
    }

    @Override
    public PreparedStatement prepareFirstJoinStatement(UUID uuid, String username) throws SQLException {
        String sql = """
            INSERT INTO playtime (uuid, username, play_minutes, first_join)
            VALUES (?, ?, 0, ?)
            ON DUPLICATE KEY UPDATE first_join = IF(first_join = 0, VALUES(first_join), first_join)
            """;
        PreparedStatement ps = prepareStatement(sql);
        ps.setString(1, uuid.toString());
        ps.setString(2, username);
        ps.setLong(3, Instant.now().getEpochSecond());
        return ps;
    }

    public void saveImportedPlaytime(UUID uuid, long playMinutes, String username, long firstJoin) {
        String sql = """
            INSERT INTO playtime (uuid, username, play_minutes, first_join)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE username = VALUES(username), play_minutes = VALUES(play_minutes), first_join = VALUES(first_join)
            """;
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, playMinutes);
            ps.setLong(4, firstJoin);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving imported playtime: " + e.getMessage());
        }
    }

    public boolean isFirstJoin(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM playtime WHERE uuid = ?";
        try (PreparedStatement ps = prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking first join: " + e.getMessage());
        }
        return false;
    }
}
