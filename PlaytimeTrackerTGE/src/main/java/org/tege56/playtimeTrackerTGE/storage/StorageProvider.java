package org.tege56.playtimeTrackerTGE.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StorageProvider {
    void connect();
    void closeConnection();
    boolean ensureConnection();

    Connection getMainConnection();
    Connection getPlayTimesConnection();

    boolean isFirstJoin(UUID uuid);

    void insertOrUpdatePlayer(UUID uuid, String username, long firstJoin) throws SQLException;
    void addPlaytime(String username, long minutesToAdd) throws SQLException;
    void updatePlaytime(UUID uuid, String username, long minutes) throws SQLException;
    void resetPlaytime(String username);
    void setPlaytime(String username, long newMinutes) throws SQLException;
    void saveImportedPlaytime(UUID uuid, long playtimeMinutes, String username, long firstJoin);

    void savePlaytime(UUID uuid, String name, long minutes);
    Optional<PlayerData> getPlaytime(String username);

    List<String> getTopPlaytimeLines(int limit);
    List<String> getTopPlaytimeLines(int limit, String format);

    Set<UUID> getAllPlayerUUIDs();
    Set<String> getAllUsernames();

    PreparedStatement prepareMainStatement(String sql) throws SQLException;
    PreparedStatement preparePlaytimeStatement(UUID uuid, String username, long minutes) throws SQLException;
    PreparedStatement prepareFirstJoinStatement(UUID uuid, String username) throws SQLException;
}
